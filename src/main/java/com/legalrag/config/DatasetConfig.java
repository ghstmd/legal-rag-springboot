package com.legalrag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Single Dataset Configuration
 * No more multi-mode complexity - just one dataset
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "legal-rag.dataset")
public class DatasetConfig {

    /**
     * FAISS index path
     */
    private String index;

    /**
     * Metadata JSONL file path
     */
    private String metadata;

    /**
     * BM25 cache file path (combined unigram + bigram) - NEW
     */
    private String bm25Cache;

    /**
     * @deprecated Use bm25Cache instead
     */
    @Deprecated
    private String bm25Uni;

    /**
     * @deprecated Use bm25Cache instead
     */
    @Deprecated
    private String bm25Bi;

    /**
     * Corpus JSON file path
     */
    private String corpus;

    /**
     * Get dataset paths as DatasetPaths object
     * For backward compatibility with existing code
     */
    public DatasetPaths getDatasetPaths() {
        return DatasetPaths.builder()
                .datasetKey("default")
                .indexPath(index)
                .metadataPath(metadata)
                .bm25CachePath(bm25Cache)
                .bm25UniPath(bm25Uni)  // deprecated
                .bm25BiPath(bm25Bi)    // deprecated
                .corpusPath(corpus)
                .build();
    }

    /**
     * Get dataset paths by mode (for backward compatibility)
     * Always returns the same single dataset regardless of mode
     * 
     * @deprecated Use getDatasetPaths() instead
     */
    @Deprecated
    public DatasetPaths getDatasetPaths(int mode) {
        if (mode != 1 && mode != 2) {
            log.warn("Dataset mode {} ignored - using single dataset configuration", mode);
        }
        return getDatasetPaths();
    }

    // ============================================================
    // DatasetPaths DTO
    // ============================================================
    @Data
    @Builder
    public static class DatasetPaths {

        /**
         * Dataset key (always "default" in single-dataset mode)
         */
        private String datasetKey;

        /**
         * FAISS index path
         */
        private String indexPath;

        /**
         * Metadata jsonl path
         */
        private String metadataPath;

        /**
         * BM25 cache path (combined) - NEW
         */
        private String bm25CachePath;

        /**
         * @deprecated Use bm25CachePath instead
         */
        @Deprecated
        private String bm25UniPath;

        /**
         * @deprecated Use bm25CachePath instead
         */
        @Deprecated
        private String bm25BiPath;

        /**
         * Corpus json path
         */
        private String corpusPath;
    }
}