package com.legalrag.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    private Integer index;
    private String chunkId;
    private String text;
    private Double score;
    private String source;
}
