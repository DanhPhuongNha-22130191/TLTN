import asyncio
import json
import logging
import os
import re
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Dict, List, Tuple

import matplotlib.pyplot as plt
import numpy as np
from matplotlib.patches import FancyBboxPatch
from rouge_score import rouge_scorer
from sentence_transformers import SentenceTransformer, util

# =========================
# FORCE LOCAL / OFFLINE MODE
# =========================

os.environ["HF_HUB_OFFLINE"] = "0"
os.environ["TRANSFORMERS_OFFLINE"] = "0"
os.environ["HF_DATASETS_OFFLINE"] = "0"
os.environ["TOKENIZERS_PARALLELISM"] = "false"

# =========================
# PATH SETUP
# =========================

script_path = Path(__file__).resolve()
workspace_dir = script_path.parent.parent.parent

if str(workspace_dir) not in sys.path:
    sys.path.append(str(workspace_dir))

# =========================
# IMPORT PIPELINE
# =========================

from ai.src.pipeline.rag_pipeline import AsyncQueryPipeline

# =========================
# LOGGING
# =========================

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)

# =========================
# CONSTANTS
# =========================

EXACT_MATCH_THRESHOLD = 1.0
COSINE_THRESHOLD = 0.85
ROUGE_F1_THRESHOLD = 0.90
EMBEDDING_MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2"

# Modern dashboard colors
COLOR_PRIMARY = "#4F46E5"
COLOR_INFO = "#06B6D4"
COLOR_SUCCESS = "#10B981"
COLOR_WARNING = "#F59E0B"
COLOR_DANGER = "#EF4444"
COLOR_TEXT = "#111827"
COLOR_MUTED = "#6B7280"
COLOR_GRID = "#E5E7EB"
COLOR_BACKGROUND = "#FFFFFF"


# =========================
# DATA CLASSES
# =========================


@dataclass
class EvaluationMetrics:
    exact_match: bool
    cosine_similarity: float
    rouge_f1: float
    is_correct: bool

    def to_dict(self) -> Dict:
        return asdict(self)


@dataclass
class EvaluationResult:
    index: int
    question: str
    expected_answer: str
    generated_answer: str
    metrics: EvaluationMetrics
    runtime_ms: float

    def to_dict(self) -> Dict:
        return {
            "index": self.index,
            "question": self.question,
            "expected_answer": self.expected_answer,
            "generated_answer": self.generated_answer,
            "metrics": self.metrics.to_dict(),
            "runtime_ms": self.runtime_ms,
        }


# =========================
# DATASET PARSER
# =========================


def parse_qa_dataset(file_path: Path) -> List[Tuple[str, str]]:
    """
    Parse markdown QA dataset.

    Supported format:

    Câu 1: <question>
    Đáp án: <answer>
    """

    logging.info("Loading dataset: %s", file_path)

    if not file_path.exists():
        raise FileNotFoundError(f"Dataset file not found: {file_path}")

    content = file_path.read_text(encoding="utf-8")

    pattern = re.compile(
        r"Câu\s*\d+\s*:\s*(.*?)"
        r"\n\s*(?:Đáp\s*án|Answer)\s*:\s*(.*?)(?=\n\s*Câu\s*\d+\s*:|\Z)",
        re.DOTALL | re.IGNORECASE,
    )

    matches = pattern.findall(content)
    qa_pairs: List[Tuple[str, str]] = []

    for question, answer in matches:
        question = question.strip()
        answer = answer.strip()

        if question and answer:
            qa_pairs.append((question, answer))

    logging.info("Parsed %d QA pairs", len(qa_pairs))
    return qa_pairs


# =========================
# EVALUATION METRICS
# =========================

embedding_model = SentenceTransformer(EMBEDDING_MODEL_NAME)

rouge = rouge_scorer.RougeScorer(
    ["rouge1"],
    use_stemmer=True,
)


def calculate_exact_match(answer_pred: str, answer_true: str) -> bool:
    """Return True when generated answer exactly matches expected answer."""

    return answer_pred.strip().lower() == answer_true.strip().lower()


def calculate_cosine_similarity(answer_pred: str, answer_true: str) -> float:
    """Calculate semantic similarity using Sentence-BERT embeddings."""

    vector_pred = embedding_model.encode(
        [answer_pred],
        convert_to_tensor=True,
    )

    vector_true = embedding_model.encode(
        [answer_true],
        convert_to_tensor=True,
    )

    cosine_score = util.cos_sim(vector_pred, vector_true)[0][0].item()
    return round(float(cosine_score), 4)


def calculate_rouge_f1(answer_pred: str, answer_true: str) -> float:
    """Calculate ROUGE-1 F1 score."""

    scores = rouge.score(answer_true, answer_pred)
    rouge_f1 = scores["rouge1"].fmeasure
    return round(float(rouge_f1), 4)


def evaluate_answer(answer_pred: str, answer_true: str) -> EvaluationMetrics:
    """Evaluate answer using Exact Match, Cosine Similarity, and ROUGE-1 F1."""

    exact_match = calculate_exact_match(answer_pred, answer_true)
    cosine_sim = calculate_cosine_similarity(answer_pred, answer_true)
    rouge_f1 = calculate_rouge_f1(answer_pred, answer_true)

    is_correct = (
        exact_match
        or cosine_sim >= COSINE_THRESHOLD
        or rouge_f1 >= ROUGE_F1_THRESHOLD
    )

    return EvaluationMetrics(
        exact_match=exact_match,
        cosine_similarity=cosine_sim,
        rouge_f1=rouge_f1,
        is_correct=is_correct,
    )


# =========================
# PIPELINE INFERENCE
# =========================


async def run_inference(
    pipeline: AsyncQueryPipeline,
    question: str,
) -> Tuple[str, float]:
    """Run RAG pipeline and return generated answer with runtime in milliseconds."""

    import time

    start_time = time.time()
    logging.info("Question: %s", question[:80])

    result = await pipeline.run(question)

    if isinstance(result, dict):
        answer = result.get("answer", "")
    else:
        answer = str(result)

    runtime_ms = (time.time() - start_time) * 1000
    return answer.strip(), runtime_ms


# =========================
# SAVE RESULTS
# =========================


def save_results(
    results: List[EvaluationResult],
    output_path: Path,
) -> None:
    """Save detailed evaluation results to JSON."""

    output_path.parent.mkdir(parents=True, exist_ok=True)
    results_dict = [result.to_dict() for result in results]

    with output_path.open("w", encoding="utf-8") as f:
        json.dump(
            results_dict,
            f,
            ensure_ascii=False,
            indent=2,
        )

    logging.info("Saved results -> %s", output_path)


def calculate_summary_values(results: List[EvaluationResult]) -> Dict[str, float]:
    """Calculate reusable summary values for report and charts."""

    n_total = len(results)

    if n_total == 0:
        raise ValueError("Cannot calculate summary because results is empty")

    exact_matches = [result.metrics.exact_match for result in results]
    cosine_scores = [result.metrics.cosine_similarity for result in results]
    rouge_scores = [result.metrics.rouge_f1 for result in results]
    runtimes = [result.runtime_ms for result in results]

    n_correct = sum(1 for result in results if result.metrics.is_correct)
    n_failed = n_total - n_correct

    return {
        "n_total": n_total,
        "n_correct": n_correct,
        "n_failed": n_failed,
        "exact_match_percent": float(np.mean(exact_matches) * 100),
        "cosine_mean_percent": float(np.mean(cosine_scores) * 100),
        "cosine_std": float(np.std(cosine_scores)),
        "rouge_mean_percent": float(np.mean(rouge_scores) * 100),
        "rouge_std": float(np.std(rouge_scores)),
        "overall_accuracy_percent": float(n_correct / n_total * 100),
        "avg_runtime_ms": float(np.mean(runtimes)),
        "min_runtime_ms": float(np.min(runtimes)),
        "max_runtime_ms": float(np.max(runtimes)),
        "n_cosine_passed": sum(1 for score in cosine_scores if score >= COSINE_THRESHOLD),
        "n_rouge_passed": sum(1 for score in rouge_scores if score >= ROUGE_F1_THRESHOLD),
    }


def save_summary_report(
    results: List[EvaluationResult],
    output_path: Path,
) -> None:
    """Save text summary report."""

    output_path.parent.mkdir(parents=True, exist_ok=True)
    summary = calculate_summary_values(results)

    n_total = int(summary["n_total"])
    n_correct = int(summary["n_correct"])

    exact_matches = [result.metrics.exact_match for result in results]
    cosine_scores = [result.metrics.cosine_similarity for result in results]
    rouge_scores = [result.metrics.rouge_f1 for result in results]

    report = f"""
================================================================================
BÁO CÁO ĐÁNH GIÁ MÔ HÌNH RAG - CHUẨN KHOA HỌC
================================================================================

Số lượng mẫu đánh giá: {n_total}

--------------------------------------------------------------------------------
KẾT QUẢ THEO TỪNG METRIC
--------------------------------------------------------------------------------

1. EXACT MATCH (giống tuyệt đối)

   - Kết quả:
     {sum(exact_matches)}/{n_total}
     = {summary["exact_match_percent"]:.1f}%

   - Threshold:
     {EXACT_MATCH_THRESHOLD}

   - Reference:
     Rajpurkar et al., EMNLP 2016

2. COSINE SIMILARITY (semantic similarity)

   - Trung bình:
     {np.mean(cosine_scores):.4f}
     ±
     {np.std(cosine_scores):.4f}

   - Min:
     {np.min(cosine_scores):.4f}

   - Max:
     {np.max(cosine_scores):.4f}

   - Threshold:
     {COSINE_THRESHOLD}

   - Số lượng đạt:
     {int(summary["n_cosine_passed"])}/{n_total}

   - Reference:
     Reimers & Gurevych, EMNLP 2019

3. ROUGE-1 F1 SCORE (token overlap)

   - Trung bình:
     {np.mean(rouge_scores):.4f}
     ±
     {np.std(rouge_scores):.4f}

   - Min:
     {np.min(rouge_scores):.4f}

   - Max:
     {np.max(rouge_scores):.4f}

   - Threshold:
     {ROUGE_F1_THRESHOLD}

   - Số lượng đạt:
     {int(summary["n_rouge_passed"])}/{n_total}

   - Reference:
     Lin, ACL 2004

--------------------------------------------------------------------------------
KẾT QUẢ TỔNG KẾT
--------------------------------------------------------------------------------

Độ chính xác tổng thể:
{n_correct}/{n_total}
=
{summary["overall_accuracy_percent"]:.1f}%

Thời gian trung bình:
{summary["avg_runtime_ms"]:.2f} ms

Thời gian tối thiểu:
{summary["min_runtime_ms"]:.2f} ms

Thời gian tối đa:
{summary["max_runtime_ms"]:.2f} ms

--------------------------------------------------------------------------------
BẢNG TỔNG KẾT
--------------------------------------------------------------------------------

| Metric              | Công thức / Ý nghĩa          | Kết quả              | Ngưỡng |
|---------------------|------------------------------|----------------------|--------|
| Exact Match         | pred == true                 | {summary["exact_match_percent"]:.1f}% | 1.0 |
| Cosine Similarity   | cosine(pred, true)           | {np.mean(cosine_scores):.4f} ± {np.std(cosine_scores):.4f} | {COSINE_THRESHOLD} |
| ROUGE-1 F1          | ROUGE overlap score          | {np.mean(rouge_scores):.4f} ± {np.std(rouge_scores):.4f} | {ROUGE_F1_THRESHOLD} |
| Overall Accuracy    | correct / total              | {summary["overall_accuracy_percent"]:.1f}% | - |

================================================================================
TÀI LIỆU THAM KHẢO
================================================================================

[1] Rajpurkar et al. (2016)
    SQuAD: 100,000+ Questions for Machine Comprehension of Text.
    EMNLP 2016.

[2] Reimers & Gurevych (2019)
    Sentence-BERT: Sentence Embeddings using Siamese BERT-Networks.
    EMNLP 2019.

[3] Lin, C.-Y. (2004)
    ROUGE: A Package for Automatic Evaluation of Summaries.
    ACL 2004.

================================================================================
"""

    with output_path.open("w", encoding="utf-8") as f:
        f.write(report)

    logging.info("Saved summary report -> %s", output_path)


# =========================
# VISUALIZATION
# =========================


def apply_modern_chart_style() -> None:
    """Apply consistent modern dashboard style for matplotlib charts."""

    plt.rcParams.update(
        {
            "figure.facecolor": COLOR_BACKGROUND,
            "axes.facecolor": COLOR_BACKGROUND,
            "axes.edgecolor": COLOR_GRID,
            "axes.labelcolor": COLOR_TEXT,
            "axes.titlecolor": COLOR_TEXT,
            "xtick.color": COLOR_MUTED,
            "ytick.color": COLOR_MUTED,
            "text.color": COLOR_TEXT,
            "font.family": "DejaVu Sans",
            "font.size": 12,
            "axes.titlesize": 24,
            "axes.titleweight": "bold",
            "axes.labelsize": 13,
            "legend.frameon": False,
            "savefig.facecolor": COLOR_BACKGROUND,
            "savefig.edgecolor": COLOR_BACKGROUND,
        }
    )


def add_rounded_bar(
    ax,
    x_center: float,
    height: float,
    width: float,
    color: str,
    radius: float = 0.08,
) -> FancyBboxPatch:
    """Draw a rounded bar using FancyBboxPatch."""

    x_left = x_center - width / 2

    rounded_bar = FancyBboxPatch(
        (x_left, 0),
        width,
        height,
        boxstyle=f"round,pad=0,rounding_size={radius}",
        linewidth=0,
        facecolor=color,
        alpha=0.97,
        zorder=3,
    )

    ax.add_patch(rounded_bar)
    return rounded_bar


def save_bar_chart_metrics(
    results: List[EvaluationResult],
    output_path: Path,
) -> None:
    """
    Save modern rounded bar chart for:
    - Exact Match
    - Cosine Similarity
    - ROUGE-1 F1
    - Overall Accuracy
    """

    apply_modern_chart_style()
    output_path.parent.mkdir(parents=True, exist_ok=True)

    summary = calculate_summary_values(results)

    metrics = [
        "Exact Match",
        "Cosine\nSimilarity",
        "ROUGE-1\nF1",
        "Overall\nAccuracy",
    ]

    scores = [
        summary["exact_match_percent"],
        summary["cosine_mean_percent"],
        summary["rouge_mean_percent"],
        summary["overall_accuracy_percent"],
    ]

    colors = [
        COLOR_PRIMARY,
        COLOR_INFO,
        COLOR_SUCCESS,
        COLOR_WARNING,
    ]

    fig, ax = plt.subplots(figsize=(12, 7.2))

    x_positions = np.arange(len(metrics))
    bar_width = 0.58

    for x, score, color in zip(x_positions, scores, colors):
        add_rounded_bar(
            ax=ax,
            x_center=float(x),
            height=float(score),
            width=bar_width,
            color=color,
            radius=0.10,
        )

        ax.text(
            x,
            score + 2.0,
            f"{score:.1f}%",
            ha="center",
            va="bottom",
            fontsize=17,
            fontweight="bold",
            color=COLOR_TEXT,
            zorder=5,
        )

    ax.set_title(
        "RAG Evaluation Metrics",
        fontsize=26,
        pad=34,
        fontweight="bold",
    )

    ax.text(
        0.5,
        1.025,
        "Performance metrics of the enterprise QA RAG system",
        transform=ax.transAxes,
        ha="center",
        va="bottom",
        fontsize=15,
        color=COLOR_MUTED,
    )

    ax.set_ylabel("Score (%)", labelpad=12, fontsize=14)
    ax.set_ylim(0, 112)

    ax.set_xlim(-0.55, len(metrics) - 0.45)
    ax.set_xticks(x_positions)
    ax.set_xticklabels(metrics, fontsize=14, fontweight="bold", color=COLOR_TEXT)

    ax.set_yticks(np.arange(0, 101, 20))
    ax.tick_params(axis="y", labelsize=12)

    ax.grid(
        axis="y",
        linestyle="--",
        linewidth=0.9,
        color=COLOR_GRID,
        alpha=0.75,
        zorder=0,
    )

    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    ax.spines["left"].set_color(COLOR_GRID)
    ax.spines["bottom"].set_color(COLOR_GRID)

    ax.tick_params(axis="x", length=0)
    ax.margins(y=0.04)

    ax.text(
        0,
        -0.15,
        (
            f"Total samples: {int(summary['n_total'])}  |  "
            f"Correct: {int(summary['n_correct'])}  |  "
            f"Failed: {int(summary['n_failed'])}"
        ),
        transform=ax.transAxes,
        ha="left",
        va="top",
        fontsize=12,
        color=COLOR_MUTED,
    )

    plt.tight_layout()
    fig.savefig(output_path, dpi=300, bbox_inches="tight")
    plt.close(fig)

    logging.info("Saved bar chart -> %s", output_path)


def save_pie_chart_correct_failed(
    results: List[EvaluationResult],
    output_path: Path,
) -> None:
    """
    Save modern donut chart for correct vs failed predictions.
    """

    apply_modern_chart_style()
    output_path.parent.mkdir(parents=True, exist_ok=True)

    summary = calculate_summary_values(results)

    labels = ["Correct", "Failed"]
    values = [
        int(summary["n_correct"]),
        int(summary["n_failed"]),
    ]
    colors = [COLOR_SUCCESS, COLOR_DANGER]

    fig, ax = plt.subplots(figsize=(9.5, 8.5))

    wedges, texts, autotexts = ax.pie(
        values,
        labels=labels,
        autopct=lambda pct: f"{pct:.1f}%" if pct > 0 else "",
        startangle=90,
        counterclock=False,
        colors=colors,
        pctdistance=0.72,
        labeldistance=1.12,
        wedgeprops={
            "width": 0.38,
            "edgecolor": COLOR_BACKGROUND,
            "linewidth": 5,
        },
        textprops={
            "fontsize": 17,
            "fontweight": "bold",
            "color": COLOR_TEXT,
        },
    )

    for text in texts:
        text.set_fontsize(17)
        text.set_fontweight("bold")
        text.set_color(COLOR_TEXT)

    for autotext in autotexts:
        autotext.set_color(COLOR_BACKGROUND)
        autotext.set_fontsize(15)
        autotext.set_fontweight("bold")

    ax.text(
        0,
        0.08,
        f"{summary['overall_accuracy_percent']:.1f}%",
        ha="center",
        va="center",
        fontsize=38,
        fontweight="bold",
        color=COLOR_TEXT,
    )

    ax.text(
        0,
        -0.15,
        "Overall Accuracy",
        ha="center",
        va="center",
        fontsize=15,
        color=COLOR_MUTED,
        fontweight="bold",
    )

    ax.set_title(
        "Prediction Distribution",
        fontsize=26,
        pad=34,
        fontweight="bold",
    )

    ax.text(
        0.5,
        1.025,
        "Correct versus failed predictions during QA evaluation",
        transform=ax.transAxes,
        ha="center",
        va="bottom",
        fontsize=15,
        color=COLOR_MUTED,
    )

    ax.legend(
        wedges,
        [f"Correct: {values[0]}", f"Failed: {values[1]}"],
        loc="lower center",
        bbox_to_anchor=(0.5, -0.10),
        ncol=2,
        fontsize=15,
    )

    ax.set_aspect("equal")

    plt.tight_layout()
    fig.savefig(output_path, dpi=300, bbox_inches="tight")
    plt.close(fig)

    logging.info("Saved pie chart -> %s", output_path)


# =========================
# MAIN
# =========================


async def main() -> None:
    ai_dir = workspace_dir / "ai"

    dataset_path = (
        ai_dir
        / "data"
        / "qa"
        / "qa_dataset_test.md"
    )

    output_json_path = (
        ai_dir
        / "data"
        / "qa"
        / "evaluation_results.json"
    )

    output_report_path = (
        ai_dir
        / "data"
        / "qa"
        / "evaluation_report.txt"
    )

    output_bar_chart_path = (
        ai_dir
        / "data"
        / "qa"
        / "evaluation_metrics_bar_chart.png"
    )

    output_pie_chart_path = (
        ai_dir
        / "data"
        / "qa"
        / "evaluation_correct_failed_pie_chart.png"
    )

    logging.info("%s", "=" * 70)
    logging.info("START RAG EVALUATION")
    logging.info("%s", "=" * 70)

    logging.info(
        "Thresholds: EM=%s, Cosine=%s, ROUGE=%s",
        EXACT_MATCH_THRESHOLD,
        COSINE_THRESHOLD,
        ROUGE_F1_THRESHOLD,
    )

    logging.info("Model: %s", EMBEDDING_MODEL_NAME)
    logging.info("%s", "=" * 70)

    qa_dataset = parse_qa_dataset(dataset_path)

    if len(qa_dataset) == 0:
        raise RuntimeError("Dataset is empty")

    logging.info("Evaluating %d samples", len(qa_dataset))

    pipeline = AsyncQueryPipeline()
    results: List[EvaluationResult] = []

    for index, (question, expected_answer) in enumerate(qa_dataset, 1):
        logging.info("%s", "-" * 70)
        logging.info("Sample [%d/%d]", index, len(qa_dataset))

        try:
            generated_answer, runtime_ms = await run_inference(
                pipeline,
                question,
            )

            logging.info("Expected: %s", expected_answer)
            logging.info("Generated: %s", generated_answer)

            metrics = evaluate_answer(
                generated_answer,
                expected_answer,
            )

            status = "✓" if metrics.is_correct else "✗"

            logging.info("Exact: %s", metrics.exact_match)
            logging.info("Cosine: %.4f", metrics.cosine_similarity)
            logging.info("ROUGE-1 F1: %.4f", metrics.rouge_f1)
            logging.info("Status: %s", status)
            logging.info("Runtime: %.2f ms", runtime_ms)

            result = EvaluationResult(
                index=index,
                question=question,
                expected_answer=expected_answer,
                generated_answer=generated_answer,
                metrics=metrics,
                runtime_ms=runtime_ms,
            )

            results.append(result)

        except Exception as exc:
            logging.exception("Evaluation failed")

            result = EvaluationResult(
                index=index,
                question=question,
                expected_answer=expected_answer,
                generated_answer=f"ERROR: {str(exc)}",
                metrics=EvaluationMetrics(
                    exact_match=False,
                    cosine_similarity=0.0,
                    rouge_f1=0.0,
                    is_correct=False,
                ),
                runtime_ms=0.0,
            )

            results.append(result)

    save_results(results, output_json_path)
    save_summary_report(results, output_report_path)

    save_bar_chart_metrics(results, output_bar_chart_path)
    save_pie_chart_correct_failed(results, output_pie_chart_path)

    logging.info("%s", "=" * 70)
    logging.info("EVALUATION FINISHED")
    logging.info("JSON results: %s", output_json_path)
    logging.info("Report: %s", output_report_path)
    logging.info("Bar chart: %s", output_bar_chart_path)
    logging.info("Pie chart: %s", output_pie_chart_path)
    logging.info("%s", "=" * 70)


if __name__ == "__main__":
    asyncio.run(main())