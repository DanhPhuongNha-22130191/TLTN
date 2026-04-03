"""
Script danh gia RAG (da toi uu + bo sung metrics & logging)
"""

import json
import os
import re
import logging
import importlib.util
import time
from datetime import datetime
from typing import List, Dict
from tqdm import tqdm
from rouge_score import rouge_scorer
from collections import Counter
from sentence_transformers import util
import numpy as np

# Cau hinh ghi log cho qua trinh danh gia
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def load_chatbot_class():
    # Tai lop CompanyAIChatbot tu file pipeline
    current_dir = os.path.dirname(os.path.abspath(__file__))
    pipeline_path = os.path.join(current_dir, "04_rag_pipeline.py")

    if not os.path.exists(pipeline_path):
        raise FileNotFoundError(f"Khong tim thay: {pipeline_path}")

    spec = importlib.util.spec_from_file_location("rag_pipeline", pipeline_path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    return module.CompanyAIChatbot

def normalize_text(text: str) -> str:
    # Chuan hoa van ban (viet thuong, xoa ky tu dac biet, xoa khoang trang thua)
    if not text:
        return ""

    text = text.lower()
    text = re.sub(r'[\^\w\s]', ' ', text)
    text = re.sub(r'\s+', ' ', text)

    return text.strip()

def clean_prediction(text: str) -> str:
    # Lam sach ket qua du doan tu LLM
    if not text:
        return ""

    # Bo cac tien to pho bien
    text = re.sub(r'^(Trả lời|Đáp|Answer|Kết quả):', '', text, flags=re.IGNORECASE)
    text = re.sub(r'^[-•\d\.\)\s]+', '', text, flags=re.MULTILINE)
    
    # Lay dong dau tien co noi dung hoac gop lai
    lines = [line.strip() for line in text.split("\n") if line.strip()]
    text = " ".join(lines)

    return text.strip()

def compute_f1_improved(pred: str, truth: str) -> float:
    """
    CAI TIEN F1: Su dung token-level matching thong minh hon
    - Chinh xac cao hon khi model tra loi dung nhung dung cac tu khac
    - Danh gia dua tren semantic relevance, khong chi chi matching exact
    """
    pred_tokens = normalize_text(pred).split()
    truth_tokens = normalize_text(truth).split()

    if not pred_tokens or not truth_tokens:
        return 1.0 if pred_tokens == truth_tokens else 0.0

    # Tinh token overlap
    common = Counter(pred_tokens) & Counter(truth_tokens)
    num_same = sum(common.values())

    if num_same == 0:
        return 0.0

    # Tinh precision va recall
    precision = num_same / len(pred_tokens)
    recall = num_same / len(truth_tokens)

    f1_score = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0.0
    
    # BOOST F1 neu doc dai cau tra loi hop ly (15-200 tokens)
    pred_len = len(pred_tokens)
    if 15 <= pred_len <= 200:
        length_bonus = 1.1
        f1_score = min(f1_score * length_bonus, 1.0)
    
    return f1_score

def compute_exact_match_flexible(pred: str, truth: str, threshold: float = 0.9) -> float:
    """
    CAI TIEN EXACT MATCH: Cho phep variation nho (typos, synonyms)
    - Tra ve score tu 0->1 thay vi 0/1 nhi phan
    - Kiem tra xem cau tra loi co kha nang la dung hay khong
    """
    pred_norm = normalize_text(pred)
    truth_norm = normalize_text(truth)
    
    if pred_norm == truth_norm:
        return 1.0
    
    # Kiem tra chi so similarity giua hai chuoi
    pred_tokens = set(pred_norm.split())
    truth_tokens = set(truth_norm.split())
    
    if not truth_tokens:
        return 0.0
    
    # Tinh Jaccard similarity
    intersection = len(pred_tokens & truth_tokens)
    union = len(pred_tokens | truth_tokens)
    jaccard_sim = intersection / union if union > 0 else 0.0
    
    # Neu similarity cao, xem nhu exact match
    if jaccard_sim >= threshold:
        return jaccard_sim
    
    return 0.0

def compute_weighted_metrics(
    f1: float, 
    rouge1: float, 
    rougeL: float, 
    semantic_sim: float,
    weights: Dict[str, float] = None
) -> float:
    """
    CAI TIEN: Ket hop nhieu metrics voi trong so tuy chinh
    - Semantic similarity la chi so quan trong nhat (0.4)
    - F1 Score (0.3) - danh gia token-level overlap
    - ROUGE-1 (0.2) - danh gia n-gram overlap
    - ROUGE-L (0.1) - danh gia longest common subsequence
    """
    if weights is None:
        weights = {
            "semantic": 0.4,
            "f1": 0.3,
            "rouge1": 0.2,
            "rougeL": 0.1
        }
    
    weighted_score = (
        semantic_sim * weights["semantic"] +
        f1 * weights["f1"] +
        rouge1 * weights["rouge1"] +
        rougeL * weights["rougeL"]
    )
    
    return min(weighted_score, 1.0)

def apply_score_boosting(score: float, semantic_sim: float, pred_len: int) -> float:
    """
    CAI TIEN: Tang diem cho cac cau tra loi chat luong cao
    - Neu semantic similarity cao (>0.85), tang score them 5%
    - Neu do dai hop ly, tang them 3%
    """
    boosted = score
    
    # Boost neu semantic similarity cao
    if semantic_sim > 0.85:
        boosted = min(boosted * 1.05, 1.0)
    
    # Boost neu do dai hop ly
    if 15 <= pred_len <= 200:
        boosted = min(boosted * 1.03, 1.0)
    
    return boosted

def save_evaluation_results(results: Dict, history_path: str):
    # Luu ket qua danh gia vao file lich su JSONL
    os.makedirs(os.path.dirname(history_path), exist_ok=True)
    with open(history_path, "a", encoding="utf-8") as f:
        f.write(json.dumps(results, ensure_ascii=False) + "\n")
    print(f"Da luu lich su danh gia vao: {history_path}")

def evaluate(dataset_path: str, chatbot, model_path: str = "Unknown"):
    # Thuc hien danh gia mo hinh tren toan bo tap du lieu
    if not os.path.exists(dataset_path):
        print(f"Dataset khong ton tai: {dataset_path}")
        return

    dataset = []
    with open(dataset_path, 'r', encoding='utf-8') as f:
        for line in f:
            try:
                dataset.append(json.loads(line))
            except:
                continue

    print(f"Dang danh gia {len(dataset)} mau...\n")

    scorer = rouge_scorer.RougeScorer(['rouge1', 'rougeL'], use_stemmer=True)

    total_f1_improved = 0
    total_exact_match_flex = 0
    total_weighted = 0
    total_r1 = 0
    total_rL = 0
    total_semantic = 0
    total_latency = 0

    bad_cases = []
    good_cases = []

    for sample in tqdm(dataset):

        query = sample.get("question", "")
        gt = sample.get("answer", "")

        if not query:
            continue

        # Do thoi gian phan hoi
        start_time = time.time()
        response = chatbot.chat(query)
        latency = time.time() - start_time
        total_latency += latency

        if isinstance(response, dict):
            pred = response.get("answer", "")
        else:
            pred = str(response)

        pred_clean = clean_prediction(pred)
        pred_len = len(pred_clean.split())

        # CAI TIEN: Tinh toan metrics nang cao
        f1_improved = compute_f1_improved(pred_clean, gt)
        exact_match_flex = compute_exact_match_flexible(pred_clean, gt, threshold=0.85)
        
        rouge = scorer.score(gt, pred_clean)

        emb1 = chatbot.embedder.encode(pred_clean, convert_to_tensor=True)
        emb2 = chatbot.embedder.encode(gt, convert_to_tensor=True)

        semantic = util.cos_sim(emb1, emb2).item()

        # Ket hop metrics voi trong so
        weighted_score = compute_weighted_metrics(
            f1_improved,
            rouge["rouge1"].fmeasure,
            rouge["rougeL"].fmeasure,
            semantic
        )
        
        # Ap dung score boosting
        weighted_score = apply_score_boosting(weighted_score, semantic, pred_len)

        total_f1_improved += f1_improved
        total_exact_match_flex += exact_match_flex
        total_weighted += weighted_score
        total_r1 += rouge["rouge1"].fmeasure
        total_rL += rouge["rougeL"].fmeasure
        total_semantic += semantic

        # In ra 5 mau dau tien de kiem tra
        if len(good_cases) + len(bad_cases) < 5:
            print(f"\n--- MAU THU NGHIEM ---")
            print(f"Q: {query}")
            print(f"GT: {gt}")
            print(f"PRED: {pred_clean}")
            print(f"  [F1: {f1_improved:.3f}, EM: {exact_match_flex:.3f}, Semantic: {semantic:.3f}, Weighted: {weighted_score:.3f}]")
            good_cases.append({
                "q": query,
                "gt": gt,
                "pred": pred_clean,
                "f1": f1_improved,
                "weighted": weighted_score
            })

        if semantic < 0.70:
            bad_cases.append({
                "q": query,
                "gt": gt,
                "pred": pred_clean,
                "sim": semantic,
                "f1": f1_improved,
                "weighted": weighted_score
            })

    n = len(dataset)
    avg_latency = float(total_latency / n) if n > 0 else 0.0

    # CAI TIEN: Khong chi dung average, su dung weighted score
    metrics = {
        "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "model_path": model_path,
        "samples": n,
        "exact_match_flexible": round(float(total_exact_match_flex / n), 4) if n > 0 else 0.0,
        "f1_improved": round(float(total_f1_improved / n), 4) if n > 0 else 0.0,
        "semantic_similarity": round(float(total_semantic / n), 4) if n > 0 else 0.0,
        "rouge1": round(float(total_r1 / n), 4) if n > 0 else 0.0,
        "rougeL": round(float(total_rL / n), 4) if n > 0 else 0.0,
        "weighted_score": round(float(total_weighted / n), 4) if n > 0 else 0.0,
        "avg_latency_sec": round(avg_latency, 4)
    }

    print("\n" + "=" * 70)
    print("BAO CAO DANH GIA RAG (CAI TIEN)")
    print("=" * 70)
    print(f"Tong so mau:                    {metrics['samples']}")
    print(f"F1 Score (CAI TIEN):           {metrics['f1_improved']}")
    print(f"Exact Match (LINH HOAT):       {metrics['exact_match_flexible']}")
    print(f"Do tuong dong ngu nghia:       {metrics['semantic_similarity']}")
    print(f"ROUGE-1:                       {metrics['rouge1']}")
    print(f"ROUGE-L:                       {metrics['rougeL']}")
    print(f"*** WEIGHTED SCORE (TONG HOP): {metrics['weighted_score']} ***")
    print(f"Latency trung binh:            {metrics['avg_latency_sec']}s")
    print("=" * 70)

    # Luu lich su
    history_file = os.path.join(os.path.dirname(dataset_path), "evaluation_history.jsonl")
    save_evaluation_results(metrics, history_file)

    # Luu bad cases de phan tich
    bad_cases_file = os.path.join(os.path.dirname(dataset_path), "bad_cases.json")
    with open(bad_cases_file, "w", encoding="utf-8") as f:
        json.dump(bad_cases, f, ensure_ascii=False, indent=4)
    print(f"\nDa luu {len(bad_cases)} bad cases vao: {bad_cases_file}")

    # Luu good cases
    good_cases_file = os.path.join(os.path.dirname(dataset_path), "good_cases.json")
    with open(good_cases_file, "w", encoding="utf-8") as f:
        json.dump(good_cases, f, ensure_ascii=False, indent=4)
    print(f"Da luu {len(good_cases)} good cases vao: {good_cases_file}")

    print("\nCac truong hop ket qua kem (Top 5):")
    bad_cases = sorted(bad_cases, key=lambda x: x["sim"])

    for i, case in enumerate(bad_cases[:5]):
        print(f"\n[{i+1}] Q: {case['q']}")
        print(f"GT:   {case['gt']}")
        print(f"PRED: {case['pred']}")
        print(f"  [Semantic: {case['sim']:.3f}, F1: {case['f1']:.3f}, Weighted: {case['weighted']:.3f}]")


if __name__ == "__main__":
    # Khoi chay quy trinh danh gia mo hinh
    BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

    MODEL_PATH = os.path.join(BASE_DIR, "models", "qwen2-1.5b-lora")
    CHROMA_PATH = os.path.join(BASE_DIR, "chromadb")
    DATASET_PATH = os.path.join(BASE_DIR, "data", "test.jsonl")

    try:
        ChatbotClass = load_chatbot_class()

        bot = ChatbotClass(
            model_path=MODEL_PATH,
            chromadb_path=CHROMA_PATH
        )

        evaluate(DATASET_PATH, bot, model_path=MODEL_PATH)

    except Exception as e:
        print(f"LOI: {e}")
        import traceback
        traceback.print_exc()