import os
import asyncio
import logging
import warnings
from typing import List, Dict

try:
    from qdrant_client import AsyncQdrantClient
    from qdrant_client.models import Prefetch, SparseVector, FusionQuery, Fusion
except ImportError:
    AsyncQdrantClient = None
    Prefetch = SparseVector = FusionQuery = Fusion = None

from ai.src.config import QDRANT_DB_PATH, COLLECTION_NAME, EMBEDDING_MODEL_NAME

os.environ["TOKENIZERS_PARALLELISM"] = "false"
warnings.filterwarnings("ignore")
logging.getLogger("transformers").setLevel(logging.ERROR)

try:
    import transformers.utils.import_utils
    if not hasattr(transformers.utils.import_utils, 'is_torch_fx_available'):
        transformers.utils.import_utils.is_torch_fx_available = lambda: False
    from FlagEmbedding import BGEM3FlagModel
except ImportError:
    BGEM3FlagModel = None
    try:
        from sentence_transformers import SentenceTransformer

        class SimpleSTWrapper:
            def __init__(self, model_name, use_fp16=False, device='cpu'):
                self.model = SentenceTransformer(model_name)

            def encode(self, texts, return_dense=True, return_sparse=False, return_colbert_vecs=False):
                dense = [v.tolist() for v in self.model.encode(texts, convert_to_numpy=True)]
                return {
                    'dense_vecs': dense,
                    'lexical_weights': [{} for _ in texts]
                }

        BGEM3FlagModel = SimpleSTWrapper
    except Exception:
        BGEM3FlagModel = None

class QdrantRetriever:
    def __init__(self, db_path: str = QDRANT_DB_PATH, collection_name: str = COLLECTION_NAME):
        print(f"Loading Async Hybrid Retriever (BGE-M3 + Qdrant collection '{collection_name}')...")
        self.db_path = db_path
        self.collection_name = collection_name
        
        if AsyncQdrantClient is None:
            raise ImportError(
                "qdrant_client is not installed. Install dependencies with: pip install -r ai/requirements.txt"
            )

        self.client = AsyncQdrantClient(path=self.db_path)
        
        if BGEM3FlagModel is None:
            raise ImportError(
                "BGEM3FlagModel (FlagEmbedding) is not available. Install or enable the FlagEmbedding wrapper that provides BGEM3FlagModel before running the retriever."
            )

        self.model = BGEM3FlagModel(
            EMBEDDING_MODEL_NAME,
            use_fp16=False,
            device="cpu"
        )
        print("Hybrid Retriever ready!")

    def _encode_query(self, query: str):
        embeddings = self.model.encode([query], return_dense=True, return_sparse=True, return_colbert_vecs=False)
        
        dense = embeddings['dense_vecs'][0]
        if hasattr(dense, 'tolist'):
            dense = dense.tolist()
        else:
            dense = list(dense)

        lexical = embeddings['lexical_weights'][0]
        
        sparse_indices = [int(k) for k in lexical.keys()]
        sparse_values = list(lexical.values())
        
        return dense, sparse_indices, sparse_values

    async def search(self, query: str, top_k: int = 15) -> List[Dict]:
        import torch
        if torch.cuda.is_available():
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
        
        results = await self.client.query_points(
            collection_name=self.collection_name,
            prefetch=[dense_prefetch, sparse_prefetch],
            query=FusionQuery(fusion=Fusion.RRF),
            limit=top_k,
            with_payload=True
        )
        
        scored = []
        for p in results.points:
            scored.append({
                "score": p.score,
                "content": p.payload.get("content", ""),
                "source": p.payload.get("source", "unknown"),
                "chunk_id": p.payload.get("chunk_id", None),
                "question": p.payload.get("question", None),
                "answer": p.payload.get("answer", None)
            })
            
        return scored
