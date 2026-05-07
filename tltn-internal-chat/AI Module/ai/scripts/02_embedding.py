import json
import os
import warnings
import logging
from tqdm import tqdm

from qdrant_client import QdrantClient
from qdrant_client.models import VectorParams, Distance, PointStruct, SparseVectorParams, SparseVector

try:
    from FlagEmbedding import BGEM3FlagModel
except ImportError:
    print("❌ Cannot import BGEM3FlagModel. Please run: pip install FlagEmbedding")
    exit(1)

# ====== ENV CLEANUP ======
os.environ["TOKENIZERS_PARALLELISM"] = "false"
warnings.filterwarnings("ignore")
logging.getLogger("transformers").setLevel(logging.ERROR)

# ====== CONFIG ======
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CHUNKS_PATH = os.path.join(BASE_DIR, "..", "data", "processed", "chunks.jsonl")
QDRANT_PATH = os.path.join(BASE_DIR, "..", "data", "qdrant_db")

COLLECTION_NAME = "handbook_v2"
BATCH_SIZE = 32

def load_chunks(path):
    chunks = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                chunks.append(json.loads(line))
    return chunks

def main():
    print("🚀 Starting Qdrant Hybrid Indexing Pipeline (Dense + Sparse)...")

    if not os.path.exists(CHUNKS_PATH):
        print(f"❌ Missing file: {CHUNKS_PATH}")
        return

    print("📖 Loading chunks...")
    chunks = load_chunks(CHUNKS_PATH)
    print(f"📦 Total chunks: {len(chunks)}")

    print("🧠 Loading BGE-M3 Model for Hybrid Search...")
    # BGE-M3 produces dense vecs, sparse (lexical) weights, and multi-vector representations
    model = BGEM3FlagModel('BAAI/bge-m3', use_fp16=False)

    print("🔌 Connecting to Qdrant...")
    client = QdrantClient(path=QDRANT_PATH)

    if client.collection_exists(COLLECTION_NAME):
        print(f"⚠️ Deleting old collection: {COLLECTION_NAME}")
        client.delete_collection(COLLECTION_NAME)

    # Hybrid Search requires 2 vector configs: 'dense' and 'sparse'
    client.create_collection(
        collection_name=COLLECTION_NAME,
        vectors_config={
            "dense": VectorParams(size=1024, distance=Distance.COSINE)
        },
        sparse_vectors_config={
            "sparse": SparseVectorParams()
        }
    )

    print("⚡ Indexing into Qdrant...")

    for i in tqdm(range(0, len(chunks), BATCH_SIZE), desc="Upserting Batches"):
        batch = chunks[i:i + BATCH_SIZE]
        
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

        # Extract hybrid representations
        embeddings = model.encode(texts, return_dense=True, return_sparse=True, return_colbert_vecs=False)
        dense_vecs = embeddings['dense_vecs']
        lexical_weights = embeddings['lexical_weights']

        points = []
        for idx, chunk in enumerate(valid_chunks):
            # Parse sparse dictionary: {'token_id': weight, ...}
            sparse_dict = lexical_weights[idx]
            indices = [int(k) for k in sparse_dict.keys()]
            values = list(sparse_dict.values())

            chunk_id = chunk.get("chunk_id", str(hash(texts[idx])))
            metadata = chunk.get("metadata", {})
            source = metadata.get("source", chunk.get("source", "unknown"))

            points.append(
                PointStruct(
                    id=chunk_id,
                    vector={
                        "dense": dense_vecs[idx].tolist(),
                        "sparse": SparseVector(indices=indices, values=values)
                    },
                    payload={
                        "chunk_id": chunk_id,
                        "content": texts[idx],
                        "source": source,
                    }
                )
            )

        client.upsert(
            collection_name=COLLECTION_NAME,
            points=points
        )

    print(f"✅ Indexed {len(chunks)} hybrid chunks into Qdrant '{COLLECTION_NAME}'")

if __name__ == "__main__":
    main()