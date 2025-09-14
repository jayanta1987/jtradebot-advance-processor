package com.jtradebot.processor.service.entry;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.config.ScoringConfigurationService;
import com.jtradebot.processor.model.strategy.DetailedCategoryScore;
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


    public ScalpingEntryDecision evaluateEntry(Tick tick, Map<String, DetailedCategoryScore> detailedCallScores, Map<String, DetailedCategoryScore> detailedPutScores, double qualityScore, UnstableMarketConditionAnalysisService.FlexibleFilteringResult result, 
 String dominantTrend) {
        try {

            // Step 3: Loop through scenarios and check their specific requirements
            List<ScalpingEntryConfig.Scenario> scenarios = configService.getScenarios();
            List<ScenarioEvaluation> scenarioEvaluations = new ArrayList<>();
            
            for (ScalpingEntryConfig.Scenario scenario : scenarios) {
                ScenarioEvaluation evaluation = evaluateScenario(scenario, qualityScore, result, dominantTrend, detailedCallScores, detailedPutScores);
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
                                                String dominantTrend, Map<String, DetailedCategoryScore> detailedCallScores, Map<String, DetailedCategoryScore> detailedPutScores) {

        
        ScenarioEvaluation evaluation = new ScenarioEvaluation();
        evaluation.setScenarioName(scenario.getName());
        evaluation.setScenario(scenario);

            int maxNTPForScenario = configService.getMaxNTPForScenario(scenario.getName());
        
        if (result.getTotalNTP() > maxNTPForScenario) {
            evaluation.setPassed(false);
            evaluation.setScore(0.0);
            evaluation.setReason(result.getReason());
            log.warn("Scenario '{}' failed flexible entry filtering: {}", scenario.getName(), result.getReason());
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
        
        // If scenario only requires quality score (no percentage requirements)
        if (requirements.getMinQualityScore() != null && 
            requirements.getMin_ema_per() == null && 
            requirements.getMin_future_signal_per() == null && 
            requirements.getMin_candlestick_per() == null && 
            requirements.getMin_momentum_per() == null) {
            
            evaluation.setPassed(qualityScorePassed);
            evaluation.setScore(qualityScore);
            evaluation.setReason(qualityScorePassed ? "Quality score requirement met" : 
                               "Quality score " + qualityScore + " below threshold " + minQualityThreshold);
            
            return evaluation;
        }
        
        // Evaluate category-based requirements using weighted scores
        Map<String, List<String>> matchedConditions = new HashMap<>();
        
        // Use the market direction passed as parameter
        boolean isCallDirection = "CALL".equals(dominantTrend);
        
        // Get weighted category scores based on market direction
        Map<String, DetailedCategoryScore> weightedDetailedScores = isCallDirection ? detailedCallScores : detailedPutScores;
        
        // Check percentage-based requirements for each category
        boolean percentageRequirementsPassed = true;
        List<String> failedPercentageCategories = new ArrayList<>();
        
        if (requirements.getMin_ema_per() != null) {
            double emaPercentage = weightedDetailedScores.getOrDefault("ema", new DetailedCategoryScore()).getScorePercentage() != null ? 
                weightedDetailedScores.get("ema").getScorePercentage() : 0.0;
            if (emaPercentage < requirements.getMin_ema_per()) {
                percentageRequirementsPassed = false;
                failedPercentageCategories.add("EMA: " + String.format("%.1f", emaPercentage) + "%/" + requirements.getMin_ema_per() + "%");
            }
        }
        
        if (requirements.getMin_future_signal_per() != null) {
            double futureSignalPercentage = weightedDetailedScores.getOrDefault("futureAndVolume", new DetailedCategoryScore()).getScorePercentage() != null ? 
                weightedDetailedScores.get("futureAndVolume").getScorePercentage() : 0.0;
            if (futureSignalPercentage < requirements.getMin_future_signal_per()) {
                percentageRequirementsPassed = false;
                failedPercentageCategories.add("FV: " + String.format("%.1f", futureSignalPercentage) + "%/" + requirements.getMin_future_signal_per() + "%");
            }
        }
        
        if (requirements.getMin_candlestick_per() != null) {
            double candlestickPercentage = weightedDetailedScores.getOrDefault("candlestick", new DetailedCategoryScore()).getScorePercentage() != null ? 
                weightedDetailedScores.get("candlestick").getScorePercentage() : 0.0;
            if (candlestickPercentage < requirements.getMin_candlestick_per()) {
                percentageRequirementsPassed = false;
                failedPercentageCategories.add("CS: " + String.format("%.1f", candlestickPercentage) + "%/" + requirements.getMin_candlestick_per() + "%");
            }
        }
        
        if (requirements.getMin_momentum_per() != null) {
            double momentumPercentage = weightedDetailedScores.getOrDefault("momentum", new DetailedCategoryScore()).getScorePercentage() != null ? 
                weightedDetailedScores.get("momentum").getScorePercentage() : 0.0;
            if (momentumPercentage < requirements.getMin_momentum_per()) {
                percentageRequirementsPassed = false;
                failedPercentageCategories.add("M: " + String.format("%.1f", momentumPercentage) + "%/" + requirements.getMin_momentum_per() + "%");
            }
        }
        

        
        // Final decision: entry filtering + percentage requirements + quality score
        boolean passed = qualityScorePassed && percentageRequirementsPassed;
        
        evaluation.setPassed(passed);
        evaluation.setScore(passed ? qualityScore : 0.0);
        evaluation.setMatchedConditions(matchedConditions);
        evaluation.setMarketDirection(dominantTrend); // Set the determined market direction
        
        // Build comprehensive reason message
        StringBuilder reason = new StringBuilder();
        if (passed) {
            reason.append("All entry conditions met");
            log.info("🎯 SCENARIO PASSED - '{}' - Quality: {}/{} ({}), Percentages: {}, Market Direction: {}", 
                    scenario.getName(), qualityScore, minQualityThreshold, qualityScorePassed ? "PASS" : "FAIL",
                    getPercentageSummary(weightedDetailedScores), dominantTrend);
        } else {
            List<String> failures = new ArrayList<>();
            if (!qualityScorePassed) {
                failures.add("Quality score " + qualityScore + " below threshold " + minQualityThreshold);
            }
            if (!percentageRequirementsPassed) {
                failures.add("Failed percentages: " + String.join(", ", failedPercentageCategories));
            }
            reason.append(String.join("; ", failures));
            
            log.warn("❌ SCENARIO FAILED - '{}' - Quality: {}/{} ({}), Percentages: {}, Failed: {}", 
                    scenario.getName(), qualityScore, minQualityThreshold, qualityScorePassed ? "PASS" : "FAIL",
                    getPercentageSummary(weightedDetailedScores), String.join(", ", failures));
        }
        evaluation.setReason(reason.toString());
        
        return evaluation;
    }

    /**
     * Helper method to generate percentage summary for logging
     */
    private String getPercentageSummary(Map<String, DetailedCategoryScore> weightedDetailedScores) {
        List<String> percentages = new ArrayList<>();
        
        double emaPercentage = weightedDetailedScores.getOrDefault("ema", new DetailedCategoryScore()).getScorePercentage() != null ? 
            weightedDetailedScores.get("ema").getScorePercentage() : 0.0;
        percentages.add("EMA:" + String.format("%.1f", emaPercentage) + "%");
        
        double futureSignalPercentage = weightedDetailedScores.getOrDefault("futureAndVolume", new DetailedCategoryScore()).getScorePercentage() != null ? 
            weightedDetailedScores.get("futureAndVolume").getScorePercentage() : 0.0;
        percentages.add("FV:" + String.format("%.1f", futureSignalPercentage) + "%");
        
        double candlestickPercentage = weightedDetailedScores.getOrDefault("candlestick", new DetailedCategoryScore()).getScorePercentage() != null ? 
            weightedDetailedScores.get("candlestick").getScorePercentage() : 0.0;
        percentages.add("CS:" + String.format("%.1f", candlestickPercentage) + "%");
        
        double momentumPercentage = weightedDetailedScores.getOrDefault("momentum", new DetailedCategoryScore()).getScorePercentage() != null ? 
            weightedDetailedScores.get("momentum").getScorePercentage() : 0.0;
        percentages.add("M:" + String.format("%.1f", momentumPercentage) + "%");
        
        return String.join(", ", percentages);
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
        private Map<String, Double> categoryScores;
        private Map<String, List<String>> matchedConditions;
        private String reason;
        private String marketDirection; // Store the determined market direction

    }
}
