package com.legalrag.service.rag;

import com.legalrag.config.DatasetConfig;
import com.legalrag.dto.internal.SearchResult;
import com.legalrag.service.data.DataLoaderService;
import com.legalrag.util.BM25Serializer;
import com.legalrag.util.BM25Serializer.SerializableBM25Index;
import com.legalrag.util.VietnameseTokenizer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * BM25 Search with weighted unigram/bigram scoring (80:20)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BM25SearchService {

    private final VietnameseTokenizer tokenizer;
    private final DataLoaderService dataLoaderService;
    private final DatasetConfig datasetConfig;

    private static final double K1 = 1.5;
    private static final double B = 0.75;
    private static final double UNIGRAM_WEIGHT = 0.8;
    private static final double BIGRAM_WEIGHT = 0.2;

    private BM25Index bm25Index;

    @PostConstruct
    public void init() {
        log.info("Initializing BM25 index");

        String corpusPath = datasetConfig.getCorpus();
        String cacheFilePath = resolveCachePath();

        log.info("Cache file: {}", cacheFilePath);

        if (loadFromCache(cacheFilePath, corpusPath)) {
            log.info("Loaded from cache: {} documents", bm25Index.corpus.size());
        } else {
            buildIndex();
            saveToCache(cacheFilePath);
            log.info("Built and cached: {} documents", bm25Index.corpus.size());
        }

        log.info("BM25 ready (unigram:bigram = {}:{})",
                (int) (UNIGRAM_WEIGHT * 10), (int) (BIGRAM_WEIGHT * 10));
    }

    public List<SearchResult> search(String query, int topK) {
        long startTime = System.nanoTime();

        if (bm25Index == null || bm25Index.corpus.isEmpty()) {
            log.warn("BM25 index not initialized");
            return List.of();
        }

        WeightedTokens weightedTokens = tokenizeWeighted(query);
        if (weightedTokens.isEmpty()) {
            return List.of();
        }

        List<SearchResult> results = new ArrayList<>();
        for (DocumentData doc : bm25Index.corpus) {
            double score = calculateWeightedBM25Score(weightedTokens, doc);
            if (score > 0) {
                results.add(toSearchResult(doc, score));
            }
        }

        long duration = (System.nanoTime() - startTime) / 1_000_000;
        log.debug("BM25 search: {} results in {}ms", results.size(), duration);

        return results.stream()
                .sorted(Comparator.comparingDouble(SearchResult::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    public double[] getScores(String query) {
        if (bm25Index == null || bm25Index.corpus.isEmpty()) {
            return new double[0];
        }

        double[] scores = new double[bm25Index.corpus.size()];
        WeightedTokens weightedTokens = tokenizeWeighted(query);

        for (DocumentData doc : bm25Index.corpus) {
            scores[doc.index] = calculateWeightedBM25Score(weightedTokens, doc);
        }
        return scores;
    }

    private String resolveCachePath() {
        String cacheFilePath = datasetConfig.getBm25Cache();

        if (cacheFilePath == null || cacheFilePath.isBlank()) {
            String legacyPath = datasetConfig.getBm25Uni();
            if (legacyPath != null && !legacyPath.isBlank()) {
                log.warn("Using legacy bm25-uni config - please update to bm25-cache");
                return legacyPath;
            }
            return generateDefaultCachePath(datasetConfig.getCorpus());
        }

        return cacheFilePath;
    }

    private String generateDefaultCachePath(String corpusPath) {
        return Paths.get(corpusPath).getParent()
                .resolve("bm25_cache.gz").toString();
    }

    private boolean loadFromCache(String cacheFilePath, String corpusPath) {
        try {
            if (!BM25Serializer.isCacheValid(cacheFilePath, corpusPath)) {
                return false;
            }

            SerializableBM25Index serializedIndex = BM25Serializer.loadIndex(cacheFilePath);
            this.bm25Index = deserializeIndex(serializedIndex);
            return true;

        } catch (Exception e) {
            log.warn("Cache load failed: {} - rebuilding", e.getMessage());
            return false;
        }
    }

    private void saveToCache(String cacheFilePath) {
        try {
            SerializableBM25Index serializedIndex = serializeIndex(bm25Index);
            BM25Serializer.saveIndex(serializedIndex, cacheFilePath);
        } catch (Exception e) {
            log.warn("Cache save failed: {} - continuing", e.getMessage());
        }
    }

    private SerializableBM25Index serializeIndex(BM25Index index) {
        SerializableBM25Index serialized = new SerializableBM25Index();

        List<SerializableBM25Index.SerializableDocumentData> serializedCorpus = new ArrayList<>();
        for (DocumentData doc : index.corpus) {
            SerializableBM25Index.SerializableDocumentData serializedDoc =
                    new SerializableBM25Index.SerializableDocumentData();

            serializedDoc.setIndex(doc.index);
            serializedDoc.setChunkId(doc.chunkId);
            serializedDoc.setText(doc.text);
            serializedDoc.setUnigrams(doc.tokens.unigrams);
            serializedDoc.setBigrams(doc.tokens.bigrams);
            serializedDoc.setUnigramFreq(doc.unigramFreq);
            serializedDoc.setBigramFreq(doc.bigramFreq);

            serializedCorpus.add(serializedDoc);
        }
        serialized.setCorpus(serializedCorpus);

        Map<String, Integer> unigramDocFreq = new HashMap<>();
        for (Map.Entry<String, DocumentFrequency> entry : index.unigramDocFreq.entrySet()) {
            unigramDocFreq.put(entry.getKey(), entry.getValue().count());
        }
        serialized.setUnigramDocFreq(unigramDocFreq);

        Map<String, Integer> bigramDocFreq = new HashMap<>();
        for (Map.Entry<String, DocumentFrequency> entry : index.bigramDocFreq.entrySet()) {
            bigramDocFreq.put(entry.getKey(), entry.getValue().count());
        }
        serialized.setBigramDocFreq(bigramDocFreq);

        serialized.setAvgDocLength(index.avgDocLength);

        return serialized;
    }

    private BM25Index deserializeIndex(SerializableBM25Index serialized) {
        BM25Index index = new BM25Index();

        for (SerializableBM25Index.SerializableDocumentData serializedDoc : serialized.getCorpus()) {
            WeightedTokens tokens = new WeightedTokens(
                    serializedDoc.getUnigrams(),
                    serializedDoc.getBigrams()
            );

            DocumentData doc = new DocumentData(
                    serializedDoc.getIndex(),
                    serializedDoc.getChunkId(),
                    serializedDoc.getText(),
                    tokens,
                    serializedDoc.getUnigramFreq(),
                    serializedDoc.getBigramFreq()
            );

            index.corpus.add(doc);
        }

        for (Map.Entry<String, Integer> entry : serialized.getUnigramDocFreq().entrySet()) {
            DocumentFrequency df = new DocumentFrequency();
            for (int i = 0; i < entry.getValue(); i++) {
                df.increment();
            }
            index.unigramDocFreq.put(entry.getKey(), df);
        }

        for (Map.Entry<String, Integer> entry : serialized.getBigramDocFreq().entrySet()) {
            DocumentFrequency df = new DocumentFrequency();
            for (int i = 0; i < entry.getValue(); i++) {
                df.increment();
            }
            index.bigramDocFreq.put(entry.getKey(), df);
        }

        index.avgDocLength = serialized.getAvgDocLength();

        return index;
    }

    private void buildIndex() {
        log.info("Building BM25 index from scratch");
        long startTime = System.currentTimeMillis();

        List<Map<String, Object>> documents = dataLoaderService.getCorpusData();

        if (documents == null || documents.isEmpty()) {
            log.error("Corpus data is empty");
            bm25Index = new BM25Index();
            return;
        }

        BM25Index index = new BM25Index();
        int totalTokens = 0;
        int skippedDocs = 0;

        for (int i = 0; i < documents.size(); i++) {
            Map<String, Object> doc = documents.get(i);
            String text = (String) doc.get("text");

            if (text == null || text.isBlank()) {
                skippedDocs++;
                continue;
            }

            String chunkId = String.valueOf(doc.get("chunk_id"));

            WeightedTokens tokens = tokenizeWeighted(text);
            if (tokens.isEmpty()) {
                skippedDocs++;
                continue;
            }

            Map<String, Integer> unigramFreq = calculateTermFreq(tokens.unigrams);
            Map<String, Integer> bigramFreq = calculateTermFreq(tokens.bigrams);

            DocumentData data = new DocumentData(
                    i, chunkId, text, tokens, unigramFreq, bigramFreq
            );
            index.corpus.add(data);

            totalTokens += tokens.totalSize();

            for (String term : unigramFreq.keySet()) {
                index.unigramDocFreq
                        .computeIfAbsent(term, k -> new DocumentFrequency())
                        .increment();
            }

            for (String term : bigramFreq.keySet()) {
                index.bigramDocFreq
                        .computeIfAbsent(term, k -> new DocumentFrequency())
                        .increment();
            }
        }

        index.avgDocLength = index.corpus.isEmpty()
                ? 0
                : (double) totalTokens / index.corpus.size();

        long duration = System.currentTimeMillis() - startTime;

        log.info("Index built: {} docs, {} skipped, avg={}, {}ms",
                index.corpus.size(), skippedDocs,
                String.format("%.2f", index.avgDocLength), duration);

        this.bm25Index = index;
    }

    private double calculateWeightedBM25Score(WeightedTokens queryTokens, DocumentData doc) {
        double unigramScore = calculateBM25ScoreForTerms(
                queryTokens.unigrams,
                doc.unigramFreq,
                bm25Index.unigramDocFreq,
                doc.tokens.totalSize()
        );

        double bigramScore = calculateBM25ScoreForTerms(
                queryTokens.bigrams,
                doc.bigramFreq,
                bm25Index.bigramDocFreq,
                doc.tokens.totalSize()
        );

        return (UNIGRAM_WEIGHT * unigramScore) + (BIGRAM_WEIGHT * bigramScore);
    }

    private double calculateBM25ScoreForTerms(
            List<String> queryTerms,
            Map<String, Integer> docTermFreq,
            Map<String, DocumentFrequency> termDocFreqMap,
            int docLength
    ) {
        double score = 0.0;
        int numDocs = bm25Index.corpus.size();

        for (String term : queryTerms) {
            int tf = docTermFreq.getOrDefault(term, 0);
            if (tf == 0) continue;

            int df = termDocFreqMap
                    .getOrDefault(term, new DocumentFrequency())
                    .count();

            double idf = Math.log((numDocs - df + 0.5) / (df + 0.5) + 1.0);
            double numerator = tf * (K1 + 1);
            double denominator = tf + K1 * (1 - B + B * docLength / bm25Index.avgDocLength);

            score += idf * (numerator / denominator);
        }
        return score;
    }

    private WeightedTokens tokenizeWeighted(String text) {
        VietnameseTokenizer.TokenResult tokens = tokenizer.tokenize(text);
        return new WeightedTokens(tokens.unigrams(), tokens.bigrams());
    }

    private Map<String, Integer> calculateTermFreq(List<String> tokens) {
        Map<String, Integer> termFreq = new HashMap<>();
        for (String token : tokens) {
            termFreq.merge(token, 1, Integer::sum);
        }
        return termFreq;
    }

    private SearchResult toSearchResult(DocumentData doc, double score) {
        return SearchResult.builder()
                .index(doc.index)
                .chunkId(doc.chunkId)
                .text(doc.text)
                .score(score)
                .source("bm25")
                .build();
    }

    private static class WeightedTokens {
        List<String> unigrams;
        List<String> bigrams;

        WeightedTokens(List<String> unigrams, List<String> bigrams) {
            this.unigrams = unigrams != null ? unigrams : List.of();
            this.bigrams = bigrams != null ? bigrams : List.of();
        }

        boolean isEmpty() {
            return unigrams.isEmpty() && bigrams.isEmpty();
        }

        int totalSize() {
            return unigrams.size() + bigrams.size();
        }
    }

    private static class BM25Index {
        List<DocumentData> corpus = new ArrayList<>();
        Map<String, DocumentFrequency> unigramDocFreq = new HashMap<>();
        Map<String, DocumentFrequency> bigramDocFreq = new HashMap<>();
        double avgDocLength;
    }

    private static class DocumentData {
        int index;
        String chunkId;
        String text;
        WeightedTokens tokens;
        Map<String, Integer> unigramFreq;
        Map<String, Integer> bigramFreq;

        DocumentData(int index, String chunkId, String text,
                     WeightedTokens tokens,
                     Map<String, Integer> unigramFreq,
                     Map<String, Integer> bigramFreq) {
            this.index = index;
            this.chunkId = chunkId;
            this.text = text;
            this.tokens = tokens;
            this.unigramFreq = unigramFreq;
            this.bigramFreq = bigramFreq;
        }
    }

    private static class DocumentFrequency {
        int count = 0;

        void increment() {
            count++;
        }

        int count() {
            return count;
        }
    }
}