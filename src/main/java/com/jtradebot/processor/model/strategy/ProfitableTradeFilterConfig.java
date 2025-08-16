package com.jtradebot.processor.model.strategy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfitableTradeFilterConfig {
    
    private Boolean enabled;
    private String description;
    
    private CallStrategyFilter callStrategy;
    private PutStrategyFilter putStrategy;
    private LoggingConfig logging;
    private WeightsConfig weights;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallStrategyFilter {
        private Boolean enabled;
        private Double minQualityScore;
        private Double minCandlestickScore;
        private VolumeSurgeMultiplier volumeSurgeMultiplier;
        private RiskManagement riskManagement;
        private Categories categories;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Categories {
        private Category ema;
        private Category futureAndVolume;
        private Category candlestick;
        private Category momentum;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Category {
        private List<String> conditions;
        private Integer minCount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskManagement {
        private Double maxRiskPerTrade;
        private Double stopLossPercentage;
        private Double stopLossPoints;
        private Double targetPercentage;
        private Double targetPoints;
        private Integer maxHoldingTimeMinutes;
        private Boolean useTrailingStop;
        private Double trailingStopPercentage;
        private Boolean useBreakEven;
        private Double breakEvenTrigger;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeightsConfig {
        private Double emaCrossoverWeight;
        private Double rsiConditionWeight;
        private Double volumeSurgeWeight;
        private Double priceActionWeight;
        private Double futuresignalsWeight;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PutStrategyFilter {
        private Boolean enabled;
        private Double minQualityScore;
        private Double minCandlestickScore;
        private VolumeSurgeMultiplier volumeSurgeMultiplier;
        private RiskManagement riskManagement;
        private Categories categories;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolumeSurgeMultiplier {
        private Double min;
        private Double max;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoggingConfig {
        private Boolean enabled;
        private Boolean logFilteredEntries;
        private Boolean logRejectionReasons;
    }
}
