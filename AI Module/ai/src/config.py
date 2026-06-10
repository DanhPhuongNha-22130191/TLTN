import os

# Đường dẫn gốc
SRC_DIR = os.path.dirname(os.path.abspath(__file__))
AI_DIR = os.path.dirname(SRC_DIR)

# Thư mục dữ liệu
DATA_DIR = os.path.join(AI_DIR, "data")
HANDBOOK_DIR = os.path.join(DATA_DIR, "handbook")
PROCESSED_DIR = os.path.join(DATA_DIR, "processed")
QA_DIR = os.path.join(DATA_DIR, "qa")

# Đường dẫn tệp tin
CHUNKS_PATH = os.path.join(PROCESSED_DIR, "chunks.jsonl")
QDRANT_DB_PATH = os.path.join(DATA_DIR, "qdrant_db")
LOGS_DIR = os.path.join(AI_DIR, "logs")

# Cấu hình Qdrant
COLLECTION_NAME = "handbook_v2"
INDEXING_BATCH_SIZE = 32

# Tên mô hình AI sử dụng
TOKENIZER_MODEL_NAME = "keepitreal/vietnamese-sbert"
EMBEDDING_MODEL_NAME = "BAAI/bge-m3"
RERANKER_MODEL_NAME = "BAAI/bge-reranker-base"
GENERATOR_MODEL_NAME = "qwen2:1.5b"


# Cấu hình cắt nhỏ văn bản (Chunking)
TARGET_TOKENS = 220
MAX_TOKENS = 350
MIN_TOKENS = 30
OVERLAP_SENTENCES = 1
PRESERVE_HEADERS = True

