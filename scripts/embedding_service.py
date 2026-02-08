from fastapi import FastAPI
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer
import torch

app = FastAPI()

MODEL_NAME = "dangvantuan/vietnamese-embedding"
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"

print(f"Loading model on {DEVICE}...")
model = SentenceTransformer(MODEL_NAME, device=DEVICE)
print("Model loaded!")

class EmbedRequest(BaseModel):
    texts: list[str]

@app.post("/embed")
def embed(req: EmbedRequest):
    embeddings = model.encode(
        req.texts,
        convert_to_numpy=True,
        normalize_embeddings=True,
        batch_size=32
    )
    return {"embeddings": embeddings.tolist()}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)