"""Use the pinned SentrySearch index and retrieval code without its CLI/trimmer."""
import hashlib
import os
from pathlib import Path
import sys
import threading
import shutil

UPSTREAM_COMMIT='ab688137e2a4a9d6f67cac5677be26db16dc406c'

class SentryAdapter:
    def __init__(self,cache,upstream):
        sys.path.insert(0,str(upstream))
        self.cache=Path(cache);self.cache.mkdir(parents=True,exist_ok=True)
        self.lock=threading.Lock();self.indices={}
    def index(self,media_id,path,backend='gemini',progress=lambda p:None):
        # Imports and model loading happen in the analysis worker.
        from sentrysearch import embedder,chunker
        from sentrysearch.store import SentryStore
        from .vision import sha256_file
        with self.lock:
            identity=sha256_file(path)
            path=str(Path(path).resolve())
            if backend not in ('gemini','local'):raise ValueError('Choose gemini or local search.')
            model='qwen2b' if backend=='local' else None
            directory=self.cache/(identity+'-'+backend+'-8s-2fps-v1')
            directory.mkdir(exist_ok=True)
            # Configuring the embedder is global upstream state; serialize it.
            embedder.reset_embedder()
            engine=embedder.get_embedder(backend,**({'model':model} if model else {}))
            store=SentryStore(db_path=str(directory),backend=backend,model=model)
            chunks=chunker.chunk_video(path,chunk_duration=8,overlap=2)
            try:
                for i,chunk in enumerate(chunks):
                    chunk_id=hashlib.sha256((identity+':'+str(chunk['start_time'])).encode()).hexdigest()[:24]
                    if not store.collection.get(ids=[chunk_id])['ids']:
                        reduced=chunker.preprocess_chunk(chunk['chunk_path'],target_resolution=320,target_fps=2)
                        vector=engine.embed_video_chunk(reduced)
                        store.add_chunk(chunk_id,vector,chunk)
                    progress({'phase':'indexing','progress':round((i+1)/len(chunks)*100)})
            finally:
                if chunks:shutil.rmtree(Path(chunks[0]['chunk_path']).parent,ignore_errors=True)
            self.indices[str(media_id)]=(store,engine,identity,path)
            return {'phase':'indexed','progress':100,'media_sha256':identity}
    def search(self,media_id,query):
        from sentrysearch.search import _search_with_embedding
        if not query.strip() or len(query)>1000:raise ValueError('Describe a scene in 1–1000 characters.')
        with self.lock:
            if str(media_id) not in self.indices:raise ValueError('Index this video before searching.')
            store,engine,identity,path=self.indices[str(media_id)]
            hits=_search_with_embedding(engine.embed_query(query),store,n_results=12,dedupe_threshold=.9)
            return {'media_id':str(media_id),'media_sha256':identity,'results':[
              {'start':round(h['start_time']*1000),'end':round(h['end_time']*1000),
               'similarity':h['similarity_score'],'source':'SentrySearch'}
              for h in hits][:5]}
