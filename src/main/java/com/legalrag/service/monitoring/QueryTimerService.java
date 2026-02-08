package com.legalrag.service.monitoring;

import lombok.Getter;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class QueryTimerService {

    @Getter
    private Long startTime;

    @Getter
    private Long endTime;

    private final Map<String, Long> timings = new LinkedHashMap<>();

    public void start() {
        this.startTime = System.currentTimeMillis();
        this.timings.clear();
        this.endTime = null;
    }

    public void mark(String stepName) {
        if (startTime == null) {
            start();
        }

        long currentTime = System.currentTimeMillis();
        timings.put(stepName, currentTime - startTime);
    }

    public void end() {
        this.endTime = System.currentTimeMillis();
    }

    public double getTotalTime() {
        if (startTime == null || endTime == null) {
            return 0.0;
        }
        return (endTime - startTime) / 1000.0;
    }

    public Map<String, Double> getStepDurations() {
        Map<String, Double> durations = new LinkedHashMap<>();

        long prevTime = 0L;
        for (Map.Entry<String, Long> entry : timings.entrySet()) {
            long cumulativeTime = entry.getValue();
            double duration = (cumulativeTime - prevTime) / 1000.0;
            durations.put(entry.getKey(), duration);
            prevTime = cumulativeTime;
        }

        return durations;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalTime", getTotalTime());
        result.put("stepDurations", getStepDurations());
        result.put("timestamp", Instant.now().toString());
        return result;
    }

    public String formatDisplay() {
        StringBuilder sb = new StringBuilder();
        sb.append("\nThoi gian xu ly: ")
          .append(String.format("%.2fs", getTotalTime()));
        sb.append("\n\nChi tiet:\n");

        for (Map.Entry<String, Double> entry : getStepDurations().entrySet()) {
            sb.append(String.format(
                    "  - %s: %.2fs\n",
                    entry.getKey(),
                    entry.getValue()
            ));
        }

        return sb.toString();
    }

    public void reset() {
        this.startTime = null;
        this.endTime = null;
        this.timings.clear();
    }
}
