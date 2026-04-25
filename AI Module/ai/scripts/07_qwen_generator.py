import os
import torch
import asyncio
import warnings
import logging

os.environ["TOKENIZERS_PARALLELISM"] = "false"
warnings.filterwarnings("ignore")
logging.getLogger("transformers").setLevel(logging.ERROR)

class QwenGenerator:
    def __init__(self, model_name="Qwen/Qwen2.5-3B-Instruct", use_vllm=False):
        self.model_name = model_name
        self.use_vllm = use_vllm
        self.tokenizer = None
        self.llm = None
        self.pipe = None
        
        print(f"🧠 Loading Generator {self.model_name}...")
        
        if self.use_vllm:
            try:
                from vllm import LLM, SamplingParams
                from transformers import AutoTokenizer
                print("⚡ Booting vLLM Engine (GPU Optimized PagedAttention)...")
                self.llm = LLM(model=self.model_name, dtype="auto", enforce_eager=True)
                self.sampling_params = SamplingParams(max_tokens=256, temperature=0.1)
                self.tokenizer = AutoTokenizer.from_pretrained(self.model_name)
            except ImportError:
                print("⚠️ vLLM not found. Falling back to Transformers pipeline.")
                self.use_vllm = False
                
        if not self.use_vllm:
            from transformers import AutoModelForCausalLM, AutoTokenizer, pipeline, BitsAndBytesConfig
            self.tokenizer = AutoTokenizer.from_pretrained(self.model_name)
            
            # Cấu hình lượng tử hoá 4-bit (NF4 + Double Quant) để tối ưu RAM/VRAM
            quant_config = BitsAndBytesConfig(
                load_in_4bit=True,
                bnb_4bit_quant_type="nf4",
                bnb_4bit_use_double_quant=True,
                bnb_4bit_compute_dtype=torch.float16
            )
            
            self.model = AutoModelForCausalLM.from_pretrained(
                self.model_name, 
                device_map="auto",
                torch_dtype=torch.float16,
                quantization_config=quant_config,
                low_cpu_mem_usage=True
            )
            self.pipe = pipeline(
                "text-generation", 
                model=self.model, 
                tokenizer=self.tokenizer, 
                max_new_tokens=256,
                temperature=0.1,
                do_sample=False
            )

    def _sync_generate(self, prompt: str) -> str:
        messages = [
            {"role": "system", "content": "Bạn là trợ lý ảo doanh nghiệp. BẮT BUỘC chỉ sử dụng thông tin trong CONTEXT. Nếu có thể hãy trích dẫn [doc]. Không được bịa đặt chi tiết."},
            {"role": "user", "content": prompt}
        ]
        
        text_prompt = self.tokenizer.apply_chat_template(
            messages, 
            tokenize=False, 
            add_generation_prompt=True
        )
        
        if self.use_vllm:
            outputs = self.llm.generate([text_prompt], self.sampling_params, use_tqdm=False)
            return outputs[0].outputs[0].text.strip()
        else:
            output = self.pipe(text_prompt)
            generated_text = output[0]["generated_text"]
            answer = generated_text[len(text_prompt):].strip()
            return answer

    async def generate(self, prompt: str) -> str:
        """
        Asynchronously generates text. If using pipeline, delegates to a thread.
        """
        return await asyncio.to_thread(self._sync_generate, prompt)
