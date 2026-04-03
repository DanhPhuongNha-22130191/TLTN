import os
import json
import importlib.util

def load_chatbot():
    # Tai mo hinh chatbot va cac cau hinh lien quan
    BASE_DIR = "/home/phuongnha/SourcesCode/AI Module/company-ai-chatbot"
    pipeline_path = os.path.join(BASE_DIR, "scripts", "04_rag_pipeline.py")
    spec = importlib.util.spec_from_file_location("rag_pipeline", pipeline_path)
    rag_pipeline = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(rag_pipeline)
    
    model_path = os.path.join(BASE_DIR, 'models', 'qwen2-1.5b-lora')
    chroma_path = os.path.join(BASE_DIR, 'chromadb')
    
    chatbot = rag_pipeline.CompanyAIChatbot(
        model_path=model_path,
        chromadb_path=chroma_path
    )
    return chatbot

def debug_questions():
    # Kiem tra chatbot voi mot danh sach cac cau hoi thu nghiem
    chatbot = load_chatbot()
    questions = [
        "Cụm từ xuất hiện trong chunk này là gì?",
        "Trong năm đầu tiên triển khai, hỗ trợ diễn giả bị giới hạn ở bao nhiêu sự kiện?",
        "Nếu bạn tham dự từ xa, bạn cần đảm bảo những gì về vị trí và kết nối?",
        "Ngoài viết mô tả và đăng tuyển, những hoạt động nào khác được nêu ra?",
        "Người tham dự cuộc họp có trách nhiệm gì về thời gian?"
    ]
    
    for q in questions:
        print(f"\nCAU HOI: {q}")
        contexts = chatbot.retrieve_context(q)
        print("NGU CANH TRUY XUAT:")
        for i, c in enumerate(contexts):
            print(f"[{i}] Loai: {c['type']}, Diem: {c['score']:.4f}")
            print(f"    Noi dung: {c['content'][:200]}...")
        
        answer = chatbot.generate_answer(q, contexts)
        print(f"CAU TRA LOI CUOI CUNG: {answer}")

if __name__ == "__main__":
    # Khoi chay quy trinh kiem tra cau hoi
    debug_questions()
