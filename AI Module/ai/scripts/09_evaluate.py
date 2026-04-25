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

# --- Gemini API Judge (Optional) ---
class GeminiFlashJudge:
    def __init__(self, api_key: str):
        try:
            import google.generativeai as genai
            genai.configure(api_key=api_key)
            self.model = genai.GenerativeModel('gemini-1.5-flash')
            print("🚀 Gemini-1.5-Flash ready for high-quality evaluation!")
        except ImportError:
            print("⚠️ google-generativeai not installed. Pip install it to use Gemini Judge.")
    
    async def generate(self, prompt: str) -> str:
        try:
            # Chạy sync API trong thread để tránh block event loop
            response = await asyncio.to_thread(self.model.generate_content, prompt)
            return response.text
        except Exception as e:
            return f"Error: {e}"



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
    def __init__(self, judge_llm, embed_model=None):
        print("Initializing Async RAG Evaluator...")
        self.judge_llm = judge_llm
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

    def _extract_score(self, text: str) -> float:
        # Tìm con số cuối cùng trong văn bản (thường là điểm sau khi đã giải thích)
        scores = re.findall(r"Điểm:\s*(\d)", text)
        if scores:
            return float(scores[-1])
        
        # Fallback tìm con số bất kỳ
        nums = re.findall(r"(\d)", text)
        if nums:
            return float(nums[-1])
            
        return 3.0  # Mặc định trung bình nếu không tìm thấy điểm

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

    async def evaluate_aspect_accuracy(self, aspect: str, question: str, answer: str, contexts: Optional[List[str]] = None, expected: str = "") -> float:
        combined_context = "\n---\n".join(contexts) if contexts else "N/A"
        aspect_prompts = {
            "context_relevance": f"Tài liệu Ngữ cảnh MỚI NHẤT có chứa phần trọng tâm để trả lời Câu hỏi chưa?",
            "faithfulness": f"Câu trả lời '{answer}' có được chắt lọc tuyệt đối chính xác từ Ngữ cảnh không?",
            "answer_relevance": f"Câu trả lời '{answer}' giải quyết triệt để Câu hỏi chưa?",
            "noise_robustness": f"AI có lọc được đúng thông tin cốt lõi để đưa ra Câu trả lời '{answer}' mà không bị nhiễu không?",
            "negative_rejection": f"AI có từ chối khéo léo và không bịa đặt khi thiếu thông tin không?",
            "information_integration": f"Tổng hợp xâu chuỗi thông tin trong Ngữ cảnh để tạo ra Câu trả lời '{answer}' có mượt mà không?",
            "counterfactual_robustness": f"Câu trả lời '{answer}' có vững vàng trước bất kỳ cạm bẫy nào của Câu hỏi không?"
        }
        
        prompt = f"""Bạn là Giám khảo AI khắt khe. Nhiệm vụ của bạn là chấm điểm chất lượng câu trả lời của một hệ thống RAG.
Hỏi: {question}
Đáp án: {answer}
Ngữ cảnh thu được:
{combined_context[:1000]}

Tiêu chí: {aspect_prompts.get(aspect, "")}

Hãy thực hiện:
1. Đưa ra 1 dòng nhận xét ngắn gọn về tính chính xác và đầy đủ (Lý do).
2. Chấm điểm trên thang từ 1 đến 5 (1: Tệ, 5: Hoàn hảo).

Định dạng trả về BẮT BUỘC:
Lý do: <nhận xét>
Điểm: <con số 1-5>"""
        response = await self.judge_llm.generate(prompt)
        score = self._extract_score(response)
        # Log reasoning to console for transparency
        reasoning = "N/A"
        reason_match = re.search(r"Lý do:\s*(.*)", response)
        if reason_match:
            reasoning = reason_match.group(1).strip()
        print(f"      [Evaluator] {aspect}: {score}/5.0 | {reasoning[:100]}...")
        return float(score)

    def calculate_string_metrics(self, prediction: str, ground_truth: str) -> Dict[str, float]:
        if not ground_truth: return {"bleu": 0.0, "rouge_l": 0.0, "em": 0.0}
        try:
            from nltk.translate.bleu_score import sentence_bleu
            from nltk.tokenize import word_tokenize
            pred_tokens = word_tokenize(prediction.lower())
            truth_tokens = [word_tokenize(ground_truth.lower())]
            bleu = sentence_bleu(truth_tokens, pred_tokens)
        except: bleu = 0.0
        try:
            from rouge_score import rouge_scorer
            scorer = rouge_scorer.RougeScorer(['rougeL'], use_stemmer=True)
            rouge_l = scorer.score(ground_truth, prediction)['rougeL'].fmeasure
        except: rouge_l = 0.0
        em = 1.0 if prediction.strip().lower() == ground_truth.strip().lower() else 0.0
        return {"bleu": bleu, "rouge_l": rouge_l, "em": em}

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
        r_contexts = [doc.get("content", "") for doc in retrieved_docs]
        ret_metrics = self.calculate_retrieval_metrics(r_ids, ground_truth_ids, k_val)
        str_metrics = self.calculate_string_metrics(a, expected_answer)
        print(f"      [Evaluator] -> Bắt đầu quá trình chấm điểm 7 khía cạnh (Chạy AI nhiều lần)...", flush=True)
        semantic_sim = await self.calculate_semantic_similarity(a, expected_answer)
        
        print(f"      [Evaluator] 1/7 - Đang chấm Context Relevance...", flush=True)
        acc_context = await self.evaluate_aspect_accuracy("context_relevance", q, a, r_contexts)
        
        print(f"      [Evaluator] 2/7 - Đang chấm Faithfulness...", flush=True)
        acc_faithfulness = await self.evaluate_aspect_accuracy("faithfulness", q, a, r_contexts)
        
        print(f"      [Evaluator] 3/7 - Đang chấm Answer Relevance...", flush=True)
        acc_answer = await self.evaluate_aspect_accuracy("answer_relevance", q, a, r_contexts, expected_answer)
        
        print(f"      [Evaluator] 4/7 - Đang chấm Noise Robustness...", flush=True)
        acc_noise = await self.evaluate_aspect_accuracy("noise_robustness", q, a, r_contexts)
        
        print(f"      [Evaluator] 5/7 - Đang chấm Negative Rejection...", flush=True)
        acc_neg_reject = await self.evaluate_aspect_accuracy("negative_rejection", q, a, r_contexts)
        
        print(f"      [Evaluator] 6/7 - Đang chấm Information Integration...", flush=True)
        acc_info_integ = await self.evaluate_aspect_accuracy("information_integration", q, a, r_contexts)
        
        print(f"      [Evaluator] 7/7 - Đang chấm Counterfactual Robustness...", flush=True)
        acc_counterfact = await self.evaluate_aspect_accuracy("counterfactual_robustness", q, a, r_contexts)
        print(f"      [Evaluator] -> Đã hoàn thành chấm điểm Record này!", flush=True)
        res = {
            "question": q, "answer": a,
            "ctx_accuracy": acc_context, "ctx_recall@k": ret_metrics["recall"], "ctx_precision@k": ret_metrics["precision"],
            "ctx_hit_rate@k": ret_metrics["hit_rate"], "ctx_mrr@k": ret_metrics["mrr"], "ctx_ndcg@k": ret_metrics["ndcg"],
            "ctx_bleu": str_metrics["bleu"], "ctx_rouge_l": str_metrics["rouge_l"],
            "faith_accuracy": acc_faithfulness, "faith_bleu": str_metrics["bleu"], "faith_rouge_l": str_metrics["rouge_l"],
            "ans_accuracy": acc_answer, "ans_cosine_sim": semantic_sim, "ans_bleu": str_metrics["bleu"], "ans_rouge_l": str_metrics["rouge_l"],
            "noise_accuracy": acc_noise, "noise_precision": ret_metrics["precision"],
            "neg_accuracy": acc_neg_reject, "neg_em": str_metrics["em"],
            "info_accuracy": acc_info_integ,
            "counter_accuracy": acc_counterfact, "counter_r_rate": 0.0
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
        term_table += f"{'📊 BẢNG KẾT QUẢ ĐÁNH GIÁ 7 TIÊU CHÍ (RAG EVALUATOR) 📊'.center(80)}\n"
        term_table += f"{table_border}\n"
        term_table += f"✅ Đã đánh giá xong: {total} cặp câu hỏi/văn bản.\n"
        term_table += "-" * 80 + "\n"
        term_table += f"| {'TIÊU CHÍ ĐÁNH GIÁ (ASPECTS)'.ljust(35)} | {'ĐIỂM TRUNG BÌNH'.center(20)} | {'PHẦN TRĂM (%)'.center(15)} |\n"
        term_table += "|" + "-"*37 + "|" + "-"*22 + "|" + "-"*17 + "|\n"
        
        metrics_mapping = [
            ("Context Relevance", avg('ctx_accuracy')),
            ("Faithfulness", avg('faith_accuracy')),
            ("Answer Relevance", avg('ans_accuracy')),
            ("Noise Robustness", avg('noise_accuracy')),
            ("Negative Rejection", avg('neg_accuracy')),
            ("Information Integration", avg('info_accuracy')),
            ("Counterfactual Robustness", avg('counter_accuracy'))
        ]
        
        for name, score in metrics_mapping:
            percent = (score / 5) * 100
            term_table += f"| {name.ljust(35)} | {f'{score:.2f} / 5.0'.center(20)} | {f'{percent:.1f}%'.center(15)} |\n"
        
        term_table += f"{table_border}\n"
        print(term_table)
        
        with open(log_filepath, "w", encoding="utf-8") as f: f.write(term_table)


async def interactive_eval():
    print("\n" + "="*80)
    print("🤖 CHẾ ĐỘ HỎI ĐÁP & CHẤM ĐIỂM TRỰC TIẾP (INTERACTIVE RAG EVAL) 🤖".center(80))
    print("="*80)
    
    pipeline = init_pipeline()
    evaluator = RAGEvaluator(judge_llm=pipeline.generator, embed_model=pipeline.retriever.model)
    
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
            
            print(f"      [Evaluator] Đang chuyển sang cho AI Giám khảo chấm điểm từng khía cạnh...")
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


async def evaluate(api_judge=None, limit: Optional[int] = None, dataset_path: Optional[str] = None):
    if dataset_path is None:
        dataset_path = os.path.join(base_dir, "..", "data", "qa", "test_qa.md")
    
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
    
    # Use API judge if provided, else fall back to local 1.5B
    judge = api_judge if api_judge else pipeline.generator
    evaluator = RAGEvaluator(judge_llm=judge, embed_model=pipeline.retriever.model)

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
        print("      " + f"| {'Tiêu chí (Aspect)'.ljust(30)} | {'Điểm số'.center(12)} | {'Thang điểm'.center(12)} |")
        print("      " + "-"*65)
        for aspect, key in [("Context Relevance", "ctx_accuracy"), ("Faithfulness", "faith_accuracy"), 
                           ("Answer Relevance", "ans_accuracy"), ("Noise Robustness", "noise_accuracy"),
                           ("Negative Rejection", "neg_accuracy"), ("Information Integration", "info_accuracy"),
                           ("Counterfactual", "counter_accuracy")]:
            print("      " + f"| {aspect.ljust(30)} | {f'{eval_res.get(key, 0):.1f}'.center(12)} | {'5.0'.center(12)} |")
        print("      " + "-"*65 + "\n")
    evaluator.export(report_csv_path, report_log_path)

async def main():
    import argparse
    parser = argparse.ArgumentParser(description="Advanced RAG Evaluator")
    parser.add_argument("--limit", type=int, default=None, help="Limit number of QA pairs to evaluate")
    parser.add_argument("--dataset", type=str, default=None, help="Path to the dataset file")
    parser.add_argument("--mode", type=str, choices=["auto", "interactive"], default="auto", help="Evaluation mode")
    parser.add_argument("--gemini", action="store_true", help="Use Gemini as judge")
    
    args, unknown = parser.parse_known_args()
    
    if args.mode == "interactive":
        await interactive_eval()
        return

    # Auto mode logic
    api_judge = None
    if args.gemini:
        api_key = os.getenv("GEMINI_API_KEY")
        if not api_key:
            print("❌ GEMINI_API_KEY not found in environment.")
            return
        api_judge = GeminiFlashJudge(api_key)
    
    await evaluate(api_judge=api_judge, limit=args.limit, dataset_path=args.dataset)

if __name__ == "__main__":
    asyncio.run(main())