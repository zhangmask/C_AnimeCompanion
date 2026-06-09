"""
Export m3e-small model to ONNX format
"""
import os
import shutil
from pathlib import Path

def export_m3e_small():
    print("Loading m3e-small model...")
    
    from optimum.onnxruntime import ORTModelForFeatureExtraction
    from transformers import AutoTokenizer
    
    model_id = "moka-ai/m3e-small"
    output_dir = Path("app/src/main/assets/embedding")
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # Load tokenizer and export model
    print("Loading tokenizer...")
    tokenizer = AutoTokenizer.from_pretrained(model_id)
    
    print("Exporting model to ONNX...")
    model = ORTModelForFeatureExtraction.from_pretrained(model_id, export=True)
    
    # Save model and tokenizer
    print("Saving model.onnx...")
    model.save_pretrained(output_dir)
    
    print("Saving vocab.txt...")
    tokenizer.save_pretrained(output_dir)
    
    # List output files
    print("\nExported files:")
    for f in output_dir.iterdir():
        print(f"  {f.name}: {f.stat().st_size / 1024 / 1024:.2f} MB")
    
    print("\nDone! Model exported successfully.")

if __name__ == "__main__":
    export_m3e_small()
