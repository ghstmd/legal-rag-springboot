package com.legalrag.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@Getter
public class EmbeddingConfig {

    @Value("${legal-rag.embedding.dimension}")
    private Integer dimension;

    @Value("${legal-rag.embedding.timeout-seconds:60}")
    private Integer timeoutSeconds;

    @Value("${legal-rag.embedding.python-base-url:http://localhost:8000}")
    private String pythonBaseUrl;

    @Value("${legal-rag.embedding.max-retries:3}")
    private Integer maxRetries;

    @Bean
    public WebClient embeddingWebClient() {
        log.info("==============================================");
        log.info("PYTHON EMBEDDING SERVICE CONFIGURATION");
        log.info("==============================================");
        log.info("  Base URL  : {}", pythonBaseUrl);
        log.info("  Dimension : {}", dimension);
        log.info("  Timeout   : {}s", timeoutSeconds);
        log.info("  Retries   : {}", maxRetries);
        log.info("==============================================");

        return WebClient.builder()
                .baseUrl(pythonBaseUrl)
                .clientConnector(
                        new org.springframework.http.client.reactive.ReactorClientHttpConnector(
                                reactor.netty.http.client.HttpClient.create()
                                        .responseTimeout(Duration.ofSeconds(timeoutSeconds))
                        )
                )
                .build();
    }
}
