package com.jtradebot.processor.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.model.indicator.EntryQuality;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.strategy.ProfitableTradeFilterConfig;
import com.jtradebot.processor.model.strategy.ProfitableTradeFilterResult;
import com.jtradebot.processor.service.ProfitableTradeFilterService;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfitableTradeFilterServiceImpl implements ProfitableTradeFilterService {
    
    private final DynamicStrategyConfigService configService;
    private final ObjectMapper objectMapper;
    
    // Cache for filter configuration
    private ProfitableTradeFilterConfig filterConfig;
    private final Map<String, Long> lastFilterLogTime = new ConcurrentHashMap<>();
    private static final long FILTER_LOG_COOLDOWN_MS = 5000; // 5 seconds between filter logs
    
    @Override
    public ProfitableTradeFilterResult evaluateCallEntry(FlattenedIndicators indicators, EntryQuality entryQuality, Tick tick) {
        ProfitableTradeFilterConfig config = getFilterConfig();
        
        if (!config.getEnabled() || !config.getCallStrategy().getEnabled()) {
            return ProfitableTradeFilterResult.builder()
                    .isProfitableEntry(true)
                    .strategyType("CALL")
                    .build();
        }
        
        ProfitableTradeFilterResult.ProfitableTradeFilterResultBuilder resultBuilder = ProfitableTradeFilterResult.builder()
                .strategyType("CALL")
                .qualityScore(entryQuality.getQualityScore())
                .candlestickScore(entryQuality.getCandlestickScore())
                .volumeSurgeMultiplier(indicators.getVolume_surge_multiplier());
        
        // Check quality score
        boolean qualityScorePassed = entryQuality.getQualityScore() >= config.getCallStrategy().getMinQualityScore();
        resultBuilder.qualityScorePassed(qualityScorePassed);
        
        if (!qualityScorePassed) {
            return resultBuilder
                    .isProfitableEntry(false)
                    .rejectionReason("Quality score " + entryQuality.getQualityScore() + " < " + config.getCallStrategy().getMinQualityScore())
                    .build();
        }
        
        // Check candlestick score
        boolean candlestickScorePassed = entryQuality.getCandlestickScore() >= config.getCallStrategy().getMinCandlestickScore();
        resultBuilder.candlestickScorePassed(candlestickScorePassed);
        
        if (!candlestickScorePassed) {
            return resultBuilder
                    .isProfitableEntry(false)
                    .rejectionReason("Candlestick score " + entryQuality.getCandlestickScore() + " < " + config.getCallStrategy().getMinCandlestickScore())
                    .build();
        }
        
        // Check volume surge multiplier
        boolean volumeSurgeMultiplierPassed = false;
        if (indicators.getVolume_surge_multiplier() != null) {
            double multiplier = indicators.getVolume_surge_multiplier();
            volumeSurgeMultiplierPassed = multiplier >= config.getCallStrategy().getVolumeSurgeMultiplier().getMin() &&
                                         multiplier <= config.getCallStrategy().getVolumeSurgeMultiplier().getMax();
        }
        resultBuilder.volumeSurgeMultiplierPassed(volumeSurgeMultiplierPassed);
        
        if (!volumeSurgeMultiplierPassed) {
            return resultBuilder
                    .isProfitableEntry(false)
                    .rejectionReason("Volume surge multiplier " + indicators.getVolume_surge_multiplier() + " not in range [" +
                            config.getCallStrategy().getVolumeSurgeMultiplier().getMin() + ", " +
                            config.getCallStrategy().getVolumeSurgeMultiplier().getMax() + "]")
                    .build();
        }
        
        // Evaluate each category
        ProfitableTradeFilterConfig.Categories categories = config.getCallStrategy().getCategories();
        
        // EMA Category
        ProfitableTradeFilterResult.CategoryResult emaCategory = evaluateCategory(indicators, categories.getEma(), "EMA");
        resultBuilder.emaCategory(emaCategory);
        resultBuilder.emaCategoryPassed(emaCategory.getPassed());
        
        // Future and Volume Category
        ProfitableTradeFilterResult.CategoryResult futureAndVolumeCategory = evaluateCategory(indicators, categories.getFutureAndVolume(), "FutureAndVolume");
        resultBuilder.futureAndVolumeCategory(futureAndVolumeCategory);
        resultBuilder.futureAndVolumeCategoryPassed(futureAndVolumeCategory.getPassed());
        
        // Candlestick Category
        ProfitableTradeFilterResult.CategoryResult candlestickCategory = evaluateCategory(indicators, categories.getCandlestick(), "Candlestick");
        resultBuilder.candlestickCategory(candlestickCategory);
        resultBuilder.candlestickCategoryPassed(candlestickCategory.getPassed());
        
        // Momentum Category
        ProfitableTradeFilterResult.CategoryResult momentumCategory = evaluateCategory(indicators, categories.getMomentum(), "Momentum");
        resultBuilder.momentumCategory(momentumCategory);
        resultBuilder.momentumCategoryPassed(momentumCategory.getPassed());
        
        // Check if all categories passed
        boolean allCategoriesPassed = emaCategory.getPassed() && futureAndVolumeCategory.getPassed() && 
                                    candlestickCategory.getPassed() && momentumCategory.getPassed();
        
        if (!allCategoriesPassed) {
            // Build rejection reason from failed categories
            List<String> failedCategories = new ArrayList<>();
            if (!emaCategory.getPassed()) failedCategories.add("EMA(" + emaCategory.getPassedCount() + "/" + emaCategory.getRequiredCount() + ")");
            if (!futureAndVolumeCategory.getPassed()) failedCategories.add("FutureAndVolume(" + futureAndVolumeCategory.getPassedCount() + "/" + futureAndVolumeCategory.getRequiredCount() + ")");
            if (!candlestickCategory.getPassed()) failedCategories.add("Candlestick(" + candlestickCategory.getPassedCount() + "/" + candlestickCategory.getRequiredCount() + ")");
            if (!momentumCategory.getPassed()) failedCategories.add("Momentum(" + momentumCategory.getPassedCount() + "/" + momentumCategory.getRequiredCount() + ")");
            
            return resultBuilder
                    .isProfitableEntry(false)
                    .rejectionReason("Categories failed: " + String.join(", ", failedCategories))
                    .build();
        }
        
        // All checks passed
        ProfitableTradeFilterResult result = resultBuilder.isProfitableEntry(true).build();
        
        // Log the successful filter pass
        if (config.getLogging().getEnabled() && config.getLogging().getLogFilteredEntries()) {
            logFilterResult(tick, result, true);
        }
        
        return result;
    }
    
    @Override
    public ProfitableTradeFilterResult evaluatePutEntry(FlattenedIndicators indicators, EntryQuality entryQuality, Tick tick) {
        ProfitableTradeFilterConfig config = getFilterConfig();
        
        if (!config.getEnabled() || !config.getPutStrategy().getEnabled()) {
            return ProfitableTradeFilterResult.builder()
                    .isProfitableEntry(true)
                    .strategyType("PUT")
                    .build();
        }
        
        ProfitableTradeFilterResult.ProfitableTradeFilterResultBuilder resultBuilder = ProfitableTradeFilterResult.builder()
                .strategyType("PUT")
                .qualityScore(entryQuality.getQualityScore())
                .candlestickScore(entryQuality.getCandlestickScore())
                .volumeSurgeMultiplier(indicators.getVolume_surge_multiplier());
        
        // Check quality score
        boolean qualityScorePassed = entryQuality.getQualityScore() >= config.getPutStrategy().getMinQualityScore();
        resultBuilder.qualityScorePassed(qualityScorePassed);
        
        if (!qualityScorePassed) {
            return resultBuilder
                    .isProfitableEntry(false)
                    .rejectionReason("Quality score " + entryQuality.getQualityScore() + " < " + config.getPutStrategy().getMinQualityScore())
                    .build();
        }
        
        // Check candlestick score
        boolean candlestickScorePassed = entryQuality.getCandlestickScore() >= config.getPutStrategy().getMinCandlestickScore();
        resultBuilder.candlestickScorePassed(candlestickScorePassed);
        
        if (!candlestickScorePassed) {
            return resultBuilder
                    .isProfitableEntry(false)
                    .rejectionReason("Candlestick score " + entryQuality.getCandlestickScore() + " < " + config.getPutStrategy().getMinCandlestickScore())
                    .build();
        }
        
        // Check volume surge multiplier
        boolean volumeSurgeMultiplierPassed = false;
        if (indicators.getVolume_surge_multiplier() != null) {
            double multiplier = indicators.getVolume_surge_multiplier();
            volumeSurgeMultiplierPassed = multiplier >= config.getPutStrategy().getVolumeSurgeMultiplier().getMin() &&
                                         multiplier <= config.getPutStrategy().getVolumeSurgeMultiplier().getMax();
        }
        resultBuilder.volumeSurgeMultiplierPassed(volumeSurgeMultiplierPassed);
        
        if (!volumeSurgeMultiplierPassed) {
            return resultBuilder
                    .isProfitableEntry(false)
                    .rejectionReason("Volume surge multiplier " + indicators.getVolume_surge_multiplier() + " not in range [" +
                            config.getPutStrategy().getVolumeSurgeMultiplier().getMin() + ", " +
                            config.getPutStrategy().getVolumeSurgeMultiplier().getMax() + "]")
                    .build();
        }
        
        // Evaluate each category
        ProfitableTradeFilterConfig.Categories categories = config.getPutStrategy().getCategories();
        
        // EMA Category
        ProfitableTradeFilterResult.CategoryResult emaCategory = evaluateCategory(indicators, categories.getEma(), "EMA");
        resultBuilder.emaCategory(emaCategory);
        resultBuilder.emaCategoryPassed(emaCategory.getPassed());
        
        // Future and Volume Category
        ProfitableTradeFilterResult.CategoryResult futureAndVolumeCategory = evaluateCategory(indicators, categories.getFutureAndVolume(), "FutureAndVolume");
        resultBuilder.futureAndVolumeCategory(futureAndVolumeCategory);
        resultBuilder.futureAndVolumeCategoryPassed(futureAndVolumeCategory.getPassed());
        
        // Candlestick Category
        ProfitableTradeFilterResult.CategoryResult candlestickCategory = evaluateCategory(indicators, categories.getCandlestick(), "Candlestick");
        resultBuilder.candlestickCategory(candlestickCategory);
        resultBuilder.candlestickCategoryPassed(candlestickCategory.getPassed());
        
        // Momentum Category
        ProfitableTradeFilterResult.CategoryResult momentumCategory = evaluateCategory(indicators, categories.getMomentum(), "Momentum");
        resultBuilder.momentumCategory(momentumCategory);
        resultBuilder.momentumCategoryPassed(momentumCategory.getPassed());
        
        // Check if all categories passed
        boolean allCategoriesPassed = emaCategory.getPassed() && futureAndVolumeCategory.getPassed() && 
                                    candlestickCategory.getPassed() && momentumCategory.getPassed();
        
        if (!allCategoriesPassed) {
            // Build rejection reason from failed categories
            List<String> failedCategories = new ArrayList<>();
            if (!emaCategory.getPassed()) failedCategories.add("EMA(" + emaCategory.getPassedCount() + "/" + emaCategory.getRequiredCount() + ")");
            if (!futureAndVolumeCategory.getPassed()) failedCategories.add("FutureAndVolume(" + futureAndVolumeCategory.getPassedCount() + "/" + futureAndVolumeCategory.getRequiredCount() + ")");
            if (!candlestickCategory.getPassed()) failedCategories.add("Candlestick(" + candlestickCategory.getPassedCount() + "/" + candlestickCategory.getRequiredCount() + ")");
            if (!momentumCategory.getPassed()) failedCategories.add("Momentum(" + momentumCategory.getPassedCount() + "/" + momentumCategory.getRequiredCount() + ")");
            
            return resultBuilder
                    .isProfitableEntry(false)
                    .rejectionReason("Categories failed: " + String.join(", ", failedCategories))
                    .build();
        }
        
        // All checks passed
        ProfitableTradeFilterResult result = resultBuilder.isProfitableEntry(true).build();
        
        // Log the successful filter pass
        if (config.getLogging().getEnabled() && config.getLogging().getLogFilteredEntries()) {
            logFilterResult(tick, result, true);
        }
        
        return result;
    }
    
    @Override
    public boolean isFilterEnabled() {
        ProfitableTradeFilterConfig config = getFilterConfig();
        return config.getEnabled();
    }
    
    @Override
    public ProfitableTradeFilterConfig getFilterConfig() {
        if (filterConfig == null) {
            try {
                filterConfig = configService.getProfitableTradeFilterConfig();
            } catch (Exception e) {
                log.error("Failed to load profitable trade filter config, using default", e);
                // Return default config that disables filtering
                filterConfig = ProfitableTradeFilterConfig.builder()
                        .enabled(false)
                        .description("Default config - filtering disabled")
                        .build();
            }
        }
        return filterConfig;
    }
    
    /**
     * Evaluate a category and return the result
     */
    private ProfitableTradeFilterResult.CategoryResult evaluateCategory(FlattenedIndicators indicators, ProfitableTradeFilterConfig.Category category, String categoryName) {
        List<String> passedConditions = new ArrayList<>();
        List<String> failedConditions = new ArrayList<>();
        
        for (String condition : category.getConditions()) {
            Boolean conditionValue = getConditionValue(indicators, condition);
            if (Boolean.TRUE.equals(conditionValue)) {
                passedConditions.add(condition);
            } else {
                failedConditions.add(condition);
            }
        }
        
        int passedCount = passedConditions.size();
        int requiredCount = category.getMinCount();
        boolean passed = passedCount >= requiredCount;
        
        String failureReason = passed ? null : 
            String.format("%s category: %d/%d conditions met (need %d)", categoryName, passedCount, category.getConditions().size(), requiredCount);
        
        return ProfitableTradeFilterResult.CategoryResult.builder()
                .categoryName(categoryName)
                .passed(passed)
                .passedCount(passedCount)
                .requiredCount(requiredCount)
                .passedConditions(passedConditions)
                .failedConditions(failedConditions)
                .failureReason(failureReason)
                .build();
    }
    
    /**
     * Get the boolean value of a condition from FlattenedIndicators
     */
    private Boolean getConditionValue(FlattenedIndicators indicators, String condition) {
        switch (condition) {
            // EMA conditions
            case "ema9_5min_gt_ema21_5min":
                return indicators.getEma9_5min_gt_ema21_5min();
            case "ema9_1min_gt_ema21_1min":
                return indicators.getEma9_1min_gt_ema21_1min();
            case "ema9_15min_gt_ema21_15min":
                return indicators.getEma9_15min_gt_ema21_15min();
            case "ema9_5min_lt_ema21_5min":
                return indicators.getEma9_5min_gt_ema21_5min() != null ? !indicators.getEma9_5min_gt_ema21_5min() : null;
            case "ema9_1min_lt_ema21_1min":
                return indicators.getEma9_1min_gt_ema21_1min() != null ? !indicators.getEma9_1min_gt_ema21_1min() : null;
            case "ema9_15min_lt_ema21_15min":
                return indicators.getEma9_15min_gt_ema21_15min() != null ? !indicators.getEma9_15min_gt_ema21_15min() : null;
            
            // Volume conditions
            case "volume_5min_surge":
                return indicators.getVolume_5min_surge();
            case "volume_1min_surge":
                return indicators.getVolume_1min_surge();
            case "volume_15min_surge":
                return indicators.getVolume_15min_surge();
            
            // RSI conditions
            case "rsi_5min_gt_56":
                return indicators.getRsi_5min_gt_56();
            case "rsi_1min_gt_56":
                return indicators.getRsi_1min_gt_56();
            case "rsi_15min_gt_56":
                return indicators.getRsi_15min_gt_56();
            case "rsi_5min_lt_44":
                return indicators.getRsi_5min_lt_44();
            case "rsi_1min_lt_44":
                return indicators.getRsi_1min_lt_44();
            case "rsi_15min_lt_44":
                return indicators.getRsi_15min_lt_44();
            
            // Price conditions
            case "price_gt_vwap_1min":
                return indicators.getPrice_gt_vwap_1min();
            case "price_gt_vwap_5min":
                return indicators.getPrice_gt_vwap_5min();
            case "price_gt_vwap_15min":
                return indicators.getPrice_gt_vwap_15min();
            case "price_lt_vwap_1min":
                return indicators.getPrice_lt_vwap_1min();
            case "price_lt_vwap_5min":
                return indicators.getPrice_lt_vwap_5min();
            case "price_lt_vwap_15min":
                return indicators.getPrice_lt_vwap_15min();
            case "price_above_resistance":
                return indicators.getPrice_above_resistance();
            case "price_below_support":
                return indicators.getPrice_below_support();
            
            // Candlestick patterns
            case "bullish_engulfing_5min":
                return indicators.getBullish_engulfing_5min();
            case "bullish_engulfing_1min":
                return indicators.getBullish_engulfing_1min();
            case "bullish_morning_star_5min":
                return indicators.getBullish_morning_star_5min();
            case "bullish_morning_star_1min":
                return indicators.getBullish_morning_star_1min();
            case "bearish_engulfing_5min":
                return indicators.getBearish_engulfing_5min();
            case "bearish_engulfing_1min":
                return indicators.getBearish_engulfing_1min();
            case "bearish_evening_star_5min":
                return indicators.getBearish_evening_star_5min();
            case "bearish_evening_star_1min":
                return indicators.getBearish_evening_star_1min();
            
            // Additional candlestick patterns
            case "hammer_5min":
                return indicators.getHammer_5min();
            case "hammer_1min":
                return indicators.getHammer_1min();
            case "inverted_hammer_5min":
                return indicators.getInverted_hammer_5min();
            case "inverted_hammer_1min":
                return indicators.getInverted_hammer_1min();
            case "shooting_star_5min":
                return indicators.getShooting_star_5min();
            case "shooting_star_1min":
                return indicators.getShooting_star_1min();
            case "hanging_man_5min":
                return indicators.getHanging_man_5min();
            case "hanging_man_1min":
                return indicators.getHanging_man_1min();
            
            // Candle body and color patterns
            case "long_body_5min":
                return indicators.getLong_body_5min();
            case "long_body_1min":
                return indicators.getLong_body_1min();
            case "green_candle_5min":
                return indicators.getGreen_candle_5min();
            case "green_candle_1min":
                return indicators.getGreen_candle_1min();
            case "red_candle_5min":
                return indicators.getRed_candle_5min();
            case "red_candle_1min":
                return indicators.getRed_candle_1min();
            
            default:
                log.warn("Unknown condition: {}", condition);
                return null;
        }
    }
    
    /**
     * Log filter result with cooldown to prevent spam
     */
    private void logFilterResult(Tick tick, ProfitableTradeFilterResult result, boolean isAccepted) {
        String instrumentToken = String.valueOf(tick.getInstrumentToken());
        long currentTime = System.currentTimeMillis();
        Long lastLogTime = lastFilterLogTime.get(instrumentToken);
        
        if (lastLogTime == null || (currentTime - lastLogTime) > FILTER_LOG_COOLDOWN_MS) {
            if (isAccepted) {
                log.info("🎯 PROFITABLE TRADE FILTER - ACCEPTED | {} | Price: {} | {}", 
                        result.getStrategyType(), tick.getLastTradedPrice(), result.getSummary());
            } else if (getFilterConfig().getLogging().getLogRejectionReasons()) {
                log.debug("🚫 PROFITABLE TRADE FILTER - REJECTED | {} | Price: {} | Reason: {}", 
                        result.getStrategyType(), tick.getLastTradedPrice(), result.getRejectionReason());
            }
            lastFilterLogTime.put(instrumentToken, currentTime);
        }
    }
}
