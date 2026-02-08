package com.legalrag.service.deepthinking;

import com.legalrag.config.ModelConfig;
import com.legalrag.dto.internal.ContextItem;
import com.legalrag.dto.internal.TimingInfo;
import com.legalrag.dto.response.DeepThinkingResponse;
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
public class DeepThinkingService {

    private final KeywordExtractionService keywordExtractionService;
    private final MultiQuerySearchService multiQuerySearchService;
    private final OllamaLlmService llmService;
    private final SessionManagerService sessionManager;
    private final PerformanceMonitorService performanceMonitor;
    private final PromptBuilder promptBuilder;
    private final ModelConfig modelConfig;

    /**
     * Agentic Deep Thinking pipeline (NO GRAPH)
     */
    public DeepThinkingResponse query(String question,
                                     String sessionId,
                                     Integer topKPerKeyword,
                                     Integer finalTopK) {

        QueryTimerService timer = new QueryTimerService();
        timer.start();

        log.info("Deep Thinking query: {}", truncate(question, 80));

        try {
            topKPerKeyword = topKPerKeyword != null
                ? topKPerKeyword
                : modelConfig.getTopKPerKeyword();

            finalTopK = finalTopK != null
                ? finalTopK
                : modelConfig.getFinalTopK();

            /* =========================
               STEP 1: KEYWORD EXTRACTION
               ========================= */
            KeywordExtractionService.KeywordResult keywordResult =
                keywordExtractionService.extractKeywords(question);
            timer.mark("Keyword Extraction");

            List<String> keywords = keywordResult.getAllKeywords();
            if (keywords.isEmpty()) {
                keywords = List.of(question);
            }

            if (keywords.size() > modelConfig.getMaxKeywords()) {
                keywords = keywords.subList(0, modelConfig.getMaxKeywords());
            }

            log.info("Keywords: {}", keywords);

            /* =========================
               STEP 2: MULTI-QUERY SEARCH
               ========================= */
            List<MultiQuerySearchService.MergedResult> mergedResults =
                multiQuerySearchService.searchMultipleKeywords(
                    keywords,
                    topKPerKeyword,
                    finalTopK,
                    timer
                );

            if (mergedResults.isEmpty()) {
                timer.end();
                return buildEmptyResponse(question, keywords, sessionId, timer);
            }

            /* =========================
               STEP 3: BUILD CONTEXT
               ========================= */
            String context = buildContext(
                keywordResult,
                mergedResults,
                keywords
            );
            timer.mark("Context Building");

            /* =========================
               STEP 4: CHAT HISTORY
               ========================= */
            String chatHistory = null;
            if (sessionId != null) {
                chatHistory = sessionManager.getRecentContext(sessionId, 2);
                timer.mark("Chat History");
            }

            /* =========================
               STEP 5: PROMPT BUILDING
               ========================= */
            Map<String, Object> thinkingProcess = buildThinkingProcess(
                keywordResult,
                keywords,
                mergedResults
            );

            String systemPrompt = promptBuilder.buildSystemPrompt();
            String userPrompt = promptBuilder.buildDeepThinkingPrompt(
                question,
                thinkingProcess,
                context,
                chatHistory
            );
            timer.mark("Prompt Building");

            /* =========================
               STEP 6: LLM GENERATION
               ========================= */
            String answer = generateWithFallback(
                systemPrompt,
                userPrompt,
                mergedResults
            );
            timer.mark("LLM Generation");

            timer.end();

            /* =========================
               MEMORY & MONITORING
               ========================= */
            if (sessionId != null) {
                sessionManager.addTurn(
                    sessionId,
                    question,
                    answer,
                    Map.of(
                        "mode", "deep_thinking",
                        "keywords", keywords,
                        "timing", timer.toMap()
                    )
                );
            }

            performanceMonitor.addQuery(
                question,
                timer.toMap(),
                "deep_thinking"
            );

            return buildResponse(
                question,
                answer,
                mergedResults,
                keywords,
                thinkingProcess,
                sessionId,
                timer
            );

        } catch (Exception e) {
            log.error("DeepThinking error", e);
            timer.end();
            return buildErrorResponse(question, e.getMessage(), sessionId, timer);
        }
    }

    /* ============================================================
       CONTEXT BUILDING
       ============================================================ */

    private String buildContext(KeywordExtractionService.KeywordResult keywordResult,
                                List<MultiQuerySearchService.MergedResult> results,
                                List<String> keywords) {

        StringBuilder sb = new StringBuilder();

        sb.append("=== PHÂN TÍCH ===\n");
        sb.append("Keywords: ").append(String.join(", ", keywords)).append("\n");

        String reasoning = keywordResult.getReasoning();
        if (reasoning != null && reasoning.length() > 150) {
            reasoning = reasoning.substring(0, 150) + "...";
        }
        sb.append("Lý do: ").append(reasoning).append("\n\n");

        sb.append("=== VĂN BẢN LIÊN QUAN ===\n\n");

        for (int i = 0; i < results.size(); i++) {
            var r = results.get(i);
            sb.append(String.format("[%d] (Score %.2f, Appears %d)\n",
                i + 1,
                r.getFinalScore(),
                r.getAppearanceCount()
            ));
            sb.append(truncate(r.getText(), 400)).append("\n\n");
        }

        return sb.toString();
    }

    /* ============================================================
       RESPONSE BUILDERS
       ============================================================ */

    private DeepThinkingResponse buildResponse(
        String question,
        String answer,
        List<MultiQuerySearchService.MergedResult> results,
        List<String> keywords,
        Map<String, Object> thinkingProcess,
        String sessionId,
        QueryTimerService timer
    ) {

        List<ContextItem> context = results.stream()
            .map(r -> ContextItem.builder()
                .rank(results.indexOf(r) + 1)
                .score(r.getFinalScore())
                .chunkId(r.getChunkId())
                .text(truncate(r.getText(), 300))
                .source("multi_query")
                .build())
            .collect(Collectors.toList());

        return DeepThinkingResponse.builder()
            .question(question)
            .answer(answer)
            .context(context)
            .keywords(keywords)
            .thinkingProcess(thinkingProcess)
            .timing(buildTiming(timer))
            .timingDisplay(timer.formatDisplay())
            .sessionId(sessionId)
            .mode("deep_thinking")
            .build();
    }

    private DeepThinkingResponse buildEmptyResponse(
        String question,
        List<String> keywords,
        String sessionId,
        QueryTimerService timer
    ) {

        return DeepThinkingResponse.builder()
            .question(question)
            .answer("Không tìm thấy tài liệu liên quan.")
            .context(List.of())
            .keywords(keywords)
            .thinkingProcess(Map.of(
                "keywords", keywords,
                "total_docs_found", 0
            ))
            .timing(buildTiming(timer))
            .timingDisplay(timer.formatDisplay())
            .sessionId(sessionId)
            .mode("deep_thinking")
            .build();
    }

    private DeepThinkingResponse buildErrorResponse(
        String question,
        String error,
        String sessionId,
        QueryTimerService timer
    ) {

        return DeepThinkingResponse.builder()
            .question(question)
            .answer("Đã xảy ra lỗi: " + error)
            .context(List.of())
            .keywords(List.of())
            .thinkingProcess(Map.of("error", error))
            .timing(buildTiming(timer))
            .timingDisplay(timer.formatDisplay())
            .sessionId(sessionId)
            .mode("error")
            .build();
    }

    private TimingInfo buildTiming(QueryTimerService timer) {
        return TimingInfo.builder()
            .totalTime(timer.getTotalTime())
            .stepDurations(timer.getStepDurations())
            .timestamp(Instant.now().toString())
            .build();
    }

    /* ============================================================
       HELPERS
       ============================================================ */

    private Map<String, Object> buildThinkingProcess(
        KeywordExtractionService.KeywordResult keywordResult,
        List<String> keywords,
        List<MultiQuerySearchService.MergedResult> results
    ) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("keywords", keywords);
        map.put("reasoning", keywordResult.getReasoning());
        map.put("searches_performed", keywords.size());
        map.put("total_docs_found", results.size());
        return map;
    }

    private String generateWithFallback(String systemPrompt,
                                        String userPrompt,
                                        List<MultiQuerySearchService.MergedResult> results) {

        try {
            return llmService.generate(systemPrompt, userPrompt);
        } catch (Exception e) {
            log.warn("Primary LLM failed, fallback activated");
            return llmService.generate(
                "Câu hỏi:\n" + userPrompt +
                "\n\nTài liệu:\n" + truncate(results.get(0).getText(), 400)
            );
        }
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
