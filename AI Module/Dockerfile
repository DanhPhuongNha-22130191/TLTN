# Sử dụng Python 3.11 làm base image
FROM python:3.11-slim

# Cài đặt các công cụ build cơ bản phòng trường hợp cần thiết
RUN apt-get update && apt-get install -y gcc g++ libffi-dev

WORKDIR /app

# Copy file requirements.txt (nếu có) và cài đặt
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

# Đảm bảo cài đặt đủ FastAPI và Uvicorn
RUN pip install --no-cache-dir fastapi uvicorn pydantic

# Copy toàn bộ thư mục gốc vào trong Docker (bao gồm cả thư mục "ai")
COPY . /app/

# Mở cổng 3000 ra ngoài
EXPOSE 3000

# Lệnh khởi chạy API server trên port 3000
CMD ["uvicorn", "ai.src.api:app", "--host", "0.0.0.0", "--port", "3000"]
