import os
import json
import faiss
import numpy as np
from tqdm import tqdm
from sentence_transformers import SentenceTransformer
from transformers import AutoTokenizer

# ================= CONFIG =================
CHUNK_JSON = "embed/all_500_new_chunk.json"
FAISS_OUT = "embed/faiss.index"
META_OUT = "embed/metadata.jsonl"
MODEL_NAME = "dangvantuan/vietnamese-embedding"
BATCH_SIZE = 8
MAX_LEN = 256  # Conservative limit to avoid position embedding errors
DEVICE = "cpu"
# =========================================


def extract_text(chunk: dict) -> str | None:
    """Extract text content from chunk dictionary."""
    for key in ["chunk_content", "text", "content", "page_content", "body"]:
        if key in chunk and isinstance(chunk[key], str):
            return chunk[key].strip()
    return None


def truncate_texts(texts: list[str], tokenizer, max_length: int) -> list[str]:
    """Manually truncate texts to max token length."""
    truncated = []
    
    for text in texts:
        # Tokenize with truncation
        tokens = tokenizer.encode(
            text,
            add_special_tokens=True,
            truncation=True,
            max_length=max_length
        )
        
        # Decode back to text
        truncated_text = tokenizer.decode(tokens, skip_special_tokens=True)
        truncated.append(truncated_text)
    
    return truncated


def main():
    print("=" * 80)
    print(" BUILD FAISS EMBEDDINGS (MANUAL TRUNCATION)")
    print("=" * 80)

    # Load chunks
    with open(CHUNK_JSON, "r", encoding="utf-8") as f:
        chunks = json.load(f)

    texts = []
    metadata = []

    for c in chunks:
        t = extract_text(c)
        if not t:
            continue
        texts.append(t)
        metadata.append(c)

    print(f"📥 Loaded chunks: {len(chunks)}")
    print(f"✅ Valid chunks: {len(texts)}")

    # Load tokenizer for manual truncation
    print(f"🔧 Loading tokenizer: {MODEL_NAME}")
    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
    
    # 🔥 CRITICAL: Pre-truncate ALL texts
    print(f"✂️  Pre-truncating texts to {MAX_LEN} tokens...")
    texts = truncate_texts(texts, tokenizer, MAX_LEN)
    print(f"✅ Truncation complete")

    # Load model
    print(f"🔧 Loading embedding model...")
    model = SentenceTransformer(MODEL_NAME, device=DEVICE)
    model.max_seq_length = MAX_LEN
    
    dim = model.get_sentence_embedding_dimension()
    print(f"✅ Model loaded | dim={dim} | max_len={MAX_LEN}")

    # Initialize FAISS index
    index = faiss.IndexFlatIP(dim)
    all_embeddings = []

    # Encode in batches with progress bar
    for i in tqdm(range(0, len(texts), BATCH_SIZE), desc="Embedding (CPU)", unit="batch"):
        batch = texts[i:i + BATCH_SIZE]

        try:
            emb = model.encode(
                batch,
                batch_size=BATCH_SIZE,
                convert_to_numpy=True,
                normalize_embeddings=True,
                show_progress_bar=False,
            )
            
            all_embeddings.append(emb)
            
        except Exception as e:
            print(f"\n❌ Error at batch {i//BATCH_SIZE}: {e}")
            print(f"   Batch indices: {i} to {i+len(batch)}")
            print(f"   Batch size: {len(batch)}")
            for j, text in enumerate(batch):
                token_count = len(tokenizer.encode(text, add_special_tokens=True))
                print(f"   Text {j}: {len(text)} chars, {token_count} tokens")
            raise

    # Concatenate all embeddings
    all_embeddings = np.vstack(all_embeddings)
    print(f"\n✅ Generated embeddings: {all_embeddings.shape}")

    # Add to FAISS index
    index.add(all_embeddings)

    # Save outputs
    os.makedirs(os.path.dirname(FAISS_OUT) or ".", exist_ok=True)
    faiss.write_index(index, FAISS_OUT)
    
    with open(META_OUT, "w", encoding="utf-8") as f:
        for item in metadata:
            f.write(json.dumps(item, ensure_ascii=False) + "\n")

    print(f"\n{'='*80}")
    print(f"✅ FAISS index saved: {FAISS_OUT}")
    print(f"✅ Metadata saved: {META_OUT}")
    print(f"📊 Total vectors: {index.ntotal}")
    print(f"📏 Vector dimension: {dim}")
    print(f"🎉 SUCCESS - NO ERRORS!")
    print(f"{'='*80}")


if __name__ == "__main__":
    main()