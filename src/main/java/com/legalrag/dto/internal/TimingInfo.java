package com.legalrag.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimingInfo {
    
    private Double totalTime;
    
    @Builder.Default
    private Map<String, Double> stepDurations = new LinkedHashMap<>();
    
    private String timestamp;
    
    public void addStep(String stepName, Double duration) {
        stepDurations.put(stepName, duration);
    }
}
