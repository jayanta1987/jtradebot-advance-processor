package com.jtradebot.processor.service;

import com.jtradebot.processor.model.indicator.EntryQuality;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EntryConditionAnalysisService {
    
    private final ScalpingVolumeSurgeService scalpingVolumeSurgeService;
    
    /**
     * Capture all conditions that led to the order entry - Category-based approach aligned with JSON configuration
     */
    public List<String> captureEntryConditions(Tick tick, String orderType) {
        List<String> conditions = new ArrayList<>();
        
        try {
            // Get flattened indicators
            FlattenedIndicators indicators = scalpingVolumeSurgeService.getFlattenedIndicators(tick);
            
            if (indicators == null) {
                conditions.add("ERROR: No indicators available");
                return conditions;
            }
            
            // Add quality scores
            if ("CALL_BUY".equals(orderType)) {
                EntryQuality callQuality = scalpingVolumeSurgeService.evaluateCallEntryQuality(indicators, tick);
                conditions.add(String.format("Quality Score: %.1f/10", callQuality.getQualityScore()));
                conditions.add(String.format("EMA Score: %.1f/10", callQuality.getEmaScore()));
                conditions.add(String.format("RSI Score: %.1f/10", callQuality.getRsiScore()));
                conditions.add(String.format("Volume Score: %.1f/10", callQuality.getVolumeScore()));
                conditions.add(String.format("Price Action Score: %.1f/10", callQuality.getPriceActionScore()));
                conditions.add(String.format("Futuresignal Score: %.1f/10", callQuality.getFuturesignalScore()));
                conditions.add(String.format("Momentum Score: %.1f/10", callQuality.getMomentumScore()));
                conditions.add(String.format("Candlestick Score: %.1f/10", callQuality.getCandlestickScore()));
            } else if ("PUT_BUY".equals(orderType)) {
                EntryQuality putQuality = scalpingVolumeSurgeService.evaluatePutEntryQuality(indicators, tick);
                conditions.add(String.format("Quality Score: %.1f/10", putQuality.getQualityScore()));
                conditions.add(String.format("EMA Score: %.1f/10", putQuality.getEmaScore()));
                conditions.add(String.format("RSI Score: %.1f/10", putQuality.getRsiScore()));
                conditions.add(String.format("Volume Score: %.1f/10", putQuality.getVolumeScore()));
                conditions.add(String.format("Price Action Score: %.1f/10", putQuality.getPriceActionScore()));
                conditions.add(String.format("Futuresignal Score: %.1f/10", putQuality.getFuturesignalScore()));
                conditions.add(String.format("Momentum Score: %.1f/10", putQuality.getMomentumScore()));
                conditions.add(String.format("Candlestick Score: %.1f/10", putQuality.getCandlestickScore()));
            }
            
            // Capture conditions by category using the same logic as ProfitableTradeFilterService
            conditions.add("--- CATEGORY-BASED CONDITIONS (JSON Configuration) ---");
            
            if ("CALL_BUY".equals(orderType)) {
                captureCallCategoryConditions(conditions, indicators);
            } else if ("PUT_BUY".equals(orderType)) {
                capturePutCategoryConditions(conditions, indicators);
            }
            
            // Add futuresignal information
            if (indicators.getFuturesignals() != null) {
                conditions.add("--- FUTURESIGNALS ---");
                if (indicators.getFuturesignals().getAllTimeframesBullish()) {
                    conditions.add("✓ All timeframes bullish");
                } else if (indicators.getFuturesignals().getAllTimeframesBearish()) {
                    conditions.add("✓ All timeframes bearish");
                } else {
                    conditions.add("Mixed futuresignals");
                }
            }
            
            // Add volume surge multiplier if available
            if (indicators.getVolume_surge_multiplier() != null) {
                conditions.add(String.format("Volume Surge Multiplier: %.2fx", indicators.getVolume_surge_multiplier()));
            }
            
        } catch (Exception e) {
            conditions.add("ERROR: Failed to capture conditions - " + e.getMessage());
            log.error("Error capturing entry conditions: {}", e.getMessage());
        }
        
        return conditions;
    }
    
    /**
     * Capture CALL strategy conditions by category (aligned with JSON configuration)
     */
    private void captureCallCategoryConditions(List<String> conditions, FlattenedIndicators indicators) {
        // EMA Category (need 2/3)
        conditions.add("--- EMA CATEGORY (Need 2/3) ---");
        int emaPassed = 0;
        if (checkCondition(indicators, "ema9_5min_gt_ema21_5min")) { conditions.add("✓ ema9_5min_gt_ema21_5min"); emaPassed++; }
        if (checkCondition(indicators, "ema9_1min_gt_ema21_1min")) { conditions.add("✓ ema9_1min_gt_ema21_1min"); emaPassed++; }
        if (checkCondition(indicators, "ema9_15min_gt_ema21_15min")) { conditions.add("✓ ema9_15min_gt_ema21_15min"); emaPassed++; }
        conditions.add(String.format("EMA Category: %d/3 passed (Need 2)", emaPassed));
        
        // Future and Volume Category (need 4/7)
        conditions.add("--- FUTURE & VOLUME CATEGORY (Need 4/7) ---");
        int fvPassed = 0;
        if (checkCondition(indicators, "volume_5min_surge")) { conditions.add("✓ volume_5min_surge"); fvPassed++; }
        if (checkCondition(indicators, "volume_1min_surge")) { conditions.add("✓ volume_1min_surge"); fvPassed++; }
        if (checkCondition(indicators, "volume_15min_surge")) { conditions.add("✓ volume_15min_surge"); fvPassed++; }
        if (checkCondition(indicators, "price_gt_vwap_5min")) { conditions.add("✓ price_gt_vwap_5min"); fvPassed++; }
        if (checkCondition(indicators, "price_gt_vwap_1min")) { conditions.add("✓ price_gt_vwap_1min"); fvPassed++; }
        if (checkCondition(indicators, "price_gt_vwap_15min")) { conditions.add("✓ price_gt_vwap_15min"); fvPassed++; }
        if (checkCondition(indicators, "price_above_resistance")) { conditions.add("✓ price_above_resistance"); fvPassed++; }
        conditions.add(String.format("Future & Volume Category: %d/7 passed (Need 4)", fvPassed));
        
        // Candlestick Category (need 3/10)
        conditions.add("--- CANDLESTICK CATEGORY (Need 3/10) ---");
        int csPassed = 0;
        if (checkCondition(indicators, "green_candle_5min")) { conditions.add("✓ green_candle_5min"); csPassed++; }
        if (checkCondition(indicators, "green_candle_1min")) { conditions.add("✓ green_candle_1min"); csPassed++; }
        if (checkCondition(indicators, "long_body_5min")) { conditions.add("✓ long_body_5min"); csPassed++; }
        if (checkCondition(indicators, "long_body_1min")) { conditions.add("✓ long_body_1min"); csPassed++; }
        if (checkCondition(indicators, "bullish_engulfing_5min")) { conditions.add("✓ bullish_engulfing_5min"); csPassed++; }
        if (checkCondition(indicators, "bullish_engulfing_1min")) { conditions.add("✓ bullish_engulfing_1min"); csPassed++; }
        if (checkCondition(indicators, "bullish_morning_star_5min")) { conditions.add("✓ bullish_morning_star_5min"); csPassed++; }
        if (checkCondition(indicators, "bullish_morning_star_1min")) { conditions.add("✓ bullish_morning_star_1min"); csPassed++; }
        if (checkCondition(indicators, "hammer_5min")) { conditions.add("✓ hammer_5min"); csPassed++; }
        if (checkCondition(indicators, "hammer_1min")) { conditions.add("✓ hammer_1min"); csPassed++; }
        conditions.add(String.format("Candlestick Category: %d/10 passed (Need 3)", csPassed));
        
        // Momentum Category (need 2/3)
        conditions.add("--- MOMENTUM CATEGORY (Need 2/3) ---");
        int momentumPassed = 0;
        if (checkCondition(indicators, "rsi_5min_gt_56")) { conditions.add("✓ rsi_5min_gt_56"); momentumPassed++; }
        if (checkCondition(indicators, "rsi_1min_gt_56")) { conditions.add("✓ rsi_1min_gt_56"); momentumPassed++; }
        if (checkCondition(indicators, "rsi_15min_gt_56")) { conditions.add("✓ rsi_15min_gt_56"); momentumPassed++; }
        conditions.add(String.format("Momentum Category: %d/3 passed (Need 2)", momentumPassed));
    }
    
    /**
     * Capture PUT strategy conditions by category (aligned with JSON configuration)
     */
    private void capturePutCategoryConditions(List<String> conditions, FlattenedIndicators indicators) {
        // EMA Category (need 2/3) - Bearish
        conditions.add("--- EMA CATEGORY (Need 2/3) - Bearish ---");
        int emaPassed = 0;
        if (checkCondition(indicators, "ema9_5min_lt_ema21_5min")) { conditions.add("✓ ema9_5min_lt_ema21_5min"); emaPassed++; }
        if (checkCondition(indicators, "ema9_1min_lt_ema21_1min")) { conditions.add("✓ ema9_1min_lt_ema21_1min"); emaPassed++; }
        if (checkCondition(indicators, "ema9_15min_lt_ema21_15min")) { conditions.add("✓ ema9_15min_lt_ema21_15min"); emaPassed++; }
        conditions.add(String.format("EMA Category: %d/3 passed (Need 2)", emaPassed));
        
        // Future and Volume Category (need 4/7) - Bearish
        conditions.add("--- FUTURE & VOLUME CATEGORY (Need 4/7) - Bearish ---");
        int fvPassed = 0;
        if (checkCondition(indicators, "volume_5min_surge")) { conditions.add("✓ volume_5min_surge"); fvPassed++; }
        if (checkCondition(indicators, "volume_1min_surge")) { conditions.add("✓ volume_1min_surge"); fvPassed++; }
        if (checkCondition(indicators, "volume_15min_surge")) { conditions.add("✓ volume_15min_surge"); fvPassed++; }
        if (checkCondition(indicators, "price_lt_vwap_5min")) { conditions.add("✓ price_lt_vwap_5min"); fvPassed++; }
        if (checkCondition(indicators, "price_lt_vwap_1min")) { conditions.add("✓ price_lt_vwap_1min"); fvPassed++; }
        if (checkCondition(indicators, "price_lt_vwap_15min")) { conditions.add("✓ price_lt_vwap_15min"); fvPassed++; }
        if (checkCondition(indicators, "price_below_support")) { conditions.add("✓ price_below_support"); fvPassed++; }
        conditions.add(String.format("Future & Volume Category: %d/7 passed (Need 4)", fvPassed));
        
        // Candlestick Category (need 3/10) - Bearish
        conditions.add("--- CANDLESTICK CATEGORY (Need 3/10) - Bearish ---");
        int csPassed = 0;
        if (checkCondition(indicators, "red_candle_5min")) { conditions.add("✓ red_candle_5min"); csPassed++; }
        if (checkCondition(indicators, "red_candle_1min")) { conditions.add("✓ red_candle_1min"); csPassed++; }
        if (checkCondition(indicators, "long_body_5min")) { conditions.add("✓ long_body_5min"); csPassed++; }
        if (checkCondition(indicators, "long_body_1min")) { conditions.add("✓ long_body_1min"); csPassed++; }
        if (checkCondition(indicators, "bearish_engulfing_5min")) { conditions.add("✓ bearish_engulfing_5min"); csPassed++; }
        if (checkCondition(indicators, "bearish_engulfing_1min")) { conditions.add("✓ bearish_engulfing_1min"); csPassed++; }
        if (checkCondition(indicators, "bearish_evening_star_5min")) { conditions.add("✓ bearish_evening_star_5min"); csPassed++; }
        if (checkCondition(indicators, "bearish_evening_star_1min")) { conditions.add("✓ bearish_evening_star_1min"); csPassed++; }
        if (checkCondition(indicators, "shooting_star_5min")) { conditions.add("✓ shooting_star_5min"); csPassed++; }
        if (checkCondition(indicators, "shooting_star_1min")) { conditions.add("✓ shooting_star_1min"); csPassed++; }
        conditions.add(String.format("Candlestick Category: %d/10 passed (Need 3)", csPassed));
        
        // Momentum Category (need 2/3) - Bearish
        conditions.add("--- MOMENTUM CATEGORY (Need 2/3) - Bearish ---");
        int momentumPassed = 0;
        if (checkCondition(indicators, "rsi_5min_lt_44")) { conditions.add("✓ rsi_5min_lt_44"); momentumPassed++; }
        if (checkCondition(indicators, "rsi_1min_lt_44")) { conditions.add("✓ rsi_1min_lt_44"); momentumPassed++; }
        if (checkCondition(indicators, "rsi_15min_lt_44")) { conditions.add("✓ rsi_15min_lt_44"); momentumPassed++; }
        conditions.add(String.format("Momentum Category: %d/3 passed (Need 2)", momentumPassed));
    }
    
    /**
     * Helper method to check condition value (same logic as ProfitableTradeFilterService)
     */
    private boolean checkCondition(FlattenedIndicators indicators, String condition) {
        switch (condition) {
            // EMA conditions
            case "ema9_5min_gt_ema21_5min":
                return Boolean.TRUE.equals(indicators.getEma9_5min_gt_ema21_5min());
            case "ema9_1min_gt_ema21_1min":
                return Boolean.TRUE.equals(indicators.getEma9_1min_gt_ema21_1min());
            case "ema9_15min_gt_ema21_15min":
                return Boolean.TRUE.equals(indicators.getEma9_15min_gt_ema21_15min());
            case "ema9_5min_lt_ema21_5min":
                return indicators.getEma9_5min_gt_ema21_5min() != null ? !indicators.getEma9_5min_gt_ema21_5min() : false;
            case "ema9_1min_lt_ema21_1min":
                return indicators.getEma9_1min_gt_ema21_1min() != null ? !indicators.getEma9_1min_gt_ema21_1min() : false;
            case "ema9_15min_lt_ema21_15min":
                return indicators.getEma9_15min_gt_ema21_15min() != null ? !indicators.getEma9_15min_gt_ema21_15min() : false;
            
            // Volume conditions
            case "volume_5min_surge":
                return Boolean.TRUE.equals(indicators.getVolume_5min_surge());
            case "volume_1min_surge":
                return Boolean.TRUE.equals(indicators.getVolume_1min_surge());
            case "volume_15min_surge":
                return Boolean.TRUE.equals(indicators.getVolume_15min_surge());
            
            // RSI conditions
            case "rsi_5min_gt_56":
                return Boolean.TRUE.equals(indicators.getRsi_5min_gt_56());
            case "rsi_1min_gt_56":
                return Boolean.TRUE.equals(indicators.getRsi_1min_gt_56());
            case "rsi_15min_gt_56":
                return Boolean.TRUE.equals(indicators.getRsi_15min_gt_56());
            case "rsi_5min_lt_44":
                return Boolean.TRUE.equals(indicators.getRsi_5min_lt_44());
            case "rsi_1min_lt_44":
                return Boolean.TRUE.equals(indicators.getRsi_1min_lt_44());
            case "rsi_15min_lt_44":
                return Boolean.TRUE.equals(indicators.getRsi_15min_lt_44());
            
            // Price conditions
            case "price_gt_vwap_1min":
                return Boolean.TRUE.equals(indicators.getPrice_gt_vwap_1min());
            case "price_gt_vwap_5min":
                return Boolean.TRUE.equals(indicators.getPrice_gt_vwap_5min());
            case "price_gt_vwap_15min":
                return Boolean.TRUE.equals(indicators.getPrice_gt_vwap_15min());
            case "price_lt_vwap_1min":
                return Boolean.TRUE.equals(indicators.getPrice_lt_vwap_1min());
            case "price_lt_vwap_5min":
                return Boolean.TRUE.equals(indicators.getPrice_lt_vwap_5min());
            case "price_lt_vwap_15min":
                return Boolean.TRUE.equals(indicators.getPrice_lt_vwap_15min());
            case "price_above_resistance":
                return Boolean.TRUE.equals(indicators.getPrice_above_resistance());
            case "price_below_support":
                return Boolean.TRUE.equals(indicators.getPrice_below_support());
            
            // Candlestick patterns
            case "green_candle_5min":
                return Boolean.TRUE.equals(indicators.getGreen_candle_5min());
            case "green_candle_1min":
                return Boolean.TRUE.equals(indicators.getGreen_candle_1min());
            case "red_candle_5min":
                return Boolean.TRUE.equals(indicators.getRed_candle_5min());
            case "red_candle_1min":
                return Boolean.TRUE.equals(indicators.getRed_candle_1min());
            case "long_body_5min":
                return Boolean.TRUE.equals(indicators.getLong_body_5min());
            case "long_body_1min":
                return Boolean.TRUE.equals(indicators.getLong_body_1min());
            case "bullish_engulfing_5min":
                return Boolean.TRUE.equals(indicators.getBullish_engulfing_5min());
            case "bullish_engulfing_1min":
                return Boolean.TRUE.equals(indicators.getBullish_engulfing_1min());
            case "bullish_morning_star_5min":
                return Boolean.TRUE.equals(indicators.getBullish_morning_star_5min());
            case "bullish_morning_star_1min":
                return Boolean.TRUE.equals(indicators.getBullish_morning_star_1min());
            case "bearish_engulfing_5min":
                return Boolean.TRUE.equals(indicators.getBearish_engulfing_5min());
            case "bearish_engulfing_1min":
                return Boolean.TRUE.equals(indicators.getBearish_engulfing_1min());
            case "bearish_evening_star_5min":
                return Boolean.TRUE.equals(indicators.getBearish_evening_star_5min());
            case "bearish_evening_star_1min":
                return Boolean.TRUE.equals(indicators.getBearish_evening_star_1min());
            case "hammer_5min":
                return Boolean.TRUE.equals(indicators.getHammer_5min());
            case "hammer_1min":
                return Boolean.TRUE.equals(indicators.getHammer_1min());
            case "shooting_star_5min":
                return Boolean.TRUE.equals(indicators.getShooting_star_5min());
            case "shooting_star_1min":
                return Boolean.TRUE.equals(indicators.getShooting_star_1min());
            
            default:
                return false;
        }
    }
}
