package com.jtradebot.processor.service.entry;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.config.ScoringConfigurationService;
import com.jtradebot.processor.model.strategy.FlatMarketFilteringConfig;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.strategy.ScalpingEntryConfig;
import com.jtradebot.processor.model.strategy.ScalpingEntryDecision;
import com.jtradebot.processor.service.analysis.MarketDirectionService;
import com.jtradebot.processor.service.analysis.SignalDeterminationService;

import com.zerodhatech.models.Tick;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScalpingEntryService {

    private final DynamicStrategyConfigService configService;
    private final ScoringConfigurationService scoringConfigService;
    private final UnstableMarketConditionAnalysisService unstableMarketConditionAnalysisService;
    private final MarketDirectionService marketDirectionService;
    private final SignalDeterminationService signalDeterminationService;


    public ScalpingEntryDecision evaluateEntry(Tick tick, FlattenedIndicators indicators, Double preCalculatedQualityScore, Boolean preCalculatedMarketCondition) {
        try {
            // Step 1: Use MarketDirectionService to get category counts and market direction
            String marketDirection = marketDirectionService.determineMarketDirection(indicators);
            Map<String, Integer> callCategoryCounts = marketDirectionService.getCategoryScores(indicators, "CALL");
            Map<String, Integer> putCategoryCounts = marketDirectionService.getCategoryScores(indicators, "PUT");
            
            // Step 2: Use pre-calculated quality score or calculate if not provided
            double qualityScore = Objects.requireNonNullElseGet(preCalculatedQualityScore, () -> calculateQualityScore(indicators));
            
            // Step 3: Loop through scenarios and check their specific requirements
            List<ScalpingEntryConfig.Scenario> scenarios = configService.getScenarios();
            List<ScenarioEvaluation> scenarioEvaluations = new ArrayList<>();
            
            for (ScalpingEntryConfig.Scenario scenario : scenarios) {
                ScenarioEvaluation evaluation = evaluateScenario(scenario, indicators, callCategoryCounts, putCategoryCounts, qualityScore, tick, preCalculatedMarketCondition, marketDirection);
                scenarioEvaluations.add(evaluation);
            }
            
            // Step 4: If any scenario matches, take entry
            Optional<ScenarioEvaluation> bestScenario = scenarioEvaluations.stream()
                    .filter(ScenarioEvaluation::isPassed)
                    .max(Comparator.comparing(ScenarioEvaluation::getScore));
            
            if (bestScenario.isPresent()) {
                ScenarioEvaluation best = bestScenario.get();
                
                // Create entry decision with market direction
                ScalpingEntryDecision entryDecision = ScalpingEntryDecision.builder()
                        .shouldEntry(true)
                        .scenarioName(best.getScenarioName())
                        .confidence(best.getScore())
                        .qualityScore(qualityScore)
                        .reason(best.getReason())
                        .riskManagement(best.getScenario().getRiskManagement())
                        .categoryScores(best.getCategoryScores())
                        .marketDirection(marketDirection)
                        .build();
                
                // Use SignalDeterminationService to set explicit signal flags
                SignalDeterminationService.SignalResult signalResult = signalDeterminationService.determineSignals(entryDecision);
                entryDecision.setShouldCall(signalResult.isShouldCall());
                entryDecision.setShouldPut(signalResult.isShouldPut());
                
                return entryDecision;
            } else {
                return ScalpingEntryDecision.builder()
                        .shouldEntry(false)
                        .scenarioName("NO_SCENARIO_PASSED")
                        .reason("No scenario passed the evaluation criteria")
                        .build();
            }
            
        } catch (Exception e) {
            log.error("Error evaluating entry for tick: {}", tick.getInstrumentToken(), e);
            return ScalpingEntryDecision.builder()
                    .shouldEntry(false)
                    .reason("Error during evaluation: " + e.getMessage())
                    .build();
        }
    }

    private ScenarioEvaluation evaluateScenario(ScalpingEntryConfig.Scenario scenario, 
                                               FlattenedIndicators indicators, 
                                               Map<String, Integer> callCategoryCounts,
                                               Map<String, Integer> putCategoryCounts,
                                               double preCalculatedQualityScore,
                                               Tick tick,
                                               Boolean preCalculatedMarketCondition,
                                               String marketDirection) {

        
        ScenarioEvaluation evaluation = new ScenarioEvaluation();
        evaluation.setScenarioName(scenario.getName());
        evaluation.setScenario(scenario);

        // Use the pre-calculated market condition result to avoid redundant calculations
        boolean marketConditionSuitable = preCalculatedMarketCondition != null ? preCalculatedMarketCondition : 
            unstableMarketConditionAnalysisService.isMarketConditionSuitable(tick, indicators);
        
        if (!marketConditionSuitable) {
            evaluation.setPassed(false);
            evaluation.setScore(0.0);
            
            // Get detailed filtering result for logging
            if (configService.isNoTradeZonesEnabled()) {
                UnstableMarketConditionAnalysisService.FlexibleFilteringResult flexibleResult = 
                    unstableMarketConditionAnalysisService.checkFlexibleFilteringConditions(tick, indicators);
                evaluation.setReason(flexibleResult.getReason());
                log.warn("Scenario '{}' failed flexible entry filtering: {}", scenario.getName(), flexibleResult.getReason());
            } else {
                // Fallback to old filtering for detailed reason
                UnstableMarketConditionAnalysisService.EntryFilteringResult filteringResult =
                    unstableMarketConditionAnalysisService.checkEntryFilteringConditions(tick, indicators);
                evaluation.setReason(filteringResult.getReason());
                log.warn("Scenario '{}' failed entry filtering: {}", scenario.getName(), filteringResult.getReason());
            }
            return evaluation;
        } else {
            log.info("✅ FLEXIBLE FILTERING PASSED - Scenario '{}' passed market condition filtering", scenario.getName());
        }
        
        ScalpingEntryConfig.ScenarioRequirements requirements = scenario.getRequirements();
        
        // Check quality score requirement first (if specified)
        boolean qualityScorePassed = true;
        // Use pre-calculated score

        // Use scoring config threshold if scenario doesn't specify one
        double minQualityThreshold = requirements.getMinQualityScore() != null ? 
            requirements.getMinQualityScore() : scoringConfigService.getMinQualityScore();
        
        if (minQualityThreshold > 0) {
            qualityScorePassed = preCalculatedQualityScore >= minQualityThreshold;
            
            if (!qualityScorePassed) {
                log.debug("Quality score {} below threshold {} for scenario {}",
                        preCalculatedQualityScore, minQualityThreshold, scenario.getName());
            }
        }
        
        // If scenario only requires quality score (no category requirements)
        if (requirements.getMinQualityScore() != null && 
            requirements.getEma_min_count() == null && 
            requirements.getFutureAndVolume_min_count() == null && 
            requirements.getCandlestick_min_count() == null && 
            requirements.getMomentum_min_count() == null) {
            
            evaluation.setPassed(qualityScorePassed);
            evaluation.setScore(preCalculatedQualityScore);
            evaluation.setReason(qualityScorePassed ? "Quality score requirement met" : 
                               "Quality score " + preCalculatedQualityScore + " below threshold " + minQualityThreshold);
            
            return evaluation;
        }
        
        // Evaluate category-based requirements
        Map<String, Integer> categoryScores = new HashMap<>();
        Map<String, List<String>> matchedConditions = new HashMap<>();
        
        // Use the market direction passed as parameter
        boolean isCallDirection = "CALL".equals(marketDirection);
        
        // Category breakdown is now included in the main UnifiedIndicatorService log
        
        // Use only the appropriate category counts based on market direction
        if (requirements.getEma_min_count() != null) {
            int emaCount = isCallDirection ? callCategoryCounts.getOrDefault("ema", 0) : putCategoryCounts.getOrDefault("ema", 0);
            categoryScores.put("ema", emaCount);
        }
        
        if (requirements.getFutureAndVolume_min_count() != null) {
            int volumeCount = isCallDirection ? callCategoryCounts.getOrDefault("futureAndVolume", 0) : putCategoryCounts.getOrDefault("futureAndVolume", 0);
            categoryScores.put("futureAndVolume", volumeCount);
        }
        
        if (requirements.getCandlestick_min_count() != null) {
            int candlestickCount = isCallDirection ? callCategoryCounts.getOrDefault("candlestick", 0) : putCategoryCounts.getOrDefault("candlestick", 0);
            categoryScores.put("candlestick", candlestickCount);
        }
        
        if (requirements.getMomentum_min_count() != null) {
            int momentumCount = isCallDirection ? callCategoryCounts.getOrDefault("momentum", 0) : putCategoryCounts.getOrDefault("momentum", 0);
            categoryScores.put("momentum", momentumCount);
        }
        

        
        // Keep category-based checks for entry decisions
        boolean categoryRequirementsPassed = true;
        List<String> failedCategories = new ArrayList<>();
        
        if (requirements.getEma_min_count() != null && 
            categoryScores.get("ema") < requirements.getEma_min_count()) {
            categoryRequirementsPassed = false;
            failedCategories.add("EMA: " + categoryScores.get("ema") + "/" + requirements.getEma_min_count());
        }
        
        if (requirements.getFutureAndVolume_min_count() != null && 
            categoryScores.get("futureAndVolume") < requirements.getFutureAndVolume_min_count()) {
            categoryRequirementsPassed = false;
            failedCategories.add("FV: " + categoryScores.get("futureAndVolume") + "/" + requirements.getFutureAndVolume_min_count());
        }
        
        if (requirements.getCandlestick_min_count() != null && 
            categoryScores.get("candlestick") < requirements.getCandlestick_min_count()) {
            categoryRequirementsPassed = false;
            failedCategories.add("CS: " + categoryScores.get("candlestick") + "/" + requirements.getCandlestick_min_count());
        }
        
        if (requirements.getMomentum_min_count() != null && 
            categoryScores.get("momentum") < requirements.getMomentum_min_count()) {
            categoryRequirementsPassed = false;
            failedCategories.add("M: " + categoryScores.get("momentum") + "/" + requirements.getMomentum_min_count());
        }
        

        
        // Final decision: entry filtering + category requirements + quality score
        boolean passed = qualityScorePassed && categoryRequirementsPassed;
        
        evaluation.setPassed(passed);
        evaluation.setScore(passed ? preCalculatedQualityScore : 0.0);
        evaluation.setCategoryScores(categoryScores);
        evaluation.setMatchedConditions(matchedConditions);
        evaluation.setMarketDirection(marketDirection); // Set the determined market direction
        
        // Build comprehensive reason message
        StringBuilder reason = new StringBuilder();
        if (passed) {
            reason.append("All entry conditions met");
            log.info("🎯 SCENARIO PASSED - '{}' - Quality: {}/{} ({}), Categories: {}, Market Direction: {}", 
                    scenario.getName(), preCalculatedQualityScore, minQualityThreshold, qualityScorePassed ? "PASS" : "FAIL", 
                    categoryScores, marketDirection);
        } else {
            List<String> failures = new ArrayList<>();
            if (!qualityScorePassed) {
                failures.add("Quality score " + preCalculatedQualityScore + " below threshold " + minQualityThreshold);
            }
            if (!categoryRequirementsPassed) {
                failures.add("Failed categories: " + String.join(", ", failedCategories));
            }
            reason.append(String.join("; ", failures));
            
            log.warn("❌ SCENARIO FAILED - '{}' - Quality: {}/{} ({}), Categories: {}, Failed: {}", 
                    scenario.getName(), preCalculatedQualityScore, minQualityThreshold, qualityScorePassed ? "PASS" : "FAIL", 
                    categoryScores, String.join(", ", failures));
        }
        evaluation.setReason(reason.toString());
        
        return evaluation;
    }

    
    private boolean evaluateCondition(String condition, FlattenedIndicators indicators) {
        try {
            // This is a simplified condition evaluation
            // In a real implementation, you would have more sophisticated logic
            
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
        
        // Volume conditions - check specific timeframe using thresholds from scoring config
        if (condition.equals("volume_5min_surge")) {
            boolean hasVolumeSurge = Boolean.TRUE.equals(indicators.getVolume_5min_surge());
            Double volumeMultiplier = indicators.getVolume_surge_multiplier();
            
            // Check if volume multiplier meets the minimum threshold from scoring config
            if (hasVolumeSurge && volumeMultiplier != null) {
                double minVolumeMultiplier = scoringConfigService.getSurgeMultiplier();
                return volumeMultiplier >= minVolumeMultiplier;
            }
            return hasVolumeSurge;
        }
        if (condition.equals("volume_1min_surge")) {
            boolean hasVolumeSurge = Boolean.TRUE.equals(indicators.getVolume_1min_surge());
            Double volumeMultiplier = indicators.getVolume_surge_multiplier();
            
            // Check if volume multiplier meets the minimum threshold from scoring config
            if (hasVolumeSurge && volumeMultiplier != null) {
                double minVolumeMultiplier = scoringConfigService.getSurgeMultiplier();
                return volumeMultiplier >= minVolumeMultiplier;
            }
            return hasVolumeSurge;
        }
        if (condition.equals("volume_15min_surge")) {
            boolean hasVolumeSurge = Boolean.TRUE.equals(indicators.getVolume_15min_surge());
            Double volumeMultiplier = indicators.getVolume_surge_multiplier();
            
            // Check if volume multiplier meets the minimum threshold from scoring config
            if (hasVolumeSurge && volumeMultiplier != null) {
                double minVolumeMultiplier = scoringConfigService.getSurgeMultiplier();
                return volumeMultiplier >= minVolumeMultiplier;
            }
            return hasVolumeSurge;
        }
        
        // VWAP conditions - check specific timeframe
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
        
        // Support/Resistance conditions
        if (condition.contains("price_above_resistance")) {
            return Boolean.TRUE.equals(indicators.getPrice_above_resistance());
        }
        
        if (condition.contains("price_below_support")) {
            return Boolean.TRUE.equals(indicators.getPrice_below_support());
        }
        
        // Candlestick conditions - check specific timeframe
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
        if (condition.equals("long_body_5min")) {
            return Boolean.TRUE.equals(indicators.getLong_body_5min());
        }
        if (condition.equals("long_body_1min")) {
            return Boolean.TRUE.equals(indicators.getLong_body_1min());
        }
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
        
        // RSI conditions - check specific timeframe using thresholds from scoring config
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
        
        return false; // Default to false for unknown conditions
        } catch (Exception e) {
            log.warn("Error evaluating condition '{}': {}", condition, e.getMessage());
            return false; // Return false on any error
        }
    }
    
    private double calculateQualityScore(FlattenedIndicators indicators) {
        try {
            // Use dedicated scoring configuration from scoring-config.json
            double score = 0.0;
            
            // Determine market direction based on dominant trend
            boolean isCallDirection = determineCallDirection(indicators);
            
            if (isCallDirection) {
                score = calculateCallQualityScore(indicators);
            } else {
                score = calculatePutQualityScore(indicators);
            }
            
            // Cap at maximum quality score (from scoring-config.json)
            double maxQualityScore = scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMaxScore();
            score = Math.min(score, maxQualityScore);
            
            // Apply quality threshold from scoring config
            double minQualityThreshold = scoringConfigService.getMinQualityScore();
            log.debug("🔍 QUALITY SCORE CALCULATION - Raw Score: {}, Min Threshold: {}, Direction: {}", 
                score, minQualityThreshold, isCallDirection ? "CALL" : "PUT");
            
            if (score < minQualityThreshold) {
                log.debug("Quality score {} below threshold {}, reducing to 0", score, minQualityThreshold);
                score = 0.0;
            }
            
            log.debug("🔍 QUALITY SCORE FINAL - Final Score: {}", score);
            return score;
        } catch (Exception e) {
            log.warn("Error calculating quality score: {}", e.getMessage());
            return 0.0; // Return 0 on any error
        }
    }
    
    private boolean determineCallDirection(FlattenedIndicators indicators) {
        // Count bullish vs bearish signals to determine dominant direction
        int bullishSignals = 0;
        int bearishSignals = 0;
        
        // EMA signals
        if (Boolean.TRUE.equals(indicators.getEma5_5min_gt_ema34_5min())) bullishSignals++;
        if (Boolean.TRUE.equals(indicators.getEma5_5min_lt_ema34_5min())) bearishSignals++;
        
        // RSI signals
        if (Boolean.TRUE.equals(indicators.getRsi_5min_gt_60())) bullishSignals++;
        if (Boolean.TRUE.equals(indicators.getRsi_5min_lt_40())) bearishSignals++;
        
        // Price action signals
        if (Boolean.TRUE.equals(indicators.getPrice_gt_vwap_5min())) bullishSignals++;
        if (Boolean.TRUE.equals(indicators.getPrice_lt_vwap_5min())) bearishSignals++;
        
        return bullishSignals >= bearishSignals;
    }
    
    private double calculateCallQualityScore(FlattenedIndicators indicators) {
        // EMA Quality Score (CALL - bullish)
        double emaScore = 0.0;
        if (Boolean.TRUE.equals(indicators.getEma5_5min_gt_ema34_5min())) emaScore += scoringConfigService.getEmaQuality();
        if (Boolean.TRUE.equals(indicators.getEma5_1min_gt_ema34_1min())) emaScore += scoringConfigService.getEmaQuality();
        if (Boolean.TRUE.equals(indicators.getEma5_15min_gt_ema34_15min())) emaScore += scoringConfigService.getEmaQuality();
        // Cap EMA score at configured maximum
        emaScore = Math.min(emaScore, scoringConfigService.getEmaQuality());
        
        // RSI Quality Score (CALL - bullish)
        double rsiScore = 0.0;
        if (Boolean.TRUE.equals(indicators.getRsi_5min_gt_60())) rsiScore += scoringConfigService.getRsiQuality();
        if (Boolean.TRUE.equals(indicators.getRsi_1min_gt_60())) rsiScore += scoringConfigService.getRsiQuality();
        if (Boolean.TRUE.equals(indicators.getRsi_15min_gt_60())) rsiScore += scoringConfigService.getRsiQuality();
        
        // RSI Divergence Bonus (CALL - bullish divergence)
        if (Boolean.TRUE.equals(indicators.getRsi_bullish_divergence_5min()) || 
            Boolean.TRUE.equals(indicators.getRsi_bullish_divergence_1min()) || 
            Boolean.TRUE.equals(indicators.getRsi_bullish_divergence_15min())) {
            rsiScore += scoringConfigService.getScoringConfig().getQualityScoring().getMomentumQuality().getRsiDivergenceBonus();
            log.info("🎯 RSI BULLISH DIVERGENCE BONUS - Added {} points to CALL quality score", 
                    scoringConfigService.getScoringConfig().getQualityScoring().getMomentumQuality().getRsiDivergenceBonus());
        }
        
        // Cap RSI score at configured maximum
        rsiScore = Math.min(rsiScore, scoringConfigService.getRsiQuality());
        
        // Volume Quality Score (same for both directions)
        double volumeScore = 0.0;
        if (Boolean.TRUE.equals(indicators.getVolume_5min_surge())) volumeScore += scoringConfigService.getScoringConfig().getQualityScoring().getVolumeQuality().getVolume5min();
        if (Boolean.TRUE.equals(indicators.getVolume_1min_surge())) volumeScore += scoringConfigService.getScoringConfig().getQualityScoring().getVolumeQuality().getVolume1min();
        if (Boolean.TRUE.equals(indicators.getVolume_15min_surge())) volumeScore += scoringConfigService.getScoringConfig().getQualityScoring().getVolumeQuality().getVolume1min();
        // Cap volume score at configured maximum
        volumeScore = Math.min(volumeScore, scoringConfigService.getScoringConfig().getQualityScoring().getVolumeQuality().getVolume5min());
        
        // Price Action Quality Score (CALL - bullish)
        double priceActionScore = 0.0;
        if (Boolean.TRUE.equals(indicators.getGreen_candle_5min())) priceActionScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (Boolean.TRUE.equals(indicators.getGreen_candle_1min())) priceActionScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (Boolean.TRUE.equals(indicators.getLong_body_5min())) priceActionScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getLowReliability();
        if (Boolean.TRUE.equals(indicators.getLong_body_1min())) priceActionScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getLowReliability();
        if (Boolean.TRUE.equals(indicators.getPrice_gt_vwap_5min())) priceActionScore += scoringConfigService.getPriceActionQuality();
        if (Boolean.TRUE.equals(indicators.getPrice_gt_vwap_1min())) priceActionScore += scoringConfigService.getPriceActionQuality();
        if (Boolean.TRUE.equals(indicators.getPrice_above_resistance())) priceActionScore += scoringConfigService.getPriceActionQuality();
        // Cap price action score at configured maximum
        priceActionScore = Math.min(priceActionScore, scoringConfigService.getPriceActionQuality());
        
        // Futuresignals Quality Score (CALL - bullish)
        double futuresignalsScore = 0.0;
        if (indicators.getFuturesignals() != null && indicators.getFuturesignals().getAllTimeframesBullish()) {
            futuresignalsScore = scoringConfigService.getFuturesignalQuality();
        } else if (indicators.getFuturesignals() != null && 
                   (indicators.getFuturesignals().getFiveMinBullishSurge() || indicators.getFuturesignals().getOneMinBullishSurge())) {
            futuresignalsScore = scoringConfigService.getFuturesignalQuality() / 2.0;
        }
        // Cap futuresignals score at configured maximum
        futuresignalsScore = Math.min(futuresignalsScore, scoringConfigService.getFuturesignalQuality());
        
        // Momentum Quality Score (CALL - bullish)
        double momentumScore = 0.0;
        int bullishTimeframes = 0;
        if (Boolean.TRUE.equals(indicators.getEma5_1min_gt_ema34_1min())) bullishTimeframes++;
        if (Boolean.TRUE.equals(indicators.getEma5_5min_gt_ema34_5min())) bullishTimeframes++;
        if (Boolean.TRUE.equals(indicators.getEma5_15min_gt_ema34_15min())) bullishTimeframes++;
        
        if (bullishTimeframes == 3) momentumScore = scoringConfigService.getScoringConfig().getQualityScoring().getMomentumQuality().getPerfectAlignment();
        else if (bullishTimeframes == 2) momentumScore = scoringConfigService.getScoringConfig().getQualityScoring().getMomentumQuality().getMajorityAlignment();
        else if (bullishTimeframes == 1) momentumScore = scoringConfigService.getScoringConfig().getQualityScoring().getMomentumQuality().getSingleAlignment();
        // Cap momentum score at configured maximum
        momentumScore = Math.min(momentumScore, scoringConfigService.getScoringConfig().getQualityScoring().getMomentumQuality().getPerfectAlignment());
        
        // Candlestick Quality Score (CALL - bullish patterns)
        double candlestickScore = 0.0;
        if (Boolean.TRUE.equals(indicators.getBullish_engulfing_5min())) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getHighReliability();
        if (Boolean.TRUE.equals(indicators.getBullish_engulfing_1min())) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getHighReliability();
        if (Boolean.TRUE.equals(indicators.getBullish_morning_star_5min())) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getHighReliability();
        if (Boolean.TRUE.equals(indicators.getBullish_morning_star_1min())) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getHighReliability();
        if (Boolean.TRUE.equals(indicators.getHammer_5min())) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (Boolean.TRUE.equals(indicators.getHammer_1min())) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        candlestickScore = Math.min(candlestickScore, scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMaxScore());
        // Cap candlestick score at configured maximum
        candlestickScore = Math.min(candlestickScore, scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMaxScore());
        
        // Calculate average of all component scores (0-10 range)
        double totalScore = emaScore + rsiScore + volumeScore + priceActionScore + 
                           futuresignalsScore + momentumScore + candlestickScore;
        return totalScore / 7.0; // Average of 7 components
    }
    
    private double calculatePutQualityScore(FlattenedIndicators indicators) {
        // EMA Quality Score (PUT - bearish)
        double emaScore = 0.0;
        if (Boolean.TRUE.equals(indicators.getEma5_5min_lt_ema34_5min())) emaScore += scoringConfigService.getEmaQuality();
        if (Boolean.TRUE.equals(indicators.getEma5_1min_lt_ema34_1min())) emaScore += scoringConfigService.getEmaQuality();
        if (Boolean.TRUE.equals(indicators.getEma5_15min_lt_ema34_15min())) emaScore += scoringConfigService.getEmaQuality();
        // Cap EMA score at configured maximum
        emaScore = Math.min(emaScore, scoringConfigService.getEmaQuality());
        
        // RSI Quality Score (PUT - bearish)
        double rsiScore = 0.0;
        if (Boolean.TRUE.equals(indicators.getRsi_5min_lt_40())) rsiScore += scoringConfigService.getRsiQuality();
        if (Boolean.TRUE.equals(indicators.getRsi_1min_lt_40())) rsiScore += scoringConfigService.getRsiQuality();
        if (Boolean.TRUE.equals(indicators.getRsi_15min_lt_40())) rsiScore += scoringConfigService.getRsiQuality();
        
        // RSI Divergence Bonus (PUT - bearish divergence)
        if (Boolean.TRUE.equals(indicators.getRsi_bearish_divergence_5min()) || 
            Boolean.TRUE.equals(indicators.getRsi_bearish_divergence_1min()) || 
            Boolean.TRUE.equals(indicators.getRsi_bearish_divergence_15min())) {
            rsiScore += scoringConfigService.getScoringConfig().getQualityScoring().getMomentumQuality().getRsiDivergenceBonus();
            log.info("🎯 RSI BEARISH DIVERGENCE BONUS - Added {} points to PUT quality score", 
                    scoringConfigService.getScoringConfig().getQualityScoring().getMomentumQuality().getRsiDivergenceBonus());
        }
        
        // Cap RSI score at configured maximum
        rsiScore = Math.min(rsiScore, scoringConfigService.getRsiQuality());
        
        // Volume Quality Score (same for both directions)
        double volumeScore = 0.0;
        if (Boolean.TRUE.equals(indicators.getVolume_5min_surge())) volumeScore += scoringConfigService.getScoringConfig().getQualityScoring().getVolumeQuality().getVolume5min();
        if (Boolean.TRUE.equals(indicators.getVolume_1min_surge())) volumeScore += scoringConfigService.getScoringConfig().getQualityScoring().getVolumeQuality().getVolume1min();
        if (Boolean.TRUE.equals(indicators.getVolume_15min_surge())) volumeScore += scoringConfigService.getScoringConfig().getQualityScoring().getVolumeQuality().getVolume1min();
        // Cap volume score at configured maximum
        volumeScore = Math.min(volumeScore, scoringConfigService.getScoringConfig().getQualityScoring().getVolumeQuality().getVolume5min());
        
        // Price Action Quality Score (PUT - bearish)
        double priceActionScore = 0.0;
        if (Boolean.TRUE.equals(indicators.getRed_candle_5min())) priceActionScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (Boolean.TRUE.equals(indicators.getRed_candle_1min())) priceActionScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (Boolean.TRUE.equals(indicators.getLong_body_5min())) priceActionScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getLowReliability();
        if (Boolean.TRUE.equals(indicators.getLong_body_1min())) priceActionScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getLowReliability();
        if (Boolean.TRUE.equals(indicators.getPrice_lt_vwap_5min())) priceActionScore += scoringConfigService.getPriceActionQuality();
        if (Boolean.TRUE.equals(indicators.getPrice_lt_vwap_1min())) priceActionScore += scoringConfigService.getPriceActionQuality();
        if (Boolean.TRUE.equals(indicators.getPrice_below_support())) priceActionScore += scoringConfigService.getPriceActionQuality();
        // Cap price action score at configured maximum
        priceActionScore = Math.min(priceActionScore, scoringConfigService.getPriceActionQuality());
        
        // Futuresignals Quality Score (PUT - bearish)
        double futuresignalsScore = 0.0;
        if (indicators.getFuturesignals() != null && indicators.getFuturesignals().getAllTimeframesBearish()) {
            futuresignalsScore = scoringConfigService.getFuturesignalQuality();
        } else if (indicators.getFuturesignals() != null && 
                   (indicators.getFuturesignals().getFiveMinBearishSurge() || indicators.getFuturesignals().getOneMinBearishSurge())) {
            futuresignalsScore = scoringConfigService.getFuturesignalQuality() / 2.0;
        }
        // Cap futuresignals score at configured maximum
        futuresignalsScore = Math.min(futuresignalsScore, scoringConfigService.getFuturesignalQuality());
        
        // Momentum Quality Score (PUT - bearish)
        double momentumScore = 0.0;
        int bearishTimeframes = 0;
        if (Boolean.TRUE.equals(indicators.getEma5_1min_lt_ema34_1min())) bearishTimeframes++;
        if (Boolean.TRUE.equals(indicators.getEma5_5min_lt_ema34_5min())) bearishTimeframes++;
        if (Boolean.TRUE.equals(indicators.getEma5_15min_lt_ema34_15min())) bearishTimeframes++;
        
        if (bearishTimeframes == 3) momentumScore = scoringConfigService.getScoringConfig().getQualityScoring().getMomentumQuality().getPerfectAlignment();
        else if (bearishTimeframes == 2) momentumScore = scoringConfigService.getScoringConfig().getQualityScoring().getMomentumQuality().getMajorityAlignment();
        else if (bearishTimeframes == 1) momentumScore = scoringConfigService.getScoringConfig().getQualityScoring().getMomentumQuality().getSingleAlignment();
        // Cap momentum score at configured maximum
        momentumScore = Math.min(momentumScore, scoringConfigService.getScoringConfig().getQualityScoring().getMomentumQuality().getPerfectAlignment());
        
        // Candlestick Quality Score (PUT - bearish patterns)
        double candlestickScore = 0.0;
        if (Boolean.TRUE.equals(indicators.getBearish_engulfing_5min())) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getHighReliability();
        if (Boolean.TRUE.equals(indicators.getBearish_engulfing_1min())) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getHighReliability();
        if (Boolean.TRUE.equals(indicators.getBearish_evening_star_5min())) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getHighReliability();
        if (Boolean.TRUE.equals(indicators.getBearish_evening_star_1min())) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getHighReliability();
        if (Boolean.TRUE.equals(indicators.getShooting_star_5min())) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (Boolean.TRUE.equals(indicators.getShooting_star_1min())) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        candlestickScore = Math.min(candlestickScore, scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMaxScore());
        // Cap candlestick score at configured maximum
        candlestickScore = Math.min(candlestickScore, scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMaxScore());
        
        // Calculate average of all component scores (0-10 range)
        double totalScore = emaScore + rsiScore + volumeScore + priceActionScore + 
                           futuresignalsScore + momentumScore + candlestickScore;
        return totalScore / 7.0; // Average of 7 components
    }

    
    // Helper class for scenario evaluation results
    @Setter
    @Getter
    private static class ScenarioEvaluation {
        // Getters and setters
        private String scenarioName;
        private ScalpingEntryConfig.Scenario scenario;
        private boolean passed;
        private double score;
        private Map<String, Integer> categoryScores;
        private Map<String, List<String>> matchedConditions;
        private String reason;
        private String marketDirection; // Store the determined market direction

    }
}
