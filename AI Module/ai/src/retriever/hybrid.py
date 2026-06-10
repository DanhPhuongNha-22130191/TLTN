"""
Module này định nghĩa lớp QdrantRetriever, một bộ truy xuất lai (Hybrid Retriever) nâng cao,
kết hợp giữa tìm kiếm ngữ nghĩa (Dense Search) và tìm kiếm từ khóa (Sparse Search) sử dụng 
mô hình embedding BGE-M3 và cơ sở dữ liệu vector Qdrant. Kết quả của hai phương thức tìm kiếm 
này được tổng hợp và xếp hạng lại bằng thuật toán Reciprocal Rank Fusion (RRF) trực tiếp trong Qdrant.
"""

import os
import asyncio
import logging
import warnings
from typing import List, Dict

# Thư viện kết nối và tương tác với cơ sở dữ liệu vector Qdrant
try:
    from qdrant_client import AsyncQdrantClient
    # Các mô hình dữ liệu cần thiết cho việc cấu hình tìm kiếm lai (Hybrid Search) và RRF trong Qdrant
    from qdrant_client.models import Prefetch, SparseVector, FusionQuery, Fusion
except ImportError:
    AsyncQdrantClient = None
    Prefetch = SparseVector = FusionQuery = Fusion = None

# Cấu hình hệ thống (đường dẫn DB, tên collection, và tên mô hình embedding)
from ai.src.config import QDRANT_DB_PATH, COLLECTION_NAME, EMBEDDING_MODEL_NAME

# Tắt cảnh báo chạy song song từ thư viện Tokenizers để tránh xung đột luồng khi chạy bất đồng bộ
os.environ["TOKENIZERS_PARALLELISM"] = "false"
# Ẩn các cảnh báo không cần thiết và giới hạn log từ thư viện transformers để giữ console sạch sẽ
warnings.filterwarnings("ignore")
logging.getLogger("transformers").setLevel(logging.ERROR)

try:
    # --- HOTFIX CHO TRANSFORMERS V4.45+ VÀ PYTHON 3.13 ---
    # Trong các phiên bản Python 3.13 kết hợp với Transformers 4.45+, hàm is_torch_fx_available
    # có thể bị thiếu hoặc gây lỗi import. Đoạn mã dưới đây kiểm tra và khôi phục hàm này 
    # để đảm bảo FlagEmbedding chạy ổn định mà không ném ngoại lệ Import.
    import transformers.utils.import_utils
    if not hasattr(transformers.utils.import_utils, 'is_torch_fx_available'):
        transformers.utils.import_utils.is_torch_fx_available = lambda: False
    # ---------------------------------------------------
    # BGEM3FlagModel là lớp wrapper từ FlagEmbedding để tải và sử dụng mô hình đa chức năng BGE-M3
        from FlagEmbedding import BGEM3FlagModel
except ImportError:
    BGEM3FlagModel = None
    # Try to provide a lightweight fallback using sentence-transformers (dense only)
    try:
        from sentence_transformers import SentenceTransformer

        class SimpleSTWrapper:
            """Fallback wrapper that mimics BGEM3FlagModel.encode interface for queries.

            It only returns dense vectors and an empty lexical_weights mapping.
            """
            def __init__(self, model_name, use_fp16=False, device='cpu'):
                # device handling delegated to sentence-transformers
                self.model = SentenceTransformer(model_name)

            def encode(self, texts, return_dense=True, return_sparse=False, return_colbert_vecs=False):
                dense = [v.tolist() for v in self.model.encode(texts, convert_to_numpy=True)]
                # mimic keys used in original code
                return {
                    'dense_vecs': dense,
                    'lexical_weights': [{} for _ in texts]
                }

        BGEM3FlagModel = SimpleSTWrapper
    except Exception:
        # leave BGEM3FlagModel as None to trigger clear error later
        BGEM3FlagModel = None

class QdrantRetriever:
    """
    Bộ truy xuất lai nâng cao bất đồng bộ (Async Advanced Hybrid Retriever).
    Thực hiện tìm kiếm kết hợp Dense + Sparse nguyên bản trong Qdrant sử dụng mô hình BGE-M3 
    và thuật toán gộp xếp hạng Reciprocal Rank Fusion (RRF).
    
    Thuộc tính:
        db_path (str): Đường dẫn đến thư mục cơ sở dữ liệu Qdrant (Local DB).
        collection_name (str): Tên của collection chứa các vector tài liệu trong Qdrant.
        client (AsyncQdrantClient): Client bất đồng bộ để giao tiếp với Qdrant DB.
        model (BGEM3FlagModel): Thực thể mô hình BGE-M3 dùng để sinh embeddings (dense & sparse).
    """
    def __init__(self, db_path: str = QDRANT_DB_PATH, collection_name: str = COLLECTION_NAME):
        """
        Khởi tạo QdrantRetriever, thiết lập kết nối Qdrant và tải mô hình embedding BGE-M3 lên GPU.
        
        Args:
            db_path (str): Đường dẫn vật lý của cơ sở dữ liệu Qdrant (mặc định lấy từ config).
            collection_name (str): Tên collection cần truy xuất dữ liệu (mặc định lấy từ config).
        """
        print(f"Loading Async Hybrid Retriever (BGE-M3 + Qdrant collection '{collection_name}')...")
        self.db_path = db_path
        self.collection_name = collection_name
        
        # Kiểm tra xem qdrant-client đã được cài đặt chưa
        if AsyncQdrantClient is None:
            raise ImportError(
                "qdrant_client is not installed. Install dependencies with: pip install -r ai/requirements.txt"
            )

        # Khởi tạo client bất đồng bộ đến cơ sở dữ liệu Qdrant lưu trữ cục bộ
        self.client = AsyncQdrantClient(path=self.db_path)
        
        # Kiểm tra xem BGEM3FlagModel (FlagEmbedding) đã được import thành công chưa
        if BGEM3FlagModel is None:
            raise ImportError(
                "BGEM3FlagModel (FlagEmbedding) is not available. Install or enable the FlagEmbedding wrapper that provides BGEM3FlagModel before running the retriever."
            )

        # Tải mô hình BGE-M3 lên thiết bị CPU (do cấu hình VRAM/RAM hạn chế)
        # Tắt fp16 vì CPU xử lý float32 tốt hơn.
        self.model = BGEM3FlagModel(
            EMBEDDING_MODEL_NAME,
            use_fp16=False,
            device="cpu"
        )
        print("Hybrid Retriever ready!")

    def _encode_query(self, query: str):
        """
        Mã hóa câu truy vấn thô thành vector Dense (nhúng ngữ nghĩa) và vector Sparse (trọng số từ khóa).
        Đây là phương thức đồng bộ (synchronous) vì FlagEmbedding chạy tính toán trên GPU.
        
        Args:
            query (str): Câu hỏi hoặc nội dung cần truy vấn của người dùng.
            
        Returns:
            tuple: Gồm 3 phần tử:
                - dense (list): Danh sách các số thực biểu diễn vector ngữ nghĩa dense.
                - sparse_indices (list of int): Danh sách các chỉ số từ khóa (token IDs) trong từ điển.
                - sparse_values (list of float): Danh sách các trọng số tương ứng của các từ khóa đó.
        """
        # Mã hóa câu hỏi: sinh cả vector dense (return_dense=True) và sparse (return_sparse=True).
        # Không sinh vector ColBERT (return_colbert_vecs=False) để tối ưu hiệu suất và dung lượng.
        embeddings = self.model.encode([query], return_dense=True, return_sparse=True, return_colbert_vecs=False)
        
        # Lấy vector dense đầu tiên trong kết quả và chuyển đổi thành danh sách float thường
        dense = embeddings['dense_vecs'][0]
        if hasattr(dense, 'tolist'):
            dense = dense.tolist()
        else:
            dense = list(dense)

        # Lấy bản đồ trọng số lexical (sparse representation): key là token ID dưới dạng chuỗi, value là trọng số float
        lexical = embeddings['lexical_weights'][0]
        
        # Chuyển đổi các khóa (token ID) sang kiểu int để tương thích với định dạng vector thưa của Qdrant
        sparse_indices = [int(k) for k in lexical.keys()]
        sparse_values = list(lexical.values())
        
        return dense, sparse_indices, sparse_values

    async def search(self, query: str, top_k: int = 15) -> List[Dict]:
        """
        Thực hiện tìm kiếm lai bất đồng bộ (Async Hybrid Search) kết hợp Dense + Sparse.
        Kết quả xếp hạng từ hai phương pháp được hợp nhất thông qua thuật toán RRF trực tiếp trên Qdrant.
        
        Args:
            query (str): Nội dung câu hỏi/truy vấn từ người dùng.
            top_k (int, optional): Số lượng kết quả tốt nhất cần trả về. Mặc định là 15.
            
        Returns:
            List[Dict]: Danh sách các kết quả tìm kiếm được định dạng lại, mỗi phần tử gồm:
                - score (float): Điểm số RRF sau khi hợp nhất.
                - content (str): Nội dung đoạn văn bản được truy xuất.
                - source (str): Nguồn gốc của tài liệu (ví dụ: tên file PDF, URL).
                - chunk_id (int/str/None): ID của phân mảnh tài liệu.
        """
        import torch
        # Giải phóng bộ nhớ đệm CUDA nếu có sẵn GPU để ngăn chặn phân mảnh bộ nhớ và tối ưu hiệu năng
        if torch.cuda.is_available():
            torch.cuda.empty_cache()
            
        # Do việc mã hóa câu truy vấn trên GPU (`_encode_query`) là một tác vụ nặng tính toán 
        # và chạy đồng bộ, ta đẩy nó sang một luồng riêng (thread) bằng `asyncio.to_thread` 
        # để không làm nghẽn (block) event loop chính của ứng dụng ASGI/FastAPI.
        dense, sparse_indices, sparse_values = await asyncio.to_thread(self._encode_query, query)
        
        # Cấu hình Prefetch cho tìm kiếm ngữ nghĩa (Dense Search)
        # Sử dụng không gian vector tên "dense" đã được định nghĩa khi tạo Qdrant Collection.
        dense_prefetch = Prefetch(
            query=dense,
            using="dense",
            limit=top_k
        )
        
        # Cấu hình Prefetch cho tìm kiếm từ khóa (Sparse Search)
        # Sử dụng cấu trúc SparseVector và tìm kiếm trong không gian vector thưa tên "sparse".
        sparse_prefetch = Prefetch(
            query=SparseVector(indices=sparse_indices, values=sparse_values),
            using="sparse",
            limit=top_k
        )
        
        # Gửi truy vấn lai tích hợp RRF đến Qdrant:
        # 1. Qdrant chạy song song hai truy vấn prefetch (dense & sparse), mỗi luồng lấy ra tối đa top_k kết quả.
        # 2. Qdrant áp dụng thuật toán Reciprocal Rank Fusion (RRF) để gộp hai danh sách kết quả lại.
        # 3. Kết quả cuối cùng được lấy ra tối đa `limit` phần tử kèm theo payload (nội dung gốc).
        results = await self.client.query_points(
            collection_name=self.collection_name,
            prefetch=[dense_prefetch, sparse_prefetch],
            query=FusionQuery(fusion=Fusion.RRF),
            limit=top_k,
            with_payload=True
        )
        
        # Định dạng và chuẩn hóa kết quả đầu ra
        scored = []
        for p in results.points:
            scored.append({
                "score": p.score,  # Điểm số xếp hạng tổng hợp (RRF score)
                "content": p.payload.get("content", ""),  # Nội dung văn bản của chunk
                "source": p.payload.get("source", "unknown"),  # Nguồn tài liệu gốc
                "chunk_id": p.payload.get("chunk_id", None),  # Định danh duy nhất của chunk
                "question": p.payload.get("question", None), # Câu hỏi trong bộ QA (nếu có)
                "answer": p.payload.get("answer", None) # Câu trả lời trong bộ QA (nếu có)
            })
            
        return scored

