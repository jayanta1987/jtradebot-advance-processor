package com.jtradebot.processor.service.impl;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.config.ScoringConfigurationService;
import com.jtradebot.processor.model.strategy.FlatMarketFilteringConfig;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.strategy.ScalpingEntryConfig;
import com.jtradebot.processor.model.strategy.ScalpingEntryDecision;
import com.jtradebot.processor.service.MarketConditionAnalysisService;
import com.jtradebot.processor.service.ScalpingEntryService;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScalpingEntryServiceImpl implements ScalpingEntryService {

    private final DynamicStrategyConfigService configService;
    private final ScoringConfigurationService scoringConfigService;
    private final MarketConditionAnalysisService marketConditionAnalysisService;

    @Override
    public ScalpingEntryDecision evaluateEntry(Tick tick, FlattenedIndicators indicators) {
        return evaluateEntry(tick, indicators, null);
    }
    
    /**
     * Evaluates entry conditions with optional pre-calculated quality score
     */
    public ScalpingEntryDecision evaluateEntry(Tick tick, FlattenedIndicators indicators, Double preCalculatedQualityScore) {
        try {
            // Step 0 removed: we no longer hard-block on flat market; requirements are tightened per-scenario
            
            // Step 1: Pre-calculate category counts for both CALL and PUT
            Map<String, Integer> callCategoryCounts = calculateCategoryCounts(indicators, configService.getCallCategories());
            Map<String, Integer> putCategoryCounts = calculateCategoryCounts(indicators, configService.getPutCategories());
            
            // Step 2: Use pre-calculated quality score or calculate if not provided
            double qualityScore;
            if (preCalculatedQualityScore != null) {
                qualityScore = preCalculatedQualityScore;
            } else {
                qualityScore = calculateQualityScore(indicators);
            }
            
            // Step 3: Loop through scenarios and check their specific requirements
            List<ScalpingEntryConfig.Scenario> scenarios = configService.getScenarios();
            List<ScenarioEvaluation> scenarioEvaluations = new ArrayList<>();
            
            for (ScalpingEntryConfig.Scenario scenario : scenarios) {
                ScenarioEvaluation evaluation = evaluateScenario(scenario, indicators, callCategoryCounts, putCategoryCounts, qualityScore, tick);
                scenarioEvaluations.add(evaluation);
            }
            
            // Step 4: If any scenario matches, take entry
            Optional<ScenarioEvaluation> bestScenario = scenarioEvaluations.stream()
                    .filter(eval -> eval.isPassed())
                    .max(Comparator.comparing(ScenarioEvaluation::getScore));
            
            if (bestScenario.isPresent()) {
                ScenarioEvaluation best = bestScenario.get();
                // Removed verbose scenario found log
                
                return ScalpingEntryDecision.builder()
                        .shouldEntry(true)
                        .scenarioName(best.getScenarioName())
                        .confidence(best.getScore())
                        .qualityScore(qualityScore) // Use pre-calculated quality score
                        .reason(best.getReason()) // Set the reason from scenario evaluation
                        .riskManagement(best.getScenario().getRiskManagement())
                        .categoryScores(best.getCategoryScores()) // Store actual scores used in evaluation
                        .build();
            } else {
                // Only log when scenarios fail - removed verbose logging
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
    
    /**
     * Step 1: Calculate category counts for given indicators and categories
     */
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
    
    private ScenarioEvaluation evaluateScenario(ScalpingEntryConfig.Scenario scenario, 
                                               FlattenedIndicators indicators, 
                                               Map<String, Integer> callCategoryCounts,
                                               Map<String, Integer> putCategoryCounts,
                                               double preCalculatedQualityScore,
                                               Tick tick) {
        
        ScenarioEvaluation evaluation = new ScenarioEvaluation();
        evaluation.setScenarioName(scenario.getName());
        evaluation.setScenario(scenario);
        
        ScalpingEntryConfig.ScenarioRequirements requirements = scenario.getRequirements();
        
        // Check quality score requirement first (if specified)
        boolean qualityScorePassed = true;
        double qualityScore = preCalculatedQualityScore; // Use pre-calculated score
        
        // Use scoring config threshold if scenario doesn't specify one
        double minQualityThreshold = requirements.getMinQualityScore() != null ? 
            requirements.getMinQualityScore() : scoringConfigService.getMinQualityScore();
        
        if (minQualityThreshold > 0) {
            qualityScorePassed = qualityScore >= minQualityThreshold;
            
            if (!qualityScorePassed) {
                log.debug("Quality score {} below threshold {} for scenario {}", 
                    qualityScore, minQualityThreshold, scenario.getName());
            }
        }
        
        // If scenario only requires quality score (no category requirements)
        if (requirements.getMinQualityScore() != null && 
            requirements.getEma_min_count() == null && 
            requirements.getFutureAndVolume_min_count() == null && 
            requirements.getCandlestick_min_count() == null && 
            requirements.getMomentum_min_count() == null) {
            
            evaluation.setPassed(qualityScorePassed);
            evaluation.setScore(qualityScore);
            evaluation.setReason(qualityScorePassed ? "Quality score requirement met" : 
                               "Quality score " + qualityScore + " below threshold " + minQualityThreshold);
            
            return evaluation;
        }
        
        // Evaluate category-based requirements
        Map<String, Integer> categoryScores = new HashMap<>();
        Map<String, List<String>> matchedConditions = new HashMap<>();
        
        // Determine market direction based on category strengths
        int callTotalScore = callCategoryCounts.getOrDefault("ema", 0) + 
                           callCategoryCounts.getOrDefault("futureAndVolume", 0) + 
                           callCategoryCounts.getOrDefault("candlestick", 0) + 
                           callCategoryCounts.getOrDefault("momentum", 0);
        int putTotalScore = putCategoryCounts.getOrDefault("ema", 0) + 
                          putCategoryCounts.getOrDefault("futureAndVolume", 0) + 
                          putCategoryCounts.getOrDefault("candlestick", 0) + 
                          putCategoryCounts.getOrDefault("momentum", 0);
        
        boolean isCallDirection = callTotalScore >= putTotalScore;
        
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
        
        // Check flat market filtering requirements first
        boolean flatMarketFilterPassed = true;
        ScalpingEntryConfig.ScenarioRequirements adjustedRequirements = requirements;
        boolean requirementsAdjusted = false;
        
        if (requirements.getFlatMarketFilter() != null && requirements.getFlatMarketFilter()) {
            boolean isMarketSuitable = marketConditionAnalysisService.isMarketConditionSuitable(tick, indicators);
            if (!isMarketSuitable) {
                // Instead of blocking, adjust requirements for flat market - Made more restrictive
                adjustedRequirements = adjustRequirementsForFlatMarket(requirements);
                requirementsAdjusted = true;
                
                // Additional flat market restrictions using configuration
                FlatMarketFilteringConfig config = configService.getFlatMarketFilteringConfig();
                double minQualityScore = config.getThresholds().getFlatMarketAdjustments().getMinQualityScore();
                
                if (preCalculatedQualityScore < minQualityScore) {
                    log.debug("Flat market detected - Quality score {} below flat market threshold {}", 
                             preCalculatedQualityScore, minQualityScore);
                    evaluation.setPassed(false);
                    evaluation.setScore(0.0);
                    evaluation.setReason("Flat market detected - Quality score " + preCalculatedQualityScore + 
                                       " below flat market threshold " + minQualityScore);
                    return evaluation;
                }
            }
        }
        
        // Check if all requirements are met (using adjusted requirements for flat market)
        boolean categoryRequirementsPassed = true;
        List<String> failedCategories = new ArrayList<>();
        
        if (adjustedRequirements.getEma_min_count() != null && 
            categoryScores.get("ema") < adjustedRequirements.getEma_min_count()) {
            categoryRequirementsPassed = false;
            String adjustmentNote = requirementsAdjusted ? " (+" + configService.getFlatMarketFilteringConfig().getThresholds().getFlatMarketAdjustments().getCategoryIncrement() + ")" : "";
            failedCategories.add("EMA: " + categoryScores.get("ema") + "/" + adjustedRequirements.getEma_min_count() + adjustmentNote);
        }
        
        if (adjustedRequirements.getFutureAndVolume_min_count() != null && 
            categoryScores.get("futureAndVolume") < adjustedRequirements.getFutureAndVolume_min_count()) {
            categoryRequirementsPassed = false;
            String adjustmentNote = requirementsAdjusted ? " (+" + configService.getFlatMarketFilteringConfig().getThresholds().getFlatMarketAdjustments().getCategoryIncrement() + ")" : "";
            failedCategories.add("FV: " + categoryScores.get("futureAndVolume") + "/" + adjustedRequirements.getFutureAndVolume_min_count() + adjustmentNote);
        }
        
        if (adjustedRequirements.getCandlestick_min_count() != null && 
            categoryScores.get("candlestick") < adjustedRequirements.getCandlestick_min_count()) {
            categoryRequirementsPassed = false;
            String adjustmentNote = requirementsAdjusted ? " (+" + configService.getFlatMarketFilteringConfig().getThresholds().getFlatMarketAdjustments().getCategoryIncrement() + ")" : "";
            failedCategories.add("CS: " + categoryScores.get("candlestick") + "/" + adjustedRequirements.getCandlestick_min_count() + adjustmentNote);
        }
        
        if (adjustedRequirements.getMomentum_min_count() != null && 
            categoryScores.get("momentum") < adjustedRequirements.getMomentum_min_count()) {
            categoryRequirementsPassed = false;
            String adjustmentNote = requirementsAdjusted ? " (+" + configService.getFlatMarketFilteringConfig().getThresholds().getFlatMarketAdjustments().getCategoryIncrement() + ")" : "";
            failedCategories.add("M: " + categoryScores.get("momentum") + "/" + adjustedRequirements.getMomentum_min_count() + adjustmentNote);
        }
        
        // Check directional strength requirement
        if (requirements.getMinDirectionalStrength() != null) {
            double directionalStrength = marketConditionAnalysisService.calculateDirectionalStrength(tick, indicators);
            if (directionalStrength < requirements.getMinDirectionalStrength()) {
                categoryRequirementsPassed = false;
                failedCategories.add("Directional strength: " + String.format("%.2f", directionalStrength) + 
                                   "/" + requirements.getMinDirectionalStrength());
            }
        }
        

        
        // Check confidence score threshold from scoring config
        boolean confidenceScorePassed = true;
        double confidenceScore = calculateScenarioScore(categoryScores, adjustedRequirements);
        double minConfidenceThreshold = scoringConfigService.getMinConfidenceScore();
        
        if (minConfidenceThreshold > 0) {
            confidenceScorePassed = confidenceScore >= minConfidenceThreshold;
            if (!confidenceScorePassed) {
                log.debug("Confidence score {} below threshold {} for scenario {}", 
                    confidenceScore, minConfidenceThreshold, scenario.getName());
            }
        }
        
        // All three requirements must be met: quality score, category requirements, and confidence score
        boolean passed = qualityScorePassed && categoryRequirementsPassed && confidenceScorePassed;
        
        // Add detailed debug logging
        if (!passed) {
            log.debug("🔍 SCENARIO EVALUATION FAILED - Scenario: {}, Quality: {}, Categories: {}, Confidence: {}", 
                scenario.getName(), qualityScorePassed, categoryRequirementsPassed, confidenceScorePassed);
            if (!qualityScorePassed) {
                log.debug("  ❌ Quality Score: {} < {}", preCalculatedQualityScore, minQualityThreshold);
            }
            if (!categoryRequirementsPassed) {
                log.debug("  ❌ Category Requirements: {}", String.join(", ", failedCategories));
            }
            if (!confidenceScorePassed) {
                log.debug("  ❌ Confidence Score: {} < {}", confidenceScore, minConfidenceThreshold);
            }
        } else {
            log.debug("✅ SCENARIO EVALUATION PASSED - Scenario: {}, Quality: {}, Categories: {}, Confidence: {}", 
                scenario.getName(), qualityScorePassed, categoryRequirementsPassed, confidenceScorePassed);
        }
        
        // Calculate overall score
        double score = calculateScenarioScore(categoryScores, adjustedRequirements);
        
        evaluation.setPassed(passed);
        evaluation.setScore(score);
        evaluation.setCategoryScores(categoryScores);
        evaluation.setMatchedConditions(matchedConditions);
        
        // Build comprehensive reason message
        StringBuilder reason = new StringBuilder();
        if (passed) {
            reason.append("All requirements met");
            if (requirementsAdjusted) {
                reason.append(" (flat market adjusted)");
            }
        } else {
            List<String> failures = new ArrayList<>();
            if (!qualityScorePassed) {
                failures.add("Quality score " + qualityScore + " below threshold " + minQualityThreshold);
            }
            if (!categoryRequirementsPassed) {
                failures.add("Failed categories: " + String.join(", ", failedCategories));
            }
            if (!confidenceScorePassed) {
                failures.add("Confidence score " + confidenceScore + " below threshold " + minConfidenceThreshold);
            }
            reason.append(String.join("; ", failures));
            if (requirementsAdjusted) {
                reason.append(" (flat market adjusted)");
            }
        }
        evaluation.setReason(reason.toString());
        
        return evaluation;
    }
    
    private List<String> evaluateCategoryConditions(List<String> conditions, FlattenedIndicators indicators, String category) {
        List<String> matchedConditions = new ArrayList<>();
        
        for (String condition : conditions) {
            if (evaluateCondition(condition, indicators)) {
                matchedConditions.add(condition);
            }
        }
        
        return matchedConditions;
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
        if (condition.equals("rsi_5min_gt_56")) {
            return Boolean.TRUE.equals(indicators.getRsi_5min_gt_56());
        }
        if (condition.equals("rsi_1min_gt_56")) {
            return Boolean.TRUE.equals(indicators.getRsi_1min_gt_56());
        }
        if (condition.equals("rsi_15min_gt_56")) {
            return Boolean.TRUE.equals(indicators.getRsi_15min_gt_56());
        }
        if (condition.equals("rsi_5min_lt_44")) {
            return Boolean.TRUE.equals(indicators.getRsi_5min_lt_44());
        }
        if (condition.equals("rsi_1min_lt_44")) {
            return Boolean.TRUE.equals(indicators.getRsi_1min_lt_44());
        }
        if (condition.equals("rsi_15min_lt_44")) {
            return Boolean.TRUE.equals(indicators.getRsi_15min_lt_44());
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
            log.info("🔍 QUALITY SCORE CALCULATION - Raw Score: {}, Min Threshold: {}, Direction: {}", 
                score, minQualityThreshold, isCallDirection ? "CALL" : "PUT");
            
            if (score < minQualityThreshold) {
                log.debug("Quality score {} below threshold {}, reducing to 0", score, minQualityThreshold);
                score = 0.0;
            }
            
            log.info("🔍 QUALITY SCORE FINAL - Final Score: {}", score);
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
        if (Boolean.TRUE.equals(indicators.getRsi_5min_gt_56())) bullishSignals++;
        if (Boolean.TRUE.equals(indicators.getRsi_5min_lt_44())) bearishSignals++;
        
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
        // Cap EMA score at 10
        emaScore = Math.min(emaScore, 10.0);
        
        // RSI Quality Score (CALL - bullish)
        double rsiScore = 0.0;
        if (Boolean.TRUE.equals(indicators.getRsi_5min_gt_56())) rsiScore += scoringConfigService.getRsiQuality();
        if (Boolean.TRUE.equals(indicators.getRsi_1min_gt_56())) rsiScore += scoringConfigService.getRsiQuality();
        if (Boolean.TRUE.equals(indicators.getRsi_15min_gt_56())) rsiScore += scoringConfigService.getRsiQuality();
        // Cap RSI score at 10
        rsiScore = Math.min(rsiScore, 10.0);
        
        // Volume Quality Score (same for both directions)
        double volumeScore = 0.0;
        if (Boolean.TRUE.equals(indicators.getVolume_5min_surge())) volumeScore += scoringConfigService.getScoringConfig().getQualityScoring().getVolumeQuality().getVolume5min();
        if (Boolean.TRUE.equals(indicators.getVolume_1min_surge())) volumeScore += scoringConfigService.getScoringConfig().getQualityScoring().getVolumeQuality().getVolume1min();
        if (Boolean.TRUE.equals(indicators.getVolume_15min_surge())) volumeScore += scoringConfigService.getScoringConfig().getQualityScoring().getVolumeQuality().getVolume1min();
        // Cap volume score at 10
        volumeScore = Math.min(volumeScore, 10.0);
        
        // Price Action Quality Score (CALL - bullish)
        double priceActionScore = 0.0;
        if (Boolean.TRUE.equals(indicators.getGreen_candle_5min())) priceActionScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (Boolean.TRUE.equals(indicators.getGreen_candle_1min())) priceActionScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (Boolean.TRUE.equals(indicators.getLong_body_5min())) priceActionScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getLowReliability();
        if (Boolean.TRUE.equals(indicators.getLong_body_1min())) priceActionScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getLowReliability();
        if (Boolean.TRUE.equals(indicators.getPrice_gt_vwap_5min())) priceActionScore += scoringConfigService.getPriceActionQuality();
        if (Boolean.TRUE.equals(indicators.getPrice_gt_vwap_1min())) priceActionScore += scoringConfigService.getPriceActionQuality();
        if (Boolean.TRUE.equals(indicators.getPrice_above_resistance())) priceActionScore += scoringConfigService.getPriceActionQuality();
        // Cap price action score at 10
        priceActionScore = Math.min(priceActionScore, 10.0);
        
        // Futuresignals Quality Score (CALL - bullish)
        double futuresignalsScore = 0.0;
        if (indicators.getFuturesignals() != null && indicators.getFuturesignals().getAllTimeframesBullish()) {
            futuresignalsScore = scoringConfigService.getFuturesignalQuality();
        } else if (indicators.getFuturesignals() != null && 
                   (indicators.getFuturesignals().getFiveMinBullishSurge() || indicators.getFuturesignals().getOneMinBullishSurge())) {
            futuresignalsScore = scoringConfigService.getFuturesignalQuality() / 2.0;
        }
        // Cap futuresignals score at 10
        futuresignalsScore = Math.min(futuresignalsScore, 10.0);
        
        // Momentum Quality Score (CALL - bullish)
        double momentumScore = 0.0;
        int bullishTimeframes = 0;
        if (Boolean.TRUE.equals(indicators.getEma5_1min_gt_ema34_1min())) bullishTimeframes++;
        if (Boolean.TRUE.equals(indicators.getEma5_5min_gt_ema34_5min())) bullishTimeframes++;
        if (Boolean.TRUE.equals(indicators.getEma5_15min_gt_ema34_15min())) bullishTimeframes++;
        
        if (bullishTimeframes == 3) momentumScore = scoringConfigService.getScoringConfig().getQualityScoring().getMomentumQuality().getPerfectAlignment();
        else if (bullishTimeframes == 2) momentumScore = scoringConfigService.getScoringConfig().getQualityScoring().getMomentumQuality().getMajorityAlignment();
        else if (bullishTimeframes == 1) momentumScore = scoringConfigService.getScoringConfig().getQualityScoring().getMomentumQuality().getSingleAlignment();
        // Cap momentum score at 10
        momentumScore = Math.min(momentumScore, 10.0);
        
        // Candlestick Quality Score (CALL - bullish patterns)
        double candlestickScore = 0.0;
        if (Boolean.TRUE.equals(indicators.getBullish_engulfing_5min())) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getHighReliability();
        if (Boolean.TRUE.equals(indicators.getBullish_engulfing_1min())) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getHighReliability();
        if (Boolean.TRUE.equals(indicators.getBullish_morning_star_5min())) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getHighReliability();
        if (Boolean.TRUE.equals(indicators.getBullish_morning_star_1min())) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getHighReliability();
        if (Boolean.TRUE.equals(indicators.getHammer_5min())) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (Boolean.TRUE.equals(indicators.getHammer_1min())) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        candlestickScore = Math.min(candlestickScore, scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMaxScore());
        // Cap candlestick score at 10
        candlestickScore = Math.min(candlestickScore, 10.0);
        
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
        // Cap EMA score at 10
        emaScore = Math.min(emaScore, 10.0);
        
        // RSI Quality Score (PUT - bearish)
        double rsiScore = 0.0;
        if (Boolean.TRUE.equals(indicators.getRsi_5min_lt_44())) rsiScore += scoringConfigService.getRsiQuality();
        if (Boolean.TRUE.equals(indicators.getRsi_1min_lt_44())) rsiScore += scoringConfigService.getRsiQuality();
        if (Boolean.TRUE.equals(indicators.getRsi_15min_lt_44())) rsiScore += scoringConfigService.getRsiQuality();
        // Cap RSI score at 10
        rsiScore = Math.min(rsiScore, 10.0);
        
        // Volume Quality Score (same for both directions)
        double volumeScore = 0.0;
        if (Boolean.TRUE.equals(indicators.getVolume_5min_surge())) volumeScore += scoringConfigService.getScoringConfig().getQualityScoring().getVolumeQuality().getVolume5min();
        if (Boolean.TRUE.equals(indicators.getVolume_1min_surge())) volumeScore += scoringConfigService.getScoringConfig().getQualityScoring().getVolumeQuality().getVolume1min();
        if (Boolean.TRUE.equals(indicators.getVolume_15min_surge())) volumeScore += scoringConfigService.getScoringConfig().getQualityScoring().getVolumeQuality().getVolume1min();
        // Cap volume score at 10
        volumeScore = Math.min(volumeScore, 10.0);
        
        // Price Action Quality Score (PUT - bearish)
        double priceActionScore = 0.0;
        if (Boolean.TRUE.equals(indicators.getRed_candle_5min())) priceActionScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (Boolean.TRUE.equals(indicators.getRed_candle_1min())) priceActionScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (Boolean.TRUE.equals(indicators.getLong_body_5min())) priceActionScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getLowReliability();
        if (Boolean.TRUE.equals(indicators.getLong_body_1min())) priceActionScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getLowReliability();
        if (Boolean.TRUE.equals(indicators.getPrice_lt_vwap_5min())) priceActionScore += scoringConfigService.getPriceActionQuality();
        if (Boolean.TRUE.equals(indicators.getPrice_lt_vwap_1min())) priceActionScore += scoringConfigService.getPriceActionQuality();
        if (Boolean.TRUE.equals(indicators.getPrice_below_support())) priceActionScore += scoringConfigService.getPriceActionQuality();
        // Cap price action score at 10
        priceActionScore = Math.min(priceActionScore, 10.0);
        
        // Futuresignals Quality Score (PUT - bearish)
        double futuresignalsScore = 0.0;
        if (indicators.getFuturesignals() != null && indicators.getFuturesignals().getAllTimeframesBearish()) {
            futuresignalsScore = scoringConfigService.getFuturesignalQuality();
        } else if (indicators.getFuturesignals() != null && 
                   (indicators.getFuturesignals().getFiveMinBearishSurge() || indicators.getFuturesignals().getOneMinBearishSurge())) {
            futuresignalsScore = scoringConfigService.getFuturesignalQuality() / 2.0;
        }
        // Cap futuresignals score at 10
        futuresignalsScore = Math.min(futuresignalsScore, 10.0);
        
        // Momentum Quality Score (PUT - bearish)
        double momentumScore = 0.0;
        int bearishTimeframes = 0;
        if (Boolean.TRUE.equals(indicators.getEma5_1min_lt_ema34_1min())) bearishTimeframes++;
        if (Boolean.TRUE.equals(indicators.getEma5_5min_lt_ema34_5min())) bearishTimeframes++;
        if (Boolean.TRUE.equals(indicators.getEma5_15min_lt_ema34_15min())) bearishTimeframes++;
        
        if (bearishTimeframes == 3) momentumScore = scoringConfigService.getScoringConfig().getQualityScoring().getMomentumQuality().getPerfectAlignment();
        else if (bearishTimeframes == 2) momentumScore = scoringConfigService.getScoringConfig().getQualityScoring().getMomentumQuality().getMajorityAlignment();
        else if (bearishTimeframes == 1) momentumScore = scoringConfigService.getScoringConfig().getQualityScoring().getMomentumQuality().getSingleAlignment();
        // Cap momentum score at 10
        momentumScore = Math.min(momentumScore, 10.0);
        
        // Candlestick Quality Score (PUT - bearish patterns)
        double candlestickScore = 0.0;
        if (Boolean.TRUE.equals(indicators.getBearish_engulfing_5min())) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getHighReliability();
        if (Boolean.TRUE.equals(indicators.getBearish_engulfing_1min())) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getHighReliability();
        if (Boolean.TRUE.equals(indicators.getBearish_evening_star_5min())) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getHighReliability();
        if (Boolean.TRUE.equals(indicators.getBearish_evening_star_1min())) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getHighReliability();
        if (Boolean.TRUE.equals(indicators.getShooting_star_5min())) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (Boolean.TRUE.equals(indicators.getShooting_star_1min())) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        candlestickScore = Math.min(candlestickScore, scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMaxScore());
        // Cap candlestick score at 10
        candlestickScore = Math.min(candlestickScore, 10.0);
        
        // Calculate average of all component scores (0-10 range)
        double totalScore = emaScore + rsiScore + volumeScore + priceActionScore + 
                           futuresignalsScore + momentumScore + candlestickScore;
        return totalScore / 7.0; // Average of 7 components
    }
    
    private double calculateScenarioScore(Map<String, Integer> categoryScores, 
                                        ScalpingEntryConfig.ScenarioRequirements requirements) {
        double totalScore = 0.0;
        int totalRequirements = 0;
        int totalPossible = 0;
        
        if (requirements.getEma_min_count() != null) {
            int actualCount = categoryScores.getOrDefault("ema", 0);
            totalScore += Math.min(actualCount, requirements.getEma_min_count());
            totalRequirements += requirements.getEma_min_count();
            totalPossible += 3; // 3 possible EMA conditions
        }
        
        if (requirements.getFutureAndVolume_min_count() != null) {
            int actualCount = categoryScores.getOrDefault("futureAndVolume", 0);
            totalScore += Math.min(actualCount, requirements.getFutureAndVolume_min_count());
            totalRequirements += requirements.getFutureAndVolume_min_count();
            totalPossible += 7; // 7 possible Volume conditions
        }
        
        if (requirements.getCandlestick_min_count() != null) {
            int actualCount = categoryScores.getOrDefault("candlestick", 0);
            totalScore += Math.min(actualCount, requirements.getCandlestick_min_count());
            totalRequirements += requirements.getCandlestick_min_count();
            totalPossible += 10; // 10 possible Candlestick conditions
        }
        
        if (requirements.getMomentum_min_count() != null) {
            int actualCount = categoryScores.getOrDefault("momentum", 0);
            totalScore += Math.min(actualCount, requirements.getMomentum_min_count());
            totalRequirements += requirements.getMomentum_min_count();
            totalPossible += 3; // 3 possible Momentum conditions
        }
        
        // Calculate percentage of requirements met
        double requirementPercentage = totalRequirements > 0 ? (totalScore / totalRequirements) : 0.0;
        
        // Calculate percentage of total possible conditions met
        double totalPercentage = totalPossible > 0 ? (totalScore / totalPossible) : 0.0;
        
        // Weighted score: 70% based on requirements met, 30% based on total conditions
        return (requirementPercentage * 0.7 + totalPercentage * 0.3) * 10.0;
    }
    
    // Helper class for scenario evaluation results
    private static class ScenarioEvaluation {
        private String scenarioName;
        private ScalpingEntryConfig.Scenario scenario;
        private boolean passed;
        private double score;
        private Map<String, Integer> categoryScores;
        private Map<String, List<String>> matchedConditions;
        private String reason;
        
        // Getters and setters
        public String getScenarioName() { return scenarioName; }
        public void setScenarioName(String scenarioName) { this.scenarioName = scenarioName; }
        
        public ScalpingEntryConfig.Scenario getScenario() { return scenario; }
        public void setScenario(ScalpingEntryConfig.Scenario scenario) { this.scenario = scenario; }
        
        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
        
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        
        public Map<String, Integer> getCategoryScores() { return categoryScores; }
        public void setCategoryScores(Map<String, Integer> categoryScores) { this.categoryScores = categoryScores; }
        
        public Map<String, List<String>> getMatchedConditions() { return matchedConditions; }
        public void setMatchedConditions(Map<String, List<String>> matchedConditions) { this.matchedConditions = matchedConditions; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
    
    /**
     * Adjust scenario requirements for flat market conditions
     * Increases minimum count requirements using configuration
     */
    private ScalpingEntryConfig.ScenarioRequirements adjustRequirementsForFlatMarket(
            ScalpingEntryConfig.ScenarioRequirements originalRequirements) {
        
        ScalpingEntryConfig.ScenarioRequirements adjusted = new ScalpingEntryConfig.ScenarioRequirements();
        
        // Get configuration
        FlatMarketFilteringConfig config = configService.getFlatMarketFilteringConfig();
        int categoryIncrement = config.getThresholds().getFlatMarketAdjustments().getCategoryIncrement();
        
        // Copy all original values
        adjusted.setEma_min_count(originalRequirements.getEma_min_count());
        adjusted.setFutureAndVolume_min_count(originalRequirements.getFutureAndVolume_min_count());
        adjusted.setCandlestick_min_count(originalRequirements.getCandlestick_min_count());
        adjusted.setMomentum_min_count(originalRequirements.getMomentum_min_count());
        adjusted.setMinQualityScore(originalRequirements.getMinQualityScore());
        adjusted.setFlatMarketFilter(originalRequirements.getFlatMarketFilter());
        adjusted.setMinDirectionalStrength(originalRequirements.getMinDirectionalStrength());
        
        // Increase minimum counts using configuration
        if (adjusted.getEma_min_count() != null) {
            adjusted.setEma_min_count(adjusted.getEma_min_count() + categoryIncrement);
        }
        if (adjusted.getFutureAndVolume_min_count() != null) {
            adjusted.setFutureAndVolume_min_count(adjusted.getFutureAndVolume_min_count() + categoryIncrement);
        }
        if (adjusted.getCandlestick_min_count() != null) {
            adjusted.setCandlestick_min_count(adjusted.getCandlestick_min_count() + categoryIncrement);
        }
        if (adjusted.getMomentum_min_count() != null) {
            adjusted.setMomentum_min_count(adjusted.getMomentum_min_count() + categoryIncrement);
        }
        
        return adjusted;
    }
}
