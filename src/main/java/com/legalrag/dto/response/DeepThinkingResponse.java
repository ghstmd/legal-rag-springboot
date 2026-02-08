package com.legalrag.dto.response;

import com.legalrag.dto.internal.ContextItem;
import com.legalrag.dto.internal.TimingInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepThinkingResponse {

    // ================= CORE RESPONSE =================
    private String question;

    private String answer;

    private List<ContextItem> context;

    // ================= DEEP THINKING DETAILS =================
    /**
     * Keywords extracted for multi-query reasoning
     */
    private List<String> keywords;

    /**
     * Internal reasoning / thinking trace (optional, for debug / research)
     */
    private Map<String, Object> thinkingProcess;

    // ================= TIMING =================
    private TimingInfo timing;

    private String timingDisplay;

    private String sessionId;

    // ================= EXECUTION MODE =================
    /**
     * High-level execution mode
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
     */
    private Double alphaUsed;

    // ================= ENHANCEMENT FLAGS =================
    /**
     * Whether GraphRAG was applied
     */
    private Boolean graphUsed;

    /**
     * Whether Deep Thinking (Agentic RAG) was applied
     * (always true for this response type, but kept for consistency)
     */
    private Boolean deepThinkingUsed;
}
