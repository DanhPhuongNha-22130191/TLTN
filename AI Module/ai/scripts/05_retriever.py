import os
import asyncio
import logging
import warnings
from typing import List, Dict

from qdrant_client import AsyncQdrantClient
from qdrant_client.models import Prefetch, SparseVector, FusionQuery, Fusion

os.environ["TOKENIZERS_PARALLELISM"] = "false"
warnings.filterwarnings("ignore")
logging.getLogger("transformers").setLevel(logging.ERROR)

try:
    # --- HOTFIX CHO TRANSFORMERS 4.45+ VÀ PYTHON 3.13 ---
    import transformers.utils.import_utils
    if not hasattr(transformers.utils.import_utils, 'is_torch_fx_available'):
        transformers.utils.import_utils.is_torch_fx_available = lambda: False
    # ---------------------------------------------------
    from FlagEmbedding import BGEM3FlagModel
except ImportError:
    pass

class QdrantRetriever:
    """
    Async Advanced Hybrid Retriever (Dense + Sparse Qdrant Native Search)
    """
    def __init__(self):
        print("🔍 Loading Async Hybrid Retriever (BGE-M3 + Qdrant)...")
        base_dir = os.path.dirname(os.path.abspath(__file__))
        self.db_path = os.path.join(base_dir, "..", "data", "qdrant_db")
        self.collection_name = "handbook_v2"
        
        self.client = AsyncQdrantClient(path=self.db_path)
        # Dùng BGE-M3 trên GPU với FP16 để tối ưu tốc độ và dung lượng
        self.model = BGEM3FlagModel('BAAI/bge-m3', use_fp16=True, device='cuda')
        print("✅ Hybrid Retriever ready!")

    def _encode_query(self, query: str):
        embeddings = self.model.encode([query], return_dense=True, return_sparse=True, return_colbert_vecs=False)
        dense = embeddings['dense_vecs'][0].tolist()
        lexical = embeddings['lexical_weights'][0]
        
        sparse_indices = [int(k) for k in lexical.keys()]
        sparse_values = list(lexical.values())
        return dense, sparse_indices, sparse_values

    async def search(self, query: str, top_k: int = 15) -> List[Dict]:
        """
        Executes an async hybrid search utilizing Qdrant's Reciprocal Rank Fusion.
        """
        import torch
        torch.cuda.empty_cache()
        dense, sparse_indices, sparse_values = await asyncio.to_thread(self._encode_query, query)
        
        dense_prefetch = Prefetch(
            query=dense,
            using="dense",
            limit=top_k
        )
        
        sparse_prefetch = Prefetch(
            query=SparseVector(indices=sparse_indices, values=sparse_values),
            using="sparse",
            limit=top_k
        )
        
        # Native Hybrid Search with Prefetch
        results = await self.client.query_points(
            collection_name=self.collection_name,
            prefetch=[dense_prefetch, sparse_prefetch],
            query=FusionQuery(fusion=Fusion.RRF), # fusion
            limit=top_k,
            with_payload=True
        )
        
        scored = []
        for p in results.points:
            scored.append({
                "score": p.score,
                "content": p.payload.get("content", ""),
                "source": p.payload.get("source", "unknown"),
                "chunk_id": p.payload.get("chunk_id", None)
            })
            
        return scored

async def test_retriever():
    retriever = QdrantRetriever()
    results = await retriever.search("Làm việc từ xa (remote)")
    print("\n🔎 RESULTS:\n")
    for r in results[:5]:
        print("=" * 50)
        print("Score:", r["score"])
        print("Source:", r["source"])
        print("Content:", r["content"][:200])

if __name__ == "__main__":
    asyncio.run(test_retriever())