package com.legalrag.service.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class PythonEmbeddingServiceManager {

    @Value("${legal-rag.embedding.python-script-path:embedding_service.py}")
    private String pythonScriptPath;

    @Value("${legal-rag.embedding.python-command:python}")
    private String pythonCommand;

    @Value("${legal-rag.embedding.auto-start:true}")
    private boolean autoStart;

    private Process pythonProcess;

    @PostConstruct
    public void startPythonService() {
        if (!autoStart) {
            log.info("Python service auto-start disabled");
            return;
        }

        try {
            log.info("Starting Python embedding service...");
            
            File scriptFile = new File(pythonScriptPath);
            if (!scriptFile.exists()) {
                log.error("Script not found: {}", scriptFile.getAbsolutePath());
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(pythonCommand, pythonScriptPath);
            pb.redirectErrorStream(true);
            pythonProcess = pb.start();

            // Log Python output
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(pythonProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info("[Python] {}", line);
                    }
                } catch (Exception e) {
                    log.error("Error reading Python output", e);
                }
            }, "python-logger").start();

            log.info("Waiting 15s for model to load...");
            TimeUnit.SECONDS.sleep(15);

            if (pythonProcess.isAlive()) {
                log.info("Python service started successfully!");
            } else {
                log.error("Python service failed to start");
            }

        } catch (Exception e) {
            log.error("Failed to start Python service", e);
        }
    }

    @PreDestroy
    public void stopPythonService() {
        if (pythonProcess != null && pythonProcess.isAlive()) {
            log.info("Stopping Python service...");
            pythonProcess.destroy();
            try {
                pythonProcess.waitFor(5, TimeUnit.SECONDS);
                log.info("Python service stopped");
            } catch (InterruptedException e) {
                pythonProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
    }
}