package com.legalrag.service.rag;

import com.legalrag.config.ModelConfig;
import com.legalrag.dto.internal.RerankResult;
import com.legalrag.dto.internal.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RerankService {
    
    private final ModelConfig modelConfig;
    
    public List<RerankResult> rerank(String query, List<SearchResult> searchResults, int topK) {
        log.debug("Reranking {} results to top {}", searchResults.size(), topK);
        
        if (searchResults.isEmpty()) {
            return List.of();
        }
        
        // For small result sets, just convert without reranking
        if (searchResults.size() <= topK) {
            return searchResults.stream()
                .map(sr -> RerankResult.builder()
                    .score(sr.getScore())
                    .index(sr.getIndex())
                    .chunkId(sr.getChunkId())
                    .text(sr.getText())
                    .build())
                .collect(Collectors.toList());
        }
        
        // Use hybrid reranking: combine original scores with text matching
        List<RerankResult> rerankedResults = new ArrayList<>();
        
        for (SearchResult result : searchResults) {
            double rerankScore = calculateRerankScore(query, result);
            
            rerankedResults.add(RerankResult.builder()
                .score(rerankScore)
                .index(result.getIndex())
                .chunkId(result.getChunkId())
                .text(result.getText())
                .build());
        }
        
        // Sort by rerank score descending
        rerankedResults.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        
        // Return top K
        List<RerankResult> topResults = rerankedResults.stream()
            .limit(topK)
            .collect(Collectors.toList());
        
        log.debug("Reranking complete: returned {} results", topResults.size());
        return topResults;
    }
    
    /**
     * Calculate rerank score using hybrid approach:
     * - Original search score (70%)
     * - Exact term matching bonus (20%)
     * - Length penalty (10%)
     */
    private double calculateRerankScore(String query, SearchResult result) {
        double originalScore = result.getScore();
        
        // Normalize query and text
        String normalizedQuery = query.toLowerCase();
        String normalizedText = result.getText().toLowerCase();
        
        // Exact match bonus
        double exactMatchScore = 0.0;
        String[] queryTerms = normalizedQuery.split("\\s+");
        int matchCount = 0;
        
        for (String term : queryTerms) {
            if (term.length() > 2 && normalizedText.contains(term)) {
                matchCount++;
            }
        }
        
        if (queryTerms.length > 0) {
            exactMatchScore = (double) matchCount / queryTerms.length;
        }
        
        // Length penalty (prefer concise relevant answers)
        double lengthPenalty = 1.0;
        int textLength = result.getText().length();
        if (textLength > 1000) {
            lengthPenalty = 0.9;
        } else if (textLength > 2000) {
            lengthPenalty = 0.8;
        }
        
        // Combine scores
        double rerankScore = (originalScore * 0.7) + (exactMatchScore * 0.2) + (lengthPenalty * 0.1);
        
        return rerankScore;
    }
    
    /**
     * Advanced reranking using LLM (for top candidates only)
     * This is expensive, use sparingly
     */
    public List<RerankResult> rerankWithLLM(String query, List<SearchResult> searchResults, 
                                            int topK, ChatModel chatModel) {
        log.info("Performing LLM-based reranking (expensive operation)");
        
        if (searchResults.isEmpty()) {
            return List.of();
        }
        
        // Only rerank top candidates to save cost
        int candidateCount = Math.min(searchResults.size(), topK * 2);
        List<SearchResult> candidates = searchResults.stream()
            .limit(candidateCount)
            .collect(Collectors.toList());
        
        List<RerankResult> rerankedResults = new ArrayList<>();
        
        for (SearchResult result : candidates) {
            double llmScore = scorePairWithLLM(query, result.getText(), chatModel);
            
            rerankedResults.add(RerankResult.builder()
                .score(llmScore)
                .index(result.getIndex())
                .chunkId(result.getChunkId())
                .text(result.getText())
                .build());
        }
        
        // Sort and return top K
        rerankedResults.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        
        return rerankedResults.stream()
            .limit(topK)
            .collect(Collectors.toList());
    }
    
    /**
     * Score a query-document pair using LLM
     */
    private double scorePairWithLLM(String query, String document, ChatModel chatModel) {
        String prompt = String.format("""
            Đánh giá mức độ liên quan giữa câu hỏi và văn bản sau.
            Chỉ trả lời MỘT SỐ từ 0.0 đến 1.0 (1.0 = rất liên quan, 0.0 = không liên quan).
            
            Câu hỏi: %s
            
            Văn bản: %s
            
            Điểm số (chỉ số, không giải thích):
            """, query, document.substring(0, Math.min(document.length(), 500)));

        
        try {
            ChatClient client = ChatClient.create(chatModel);
            String response = client.prompt()
                .user(prompt)
                .call()
                .content();
            
            // Extract number from response
            String cleaned = response.trim().replaceAll("[^0-9.]", "");
            return Double.parseDouble(cleaned);
            
        } catch (Exception e) {
            log.warn("Failed to score pair with LLM: {}", e.getMessage());
            return 0.5; // Default score
        }
    }
    
    /**
     * Batch reranking for efficiency
     */
    public List<RerankResult> rerankBatch(String query, List<SearchResult> searchResults, 
                                         int batchSize, int topK) {
        log.debug("Batch reranking with batch size: {}", batchSize);
        
        List<RerankResult> allReranked = new ArrayList<>();
        
        for (int i = 0; i < searchResults.size(); i += batchSize) {
            int end = Math.min(i + batchSize, searchResults.size());
            List<SearchResult> batch = searchResults.subList(i, end);
            
            List<RerankResult> batchResults = rerank(query, batch, batch.size());
            allReranked.addAll(batchResults);
        }
        
        // Sort all results and return top K
        allReranked.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        
        return allReranked.stream()
            .limit(topK)
            .collect(Collectors.toList());
    }
}
