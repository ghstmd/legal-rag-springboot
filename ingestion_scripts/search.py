import json
import faiss
import numpy as np
from sentence_transformers import SentenceTransformer
from pyvi import ViTokenizer
import torch

class SearchEngine:
    def __init__(self, index_path="faiss.index", metadata_path="metadata.jsonl"):
        """
        Khởi tạo search engine với FAISS index và metadata
        """
        print("Loading search engine...")
        
        # Load FAISS index
        self.index = faiss.read_index(index_path)
        print(f"✅ Loaded FAISS index with {self.index.ntotal} vectors")
        
        # Load metadata
        self.metadata = []
        with open(metadata_path, "r", encoding="utf-8") as f:
            for line in f:
                self.metadata.append(json.loads(line.strip()))
        print(f"✅ Loaded {len(self.metadata)} metadata entries")
        
        # Initialize embedding model
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        print(f"🖥️  Using device: {self.device.upper()}")
        self.model = SentenceTransformer("dangvantuan/vietnamese-embedding", device=self.device)
        print("✅ Model loaded")
    
    def tokenize_text(self, text):
        """Tokenize Vietnamese text"""
        return ViTokenizer.tokenize(text).split()
    
    def create_query_embedding(self, query):
        """
        Tạo embedding cho query (giống cách tạo embedding cho documents)
        """
        tokens = self.tokenize_text(query)
        
        # Chia thành subchunks như khi embedding
        subchunk_size = 60
        subchunks = [' '.join(tokens[j:j+subchunk_size]) for j in range(0, len(tokens), subchunk_size)]
        
        # Encode và lấy mean
        sub_embeddings = self.model.encode(subchunks, convert_to_numpy=True)
        mean_embedding = np.mean(sub_embeddings, axis=0).astype('float32')
        
        return mean_embedding
    
    def search(self, query, top_k=5):
        """
        Tìm kiếm top_k documents gần nhất với query
        
        Args:
            query: Câu truy vấn (string)
            top_k: Số lượng kết quả trả về
            
        Returns:
            List of (metadata, distance) tuples
        """
        print(f"\n🔍 Searching for: '{query}'")
        
        # Tạo embedding cho query
        query_embedding = self.create_query_embedding(query)
        query_vector = query_embedding.reshape(1, -1)
        
        # Search trong FAISS index
        distances, indices = self.index.search(query_vector, top_k)
        
        # Lấy metadata tương ứng
        results = []
        for i, (dist, idx) in enumerate(zip(distances[0], indices[0])):
            if idx < len(self.metadata):  # Kiểm tra index hợp lệ
                results.append({
                    'rank': i + 1,
                    'distance': float(dist),
                    'metadata': self.metadata[idx]
                })
        
        return results
    
    def print_results(self, results):
        """In kết quả search ra console"""
        print(f"\n📊 Found {len(results)} results:\n")
        print("="*80)
        
        for result in results:
            print(f"\n🏆 Rank {result['rank']} | Distance: {result['distance']:.4f}")
            print("-"*80)
            
            metadata = result['metadata']
            
            # In các trường metadata
            for key, value in metadata.items():
                if key == 'chunk_content':
                    continue  # Skip content trong metadata nếu có
                print(f"  {key}: {value}")
            
            # In preview của content nếu có
            if 'chunk_content' in metadata:
                content = metadata['chunk_content']
                preview = content[:200] + "..." if len(content) > 200 else content
                print(f"\n  📄 Preview:\n  {preview}")
            
            print("="*80)


def main():
    """
    Example usage
    """
    # Khởi tạo search engine
    search_engine = SearchEngine(
        index_path="embed/normalized_faiss.index",
        metadata_path="embed/metadata.jsonl"
    )
    
    # Example queries
    queries = [
        "Mua đất bằng giấy tay có được cấp sổ đỏ không?",
        "Đất chưa có sổ đỏ có được xây nhà không?"
    ]

    for query in queries:
        results = search_engine.search(query, top_k=5)
        search_engine.print_results(results)
        print("\n" + "="*80 + "\n")


if __name__ == "__main__":
    main()