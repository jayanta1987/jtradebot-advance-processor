package com.jtradebot.processor.service.analysis;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
            // Calculate category counts for both CALL and PUT strategies
            Map<String, Integer> callCategoryCounts = calculateCategoryCounts(indicators, configService.getCallCategories());
            Map<String, Integer> putCategoryCounts = calculateCategoryCounts(indicators, configService.getPutCategories());

            // Calculate total scores for each direction
            int callTotalScore = callCategoryCounts.values().stream().mapToInt(Integer::intValue).sum();
            int putTotalScore = putCategoryCounts.values().stream().mapToInt(Integer::intValue).sum();

            // Determine market direction based on total scores
            boolean isCallDirection = callTotalScore >= putTotalScore;
            String marketDirection = isCallDirection ? "CALL" : "PUT";

            log.debug("🔍 MARKET DIRECTION ANALYSIS - CallTotalScore: {}, PutTotalScore: {}, Direction: {}", 
                    callTotalScore, putTotalScore, marketDirection);
            log.debug("🔍 CATEGORY BREAKDOWN - Call: EMA={}, FV={}, CS={}, M={} | Put: EMA={}, FV={}, CS={}, M={}", 
                    callCategoryCounts.getOrDefault("ema", 0), callCategoryCounts.getOrDefault("futureAndVolume", 0),
                    callCategoryCounts.getOrDefault("candlestick", 0), callCategoryCounts.getOrDefault("momentum", 0),
                    putCategoryCounts.getOrDefault("ema", 0), putCategoryCounts.getOrDefault("futureAndVolume", 0),
                    putCategoryCounts.getOrDefault("candlestick", 0), putCategoryCounts.getOrDefault("momentum", 0));

            return marketDirection;

        } catch (Exception e) {
            log.error("Error determining market direction: {}", e.getMessage(), e);
            return "CALL"; // Default to CALL direction on error
        }
    }


    public Map<String, Integer> getCategoryScores(FlattenedIndicators indicators, String direction) {
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


    private Map<String, Integer> calculateCategoryCounts(FlattenedIndicators indicators, Map<String, List<String>> categories) {
        Map<String, Integer> categoryCounts = new HashMap<>();
        
        for (Map.Entry<String, List<String>> entry : categories.entrySet()) {
            String categoryName = entry.getKey();
            List<String> conditions = entry.getValue();
            
            int satisfiedCount = 0;
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

            // Volume conditions
            if (condition.equals("volume_5min_surge")) {
                return Boolean.TRUE.equals(indicators.getVolume_5min_surge());
            }
            if (condition.equals("volume_1min_surge")) {
                return Boolean.TRUE.equals(indicators.getVolume_1min_surge());
            }
            if (condition.equals("volume_15min_surge")) {
                return Boolean.TRUE.equals(indicators.getVolume_15min_surge());
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
            if (condition.equals("bullish_engulfing_3min")) {
                return Boolean.TRUE.equals(indicators.getBullish_engulfing_3min());
            }
            if (condition.equals("bearish_engulfing_5min")) {
                return Boolean.TRUE.equals(indicators.getBearish_engulfing_5min());
            }
            if (condition.equals("bearish_engulfing_1min")) {
                return Boolean.TRUE.equals(indicators.getBearish_engulfing_1min());
            }
            if (condition.equals("bearish_engulfing_3min")) {
                return Boolean.TRUE.equals(indicators.getBearish_engulfing_3min());
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

            // Additional candlestick pattern conditions
            if (condition.equals("bullish_morning_star_5min")) {
                return Boolean.TRUE.equals(indicators.getBullish_morning_star_5min());
            }
            if (condition.equals("bullish_morning_star_1min")) {
                return Boolean.TRUE.equals(indicators.getBullish_morning_star_1min());
            }
            if (condition.equals("bullish_morning_star_3min")) {
                return Boolean.TRUE.equals(indicators.getBullish_morning_star_3min());
            }
            if (condition.equals("bearish_evening_star_5min")) {
                return Boolean.TRUE.equals(indicators.getBearish_evening_star_5min());
            }
            if (condition.equals("bearish_evening_star_1min")) {
                return Boolean.TRUE.equals(indicators.getBearish_evening_star_1min());
            }
            if (condition.equals("bearish_evening_star_3min")) {
                return Boolean.TRUE.equals(indicators.getBearish_evening_star_3min());
            }
            if (condition.equals("hammer_5min")) {
                return Boolean.TRUE.equals(indicators.getHammer_5min());
            }
            if (condition.equals("hammer_1min")) {
                return Boolean.TRUE.equals(indicators.getHammer_1min());
            }
            if (condition.equals("hammer_3min")) {
                return Boolean.TRUE.equals(indicators.getHammer_3min());
            }
            if (condition.equals("shooting_star_5min")) {
                return Boolean.TRUE.equals(indicators.getShooting_star_5min());
            }
            if (condition.equals("shooting_star_1min")) {
                return Boolean.TRUE.equals(indicators.getShooting_star_1min());
            }
            if (condition.equals("shooting_star_3min")) {
                return Boolean.TRUE.equals(indicators.getShooting_star_3min());
            }

            // Candle color conditions
            if (condition.equals("green_candle_5min")) {
                return Boolean.TRUE.equals(indicators.getGreen_candle_5min());
            }
            if (condition.equals("green_candle_1min")) {
                return Boolean.TRUE.equals(indicators.getGreen_candle_1min());
            }
            if (condition.equals("green_candle_3min")) {
                return Boolean.TRUE.equals(indicators.getGreen_candle_3min());
            }
            if (condition.equals("red_candle_5min")) {
                return Boolean.TRUE.equals(indicators.getRed_candle_5min());
            }
            if (condition.equals("red_candle_1min")) {
                return Boolean.TRUE.equals(indicators.getRed_candle_1min());
            }
            if (condition.equals("red_candle_3min")) {
                return Boolean.TRUE.equals(indicators.getRed_candle_3min());
            }

            // Candle body conditions
            if (condition.equals("long_body_5min")) {
                return Boolean.TRUE.equals(indicators.getLong_body_5min());
            }
            if (condition.equals("long_body_1min")) {
                return Boolean.TRUE.equals(indicators.getLong_body_1min());
            }
            if (condition.equals("long_body_3min")) {
                return Boolean.TRUE.equals(indicators.getLong_body_3min());
            }
            if (condition.equals("short_body_5min")) {
                return Boolean.TRUE.equals(indicators.getShort_body_5min());
            }
            if (condition.equals("short_body_1min")) {
                return Boolean.TRUE.equals(indicators.getShort_body_1min());
            }
            if (condition.equals("short_body_3min")) {
                return Boolean.TRUE.equals(indicators.getShort_body_3min());
            }

            // Future signal conditions - check futuresignals object
            if (condition.equals("futuresignals_bullish")) {
                return indicators.getFuturesignals() != null && indicators.getFuturesignals().getAllTimeframesBullish();
            }
            if (condition.equals("futuresignals_bearish")) {
                return indicators.getFuturesignals() != null && indicators.getFuturesignals().getAllTimeframesBearish();
            }

            // Volume surge multiplier conditions
            if (condition.equals("volume_surge_multiplier_gt_2")) {
                return indicators.getVolume_surge_multiplier() != null && indicators.getVolume_surge_multiplier() > 2.0;
            }
            if (condition.equals("volume_surge_multiplier_gt_3")) {
                return indicators.getVolume_surge_multiplier() != null && indicators.getVolume_surge_multiplier() > 3.0;
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
