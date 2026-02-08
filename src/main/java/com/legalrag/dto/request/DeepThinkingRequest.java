package com.legalrag.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepThinkingRequest {

    // ================= CORE QUERY =================
    @NotBlank(message = "Query cannot be blank")
    private String query;

    // ================= DATASET MODE =================
    /**
     * Dataset mode selected from frontend
     * 1 = Dense only (dataset-1, alpha = 0.0)
     * 2 = Hybrid (dataset-2, alpha = 0.6)
     */
    @Min(value = 1, message = "Dataset mode must be 1 or 2")
    @Max(value = 2, message = "Dataset mode must be 1 or 2")
    @Builder.Default
    private Integer datasetMode = 2;

    // ================= DEEP THINKING PARAMETERS =================
    @Min(value = 10, message = "Top-K per keyword must be at least 10")
    @Max(value = 100, message = "Top-K per keyword cannot exceed 100")
    @Builder.Default
    private Integer topKPerKeyword = 30;

    @Min(value = 3, message = "Final Top-K must be at least 3")
    @Max(value = 20, message = "Final Top-K cannot exceed 20")
    @Builder.Default
    private Integer finalTopK = 8;

    // ================= ENHANCEMENT OPTIONS =================
    /**
     * Enable GraphRAG during Deep Thinking
     * - true  → Deep Thinking + GraphRAG
     * - false → Deep Thinking only (multi-query RAG)
     */
    @Builder.Default
    private Boolean useGraph = true;
}
