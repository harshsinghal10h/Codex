"""Contract tests use real SDK/chunking/store; fake embeddings are not an accuracy evaluation."""
import json
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
sys.path.insert(0,str(next((ROOT/'upstream/sentrysearch').glob('sentrysearch-*'))))
from intelligence.sentry_adapter import SentryAdapter,POLICY
from intelligence.gemini_compat import GeminiEmbedding2

class SemanticSearchTests(unittest.TestCase):
    def test_authoritative_zip_source(self):
        manifest=json.loads((ROOT/'vendor/search-source.json').read_text())
        import hashlib
        upstream=next((ROOT/'upstream/sentrysearch').glob('sentrysearch-*'))
        for name,digest in manifest['files'].items():
            self.assertEqual(hashlib.sha256((upstream/name).read_bytes()).hexdigest(),digest,name)

    def test_embedding2_request_contract(self):
        from google.genai import types as sdk
        calls=[]
        def embed(**kw):
            body=kw['config'].model_dump(exclude_none=True)
            self.assertNotIn('task_type',body)
            self.assertEqual(body['output_dimensionality'],768)
            calls.append(kw)
            return types.SimpleNamespace(embeddings=[types.SimpleNamespace(values=[1.,0.])])
        upstream=types.SimpleNamespace(_limiter=types.SimpleNamespace(wait=lambda:None),_client=types.SimpleNamespace(models=types.SimpleNamespace(embed_content=embed)),_make_video_part=lambda p,t:sdk.Part.from_bytes(data=b'raw-video',mime_type='video/mp4'))
        engine=GeminiEmbedding2(upstream)
        engine.embed_query('a cyclist turning left');engine.embed_video_chunk('clip.mp4')
        self.assertEqual(calls[0]['contents'],'task: search result | query: a cyclist turning left')
        self.assertEqual(calls[1]['contents'].parts[0].inline_data.mime_type,'video/mp4')
        self.assertEqual(calls[1]['contents'].parts[0].inline_data.data,b'raw-video')

    def test_real_zip_retrieval_and_reopen_without_reindex(self):
        from sentrysearch.store import SentryStore
        from sentrysearch import embedder,chunker
        with tempfile.TemporaryDirectory() as temp:
            base=Path(temp);media=base/'unrelated-filename.mp4';media.write_bytes(b'safe-fixture')
            adapter=SentryAdapter(base/'cache',ROOT)
            identity=adapter._identity(str(media.resolve()))
            directory=adapter.cache/(identity+'-local-'+POLICY)
            store=SentryStore(str(directory),backend='local',model='qwen2b')
            store.add_chunk('opening',[1.,0.,0.],{'source_file':str(media),'start_time':0.,'end_time':8.})
            store.add_chunk('cyclist',[0.,1.,0.],{'source_file':str(media),'start_time':162.,'end_time':170.})
            (directory/'complete.json').write_text('{}')
            engine=types.SimpleNamespace(embed_query=lambda q:[0.,1.,0.])
            with patch.object(embedder,'get_embedder',return_value=engine),patch.object(chunker,'chunk_video',side_effect=AssertionError('Reopening must not decode indexed media')):
                result=adapter.search(7,'a cyclist turning left',media,'local')
                self.assertEqual(result['results'][0]['start'],162000)
                self.assertEqual(result['results'][0]['end'],170000)
                reopened=SentryAdapter(base/'cache',ROOT)
                again=reopened.search(7,'a cyclist turning left',media,'local')
                self.assertEqual(again['results'],result['results'])
            # A changed video cannot reuse the prior content-addressed index.
            media.write_bytes(b'another-video')
            with self.assertRaisesRegex(ValueError,'Prepare this video'):
                adapter.search(7,'a cyclist turning left',media,'local')

    def test_unprepared_search_does_not_send_video(self):
        from sentrysearch import embedder
        with tempfile.TemporaryDirectory() as temp:
            media=Path(temp)/'video.mp4';media.write_bytes(b'fixture')
            adapter=SentryAdapter(Path(temp)/'cache',ROOT)
            with patch.object(embedder,'get_embedder',side_effect=AssertionError('No model or upload without preparation consent')):
                with self.assertRaisesRegex(ValueError,'Prepare this video'):
                    adapter.search(1,'a car',media)

if __name__=='__main__':unittest.main()
