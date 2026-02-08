package com.legalrag.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class VietnameseTokenizer {
    
    private static final Set<String> STOP_WORDS = Set.of(
        "là", "của", "và", "có", "được", "trong", "cho", "với",
        "theo", "như", "về", "khi", "để", "bởi", "từ", "tại",
        "các", "những", "này", "đó", "thì", "gì", "nào", "sao",
        "đã", "sẽ", "đang", "do", "nên", "mà", "hay", "hoặc"
    );

    
    /**
     * Tokenize text into unigrams (single words)
     */
    public List<String> tokenizeUnigram(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        
        return Arrays.stream(text.toLowerCase().split("\\s+"))
            .filter(word -> !word.isEmpty())
            .filter(word -> word.length() > 1)
            .filter(word -> !STOP_WORDS.contains(word))
            .collect(Collectors.toList());
    }
    
    /**
     * Tokenize text into bigrams (pairs of consecutive words)
     */
    public List<String> tokenizeBigram(String text) {
        List<String> tokens = tokenizeUnigram(text);
        List<String> bigrams = new ArrayList<>();
        
        for (int i = 0; i < tokens.size() - 1; i++) {
            bigrams.add(tokens.get(i) + "_" + tokens.get(i + 1));
        }
        
        return bigrams;
    }
    
    /**
     * Tokenize text into both unigrams and bigrams
     */
    public TokenResult tokenize(String text) {
        List<String> unigrams = tokenizeUnigram(text);
        List<String> bigrams = tokenizeBigram(text);
        
        return new TokenResult(unigrams, bigrams);
    }
    
    /**
     * Extract keywords from text (remove stop words)
     */
    public List<String> extractKeywords(String text, int maxKeywords) {
        return tokenizeUnigram(text).stream()
            .filter(word -> word.length() >= 3)
            .distinct()
            .limit(maxKeywords)
            .collect(Collectors.toList());
    }
    
    public record TokenResult(List<String> unigrams, List<String> bigrams) {}
}

