package com.legalrag.dto.response;

import com.legalrag.dto.internal.ContextItem;
import com.legalrag.dto.internal.TimingInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagResponse {

    // ================= CORE RESPONSE =================
    private String question;

    private String answer;

    private List<ContextItem> context;

    private TimingInfo timing;

    private String timingDisplay;

    private String sessionId;

    // ================= EXECUTION MODE =================
    /**
     * High-level execution mode
     * - base_rag
     * - graph_rag
     * - deep_thinking
     * - deep_thinking_graph
     */
    private String mode;

    // ================= DATASET METADATA =================
    /**
     * Dataset mode used
     * - mode-1 → Dense only
     * - mode-2 → Hybrid
     */
    private String datasetMode;

    /**
     * Alpha value actually used in score fusion
     * - 0.0 for dense only
     * - 0.6 for hybrid
     */
    private Double alphaUsed;

    // ================= ENHANCEMENT FLAGS =================
    /**
     * Whether GraphRAG was applied
     */
    private Boolean graphUsed;

    /**
     * Whether Deep Thinking (Agentic RAG) was applied
     */
    private Boolean deepThinkingUsed;
}
