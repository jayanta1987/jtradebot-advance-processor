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
    
    private ScalpingEntryLogic callStrategy;
    private ScalpingEntryLogic putStrategy;
    private CommonSettings commonSettings;
    private Map<String, QualityGrade> qualityGrades;
    private Map<String, MarketSession> marketSessions;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScalpingEntryLogic {
        private String strategyType; // CALL or PUT
        private EntryConditions entryConditions;
        private RiskManagement riskManagement;
        private EntryQuality entryQuality;
        private MarketConditions marketConditions;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EntryConditions {
        private List<String> mandatoryConditions; // Must be satisfied
        private List<String> optionalConditions; // Nice to have
        private int minMandatoryCount; // Minimum mandatory conditions
        private int minOptionalCount; // Minimum optional conditions
        private double minConfidenceScore; // Minimum confidence score
        private boolean requireAllTimeframes; // All timeframes must align
        private boolean requireVolumeConfirmation; // Volume must confirm
        private boolean requirePriceActionConfirmation; // Price action must confirm
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RiskManagement {
        private double maxRiskPerTrade; // Maximum risk per trade (%)
        private double positionSize; // Position size (% of capital)
        private double stopLossPercentage; // Stop loss percentage
        private double stopLossPoints; // Stop loss in points
        private double targetPercentage; // Target percentage
        private double targetPoints; // Target in points
        private int maxHoldingTimeMinutes; // Maximum holding time
        private boolean useTrailingStop; // Use trailing stop
        private double trailingStopPercentage; // Trailing stop percentage
        private boolean useBreakEven; // Move to break even after target
        private double breakEvenTrigger; // Trigger for break even
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EntryQuality {
        private double minVolumeSurge; // Minimum volume surge multiplier
        private double minRsiStrength; // Minimum RSI strength
        private double minEmaAlignment; // Minimum EMA alignment
        private double minPriceMomentum; // Minimum price momentum
        private double minSignalStrength; // Minimum signal strength
        private boolean requireTrendAlignment; // Require trend alignment
        private boolean requireSupportResistance; // Require S/R confirmation
        private boolean requireVwapAlignment; // Require VWAP alignment
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MarketConditions {
        private String marketSession; // Market session (OPENING, MID, CLOSING)
        private double minVolatility; // Minimum volatility requirement
        private double maxVolatility; // Maximum volatility limit
        private boolean avoidHighSpread; // Avoid high spread conditions
        private double maxSpreadPercentage; // Maximum spread percentage
        private boolean avoidNewsTime; // Avoid news time
        private List<String> avoidTimeSlots; // Time slots to avoid
        private boolean requireLiquidity; // Require sufficient liquidity
        private double minLiquidityThreshold; // Minimum liquidity threshold
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommonSettings {
        private double accountBalance;
        private int lotSize;
        private double tickSize;
        private int maxOpenPositions;
        private int minTimeBetweenTrades;
        private double maxDailyLoss;
        private int maxDailyTrades;
        private boolean enablePositionSizing;
        private boolean enableRiskManagement;
        private boolean enableMarketConditionValidation;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QualityGrade {
        private double minScore;
        private String description;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MarketSession {
        private String startTime;
        private String endTime;
        private String description;
    }
    
}
