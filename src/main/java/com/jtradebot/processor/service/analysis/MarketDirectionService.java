package com.jtradebot.processor.service.analysis;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.strategy.DetailedCategoryScore;
import com.jtradebot.processor.model.strategy.ScalpingEntryConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
@Slf4j
public class MarketDirectionService {

    private final DynamicStrategyConfigService configService;


    public String determineMarketDirection(FlattenedIndicators indicators) {
        try {
            // Calculate weighted category scores for both CALL and PUT strategies
            Map<String, Double> callCategoryScores = getWeightedCategoryScores(indicators, "CALL");
            Map<String, Double> putCategoryScores = getWeightedCategoryScores(indicators, "PUT");

            // Calculate total scores for each direction
            double callTotalScore = callCategoryScores.values().stream().mapToDouble(Double::doubleValue).sum();
            double putTotalScore = putCategoryScores.values().stream().mapToDouble(Double::doubleValue).sum();

            // Determine market direction based on total scores
            boolean isCallDirection = callTotalScore >= putTotalScore;
            String marketDirection = isCallDirection ? "CALL" : "PUT";

            log.debug("🔍 MARKET DIRECTION ANALYSIS - CallTotalScore: {}, PutTotalScore: {}, Direction: {}", 
                    callTotalScore, putTotalScore, marketDirection);
            log.debug("🔍 CATEGORY BREAKDOWN - Call: EMA={}, FV={}, CS={}, M={} | Put: EMA={}, FV={}, CS={}, M={}", 
                    callCategoryScores.getOrDefault("ema", 0.0), callCategoryScores.getOrDefault("futureAndVolume", 0.0),
                    callCategoryScores.getOrDefault("candlestick", 0.0), callCategoryScores.getOrDefault("momentum", 0.0),
                    putCategoryScores.getOrDefault("ema", 0.0), putCategoryScores.getOrDefault("futureAndVolume", 0.0),
                    putCategoryScores.getOrDefault("candlestick", 0.0), putCategoryScores.getOrDefault("momentum", 0.0));

            return marketDirection;

        } catch (Exception e) {
            log.error("Error determining market direction: {}", e.getMessage(), e);
            return "CALL"; // Default to CALL direction on error
        }
    }


    public Map<String, Double> getCategoryScores(FlattenedIndicators indicators, String direction) {
        try {
            if ("CALL".equalsIgnoreCase(direction)) {
                return calculateCategoryCounts(indicators, configService.getCallCategories());
            } else if ("PUT".equalsIgnoreCase(direction)) {
                return calculateCategoryCounts(indicators, configService.getPutCategories());
            } else {
                log.warn("Invalid market direction: {}. Returning empty map.", direction);
                return new HashMap<>();
            }
        } catch (Exception e) {
            log.error("Error getting category scores for direction {}: {}", direction, e.getMessage(), e);
            return new HashMap<>();
        }
    }
    
    /**
     * Get weighted category scores using the new scoring structure
     */
    public Map<String, Double> getWeightedCategoryScores(FlattenedIndicators indicators, String direction) {
        try {
            ScalpingEntryConfig config = configService.getScalpingEntryConfig();
            if (config == null || config.getCategoryScoring() == null) {
                log.warn("Category scoring configuration not found, falling back to count-based scoring");
                return getCategoryScores(indicators, direction);
            }
            
            if ("CALL".equalsIgnoreCase(direction)) {
                return calculateWeightedCategoryScores(indicators, config.getCategoryScoring().getCallCategories());
            } else if ("PUT".equalsIgnoreCase(direction)) {
                return calculateWeightedCategoryScores(indicators, config.getCategoryScoring().getPutCategories());
            } else {
                log.warn("Invalid market direction: {}. Returning empty map.", direction);
                return new HashMap<>();
            }
        } catch (Exception e) {
            log.error("Error getting weighted category scores for direction {}: {}", direction, e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * 🔥 NEW: Get detailed category scores with individual indicator breakdowns
     */
    public Map<String, DetailedCategoryScore> getDetailedCategoryScores(FlattenedIndicators indicators, String direction) {
        try {
            ScalpingEntryConfig config = configService.getScalpingEntryConfig();
            if (config == null || config.getCategoryScoring() == null) {
                log.warn("Category scoring configuration not found, cannot provide detailed scores");
                return new HashMap<>();
            }
            
            if ("CALL".equalsIgnoreCase(direction)) {
                return calculateDetailedCategoryScores(indicators, config.getCategoryScoring().getCallCategories());
            } else if ("PUT".equalsIgnoreCase(direction)) {
                return calculateDetailedCategoryScores(indicators, config.getCategoryScoring().getPutCategories());
            } else {
                log.warn("Invalid market direction: {}. Returning empty map.", direction);
                return new HashMap<>();
            }
        } catch (Exception e) {
            log.error("Error getting detailed category scores for direction {}: {}", direction, e.getMessage(), e);
            return new HashMap<>();
        }
    }
    
    private Map<String, Double> calculateCategoryCounts(FlattenedIndicators indicators, Map<String, List<String>> categories) {
        Map<String, Double> categoryCounts = new HashMap<>();
        
        for (Map.Entry<String, List<String>> entry : categories.entrySet()) {
            String categoryName = entry.getKey();
            List<String> conditions = entry.getValue();
            
            double satisfiedCount = 0.0;
            for (String condition : conditions) {
                if (evaluateCondition(condition, indicators)) {
                    satisfiedCount++;
                }
            }
            categoryCounts.put(categoryName, satisfiedCount);
        }
        
        return categoryCounts;
    }
    
    /**
     * Calculate weighted category scores using the new scoring structure
     */
    private Map<String, Double> calculateWeightedCategoryScores(FlattenedIndicators indicators, Map<String, ScalpingEntryConfig.CategoryIndicatorScoring> categories) {
        Map<String, Double> categoryScores = new HashMap<>();
        
        for (Map.Entry<String, ScalpingEntryConfig.CategoryIndicatorScoring> entry : categories.entrySet()) {
            String categoryName = entry.getKey();
            ScalpingEntryConfig.CategoryIndicatorScoring categoryScoring = entry.getValue();
            
            double totalScore = 0.0;
            if (categoryScoring != null && categoryScoring.getIndicators() != null) {
                for (Map.Entry<String, Double> indicatorEntry : categoryScoring.getIndicators().entrySet()) {
                    String indicatorName = indicatorEntry.getKey();
                    Double weightage = indicatorEntry.getValue();
                    
                    if (evaluateCondition(indicatorName, indicators)) {
                        totalScore += weightage != null ? weightage : 1.0;
                    }
                }
            }
            categoryScores.put(categoryName, totalScore);
        }
        
        return categoryScores;
    }

    /**
     * 🔥 NEW: Calculate detailed category scores with individual indicator breakdowns
     */
    private Map<String, DetailedCategoryScore> calculateDetailedCategoryScores(FlattenedIndicators indicators, Map<String, ScalpingEntryConfig.CategoryIndicatorScoring> categories) {
        Map<String, DetailedCategoryScore> detailedScores = new HashMap<>();

        for (Map.Entry<String, ScalpingEntryConfig.CategoryIndicatorScoring> entry : categories.entrySet()) {
            String categoryName = entry.getKey();
            ScalpingEntryConfig.CategoryIndicatorScoring categoryScoring = entry.getValue();

            DetailedCategoryScore detailedScore = new DetailedCategoryScore();
            detailedScore.setCategoryName(categoryName);

            if (categoryScoring != null && categoryScoring.getIndicators() != null) {
                for (Map.Entry<String, Double> indicatorEntry : categoryScoring.getIndicators().entrySet()) {
                    String indicatorName = indicatorEntry.getKey();
                    Double weightage = indicatorEntry.getValue();

                    boolean isSatisfied = evaluateCondition(indicatorName, indicators);
                    detailedScore.addIndicatorScore(indicatorName, weightage, isSatisfied);
                }
            }
            detailedScores.put(categoryName, detailedScore);
        }
        return detailedScores;
    }

    /**
     * Evaluates a single condition against the indicators.
     * 
     * @param condition The condition to evaluate
     * @param indicators The flattened indicators containing all market data
     * @return true if the condition is satisfied, false otherwise
     */
    private boolean evaluateCondition(String condition, FlattenedIndicators indicators) {
        try {
            // EMA conditions - check specific timeframe (EMA5 vs EMA34)
            if (condition.equals("ema5_5min_gt_ema34_5min")) {
                return Boolean.TRUE.equals(indicators.getEma5_5min_gt_ema34_5min());
            }
            if (condition.equals("ema5_1min_gt_ema34_1min")) {
                return Boolean.TRUE.equals(indicators.getEma5_1min_gt_ema34_1min());
            }
            if (condition.equals("ema5_15min_gt_ema34_15min")) {
                return Boolean.TRUE.equals(indicators.getEma5_15min_gt_ema34_15min());
            }
            
            // New EMA price position indicators
            if (condition.equals("price_above_ema5_5min")) {
                return Boolean.TRUE.equals(indicators.getPrice_above_ema5_5min());
            }
            if (condition.equals("price_above_ema5_1min")) {
                return Boolean.TRUE.equals(indicators.getPrice_above_ema5_1min());
            }
            if (condition.equals("price_above_ema34_5min")) {
                return Boolean.TRUE.equals(indicators.getPrice_above_ema34_5min());
            }
            if (condition.equals("price_above_ema34_1min")) {
                return Boolean.TRUE.equals(indicators.getPrice_above_ema34_1min());
            }
            
            // New EMA price position indicators for PUT strategy
            if (condition.equals("price_below_ema5_5min")) {
                return Boolean.TRUE.equals(indicators.getPrice_below_ema5_5min());
            }
            if (condition.equals("price_below_ema5_1min")) {
                return Boolean.TRUE.equals(indicators.getPrice_below_ema5_1min());
            }
            if (condition.equals("price_below_ema34_5min")) {
                return Boolean.TRUE.equals(indicators.getPrice_below_ema34_5min());
            }
            if (condition.equals("price_below_ema34_1min")) {
                return Boolean.TRUE.equals(indicators.getPrice_below_ema34_1min());
            }
            
            // New EMA crossover indicators
            if (condition.equals("ema_crossover_bullish_5min")) {
                return Boolean.TRUE.equals(indicators.getEma_crossover_bullish_5min());
            }
            if (condition.equals("ema_crossover_bullish_1min")) {
                return Boolean.TRUE.equals(indicators.getEma_crossover_bullish_1min());
            }
            if (condition.equals("ema_crossover_bearish_5min")) {
                return Boolean.TRUE.equals(indicators.getEma_crossover_bearish_5min());
            }
            if (condition.equals("ema_crossover_bearish_1min")) {
                return Boolean.TRUE.equals(indicators.getEma_crossover_bearish_1min());
            }
            if (condition.equals("ema5_5min_lt_ema34_5min")) {
                return Boolean.TRUE.equals(indicators.getEma5_5min_lt_ema34_5min());
            }
            if (condition.equals("ema5_1min_lt_ema34_1min")) {
                return Boolean.TRUE.equals(indicators.getEma5_1min_lt_ema34_1min());
            }
            if (condition.equals("ema5_15min_lt_ema34_15min")) {
                return Boolean.TRUE.equals(indicators.getEma5_15min_lt_ema34_15min());
            }

            // RSI conditions
            if (condition.equals("rsi_5min_gt_60")) {
                return Boolean.TRUE.equals(indicators.getRsi_5min_gt_60());
            }
            if (condition.equals("rsi_1min_gt_60")) {
                return Boolean.TRUE.equals(indicators.getRsi_1min_gt_60());
            }
            if (condition.equals("rsi_15min_gt_60")) {
                return Boolean.TRUE.equals(indicators.getRsi_15min_gt_60());
            }
            if (condition.equals("rsi_5min_lt_40")) {
                return Boolean.TRUE.equals(indicators.getRsi_5min_lt_40());
            }
            if (condition.equals("rsi_1min_lt_40")) {
                return Boolean.TRUE.equals(indicators.getRsi_1min_lt_40());
            }
            if (condition.equals("rsi_15min_lt_40")) {
                return Boolean.TRUE.equals(indicators.getRsi_15min_lt_40());
            }

            // MACD conditions
            if (condition.equals("macd_bullish_crossover_5min")) {
                return Boolean.TRUE.equals(indicators.getMacd_bullish_crossover_5min());
            }
            if (condition.equals("macd_bullish_crossover_1min")) {
                return Boolean.TRUE.equals(indicators.getMacd_bullish_crossover_1min());
            }
            if (condition.equals("macd_bullish_crossover_15min")) {
                return Boolean.TRUE.equals(indicators.getMacd_bullish_crossover_15min());
            }
            if (condition.equals("macd_above_zero_5min")) {
                return Boolean.TRUE.equals(indicators.getMacd_above_zero_5min());
            }
            if (condition.equals("macd_above_zero_1min")) {
                return Boolean.TRUE.equals(indicators.getMacd_above_zero_1min());
            }
            if (condition.equals("macd_above_zero_15min")) {
                return Boolean.TRUE.equals(indicators.getMacd_above_zero_15min());
            }
            if (condition.equals("macd_bearish_crossover_5min")) {
                return Boolean.TRUE.equals(indicators.getMacd_bearish_crossover_5min());
            }
            if (condition.equals("macd_bearish_crossover_1min")) {
                return Boolean.TRUE.equals(indicators.getMacd_bearish_crossover_1min());
            }
            if (condition.equals("macd_bearish_crossover_15min")) {
                return Boolean.TRUE.equals(indicators.getMacd_bearish_crossover_15min());
            }
            if (condition.equals("macd_below_zero_5min")) {
                return Boolean.TRUE.equals(indicators.getMacd_below_zero_5min());
            }
            if (condition.equals("macd_below_zero_1min")) {
                return Boolean.TRUE.equals(indicators.getMacd_below_zero_1min());
            }
            if (condition.equals("macd_below_zero_15min")) {
                return Boolean.TRUE.equals(indicators.getMacd_below_zero_15min());
            }

            // Volume conditions (keeping for backward compatibility but not used in JSON)
            if (condition.equals("volume_5min_surge")) {
                return Boolean.TRUE.equals(indicators.getVolume_5min_surge());
            }
            if (condition.equals("volume_1min_surge")) {
                return Boolean.TRUE.equals(indicators.getVolume_1min_surge());
            }
            if (condition.equals("volume_15min_surge")) {
                return Boolean.TRUE.equals(indicators.getVolume_15min_surge());
            }

            // Combined Price-Volume Directional Indicators
            if (condition.equals("price_volume_bullish_surge_5min")) {
                return Boolean.TRUE.equals(indicators.getPrice_volume_bullish_surge_5min());
            }
            if (condition.equals("price_volume_bullish_surge_1min")) {
                return Boolean.TRUE.equals(indicators.getPrice_volume_bullish_surge_1min());
            }
            if (condition.equals("price_volume_bullish_surge_15min")) {
                return Boolean.TRUE.equals(indicators.getPrice_volume_bullish_surge_15min());
            }
            if (condition.equals("price_volume_bearish_surge_5min")) {
                return Boolean.TRUE.equals(indicators.getPrice_volume_bearish_surge_5min());
            }
            if (condition.equals("price_volume_bearish_surge_1min")) {
                return Boolean.TRUE.equals(indicators.getPrice_volume_bearish_surge_1min());
            }
            if (condition.equals("price_volume_bearish_surge_15min")) {
                return Boolean.TRUE.equals(indicators.getPrice_volume_bearish_surge_15min());
            }

            // Price action conditions
            if (condition.equals("price_gt_vwap_5min")) {
                return Boolean.TRUE.equals(indicators.getPrice_gt_vwap_5min());
            }
            if (condition.equals("price_gt_vwap_1min")) {
                return Boolean.TRUE.equals(indicators.getPrice_gt_vwap_1min());
            }
            if (condition.equals("price_gt_vwap_15min")) {
                return Boolean.TRUE.equals(indicators.getPrice_gt_vwap_15min());
            }
            if (condition.equals("price_lt_vwap_5min")) {
                return Boolean.TRUE.equals(indicators.getPrice_lt_vwap_5min());
            }
            if (condition.equals("price_lt_vwap_1min")) {
                return Boolean.TRUE.equals(indicators.getPrice_lt_vwap_1min());
            }
            if (condition.equals("price_lt_vwap_15min")) {
                return Boolean.TRUE.equals(indicators.getPrice_lt_vwap_15min());
            }

            // Resistance/Support conditions
            if (condition.equals("price_above_resistance")) {
                return Boolean.TRUE.equals(indicators.getPrice_above_resistance());
            }
            if (condition.equals("price_below_support")) {
                return Boolean.TRUE.equals(indicators.getPrice_below_support());
            }

            // Candlestick pattern conditions
            if (condition.equals("bullish_engulfing_5min")) {
                return Boolean.TRUE.equals(indicators.getBullish_engulfing_5min());
            }
            if (condition.equals("bullish_engulfing_1min")) {
                return Boolean.TRUE.equals(indicators.getBullish_engulfing_1min());
            }

            if (condition.equals("bearish_engulfing_5min")) {
                return Boolean.TRUE.equals(indicators.getBearish_engulfing_5min());
            }
            if (condition.equals("bearish_engulfing_1min")) {
                return Boolean.TRUE.equals(indicators.getBearish_engulfing_1min());
            }


            // RSI MA comparison conditions
            if (condition.equals("rsi_5min_gt_rsi_ma")) {
                return Boolean.TRUE.equals(indicators.getRsi_5min_gt_rsi_ma());
            }
            if (condition.equals("rsi_1min_gt_rsi_ma")) {
                return Boolean.TRUE.equals(indicators.getRsi_1min_gt_rsi_ma());
            }
            if (condition.equals("rsi_15min_gt_rsi_ma")) {
                return Boolean.TRUE.equals(indicators.getRsi_15min_gt_rsi_ma());
            }
            if (condition.equals("rsi_5min_lt_rsi_ma")) {
                return Boolean.TRUE.equals(indicators.getRsi_5min_lt_rsi_ma());
            }
            if (condition.equals("rsi_1min_lt_rsi_ma")) {
                return Boolean.TRUE.equals(indicators.getRsi_1min_lt_rsi_ma());
            }
            if (condition.equals("rsi_15min_lt_rsi_ma")) {
                return Boolean.TRUE.equals(indicators.getRsi_15min_lt_rsi_ma());
            }
            
            // RSI Divergence conditions
            if (condition.equals("rsi_bullish_divergence_5min")) {
                return Boolean.TRUE.equals(indicators.getRsi_bullish_divergence_5min());
            }
            if (condition.equals("rsi_bullish_divergence_1min")) {
                return Boolean.TRUE.equals(indicators.getRsi_bullish_divergence_1min());
            }
            if (condition.equals("rsi_bullish_divergence_15min")) {
                return Boolean.TRUE.equals(indicators.getRsi_bullish_divergence_15min());
            }
            if (condition.equals("rsi_bearish_divergence_5min")) {
                return Boolean.TRUE.equals(indicators.getRsi_bearish_divergence_5min());
            }
            if (condition.equals("rsi_bearish_divergence_1min")) {
                return Boolean.TRUE.equals(indicators.getRsi_bearish_divergence_1min());
            }
            if (condition.equals("rsi_bearish_divergence_15min")) {
                return Boolean.TRUE.equals(indicators.getRsi_bearish_divergence_15min());
            }

            // Additional candlestick pattern conditions
            if (condition.equals("bullish_morning_star_5min")) {
                return Boolean.TRUE.equals(indicators.getBullish_morning_star_5min());
            }
            if (condition.equals("bullish_morning_star_1min")) {
                return Boolean.TRUE.equals(indicators.getBullish_morning_star_1min());
            }

            if (condition.equals("bearish_evening_star_5min")) {
                return Boolean.TRUE.equals(indicators.getBearish_evening_star_5min());
            }
            if (condition.equals("bearish_evening_star_1min")) {
                return Boolean.TRUE.equals(indicators.getBearish_evening_star_1min());
            }

            if (condition.equals("hammer_5min")) {
                return Boolean.TRUE.equals(indicators.getHammer_5min());
            }
            if (condition.equals("hammer_1min")) {
                return Boolean.TRUE.equals(indicators.getHammer_1min());
            }

            if (condition.equals("shooting_star_5min")) {
                return Boolean.TRUE.equals(indicators.getShooting_star_5min());
            }
            if (condition.equals("shooting_star_1min")) {
                return Boolean.TRUE.equals(indicators.getShooting_star_1min());
            }

            // Additional candlestick patterns
            if (condition.equals("inside_bar_breakout_5min")) {
                return Boolean.TRUE.equals(indicators.getInside_bar_breakout_5min());
            }
            if (condition.equals("inside_bar_breakdown_5min")) {
                return Boolean.TRUE.equals(indicators.getInside_bar_breakdown_5min());
            }
            
            // Bearish wick rejection filters with explicit naming
            if (condition.equals("wick_rejection_filter_bearish_1min")) {
                return Boolean.TRUE.equals(indicators.getWick_rejection_filter_bearish_1min());
            }
            if (condition.equals("wick_rejection_filter_bearish_5min")) {
                return Boolean.TRUE.equals(indicators.getWick_rejection_filter_bearish_5min());
            }
            
            // Bullish wick rejection filters
            if (condition.equals("wick_rejection_filter_bullish_1min")) {
                return Boolean.TRUE.equals(indicators.getWick_rejection_filter_bullish_1min());
            }
            if (condition.equals("wick_rejection_filter_bullish_5min")) {
                return Boolean.TRUE.equals(indicators.getWick_rejection_filter_bullish_5min());
            }

            // Marubozu conditions
            if (condition.equals("marubozu_5min")) {
                return Boolean.TRUE.equals(indicators.getMarubozu_5min());
            }
            if (condition.equals("marubozu_1min")) {
                return Boolean.TRUE.equals(indicators.getMarubozu_1min());
            }
            
            // Directional Marubozu conditions
            if (condition.equals("bullish_marubozu_5min")) {
                return Boolean.TRUE.equals(indicators.getBullish_marubozu_5min());
            }
            if (condition.equals("bullish_marubozu_1min")) {
                return Boolean.TRUE.equals(indicators.getBullish_marubozu_1min());
            }
            if (condition.equals("bearish_marubozu_5min")) {
                return Boolean.TRUE.equals(indicators.getBearish_marubozu_5min());
            }
            if (condition.equals("bearish_marubozu_1min")) {
                return Boolean.TRUE.equals(indicators.getBearish_marubozu_1min());
            }

            // Candle color conditions
            if (condition.equals("green_candle_5min")) {
                return Boolean.TRUE.equals(indicators.getGreen_candle_5min());
            }
            if (condition.equals("green_candle_1min")) {
                return Boolean.TRUE.equals(indicators.getGreen_candle_1min());
            }
            if (condition.equals("red_candle_5min")) {
                return Boolean.TRUE.equals(indicators.getRed_candle_5min());
            }
            if (condition.equals("red_candle_1min")) {
                return Boolean.TRUE.equals(indicators.getRed_candle_1min());
            }

            // Candle body conditions
            if (condition.equals("long_body_5min")) {
                return Boolean.TRUE.equals(indicators.getLong_body_5min());
            }
            if (condition.equals("long_body_1min")) {
                return Boolean.TRUE.equals(indicators.getLong_body_1min());
            }
            
            // Directional Long Body conditions
            if (condition.equals("bullish_long_body_5min")) {
                return Boolean.TRUE.equals(indicators.getBullish_long_body_5min());
            }
            if (condition.equals("bullish_long_body_1min")) {
                return Boolean.TRUE.equals(indicators.getBullish_long_body_1min());
            }
            if (condition.equals("bearish_long_body_5min")) {
                return Boolean.TRUE.equals(indicators.getBearish_long_body_5min());
            }
            if (condition.equals("bearish_long_body_1min")) {
                return Boolean.TRUE.equals(indicators.getBearish_long_body_1min());
            }

            // OI (Open Interest) conditions
            if (condition.equals("oi_bullish_signal_5min")) {
                return Boolean.TRUE.equals(indicators.getOi_bullish_signal_5min());
            }
            if (condition.equals("oi_bullish_signal_1min")) {
                return Boolean.TRUE.equals(indicators.getOi_bullish_signal_1min());
            }
            if (condition.equals("oi_bullish_signal_15min")) {
                return Boolean.TRUE.equals(indicators.getOi_bullish_signal_15min());
            }
            if (condition.equals("oi_bearish_signal_5min")) {
                return Boolean.TRUE.equals(indicators.getOi_bearish_signal_5min());
            }
            if (condition.equals("oi_bearish_signal_1min")) {
                return Boolean.TRUE.equals(indicators.getOi_bearish_signal_1min());
            }
            if (condition.equals("oi_bearish_signal_15min")) {
                return Boolean.TRUE.equals(indicators.getOi_bearish_signal_15min());
            }

            // Current candle crossed previous high/low patterns
            if (condition.equals("current_candle_crossed_above_prev_high_5min")) {
                return Boolean.TRUE.equals(indicators.getCurrent_candle_crossed_above_prev_high_5min());
            }
            if (condition.equals("current_candle_crossed_below_prev_low_5min")) {
                return Boolean.TRUE.equals(indicators.getCurrent_candle_crossed_below_prev_low_5min());
            }

            // Default case - condition not recognized
            log.warn("Unknown condition: {}", condition);
            return false;

        } catch (Exception e) {
            log.error("Error evaluating condition {}: {}", condition, e.getMessage(), e);
            return false;
        }
    }
}
