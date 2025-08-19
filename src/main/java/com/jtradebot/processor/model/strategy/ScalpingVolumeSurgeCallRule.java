package com.jtradebot.processor.model.strategy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScalpingVolumeSurgeCallRule {
    
    // Rule identification
    private String ruleName;
    private String strategyType; // "SCALPING_FUTURE_VOLUME_SURGE_BULLISH"
    private Boolean isActive;
    
    // Entry conditions for CALL
    private Boolean requireAllTimeframesBullish;
    private Boolean requireVolumeSurge;
    private Boolean requireEmaCrossover;
    private Boolean requireRsiOverbought;
    private Boolean requirePriceAboveVwap;
    private Boolean requirePriceAboveResistance;
    
    // Thresholds
    private Double minVolumeSurgeMultiplier; // e.g., 2.0 for 2x average
    private Double minRsiThreshold; // e.g., 70.0
    private Double minSignalStrength; // e.g., 7.0
    
    // Timeframes to check
    private Boolean check1Min;
    private Boolean check5Min;
    private Boolean check15Min;
    
    // Risk management
    private Double stopLossPercentage;
    private Double targetPercentage;
}
