package com.jtradebot.processor.service.entry;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.config.ScoringConfigurationService;
import com.jtradebot.processor.model.strategy.ScalpingEntryConfig;
import com.jtradebot.processor.model.strategy.ScalpingEntryDecision;
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
    private final SignalDeterminationService signalDeterminationService;


    public ScalpingEntryDecision evaluateEntry(Tick tick, Map<String, Integer> callScores, Map<String, Integer> putScores,
                                               Double qualityScore, UnstableMarketConditionAnalysisService.FlexibleFilteringResult result, String dominantTrend) {
        try {

            // Step 3: Loop through scenarios and check their specific requirements
            List<ScalpingEntryConfig.Scenario> scenarios = configService.getScenarios();
            List<ScenarioEvaluation> scenarioEvaluations = new ArrayList<>();
            
            for (ScalpingEntryConfig.Scenario scenario : scenarios) {
                ScenarioEvaluation evaluation = evaluateScenario(scenario, qualityScore, result, dominantTrend, callScores, putScores);
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
                        .marketDirection(dominantTrend)
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
                                                double qualityScore,
                                                UnstableMarketConditionAnalysisService.FlexibleFilteringResult result,
                                                String dominantTrend, Map<String, Integer> callScores, Map<String, Integer> putScores) {

        
        ScenarioEvaluation evaluation = new ScenarioEvaluation();
        evaluation.setScenarioName(scenario.getName());
        evaluation.setScenario(scenario);
        
        if (!result.isConditionsMet()) {
            evaluation.setPassed(false);
            evaluation.setScore(0.0);
            
            // Get detailed filtering result for logging
            if (configService.isNoTradeZonesEnabled()) {
                evaluation.setReason(result.getReason());
                log.warn("Scenario '{}' failed flexible entry filtering: {}", scenario.getName(), result.getReason());
            } else {
                evaluation.setReason(result.getReason());
                log.warn("Scenario '{}' failed entry filtering: {}", scenario.getName(), result.getReason());
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
            qualityScorePassed = qualityScore >= minQualityThreshold;
            
            if (!qualityScorePassed) {
                log.debug("Quality score {} below threshold {} for scenario {}",
                        qualityScore, minQualityThreshold, scenario.getName());
            }
        }
        
        // If scenario only requires quality score (no category requirements)
        if (requirements.getMinQualityScore() != null && 
            requirements.getEma_min_score() == null && 
            requirements.getFutureAndVolume_min_score() == null && 
            requirements.getCandlestick_min_score() == null && 
            requirements.getMomentum_min_score() == null) {
            
            evaluation.setPassed(qualityScorePassed);
            evaluation.setScore(qualityScore);
            evaluation.setReason(qualityScorePassed ? "Quality score requirement met" : 
                               "Quality score " + qualityScore + " below threshold " + minQualityThreshold);
            
            return evaluation;
        }
        
        // Evaluate category-based requirements using weighted scores
        Map<String, Integer> categoryScores = new HashMap<>();
        Map<String, List<String>> matchedConditions = new HashMap<>();
        
        // Use the market direction passed as parameter
        boolean isCallDirection = "CALL".equals(dominantTrend);
        
        // Get weighted category scores based on market direction
        Map<String, Integer> weightedCategoryScores = isCallDirection ? callScores :putScores;
        
        // Use weighted scores for category validation
        if (requirements.getEma_min_score() != null) {
            int emaScore = weightedCategoryScores.getOrDefault("ema", 0);
            categoryScores.put("ema", emaScore);
        }
        
        if (requirements.getFutureAndVolume_min_score() != null) {
            int volumeScore = weightedCategoryScores.getOrDefault("futureAndVolume", 0);
            categoryScores.put("futureAndVolume", volumeScore);
        }
        
        if (requirements.getCandlestick_min_score() != null) {
            int candlestickScore = weightedCategoryScores.getOrDefault("candlestick", 0);
            categoryScores.put("candlestick", candlestickScore);
        }
        
        if (requirements.getMomentum_min_score() != null) {
            int momentumScore = weightedCategoryScores.getOrDefault("momentum", 0);
            categoryScores.put("momentum", momentumScore);
        }
        

        
        // Keep category-based checks for entry decisions
        boolean categoryRequirementsPassed = true;
        List<String> failedCategories = new ArrayList<>();
        
        if (requirements.getEma_min_score() != null && 
            categoryScores.get("ema") < requirements.getEma_min_score()) {
            categoryRequirementsPassed = false;
            failedCategories.add("EMA: " + categoryScores.get("ema") + "/" + requirements.getEma_min_score());
        }
        
        if (requirements.getFutureAndVolume_min_score() != null && 
            categoryScores.get("futureAndVolume") < requirements.getFutureAndVolume_min_score()) {
            categoryRequirementsPassed = false;
            failedCategories.add("FV: " + categoryScores.get("futureAndVolume") + "/" + requirements.getFutureAndVolume_min_score());
        }
        
        if (requirements.getCandlestick_min_score() != null && 
            categoryScores.get("candlestick") < requirements.getCandlestick_min_score()) {
            categoryRequirementsPassed = false;
            failedCategories.add("CS: " + categoryScores.get("candlestick") + "/" + requirements.getCandlestick_min_score());
        }
        
        if (requirements.getMomentum_min_score() != null && 
            categoryScores.get("momentum") < requirements.getMomentum_min_score()) {
            categoryRequirementsPassed = false;
            failedCategories.add("M: " + categoryScores.get("momentum") + "/" + requirements.getMomentum_min_score());
        }
        

        
        // Final decision: entry filtering + category requirements + quality score
        boolean passed = qualityScorePassed && categoryRequirementsPassed;
        
        evaluation.setPassed(passed);
        evaluation.setScore(passed ? qualityScore : 0.0);
        evaluation.setCategoryScores(categoryScores);
        evaluation.setMatchedConditions(matchedConditions);
        evaluation.setMarketDirection(dominantTrend); // Set the determined market direction
        
        // Build comprehensive reason message
        StringBuilder reason = new StringBuilder();
        if (passed) {
            reason.append("All entry conditions met");
            log.info("🎯 SCENARIO PASSED - '{}' - Quality: {}/{} ({}), Categories: {}, Market Direction: {}", 
                    scenario.getName(), qualityScore, minQualityThreshold, qualityScorePassed ? "PASS" : "FAIL",
                    categoryScores, dominantTrend);
        } else {
            List<String> failures = new ArrayList<>();
            if (!qualityScorePassed) {
                failures.add("Quality score " + qualityScore + " below threshold " + minQualityThreshold);
            }
            if (!categoryRequirementsPassed) {
                failures.add("Failed categories: " + String.join(", ", failedCategories));
            }
            reason.append(String.join("; ", failures));
            
            log.warn("❌ SCENARIO FAILED - '{}' - Quality: {}/{} ({}), Categories: {}, Failed: {}", 
                    scenario.getName(), qualityScore, minQualityThreshold, qualityScorePassed ? "PASS" : "FAIL",
                    categoryScores, String.join(", ", failures));
        }
        evaluation.setReason(reason.toString());
        
        return evaluation;
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
