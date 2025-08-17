package com.jtradebot.processor.model.strategy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyScore {
    
    // Basic identification
    private String instrumentToken;
    private LocalDateTime timestamp;
    private Double lastTradedPrice;
    
    // Main score (-10 to +10)
    // Positive = Bullish/CALL strength, Negative = Bearish/PUT strength
    private Double score;
    
    // Score breakdown by component
    private Double emaScore;        // EMA crossover contribution
    private Double rsiScore;        // RSI condition contribution  
    private Double volumeScore;     // Volume surge contribution
    private Double priceActionScore; // Price action contribution
    private Double futuresignalScore; // Futuresignal contribution
    
    // Score interpretation
    private String sentiment;       // "BULLISH", "BEARISH", "NEUTRAL"
    private String strength;        // "STRONG", "MODERATE", "WEAK"
    private String recommendation;  // "CALL", "PUT", "NO_TRADE"
    
    // Entry conditions
    private Boolean shouldMakeCallEntry;
    private Boolean shouldMakePutEntry;
    
    // Confidence level (0.0 to 1.0)
    private Double confidence;
    
    // Score thresholds for interpretation
    private static final double STRONG_THRESHOLD = 6.0;
    private static final double MODERATE_THRESHOLD = 3.0;
    private static final double WEAK_THRESHOLD = 1.5;
    
    /**
     * Interpret the score and set sentiment, strength, and recommendation
     */
    public void interpretScore() {
        double absScore = Math.abs(score);
        
        // Set sentiment
        if (score > 0) {
            sentiment = "BULLISH";
        } else if (score < 0) {
            sentiment = "BEARISH";
        } else {
            sentiment = "NEUTRAL";
        }
        
        // Set strength
        if (absScore >= STRONG_THRESHOLD) {
            strength = "STRONG";
        } else if (absScore >= MODERATE_THRESHOLD) {
            strength = "MODERATE";
        } else if (absScore >= WEAK_THRESHOLD) {
            strength = "WEAK";
        } else {
            strength = "VERY_WEAK";
        }
        
        // Set recommendation
        if (shouldMakeCallEntry != null && shouldMakeCallEntry) {
            recommendation = "CALL";
        } else if (shouldMakePutEntry != null && shouldMakePutEntry) {
            recommendation = "PUT";
        } else {
            recommendation = "NO_TRADE";
        }
    }
    
    /**
     * Get a human-readable score description
     */
    public String getScoreDescription() {
        if (score == null) {
            return "NO_SCORE";
        }
        
        String direction = score > 0 ? "BULLISH" : "BEARISH";
        double absScore = Math.abs(score);
        
        if (absScore >= STRONG_THRESHOLD) {
            return String.format("STRONG_%s (%.1f)", direction, absScore);
        } else if (absScore >= MODERATE_THRESHOLD) {
            return String.format("MODERATE_%s (%.1f)", direction, absScore);
        } else if (absScore >= WEAK_THRESHOLD) {
            return String.format("WEAK_%s (%.1f)", direction, absScore);
        } else {
            return String.format("VERY_WEAK_%s (%.1f)", direction, absScore);
        }
    }
    
    /**
     * Get score with sign indicator
     */
    public String getScoreWithSign() {
        if (score == null) {
            return "0";
        }
        return String.format("%+.1f", score);
    }
}
