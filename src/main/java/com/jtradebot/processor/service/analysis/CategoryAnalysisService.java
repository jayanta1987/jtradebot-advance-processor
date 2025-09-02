package com.jtradebot.processor.service.analysis;

import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.model.strategy.ScalpingEntryConfig;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryAnalysisService {

    private final DynamicStrategyConfigService configService;

    /**
     * Get category counts as a map for scenario evaluation
     */
    public Map<String, Integer> getCategoryCountsMap(FlattenedIndicators indicators, String strategy) {
        Map<String, Integer> categoryCounts = new HashMap<>();
        
        if ("CALL".equals(strategy)) {
            categoryCounts.put("ema", getEmaCount(indicators, true));
            categoryCounts.put("futureAndVolume", getFutureVolumeCount(indicators, true));
            categoryCounts.put("candlestick", getCandlestickCount(indicators, true));
            categoryCounts.put("momentum", getMomentumCount(indicators, true));
        } else if ("PUT".equals(strategy)) {
            categoryCounts.put("ema", getEmaCount(indicators, false));
            categoryCounts.put("futureAndVolume", getFutureVolumeCount(indicators, false));
            categoryCounts.put("candlestick", getCandlestickCount(indicators, false));
            categoryCounts.put("momentum", getMomentumCount(indicators, false));
        }
        
        return categoryCounts;
    }
    
    /**
     * Get EMA count for the given strategy
     */
    public int getEmaCount(FlattenedIndicators indicators, boolean isCall) {
        int emaCount = 0;
        if (isCall) {
            // EMA5 vs EMA34 logic
            if (Boolean.TRUE.equals(indicators.getEma5_5min_gt_ema34_5min())) emaCount++;
            if (Boolean.TRUE.equals(indicators.getEma5_1min_gt_ema34_1min())) emaCount++;
            if (Boolean.TRUE.equals(indicators.getEma5_15min_gt_ema34_15min())) emaCount++;
        } else {
            // EMA5 vs EMA34 logic
            if (Boolean.TRUE.equals(indicators.getEma5_5min_lt_ema34_5min())) emaCount++;
            if (Boolean.TRUE.equals(indicators.getEma5_1min_lt_ema34_1min())) emaCount++;
            if (Boolean.TRUE.equals(indicators.getEma5_15min_lt_ema34_15min())) emaCount++;
        }
        return emaCount;
    }
    
    /**
     * Get Future & Volume count for the given strategy
     */
    public int getFutureVolumeCount(FlattenedIndicators indicators, boolean isCall) {
        int futureVolumeCount = 0;
        if (isCall) {
            if (Boolean.TRUE.equals(indicators.getVolume_5min_surge())) futureVolumeCount++;
            if (Boolean.TRUE.equals(indicators.getVolume_1min_surge())) futureVolumeCount++;
            if (Boolean.TRUE.equals(indicators.getVolume_15min_surge())) futureVolumeCount++;
            if (Boolean.TRUE.equals(indicators.getPrice_gt_vwap_5min())) futureVolumeCount++;
            if (Boolean.TRUE.equals(indicators.getPrice_gt_vwap_1min())) futureVolumeCount++;
            if (Boolean.TRUE.equals(indicators.getPrice_gt_vwap_15min())) futureVolumeCount++;
            if (Boolean.TRUE.equals(indicators.getPrice_above_resistance())) futureVolumeCount++;
        } else {
            if (Boolean.TRUE.equals(indicators.getVolume_5min_surge())) futureVolumeCount++;
            if (Boolean.TRUE.equals(indicators.getVolume_1min_surge())) futureVolumeCount++;
            if (Boolean.TRUE.equals(indicators.getVolume_15min_surge())) futureVolumeCount++;
            if (Boolean.TRUE.equals(indicators.getPrice_lt_vwap_5min())) futureVolumeCount++;
            if (Boolean.TRUE.equals(indicators.getPrice_lt_vwap_1min())) futureVolumeCount++;
            if (Boolean.TRUE.equals(indicators.getPrice_lt_vwap_15min())) futureVolumeCount++;
            if (Boolean.TRUE.equals(indicators.getPrice_below_support())) futureVolumeCount++;
        }
        return futureVolumeCount;
    }
    
    /**
     * Get Candlestick count for the given strategy
     */
    public int getCandlestickCount(FlattenedIndicators indicators, boolean isCall) {
        int candlestickCount = 0;
        if (isCall) {
            if (Boolean.TRUE.equals(indicators.getGreen_candle_5min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getGreen_candle_1min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getLong_body_5min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getLong_body_1min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getBullish_engulfing_5min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getBullish_engulfing_1min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getBullish_morning_star_5min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getBullish_morning_star_1min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getHammer_5min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getHammer_1min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getMarubozu_5min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getMarubozu_1min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getInside_bar_breakout_5min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getWick_rejection_filter_1min())) candlestickCount++;
        } else {
            if (Boolean.TRUE.equals(indicators.getRed_candle_5min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getRed_candle_1min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getLong_body_5min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getLong_body_1min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getBearish_engulfing_5min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getBearish_engulfing_1min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getBearish_evening_star_5min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getBearish_evening_star_1min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getShooting_star_5min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getShooting_star_1min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getMarubozu_5min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getMarubozu_1min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getInside_bar_breakdown_5min())) candlestickCount++;
            if (Boolean.TRUE.equals(indicators.getWick_rejection_filter_1min())) candlestickCount++;
        }
        return candlestickCount;
    }
    
    /**
     * Get Momentum count for the given strategy
     */
    public int getMomentumCount(FlattenedIndicators indicators, boolean isCall) {
        int momentumCount = 0;
        if (isCall) {
            if (Boolean.TRUE.equals(indicators.getRsi_5min_gt_60())) momentumCount++;
            if (Boolean.TRUE.equals(indicators.getRsi_1min_gt_60())) momentumCount++;
            if (Boolean.TRUE.equals(indicators.getRsi_15min_gt_60())) momentumCount++;
            // RSI MA comparison for bullish momentum
            if (Boolean.TRUE.equals(indicators.getRsi_5min_gt_rsi_ma())) momentumCount++;
            if (Boolean.TRUE.equals(indicators.getRsi_1min_gt_rsi_ma())) momentumCount++;
            if (Boolean.TRUE.equals(indicators.getRsi_15min_gt_rsi_ma())) momentumCount++;
            // RSI Divergence for bullish momentum
            if (Boolean.TRUE.equals(indicators.getRsi_bullish_divergence_5min())) momentumCount++;
            if (Boolean.TRUE.equals(indicators.getRsi_bullish_divergence_1min())) momentumCount++;
            if (Boolean.TRUE.equals(indicators.getRsi_bullish_divergence_15min())) momentumCount++;
        } else {
            if (Boolean.TRUE.equals(indicators.getRsi_5min_lt_40())) momentumCount++;
            if (Boolean.TRUE.equals(indicators.getRsi_1min_lt_40())) momentumCount++;
            if (Boolean.TRUE.equals(indicators.getRsi_15min_lt_40())) momentumCount++;
            // RSI MA comparison for bearish momentum
            if (Boolean.TRUE.equals(indicators.getRsi_5min_lt_rsi_ma())) momentumCount++;
            if (Boolean.TRUE.equals(indicators.getRsi_1min_lt_rsi_ma())) momentumCount++;
            if (Boolean.TRUE.equals(indicators.getRsi_15min_lt_rsi_ma())) momentumCount++;
            // RSI Divergence for bearish momentum
            if (Boolean.TRUE.equals(indicators.getRsi_bearish_divergence_5min())) momentumCount++;
            if (Boolean.TRUE.equals(indicators.getRsi_bearish_divergence_1min())) momentumCount++;
            if (Boolean.TRUE.equals(indicators.getRsi_bearish_divergence_15min())) momentumCount++;
        }
        return momentumCount;
    }

}
