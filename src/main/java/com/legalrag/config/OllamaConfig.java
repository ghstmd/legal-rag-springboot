package com.legalrag.config;

import java.util.Collections;
import java.util.List;

import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class OllamaConfig {

    @Value("${spring.ai.ollama.base-url}")
    private String baseUrl;

    @Value("${spring.ai.ollama.chat.options.model}")
    private String model;

    @Value("${spring.ai.ollama.chat.options.temperature:0.1}")
    private Double temperature;

    @Value("${spring.ai.ollama.chat.options.num-predict:4096}")
    private Integer numPredict;

    @Value("${spring.ai.ollama.chat.options.top-k:40}")
    private Integer topK;

    @Value("${spring.ai.ollama.chat.options.top-p:0.9}")
    private Double topP;

    @Value("${spring.ai.ollama.chat.options.repeat-penalty:1.1}")
    private Double repeatPenalty;

    @Bean
    public OllamaApi ollamaApi() {
        log.info("Initializing Ollama API with base URL: {}", baseUrl);
        return new OllamaApi(baseUrl);
    }

    @Bean
    public OllamaOptions defaultOllamaOptions() {
        return OllamaOptions.builder()
                .withModel(model)
                .withTemperature(temperature)
                .withNumPredict(numPredict) // Max output tokens
                .withTopK(topK)
                .withTopP(topP)
                .withRepeatPenalty(repeatPenalty)
                .build();
    }

    @Bean
    public OllamaChatModel ollamaChatModel(
            OllamaApi ollamaApi,
            OllamaOptions ollamaOptions,
            FunctionCallbackContext functionCallbackContext,
            List<FunctionCallback> functionCallbacks,
            ObservationRegistry observationRegistry,
            ModelManagementOptions modelManagementOptions) {

        // normalize optional params
        List<FunctionCallback> callbacks = functionCallbacks != null ? functionCallbacks : Collections.emptyList();
        ObservationRegistry obs = observationRegistry != null ? observationRegistry : ObservationRegistry.NOOP;
        ModelManagementOptions mmo = modelManagementOptions != null ? modelManagementOptions
                : ModelManagementOptions.builder().build();

        // use the multi-arg constructor expected by the Spring AI version in your
        // classpath
        return new OllamaChatModel(
                ollamaApi,
                ollamaOptions,
                functionCallbackContext,
                callbacks,
                obs,
                mmo);
    }

    @Bean
    public ModelManagementOptions modelManagementOptions() {
        return ModelManagementOptions.builder().build();
    }
}