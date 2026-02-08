package com.legalrag.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.legalrag.service.data.DataLoaderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupInitializer implements ApplicationRunner {

    private final DataLoaderService dataLoaderService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("\n{}", "=".repeat(70));
        log.info("INITIALIZING LEGAL RAG SYSTEM");
        log.info("{}\n", "=".repeat(70));

        try {
            loadData();

            log.info("\n{}", "=".repeat(70));
            log.info("SYSTEM READY");
            log.info("{}\n", "=".repeat(70));

        } catch (Exception e) {
            log.error("\n{}", "=".repeat(70));
            log.error("INITIALIZATION FAILED");
            log.error("{}\n", "=".repeat(70));
            log.error("Error: {}", e.getMessage(), e);
            log.warn("Application started but some features may not work");
        }
    }

    private void loadData() {
        log.info("Loading dataset (single-dataset mode)");

        dataLoaderService.loadDataset();

        log.info("Dataset loaded successfully\n");
    }
}
