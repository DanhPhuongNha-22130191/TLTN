import os
import sys
import uvicorn

# Ensure workspace root is in path
script_dir = os.path.dirname(os.path.abspath(__file__))
workspace_dir = os.path.dirname(os.path.dirname(script_dir))
if workspace_dir not in sys.path:
    sys.path.append(workspace_dir)

from ai.api.main import app

if __name__ == "__main__":
    print("\n" + "="*60)
    print("ĐANG CHẠY MÁY CHỦ API & GIAO DIỆN CHAT AI ")
    print("Truy cập giao diện tại: http://localhost:8000")
    print("="*60 + "\n")
    uvicorn.run(app, host="0.0.0.0", port=8000)
