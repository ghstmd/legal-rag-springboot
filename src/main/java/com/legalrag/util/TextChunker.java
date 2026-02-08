package com.legalrag.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class TextChunker {
    
    /**
     * Split long text into chunks with overlap
     */
    public List<String> splitText(String text, int maxWords) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        
        String[] words = text.trim().split("\\s+");
        List<String> chunks = new ArrayList<>();
        
        for (int i = 0; i < words.length; i += maxWords) {
            int end = Math.min(i + maxWords, words.length);
            String chunk = String.join(" ", Arrays.copyOfRange(words, i, end));
            chunks.add(chunk);
        }
        
        return chunks;
    }
    
    /**
     * Split text with overlap for better context preservation
     */
    public List<String> splitTextWithOverlap(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        
        String[] words = text.trim().split("\\s+");
        List<String> chunks = new ArrayList<>();
        
        int step = chunkSize - overlap;
        if (step <= 0) {
            step = chunkSize / 2; // Fallback
        }
        
        for (int i = 0; i < words.length; i += step) {
            int end = Math.min(i + chunkSize, words.length);
            String chunk = String.join(" ", Arrays.copyOfRange(words, i, end));
            
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            
            if (end >= words.length) {
                break;
            }
        }
        
        return chunks;
    }
    
    /**
     * Truncate text to maximum length
     */
    public String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        
        return text.substring(0, maxLength) + "...";
    }
    
    /**
     * Truncate text to maximum words
     */
    public String truncateWords(String text, int maxWords) {
        if (text == null || text.isBlank()) {
            return text;
        }
        
        String[] words = text.split("\\s+");
        if (words.length <= maxWords) {
            return text;
        }
        
        return String.join(" ", Arrays.copyOfRange(words, 0, maxWords)) + "...";
    }
}