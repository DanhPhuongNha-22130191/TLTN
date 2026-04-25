# TÀI LIỆU KỸ THUẬT DỰ ÁN: AI MODULE (ADVANCED RAG)

## 1. Kết quả Đánh giá Hệ thống (Preliminary Evaluation)

Dưới đây là kết quả đánh giá hiệu năng của hệ thống RAG hiện tại. 

> [!NOTE]
> **Thông tin về tập dữ liệu (Dataset):**
> Hệ thống hiện có bộ dữ liệu gồm **50 câu hỏi mẫu (QA Pairs)**. Báo cáo này dựa trên kết quả đánh giá chi tiết của **10 câu hỏi tiêu biểu** (tập `test_qa.md`).

| TIÊU CHÍ ĐÁNH GIÁ (ASPECTS) | ĐIỂM TRUNG BÌNH | PHẦN TRĂM (%) |
| :--- | :---: | :---: |
| **Context Relevance** (Độ liên quan ngữ cảnh) | 3.70 / 5.0 | 74.0% |
| **Faithfulness** (Tính trung thực/không bịa đặt) | 4.50 / 5.0 | 90.0% |
| **Answer Relevance** (Độ liên quan câu trả lời) | 4.00 / 5.0 | 80.0% |
| **Noise Robustness** (Khả năng chống nhiễu) | 4.50 / 5.0 | 90.0% |
| **Negative Rejection** (Từ chối khéo khi thiếu thông tin) | 4.10 / 5.0 | 82.0% |
| **Information Integration** (Khả năng tổng hợp thông tin) | 3.90 / 5.0 | 78.0% |
| **Counterfactual Robustness** (Khả năng chống bẫy thông tin) | 4.40 / 5.0 | 88.0% |

---

## 2. Tổng quan Dự án

### 2.1. Giới thiệu chung
Dự án **AI Module** là một hệ thống **Advanced RAG (Retrieval-Augmented Generation)** chuyên biệt nhằm xử lý và truy vấn thông tin từ **Sổ tay Công ty (Company Handbook)**. Hệ thống cho phép người dùng hỏi đáp về các quy định, chính sách và thông tin nội bộ một cách tự động với độ chính xác cao.

### 2.2. Mục tiêu Giải quyết
*   Giảm tải cho bộ phận HR/Admin trong việc trả lời các câu hỏi lặp đi lặp lại.
*   Cung cấp thông tin chính xác 100% dựa trên tài liệu gốc, tránh tình trạng LLM bị "ảo giác" (hallucination).
*   Đảm bảo câu trả lời ngắn gọn, súc tích và hoàn toàn bằng tiếng Việt.

### 2.3. Người dùng Mục tiêu
*   Nhân viên mới cần tìm hiểu văn hóa và quy định công ty (Onboarding).
*   Nhân viên hiện tại cần tra cứu nhanh các chính sách phúc lợi, nghỉ phép, quy trình nghiệp vụ.

---

## 3. Kiến trúc Hệ thống

### 3.1. Mô hình Tổng thể
Hệ thống được thiết kế theo kiến trúc **Advanced RAG Pipeline**, gồm 4 giai đoạn chính:
1.  **Data Ingestion**: Tiền xử lý tài liệu Markdown/PDF thành các đoạn văn (chunks) có tính định danh duy nhất.
2.  **Hybrid Retrieval**: Kết hợp tìm kiếm ngữ nghĩa (Semantic) và tìm kiếm từ khóa (Lexical) trên Vector Database.
3.  **Reranking**: Sử dụng mô hình Cross-Encoder để xếp hạng lại độ liên quan của các đoạn văn retrieved được, tăng độ nhiễu và độ chính xác cho ngữ cảnh.
4.  **Generation**: LLM (Qwen2.5) sinh câu trả lời dựa trên ngữ cảnh đã được tinh lọc.

### 3.2. Luồng Dữ liệu (Data Flow)
`User Query` → `Embedding Model (BGE-M3)` → `Qdrant (Dense + Sparse Search)` → `Reranker (BGE-Reranker-v2)` → `Prompt Construction` → `Qwen LLM` → `Final Answer`.

---

## 4. Công nghệ Sử dụng

| Công nghệ / Thư viện | Vai trò |
| :--- | :--- |
| **BGE-M3 (FlagEmbedding)** | Tạo vector nhúng (embeddings) cho Hybrid Search (Dense & Sparse). |
| **Qdrant** | Vector Database lưu trữ và truy vấn tốc độ cao (Native RRF fusion). |
| **BGE-Reranker-v2-M3** | Cross-Encoder xếp hạng lại các ứng viên, nâng cao độ chính xác Top-K. |
| **Qwen2.5-1.5B/3B-Instruct** | Large Language Model (LLM) dùng để sinh câu trả lời tiếng Việt. |
| **FastAPI / Uvicorn** | Cung cấp Restful API cho ứng dụng frontend/client. |
| **Underthesea** | Xử lý ngôn ngữ tự nhiên tiếng Việt (Sentence Tokenization). |
| **Transformers / BitsAndbytes** | Quantization (4-bit/FP16) để chạy mô hình tối ưu trên GPU 4GB VRAM. |

---

## 5. Chức năng Chính

1.  **Xử lý dữ liệu thông minh**: Tự động phân đoạn tài liệu Markdown theo Header, giữ nguyên ngữ cảnh phân cấp và tạo định danh (chunk_id) theo mã hash nội dung.
2.  **Tìm kiếm lai (Hybrid Search)**: Kết hợp sức mạnh của Vector Search (hiểu ý nghĩa) và BM25-like Search (khớp từ khóa chính xác), tự động gộp kết quả bằng Reciprocal Rank Fusion (RRF).
3.  **Tái xếp hạng (Reranking)**: Lọc bỏ các đoạn văn ít liên quan nhất, chỉ giữ lại Top 3-5 đoạn thực sự giá trị để đưa vào Prompt, giúp tiết kiệm Token và tránh làm nhiễu LLM.
4.  **Sinh câu trả lời định hướng doanh nghiệp**: Prompt được thiết kế khắt khe để LLM chỉ được dùng ngữ cảnh cung cấp, không bịa đặt, và trích dẫn mã tài liệu rõ ràng.
5.  **Hệ thống Đánh giá chuyên sâu (RAG Evaluator)**: Framework chấm điểm tự động dựa trên 7 tiêu chí chất lượng, hỗ trợ cả Local Judge và Gemini API Judge.

---

## 6. Cấu trúc Source Code

```text
AI Module/
├── ai/
│   ├── data/               # Chứa tài liệu gốc và dữ liệu đã xử lý
│   │   ├── handbook/       # Tài liệu Sổ tay (Markdown/PDF)
│   │   ├── processed/      # Dữ liệu chunked (chunks.jsonl)
│   │   └── qa/             # Bộ dữ liệu câu hỏi kiểm thử (test_qa.md)
│   ├── logs/               # Nhật ký truy vấn và báo cáo đánh giá
│   └── scripts/            # Các module xử lý logic chính
│       ├── 01_prepare_data.py   # Load & Chunking tài liệu
│       ├── 02_embedding.py      # Đẩy dữ liệu vào Qdrant
│       ├── 05_retriever.py      # Logic Hybrid Search
│       ├── 06_reranker.py       # Logic Cross-Encoder Rerank
│       ├── 07_qwen_generator.py # Tương tác với LLM
│       ├── 08_query_pipeline.py # Tích hợp Pipeline & FastAPI
│       └── 09_evaluate.py       # Khung đánh giá 7 tiêu chí
└── data/
    └── qdrant_db/          # File dữ liệu vật lý của Vector DB
```

---

## 7. Luồng xử lý một Request (Core Flow)

1.  **Tiếp nhận**: API tiếp nhận `query` từ người dùng.
2.  **Truy xuất (Retrieve)**: 
    *   Hóa query thành Dense Vector (1024 dim) và Sparse Vector (Trọng số từ khóa).
    *   Qdrant tìm kiếm Top 20 ứng viên gần nhất.
3.  **Xếp hạng lại (Rerank)**:
    *   Đưa Query và 20 đoạn văn vào Cross-Encoder.
    *   Lấy ra Top 5 đoạn văn có điểm số cao nhất.
4.  **Sinh văn bản (Generate)**:
    *   Ghép Top 5 đoạn văn vào System Prompt.
    *   LLM Qwen thực hiện suy luận và đưa ra câu trả lời bằng tiếng Việt.
5.  **Ghi log**: Lưu vết Query, Answer và Sources vào file JSONL để theo dõi hiệu năng.

---

## 8. Đánh giá & Nhận xét

### Điểm mạnh
*   **Độ chính xác cao**: Nhờ cơ chế Reranking, hệ thống loại bỏ được hầu hết các kết quả tìm kiếm sai.
*   **Tối ưu tài nguyên**: Sử dụng quantization 4-bit giúp chạy được các mô hình mạnh ngay cả trên GPU phổ thông (4GB VRAM).
*   **Khả năng mở rộng**: Cấu trúc module hóa rõ ràng, dễ dàng thay thế LLM hoặc Vector DB khác.

### Hạn chế
*   **Thời gian phản hồi**: Việc chạy đồng thời Embedding, Reranking và Generation trên một GPU nhỏ có thể gây độ trễ (latency) nhất định.
*   **Xử lý bảng biểu**: Hiện tại vẫn đang tập trung vào văn bản thuần túy, việc xử lý dữ liệu dạng bảng trong PDF cần được cải thiện.

---

## 9. Hướng phát triển

*   **Tích hợp Agentic RAG**: Cho phép AI tự quyết định khi nào cần tìm kiếm thêm hoặc khi nào cần phản hồi ngay.
*   **Xử lý Đa phương thức**: Hỗ trợ đọc hiểu hình ảnh, biểu đồ trong tài liệu sổ tay.
*   **Web Dashboard**: Xây dựng giao diện quản lý logs và xem báo cáo đánh giá trực quan hơn.
