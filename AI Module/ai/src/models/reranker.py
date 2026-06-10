import os
import asyncio
import warnings
import logging
import numpy as np
try:
    from sentence_transformers import CrossEncoder
except ImportError:
    CrossEncoder = None
from ai.src.config import RERANKER_MODEL_NAME

# Tắt cảnh báo phiền phức từ các thư viện model
os.environ["TOKENIZERS_PARALLELISM"] = "false"
warnings.filterwarnings("ignore")
logging.getLogger("transformers").setLevel(logging.ERROR)

class Reranker:
    def __init__(self, model_name: str = RERANKER_MODEL_NAME):
        if CrossEncoder is None:
            raise ImportError(
                "sentence-transformers is not installed. Install with: pip install -r ai/requirements.txt"
            )
        print(f"Đang tải mô hình Reranker '{model_name}' trên GPU (FP16)...")
        import torch
        self.model = CrossEncoder(
            model_name, 
            device="cuda" if torch.cuda.is_available() else "cpu", 
            model_kwargs={"torch_dtype": torch.float16} if torch.cuda.is_available() else {}
        )

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
        Đánh giá lại độ liên quan (Rerank) bất đồng bộ của danh sách chunks dựa trên câu truy vấn.
        Chuyển tác vụ tính toán CrossEncoder (nặng về CPU/GPU) sang chạy trên Thread Pool để tránh làm tắc nghẽn event loop chính.
        """
        return await asyncio.to_thread(self._sync_rerank, query, chunks, top_k)

    def _sync_check_qa(self, query: str, qa_chunks: list, threshold: float):
        if not qa_chunks:
            return None
            
        pairs = [(query, c.get("question", "")) for c in qa_chunks]
        scores = self.model.predict(pairs)
        scores = np.array(scores).reshape(-1).tolist()
        
        best_idx = int(np.argmax(scores))
        best_score = float(scores[best_idx])
        
        if best_score >= threshold:
            best_chunk = qa_chunks[best_idx].copy()
            best_chunk["rerank_score"] = best_score
            best_chunk["score"] = best_score
            return best_chunk
        return None

    async def check_qa_match(self, query: str, qa_chunks: list, threshold: float = 1.0):
        """
        Kiểm tra xem câu hỏi của người dùng có trùng khớp/gần giống với câu hỏi nào trong tập QA không.
        Sử dụng CrossEncoder để đánh giá mức độ tương đồng giữa hai câu hỏi.
        """
        return await asyncio.to_thread(self._sync_check_qa, query, qa_chunks, threshold)

