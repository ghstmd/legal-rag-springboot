package com.legalrag.service.embedding;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.legalrag.config.EmbeddingConfig;
import com.legalrag.exception.EmbeddingException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class VietnameseEmbeddingService {

    private final EmbeddingConfig embeddingConfig;
    private final WebClient embeddingWebClient;

    /**
     * Generate embedding via Python FastAPI
     * FIXED: Return type must match fallback method
     */
    @Cacheable(value = "embeddings", key = "#text.hashCode()")
    @CircuitBreaker(name = "embedding", fallbackMethod = "fallbackEmbedding")
    @Retry(name = "embedding")
    public List<Double> generateEmbedding(String text) {
        try {
            if (text == null || text.trim().isEmpty()) {
                throw new EmbeddingException("Text is empty");
            }

            log.debug("Calling Python embedding service");

            Map<String, Object> response =
                embeddingWebClient.post()
                    .uri("/embed")
                    .bodyValue(Map.of("texts", List.of(text)))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(embeddingConfig.getTimeoutSeconds()));

            if (response == null || !response.containsKey("embeddings")) {
                throw new EmbeddingException("Invalid response from embedding service");
            }

            @SuppressWarnings("unchecked")
            List<List<Double>> embeddings = (List<List<Double>>) response.get("embeddings");

            if (embeddings == null || embeddings.isEmpty()) {
                throw new EmbeddingException("Empty embedding response from Python service");
            }

            List<Double> vector = embeddings.get(0);

            if (vector.size() != embeddingConfig.getDimension()) {
                log.warn(
                    "Embedding dimension mismatch: expected {}, got {}",
                    embeddingConfig.getDimension(),
                    vector.size()
                );
            }

            return vector;

        } catch (Exception e) {
            log.error("Embedding service failed", e);
            throw new EmbeddingException("Failed to generate embedding", e);
        }
    }

    /**
     * Batch embeddings
     */
    @CircuitBreaker(name = "embedding", fallbackMethod = "fallbackBatchEmbeddings")
    @Retry(name = "embedding")
    public List<List<Double>> generateEmbeddings(List<String> texts) {
        try {
            if (texts == null || texts.isEmpty()) {
                return Collections.emptyList();
            }

            log.debug("Calling Python embedding service for {} texts", texts.size());

            Map<String, Object> response =
                embeddingWebClient.post()
                    .uri("/embed")
                    .bodyValue(Map.of("texts", texts))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(embeddingConfig.getTimeoutSeconds()));

            if (response == null || !response.containsKey("embeddings")) {
                throw new EmbeddingException("Invalid response from embedding service");
            }

            @SuppressWarnings("unchecked")
            List<List<Double>> embeddings = (List<List<Double>>) response.get("embeddings");

            return embeddings;

        } catch (Exception e) {
            log.error("Batch embedding failed", e);
            throw new EmbeddingException("Failed to generate batch embeddings", e);
        }
    }

    /**
     * FIXED: Fallback for single embedding - must return List<Double>
     */
    public List<Double> fallbackEmbedding(String text, Throwable ex) {
        log.warn("Using fallback embedding for text: '{}' - Error: {}", 
            text != null && text.length() > 50 ? text.substring(0, 50) + "..." : text,
            ex.getMessage()
        );
        
        int dimension = (embeddingConfig != null) ? embeddingConfig.getDimension() : 768;
        
        // Return zero vector as List<Double>
        List<Double> zeroVector = new ArrayList<>(dimension);
        for (int i = 0; i < dimension; i++) {
            zeroVector.add(0.0);
        }
        
        return zeroVector;
    }

    /**
     * FIXED: Fallback for batch embeddings - must return List<List<Double>>
     */
    public List<List<Double>> fallbackBatchEmbeddings(List<String> texts, Throwable ex) {
        log.warn("Using fallback batch embeddings for {} texts - Error: {}", 
            texts != null ? texts.size() : 0,
            ex.getMessage()
        );
        
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        
        int dimension = (embeddingConfig != null) ? embeddingConfig.getDimension() : 768;
        
        // Return zero vectors for all texts
        List<List<Double>> results = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            List<Double> zeroVector = new ArrayList<>(dimension);
            for (int j = 0; j < dimension; j++) {
                zeroVector.add(0.0);
            }
            results.add(zeroVector);
        }
        
        return results;
    }
}
