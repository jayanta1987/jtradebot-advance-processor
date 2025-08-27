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
public class FlattenedIndicators {
    
    // Basic identification
    private String instrumentToken;
    private LocalDateTime timestamp;
    private String timeframe;
    
    // EMA Crossover indicators (EMA5 vs EMA34)
    private Boolean ema5_1min_gt_ema34_1min;
    private Boolean ema5_5min_gt_ema34_5min;
    private Boolean ema5_15min_gt_ema34_15min;
    
    // Bearish EMA indicators (EMA5 < EMA34)
    private Boolean ema5_1min_lt_ema34_1min;
    private Boolean ema5_5min_lt_ema34_5min;
    private Boolean ema5_15min_lt_ema34_15min;
    
    // EMA 200 distance indicators (distance from EMA 200)
    private Double ema200_distance_1min; // Distance from EMA 200 in 1min timeframe
    private Double ema200_distance_5min; // Distance from EMA 200 in 5min timeframe
    private Double ema200_distance_15min; // Distance from EMA 200 in 15min timeframe
    
    // EMA values for additional filtering
    private Double ema34_5min; // EMA 34 value in 5min timeframe
    private Double ema200_5min; // EMA 200 value in 5min timeframe
    
    // RSI indicators - Updated thresholds
    private Boolean rsi_1min_gt_80;
    private Boolean rsi_5min_gt_80;
    private Boolean rsi_15min_gt_80;
    private Boolean rsi_1min_lt_20;
    private Boolean rsi_5min_lt_20;
    private Boolean rsi_15min_lt_20;
    
    // New RSI indicators for updated thresholds (60 and 40)
    private Boolean rsi_1min_gt_60;
    private Boolean rsi_5min_gt_60;
    private Boolean rsi_15min_gt_60;
    private Boolean rsi_1min_lt_40;
    private Boolean rsi_5min_lt_40;
    private Boolean rsi_15min_lt_40;
    
    // RSI MA comparison indicators
    private Boolean rsi_1min_gt_rsi_ma;
    private Boolean rsi_5min_gt_rsi_ma;
    private Boolean rsi_15min_gt_rsi_ma;
    private Boolean rsi_1min_lt_rsi_ma;
    private Boolean rsi_5min_lt_rsi_ma;
    private Boolean rsi_15min_lt_rsi_ma;
    
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
    
    // Breakout strength indicators
    private Double breakoutStrength; // Percentage above resistance
    private Double breakdownStrength; // Percentage below support
    
    // Candlestick Pattern indicators
    // Bullish Patterns
    private Boolean bullish_engulfing_1min;
    private Boolean bullish_engulfing_3min;
    private Boolean bullish_engulfing_5min;
    private Boolean bullish_harami_1min;
    private Boolean bullish_harami_3min;
    private Boolean bullish_harami_5min;
    private Boolean bullish_morning_star_1min;
    private Boolean bullish_morning_star_3min;
    private Boolean bullish_morning_star_5min;
    private Boolean hammer_1min;
    private Boolean hammer_3min;
    private Boolean hammer_5min;
    private Boolean inverted_hammer_1min;
    private Boolean inverted_hammer_3min;
    private Boolean inverted_hammer_5min;
    private Boolean bullish_marubozu_1min;
    private Boolean bullish_marubozu_3min;
    private Boolean bullish_marubozu_5min;
    private Boolean long_lower_shadow_1min;
    private Boolean long_lower_shadow_3min;
    private Boolean long_lower_shadow_5min;
    
    // Bearish Patterns
    private Boolean bearish_engulfing_1min;
    private Boolean bearish_engulfing_3min;
    private Boolean bearish_engulfing_5min;
    private Boolean bearish_harami_1min;
    private Boolean bearish_harami_3min;
    private Boolean bearish_harami_5min;
    private Boolean bearish_evening_star_1min;
    private Boolean bearish_evening_star_3min;
    private Boolean bearish_evening_star_5min;
    private Boolean shooting_star_1min;
    private Boolean shooting_star_3min;
    private Boolean shooting_star_5min;
    private Boolean hanging_man_1min;
    private Boolean hanging_man_3min;
    private Boolean hanging_man_5min;
    private Boolean bearish_marubozu_1min;
    private Boolean bearish_marubozu_3min;
    private Boolean bearish_marubozu_5min;
    private Boolean long_upper_shadow_1min;
    private Boolean long_upper_shadow_3min;
    private Boolean long_upper_shadow_5min;
    
    // Neutral Patterns
    private Boolean doji_1min;
    private Boolean doji_3min;
    private Boolean doji_5min;
    private Boolean spinning_top_1min;
    private Boolean spinning_top_3min;
    private Boolean spinning_top_5min;
    private Boolean marubozu_1min;
    private Boolean marubozu_3min;
    private Boolean marubozu_5min;
    private Boolean long_body_1min;
    private Boolean long_body_3min;
    private Boolean long_body_5min;
    private Boolean short_body_1min;
    private Boolean short_body_3min;
    private Boolean short_body_5min;
    
    // Candle Color indicators (for directional confirmation)
    private Boolean green_candle_1min;
    private Boolean green_candle_3min;
    private Boolean green_candle_5min;
    private Boolean red_candle_1min;
    private Boolean red_candle_3min;
    private Boolean red_candle_5min;
    
    // Futuresignals for each timeframe
    private FuturesignalData futuresignals;
    
    // Strategy decision
    private String recommendedStrategy; // CALL, PUT, or NO_TRADE
    private Double strategyConfidence; // 0.0 to 1.0
}
