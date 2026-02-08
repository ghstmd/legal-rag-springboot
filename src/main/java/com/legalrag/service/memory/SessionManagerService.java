package com.legalrag.service.memory;

import com.legalrag.config.ModelConfig;
import com.legalrag.service.embedding.VietnameseEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionManagerService {
    
    private final VietnameseEmbeddingService embeddingService;
    private final ModelConfig modelConfig;
    
    private final Map<String, ConversationMemoryService> sessions = new ConcurrentHashMap<>();
    
    /**
     * Get or create a session
     */
    public ConversationMemoryService getSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, id -> {
            log.info("Creating new session: {}", id);
            return new ConversationMemoryService(
                embeddingService,
                modelConfig.getMaxSessionHistory(),
                modelConfig.getMaxContextTurns()
            );
        });
    }
    
    /**
     * Check if session exists
     */
    public boolean sessionExists(String sessionId) {
        return sessions.containsKey(sessionId);
    }
    
    /**
     * Delete a session
     */
    public void deleteSession(String sessionId) {
        ConversationMemoryService removed = sessions.remove(sessionId);
        if (removed != null) {
            log.info("Deleted session: {}", sessionId);
        } else {
            log.warn("Attempted to delete non-existent session: {}", sessionId);
        }
    }
    
    /**
     * Clear session history but keep the session
     */
    public void clearSession(String sessionId) {
        ConversationMemoryService session = sessions.get(sessionId);
        if (session != null) {
            session.clear();
            log.info("Cleared session history: {}", sessionId);
        } else {
            log.warn("Attempted to clear non-existent session: {}", sessionId);
        }
    }
    
    /**
     * List all active sessions
     */
    public List<String> listSessions() {
        return new ArrayList<>(sessions.keySet());
    }
    
    /**
     * Get session count
     */
    public int getSessionCount() {
        return sessions.size();
    }
    
    /**
     * Add a conversation turn to a session
     */
    public void addTurn(String sessionId, String question, String answer, Map<String, Object> metadata) {
        ConversationMemoryService session = getSession(sessionId);
        session.addTurn(question, answer, metadata);
    }
    
    /**
     * Get recent context from a session
     */
    public String getRecentContext(String sessionId, int n) {
        if (!sessionExists(sessionId)) {
            return "";
        }
        
        ConversationMemoryService session = getSession(sessionId);
        return session.getRecentContext(n);
    }
    
    /**
     * Get enhanced context (recent + relevant) from a session
     */
    public String getEnhancedContext(String sessionId, String currentQuery) {
        if (!sessionExists(sessionId)) {
            return "";
        }
        
        ConversationMemoryService session = getSession(sessionId);
        return session.buildEnhancedContext(currentQuery);
    }
    
    /**
     * Get full conversation history from a session
     */
    public List<ConversationMemoryService.ConversationTurn> getFullConversation(String sessionId) {
        if (!sessionExists(sessionId)) {
            return List.of();
        }
        
        ConversationMemoryService session = getSession(sessionId);
        return session.getFullConversation();
    }
    
    /**
     * Get session statistics
     */
    public Map<String, Object> getSessionStatistics(String sessionId) {
        if (!sessionExists(sessionId)) {
            return Map.of("error", "Session not found");
        }
        
        ConversationMemoryService session = getSession(sessionId);
        Map<String, Object> stats = new HashMap<>(session.getStatistics());
        stats.put("sessionId", sessionId);
        return stats;
    }
    
    /**
     * Get all sessions statistics
     */
    public Map<String, Object> getAllSessionsStatistics() {
        Map<String, Object> allStats = new HashMap<>();
        allStats.put("totalSessions", sessions.size());
        
        Map<String, Map<String, Object>> sessionStats = new HashMap<>();
        for (String sessionId : sessions.keySet()) {
            sessionStats.put(sessionId, getSessionStatistics(sessionId));
        }
        allStats.put("sessions", sessionStats);
        
        return allStats;
    }
    
    /**
     * Clean up inactive sessions (optional, can be scheduled)
     */
    public int cleanupInactiveSessions(long maxInactiveMinutes) {
        // This is a placeholder for future implementation
        // Would require tracking last access time for each session
        log.info("Session cleanup not yet implemented");
        return 0;
    }
    
    /**
     * Export session to Map
     */
    public Map<String, Object> exportSession(String sessionId) {
        if (!sessionExists(sessionId)) {
            return Map.of("error", "Session not found");
        }
        
        ConversationMemoryService session = getSession(sessionId);
        Map<String, Object> export = new HashMap<>();
        export.put("sessionId", sessionId);
        export.put("conversations", session.getFullConversation());
        export.put("statistics", session.getStatistics());
        
        return export;
    }
}
