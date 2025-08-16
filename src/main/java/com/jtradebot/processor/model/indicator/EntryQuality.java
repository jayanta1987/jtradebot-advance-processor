package com.jtradebot.processor.model.indicator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntryQuality {
    
    // Basic identification
    private String instrumentToken;
    private LocalDateTime timestamp;
    private Double lastTradedPrice;
    
    // Individual component scores (0-10 scale)
    private Double emaScore;
    private Double rsiScore;
    private Double volumeScore;
    private Double priceActionScore;
    private Double futuresignalScore;
    private Double momentumScore;
    private Double candlestickScore;
    
    // Overall quality score (0-10 scale)
    private Double qualityScore;
    
    // Entry type
    private String entryType; // "CALL" or "PUT"
    
    // Market context
    private Boolean isMarketOpen;
    private Boolean isHighVolatility;
    private Boolean isTrending;
    
    // Risk management
    private Boolean isWithinRiskLimits;
    private Boolean isGoodEntryTiming;
    
    // Quality level
    private String qualityLevel; // "EXCELLENT", "GOOD", "FAIR", "POOR"
    
    /**
     * Calculate overall quality score based on component scores
     */
    public void calculateQualityScore() {
        double totalScore = 0.0;
        int componentCount = 0;
        
        if (emaScore != null) {
            totalScore += emaScore;
            componentCount++;
        }
        if (rsiScore != null) {
            totalScore += rsiScore;
            componentCount++;
        }
        if (volumeScore != null) {
            totalScore += volumeScore;
            componentCount++;
        }
        if (priceActionScore != null) {
            totalScore += priceActionScore;
            componentCount++;
        }
        if (futuresignalScore != null) {
            totalScore += futuresignalScore;
            componentCount++;
        }
        if (momentumScore != null) {
            totalScore += momentumScore;
            componentCount++;
        }
        if (candlestickScore != null) {
            totalScore += candlestickScore;
            componentCount++;
        }
        
        this.qualityScore = componentCount > 0 ? totalScore / componentCount : 0.0;
        
        // Determine quality level
        if (this.qualityScore >= 8.0) {
            this.qualityLevel = "EXCELLENT";
        } else if (this.qualityScore >= 6.0) {
            this.qualityLevel = "GOOD";
        } else if (this.qualityScore >= 4.0) {
            this.qualityLevel = "FAIR";
        } else {
            this.qualityLevel = "POOR";
        }
    }
    
    /**
     * Get quality description
     */
    public String getQualityDescription() {
        return String.format("%s (%.1f/10)", qualityLevel, qualityScore);
    }
}
