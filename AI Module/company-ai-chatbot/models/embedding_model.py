import torch
from sentence_transformers import SentenceTransformer
from typing import List, Dict
from core.logger import setup_logger
from config.settings import EMBEDDING_MODEL, DEVICE

logger = setup_logger(__name__)

class Embedder:
    # Lop thuc hien viec tao vector embedding cho van ban

    def __init__(self, model_name: str = EMBEDDING_MODEL, device: str = DEVICE):
        # Khoi tao mo hinh embedding voi thiet bi tinh toan phu hop
        logger.info(f"Dang tai mo hinh embedding: {model_name}")
        
        if device == "cuda" and not torch.cuda.is_available():
            device = "cpu"
            logger.warning("CUDA khong kha dung, su dung CPU")
        
        self.device = device
        self.model = SentenceTransformer(model_name, device=device)
        logger.info(f"Mo hinh tai thanh cong (Device: {device})")
    
    def embedding_chunks(self, chunks: List[Dict]) -> List[Dict]:
        # Tao embedding cho mot danh sach cac doan van ban
        logger.info(f"Dang tao embedding cho {len(chunks)} chunks...")
        
        texts = [c["content"] for c in chunks]
        
        embeddings = self.model.encode(
            texts,
            batch_size=32,
            show_progress_bar=True,
            convert_to_tensor=False
        )
        
        for i, emb in enumerate(embeddings):
            chunks[i]["embedding"] = emb.tolist()
        
        logger.info("Hoan thanh tao embedding")
        return chunks
    
    def embedding_query(self, query: str) -> List[float]:
        # Tao vector embedding cho mot cau truy van duy nhat
        embedding = self.model.encode([query], convert_to_tensor=False)[0]
        return embedding.tolist()
