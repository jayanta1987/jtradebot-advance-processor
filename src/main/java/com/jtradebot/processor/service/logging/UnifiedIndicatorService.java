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
 * Simplified service that provides indicator analysis utilities.
 * Main logging has been moved to TickOrchestrationService.
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
     * Simplified method - main logging moved to TickOrchestrationService
     */
    public void logUnifiedIndicatorAnalysis(Tick indexTick, FlattenedIndicators indicators, boolean isMarketSuitable) {
        // Main logging moved to TickOrchestrationService
        log.debug("UnifiedIndicatorService called - logging handled by TickOrchestrationService");
    }

    /**
     * Get entry decision and quality scores for external use
     */
    public EntryAnalysisResult getEntryAnalysis(Tick indexTick, FlattenedIndicators indicators, Boolean preCalculatedMarketCondition) {
        try {
            // Use pre-calculated entry decision
            ScalpingEntryDecision entryDecision = dynamicRuleEvaluatorService.getEntryDecision(indexTick, indicators, preCalculatedMarketCondition);

            // Use pre-calculated quality scores
            EntryQuality callQuality = dynamicRuleEvaluatorService.evaluateCallEntryQuality(indicators, indexTick);
            EntryQuality putQuality = dynamicRuleEvaluatorService.evaluatePutEntryQuality(indicators, indexTick);

            return new EntryAnalysisResult(entryDecision, callQuality, putQuality);
        } catch (Exception e) {
            log.error("Error getting entry analysis: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get entry signals for external use
     */
    public EntrySignalResult getEntrySignals(EntryAnalysisResult analysisResult) {
        try {
            // Use SignalDeterminationService for consistent signal determination
            SignalDeterminationService.SignalResult signalResult = signalDeterminationService.determineSignals(analysisResult.getEntryDecision());

            return new EntrySignalResult(signalResult.isShouldCall(), signalResult.isShouldPut(), 
                                       signalResult.isCallDominant(), signalResult.isPutDominant());
        } catch (Exception e) {
            log.error("Error getting entry signals: {}", e.getMessage(), e);
            return new EntrySignalResult(false, false, false, false);
        }
    }

    /**
     * Result classes for external use
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
