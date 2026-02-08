package com.legalrag.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueryRequest {

    @NotBlank
    private String question;

    private String sessionId;

    private Integer datasetMode;
    private Integer topK;
    private Integer rerankTopK;

    private Boolean useGraphRag;
    private Boolean useDeepThinking;
}
