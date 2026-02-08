package com.legalrag.service.memory;

import com.legalrag.service.embedding.VietnameseEmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ConversationMemoryService {
    
    private final VietnameseEmbeddingService embeddingService;
    
    private final int maxHistory;
    private final int maxContextTurns;
    
    private final Deque<ConversationTurn> conversations = new ArrayDeque<>();
    private final Deque<List<Double>> embeddings = new ArrayDeque<>();
    
    public ConversationMemoryService(
            VietnameseEmbeddingService embeddingService,
            @Value("${legal-rag.session.max-history:20}") int maxHistory,
            @Value("${legal-rag.session.max-context-turns:5}") int maxContextTurns) {
        this.embeddingService = embeddingService;
        this.maxHistory = maxHistory;
        this.maxContextTurns = maxContextTurns;
    }
    
    /**
     * Add a new conversation turn
     */
    public void addTurn(String question, String answer, Map<String, Object> metadata) {
        ConversationTurn turn = ConversationTurn.builder()
            .timestamp(Instant.now().toString())
            .question(question)
            .answer(answer)
            .metadata(metadata != null ? metadata : new HashMap<>())
            .build();
        
        // Generate embedding for the question (for semantic search)
        try {
            List<Double> questionEmbedding = embeddingService.generateEmbedding(question);
            
            conversations.addLast(turn);
            embeddings.addLast(questionEmbedding);
            
            // Keep only recent N turns
            while (conversations.size() > maxHistory) {
                conversations.removeFirst();
                embeddings.removeFirst();
            }
            
            log.debug("Added conversation turn: {}", question.substring(0, Math.min(question.length(), 50)));
            
        } catch (Exception e) {
            log.error("Failed to add conversation turn: {}", e.getMessage());
        }
    }
    
    /**
     * Get N most recent turns as context
     */
    public String getRecentContext(int n) {
        if (conversations.isEmpty()) {
            return "";
        }
        
        int turnsToGet = Math.min(n, conversations.size());
        List<ConversationTurn> recentTurns = new ArrayList<>();
        
        Iterator<ConversationTurn> iterator = conversations.descendingIterator();
        for (int i = 0; i < turnsToGet; i++) {
            recentTurns.add(0, iterator.next());
        }
        
        return formatContext(recentTurns, "Lịch sử hội thoại gần đây");
    }
    
    /**
     * Find relevant turns from history based on semantic similarity
     */
    public List<RelevantTurn> findRelevantHistory(String currentQuery, int topK) {
        if (conversations.isEmpty()) {
            return List.of();
        }
        
        try {
            // Generate embedding for current query
            List<Double> queryEmbedding = embeddingService.generateEmbedding(currentQuery);
            
            // Calculate similarities
            List<RelevantTurn> relevantTurns = new ArrayList<>();
            
            List<ConversationTurn> turnsList = new ArrayList<>(conversations);
            List<List<Double>> embeddingsList = new ArrayList<>(embeddings);
            
            for (int i = 0; i < turnsList.size(); i++) {
                double similarity = cosineSimilarity(queryEmbedding, embeddingsList.get(i));
                
                if (similarity > 0.5) { // Threshold
                    relevantTurns.add(RelevantTurn.builder()
                        .turn(turnsList.get(i))
                        .similarity(similarity)
                        .build());
                }
            }
            
            // Sort by similarity descending
            relevantTurns.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
            
            // Return top K
            return relevantTurns.stream()
                .limit(topK)
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Failed to find relevant history: {}", e.getMessage());
            return List.of();
        }
    }
    
    /**
     * Build enhanced context: recent + relevant
     */
    public String buildEnhancedContext(String currentQuery) {
        List<String> contextParts = new ArrayList<>();
        
        // 1. Recent context (always include)
        String recent = getRecentContext(6);
        if (!recent.isBlank()) {
            contextParts.add(recent);
        }
        
        // 2. Relevant context from history
        List<RelevantTurn> relevant = findRelevantHistory(currentQuery, 2);
        if (!relevant.isEmpty()) {
            StringBuilder relevantContext = new StringBuilder();
            relevantContext.append("\n=== Các cuộc hội thoại liên quan trước đó ===\n");
            
            for (RelevantTurn item : relevant) {
                ConversationTurn turn = item.getTurn();
                relevantContext.append(String.format("\n[Độ liên quan: %.2f]\n", item.getSimilarity()));
                relevantContext.append("Người dùng: ").append(turn.getQuestion()).append("\n");
                
                String truncatedAnswer = turn.getAnswer();
                if (truncatedAnswer.length() > 150) {
                    truncatedAnswer = truncatedAnswer.substring(0, 150) + "...";
                }
                relevantContext.append("Trợ lý: ").append(truncatedAnswer).append("\n");
            }
            
            contextParts.add(relevantContext.toString());
        }
        
        return String.join("\n", contextParts);
    }
    
    /**
     * Get full conversation history
     */
    public List<ConversationTurn> getFullConversation() {
        return new ArrayList<>(conversations);
    }
    
    /**
     * Clear all conversation history
     */
    public void clear() {
        conversations.clear();
        embeddings.clear();
        log.debug("Conversation history cleared");
    }
    
    /**
     * Get conversation statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTurns", conversations.size());
        stats.put("maxHistory", maxHistory);
        stats.put("maxContextTurns", maxContextTurns);
        
        if (!conversations.isEmpty()) {
            ConversationTurn first = conversations.peekFirst();
            ConversationTurn last = conversations.peekLast();
            stats.put("firstTurnTimestamp", first.getTimestamp());
            stats.put("lastTurnTimestamp", last.getTimestamp());
        }
        
        return stats;
    }
    
    /**
     * Format turns as context string
     */
    private String formatContext(List<ConversationTurn> turns, String header) {
        if (turns.isEmpty()) {
            return "";
        }
        
        StringBuilder context = new StringBuilder();
        context.append("=== ").append(header).append(" ===\n");
        
        for (int i = 0; i < turns.size(); i++) {
            ConversationTurn turn = turns.get(i);
            context.append(String.format("\n[Turn %d]\n", i + 1));
            context.append("Người dùng: ").append(turn.getQuestion()).append("\n");
            
            String truncatedAnswer = turn.getAnswer();
            if (truncatedAnswer.length() > 200) {
                truncatedAnswer = truncatedAnswer.substring(0, 200) + "...";
            }
            context.append("Trợ lý: ").append(truncatedAnswer).append("\n");
        }
        
        return context.toString();
    }
    
    /**
     * Calculate cosine similarity between two vectors
     */
    private double cosineSimilarity(List<Double> vec1, List<Double> vec2) {
        if (vec1.size() != vec2.size()) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vec1.size(); i++) {
            dotProduct += vec1.get(i) * vec2.get(i);
            norm1 += vec1.get(i) * vec1.get(i);
            norm2 += vec2.get(i) * vec2.get(i);
        }
        
        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    // DTOs
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ConversationTurn {
        private String timestamp;
        private String question;
        private String answer;
        private Map<String, Object> metadata;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RelevantTurn {
        private ConversationTurn turn;
        private Double similarity;
    }
}