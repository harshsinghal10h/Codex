"""Embedding 2 compatibility for the supplied SentrySearch ZIP.

Upstream chunking/retrieval stay intact. The current Embedding 2 API rejects
Embedding 001's task_type field; it requires a retrieval query prefix instead.
"""
class GeminiEmbedding2:
    def __init__(self, upstream):
        self.upstream = upstream

    def _embed(self, content):
        from google.genai import types
        from sentrysearch.gemini_embedder import EMBED_MODEL, DIMENSIONS, _retry
        self.upstream._limiter.wait()
        response = _retry(lambda: self.upstream._client.models.embed_content(
            model=EMBED_MODEL, contents=content,
            config=types.EmbedContentConfig(output_dimensionality=DIMENSIONS)))
        return response.embeddings[0].values

    def embed_video_chunk(self, path):
        from google.genai import types
        return self._embed(types.Content(parts=[self.upstream._make_video_part(path, types)]))

    def embed_query(self, query):
        return self._embed('task: search result | query: ' + query)
