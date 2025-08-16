package com.jtradebot.processor.model.strategy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScalpingVolumeSurgePutRule {
    
    // Rule identification
    private String ruleName;
    private String strategyType; // "SCALPING_FUTURE_VOLUME_SURGE_BEARISH"
    private Boolean isActive;
    
    // Entry conditions for PUT
    private Boolean requireAllTimeframesBearish;
    private Boolean requireVolumeSurge;
    private Boolean requireEmaCrossover;
    private Boolean requireRsiOversold;
    private Boolean requirePriceBelowVwap;
    private Boolean requirePriceBelowSupport;
    
    // Thresholds
    private Double minVolumeSurgeMultiplier; // e.g., 2.0 for 2x average
    private Double maxRsiThreshold; // e.g., 30.0
    private Double minSignalStrength; // e.g., 7.0
    
    // Timeframes to check
    private Boolean check1Min;
    private Boolean check5Min;
    private Boolean check15Min;
    
    // Risk management
    private Double stopLossPercentage;
    private Double targetPercentage;
    private Integer maxHoldingTimeMinutes;
}
