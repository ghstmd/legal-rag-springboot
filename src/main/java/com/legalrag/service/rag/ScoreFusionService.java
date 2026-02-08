package com.legalrag.service.rag;

import com.legalrag.config.ModelConfig;
import com.legalrag.dto.internal.SearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScoreFusionService {

    private final ModelConfig modelConfig;

    public List<SearchResult> fuse(
            List<SearchResult> dense,
            List<SearchResult> sparse,
            int topK
    ) {
        double alpha = modelConfig.getAlpha();

        Map<String, SearchResult> map = new HashMap<>();

        dense.forEach(r -> map.put(r.getChunkId(), r));

        sparse.forEach(r ->
                map.merge(
                        r.getChunkId(),
                        r,
                        (d, s) -> d.toBuilder()
                                .score((1 - alpha) * d.getScore() + alpha * s.getScore())
                                .build()
                )
        );

        return map.values().stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(topK)
                .collect(Collectors.toList());
    }
}