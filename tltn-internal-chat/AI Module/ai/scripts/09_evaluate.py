import json
import os
import sys
import re
import csv
import datetime
import asyncio
import math
import nltk
from typing import List, Dict, Any, Tuple, Optional

# --- HOTFIX CHO TRANSFORMERS 4.45+ VÀ PYTHON 3.13 ---
import transformers.utils.import_utils
if not hasattr(transformers.utils.import_utils, 'is_torch_fx_available'):
    transformers.utils.import_utils.is_torch_fx_available = lambda: False
# ---------------------------------------------------

from FlagEmbedding import BGEM3FlagModel
base_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.append(base_dir)

from importlib import import_module
init_pipeline = import_module("08_query_pipeline").init_pipeline
QwenGenerator = import_module("07_qwen_generator").QwenGenerator



def parse_qa_dataset(filepath: str) -> list:
    pairs = []
    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()

    matches1 = re.findall(
        r"(?:Q:|Question:)\s*(.*?)\n(?:A:|Answer:)\s*(.*?)(?=\nQ:|\nQuestion:|$)",
        content,
        re.DOTALL
    )

    matches2 = re.findall(
        r"\*\*Q\d+:\*\*\s*(.*?)\s*\*\*A:\*\*\s*(.*?)(?:\n\*\*ChunkID:\*\*\s*(.*?))?(?=\n\*\*Q\d+:|\Z)",
        content,
        re.DOTALL
    )

    for q, a in matches1:
        pairs.append({
            "question": q.strip(),
            "truth": a.strip(),
            "chunk_id": None
        })
    for q, a, cid in matches2:
        pairs.append({
            "question": q.strip(),
            "truth": a.strip(),
            "chunk_id": cid.strip() if cid else None
        })
    return pairs


class RAGEvaluator:
    def __init__(self, embed_model=None):
        print("Initializing Async RAG Evaluator...")
        self.results = []
        self.embed_model = embed_model
        if self.embed_model is None:
            try:
                from FlagEmbedding import BGEM3FlagModel
                self.embed_model = BGEM3FlagModel('BAAI/bge-m3', use_fp16=True)
                print("✅ BGE-M3 model loaded for semantic similarity.")
            except Exception as e:
                print(f"⚠️ Could not load BGE-M3: {e}")
                self.embed_model = None
                
        # Khởi tạo ma trận chiếu (projection matrix) để nén vector từ 1024 xuống 256 chiều
        # Dùng phương pháp random projection để giảm chiều nhưng giữ nguyên khoảng cách (theo định lý Johnson-Lindenstrauss)
        import numpy as np
        np.random.seed(42)
        self.compress_matrix = np.random.randn(1024, 256) / np.sqrt(256)


    def calculate_retrieval_metrics(self, retrieved_ids: List[str], ground_truth_ids: List[str], k: int) -> Dict[str, float]:
        if not ground_truth_ids:
            return {"recall": 0.0, "precision": 0.0, "hit_rate": 0.0, "mrr": 0.0, "ndcg": 0.0}
        retrieved_k = retrieved_ids[:k]
        relevant_and_retrieved = [rid for rid in retrieved_k if rid in ground_truth_ids]
        recall = len(set(relevant_and_retrieved)) / len(ground_truth_ids)
        precision = len(set(relevant_and_retrieved)) / k if k > 0 else 0.0
        hit_rate = 1.0 if len(relevant_and_retrieved) > 0 else 0.0
        mrr = 0.0
        for i, rid in enumerate(retrieved_k):
            if rid in ground_truth_ids:
                mrr = 1.0 / (i + 1)
                break
        dcg = 0.0
        for i, rid in enumerate(retrieved_k):
            if rid in ground_truth_ids:
                dcg += 1.0 / math.log2(i + 2)
        idcg = 0.0
        for i in range(min(len(ground_truth_ids), k)):
            idcg += 1.0 / math.log2(i + 2)
        ndcg = dcg / idcg if idcg > 0 else 0.0
        return {"recall": recall, "precision": precision, "hit_rate": hit_rate, "mrr": mrr, "ndcg": ndcg}


    def calculate_string_metrics(self, prediction: str, ground_truth: str) -> Dict[str, float]:
        if not ground_truth: return {"precision": 0.0, "recall": 0.0, "f1": 0.0, "em": 0.0}
        try:
            from rouge_score import rouge_scorer
            scorer = rouge_scorer.RougeScorer(['rougeL'], use_stemmer=True)
            scores = scorer.score(ground_truth, prediction)['rougeL']
            rouge_p = scores.precision
            rouge_r = scores.recall
            rouge_f = scores.fmeasure
        except: 
            rouge_p, rouge_r, rouge_f = 0.0, 0.0, 0.0
            
        em = 1.0 if prediction.strip().lower() == ground_truth.strip().lower() else 0.0
        return {"precision": rouge_p, "recall": rouge_r, "f1": rouge_f, "em": em}

    async def calculate_semantic_similarity(self, text1: str, text2: str) -> float:
        if not text1 or not text2 or not self.embed_model: return 0.0
        embeddings = self.embed_model.encode([text1, text2], return_dense=True)
        from sklearn.metrics.pairwise import cosine_similarity
        import numpy as np
        
        vec_1 = embeddings['dense_vecs'][0]
        vec_2 = embeddings['dense_vecs'][1]
        
        # BƯỚC 1 (PolarQuant): Xoay và Nén vector bằng cách nhân với ma trận (Random Projection)
        if hasattr(self, 'compress_matrix') and vec_1.shape[0] == self.compress_matrix.shape[0]:
            vec_1 = np.dot(vec_1, self.compress_matrix)
            vec_2 = np.dot(vec_2, self.compress_matrix)
            
        # BƯỚC 2 (Scalar Quantization): Lượng tử hoá kiểu Float -> Int8
        def quantize_8bit(vec: np.ndarray):
            v_min, v_max = vec.min(), vec.max()
            if v_max == v_min:
                return np.zeros_like(vec, dtype=np.uint8), v_min, v_max
            scale = 255.0 / (v_max - v_min)
            # Ép kiểu giá trị về khoảng [0, 255] dưới dạng số nguyên 8-bit
            quantized = np.clip(np.round((vec - v_min) * scale), 0, 255).astype(np.uint8)
            return quantized, v_min, v_max

        def dequantize_8bit(q_vec: np.ndarray, v_min: float, v_max: float):
            if v_max == v_min:
                return np.full(q_vec.shape, v_min, dtype=np.float32)
            scale = (v_max - v_min) / 255.0
            # Phục hồi giá trị về dạng Float32 để tính khoảng cách
            return (q_vec.astype(np.float32) * scale) + v_min

        # Lượng tử hoá
        q_vec_1, min1, max1 = quantize_8bit(vec_1)
        q_vec_2, min2, max2 = quantize_8bit(vec_2)
        
        # (Ở thực tế: q_vec_1, min1, max1 sẽ được lưu vào Qdrant/Database tiết kiệm 4x RAM)
        
        # Mô phỏng giải lượng tử hoá (Dequantize) khi truy vấn
        deq_vec_1 = dequantize_8bit(q_vec_1, min1, max1)
        deq_vec_2 = dequantize_8bit(q_vec_2, min2, max2)
            
        return float(cosine_similarity(deq_vec_1.reshape(1,-1), deq_vec_2.reshape(1,-1))[0][0])

    async def evaluate_single(self, q: str, a: str, retrieved_docs: list, ground_truth_ids: list = None, expected_answer: str = "", k_val: int = 5):
        if ground_truth_ids is None: ground_truth_ids = []
        r_ids = [doc.get("chunk_id", "") for doc in retrieved_docs]
        ret_metrics = self.calculate_retrieval_metrics(r_ids, ground_truth_ids, k_val)
        str_metrics = self.calculate_string_metrics(a, expected_answer)
        
        print(f"      [Evaluator] -> Bắt đầu tính toán Semantic Similarity...", flush=True)
        semantic_sim = await self.calculate_semantic_similarity(a, expected_answer)
        print(f"      [Evaluator] -> Hoàn thành Semantic Similarity: {semantic_sim:.4f}", flush=True)
        
        res = {
            "question": q, "answer": a,
            "ans_cosine_sim": semantic_sim,
            "accuracy": 1.0 if semantic_sim >= 0.80 else 0.0,
            "precision": str_metrics["precision"],
            "recall": str_metrics["recall"],
            "f1_score": str_metrics["f1"],
            "ctx_recall@k": ret_metrics["recall"], "ctx_precision@k": ret_metrics["precision"],
            "ctx_hit_rate@k": ret_metrics["hit_rate"], "ctx_mrr@k": ret_metrics["mrr"], "ctx_ndcg@k": ret_metrics["ndcg"]
        }
        self.results.append(res)
        return res

    def export(self, csv_filepath: str, log_filepath: str):
        if not self.results: return
        with open(csv_filepath, "w", encoding="utf-8-sig", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=self.results[0].keys())
            writer.writeheader()
            writer.writerows(self.results)
        total = len(self.results)
        def avg(key): return sum(r[key] for r in self.results) / total
        
        # Build Table Terminal
        table_border = "================================================================================"
        term_table = f"\n{table_border}\n"
        term_table += f"{'📊 BẢNG KẾT QUẢ ĐÁNH GIÁ (SEMANTIC SIMILARITY & METRICS) 📊'.center(80)}\n"
        term_table += f"{table_border}\n"
        term_table += f"✅ Đã đánh giá xong: {total} cặp câu hỏi/văn bản.\n"
        term_table += "-" * 80 + "\n"
        term_table += f"| {'TIÊU CHÍ (MATH/STRING)'.ljust(35)} | {'GIÁ TRỊ TRUNG BÌNH'.center(20)} | {'PHẦN TRĂM (%)'.center(15)} |\n"
        term_table += "|" + "-"*37 + "|" + "-"*22 + "|" + "-"*17 + "|\n"
        
        math_mapping = [
            ("Cosine Similarity (Semantic)", avg('ans_cosine_sim')),
            ("Accuracy (Đúng/Sai > 0.8)", avg('accuracy')),
            ("Precision (Tránh Hallucination)", avg('precision')),
            ("Recall (Đủ ý)", avg('recall')),
            ("F1-score (Cân bằng)", avg('f1_score'))
        ]
        
        for name, score in math_mapping:
            percent = score * 100
            term_table += f"| {name.ljust(35)} | {f'{score:.4f} / 1.0'.center(20)} | {f'{percent:.1f}%'.center(15)} |\n"
            
        term_table += f"{table_border}\n"
        
        print(term_table)
        
        with open(log_filepath, "w", encoding="utf-8") as f: f.write(term_table)


async def interactive_eval():
    print("\n" + "="*80)
    print("🤖 CHẾ ĐỘ HỎI ĐÁP & CHẤM ĐIỂM TRỰC TIẾP (INTERACTIVE RAG EVAL) 🤖".center(80))
    print("="*80)
    
    pipeline = init_pipeline()
    evaluator = RAGEvaluator(embed_model=pipeline.retriever.model)
    
    print("\n💡 Gõ câu hỏi của bạn (hoặc 'exit'/ 'quit' để thoát).\n")
    i = 1
    while True:
        try:
            q = input(f"\n[Câu hỏi {i}] >> ").strip()
            if not q:
                continue
            if q.lower() in ['exit', 'quit']:
                print("Đóng chế độ tương tác.")
                break
                
            result = await pipeline.run(q)
            answer = result.get("answer", "")
            
            print("\n=== 🎯 CÂU TRẢ LỜI CỦA AI ===")
            print(answer)
            print("=============================\n")
            
            print(f"      [Evaluator] Đang chuyển sang tính toán Semantic Similarity...")
            used_ctx = result.get('used_chunks', [])
            
            eval_res = await evaluator.evaluate_single(
                q, 
                answer, 
                used_ctx, 
                ground_truth_ids=[], 
                expected_answer=""
            )
            i += 1
            
        except KeyboardInterrupt:
            print("\nĐã thoát.")
            break
        except Exception as e:
            print(f"\nLỗi: {e}")
            break


async def evaluate(limit: Optional[int] = None, dataset_path: Optional[str] = None):
    if dataset_path is None:
        dataset_path = os.path.join(base_dir, "..", "data", "qa", "qa_dataset.md")
    
    timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    report_csv_path = os.path.join(base_dir, "..", "logs", f"eval_results_{timestamp}.csv")
    report_log_path = os.path.join(base_dir, "..", "logs", f"eval_report_{timestamp}.log")
    
    if not os.path.exists(dataset_path):
        print(f"❌ Dataset not found at {dataset_path}")
        return
        
    pairs = parse_qa_dataset(dataset_path)
    if limit:
        pairs = pairs[:limit]
    pipeline = init_pipeline()
    
    evaluator = RAGEvaluator(embed_model=pipeline.retriever.model)

    for i, pair in enumerate(pairs, 1):
        print(f"[{i}/{len(pairs)}] Query: {pair['question']} ...")
        result = await pipeline.run(pair["question"])
        ground_truth_ids = [pair["chunk_id"]] if pair.get("chunk_id") else []
        print(f"      [Log - AI Generator Output]: {result.get('answer', '')}")
        used_ctx = result.get('used_chunks', [])
        if used_ctx:
             print(f"      [Log - Top 1 Context]: {used_ctx[0].get('content', '')[:150]}...")
             
        eval_res = await evaluator.evaluate_single(
            pair["question"], 
            result.get("answer", ""), 
            used_ctx, 
            ground_truth_ids, 
            expected_answer=pair["truth"]
        )
        print(f"\n      [Hoàn thành] Đã chấm xong Câu {i}:")
        print("      " + "-"*65)
        print("      " + f"| {'Tiêu chí (Aspect)'.ljust(30)} | {'Điểm số'.center(12)} |")
        print("      " + "-"*65)
        ans_sim = eval_res.get("ans_cosine_sim", 0)
        acc = eval_res.get("accuracy", 0)
        prec = eval_res.get("precision", 0)
        rec = eval_res.get("recall", 0)
        f1 = eval_res.get("f1_score", 0)
        
        print("      " + f"| {'Cosine Similarity'.ljust(30)} | {f'{ans_sim:.4f}'.center(12)} |")
        print("      " + f"| {'Accuracy (>0.8)'.ljust(30)} | {f'{acc:.4f}'.center(12)} |")
        print("      " + f"| {'Precision'.ljust(30)} | {f'{prec:.4f}'.center(12)} |")
        print("      " + f"| {'Recall'.ljust(30)} | {f'{rec:.4f}'.center(12)} |")
        print("      " + f"| {'F1-score'.ljust(30)} | {f'{f1:.4f}'.center(12)} |")
        print("      " + "-"*65 + "\n")
    evaluator.export(report_csv_path, report_log_path)

async def main():
    import argparse
    parser = argparse.ArgumentParser(description="Advanced RAG Evaluator")
    parser.add_argument("--limit", type=int, default=None, help="Limit number of QA pairs to evaluate")
    parser.add_argument("--dataset", type=str, default=None, help="Path to the dataset file")
    parser.add_argument("--mode", type=str, choices=["auto", "interactive"], default="auto", help="Evaluation mode")
    
    args, unknown = parser.parse_known_args()
    
    if args.mode == "interactive":
        await interactive_eval()
        return

    # Auto mode logic
    await evaluate(limit=args.limit, dataset_path=args.dataset)

if __name__ == "__main__":
    asyncio.run(main())