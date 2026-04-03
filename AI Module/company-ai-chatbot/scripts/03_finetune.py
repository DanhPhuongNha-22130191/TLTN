"""
Fine-tune Qwen2-1.5B voi LoRA + 4bit (QLoRA)
Toi uu cho GPU 4GB (RTX 3050)
"""

import json
import torch
from pathlib import Path
from transformers import (
    AutoTokenizer,
    AutoModelForCausalLM,
    TrainingArguments,
    Trainer,
    BitsAndBytesConfig,
    DataCollatorForLanguageModeling
)
from peft import (
    LoraConfig,
    get_peft_model,
    TaskType,
    prepare_model_for_kbit_training
)
from datasets import Dataset
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class QwenFineTuner:
    # Lop thuc hien quy trinh fine-tuning mo hinh Qwen

    def __init__(self, model_name="Qwen/Qwen2-1.5B", device="cuda"):
        # Khoi tao cac thiet lap co ban cho viec fine-tune
        self.model_name = model_name
        self.device = device

        if device == "cuda" and torch.cuda.is_available():
            logger.info(f"GPU: {torch.cuda.get_device_name(0)}")
        else:
            logger.warning("GPU khong kha dung -> dung CPU")
            self.device = "cpu"

    def load_tokenizer_and_model(self):
        # Tai tokenizer va mo hinh voi cau hinh 4-bit (QLoRA)
        logger.info(f"Load model: {self.model_name}")

        self.tokenizer = AutoTokenizer.from_pretrained(
            self.model_name,
            trust_remote_code=True
        )

        if self.tokenizer.pad_token is None:
            self.tokenizer.pad_token = self.tokenizer.eos_token

        bnb_config = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_compute_dtype=torch.float16,
            bnb_4bit_use_double_quant=True,
            bnb_4bit_quant_type="nf4"
        )

        self.model = AutoModelForCausalLM.from_pretrained(
            self.model_name,
            quantization_config=bnb_config,
            device_map="auto",
            trust_remote_code=True
        )

        # Chuan bi mo hinh cho viec huan luyen k-bit
        self.model = prepare_model_for_kbit_training(self.model)

        logger.info("Model loaded")

    def setup_lora(self):
        # Thiet lap cau hinh LoRA cho mo hinh
        logger.info("Setup LoRA")

        lora_config = LoraConfig(
            task_type=TaskType.CAUSAL_LM,
            r=4,
            lora_alpha=16,
            lora_dropout=0.05,
            bias="none",
            target_modules=["q_proj", "v_proj"]
        )

        self.model = get_peft_model(self.model, lora_config)
        self.model.print_trainable_parameters()

    def load_dataset(self, file_path):
        # Tai va chuan bi du lieu huan luyen tu file JSONL
        data = []

        with open(file_path, "r", encoding="utf-8") as f:
            for line in f:
                item = json.loads(line)
                text = f"Câu hỏi: {item['question']}\nTrả lời: {item['answer']}"
                data.append(text)

        return Dataset.from_dict({"text": data})

    def tokenize(self, examples):
        # Chuyen doi van ban thanh cac tokens cho mo hinh
        outputs = self.tokenizer(
            examples["text"],
            padding="max_length",
            truncation=True,
            max_length=256
        )

        # Sao chep input_ids sang labels de tinh toan loss
        outputs["labels"] = outputs["input_ids"].copy()

        return outputs

    def train(self, train_file, val_file, output_dir):
        # Thuc hien toan bo quy trinh huan luyen
        self.load_tokenizer_and_model()
        self.setup_lora()

        logger.info("Load dataset...")
        train_dataset = self.load_dataset(train_file)
        val_dataset = self.load_dataset(val_file)

        logger.info("Tokenizing...")
        train_dataset = train_dataset.map(self.tokenize, batched=True)
        val_dataset = val_dataset.map(self.tokenize, batched=True)

        training_args = TrainingArguments(
            output_dir=output_dir,
            num_train_epochs=2,
            per_device_train_batch_size=1,
            per_device_eval_batch_size=1,
            gradient_accumulation_steps=16,
            learning_rate=2e-4,
            logging_steps=10,
            fp16=True,
            gradient_checkpointing=True,
            save_steps=200,
            eval_steps=100,
            eval_strategy="steps",
            optim="paged_adamw_8bit",
            report_to="none",
            max_grad_norm=0.3
        )

        trainer = Trainer(
            model=self.model,
            args=training_args,
            train_dataset=train_dataset,
            eval_dataset=val_dataset,
            data_collator=DataCollatorForLanguageModeling(
                tokenizer=self.tokenizer,
                mlm=False
            )
        )

        logger.info("Training...")
        trainer.train()

        logger.info("Saving model...")
        self.model.save_pretrained(output_dir)
        self.tokenizer.save_pretrained(output_dir)

        logger.info("DONE")


if __name__ == "__main__":
    # Khoi chay quy trinh fine-tuning
    BASE_DIR = Path(__file__).resolve().parent.parent

    train_path = BASE_DIR / "data/train.jsonl"
    val_path = BASE_DIR / "data/val.jsonl"
    output_path = BASE_DIR / "models/qwen2-1.5b-lora"

    finetuner = QwenFineTuner()

    finetuner.train(
        train_file=str(train_path),
        val_file=str(val_path),
        output_dir=str(output_path)
    )

