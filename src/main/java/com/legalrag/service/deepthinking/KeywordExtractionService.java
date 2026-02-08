package com.legalrag.service.deepthinking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalrag.exception.RagException;
import com.legalrag.service.llm.OllamaLlmService;
import com.legalrag.util.PromptBuilder;
import com.legalrag.util.VietnameseTokenizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordExtractionService {
    
    private final OllamaLlmService llmService;
    private final PromptBuilder promptBuilder;
    private final VietnameseTokenizer tokenizer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Extract keywords from query using LLM
     */
    public KeywordResult extractKeywords(String query) {
        log.info("Extracting keywords from: '{}'", query.substring(0, Math.min(query.length(), 50)));
        
        try {
            // Build extraction prompt
            String prompt = promptBuilder.buildKeywordExtractionPrompt(query);
            
            // Call LLM
            String llmResponse = llmService.generate(prompt);
            
            log.debug("Raw LLM response: {}", llmResponse.substring(0, Math.min(llmResponse.length(), 200)));
            
            // Try to parse as JSON
            try {
                KeywordResult result = parseJsonResponse(llmResponse);
                log.info("Successfully extracted {} total keywords", 
                    result.getTotalKeywordCount());
                logKeywordResult(result);
                return result;
                
            } catch (Exception e) {
                log.warn("Direct JSON parse failed, trying to extract JSON block...");
                
                // Try to extract JSON from text
                KeywordResult result = extractJsonFromText(llmResponse);
                if (result != null) {
                    log.info("Successfully extracted JSON from text");
                    logKeywordResult(result);
                    return result;
                }
                
                // Fallback to naive extraction
                log.warn("All JSON parsing failed, using fallback keyword extraction");
                return fallbackKeywordExtraction(query);
            }
            
        } catch (Exception e) {
            log.error("Keyword extraction error: {}", e.getMessage());
            return fallbackKeywordExtraction(query);
        }
    }
    
    /**
     * Parse JSON response directly
     */
    private KeywordResult parseJsonResponse(String jsonText) throws Exception {
        Map<String, Object> parsed = objectMapper.readValue(jsonText, Map.class);
        return mapToKeywordResult(parsed);
    }
    
    /**
     * Extract JSON block from text using regex
     */
    private KeywordResult extractJsonFromText(String text) {
        // Pattern to match JSON object
        Pattern pattern = Pattern.compile("\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        
        while (matcher.find()) {
            String jsonCandidate = matcher.group(0);
            try {
                Map<String, Object> parsed = objectMapper.readValue(jsonCandidate, Map.class);
                return mapToKeywordResult(parsed);
            } catch (Exception e) {
                // Continue to next match
                log.debug("Failed to parse JSON candidate: {}", e.getMessage());
            }
        }
        
        return null;
    }
    
    /**
     * Map parsed JSON to KeywordResult
     */
    @SuppressWarnings("unchecked")
    private KeywordResult mapToKeywordResult(Map<String, Object> parsed) {
        List<String> mainKeywords = (List<String>) parsed.getOrDefault("main_keywords", List.of());
        List<String> relatedConcepts = (List<String>) parsed.getOrDefault("related_concepts", List.of());
        List<String> legalTerms = (List<String>) parsed.getOrDefault("legal_terms", List.of());
        String reasoning = (String) parsed.getOrDefault("reasoning", "");
        
        // Ensure all are valid lists
        mainKeywords = mainKeywords != null ? mainKeywords : List.of();
        relatedConcepts = relatedConcepts != null ? relatedConcepts : List.of();
        legalTerms = legalTerms != null ? legalTerms : List.of();
        
        return KeywordResult.builder()
            .mainKeywords(mainKeywords)
            .relatedConcepts(relatedConcepts)
            .legalTerms(legalTerms)
            .reasoning(reasoning)
            .build();
    }
    
    /**
     * Fallback: simple token-based keyword extraction
     */
    private KeywordResult fallbackKeywordExtraction(String query) {
        log.info("Using naive token-based fallback");
        
        List<String> keywords = tokenizer.extractKeywords(query, 5);
        
        return KeywordResult.builder()
            .mainKeywords(keywords.isEmpty() ? List.of(query) : keywords)
            .relatedConcepts(List.of())
            .legalTerms(List.of())
            .reasoning("Fallback extraction used due to LLM parsing error")
            .build();
    }
    
    /**
     * Log extracted keywords for debugging
     */
    private void logKeywordResult(KeywordResult result) {
        log.info("  - Main keywords: {}", result.getMainKeywords());
        log.info("  - Related concepts: {}", result.getRelatedConcepts());
        log.info("  - Legal terms: {}", result.getLegalTerms());
        log.debug("  - Reasoning: {}", result.getReasoning());
    }
    
    // DTO
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class KeywordResult {
        private List<String> mainKeywords;
        private List<String> relatedConcepts;
        private List<String> legalTerms;
        private String reasoning;
        
        /**
         * Get all keywords combined
         */
        public List<String> getAllKeywords() {
            List<String> all = new ArrayList<>();
            all.addAll(mainKeywords);
            all.addAll(relatedConcepts);
            all.addAll(legalTerms);
            return all;
        }
        
        /**
         * Get total keyword count
         */
        public int getTotalKeywordCount() {
            return mainKeywords.size() + relatedConcepts.size() + legalTerms.size();
        }
    }
}
