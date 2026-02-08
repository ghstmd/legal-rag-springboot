package com.legalrag.service.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalrag.config.DatasetConfig;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * Simplified Data Loader Service - Single Dataset
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataLoaderService {

    private final DatasetConfig datasetConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Single dataset storage
    private final DatasetData dataset = new DatasetData();

    @Data
    public static class DatasetData {
        private List<Map<String, Object>> corpusData = new ArrayList<>();
        private List<Map<String, Object>> metadata = new ArrayList<>();
        private boolean loaded = false;
    }

    /**
     * Load dataset on startup
     */
    @PostConstruct
    public void init() {
        log.info("\n{}", "=".repeat(70));
        log.info("LOADING DATASET");
        log.info("{}\n", "=".repeat(70));
        
        loadDataset();
    }

    /**
     * Load the single dataset
     */
    public synchronized void loadDataset() {
        if (dataset.isLoaded()) {
            log.info("Dataset already loaded, skipping");
            return;
        }

        try {
            loadCorpus(datasetConfig.getCorpus(), dataset);
            loadMetadata(datasetConfig.getMetadata(), dataset);

            dataset.setLoaded(true);

            log.info("Dataset loaded successfully ({} chunks)", dataset.getCorpusData().size());

        } catch (Exception e) {
            log.error("Failed to load dataset: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to load dataset", e);
        }
    }


    public List<Map<String, Object>> getCorpusData() {
        if (!dataset.isLoaded()) {
            loadDataset();
        }
        return dataset.getCorpusData();
    }


    /**
     * Get metadata
     */
    public List<Map<String, Object>> getMetadata() {
        if (!dataset.isLoaded()) {
            loadDataset();
        }
        return dataset.getMetadata();
    }

    /**
     * Get metadata by chunk ID
     */
    public Map<String, Object> getMetadataByChunkId(String chunkId) {
        return getMetadata().stream()
                .filter(m -> chunkId.equals(String.valueOf(m.get("chunk_id"))))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get metadata by index
     */
    public Map<String, Object> getMetadataByIndex(int index) {
        List<Map<String, Object>> metadata = getMetadata();
        if (index < 0 || index >= metadata.size()) {
            log.warn("Invalid metadata index: {}", index);
            return null;
        }
        return metadata.get(index);
    }

    /**
     * Check if data is loaded
     */
    public boolean isDataLoaded() {
        return dataset.isLoaded();
    }

    /**
     * Get dataset statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("loaded", dataset.isLoaded());
        stats.put("corpusSize", dataset.getCorpusData().size());
        stats.put("metadataSize", dataset.getMetadata().size());
        stats.put("indexPath", datasetConfig.getIndex());
        stats.put("corpusPath", datasetConfig.getCorpus());
        return stats;
    }

    // ============================================================
    // Private Helper Methods
    // ============================================================

    private void loadCorpus(String corpusPath, DatasetData data) throws Exception {
        log.info("Loading corpus from: {}", corpusPath);

        Path path = Paths.get(corpusPath).toAbsolutePath();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Corpus file not found: " + path);
        }

        String content = Files.readString(path);

        List<Map<String, Object>> rawCorpus = objectMapper.readValue(
                content,
                objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, Map.class)
        );

        for (int i = 0; i < rawCorpus.size(); i++) {
            Map<String, Object> chunk = rawCorpus.get(i);
            Map<String, Object> processed = new HashMap<>();

            processed.put("index", i);

            String text = extractText(chunk);
            if (text == null || text.isBlank()) {
                continue;
            }

            processed.put("text", text);
            processed.put("chunk_id", extractChunkId(chunk, i));

            data.getCorpusData().add(processed);
        }

        log.info("Loaded {} corpus chunks", data.getCorpusData().size());
    }

    private void loadMetadata(String metadataPath, DatasetData data) throws Exception {
        log.info("Loading metadata from: {}", metadataPath);

        Path path = Paths.get(metadataPath).toAbsolutePath();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Metadata file not found: " + path);
        }

        try (Stream<String> lines = Files.lines(path)) {
            lines.filter(l -> !l.isBlank()).forEach(line -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> meta = objectMapper.readValue(line, Map.class);
                    data.getMetadata().add(meta);
                } catch (Exception e) {
                    log.warn("Failed to parse metadata line: {}", e.getMessage());
                }
            });
        }

        log.info("Loaded {} metadata entries", data.getMetadata().size());
    }

    // ============================================================
    // Helpers
    // ============================================================

    private String extractText(Map<String, Object> chunk) {
        for (String key : List.of("chunk_content", "text", "content")) {
            Object value = chunk.get(key);
            if (value instanceof String) {
                return (String) value;
            }
        }
        return null;
    }

    private String extractChunkId(Map<String, Object> chunk, int index) {
        Object id = chunk.get("chunk_id");
        if (id != null) {
            return String.valueOf(id);
        }

        if (chunk.containsKey("metadata")) {
            Object meta = chunk.get("metadata");
            if (meta instanceof Map) {
                Object metaId = ((Map<?, ?>) meta).get("chunk_id");
                if (metaId != null) {
                    return String.valueOf(metaId);
                }
            }
        }
        return "chunk_" + index;
    }
}