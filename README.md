# Legal RAG System - Hệ thống Tư vấn Pháp luật AI

> Hệ thống RAG (Retrieval-Augmented Generation) chuyên sâu cho tư vấn pháp luật Việt Nam với **Hybrid Search** (Vector + BM25) và **Deep Thinking Mode**.

## 🎯 Tính năng chính

- ✅ **Hybrid Search**: Kết hợp FAISS (vector) và BM25 (sparse) với tỷ lệ 60:40
- 🧠 **Deep Thinking Mode**: Trích xuất keywords → Multi-query search → Merge thông minh
- 💬 **Session Memory**: Lưu lịch sử hội thoại với semantic search
- 📊 **Performance Monitoring**: Theo dõi timing từng bước xử lý
- 🔄 **Circuit Breaker**: Tự động retry và fallback khi service lỗi
- 🇻🇳 **Vietnamese Optimized**: Tokenizer và embedding chuyên biệt cho tiếng Việt

---

## 📋 Yêu cầu hệ thống

- **Java**: 17 hoặc cao hơn
- **Python**: 3.8+ (cho embedding service)
- **Maven**: 3.6+ (hoặc dùng `mvnw` đi kèm)
- **Ollama**: Đã cài đặt và pull model `gpt-oss:120b-cloud`

## 🚀 Cài đặt

### Bước 1: Clone repository

```bash
git clone https://github.com/your-org/legal-rag-springboot.git
cd legal-rag-springboot
```

### Bước 2: Cài đặt Python dependencies

```bash
pip install -r requirements.txt
```

### Bước 3: Chuẩn bị dữ liệu

Đặt các file dữ liệu vào thư mục `data/`:

```
data/
├── faiss.index           # FAISS vector index
├── data_corpus.json      # Corpus văn bản pháp luật
├── metadata.jsonl        # Metadata chunks
└── bm25_cache.gz         # BM25 cache (tự động tạo lần đầu)
```

### Bước 4: Cài đặt và chạy Ollama

```bash
# Cài đặt Ollama (nếu chưa có)
curl -fsSL https://ollama.com/install.sh | sh

# Pull model
ollama pull gpt-oss:120b-cloud

# Kiểm tra model đã sẵn sàng
ollama list
```

### Bước 5: Build và chạy ứng dụng

```bash
# Sử dụng Maven wrapper
./mvnw clean install
./mvnw spring-boot:run

# Hoặc dùng Maven đã cài
mvn clean install
mvn spring-boot:run
```

**Ứng dụng sẽ chạy tại**: `http://localhost:8080`

---

## 🔧 Cấu hình

### File `application.yml`

#### 1. **Ollama Configuration**

```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434  	# Ollama service URL
      chat:
        options:
          model: gpt-oss:120b-cloud      	# Model name
          temperature: 0.1               	# Độ sáng tạo (thấp = deterministic)
          num-predict: 4096               	# Max output tokens
```

#### 2. **Python Embedding Service**

```yaml
legal-rag:
  embedding:
    dimension: 768                       	# Vector dimension
    auto-start: true                     	# Tự động start Python service
    timeout-seconds: 60
    python-command: python               	# Hoặc python3
    python-script-path: scripts/embedding_service.py
```

#### 3. **RAG Configuration**

```yaml
legal-rag:
  rag:
    retrieval:
      top-k: 100                         # Số docs lấy từ mỗi search
      rerank-top-k: 15                   # Số docs sau rerank
      alpha: 0.6                         # Hybrid weight (0.6 = 60% vector)
  
    deep-thinking:
      enabled: true
      top-k-per-keyword: 30              # Docs mỗi keyword
      final-top-k: 8                     # Docs cuối cùng
      max-keywords: 10                   # Max keywords extract
```

#### 4. **Dataset Paths**

```yaml
legal-rag:
  dataset:
    index: data/faiss.index
    metadata: data/metadata.jsonl
    corpus: data/data_corpus.json
    bm25-cache: data/bm25_cache.gz
```

---

## 📖 Sử dụng API

### 1. Health Check

```bash
curl http://localhost:8080/api/health
```

**Response**:

```json
{
  "status": "healthy",
  "timestamp": "2026-02-08T10:30:00Z",
  "service": "Legal RAG API",
  "features": {
    "deep_thinking": true
  },
  "active_sessions": 0
}
```

---

### 2. Query - Base RAG Mode (Nhanh)

```bash
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Mức phạt vi phạm giao thông không đội mũ bảo hiểm là bao nhiêu?",
    "useDeepThinking": false,
    "sessionId": "user-123"
  }'
```

**Response**:

```json
{
  "question": "Mức phạt vi phạm giao thông...",
  "answer": "Theo Nghị định 100/2019/NĐ-CP, mức phạt từ 400.000đ đến 600.000đ...",
  "context": [
    {
      "rank": 1,
      "score": 0.89,
      "chunkId": "chunk_123",
      "text": "Điều 6. Phạt tiền đối với người điều khiển xe mô tô..."
    }
  ],
  "timing": {
    "totalTime": 2.5,
    "stepDurations": {
      "Dense Search": 0.3,
      "BM25 Search": 0.2,
      "Score Fusion": 0.1,
      "Reranking": 0.4,
      "LLM Generation": 1.5
    }
  },
  "sessionId": "user-123",
  "mode": "base_rag"
}
```

---

### 3. Query - Deep Thinking Mode (Chuyên sâu)

```bash
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "So sánh hình phạt giữa trộm cắp và cướp giật tài sản?",
    "useDeepThinking": true,
    "sessionId": "user-123"
  }'
```

**Response**:

```json
{
  "question": "So sánh hình phạt...",
  "answer": "Về trộm cắp tài sản:\n- Điều 173 BLHS 2015...\n\nVề cướp giật:\n- Điều 136 BLHS 2015...",
  "context": [...],
  "keywords": ["trộm cắp", "cướp giật", "hình phạt", "tài sản"],
  "thinkingProcess": {
    "keywords": ["trộm cắp", "cướp giật", "hình phạt"],
    "reasoning": "Câu hỏi yêu cầu so sánh 2 hành vi phạm tội...",
    "searches_performed": 3,
    "total_docs_found": 8
  },
  "timing": {...},
  "sessionId": "user-123",
  "mode": "deep_thinking"
}
```

---

### 4. Session History

```bash
curl http://localhost:8080/api/session/user-123/history
```

**Response**:

```json
{
  "sessionId": "user-123",
  "totalTurns": 5,
  "conversations": [
    {
      "timestamp": "2026-02-08T10:30:00Z",
      "question": "Mức phạt không đội mũ bảo hiểm?",
      "answer": "Theo Nghị định 100/2019...",
      "metadata": {
        "mode": "base_rag",
        "timing": {...}
      }
    }
  ]
}
```

---

### 5. Delete Session

```bash
curl -X DELETE http://localhost:8080/api/session/user-123
```

---

### 6. Performance Statistics

```bash
curl http://localhost:8080/api/performance/stats
```

**Response**:

```json
{
  "totalQueries": 150,
  "avgTotalTime": 2.8,
  "medianTotalTime": 2.5,
  "stepStatistics": {
    "Dense Search": {"avg": 0.3, "median": 0.28, "min": 0.2, "max": 0.5},
    "BM25 Search": {"avg": 0.2, "median": 0.18, "min": 0.15, "max": 0.3},
    "LLM Generation": {"avg": 1.5, "median": 1.4, "min": 0.8, "max": 3.2}
  }
}
```

---

## 🎛️ So sánh 2 chế độ

| Tính năng                | Base RAG                              | Deep Thinking                    |
| -------------------------- | ------------------------------------- | -------------------------------- |
| **Tốc độ**        | Nhanh                                 | Chậm hơn (5-8s)                |
| **Độ chính xác** | Tốt                                  | Rất tốt                        |
| **Use case**         | Câu hỏi đơn giản, tra cứu nhanh | Phân tích phức tạp, so sánh |
| **Context**          | 15 docs                               | 8 docs (đã lọc kỹ)           |
| **Keywords**         | Không có                            | Có (LLM extract)                |
| **Multi-query**      | Không                                | Có                              |

**Khi nào dùng Deep Thinking?**

- ✅ Câu hỏi phức tạp, nhiều khía cạnh
- ✅ Cần so sánh, phân tích
- ✅ Yêu cầu độ chính xác cao

**Khi nào dùng Base RAG?**

- ✅ Tra cứu nhanh
- ✅ Câu hỏi đơn giản, rõ ràng
- ✅ Cần response time thấp

---

## 📁 Cấu trúc Project

```
legal-rag-springboot/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── legalrag/
│       │           ├── config/              # Spring configs
│       │           ├── controller/          # REST controllers
│       │           ├── dto/                 # DTOs (request/response)
│       │           ├── service/
│       │           │   ├── data/            # Data loading
│       │           │   ├── deepthinking/    # Deep thinking mode
│       │           │   ├── embedding/       # Vietnamese embedding
│       │           │   ├── llm/             # Ollama LLM
│       │           │   ├── memory/          # Session management
│       │           │   ├── monitoring/      # Performance tracking
│       │           │   └── rag/             # RAG core (search, rerank, fusion)
│       │           └── util/                # Utilities (tokenizer, chunker...)
│       └── resources/
│           └── application.yml              # Spring Boot config
├── scripts/
│   ├── embedding_service.py                 # Python FastAPI embedding
│   └── faiss_service.py                     # FAISS search wrapper
├── data/                                    # Datasets
└── pom.xml                                  # Maven dependencies

```

## 📧 Contact

- **Email**: tominhducc@gmail.com
- **GitHub**: https://github.com/ghstmd/legal-rag-springboot

## 🙏 Acknowledgments

- [Spring AI](https://docs.spring.io/spring-ai/reference/) - AI framework
- [Ollama](https://ollama.com/) - LLM runtime
- [FAISS](https://github.com/facebookresearch/faiss) - Vector search
- [Sentence Transformers](https://www.sbert.net/) - Vietnamese embedding
