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
    private NoTradeZonesConfig noTradeZones;
    private CategoryScoring categoryScoring;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TradingConfiguration {
        private Double accountBalance;
        private RiskManagement riskManagement;
        private TradeSettings tradeSettings;
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
        private String targetMode;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScenarioRequirements {
        private Double minQualityScore;
        private Boolean flatMarketFilter;
        
        // Minimum score percentage requirements for each category
        private Double min_ema_per;
        private Double min_future_signal_per;
        private Double min_candlestick_per;
        private Double min_momentum_per;
        private Integer maxNTP;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScenarioRiskManagement {
        private Double stopLossPoints;
        private Double targetPoints;
        private Double stopLossPercentage;
        private Double targetPercentage;
        private Double milestonePoints;
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
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NoTradeZonesConfig {
        private Boolean enabled;
        private String description;
        private Integer maxAllowedNTP;
        private Map<String, NoTradeFilter> filters;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CategoryScoring {
        private Map<String, CategoryIndicatorScoring> callCategories;
        private Map<String, CategoryIndicatorScoring> putCategories;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CategoryIndicatorScoring {
        private Map<String, Double> indicators;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NoTradeFilter {
        private Boolean enabled;
        private Double ntp;
        private String name;
        private String description;
        private Double threshold;
        private Double minThreshold;
        private Double maxThreshold;
        private Integer priority;
        private Double overboughtThreshold;
        private Double oversoldThreshold;
        private String timeframe;
        private Integer lookbackBars;
        private Integer maxConsecutiveCount;
        private Integer analysisWindow;
        // Trading hours fields
        private Integer startHour;
        private Integer startMinute;
        private Integer endHour;
        private Integer endMinute;
        // Support/Resistance fields
        private Integer supportResistanceBuffer;
        private Integer roundFigureBuffer;
    }
}
