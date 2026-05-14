# BÁO CÁO ĐÁNH GIÁ HIỆU SUẤT HỆ THỐNG TRUY XUẤT VÀ SINH VĂN BẢN (RAG)

---

## 1. Giới thiệu chung

Mô hình Trí tuệ Nhân tạo (AI) được phát triển trong dự án là một hệ thống dựa trên kiến trúc Truy xuất và Sinh văn bản (**Retrieval-Augmented Generation - RAG**). Chức năng cốt lõi của mô hình là cung cấp nền tảng xử lý ngôn ngữ tự nhiên, đóng vai trò như một trợ lý ảo hỗ trợ người dùng trong hệ thống trò chuyện (chat) nội bộ. 

Bằng việc kết hợp khả năng sinh văn bản của Mô hình Ngôn ngữ Lớn (LLM) và hệ thống lưu trữ tài liệu đặc thù, hệ thống giảm thiểu tối đa hiện tượng *"ảo giác"* (hallucination), đồng thời đảm bảo tính xác thực, minh bạch của luồng thông tin cung cấp.

<br>

## 2. Kiến trúc và Quy trình hoạt động

Khung làm việc (framework) của hệ thống RAG được thiết kế theo kiến trúc xử lý bất đồng bộ (**Asynchronous**) nhằm đáp ứng yêu cầu về hiệu suất và khả năng mở rộng. Quá trình xử lý bao gồm hai giai đoạn độc lập:

### 2.1. Giai đoạn Chuẩn bị dữ liệu và Lưu trữ (Data Ingestion)
- **Tiền xử lý (Parsing & Chunking):** Dữ liệu đầu vào (tài liệu nội bộ, cẩm nang) được phân rã thành các đoạn văn bản (*chunks*) nhỏ nhằm tối ưu hóa chi phí tính toán và đảm bảo tính nguyên vẹn về mặt ngữ nghĩa gốc.
- **Biểu diễn không gian Vector (Embedding):** Sử dụng mô hình `BGE-M3` nhằm mã hóa các đoạn văn bản thành các vector biểu diễn mang đặc trưng đa chiều (bao gồm *Dense vectors*, *Sparse vectors* và cơ chế *ColBERT*).
- **Lưu trữ (Vector Database):** Các biểu diễn vector kết hợp với siêu dữ liệu (*metadata*) được tổ chức và lưu trữ trên hệ quản trị cơ sở dữ liệu vector **Qdrant**.

### 2.2. Giai đoạn Truy xuất và Sinh văn bản (Retrieval & Generation)
- **Mã hóa truy vấn:** Câu hỏi của người dùng được chuyển đổi thành vector thông qua mô hình Embedding `BGE-M3`.
- **Truy xuất không gian (Hybrid Search):** Dựa trên Qdrant, hệ thống thực hiện tìm kiếm kết hợp (*Hybrid Search*) giữa từ vựng và thuật toán bề mặt để xác định `K` tài liệu có độ tương đồng lớn nhất với truy vấn.
- **Xếp hạng lại (Reranking):** Hệ thống áp dụng mô hình `BGE-Reranker-v2-M3` nhằm tái cấu trúc và tính toán lại thuật toán chấm điểm sự liên quan giữa câu hỏi và văn bản gốc, qua đó chắt lọc tài liệu với độ chính xác cao nhất.
- **Khởi tạo văn bản (Generation):** Các tài liệu văn bản cuối cùng (*Context*) và câu hỏi người dùng được tổ hợp thành Prompt tiêu chuẩn. Mô hình LLM `Qwen2.5-Instruct` dựa vào thông tin cung cấp để suy luận và đưa ra câu trả lời cuối cùng trực tiếp liên quan đến vấn đề được hỏi.

<div style="page-break-after: always;"></div>

## 3. Cấu hình và Thư viện công nghệ

Quá trình xây dựng và đánh giá hệ thống RAG sử dụng các thư viện phần mềm chuyên chuẩn sau:
- **LLM và Embedding:** `transformers`, `FlagEmbedding` hỗ trợ các mô hình nền tảng như *BGE-M3*, *BGE-Reranker-v2-M3*; và `vLLM` cho mục đích tăng tốc (*inference acceleration*).
- **Vector Storage:** `qdrant-client` để thiết lập giao tiếp API tạo lập Hybrid Search.
- **Evaluation Utilities:**
  - `nltk`, `rouge-score`: Áp dụng cho các tính toán thuật toán BLEU, ROUGE.
  - `scikit-learn`: Phân tích thống kê như độ tương đồng Cosine (*Cosine Similarity*).
- **Framework bổ trợ:** `asyncio` để tối ưu xử lý I/O, `pandas` và `json` hỗ trợ tiền xử lý dữ liệu kiểm thử.

<br>

## 4. Phương pháp luận Đánh giá

Quy trình thực nghiệm được cài đặt trên bộ dữ liệu kiểm thử (*Test Set*) bao gồm 50 cặp dữ liệu (Câu hỏi người dùng - Văn bản chuẩn). Phương pháp đánh giá được tiếp cận qua ba khía cạnh:

### 4.1. Đánh giá dựa trên Mô hình AI Trọng tài (LLM-as-a-Judge)
Để đảm bảo tính khách quan, hệ thống không dùng người chấm thủ công mà áp dụng quy trình kiểm định "AI chấm điểm AI" (*LLM-as-a-Judge*). Cụ thể:
- **Công cụ chấm điểm:** Sử dụng một mô hình Ngôn ngữ Lớn độc lập chuyên trách làm Giám khảo (ưu tiên các mô hình đánh giá 1.5B tham số).
- **Phương pháp chấm điểm:** Trọng tài AI này áp dụng kỹ thuật Suy luận Từng bước (*Chain-of-Thought - CoT*) để đọc, phân tách và lập luận tuần tự dữ liệu. Việc bắt buộc AI giám khảo viện dẫn lý do trước khi kết luận giúp ngăn chặn ảo giác và triệt tiêu khuynh hướng thiên vị (*self-evaluation bias*).
- **Kết quả xuất:** Dựa trên tư duy tuyến tính, mô hình sẽ tự động xuất ra điểm số thực nghiệm theo thang đo 1.0 - 5.0 xoay quanh 7 tiêu chí đo lường nòng cốt (*Aspects*):
  - **Context Relevance:** Ngữ cảnh (tài liệu thu thập được từ bộ Handbook) có thực sự chứa nội dung trọng tâm để trả lời câu hỏi đó không?
  - **Faithfulness (Độ trung thực):** AI có "bịa chuyện" (*hallucination*) không? Mọi thông tin phản hồi có nằm đúng và trọn vẹn trong Ngữ cảnh không?
  - **Answer Relevance:** Câu trả lời có đúng trọng tâm, giải quyết trực tiếp và triệt để câu hỏi của người dùng chưa?
  - **Noise Robustness (Độ kháng nhiễu):** Dù trong kết quả search có dính vài đoạn text không liên quan, AI có nhận biết và lược bỏ được các đoạn "nhiễu" đó ra khỏi câu trả lời không?
  - **Negative Rejection (Khả năng Tự chối):** Nếu câu trả lời không có trong sổ tay nội bộ, AI có dũng cảm/khéo léo báo là "Không biết/Không có thông tin trong hệ thống" thay vì tự chế ra thông tin sai lệch hay không?
  - **Information Integration (Độ mượt tổng hợp):** Nếu câu hỏi cần tổng hợp từ 3-4 đoạn trong Handbook, AI xâu chuỗi thành câu trả lời có logic, mượt mà tiếng Việt không?
  - **Counterfactual Robustness:** AI có vững vàng trước các câu hỏi cố tình "gài rập" hoặc chứa giả định sai lầm của người dùng không?

### 4.2. Chấm bằng Ma trận Ngữ nghĩa (Semantic Similarity)
Chất lượng sinh văn bản không chỉ xét bằng điểm 1.0 - 5.0 của mô hình ngôn ngữ mà còn được chạy qua mô hình toán học để đối soát:
- Hệ thống dùng mô hình Vector nhúng là `BGE-M3` (cùng loại với công cụ tìm kiếm không gian) để chuyển cả Câu trả lời của AI sinh ra và Đáp án chuẩn của người soạn (từ file dataset) thành các Vector toán học đa luồng.
- Tiến hành nén lượng tử xuống và tính hệ số góc (*Cosine Similarity*). Nếu vector của câu trả lời sinh ra gần sát với vector đáp án chuẩn, chứng tỏ tỷ lệ "hiểu ý" của AI là cực kỳ cao, ngay cả khi hai câu dùng hệ thống từ ngữ hoàn toàn khác nhau.

### 4.3. Metric Thống kê Cổ điển (String Metrics)
Để chắc chắn và chặt chẽ hơn về mặt đo lường, quy trình hệ thống ứng dụng thêm các thư viện ngôn ngữ hình thức truyền thống:
- **ROUGE-L / BLEU Score:** Đo đếm số lượng từ vựng, cụm từ vựng có sự trùng lặp trực tiếp với đáp án gốc.
- **Exact Match (EM):** Phép đo xác suất tỷ lệ khớp ký tự chính xác 100%.
- **Retriever Hit Rate@k:** Kỹ thuật kiểm toán đo lường hiệu suất lấy trúng được văn bản chuẩn vào giới hạn không gian `k`.

*Tóm lược Phương pháp luận:* 
Toàn bộ lời giải đáp được sinh ra sẽ tự động được chứng minh tính chính xác nghiêm ngặt bằng cách định tuyến qua một "Hội đồng" kết hợp bao gồm ba lớp giám sát: (1) AI tự thân chấm điểm Logic và tính Trung thực; (2) Điểm đo đếm độ phân tách ngữ nghĩa qua hệ số toán học (*BGE-M3 Vector*); và (3) Kết quả đo xác suất trùng lập từ vựng tuyến tính (*NLTK/Rouge*). Điểm xét duyệt này căn cứ vào kho dataset Q&A gốc thuộc về danh mục dữ liệu chuẩn `data/qa/test_qa.md`. Theo luồng kịch bản, điểm số kết xuất tổng hợp tự động thành file log (%) qua đó cho phép cấu trúc điều phối viên biết được pipeline ứng dụng có đang phản hồi tốt dựa trên các chỉ số này hay không.

<div style="page-break-after: always;"></div>

## 5. Kết quả Thực nghiệm

Dưới đây là kết quả thống kê hiệu năng áp dụng cho 50 quy trình kiểm tra mô phỏng thực tế.

**Bảng 1: Kết quả đánh giá 7 tiêu chí dựa trên LLM (RAG Evaluator)**

| Tiêu chí Đánh giá (Aspects) | Điểm trung bình | Phần trăm (%) |
| :--- | :---: | :---: |
| **Trọng số Ngữ cảnh (Context Relevance)** | 3.94 / 5.0 | 78.8% |
| **Độ trung thực (Faithfulness)** | 4.68 / 5.0 | 93.6% |
| **Độ liên quan Câu trả lời (Answer Relevance)** | 4.38 / 5.0 | 87.6% |
| **Độ chống nhiễu (Noise Robustness)** | 4.72 / 5.0 | 94.4% |
| **Khả năng từ chối (Negative Rejection)** | 4.08 / 5.0 | 81.6% |
| **Khả năng tổng hợp thông định (Information Integration)** | 4.38 / 5.0 | 87.6% |
| **Kháng phủ thực tế (Counterfactual Robustness)** | 4.38 / 5.0 | 87.6% |

<br>

**Bảng 2: Các chỉ số Tương đồng và Thống kê Chuỗi Toán học (Math & String)**

| Tiêu chí (Math / String Metrics) | Giá trị trung bình | Phần trăm (%) |
| :--- | :---: | :---: |
| **Tỷ lệ trúng tài liệu (Retriever Hit Rate@k)** | 0.9400 / 1.0 | 94.0% |
| **Mức độ tương tự ngữ nghĩa (Semantic Cosine Similarity)** | 0.7654 / 1.0 | 76.5% |
| **Chuỗi ROUGE-L bao phủ (Answer ROUGE-L Score)** | 0.3992 / 1.0 | 39.9% |
| **Trùng lặp Lexical Hoàn toàn (Negative Exact Match - EM)** | 0.0200 / 1.0 | 2.0% |
| **Chỉ số dịch máy quy chuẩn (Answer BLEU Score)** | 0.0000 / 1.0 | 0.0% |

<br>

## 6. Phân tích và Kết luận

Các số liệu thống kê chứng minh hệ thống RAG được phát triển đã đạt hiệu suất truy xuất ở ngưỡng xuất sắc với **Retriever Hit Rate@k** đạt **94.0%**. Cùng với đó, hệ thống đáp ứng tính an toàn cực kỳ chi tiết với độ chịu nhiễu (*Noise Robustness: 94.4%*) và khả năng trung thực không bịa đặt nguồn dữ liệu (*Faithfulness: 93.6%*).

Các chỉ số cơ bản về hình vị từ vựng (*Answer BLEU Score* ở ngưỡng 0.0% và *Exact Match* ở mức 2.0%) cho thấy công dụng điển hình của mô hình nền AI tạo sinh. Hệ thống không sử dụng cách "so sánh để nhặt văn bản" (*Extractive*) mà dùng phương pháp tái tạo diễn đạt (*Paraphrasing Generative*) biểu diện qua mức độ trung bình ngữ nghĩa Semantic Cosine (76.5%). 

**Như vậy, mô hình AI đã hoàn thành chuẩn xác mục tiêu và sẵn sàng đáp ứng tiến độ tích hợp ở các quy trình thực địa (Production).**
