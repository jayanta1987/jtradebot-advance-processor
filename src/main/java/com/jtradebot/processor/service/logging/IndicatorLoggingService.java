package com.jtradebot.processor.service.logging;

import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.zerodhatech.models.Tick;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class IndicatorLoggingService {

    /**
     * Log trend analysis with market direction
     */
    public void logTrendAnalysis(Tick tick, String trendInfo, String marketDirectionInfo) {
        log.info("📊 {} | 💰 {} | {}{}", 
            tick.getTickTimestamp(), 
            tick.getLastTradedPrice(), 
            trendInfo, marketDirectionInfo);
    }

    /**
     * Log indicator status summary
     */
    public void logIndicatorStatus(String emaStatus, String rsiStatus, String volumeStatus, String entryProximity) {
        log.debug("📈 INDICATORS - EMA: {} | RSI: {} | VOL: {} | PROX: {}", 
            emaStatus, rsiStatus, volumeStatus, entryProximity);
    }

    /**
     * Log EMA status details
     */
    public void logEmaStatus(String emaStatus) {
        log.debug("📊 EMA Status: {}", emaStatus);
    }

    /**
     * Log RSI status details
     */
    public void logRsiStatus(String rsiStatus) {
        log.debug("📊 RSI Status: {}", rsiStatus);
    }

    /**
     * Log volume status details
     */
    public void logVolumeStatus(String volumeStatus) {
        log.debug("📊 Volume Status: {}", volumeStatus);
    }

    /**
     * Log entry proximity details
     */
    public void logEntryProximity(String entryProximity) {
        log.debug("📊 Entry Proximity: {}", entryProximity);
    }

    /**
     * Log market direction analysis
     */
    public void logMarketDirection(boolean isCallDominant, boolean isPutDominant) {
        String callStatus = isCallDominant ? "🟢" : "⚫";
        String putStatus = isPutDominant ? "🟢" : "⚫";
        log.debug("🎯 Market Direction - Call: {} Put: {}", callStatus, putStatus);
    }

    /**
     * Log bullish market conditions
     */
    public void logBullishConditions(int bullishSignals, int totalSignals) {
        log.debug("📈 Bullish Signals: {}/{} ({:.1%})", bullishSignals, totalSignals, (double) bullishSignals / totalSignals);
    }

    /**
     * Log bearish market conditions
     */
    public void logBearishConditions(int bearishSignals, int totalSignals) {
        log.debug("📉 Bearish Signals: {}/{} ({:.1%})", bearishSignals, totalSignals, (double) bearishSignals / totalSignals);
    }

    /**
     * Log scenario evaluation
     */
    public void logScenarioEvaluation(String scenarioName, double confidence, boolean passed) {
        String status = passed ? "✅" : "❌";
        log.debug("🎯 Scenario: {} - Confidence: {}/10 - Status: {}", scenarioName, confidence, status);
    }

    /**
     * Log category scores
     */
    public void logCategoryScores(String category, int score, int required, boolean passed) {
        String status = passed ? "✅" : "❌";
        log.debug("📊 Category {}: {}/{} - Status: {}", category, score, required, status);
    }

    /**
     * Log quality score evaluation
     */
    public void logQualityScore(double actualScore, double requiredScore, boolean passed) {
        String status = passed ? "✅" : "❌";
        log.debug("📊 Quality Score: {}/{} - Required: {} - Status: {}", actualScore, requiredScore, requiredScore, status);
    }

    /**
     * Log flat market filtering
     */
    public void logFlatMarketFiltering(boolean isMarketSuitable, String reason) {
        if (!isMarketSuitable) {
            log.debug("⚠️ Flat Market Filtering: Market unsuitable - {}", reason);
        } else {
            log.debug("✅ Flat Market Filtering: Market suitable");
        }
    }

    /**
     * Log indicator calculation error
     */
    public void logIndicatorCalculationError(String indicatorType, Exception e) {
        log.debug("Error calculating {} indicator: {}", indicatorType, e.getMessage());
    }

    /**
     * Log indicator data missing
     */
    public void logIndicatorDataMissing(String indicatorType) {
        log.debug("⚠️ {} indicator data missing", indicatorType);
    }

    /**
     * Log entry signal summary
     */
    public void logEntrySignalSummary(boolean shouldCall, boolean shouldPut, String entrySignal) {
        log.debug("🎯 Entry Signal Summary - Should Call: {}, Should Put: {}, Signal: {}", 
            shouldCall, shouldPut, entrySignal);
    }

    /**
     * Log dominant trend analysis
     */
    public void logDominantTrend(String dominantTrend, double dominantQuality) {
        log.debug("🎯 Dominant Trend: {} (Quality: {}/10)", dominantTrend, dominantQuality);
    }

    /**
     * Log scenario requirements check
     */
    public void logScenarioRequirementsCheck(String scenarioName, boolean allRequirementsMet) {
        String status = allRequirementsMet ? "✅" : "❌";
        log.debug("🎯 Scenario {} Requirements: {} - All Met: {}", scenarioName, status, allRequirementsMet);
    }

    /**
     * Log category count details
     */
    public void logCategoryCountDetails(String category, int count, int required, String adjustment) {
        String adjustmentText = adjustment.isEmpty() ? "" : " " + adjustment;
        log.debug("📊 Category {} Count: {}/{} - Required: {}{}", category, count, required, required, adjustmentText);
    }

    /**
     * Log market condition summary
     */
    public void logMarketConditionSummary(boolean isBullish, boolean isBearish, boolean isMarketSuitable) {
        log.debug("📊 Market Conditions - Bullish: {}, Bearish: {}, Suitable: {}", 
            isBullish, isBearish, isMarketSuitable);
    }

    /**
     * Log entry proximity calculation
     */
    public void logEntryProximityCalculation(String direction, double proximity, String intensity) {
        log.debug("📊 Entry Proximity - Direction: {}, Proximity: {:.0f}%, Intensity: {}", 
            direction, proximity, intensity);
    }

    /**
     * Log indicator status error
     */
    public void logIndicatorStatusError(String indicatorType, Exception e) {
        log.debug("Error getting {} status: {}", indicatorType, e.getMessage());
    }
}
