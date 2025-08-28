package com.jtradebot.processor.service.logging;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.model.indicator.EntryQuality;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.strategy.ScalpingEntryDecision;
import com.jtradebot.processor.service.entry.DynamicRuleEvaluatorService;
import com.jtradebot.processor.service.analysis.SignalDeterminationService;
import com.jtradebot.processor.service.analysis.MarketDirectionService;
import com.zerodhatech.models.Tick;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Unified service that consolidates indicator logging, status formatting, and market direction analysis.
 * This service uses pre-calculated indicators without performing any extra calculations.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UnifiedIndicatorService {

    private final DynamicRuleEvaluatorService dynamicRuleEvaluatorService;
    private final SignalDeterminationService signalDeterminationService;
    private final DynamicStrategyConfigService configService;
    private final MarketDirectionService marketDirectionService;

    /**
     * Main method to log all indicator information using pre-calculated data
     */
    public void logUnifiedIndicatorAnalysis(Tick indexTick, FlattenedIndicators indicators, boolean isMarketSuitable) {
        try {
            // Step 1: Get entry decision and quality scores (using pre-calculated indicators)
            EntryAnalysisResult analysisResult = analyzeEntryConditions(indexTick, indicators, isMarketSuitable);

            // Step 2: Determine entry signals using unified logic
            EntrySignalResult signalResult = determineEntrySignals(analysisResult);

            // Step 3: Log all information using pre-calculated data
            logAllIndicatorInformation(indexTick, indicators, analysisResult, signalResult, isMarketSuitable);

        } catch (Exception e) {
            log.error("Error in unified indicator analysis: {}", e.getMessage(), e);
        }
    }

    /**
     * Analyze entry conditions using pre-calculated indicators
     */
    private EntryAnalysisResult analyzeEntryConditions(Tick indexTick, FlattenedIndicators indicators, Boolean preCalculatedMarketCondition) {
        // Use pre-calculated entry decision
        ScalpingEntryDecision entryDecision = dynamicRuleEvaluatorService.getEntryDecision(indexTick, indicators, preCalculatedMarketCondition);

        // Use pre-calculated quality scores
        EntryQuality callQuality = dynamicRuleEvaluatorService.evaluateCallEntryQuality(indicators, indexTick);
        EntryQuality putQuality = dynamicRuleEvaluatorService.evaluatePutEntryQuality(indicators, indexTick);

        return new EntryAnalysisResult(entryDecision, callQuality, putQuality);
    }

    /**
     * Determine entry signals using unified logic
     */
    private EntrySignalResult determineEntrySignals(EntryAnalysisResult analysisResult) {
        // Use SignalDeterminationService for consistent signal determination
        SignalDeterminationService.SignalResult signalResult = signalDeterminationService.determineSignals(analysisResult.getEntryDecision());

        log.debug("🔍 UNIFIED SIGNAL DETERMINATION - Scenario: {}, MarketDirection: {}, ShouldCall: {}, ShouldPut: {}",
                analysisResult.getEntryDecision().getScenarioName(), 
                analysisResult.getEntryDecision().getMarketDirection(),
                signalResult.isShouldCall(), signalResult.isShouldPut());

        return new EntrySignalResult(signalResult.isShouldCall(), signalResult.isShouldPut(), 
                                   signalResult.isCallDominant(), signalResult.isPutDominant());
    }

    /**
     * Log all indicator information using pre-calculated data
     */
    private void logAllIndicatorInformation(Tick indexTick, FlattenedIndicators indicators, EntryAnalysisResult analysisResult, EntrySignalResult signalResult, boolean isMarketSuitable) {

        // Get trend information using pre-calculated data
        String trendInfo = getTrendInfoFromPreCalculated(analysisResult, indicators, isMarketSuitable);

        // Log trend analysis
        logTrendAnalysis(indexTick, trendInfo);
    }

    /**
     * Get trend info from pre-calculated data
     */
    private String getTrendInfoFromPreCalculated(EntryAnalysisResult analysisResult, FlattenedIndicators indicators, boolean isMarketSuitable) {
        try {
            ScalpingEntryDecision entryDecision = analysisResult.getEntryDecision();
            
            // Use scenario information from entry decision
            if (entryDecision.isShouldEntry() && entryDecision.getScenarioName() != null) {
                String scenarioInfo = String.format("Scenario: %s (%.1f/10)", 
                    entryDecision.getScenarioName(), entryDecision.getQualityScore());
                
                if (entryDecision.getCategoryScores() != null) {
                    Map<String, Integer> categoryScores = entryDecision.getCategoryScores();
                    StringBuilder categoryInfo = new StringBuilder();
                    
                    if (categoryScores.containsKey("ema")) {
                        categoryInfo.append(String.format("EMA:%d ", categoryScores.get("ema")));
                    }
                    if (categoryScores.containsKey("futureAndVolume")) {
                        categoryInfo.append(String.format("FV:%d ", categoryScores.get("futureAndVolume")));
                    }
                    if (categoryScores.containsKey("candlestick")) {
                        categoryInfo.append(String.format("CS:%d ", categoryScores.get("candlestick")));
                    }
                    if (categoryScores.containsKey("momentum")) {
                        categoryInfo.append(String.format("M:%d ", categoryScores.get("momentum")));
                    }
                    
                    return String.format("🎯 %s | %s", scenarioInfo, categoryInfo.toString().trim());
                } else {
                    return String.format("🎯 %s", scenarioInfo);
                }
            } else {
                // Show quality-based evaluation when no scenario is triggered
                String dominantTrend = analysisResult.getCallQuality().getQualityScore() > analysisResult.getPutQuality().getQualityScore() ? "CALL" : "PUT";
                double dominantQuality = Math.max(analysisResult.getCallQuality().getQualityScore(), analysisResult.getPutQuality().getQualityScore());
                
                // Get category breakdown with requirements
                String categoryBreakdown = getCategoryBreakdownWithRequirements(entryDecision, indicators);
                
                return String.format("🎯 %s (%.1f/10) | %s", dominantTrend, dominantQuality, categoryBreakdown);
            }
            
        } catch (Exception e) {
            return "🎯 ERROR";
        }
    }
    
    /**
     * Get category breakdown with requirements
     */
    private String getCategoryBreakdownWithRequirements(ScalpingEntryDecision entryDecision, FlattenedIndicators indicators) {
        try {
            // Get requirements from SAFE_ENTRY_SIGNAL scenario
            var scenario = configService.getScenarioByName("SAFE_ENTRY_SIGNAL");
            if (scenario == null || scenario.getRequirements() == null) {
                return "";
            }
            
            var requirements = scenario.getRequirements();
            int emaRequired = requirements.getEma_min_count() != null ? requirements.getEma_min_count() : 0;
            int fvRequired = requirements.getFutureAndVolume_min_count() != null ? requirements.getFutureAndVolume_min_count() : 0;
            int csRequired = requirements.getCandlestick_min_count() != null ? requirements.getCandlestick_min_count() : 0;
            int mRequired = requirements.getMomentum_min_count() != null ? requirements.getMomentum_min_count() : 0;
            
            // Get actual category scores for both directions from MarketDirectionService
            Map<String, Integer> callCategoryCounts = marketDirectionService.getCategoryScores(indicators, "CALL");
            Map<String, Integer> putCategoryCounts = marketDirectionService.getCategoryScores(indicators, "PUT");
            
            // Format the breakdown with actual scores for both directions
            String callEma = callCategoryCounts.getOrDefault("ema", 0) + "/" + emaRequired;
            String callFv = callCategoryCounts.getOrDefault("futureAndVolume", 0) + "/" + fvRequired;
            String callCs = callCategoryCounts.getOrDefault("candlestick", 0) + "/" + csRequired;
            String callM = callCategoryCounts.getOrDefault("momentum", 0) + "/" + mRequired;
            
            String putEma = putCategoryCounts.getOrDefault("ema", 0) + "/" + emaRequired;
            String putFv = putCategoryCounts.getOrDefault("futureAndVolume", 0) + "/" + fvRequired;
            String putCs = putCategoryCounts.getOrDefault("candlestick", 0) + "/" + csRequired;
            String putM = putCategoryCounts.getOrDefault("momentum", 0) + "/" + mRequired;
            
            return String.format("Call: EMA=%s, FV=%s, CS=%s, M=%s | Put: EMA=%s, FV=%s, CS=%s, M=%s", 
                    callEma, callFv, callCs, callM, putEma, putFv, putCs, putM);
            
        } catch (Exception e) {
            return "";
        }
    }


    /**
     * Log trend analysis
     */
    private void logTrendAnalysis(Tick tick, String trendInfo) {
        log.info("📊 {} | 💰 {} | {}", 
            tick.getTickTimestamp(), 
            tick.getLastTradedPrice(), 
            trendInfo);
    }

    /**
     * Result classes for internal use
     */
    @Getter
    public static class EntryAnalysisResult {
        private final ScalpingEntryDecision entryDecision;
        private final EntryQuality callQuality;
        private final EntryQuality putQuality;

        public EntryAnalysisResult(ScalpingEntryDecision entryDecision, EntryQuality callQuality, EntryQuality putQuality) {
            this.entryDecision = entryDecision;
            this.callQuality = callQuality;
            this.putQuality = putQuality;
        }
    }

    @Getter
    public static class EntrySignalResult {
        private final boolean shouldCall;
        private final boolean shouldPut;
        private final boolean isCallDominant;
        private final boolean isPutDominant;
        
        public EntrySignalResult(boolean shouldCall, boolean shouldPut, boolean isCallDominant, boolean isPutDominant) {
            this.shouldCall = shouldCall;
            this.shouldPut = shouldPut;
            this.isCallDominant = isCallDominant;
            this.isPutDominant = isPutDominant;
        }
    }
}
