"""Video embeddings and cosine retrieval from the authoritative supplied ZIP."""
import hashlib
import json
from pathlib import Path
import sys
import threading
import shutil

UPSTREAM_COMMIT = 'ab688137e2a4a9d6f67cac5677be26db16dc406c'
POLICY = '8s-overlap2-320p-2fps-gemini2-prefix-v2'

class SentryAdapter:
    def __init__(self, cache, upstream):
        sys.path.insert(0, str(upstream))
        self.cache = Path(cache); self.cache.mkdir(parents=True, exist_ok=True)
        self.lock = threading.RLock(); self.indices = {}; self.identities = {}

    def _identity(self, path):
        from .vision import sha256_file
        stat = Path(path).stat()
        signature = (stat.st_size, stat.st_mtime_ns, stat.st_ctime_ns, stat.st_ino)
        previous = self.identities.get(path)
        if previous and previous[0] == signature: return previous[1]
        identity = sha256_file(path)
        self.identities[path] = (signature, identity)
        return identity

    def index(self, media_id, path, backend='gemini', progress=lambda p: None, prepare=True):
        from sentrysearch import embedder, chunker
        from sentrysearch.store import SentryStore
        from .gemini_compat import GeminiEmbedding2
        with self.lock:
            path = str(Path(path).resolve())
            identity = self._identity(path)
            if backend not in ('gemini', 'local'): raise ValueError('Choose Gemini or local search.')
            model = 'qwen2b' if backend == 'local' else None
            directory = self.cache / (identity + '-' + backend + '-' + POLICY)
            directory.mkdir(exist_ok=True)
            complete = directory / 'complete.json'
            if not prepare and not complete.exists():
                raise ValueError('Prepare this video for semantic search first.')
            embedder.reset_embedder()
            engine = embedder.get_embedder(backend, **({'model': model} if model else {}))
            if backend == 'gemini': engine = GeminiEmbedding2(engine)
            store = SentryStore(db_path=str(directory), backend=backend, model=model)
            cached = complete.exists() and store.collection.count() > 0
            if not cached:
                if not prepare: raise ValueError('Prepare this video for semantic search first.')
                chunks = chunker.chunk_video(path, chunk_duration=8, overlap=2)
                if not chunks: raise ValueError('No video chunks could be decoded.')
                try:
                    for i, chunk in enumerate(chunks):
                        chunk_id = hashlib.sha256((identity + ':' + str(chunk['start_time'])).encode()).hexdigest()[:24]
                        if not store.collection.get(ids=[chunk_id])['ids']:
                            reduced = chunker.preprocess_chunk(chunk['chunk_path'], target_resolution=320, target_fps=2)
                            store.add_chunk(chunk_id, engine.embed_video_chunk(reduced), chunk)
                        progress({'phase': 'indexing', 'progress': round((i + 1) / len(chunks) * 100)})
                    temp = complete.with_suffix('.tmp')
                    temp.write_text(json.dumps({'media_sha256': identity, 'policy': POLICY, 'chunks': len(chunks)}))
                    temp.replace(complete)
                finally:
                    shutil.rmtree(Path(chunks[0]['chunk_path']).parent, ignore_errors=True)
            self.indices[str(media_id)] = (store, engine, identity, path, backend)
            return {'phase': 'indexed', 'progress': 100, 'media_sha256': identity, 'cached': cached}

    def search(self, media_id, query, path=None, backend='gemini'):
        from sentrysearch.search import _search_with_embedding
        if not query.strip() or len(query) > 1000: raise ValueError('Describe a scene in 1–1000 characters.')
        with self.lock:
            active = self.indices.get(str(media_id))
            if path is not None and (not active or active[3] != str(Path(path).resolve()) or active[4] != backend or active[2] != self._identity(str(Path(path).resolve()))):
                self.index(media_id, path, backend, prepare=False)
            elif not active:
                raise ValueError('Prepare this video for semantic search first.')
            store, engine, identity, path, backend = self.indices[str(media_id)]
            hits = _search_with_embedding(engine.embed_query(query), store, n_results=12, dedupe_threshold=.9)
            return {'media_id': str(media_id), 'media_sha256': identity, 'results': [
                {'start': round(h['start_time'] * 1000), 'end': round(h['end_time'] * 1000),
                 'similarity': h['similarity_score'], 'source': 'SentrySearch'} for h in hits][:5]}
