package com.legalrag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import lombok.Data;

/**
 * Simplified Model Configuration
 * Removed multi-dataset mode complexity
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "legal-rag")
public class ModelConfig {

    // Dataset config is now in separate DatasetConfig class
    // This class only handles RAG, LLM, Session, Monitoring configs

    private Rag rag;
    private Llm llm;
    private Session session;
    private Monitoring monitoring;

    // ============================================================
    // RAG Configuration
    // ============================================================
    @Data
    public static class Rag {
        private Retrieval retrieval;
        private DeepThinking deepThinking;
    }

    @Data
    public static class Retrieval {
        private Integer topK;
        private Integer rerankTopK;
        private Double alpha;  // Hybrid weight (dense vs sparse)
    }

    @Data
    public static class DeepThinking {
        private Boolean enabled;
        private Integer topKPerKeyword;
        private Integer finalTopK;
        private Integer maxKeywords;
        private Integer timeoutSeconds;
    }

    // ============================================================
    // LLM Configuration
    // ============================================================
    @Data
    public static class Llm {
        private Integer maxTokens;
        private Integer timeoutSeconds;
    }

    // ============================================================
    // Session Configuration
    // ============================================================
    @Data
    public static class Session {
        private Integer maxHistory;
        private Integer maxContextTurns;
    }

    // ============================================================
    // Monitoring Configuration
    // ============================================================
    @Data
    public static class Monitoring {
        private Boolean enabled;
        private Integer maxQueryHistory;
    }

    // ============================================================
    // Convenience Getters
    // ============================================================

    public Integer getTopK() {
        return rag != null && rag.getRetrieval() != null
                ? rag.getRetrieval().getTopK()
                : 100;
    }

    public Integer getRerankTopK() {
        return rag != null && rag.getRetrieval() != null
                ? rag.getRetrieval().getRerankTopK()
                : 15;
    }

    public Double getAlpha() {
        return rag != null && rag.getRetrieval() != null
                ? rag.getRetrieval().getAlpha()
                : 0.6;
    }

    public Integer getTopKPerKeyword() {
        return rag != null && rag.getDeepThinking() != null
                ? rag.getDeepThinking().getTopKPerKeyword()
                : 30;
    }

    public Integer getFinalTopK() {
        return rag != null && rag.getDeepThinking() != null
                ? rag.getDeepThinking().getFinalTopK()
                : 8;
    }

    public Integer getMaxKeywords() {
        return rag != null && rag.getDeepThinking() != null
                ? rag.getDeepThinking().getMaxKeywords()
                : 10;
    }

    public Integer getDeepThinkingTimeout() {
        return rag != null && rag.getDeepThinking() != null
                ? rag.getDeepThinking().getTimeoutSeconds()
                : 180;
    }

    public Integer getMaxSessionHistory() {
        return session != null ? session.getMaxHistory() : 20;
    }

    public Integer getMaxContextTurns() {
        return session != null ? session.getMaxContextTurns() : 5;
    }

    // ============================================================
    // Initialization & Logging
    // ============================================================

    @PostConstruct
    public void init() {
        System.out.println("=".repeat(70));
        System.out.println("MODEL CONFIGURATION INITIALIZED");
        System.out.println("=".repeat(70));

        // RAG Configuration
        if (rag != null) {
            System.out.println("\n RAG CONFIGURATION:");
            if (rag.getRetrieval() != null) {
                System.out.printf("  - Default TopK: %d\n", rag.getRetrieval().getTopK());
                System.out.printf("  - Rerank TopK: %d\n", rag.getRetrieval().getRerankTopK());
                System.out.printf("  - Alpha (Dense weight): %.1f\n", rag.getRetrieval().getAlpha());
            }
            if (rag.getDeepThinking() != null) {
                System.out.printf("  - Deep Thinking Enabled: %s\n", rag.getDeepThinking().getEnabled());
                System.out.printf("  - Max Keywords: %d\n", rag.getDeepThinking().getMaxKeywords());
            }
        }

        // LLM Configuration
        if (llm != null) {
            System.out.println("\nLLM CONFIGURATION:");
            System.out.printf("  - Max Tokens: %d\n", llm.getMaxTokens());
            System.out.printf("  - Timeout: %ds\n", llm.getTimeoutSeconds());
        }

        // Session Configuration
        if (session != null) {
            System.out.println("\nSESSION CONFIGURATION:");
            System.out.printf("  - Max History: %d turns\n", session.getMaxHistory());
            System.out.printf("  - Max Context: %d turns\n", session.getMaxContextTurns());
        }

        System.out.println("\n" + "=".repeat(70));
    }
}