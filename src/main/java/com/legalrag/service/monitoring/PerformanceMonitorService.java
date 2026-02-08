package com.legalrag.service.monitoring;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
@Service
public class PerformanceMonitorService {
    
    @Value("${legal-rag.monitoring.max-query-history:100}")
    private int maxQueryHistory;
    
    @Getter
    private final Deque<QueryRecord> queryHistory = new ConcurrentLinkedDeque<>();
    
    public void addQuery(String query, Map<String, Object> timing, String mode) {
        QueryRecord record = QueryRecord.builder()
            .timestamp(Instant.now().toString())
            .query(query.length() > 100 ? query.substring(0, 100) + "..." : query)
            .mode(mode)
            .totalTime((Double) timing.get("totalTime"))
            .stepDurations((Map<String, Double>) timing.get("stepDurations"))
            .build();
        
        queryHistory.addLast(record);
        
        // Keep only recent N queries
        while (queryHistory.size() > maxQueryHistory) {
            queryHistory.removeFirst();
        }
    }
    
    public Map<String, Object> getStatistics() {
        if (queryHistory.isEmpty()) {
            return Map.of("message", "No queries recorded yet");
        }
        
        List<Double> totalTimes = queryHistory.stream()
            .map(QueryRecord::totalTime)
            .toList();
        
        // Aggregate step times
        Map<String, List<Double>> allSteps = new HashMap<>();
        for (QueryRecord record : queryHistory) {
            for (Map.Entry<String, Double> entry : record.stepDurations().entrySet()) {
                allSteps.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                    .add(entry.getValue());
            }
        }
        
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalQueries", queryHistory.size());
        stats.put("avgTotalTime", average(totalTimes));
        stats.put("medianTotalTime", median(totalTimes));
        stats.put("minTotalTime", Collections.min(totalTimes));
        stats.put("maxTotalTime", Collections.max(totalTimes));
        
        Map<String, Map<String, Double>> stepStats = new LinkedHashMap<>();
        for (Map.Entry<String, List<Double>> entry : allSteps.entrySet()) {
            List<Double> times = entry.getValue();
            Map<String, Double> stepStat = new LinkedHashMap<>();
            stepStat.put("avg", average(times));
            stepStat.put("median", median(times));
            stepStat.put("min", Collections.min(times));
            stepStat.put("max", Collections.max(times));
            stepStats.put(entry.getKey(), stepStat);
        }
        stats.put("stepStatistics", stepStats);
        
        return stats;
    }
    
    private double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
    
    private double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 == 0) {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        } else {
            return sorted.get(size / 2);
        }
    }
    
    public record QueryRecord(
        String timestamp,
        String query,
        String mode,
        Double totalTime,
        Map<String, Double> stepDurations
    ) {
        public static QueryRecordBuilder builder() {
            return new QueryRecordBuilder();
        }
    }
    
    public static class QueryRecordBuilder {
        private String timestamp;
        private String query;
        private String mode;
        private Double totalTime;
        private Map<String, Double> stepDurations;
        
        public QueryRecordBuilder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public QueryRecordBuilder query(String query) {
            this.query = query;
            return this;
        }
        
        public QueryRecordBuilder mode(String mode) {
            this.mode = mode;
            return this;
        }
        
        public QueryRecordBuilder totalTime(Double totalTime) {
            this.totalTime = totalTime;
            return this;
        }
        
        public QueryRecordBuilder stepDurations(Map<String, Double> stepDurations) {
            this.stepDurations = stepDurations;
            return this;
        }
        
        public QueryRecord build() {
            return new QueryRecord(timestamp, query, mode, totalTime, stepDurations);
        }
    }
}
