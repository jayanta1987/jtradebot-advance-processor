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
public class ProfitableTradeFilterResult {
    
    private Boolean isProfitableEntry;
    private String strategyType; // "CALL" or "PUT"
    private String rejectionReason;
    
    // Quality checks
    private Boolean qualityScorePassed;
    private Boolean candlestickScorePassed;
    private Boolean volumeSurgeMultiplierPassed;
    
    // Category checks
    private Boolean emaCategoryPassed;
    private Boolean futureAndVolumeCategoryPassed;
    private Boolean candlestickCategoryPassed;
    private Boolean momentumCategoryPassed;
    
    // Category results
    private CategoryResult emaCategory;
    private CategoryResult futureAndVolumeCategory;
    private CategoryResult candlestickCategory;
    private CategoryResult momentumCategory;
    
    // Scores
    private Double qualityScore;
    private Double candlestickScore;
    private Double volumeSurgeMultiplier;
    
    // High quality entry logic tracking
    private Boolean highQualityEntryLogicApplied;
    
    // Helper methods
    public boolean isRejected() {
        return !isProfitableEntry;
    }
    
    public String getSummary() {
        if (isProfitableEntry) {
            String hqIndicator = Boolean.TRUE.equals(highQualityEntryLogicApplied) ? " [HQ]" : "";
            return String.format("PROFITABLE_ENTRY%s - %s | Quality: %.1f | Candlestick: %.1f | Volume: %.1fx | Categories: EMA:%s, FV:%s, CS:%s, M:%s",
                    hqIndicator, strategyType, qualityScore, candlestickScore, volumeSurgeMultiplier, 
                    emaCategoryPassed ? "✓" : "✗", futureAndVolumeCategoryPassed ? "✓" : "✗", 
                    candlestickCategoryPassed ? "✓" : "✗", momentumCategoryPassed ? "✓" : "✗");
        } else {
            return String.format("REJECTED - %s | Reason: %s", strategyType, rejectionReason);
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryResult {
        private String categoryName;
        private Boolean passed;
        private Integer passedCount;
        private Integer requiredCount;
        private List<String> passedConditions;
        private List<String> failedConditions;
        private String failureReason;
    }
}
