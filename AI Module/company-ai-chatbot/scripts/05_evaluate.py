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
    text = re.sub(r'[^\w\s]', ' ', text)
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


def compute_f1(pred: str, truth: str) -> float:
    # Tinh toan diem F1 giua cau du doan va cau chuan (ground truth)
    pred_tokens = normalize_text(pred).split()
    truth_tokens = normalize_text(truth).split()

    if not pred_tokens or not truth_tokens:
        return 1.0 if pred_tokens == truth_tokens else 0.0

    common = Counter(pred_tokens) & Counter(truth_tokens)
    num_same = sum(common.values())

    if num_same == 0:
        return 0.0

    precision = num_same / len(pred_tokens)
    recall = num_same / len(truth_tokens)

    return 2 * precision * recall / (precision + recall)


def compute_exact_match(pred: str, truth: str) -> bool:
    # Kiem tra xem cau du doan co khop hoan toan voi cau chuan hay khong
    return normalize_text(pred) == normalize_text(truth)


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

    total_f1 = 0
    total_r1 = 0
    total_rL = 0
    total_semantic = 0
    exact_match_count = 0
    total_latency = 0

    bad_cases = []

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

        f1 = compute_f1(pred_clean, gt)
        rouge = scorer.score(gt, pred_clean)

        emb1 = chatbot.embedder.encode(pred_clean, convert_to_tensor=True)
        emb2 = chatbot.embedder.encode(gt, convert_to_tensor=True)

        semantic = util.cos_sim(emb1, emb2).item()

        exact = compute_exact_match(pred_clean, gt)

        total_f1 += f1
        total_r1 += rouge["rouge1"].fmeasure
        total_rL += rouge["rougeL"].fmeasure
        total_semantic += semantic

        if exact:
            exact_match_count += 1

        # In ra 5 mau dau tien de kiem tra nhanh chat luong cau tra loi
        if len(bad_cases) + exact_match_count <= 5:
            print(f"\n--- MAU THU NGHIEM ---")
            print(f"Q: {query}")
            print(f"GT: {gt}")
            print(f"PRED: {pred_clean} (sim={semantic:.2f})")

        if semantic < 0.6:
            bad_cases.append({
                "q": query,
                "gt": gt,
                "pred": pred_clean,
                "sim": semantic
            })

    n = len(dataset)
    avg_latency = float(total_latency / n) if n > 0 else 0.0

    metrics = {
        "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "model_path": model_path,
        "samples": n,
        "exact_match": round(float(exact_match_count / n), 4) if n > 0 else 0.0,
        "semantic_similarity": round(float(total_semantic / n), 4) if n > 0 else 0.0,
        "f1": round(float(total_f1 / n), 4) if n > 0 else 0.0,
        "rouge1": round(float(total_r1 / n), 4) if n > 0 else 0.0,
        "rougeL": round(float(total_rL / n), 4) if n > 0 else 0.0,
        "avg_latency_sec": round(avg_latency, 4)
    }

    print("\n" + "=" * 60)
    print("BAO CAO DANH GIA RAG")
    print("=" * 60)
    print(f"Tong so mau:          {metrics['samples']}")
    print(f"Khop hoan toan (EM):  {metrics['exact_match']}")
    print(f"Do tuong dong ngu nghia: {metrics['semantic_similarity']}")
    print(f"Diem F1:             {metrics['f1']}")
    print(f"ROUGE-1:              {metrics['rouge1']}")
    print(f"ROUGE-L:              {metrics['rougeL']}")
    print(f"Latency trung binh:   {metrics['avg_latency_sec']}s")
    print("=" * 60)

    # Luu lich su
    history_file = os.path.join(os.path.dirname(dataset_path), "evaluation_history.jsonl")
    save_evaluation_results(metrics, history_file)

    # Luu bad cases de phan tich
    bad_cases_file = os.path.join(os.path.dirname(dataset_path), "bad_cases.json")
    with open(bad_cases_file, "w", encoding="utf-8") as f:
        json.dump(bad_cases, f, ensure_ascii=False, indent=4)
    print(f"Da luu {len(bad_cases)} bad cases vao: {bad_cases_file}")

    print("\nCac truong hop ket qua kem (Top 5):")

    bad_cases = sorted(bad_cases, key=lambda x: x["sim"])

    for i, case in enumerate(bad_cases[:5]):
        print(f"\n[{i+1}] Q: {case['q']}")
        print(f"GT:   {case['gt']}")
        print(f"PRED: {case['pred']} (sim={case['sim']:.2f})")


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