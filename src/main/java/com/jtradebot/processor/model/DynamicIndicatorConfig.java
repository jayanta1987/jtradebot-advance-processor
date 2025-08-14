package com.jtradebot.processor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DynamicIndicatorConfig {
    
    private String strategy;
    private String version;
    private String description;
    
    private Map<String, IndicatorDefinition> indicators;
    private List<String> timeframes;
    private Map<String, Object> thresholds;
    
    private CallConditions callConditions;
    private PutConditions putConditions;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IndicatorDefinition {
        private String name;
        private String type; // EMA, RSI, VOLUME, VWAP, SUPPORT_RESISTANCE, CROSSOVER, CROSSDOWN
        private List<String> timeframes;
        private Map<String, Object> parameters;
        private Map<String, Object> conditions;
        private String comparison; // GT, LT, EQ, CROSSOVER, CROSSDOWN
        private Double threshold;
        private String description;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CallConditions {
        private List<String> requiredIndicators;
        private int minRequiredCount;
        private String description;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PutConditions {
        private List<String> requiredIndicators;
        private int minRequiredCount;
        private String description;
    }
}
