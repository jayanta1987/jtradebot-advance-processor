package com.jtradebot.processor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlattenedIndicators {
    
    // Basic identification
    private String instrumentToken;
    private LocalDateTime timestamp;
    private String timeframe;
    
    // EMA Crossover indicators
    private Boolean ema9_1min_gt_ema21_1min;
    private Boolean ema9_5min_gt_ema21_5min;
    private Boolean ema9_15min_gt_ema21_15min;
    
    // RSI indicators - Updated thresholds
    private Boolean rsi_1min_gt_70;
    private Boolean rsi_5min_gt_70;
    private Boolean rsi_15min_gt_70;
    private Boolean rsi_1min_lt_30;
    private Boolean rsi_5min_lt_30;
    private Boolean rsi_15min_lt_30;
    
    // New RSI indicators for updated thresholds (56 and 44)
    private Boolean rsi_1min_gt_56;
    private Boolean rsi_5min_gt_56;
    private Boolean rsi_15min_gt_56;
    private Boolean rsi_1min_lt_44;
    private Boolean rsi_5min_lt_44;
    private Boolean rsi_15min_lt_44;
    
    // Volume surge indicators
    private Boolean volume_1min_surge;
    private Boolean volume_5min_surge;
    private Boolean volume_15min_surge;
    private Double volume_surge_multiplier;
    
    // Price action indicators
    private Boolean price_gt_vwap_1min;
    private Boolean price_gt_vwap_5min;
    private Boolean price_gt_vwap_15min;
    private Boolean price_lt_vwap_1min;
    private Boolean price_lt_vwap_5min;
    private Boolean price_lt_vwap_15min;
    
    // Support/Resistance indicators
    private Boolean price_above_resistance;
    private Boolean price_below_support;
    
    // Futuresignals for each timeframe
    private FuturesignalData futuresignals;
    
    // Strategy decision
    private String recommendedStrategy; // CALL, PUT, or NO_TRADE
    private Double strategyConfidence; // 0.0 to 1.0
}
