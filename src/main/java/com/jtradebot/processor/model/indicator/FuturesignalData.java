package com.jtradebot.processor.model.indicator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FuturesignalData {
    
    // 1 minute timeframe futuresignals
    private Boolean oneMinBullishSurge;
    private Boolean oneMinBearishSurge;
    private String oneMinTrendDirection; // UP, DOWN, SIDEWAYS
    private Double oneMinSignalStrength; // 0.0 to 10.0
    
    // 5 minute timeframe futuresignals
    private Boolean fiveMinBullishSurge;
    private Boolean fiveMinBearishSurge;
    private String fiveMinTrendDirection; // UP, DOWN, SIDEWAYS
    private Double fiveMinSignalStrength; // 0.0 to 10.0
    
    // 15 minute timeframe futuresignals
    private Boolean fifteenMinBullishSurge;
    private Boolean fifteenMinBearishSurge;
    private String fifteenMinTrendDirection; // UP, DOWN, SIDEWAYS
    private Double fifteenMinSignalStrength; // 0.0 to 10.0
    
    // Overall futuresignal summary
    private Boolean allTimeframesBullish;
    private Boolean allTimeframesBearish;
    private String overallTrendDirection;
    private Double overallSignalStrength;
}
