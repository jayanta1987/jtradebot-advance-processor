package com.jtradebot.processor.service.logging;

import com.jtradebot.processor.model.indicator.EntryQuality;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.strategy.ScalpingEntryDecision;
import com.jtradebot.processor.service.entry.DynamicRuleEvaluatorService;
import com.zerodhatech.models.Tick;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class IndicatorLoggingService {

    private final IndicatorStatusService indicatorStatusService;
    private final MarketDirectionAnalysisService marketDirectionAnalysisService;
    private final DynamicRuleEvaluatorService dynamicRuleEvaluatorService;

    public void logTrendAnalysis(Tick tick, String trendInfo, String marketDirectionInfo) {
        log.info("📊 {} | 💰 {} | {}{}", 
            tick.getTickTimestamp(), 
            tick.getLastTradedPrice(), 
            trendInfo, marketDirectionInfo);
    }

    public void logIndicatorStatus(String emaStatus, String rsiStatus, String volumeStatus, String entryProximity) {
        log.debug("📈 INDICATORS - EMA: {} | RSI: {} | VOL: {} | PROX: {}", 
            emaStatus, rsiStatus, volumeStatus, entryProximity);
    }

    public void logMarketDirection(boolean isCallDominant, boolean isPutDominant) {
        String callStatus = isCallDominant ? "🟢" : "⚫";
        String putStatus = isPutDominant ? "🟢" : "⚫";
        log.debug("🎯 Market Direction - Call: {} Put: {}", callStatus, putStatus);
    }

    /**
     * Log the REAL entry logic from ScalpingVolumeSurgeService - CONCISE ONE-LINER
     */
    public IndicatorLoggingService.EntryAnalysisResult logRealEntryLogicOptimized(Tick indexTick, FlattenedIndicators indicators, boolean isMarketSuitable) {
        try {
            // Step 1: Get entry decision and quality scores
            EntryAnalysisResult analysisResult = analyzeEntryConditions(indexTick, indicators, isMarketSuitable);

            // Step 2: Determine market direction and entry signals
            EntrySignalResult signalResult = determineEntrySignals(analysisResult);

            // Step 3: Log indicator status and trend analysis
            logIndicatorStatusAndTrend(indexTick, indicators, analysisResult, signalResult, isMarketSuitable);

            return analysisResult;
        } catch (Exception e) {
            log.error("Error logging real entry logic: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to log real entry logic", e);
        }
    }


    private void logIndicatorStatusAndTrend(Tick indexTick, FlattenedIndicators indicators, EntryAnalysisResult analysisResult, EntrySignalResult signalResult, boolean isMarketSuitable) {
        // Get essential indicator data with actual values
        String emaStatus = indicatorStatusService.getDetailedEmaStatus(indicators, indexTick);
        String rsiStatus = indicatorStatusService.getDetailedRsiStatus(indicators, indexTick);
        String volumeStatus = indicatorStatusService.getVolumeStatus(indicators);
        String entrySignal = indicatorStatusService.getEntrySignal(signalResult.isShouldCall(), signalResult.isShouldPut());
        String entryProximity = indicatorStatusService.getEntryProximity(indicators, indexTick);

        // Get trend and conditions info
        String trendInfo = marketDirectionAnalysisService.getTrendAndConditionsInfoForLog(
                analysisResult.getEntryDecision(), indicators, indexTick, isMarketSuitable,
                analysisResult.getCallQuality(), analysisResult.getPutQuality());

        // Log indicator status
        logIndicatorStatus(emaStatus, rsiStatus, volumeStatus, entryProximity);

        // Log market direction
        logMarketDirection(signalResult.isCallDominant(), signalResult.isPutDominant());

        // LOG: Show trend analysis with category conditions (market direction removed)
        String marketDirectionInfo = "";

        logTrendAnalysis(indexTick, trendInfo, marketDirectionInfo);
    }

    private EntryAnalysisResult analyzeEntryConditions(Tick indexTick, FlattenedIndicators indicators, Boolean preCalculatedMarketCondition) {
        // Use NEW scenario-based entry logic instead of old StrategyScore approach
        ScalpingEntryDecision entryDecision = dynamicRuleEvaluatorService.getEntryDecision(indexTick, indicators, preCalculatedMarketCondition);

        // 🔥 OPTIMIZATION: Calculate entry quality scores ONCE to avoid redundant calculations
        EntryQuality callQuality = dynamicRuleEvaluatorService.evaluateCallEntryQuality(indicators, indexTick);
        EntryQuality putQuality = dynamicRuleEvaluatorService.evaluatePutEntryQuality(indicators, indexTick);

        return new EntryAnalysisResult(entryDecision, callQuality, putQuality);
    }

    /**
     * Step 2: Determine entry signals
     */
    private EntrySignalResult determineEntrySignals(EntryAnalysisResult analysisResult) {
        boolean scenarioPassed = analysisResult.getEntryDecision().isShouldEntry();

        // Use the quality score from entryDecision to avoid duplicate calculations
        double qualityScore = analysisResult.getEntryDecision().getQualityScore();

        // For now, use a simple heuristic for direction - in a real implementation,
        // you'd want to store both call and put quality scores in the entryDecision
        // For now, assume CALL direction if we have a valid quality score
        boolean isCallDominant = qualityScore > 0; // BIG_ISSUE_NEED_FIX
        boolean isPutDominant = false; // BIG_ISSUE_NEED_FIX

        // Only create orders if scenario passes AND dominant direction is clear
        boolean shouldCall = scenarioPassed && isCallDominant;
        boolean shouldPut = scenarioPassed && isPutDominant;

        log.debug("🔍 SIGNAL DETERMINATION - Scenario: {}, QualityScore: {}, ScenarioPassed: {}, CallDominant: {}, PutDominant: {}, ShouldCall: {}, ShouldPut: {}",
                analysisResult.getEntryDecision().getScenarioName(), qualityScore, scenarioPassed, isCallDominant, isPutDominant, shouldCall, shouldPut);

        return new EntrySignalResult(shouldCall, shouldPut, isCallDominant, isPutDominant);
    }


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
