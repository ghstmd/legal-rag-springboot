package com.legalrag.config;

import com.legalrag.service.data.DataLoaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final DataLoaderService dataLoaderService;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeData() {
        log.info("");
        log.info("=".repeat(70));
        log.info("STARTING LEGAL RAG DATA INITIALIZATION (SINGLE DATASET)");
        log.info("=".repeat(70));

        try {
            log.info("Loading dataset");
            
            dataLoaderService.loadDataset();

            log.info("Dataset ready");
            log.info("DATA INITIALIZATION COMPLETED SUCCESSFULLY");
            log.info("=".repeat(70));

        } catch (Exception e) {
            log.error("Failed to initialize dataset", e);
            throw new RuntimeException("Data initialization failed", e);
        }
    }
}
