package com.legalrag.service.rag;

import com.legalrag.config.ModelConfig;
import com.legalrag.dto.internal.ContextItem;
import com.legalrag.dto.internal.RerankResult;
import com.legalrag.dto.internal.SearchResult;
import com.legalrag.dto.internal.TimingInfo;
import com.legalrag.dto.response.RagResponse;
import com.legalrag.service.llm.OllamaLlmService;
import com.legalrag.service.memory.SessionManagerService;
import com.legalrag.service.monitoring.PerformanceMonitorService;
import com.legalrag.service.monitoring.QueryTimerService;
import com.legalrag.util.PromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaseRagService {

    private final VectorSearchService vectorSearchService;
    private final BM25SearchService bm25SearchService;
    private final ScoreFusionService scoreFusionService;
    private final RerankService rerankService;
    private final OllamaLlmService llmService;
    private final SessionManagerService sessionManager;
    private final PerformanceMonitorService performanceMonitor;
    private final PromptBuilder promptBuilder;
    private final ModelConfig modelConfig;

    public RagResponse query(
            String question,
            String sessionId,
            Integer topK,
            Integer rerankTopK
    ) {
        QueryTimerService timer = new QueryTimerService();
        timer.start();

        topK = topK != null
                ? topK
                : modelConfig.getRag().getRetrieval().getTopK();
        rerankTopK = rerankTopK != null
                ? rerankTopK
                : modelConfig.getRag().getRetrieval().getRerankTopK();

        try {
            List<RerankResult> reranked = retrieve(question, topK, rerankTopK, timer);

            if (reranked.isEmpty()) {
                timer.end();
                return empty(question, sessionId, timer);
            }

            String chatHistory = sessionId != null
                    ? sessionManager.getRecentContext(sessionId, 3)
                    : null;

            String context = formatContext(reranked);

            String answer = llmService.generate(
                    promptBuilder.buildSystemPrompt(),
                    promptBuilder.buildStandardPrompt(question, context, chatHistory)
            );

            timer.end();

            sessionManager.addTurn(sessionId, question, answer, Map.of(
                    "mode", "base_rag",
                    "timing", timer.toMap()
            ));

            performanceMonitor.addQuery(question, timer.toMap(), "base_rag");

            return success(question, answer, reranked, sessionId, timer);

        } catch (Exception e) {
            timer.end();
            return error(question, e.getMessage(), sessionId, timer);
        }
    }

    /**
     * Core retrieval pipeline used by BaseRAG and DeepThinking
     */
    public List<RerankResult> retrieve(
            String question,
            int topK,
            int rerankTopK,
            QueryTimerService timer
    ) {
        List<SearchResult> dense = vectorSearchService.search(question, topK);
        timer.mark("Dense Search");

        List<SearchResult> sparse = bm25SearchService.search(question, topK);
        timer.mark("BM25 Search");

        List<SearchResult> fused = scoreFusionService.fuse(dense, sparse, topK);
        timer.mark("Score Fusion");

        List<RerankResult> reranked = rerankService.rerank(question, fused, rerankTopK);
        timer.mark("Reranking");

        return reranked;
    }

    private String formatContext(List<RerankResult> results) {
        return results.stream()
                .map(RerankResult::getText)
                .collect(Collectors.joining("\n\n"));
    }

    private RagResponse success(String q, String a, List<RerankResult> r,
                                String s, QueryTimerService t) {
        List<ContextItem> ctx = new ArrayList<>();
        for (int i = 0; i < r.size(); i++) {
            ctx.add(ContextItem.builder()
                    .rank(i + 1)
                    .chunkId(r.get(i).getChunkId())
                    .score(r.get(i).getScore())
                    .text(r.get(i).getText())
                    .build());
        }

        return RagResponse.builder()
                .question(q)
                .answer(a)
                .context(ctx)
                .timing(timing(t))
                .sessionId(s)
                .mode("base_rag")
                .build();
    }

    private RagResponse empty(String q, String s, QueryTimerService t) {
        return RagResponse.builder()
                .question(q)
                .answer("Không tìm thấy tài liệu.")
                .context(List.of())
                .timing(timing(t))
                .sessionId(s)
                .mode("base_rag")
                .build();
    }

    private RagResponse error(String q, String e, String s, QueryTimerService t) {
        return RagResponse.builder()
                .question(q)
                .answer("Lỗi: " + e)
                .context(List.of())
                .timing(timing(t))
                .sessionId(s)
                .mode("error")
                .build();
    }

    private TimingInfo timing(QueryTimerService t) {
        return TimingInfo.builder()
                .totalTime(t.getTotalTime())
                .stepDurations(t.getStepDurations())
                .timestamp(Instant.now().toString())
                .build();
    }
}