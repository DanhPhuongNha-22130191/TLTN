import re
import math
from typing import List, Dict, Optional, Any

def clean_text(text: str) -> str:
    """Lowercases, removes punctuation, and normalizes whitespaces."""
    if not text:
        return ""
    text = text.lower()
    # Replace punctuation with spaces
    text = re.sub(r'[^\w\s\s_]', ' ', text)
    # Normalize spaces
    return re.sub(r'\s+', ' ', text).strip()

def tokenize(text: str) -> List[str]:
    """Tokenizes text into lowercase words."""
    cleaned = clean_text(text)
    if not cleaned:
        return []
    return cleaned.split()

def split_sentences(text: str) -> List[str]:
    """Splits text into sentences based on punctuation."""
    if not text:
        return []
    # Split on sentence boundaries (. ! ?) followed by space or end of string
    raw_sentences = re.split(r'(?<=[.!?])\s+', text)
    return [s.strip() for s in raw_sentences if s.strip()]

def compute_lcs(x: List[str], y: List[str]) -> int:
    """Computes the length of the Longest Common Subsequence between two lists of tokens.
    
    Uses space-optimized O(min(N, M)) dynamic programming.
    """
    n, m = len(x), len(y)
    if n == 0 or m == 0:
        return 0
    # Ensure y is the shorter list to optimize space complexity
    if n < m:
        x, y = y, x
        n, m = m, n
    dp = [0] * (m + 1)
    for i in range(1, n + 1):
        prev = 0
        for j in range(1, m + 1):
            temp = dp[j]
            if x[i-1] == y[j-1]:
                dp[j] = prev + 1
            else:
                dp[j] = max(dp[j], dp[j-1])
            prev = temp
    return dp[m]

def compute_rouge_l(prediction: str, ground_truth: str) -> Dict[str, float]:
    """Computes ROUGE-L Precision, Recall, and F1 scores."""
    x = tokenize(ground_truth)
    y = tokenize(prediction)
    if not x or not y:
        return {"precision": 0.0, "recall": 0.0, "f1": 0.0}
    lcs_len = compute_lcs(x, y)
    precision = lcs_len / len(y)
    recall = lcs_len / len(x)
    f1 = (2 * precision * recall) / (precision + recall) if (precision + recall) > 0 else 0.0
    return {"precision": precision, "recall": recall, "f1": f1}


class SimpleTFIDF:
    """A lightweight TF-IDF Vectorizer and similarity search index."""
    def __init__(self):
        self.idf: Dict[str, float] = {}
        self.vocab: set = set()
        
    def fit(self, docs: List[List[str]]):
        """Fits the vocabulary and computes IDF values."""
        n_docs = len(docs)
        if n_docs == 0:
            return
        doc_counts = {}
        for doc in docs:
            unique_words = set(doc)
            for word in unique_words:
                doc_counts[word] = doc_counts.get(word, 0) + 1
        for word, count in doc_counts.items():
            self.idf[word] = math.log((1 + n_docs) / (1 + count)) + 1
            self.vocab.add(word)
            
    def get_vector(self, doc: List[str]) -> Dict[str, float]:
        """Computes TF-IDF vector representing a document."""
        tf = {}
        for word in doc:
            tf[word] = tf.get(word, 0) + 1
        doc_len = len(doc)
        vector = {}
        if doc_len == 0:
            return vector
        for word, count in tf.items():
            if word in self.idf:
                vector[word] = (count / doc_len) * self.idf[word]
        return vector

def cosine_similarity(v1: Dict[str, float], v2: Dict[str, float]) -> float:
    """Computes cosine similarity between two sparse TF-IDF vectors."""
    dot_product = 0.0
    for word, val in v1.items():
        if word in v2:
            dot_product += val * v2[word]
    sum1 = sum(val * val for val in v1.values())
    sum2 = sum(val * val for val in v2.values())
    if sum1 == 0 or sum2 == 0:
        return 0.0
    return dot_product / (math.sqrt(sum1) * math.sqrt(sum2))
