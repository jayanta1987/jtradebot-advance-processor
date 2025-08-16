package com.jtradebot.processor.model.indicator;

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
        private Map<String, Category> categories;
        private int minCategoriesRequired;
        private String priorityTimeframe;
        private String description;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PutConditions {
        private Map<String, Category> categories;
        private int minCategoriesRequired;
        private String priorityTimeframe;
        private String description;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Category {
        private String name;
        private String description;
        private List<String> indicators;
        private int minRequired;
    }
}
