package com.legalrag.service.deepthinking;

import com.legalrag.dto.internal.SearchResult;
import com.legalrag.service.monitoring.QueryTimerService;
import com.legalrag.service.rag.BaseRagService;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiQuerySearchService {

    private final BaseRagService baseRagService;

    public List<MergedResult> searchMultipleKeywords(
            List<String> keywords,
            int topKPerKeyword,
            int finalTopK,
            QueryTimerService timer
    ) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }

        List<List<SearchResult>> allResults = new ArrayList<>();

        for (String keyword : keywords) {
            try {
                var reranked = baseRagService.retrieve(
                        keyword,
                        topKPerKeyword,
                        topKPerKeyword,
                        timer
                );

                List<SearchResult> converted = reranked.stream()
                        .map(r -> SearchResult.builder()
                                .chunkId(r.getChunkId())
                                .index(r.getIndex())
                                .text(r.getText())
                                .score(r.getScore())
                                .source("multi_query")
                                .build())
                        .collect(Collectors.toList());

                allResults.add(converted);

            } catch (Exception e) {
                log.warn("Keyword search failed: {}", keyword, e);
            }
        }

        return mergeAndRerank(allResults, finalTopK);
    }

    private List<MergedResult> mergeAndRerank(
            List<List<SearchResult>> allResults,
            int topK
    ) {
        Map<String, ChunkAgg> map = new HashMap<>();

        for (List<SearchResult> list : allResults) {
            for (SearchResult r : list) {
                map.computeIfAbsent(r.getChunkId(), k -> new ChunkAgg(r))
                        .add(r.getScore());
            }
        }

        return map.values().stream()
                .map(ChunkAgg::toMerged)
                .sorted((a, b) -> Double.compare(b.finalScore, a.finalScore))
                .limit(topK)
                .collect(Collectors.toList());
    }

    private static class ChunkAgg {
        SearchResult base;
        List<Double> scores = new ArrayList<>();

        ChunkAgg(SearchResult r) {
            this.base = r;
        }

        void add(double s) {
            scores.add(s);
        }

        MergedResult toMerged() {
            double max = scores.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double finalScore = max * 0.6 + avg * 0.3 + scores.size() * 0.1;

            return MergedResult.builder()
                    .chunkId(base.getChunkId())
                    .index(base.getIndex())
                    .text(base.getText())
                    .maxScore(max)
                    .avgScore(avg)
                    .finalScore(finalScore)
                    .appearanceCount(scores.size())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MergedResult {
        private String chunkId;
        private Integer index;
        private String text;
        private Double finalScore;
        private Double maxScore;
        private Double avgScore;
        private Integer appearanceCount;
    }
}