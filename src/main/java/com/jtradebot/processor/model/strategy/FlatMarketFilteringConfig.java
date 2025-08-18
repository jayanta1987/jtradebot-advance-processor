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
public class FlatMarketFilteringConfig {
    
    private boolean enabled;
    private String description;
    private Requirements requirements;
    private Thresholds thresholds;
    private AtrSettings atrSettings;
    private LookbackSettings lookbackSettings;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Requirements {
        private double minCandleBodyRatio;
        private double minPriceRange;
        private double minVolumeMultiplier;
        private double minDirectionalStrength;
        private int maxConsecutiveDoji;
        private int maxConsecutiveSpinningTop;
        private int maxConsecutiveSmallCandles;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Thresholds {
        private VolatilityScore volatilityScore;
        private DirectionalStrength directionalStrength;
        private ComprehensiveChecks comprehensiveChecks;
        private FlatMarketAdjustments flatMarketAdjustments;
        private VolumeConsistency volumeConsistency;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolatilityScore {
        private double lowThreshold;
        private double veryLowThreshold;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DirectionalStrength {
        private double veryLowThreshold;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComprehensiveChecks {
        private double emaAlignmentScore;
        private double volumeConsistencyScore;
        private double priceActionScore;
        private double overallScore;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlatMarketAdjustments {
        private int categoryIncrement;
        private double minQualityScore;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolumeConsistency {
        private double minVolumeMultiplier;
        private int requiredVolumeSignals;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AtrSettings {
        private int period;
        private double minATR;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LookbackSettings {
        private int candleAnalysis;
        private int volatilityAnalysis;
    }
}
