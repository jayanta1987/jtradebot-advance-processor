package com.jtradebot.processor.model.strategy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Detailed breakdown of category scoring showing individual indicator contributions
 */
@Data
@Builder
@AllArgsConstructor
public class DetailedCategoryScore {
    
    private String categoryName;
    private Integer totalScore;
    private Integer maxPossibleScore;
    private Double scorePercentage;
    
    // Detailed breakdown of individual indicators
    private List<IndicatorContribution> indicatorContributions;
    
    // Summary of what contributed to the score
    private String scoreSummary;
    
    public DetailedCategoryScore() {
        this.indicatorContributions = new ArrayList<>();
        this.totalScore = 0;
        this.maxPossibleScore = 0;
    }
    
    public void addIndicatorScore(String indicatorName, Integer weightage, boolean isSatisfied) {
        if (indicatorContributions == null) {
            indicatorContributions = new ArrayList<>();
        }
        
        IndicatorContribution contribution = IndicatorContribution.builder()
                .indicatorName(indicatorName)
                .displayName(getDisplayName(indicatorName))
                .isSatisfied(isSatisfied)
                .weightage(weightage != null ? weightage : 1)
                .description(getDescription(indicatorName))
                .timeframe(getTimeframe(indicatorName))
                .build();
        
        indicatorContributions.add(contribution);
        
        // Update totals
        if (isSatisfied) {
            totalScore += (weightage != null ? weightage : 1);
        }
        maxPossibleScore += (weightage != null ? weightage : 1);
        
        // Calculate percentage
        if (maxPossibleScore > 0) {
            scorePercentage = (double) totalScore / maxPossibleScore * 100.0;
        }
        
        // Generate summary
        generateScoreSummary();
    }
    
    private String getDisplayName(String indicatorName) {
        // Convert indicator names to readable display names
        if (indicatorName.contains("ema5_5min_gt_ema34_5min")) return "EMA5 > EMA34 (5min)";
        if (indicatorName.contains("ema5_1min_gt_ema34_1min")) return "EMA5 > EMA34 (1min)";
        if (indicatorName.contains("ema5_15min_gt_ema34_15min")) return "EMA5 > EMA34 (15min)";
        if (indicatorName.contains("ema5_5min_lt_ema34_5min")) return "EMA5 < EMA34 (5min)";
        if (indicatorName.contains("ema5_1min_lt_ema34_1min")) return "EMA5 < EMA34 (1min)";
        if (indicatorName.contains("ema5_15min_lt_ema34_15min")) return "EMA5 < EMA34 (15min)";
        if (indicatorName.contains("rsi_5min_gt_60")) return "RSI > 60 (5min)";
        if (indicatorName.contains("rsi_1min_gt_60")) return "RSI > 60 (1min)";
        if (indicatorName.contains("rsi_15min_gt_60")) return "RSI > 60 (15min)";
        if (indicatorName.contains("rsi_5min_lt_40")) return "RSI < 40 (5min)";
        if (indicatorName.contains("rsi_1min_lt_40")) return "RSI < 40 (1min)";
        if (indicatorName.contains("rsi_15min_lt_40")) return "RSI < 40 (15min)";
        if (indicatorName.contains("volume_5min_surge")) return "Volume Surge (5min)";
        if (indicatorName.contains("volume_1min_surge")) return "Volume Surge (1min)";
        if (indicatorName.contains("volume_15min_surge")) return "Volume Surge (15min)";
        if (indicatorName.contains("price_gt_vwap_5min")) return "Price > VWAP (5min)";
        if (indicatorName.contains("price_lt_vwap_5min")) return "Price < VWAP (5min)";
        if (indicatorName.contains("bearish_engulfing_5min")) return "Bearish Engulfing (5min)";
        if (indicatorName.contains("bullish_engulfing_5min")) return "Bullish Engulfing (5min)";
        if (indicatorName.contains("shooting_star_5min")) return "Shooting Star (5min)";
        if (indicatorName.contains("hammer_5min")) return "Hammer (5min)";
        
        return indicatorName; // Fallback to original name
    }
    
    private String getDescription(String indicatorName) {
        // Provide descriptions for indicators
        if (indicatorName.contains("ema")) return "Exponential Moving Average comparison";
        if (indicatorName.contains("rsi")) return "Relative Strength Index level";
        if (indicatorName.contains("volume")) return "Volume surge detection";
        if (indicatorName.contains("vwap")) return "Volume Weighted Average Price comparison";
        if (indicatorName.contains("engulfing")) return "Candlestick pattern";
        if (indicatorName.contains("shooting_star")) return "Bearish reversal pattern";
        if (indicatorName.contains("hammer")) return "Bullish reversal pattern";
        
        return "Technical indicator";
    }
    
    private String getTimeframe(String indicatorName) {
        if (indicatorName.contains("1min")) return "1min";
        if (indicatorName.contains("5min")) return "5min";
        if (indicatorName.contains("15min")) return "15min";
        return "Unknown";
    }
    
    private void generateScoreSummary() {
        if (indicatorContributions == null || indicatorContributions.isEmpty()) {
            scoreSummary = "No indicators evaluated";
            return;
        }
        
        long satisfiedCount = indicatorContributions.stream()
                .filter(IndicatorContribution::getIsSatisfied)
                .count();
        
        scoreSummary = String.format("%d/%d indicators satisfied (%.1f%%)", 
                satisfiedCount, indicatorContributions.size(), scorePercentage);
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndicatorContribution {
        private String indicatorName;
        private String displayName;
        private Boolean isSatisfied;
        private Integer weightage;
        private String description;
        private String timeframe; // "1min", "5min", "15min"
    }
}
