package com.jtradebot.processor.model.strategy;

import com.jtradebot.processor.model.indicator.MarketConditionValidation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScalpingEntryDecision {
    
    private String strategyType; // CALL or PUT
    private boolean shouldEnter; // Whether to enter the trade
    private double entryPrice; // Entry price
    private int positionSize; // Position size in quantity
    private double stopLossPrice; // Stop loss price
    private double targetPrice; // Target price
    private double confidenceScore; // Entry confidence score
    private String entryReason; // Reason for entry
    private String rejectionReason; // Reason for rejection if shouldEnter is false
    private LocalDateTime entryTime; // Entry time
    private int maxHoldingTimeMinutes; // Maximum holding time
    private boolean useTrailingStop; // Use trailing stop
    private double trailingStopPercentage; // Trailing stop percentage
    private boolean useBreakEven; // Use break even
    private double breakEvenTrigger; // Break even trigger
    private Map<String, Object> entryDetails; // Additional entry details
    private List<String> satisfiedConditions; // Satisfied entry conditions
    private List<String> unsatisfiedConditions; // Unsatisfied entry conditions
    private MarketConditionValidation marketConditions; // Market condition validation
    private EntryQualityScore entryQuality; // Entry quality assessment
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntryQualityScore {
        private double volumeScore; // Volume quality score (0-1)
        private double momentumScore; // Momentum quality score (0-1)
        private double trendScore; // Trend alignment score (0-1)
        private double signalScore; // Signal strength score (0-1)
        private double overallScore; // Overall quality score (0-1)
        private String qualityGrade; // A, B, C, D, F
        private String qualityDescription; // Description of quality
    }
}
