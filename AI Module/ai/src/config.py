import os

SRC_DIR = os.path.dirname(os.path.abspath(__file__))
AI_DIR = os.path.dirname(SRC_DIR)

DATA_DIR = os.path.join(AI_DIR, "data")
HANDBOOK_DIR = os.path.join(DATA_DIR, "handbook")
PROCESSED_DIR = os.path.join(DATA_DIR, "processed")
QA_DIR = os.path.join(DATA_DIR, "qa")

CHUNKS_PATH = os.path.join(PROCESSED_DIR, "chunks.jsonl")
QDRANT_DB_PATH = os.path.join(DATA_DIR, "qdrant_db")
LOGS_DIR = os.path.join(AI_DIR, "logs")

COLLECTION_NAME = "handbook_v2"
INDEXING_BATCH_SIZE = 32

TOKENIZER_MODEL_NAME = "keepitreal/vietnamese-sbert"
EMBEDDING_MODEL_NAME = "BAAI/bge-m3"
RERANKER_MODEL_NAME = "BAAI/bge-reranker-base"
GENERATOR_MODEL_NAME = "qwen2:1.5b"


TARGET_TOKENS = 220
MAX_TOKENS = 350
MIN_TOKENS = 30
OVERLAP_SENTENCES = 1
PRESERVE_HEADERS = True

