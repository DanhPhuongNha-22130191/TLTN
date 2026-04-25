import os
import asyncio
import warnings
import logging
import numpy as np
from sentence_transformers import CrossEncoder

os.environ["TOKENIZERS_PARALLELISM"] = "false"
warnings.filterwarnings("ignore")
logging.getLogger("transformers").setLevel(logging.ERROR)

class Reranker:
    def __init__(self):
        print("🔄 Loading BGE-Reranker-v2-M3 on GPU (FP16)...")
        import torch
        self.model = CrossEncoder("BAAI/bge-reranker-v2-m3", device="cuda", model_kwargs={"torch_dtype": torch.float16})

    def _sync_rerank(self, query: str, chunks: list, top_k: int = 3):
        if not chunks:
            return []

        pairs = [(query, c.get("text") or c.get("content") or "") for c in chunks]
        scores = self.model.predict(pairs)
        scores = np.array(scores).reshape(-1).tolist()
        
        best_chunks = []
        for c, s in zip(chunks, scores):
            c_copy = c.copy()
            c_copy["rerank_score"] = float(s)
            c_copy["score"] = float(s)
            best_chunks.append(c_copy)
            
        best_chunks.sort(key=lambda x: x["score"], reverse=True)
        return best_chunks[:top_k]

    async def rerank(self, query: str, chunks: list, top_k: int = 3):
        """
        Asynchronously reranks a list of chunks based on a query.
        Delegates the CPU-bound CrossEncoder inference to a thread pool.
        """
        return await asyncio.to_thread(self._sync_rerank, query, chunks, top_k)