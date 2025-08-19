package com.jtradebot.processor.service.analysis;

import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.model.strategy.ScalpingEntryConfig;
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
        }
        return candlestickCount;
    }
    
    /**
     * Get Momentum count for the given strategy
     */
    public int getMomentumCount(FlattenedIndicators indicators, boolean isCall) {
        int momentumCount = 0;
        if (isCall) {
            if (Boolean.TRUE.equals(indicators.getRsi_5min_gt_56())) momentumCount++;
            if (Boolean.TRUE.equals(indicators.getRsi_1min_gt_56())) momentumCount++;
            if (Boolean.TRUE.equals(indicators.getRsi_15min_gt_56())) momentumCount++;
        } else {
            if (Boolean.TRUE.equals(indicators.getRsi_5min_lt_44())) momentumCount++;
            if (Boolean.TRUE.equals(indicators.getRsi_1min_lt_44())) momentumCount++;
            if (Boolean.TRUE.equals(indicators.getRsi_15min_lt_44())) momentumCount++;
        }
        return momentumCount;
    }
    
    /**
     * Get category-based condition counts for the given strategy
     */
    public String getConditionCounts(FlattenedIndicators indicators, String strategy) {
        try {
            if ("CALL".equals(strategy)) {
                return getCallCategoryCounts(indicators);
            } else if ("PUT".equals(strategy)) {
                return getPutCategoryCounts(indicators);
            } else {
                return "Categories: EMA:0/2 FV:0/4 CS:0/3 M:0/2";
            }
        } catch (Exception e) {
            log.debug("Error getting condition counts: {}", e.getMessage());
            return "Categories: EMA:0/2 FV:0/4 CS:0/3 M:0/2";
        }
    }
    
    /**
     * Get category counts for CALL strategy
     */
    public String getCallCategoryCounts(FlattenedIndicators indicators) {
        int emaCount = getEmaCount(indicators, true);
        int futureVolumeCount = getFutureVolumeCount(indicators, true);
        int candlestickCount = getCandlestickCount(indicators, true);
        int momentumCount = getMomentumCount(indicators, true);
        
        // Get actual scenario requirements instead of hardcoded values
        List<ScalpingEntryConfig.Scenario> scenarios = configService.getScenarios();
        ScalpingEntryConfig.ScenarioRequirements requirements = null;
        
        if (!scenarios.isEmpty()) {
            requirements = scenarios.get(0).getRequirements(); // Use first scenario (SAFE_ENTRY_SIGNAL)
        }
        
        // Use actual requirements or fallback to hardcoded if config not available
        int emaRequired = requirements != null && requirements.getEma_min_count() != null ? requirements.getEma_min_count() : 2;
        int fvRequired = requirements != null && requirements.getFutureAndVolume_min_count() != null ? requirements.getFutureAndVolume_min_count() : 3;
        int csRequired = requirements != null && requirements.getCandlestick_min_count() != null ? requirements.getCandlestick_min_count() : 3;
        int mRequired = requirements != null && requirements.getMomentum_min_count() != null ? requirements.getMomentum_min_count() : 2;
        
        return String.format("Categories: EMA:%d/%d%s FV:%d/%d%s CS:%d/%d%s M:%d/%d%s", 
                           emaCount, emaRequired, emaCount >= emaRequired ? "✅" : "",
                           futureVolumeCount, fvRequired, futureVolumeCount >= fvRequired ? "✅" : "",
                           candlestickCount, csRequired, candlestickCount >= csRequired ? "✅" : "",
                           momentumCount, mRequired, momentumCount >= mRequired ? "✅" : "");
    }
    
    /**
     * Get category counts for PUT strategy
     */
    public String getPutCategoryCounts(FlattenedIndicators indicators) {
        int emaCount = getEmaCount(indicators, false);
        int futureVolumeCount = getFutureVolumeCount(indicators, false);
        int candlestickCount = getCandlestickCount(indicators, false);
        int momentumCount = getMomentumCount(indicators, false);
        
        // Get actual scenario requirements instead of hardcoded values
        List<ScalpingEntryConfig.Scenario> scenarios = configService.getScenarios();
        ScalpingEntryConfig.ScenarioRequirements requirements = null;
        
        if (!scenarios.isEmpty()) {
            requirements = scenarios.get(0).getRequirements(); // Use first scenario (SAFE_ENTRY_SIGNAL)
        }
        
        // Use actual requirements or fallback to hardcoded if config not available
        int emaRequired = requirements != null && requirements.getEma_min_count() != null ? requirements.getEma_min_count() : 2;
        int fvRequired = requirements != null && requirements.getFutureAndVolume_min_count() != null ? requirements.getFutureAndVolume_min_count() : 3;
        int csRequired = requirements != null && requirements.getCandlestick_min_count() != null ? requirements.getCandlestick_min_count() : 3;
        int mRequired = requirements != null && requirements.getMomentum_min_count() != null ? requirements.getMomentum_min_count() : 2;
        
        return String.format("Categories: EMA:%d/%d%s FV:%d/%d%s CS:%d/%d%s M:%d/%d%s", 
                           emaCount, emaRequired, emaCount >= emaRequired ? "✅" : "",
                           futureVolumeCount, fvRequired, futureVolumeCount >= fvRequired ? "✅" : "",
                           candlestickCount, csRequired, candlestickCount >= csRequired ? "✅" : "",
                           momentumCount, mRequired, momentumCount >= mRequired ? "✅" : "");
    }

    /**
     * Get detailed category analysis for a strategy
     */
    public CategoryAnalysisResult getDetailedCategoryAnalysis(FlattenedIndicators indicators, String strategy) {
        Map<String, Integer> categoryCounts = getCategoryCountsMap(indicators, strategy);
        
        // Get scenario requirements
        List<ScalpingEntryConfig.Scenario> scenarios = configService.getScenarios();
        ScalpingEntryConfig.ScenarioRequirements requirements = null;
        
        if (!scenarios.isEmpty()) {
            requirements = scenarios.get(0).getRequirements();
        }
        
        // Calculate requirements
        int emaRequired = requirements != null && requirements.getEma_min_count() != null ? requirements.getEma_min_count() : 2;
        int fvRequired = requirements != null && requirements.getFutureAndVolume_min_count() != null ? requirements.getFutureAndVolume_min_count() : 3;
        int csRequired = requirements != null && requirements.getCandlestick_min_count() != null ? requirements.getCandlestick_min_count() : 3;
        int mRequired = requirements != null && requirements.getMomentum_min_count() != null ? requirements.getMomentum_min_count() : 2;
        
        return new CategoryAnalysisResult(
            categoryCounts.getOrDefault("ema", 0), emaRequired,
            categoryCounts.getOrDefault("futureAndVolume", 0), fvRequired,
            categoryCounts.getOrDefault("candlestick", 0), csRequired,
            categoryCounts.getOrDefault("momentum", 0), mRequired
        );
    }

    /**
     * Check if all categories meet their requirements
     */
    public boolean areAllCategoriesSatisfied(FlattenedIndicators indicators, String strategy) {
        CategoryAnalysisResult analysis = getDetailedCategoryAnalysis(indicators, strategy);
        return analysis.isEmaSatisfied() && analysis.isFutureVolumeSatisfied() && 
               analysis.isCandlestickSatisfied() && analysis.isMomentumSatisfied();
    }

    /**
     * Get category satisfaction percentage
     */
    public double getCategorySatisfactionPercentage(FlattenedIndicators indicators, String strategy) {
        CategoryAnalysisResult analysis = getDetailedCategoryAnalysis(indicators, strategy);
        int satisfiedCategories = 0;
        int totalCategories = 4;
        
        if (analysis.isEmaSatisfied()) satisfiedCategories++;
        if (analysis.isFutureVolumeSatisfied()) satisfiedCategories++;
        if (analysis.isCandlestickSatisfied()) satisfiedCategories++;
        if (analysis.isMomentumSatisfied()) satisfiedCategories++;
        
        return (double) satisfiedCategories / totalCategories * 100;
    }

    /**
     * Category analysis result
     */
    public static class CategoryAnalysisResult {
        private final int emaCount;
        private final int emaRequired;
        private final int futureVolumeCount;
        private final int futureVolumeRequired;
        private final int candlestickCount;
        private final int candlestickRequired;
        private final int momentumCount;
        private final int momentumRequired;

        public CategoryAnalysisResult(int emaCount, int emaRequired, int futureVolumeCount, int futureVolumeRequired,
                                    int candlestickCount, int candlestickRequired, int momentumCount, int momentumRequired) {
            this.emaCount = emaCount;
            this.emaRequired = emaRequired;
            this.futureVolumeCount = futureVolumeCount;
            this.futureVolumeRequired = futureVolumeRequired;
            this.candlestickCount = candlestickCount;
            this.candlestickRequired = candlestickRequired;
            this.momentumCount = momentumCount;
            this.momentumRequired = momentumRequired;
        }

        public int getEmaCount() { return emaCount; }
        public int getEmaRequired() { return emaRequired; }
        public boolean isEmaSatisfied() { return emaCount >= emaRequired; }
        
        public int getFutureVolumeCount() { return futureVolumeCount; }
        public int getFutureVolumeRequired() { return futureVolumeRequired; }
        public boolean isFutureVolumeSatisfied() { return futureVolumeCount >= futureVolumeRequired; }
        
        public int getCandlestickCount() { return candlestickCount; }
        public int getCandlestickRequired() { return candlestickRequired; }
        public boolean isCandlestickSatisfied() { return candlestickCount >= candlestickRequired; }
        
        public int getMomentumCount() { return momentumCount; }
        public int getMomentumRequired() { return momentumRequired; }
        public boolean isMomentumSatisfied() { return momentumCount >= momentumRequired; }
    }
}
