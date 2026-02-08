package com.legalrag.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextItem {
    
    private Integer rank;
    
    private Double score;
    
    private String chunkId;
    
    private String text;
    
    private String source;  // vector_search, graph_expansion, etc.
    
    private String article;  // For graph results
    
    private String lawName;  // For graph results
    
    private Integer referenceCount;  // For graph results
}
