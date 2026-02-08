package com.legalrag.service.llm;

import com.legalrag.exception.RagException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaLlmService {
    
    private final OllamaChatModel chatModel;
    private final OllamaOptions defaultOptions;
    
    /**
     * Generate answer with system and user messages
     */
    @CircuitBreaker(name = "ollama", fallbackMethod = "fallbackGenerate")
    @Retry(name = "ollama")
    public String generate(String systemPrompt, String userPrompt) {
        try {
            log.debug("Generating answer with LLM");
            
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));
            messages.add(new UserMessage(userPrompt));
            
            Prompt prompt = new Prompt(messages, defaultOptions);
            ChatResponse response = chatModel.call(prompt);
            String content = response.getResult().getOutput().getContent();
            
            log.debug("LLM response generated successfully");
            return content;
            
        } catch (Exception e) {
            log.error("Error generating LLM response: {}", e.getMessage());
            throw new RagException("Failed to generate LLM response", e);
        }
    }
    
    /**
     * Generate answer with only user prompt
     */
    @CircuitBreaker(name = "ollama", fallbackMethod = "fallbackGenerateSimple")
    @Retry(name = "ollama")
    public String generate(String prompt) {
        try {
            log.debug("Generating simple answer with LLM");
            
            // Simple string call returns String directly
            String response = chatModel.call(prompt);
            
            log.debug("LLM response generated successfully");
            return response;
            
        } catch (Exception e) {
            log.error("Error generating simple LLM response: {}", e.getMessage());
            throw new RagException("Failed to generate simple LLM response", e);
        }
    }
    
    /**
     * Generate answer with conversation history
     */
    @CircuitBreaker(name = "ollama", fallbackMethod = "fallbackGenerateWithHistory")
    @Retry(name = "ollama")
    public String generateWithHistory(List<Message> messages) {
        try {
            log.debug("Generating answer with conversation history ({} messages)", messages.size());
            
            Prompt prompt = new Prompt(messages, defaultOptions);
            ChatResponse response = chatModel.call(prompt);
            String content = response.getResult().getOutput().getContent();
            
            log.debug("LLM response with history generated successfully");
            return content;
            
        } catch (Exception e) {
            log.error("Error generating LLM response with history: {}", e.getMessage());
            throw new RagException("Failed to generate LLM response with history", e);
        }
    }
    
    /**
     * Fallback method for generate with system and user prompts
     */
    private String fallbackGenerate(String systemPrompt, String userPrompt, Exception e) {
        log.warn("Using fallback for LLM generation: {}", e.getMessage());
        return "Xin lỗi, hệ thống đang gặp sự cố khi tạo câu trả lời. Vui lòng thử lại sau.";
    }
    
    /**
     * Fallback method for simple generate
     */
    private String fallbackGenerateSimple(String prompt, Exception e) {
        log.warn("Using fallback for simple LLM generation: {}", e.getMessage());
        return "Xin lỗi, hệ thống đang gặp sự cố khi tạo câu trả lời. Vui lòng thử lại sau.";
    }
    
    /**
     * Fallback method for generate with history
     */
    private String fallbackGenerateWithHistory(List<Message> messages, Exception e) {
        log.warn("Using fallback for LLM generation with history: {}", e.getMessage());
        return "Xin lỗi, hệ thống đang gặp sự cố khi tạo câu trả lời. Vui lòng thử lại sau.";
    }
}