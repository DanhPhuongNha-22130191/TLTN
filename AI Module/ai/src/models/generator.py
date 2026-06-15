import asyncio
import json
import urllib.request
from ai.src.config import GENERATOR_MODEL_NAME


class QwenGenerator:
    def __init__(
        self,
        model_name: str = GENERATOR_MODEL_NAME,
        ollama_url: str = None,
    ):
        import os
        self.model_name = model_name
        self.ollama_url = ollama_url or os.environ.get("OLLAMA_URL", "http://localhost:11434/api/generate")

        print(f"Đang dùng Qwen local qua Ollama: {self.model_name}")

    def _sync_generate(self, prompt: str, retries=2) -> str:
        payload = {
            "model": self.model_name,
            "prompt": prompt,
            "stream": False,
            "options": {
                "temperature": 0.1,
                "num_predict": 512,
                "num_ctx": 4096,  
                "repeat_penalty": 1.15,
                "top_k": 40,
                "top_p": 0.9,
            },
        }

        data = json.dumps(payload).encode("utf-8")

        for attempt in range(retries):
            try:
                request = urllib.request.Request(
                    self.ollama_url,
                    data=data,
                    headers={"Content-Type": "application/json"},
                    method="POST",
                )

                with urllib.request.urlopen(request, timeout=120) as response:
                    result = json.loads(response.read().decode("utf-8"))

                return result.get("response", "").strip()
            except Exception as e:
                print(f"[Ollama] Lỗi ở lần thử {attempt + 1}/{retries}: {e}")
                if attempt == retries - 1:
                    return f"ERROR: {str(e)}"
                import time
                time.sleep(3) 

    async def generate(self, prompt: str) -> str:
        return await asyncio.to_thread(self._sync_generate, prompt)