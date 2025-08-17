package com.jtradebot.processor.model.strategy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScalpingEntryDecision {
    
    private boolean shouldEntry;
    private String scenarioName;
    private double confidence;
    private ScalpingEntryConfig.ScenarioRiskManagement riskManagement;
    private String reason;
    
    // Additional fields for detailed analysis
    private Map<String, Integer> categoryScores;
    private Map<String, java.util.List<String>> matchedConditions;
    private double qualityScore;
}
