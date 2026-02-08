#!/usr/bin/env python3
"""
FAISS search script for Java integration
Called via subprocess from FaissSearchService

Usage: python3 faiss_search.py <index_path> <embedding_json> <top_k>

Returns JSON with indices and scores
"""

import sys
import json
import numpy as np

try:
    import faiss
except ImportError:
    print(json.dumps({"error": "faiss-cpu not installed. Run: pip install faiss-cpu"}), file=sys.stderr)
    sys.exit(1)


def main():
    """Main search function"""
    if len(sys.argv) != 4:
        error = {
            "error": "Invalid arguments",
            "usage": "python3 faiss_search.py <index_path> <embedding_json> <top_k>"
        }
        print(json.dumps(error), file=sys.stderr)
        sys.exit(1)
    
    try:
        # Parse arguments
        index_path = sys.argv[1]
        embedding_json = sys.argv[2]
        top_k = int(sys.argv[3])
        
        # Parse embedding
        embedding = json.loads(embedding_json)
        
        # Validate embedding
        if not isinstance(embedding, list):
            raise ValueError("Embedding must be a list")
        
        if len(embedding) == 0:
            raise ValueError("Embedding is empty")
        
        # Load FAISS index
        index = faiss.read_index(index_path)
        
        # Convert embedding to numpy array (float32)
        query_vec = np.array([embedding], dtype=np.float32)
        
        # Validate dimensions
        if query_vec.shape[1] != index.d:
            raise ValueError(
                f"Dimension mismatch: query={query_vec.shape[1]}, index={index.d}"
            )
        
        # Search
        scores, indices = index.search(query_vec, top_k)
        
        # Convert to lists (handle int64/float32 JSON serialization)
        result = {
            "indices": [int(idx) for idx in indices[0]],
            "scores": [float(score) for score in scores[0]]
        }
        
        # Print result to stdout
        print(json.dumps(result))
        sys.exit(0)
        
    except FileNotFoundError as e:
        error = {"error": f"Index file not found: {e}"}
        print(json.dumps(error), file=sys.stderr)
        sys.exit(1)
        
    except json.JSONDecodeError as e:
        error = {"error": f"Invalid JSON in embedding: {e}"}
        print(json.dumps(error), file=sys.stderr)
        sys.exit(1)
        
    except Exception as e:
        error = {
            "error": str(e),
            "type": type(e).__name__
        }
        print(json.dumps(error), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()