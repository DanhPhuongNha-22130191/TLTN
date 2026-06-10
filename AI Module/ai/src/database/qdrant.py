import os
import json
import warnings
import logging
from tqdm import tqdm
from qdrant_client import QdrantClient
from qdrant_client.models import VectorParams, Distance, PointStruct, SparseVectorParams, SparseVector

from ai.src.config import (
    QDRANT_DB_PATH, COLLECTION_NAME, INDEXING_BATCH_SIZE, EMBEDDING_MODEL_NAME, CHUNKS_PATH
)

# Setup model warnings suppression
os.environ["TOKENIZERS_PARALLELISM"] = "false"
warnings.filterwarnings("ignore")
logging.getLogger("transformers").setLevel(logging.ERROR)

class QdrantIndexer:
    def __init__(self, db_path: str = QDRANT_DB_PATH, collection_name: str = COLLECTION_NAME):
        self.db_path = db_path
        self.collection_name = collection_name
        self.client = None
        self.model = None

    def connect(self):
        """Kết nối tới Qdrant client."""
        if not self.client:
            print(f"Connecting to Qdrant Database at '{self.db_path}'...")
            self.client = QdrantClient(path=self.db_path)
        return self.client

    def load_embedding_model(self):
        """Nạp chậm (lazy load) mô hình BGE-M3 để sinh hybrid embeddings."""
        if not self.model:
            print(f"Loading {EMBEDDING_MODEL_NAME} Model for Hybrid Indexing...")
            try:
                from FlagEmbedding import BGEM3FlagModel
                self.model = BGEM3FlagModel(EMBEDDING_MODEL_NAME, use_fp16=False)
            except ImportError:
                try:
                    from sentence_transformers import SentenceTransformer

                    class SimpleSTWrapper:
                        def __init__(self, model_name, use_fp16=False):
                            try:
                                self.model = SentenceTransformer(model_name)
                            except Exception:
                                print(
                                    "Unable to load configured model; falling back to sentence-transformers/all-MiniLM-L6-v2"
                                )
                                self.model = SentenceTransformer("sentence-transformers/all-MiniLM-L6-v2")

                        def encode(self, texts, return_dense=True, return_sparse=False, return_colbert_vecs=False):
                            dense_values = self.model.encode(texts, convert_to_numpy=True)
                            if hasattr(dense_values[0], 'tolist'):
                                dense_values = [v.tolist() for v in dense_values]
                            else:
                                dense_values = dense_values.tolist()
                            return {
                                "dense_vecs": dense_values,
                                "lexical_weights": [{} for _ in texts],
                            }

                    self.model = SimpleSTWrapper(EMBEDDING_MODEL_NAME)
                except ImportError:
                    raise ImportError(
                        "Cannot import BGEM3FlagModel or sentence_transformers. Install with: pip install -r ai/requirements.txt"
                    )
        return self.model

    def create_hybrid_collection(self, force_recreate: bool = True):
        """Khởi tạo hoặc tạo lại collection hybrid dense + sparse trong Qdrant."""
        client = self.connect()
        
        if client.collection_exists(self.collection_name) and force_recreate:
            print(f"️ Deleting old collection: {self.collection_name}")
            client.delete_collection(self.collection_name)

        # Tìm kiếm Hybrid yêu cầu cấu hình cho cả 2 loại vector: 'dense' và 'sparse'
        client.create_collection(
            collection_name=self.collection_name,
            vectors_config={
                "dense": VectorParams(size=1024, distance=Distance.COSINE)
            },
            sparse_vectors_config={
                "sparse": SparseVectorParams()
            }
        )
        print(f"Created hybrid collection '{self.collection_name}' successfully!")

    def load_chunks_from_jsonl(self, file_path: str = CHUNKS_PATH) -> list:
        """Đọc các đoạn văn bản (chunks) đã xử lý từ file JSONL."""
        chunks = []
        if not os.path.exists(file_path):
            print(f"Missing chunks file: {file_path}")
            return chunks

        with open(file_path, "r", encoding="utf-8") as f:
            for line in f:
                if line.strip():
                    chunks.append(json.loads(line))
        return chunks

    def index_chunks(self, file_path: str = CHUNKS_PATH, batch_size: int = INDEXING_BATCH_SIZE):
        """Sinh hybrid embeddings và lập chỉ mục (index) toàn bộ chunks vào Qdrant."""
        self.connect()
        self.load_embedding_model()
        self.create_hybrid_collection(force_recreate=True)

        chunks = self.load_chunks_from_jsonl(file_path)
        if not chunks:
            print("️ No chunks found for indexing.")
            return

        print(f"Starting indexing pipeline for {len(chunks)} chunks...")
        
        for i in tqdm(range(0, len(chunks), batch_size), desc="Upserting Batches"):
            batch = chunks[i:i + batch_size]
            
            texts = []
            valid_chunks = []

            for c in batch:
                text = c.get("text") or c.get("content")
                if not text:
                    continue
                texts.append(text)
                valid_chunks.append(c)

            if not texts:
                continue

            # Trích xuất biểu diễn dense (nhúng dày) và sparse (nhúng thưa)
            embeddings = self.model.encode(
                texts, return_dense=True, return_sparse=True, return_colbert_vecs=False
            )
            dense_vecs = embeddings['dense_vecs']
            lexical_weights = embeddings['lexical_weights']

            points = []
            for idx, chunk in enumerate(valid_chunks):
                # Phân tích cú pháp từ điển sparse: {'token_id': weight, ...}
                sparse_dict = lexical_weights[idx]
                indices = [int(k) for k in sparse_dict.keys()]
                values = list(sparse_dict.values())

                chunk_id = chunk.get("chunk_id", str(hash(texts[idx])))
                metadata = chunk.get("metadata", {})
                source = metadata.get("source", chunk.get("source", "unknown"))

                dense_vector = dense_vecs[idx]
                if hasattr(dense_vector, "tolist"):
                    dense_vector = dense_vector.tolist()
                else:
                    dense_vector = list(dense_vector)

                question = chunk.get("question")
                answer = chunk.get("answer")
                text = texts[idx]
                
                if not question and not answer:
                    import re
                    pattern = r"Câu\s*\d+\s*:\s*(.*?)\n\s*(?:Đáp\s*án|Answer)\s*:\s*(.*)"
                    match = re.search(pattern, text, re.DOTALL | re.IGNORECASE)
                    if match:
                        question = match.group(1).strip()
                        answer = match.group(2).strip()

                points.append(
                    PointStruct(
                        id=chunk_id,
                        vector={
                            "dense": dense_vector,
                            "sparse": SparseVector(indices=indices, values=values)
                        },
                        payload={
                            "chunk_id": chunk_id,
                            "content": text,
                            "source": source,
                            "question": question,
                            "answer": answer,
                            "metadata": metadata
                        }
                    )
                )

            self.client.upsert(
                collection_name=self.collection_name,
                points=points
            )

        print(f"Successfully indexed {len(chunks)} hybrid chunks into collection '{self.collection_name}'")
