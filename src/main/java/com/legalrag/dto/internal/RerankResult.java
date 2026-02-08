package com.legalrag.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankResult {
    
    private Double score;
    
    private Integer index;
    
    private String chunkId;
    
    private String text;
    
    public static RerankResult from(SearchResult searchResult, Double rerankScore) {
        return RerankResult.builder()
            .score(rerankScore)
            .index(searchResult.getIndex())
            .chunkId(searchResult.getChunkId())
            .text(searchResult.getText())
            .build();
    }
}
