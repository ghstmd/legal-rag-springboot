package com.legalrag.service.rag;

import com.legalrag.config.DatasetConfig;
import com.legalrag.dto.internal.SearchResult;
import com.legalrag.service.data.DataLoaderService;
import com.legalrag.service.embedding.VietnameseEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final VietnameseEmbeddingService embeddingService;
    private final DataLoaderService dataLoaderService;
    private final FaissSearchService faissSearchService;
    private final DatasetConfig datasetConfig;

    private FaissIndexInfo indexInfo;

    public List<SearchResult> search(String query, int topK) {
        if (indexInfo == null) {
            indexInfo = loadFaissIndexInfo();
        }

        if (!indexInfo.available) {
            log.warn("FAISS index not available");
            return List.of();
        }

        try {
            List<Double> queryEmbedding = embeddingService.generateEmbedding(query);
            if (queryEmbedding == null || queryEmbedding.isEmpty()) {
                return List.of();
            }

            List<Integer> indices = searchFaissIndex(
                    indexInfo.indexPath, queryEmbedding, topK
            );

            if (indices.isEmpty()) {
                return List.of();
            }

            return convertIndicesToResults(indices);

        } catch (Exception e) {
            log.error("Vector search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private FaissIndexInfo loadFaissIndexInfo() {
        Path indexPath = Path.of(datasetConfig.getIndex()).toAbsolutePath();

        FaissIndexInfo info = new FaissIndexInfo();
        info.indexPath = indexPath.toString();

        if (Files.exists(indexPath)) {
            log.info("FAISS index found: {}", indexPath);
            info.available = true;
        } else {
            log.error("FAISS index missing: {}", indexPath);
            info.available = false;
        }

        return info;
    }

    private List<Integer> searchFaissIndex(
            String indexPath,
            List<Double> queryEmbedding,
            int topK
    ) {
        FaissSearchService.FaissSearchResult result =
                faissSearchService.search(indexPath, queryEmbedding, topK);

        if (!result.isSuccess()) {
            log.error("FAISS search failed for index {}", indexPath);
            return List.of();
        }
        return result.getIndices();
    }

    private List<SearchResult> convertIndicesToResults(List<Integer> indices) {
        List<Map<String, Object>> corpus = dataLoaderService.getCorpusData();

        if (corpus == null || corpus.isEmpty()) {
            log.error("Corpus not loaded");
            return List.of();
        }

        List<SearchResult> results = new ArrayList<>();

        for (int rank = 0; rank < indices.size(); rank++) {
            int corpusIndex = indices.get(rank);

            if (corpusIndex < 0 || corpusIndex >= corpus.size()) {
                continue;
            }

            Map<String, Object> chunk = corpus.get(corpusIndex);

            double score = Math.max(0.1, 1.0 - (rank * 0.05));

            results.add(SearchResult.builder()
                    .index(rank)
                    .chunkId(String.valueOf(chunk.get("chunk_id")))
                    .text((String) chunk.get("text"))
                    .score(score)
                    .source("faiss")
                    .build());
        }

        return results;
    }

    private static class FaissIndexInfo {
        String indexPath;
        boolean available;
    }

    public void clearCache() {
        indexInfo = null;
    }
}