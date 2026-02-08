package com.legalrag.service.rag;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service to interact with FAISS index via Python bridge
 * Simplified - Single Dataset Mode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaissSearchService {

    private final ObjectMapper objectMapper;
    private static final String PYTHON_SCRIPT = "scripts/faiss_service.py";

    /**
     * Search FAISS index using Python subprocess
     */
    public FaissSearchResult search(String indexPath, List<Double> queryEmbedding, int topK) {
        try {
            // 1. Verify Python script exists
            if (!Files.exists(Paths.get(PYTHON_SCRIPT))) {
                log.error("Python FAISS script not found: {}", PYTHON_SCRIPT);
                return FaissSearchResult.empty();
            }

            // 2. Verify FAISS index exists
            if (!Files.exists(Paths.get(indexPath))) {
                log.error("FAISS index not found: {}", indexPath);
                return FaissSearchResult.empty();
            }

            // 3. Build command
            List<String> command = new ArrayList<>();
            command.add("python");
            command.add(PYTHON_SCRIPT);
            command.add(indexPath);
            command.add(objectMapper.writeValueAsString(queryEmbedding));
            command.add(String.valueOf(topK));

            log.debug("Executing FAISS search: {} dimensions, top-{}",
                    queryEmbedding.size(), topK);

            // 4. Execute Python process
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            // 5. Read stdout (JSON result)
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            // 6. Read stderr (warnings/debug)
            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            }

            if (errorOutput.length() > 0) {
                log.warn("FAISS Python warnings:\n{}", errorOutput);
            }

            // 7. Wait for completion
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("FAISS search failed with exit code {}", exitCode);
                log.error("Stderr: {}", errorOutput);
                log.error("Stdout: {}", output);
                return FaissSearchResult.empty();
            }

            // 8. Parse JSON result
            String jsonOutput = output.toString().trim();
            if (jsonOutput.isEmpty()) {
                log.error("FAISS search returned empty output");
                return FaissSearchResult.empty();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(jsonOutput, Map.class);

            if (result.containsKey("error")) {
                log.error("FAISS search error: {}", result.get("error"));
                return FaissSearchResult.empty();
            }

            // 9. Extract core results (with null safety)
            @SuppressWarnings("unchecked")
            List<Integer> indices = (List<Integer>) result.get("indices");
            
            @SuppressWarnings("unchecked")
            List<Number> scoresRaw = (List<Number>) result.get("scores");

            // Validate required fields
            if (indices == null || scoresRaw == null) {
                log.error("FAISS result missing required fields");
                log.error("Available keys: {}", result.keySet());
                log.error("Full JSON: {}", jsonOutput);
                return FaissSearchResult.empty();
            }

            // Validate sizes match
            if (indices.size() != scoresRaw.size()) {
                log.error("FAISS result size mismatch: indices={}, scores={}", 
                         indices.size(), scoresRaw.size());
                return FaissSearchResult.empty();
            }

            // Convert Number to Double (JSON may return Integer or Double)
            List<Double> scores = scoresRaw.stream()
                    .map(Number::doubleValue)
                    .toList();

            // Optional fields (may not exist in basic Python script)
            @SuppressWarnings("unchecked")
            List<Number> distancesRaw = (List<Number>) result.get("distances");
            List<Double> distances = distancesRaw != null 
                    ? distancesRaw.stream().map(Number::doubleValue).toList()
                    : scores; // Fallback: use scores as distances

            String metricType = (String) result.get("metric_type");

            // 10. Debug logging
            log.debug("FAISS search complete:");
            log.debug("  - Results: {}", indices.size());
            log.debug("  - Top score: {}", scores.isEmpty() ? "N/A" : String.format("%.4f", scores.get(0)));
            if (metricType != null) {
                log.debug("  - Metric: {}", metricType);
            }

            // 11. Build result
            return FaissSearchResult.builder()
                    .indices(indices)
                    .scores(scores)
                    .distances(distances)
                    .metricType(metricType)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("FAISS search exception: {}", e.getMessage(), e);
            return FaissSearchResult.empty();
        }
    }

    /**
     * Check if FAISS search is available
     */
    public boolean isAvailable() {
        try {
            Process process = new ProcessBuilder("python", "--version").start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.warn("Python not found in PATH");
                return false;
            }

            if (!Files.exists(Paths.get(PYTHON_SCRIPT))) {
                log.warn("FAISS Python script not found: {}", PYTHON_SCRIPT);
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("Failed to check FAISS availability: {}", e.getMessage());
            return false;
        }
    }

    /**
     * FAISS search result wrapper
     */
    @lombok.Data
    @lombok.Builder
    public static class FaissSearchResult {
        private List<Integer> indices;
        private List<Double> scores;
        private List<Double> distances;
        private String metricType;
        private boolean success;

        public static FaissSearchResult empty() {
            return FaissSearchResult.builder()
                    .indices(List.of())
                    .scores(List.of())
                    .distances(List.of())
                    .success(false)
                    .build();
        }

        public boolean isEmpty() {
            return indices == null || indices.isEmpty();
        }

        public int size() {
            return indices == null ? 0 : indices.size();
        }
    }
}