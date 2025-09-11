package com.jtradebot.processor.service;

import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.response.MarketStatusResponse;
import com.jtradebot.processor.model.strategy.DetailedCategoryScore;
import com.jtradebot.processor.service.analysis.MarketDirectionService;
import com.jtradebot.processor.service.entry.DynamicRuleEvaluatorService;
import com.jtradebot.processor.service.entry.UnstableMarketConditionAnalysisService;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service to provide current market status information
 * Uses existing data from TickOrchestrationService without recalculating
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketStatusService {
    
    private final TickDataManager tickDataManager;
    private final KiteInstrumentHandler kiteInstrumentHandler;
    private final DynamicRuleEvaluatorService dynamicRuleEvaluatorService;
    private final MarketDirectionService marketDirectionService;
    private final UnstableMarketConditionAnalysisService unstableMarketConditionAnalysisService;
    
    /**
     * Get current market status with all scores and filters
     * Uses cached data from TickOrchestrationService processing
     */
    public MarketStatusResponse getCurrentMarketStatus() {
        try {
            // Get Nifty token
            String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();
            
            // Get latest tick
            Tick latestTick = tickDataManager.getLastTick(niftyToken);
            if (latestTick == null) {
                log.warn("No latest tick available for market status");
                return MarketStatusResponse.builder()
                        .instrumentToken(niftyToken)
                        .timestamp(LocalDateTime.now())
                        .eligibleForEntry(false)
                        .entryEligibilityReason("No tick data available")
                        .build();
            }
            
            // Get current indicators (uses cache from DynamicRuleEvaluatorService)
            FlattenedIndicators indicators = dynamicRuleEvaluatorService.getFlattenedIndicators(latestTick);
            
            // Check filtering conditions
            UnstableMarketConditionAnalysisService.FlexibleFilteringResult filteringResult = 
                    unstableMarketConditionAnalysisService.checkFlexibleFilteringConditions(latestTick, indicators);
            
            // Get detailed category scores
            Map<String, DetailedCategoryScore> callScores = marketDirectionService.getDetailedCategoryScores(indicators, "CALL");
            Map<String, DetailedCategoryScore> putScores = marketDirectionService.getDetailedCategoryScores(indicators, "PUT");
            
            // Calculate totals
            double callTotal = callScores.values().stream().mapToDouble(DetailedCategoryScore::getTotalScore).sum();
            double putTotal = putScores.values().stream().mapToDouble(DetailedCategoryScore::getTotalScore).sum();
            double qualityScore = calculateQualityScore(callTotal, putTotal);
            String dominantTrend = callTotal > putTotal ? "CALL" : "PUT";
            
            // Determine entry eligibility
            boolean eligibleForEntry = isEligibleForEntryCheck(qualityScore, filteringResult.isConditionsMet());
            String entryEligibilityReason = getEntryEligibilityReason(qualityScore, filteringResult.isConditionsMet());
            
            // Build market condition details
            Map<String, Object> marketConditionDetails = new HashMap<>();
            marketConditionDetails.put("filteringResult", filteringResult);
            marketConditionDetails.put("qualityScore", qualityScore);
            marketConditionDetails.put("dominantTrend", dominantTrend);
            marketConditionDetails.put("callTotal", callTotal);
            marketConditionDetails.put("putTotal", putTotal);
            
            // Additional metrics
            Map<String, Object> additionalMetrics = new HashMap<>();
            additionalMetrics.put("lastTickTime", latestTick.getTickTimestamp());
            additionalMetrics.put("instrumentToken", latestTick.getInstrumentToken());
            additionalMetrics.put("lastTradedPrice", latestTick.getLastTradedPrice());
            additionalMetrics.put("volume", latestTick.getVolumeTradedToday());
            additionalMetrics.put("oi", latestTick.getOi());
            
            return MarketStatusResponse.builder()
                    .instrumentToken(niftyToken)
                    .timestamp(LocalDateTime.now())
                    .currentPrice(latestTick.getLastTradedPrice())
                    .marketSession("LIVE") // Could be enhanced to determine actual session
                    .inTradingZone(filteringResult.isConditionsMet())
                    .filteringResult(filteringResult)
                    .callCategoryScores(callScores)
                    .putCategoryScores(putScores)
                    .callTotalScore(callTotal)
                    .putTotalScore(putTotal)
                    .qualityScore(qualityScore)
                    .dominantTrend(dominantTrend)
                    .currentIndicators(indicators)
                    .marketConditionDetails(marketConditionDetails)
                    .eligibleForEntry(eligibleForEntry)
                    .entryEligibilityReason(entryEligibilityReason)
                    .additionalMetrics(additionalMetrics)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error getting current market status: {}", e.getMessage(), e);
            return MarketStatusResponse.builder()
                    .timestamp(LocalDateTime.now())
                    .eligibleForEntry(false)
                    .entryEligibilityReason("Error retrieving market status: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * Calculate quality score based on call and put totals
     * Same logic as in TickOrchestrationService
     */
    private double calculateQualityScore(double callTotal, double putTotal) {
        double totalScore = callTotal + putTotal;
        if (totalScore == 0) {
            return 0.0;
        }
        return Math.max(callTotal, putTotal) / totalScore;
    }
    
    /**
     * Check if market is eligible for entry
     * Same logic as in TickOrchestrationService
     */
    private boolean isEligibleForEntryCheck(double qualityScore, boolean inTradingZone) {
        return qualityScore >= 0.6 && inTradingZone;
    }
    
    /**
     * Get reason for entry eligibility
     */
    private String getEntryEligibilityReason(double qualityScore, boolean inTradingZone) {
        if (!inTradingZone) {
            return "Not in trading zone - market conditions not suitable";
        }
        if (qualityScore < 0.6) {
            return String.format("Quality score too low: %.2f (minimum required: 0.60)", qualityScore);
        }
        return "Eligible for entry - all conditions met";
    }
}
