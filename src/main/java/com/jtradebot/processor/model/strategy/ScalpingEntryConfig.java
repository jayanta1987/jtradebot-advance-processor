package com.jtradebot.processor.model.strategy;

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
public class ScalpingEntryConfig {
    
    private String strategy;
    private String version;
    private String description;
    
    private TradingConfiguration tradingConfiguration;
    private FuturesignalsConfig futuresignalsConfig;
    private Map<String, List<String>> callCategories;
    private Map<String, List<String>> putCategories;
    private List<Scenario> scenarios;
    private LoggingConfig logging;
    private WeightsConfig weights;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TradingConfiguration {
        private Double accountBalance;
        private QuantitySettings quantitySettings;
        private InvestmentLimits investmentLimits;
        private RiskManagement riskManagement;
        private TradeSettings tradeSettings;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuantitySettings {
        private Integer minLotSize;
        private Integer maxQuantityPerTrade;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InvestmentLimits {
        private Double maxInvestmentPercentage;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RiskManagement {
        private Double maxRiskPerDayPercentage;
        private Double maxProfitPerDayPercentage;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TradeSettings {
        private Integer maxTradeHoldingTimeInSec;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FuturesignalsConfig {
        private List<String> enabledTimeframes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Scenario {
        private String name;
        private String description;
        private ScenarioRequirements requirements;
        private ScenarioRiskManagement riskManagement;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScenarioRequirements {
        private Integer ema_min_count;
        private Integer futureAndVolume_min_count;
        private Integer candlestick_min_count;
        private Integer momentum_min_count;
        private Double minQualityScore;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScenarioRiskManagement {
        private Double stopLossPoints;
        private Double targetPoints;
        private Boolean useTrailingStop;
        private Double stopLossPercentage;
        private Double targetPercentage;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LoggingConfig {
        private Boolean enabled;
        private Boolean logFilteredEntries;
        private Boolean logRejectionReasons;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WeightsConfig {
        private Double emaCrossoverWeight;
        private Double rsiConditionWeight;
        private Double volumeSurgeWeight;
        private Double priceActionWeight;
        private Double futuresignalsWeight;
    }
}
