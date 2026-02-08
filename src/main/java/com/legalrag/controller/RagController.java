package com.legalrag.controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.legalrag.config.ModelConfig;
import com.legalrag.dto.request.QueryRequest;
import com.legalrag.dto.response.DeepThinkingResponse;
import com.legalrag.dto.response.RagResponse;
import com.legalrag.service.deepthinking.DeepThinkingService;
import com.legalrag.service.memory.ConversationMemoryService;
import com.legalrag.service.memory.SessionManagerService;
import com.legalrag.service.monitoring.PerformanceMonitorService;
import com.legalrag.service.rag.BaseRagService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001", "http://127.0.0.1:3000"})
public class RagController {

    private final BaseRagService baseRagService;
    private final DeepThinkingService deepThinkingService;
    private final SessionManagerService sessionManager;
    private final PerformanceMonitorService performanceMonitor;
    private final ModelConfig modelConfig;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        log.debug("Health check requested");
        
        Map<String, Object> health = new HashMap<>();
        health.put("status", "healthy");
        health.put("timestamp", Instant.now().toString());
        health.put("service", "Legal RAG API");
        
        Map<String, Boolean> features = new HashMap<>();
        try {
            features.put("deep_thinking", 
                modelConfig.getRag() != null && 
                modelConfig.getRag().getDeepThinking() != null &&
                modelConfig.getRag().getDeepThinking().getEnabled()
            );
        } catch (Exception e) {
            log.warn("Failed to get feature status: {}", e.getMessage());
            features.put("deep_thinking", false);
        }
        health.put("features", features);
        
        try {
            health.put("active_sessions", sessionManager.getSessionCount());
        } catch (Exception e) {
            health.put("active_sessions", 0);
        }
        
        return ResponseEntity.ok(health);
    }

    @PostMapping("/query")
    public ResponseEntity<?> query(@Valid @RequestBody QueryRequest request) {
        log.info("Query received: {}", request.getQuestion());
        log.info("Request params - useDeepThinking: {}", request.getUseDeepThinking());

        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : UUID.randomUUID().toString();

        boolean useDeep = Boolean.TRUE.equals(request.getUseDeepThinking());

        try {
            if (!useDeep) {
                log.info("Processing with Base RAG");
                RagResponse response = baseRagService.query(
                        request.getQuestion(),
                        sessionId,
                        request.getTopK(),
                        request.getRerankTopK()
                );
                return ResponseEntity.ok(response);
            }

            log.info("Processing with Deep Thinking");
            DeepThinkingResponse response = deepThinkingService.query(
                    request.getQuestion(),
                    sessionId,
                    request.getTopK(),
                    request.getRerankTopK()
            );
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Query processing failed for: {}", request.getQuestion(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                        "error", e.getMessage(), 
                        "sessionId", sessionId,
                        "question", request.getQuestion()
                    ));
        }
    }


    // ===== SESSION MANAGEMENT =====

    @GetMapping("/session/{sessionId}/history")
    public ResponseEntity<?> history(@PathVariable String sessionId) {
        log.debug("History requested for session: {}", sessionId);
        
        if (!sessionManager.sessionExists(sessionId)) {
            return ResponseEntity.notFound().build();
        }

        List<ConversationMemoryService.ConversationTurn> turns =
                sessionManager.getFullConversation(sessionId);

        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "totalTurns", turns.size(),
                "conversations", turns
        ));
    }

    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<?> deleteSession(@PathVariable String sessionId) {
        log.info("Deleting session: {}", sessionId);
        sessionManager.deleteSession(sessionId);
        return ResponseEntity.ok(Map.of("status", "deleted", "sessionId", sessionId));
    }

    @GetMapping("/sessions")
    public ResponseEntity<?> listSessions() {
        List<String> sessions = sessionManager.listSessions();
        log.debug("Active sessions: {}", sessions.size());
        return ResponseEntity.ok(Map.of("sessions", sessions, "total", sessions.size()));
    }

    // ===== PERFORMANCE MONITORING =====

    @GetMapping("/performance/stats")
    public ResponseEntity<?> performanceStats() {
        log.debug("Performance stats requested");
        return ResponseEntity.ok(performanceMonitor.getStatistics());
    }

    @GetMapping("/performance/history")
    public ResponseEntity<?> performanceHistory() {
        var history = performanceMonitor.getQueryHistory();
        return ResponseEntity.ok(Map.of("totalQueries", history.size(), "history", history));
    }
}