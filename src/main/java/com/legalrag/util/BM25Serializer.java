package com.legalrag.util;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * BM25 Index Serialization Utility
 * Save/Load BM25 index to/from disk with GZIP compression
 */
@Slf4j
public class BM25Serializer {

    /**
     * Serializable BM25 Index structure
     */
    @Data
    public static class SerializableBM25Index implements Serializable {
        private static final long serialVersionUID = 1L;

        private List<SerializableDocumentData> corpus;
        private Map<String, Integer> unigramDocFreq;
        private Map<String, Integer> bigramDocFreq;
        private double avgDocLength;

        @Data
        public static class SerializableDocumentData implements Serializable {
            private static final long serialVersionUID = 1L;
            
            private int index;
            private String chunkId;
            private String text;
            private List<String> unigrams;
            private List<String> bigrams;
            private Map<String, Integer> unigramFreq;
            private Map<String, Integer> bigramFreq;
        }
    }

    /**
     * Save BM25 index to file with GZIP compression
     */
    public static void saveIndex(SerializableBM25Index index, String filePath) throws IOException {
        Path path = Paths.get(filePath);
        Files.createDirectories(path.getParent());

        long startTime = System.currentTimeMillis();
        
        try (FileOutputStream fos = new FileOutputStream(filePath);
             GZIPOutputStream gzos = new GZIPOutputStream(fos);
             ObjectOutputStream oos = new ObjectOutputStream(gzos)) {
            
            oos.writeObject(index);
        }

        long duration = System.currentTimeMillis() - startTime;
        long fileSize = Files.size(path);
        
        log.info("BM25 index saved | path={} | size={}KB | time={}ms",
                filePath, fileSize / 1024, duration);
    }

    /**
     * Load BM25 index from file
     */
    public static SerializableBM25Index loadIndex(String filePath) throws IOException, ClassNotFoundException {
        long startTime = System.currentTimeMillis();

        SerializableBM25Index index;
        try (FileInputStream fis = new FileInputStream(filePath);
             GZIPInputStream gzis = new GZIPInputStream(fis);
             ObjectInputStream ois = new ObjectInputStream(gzis)) {
            
            index = (SerializableBM25Index) ois.readObject();
        }

        long duration = System.currentTimeMillis() - startTime;
        
        log.info("BM25 index loaded | docs={} | time={}ms",
                index.getCorpus().size(), duration);

        return index;
    }

    /**
     * Check if cached index file exists and is valid
     */
    public static boolean isCacheValid(String cacheFilePath, String corpusFilePath) {
        try {
            Path cachePath = Paths.get(cacheFilePath);
            Path corpusPath = Paths.get(corpusFilePath);

            if (!Files.exists(cachePath)) {
                log.debug("Cache file not found: {}", cacheFilePath);
                return false;
            }

            if (!Files.exists(corpusPath)) {
                log.warn("Corpus file not found: {}", corpusFilePath);
                return false;
            }

            // Check if corpus is newer than cache
            long cacheTime = Files.getLastModifiedTime(cachePath).toMillis();
            long corpusTime = Files.getLastModifiedTime(corpusPath).toMillis();

            if (corpusTime > cacheTime) {
                log.info("Corpus file is newer than cache - cache invalidated");
                return false;
            }

            log.info("Valid cache found: {}", cacheFilePath);
            return true;

        } catch (IOException e) {
            log.warn("Error checking cache validity: {}", e.getMessage());
            return false;
        }
    }
}