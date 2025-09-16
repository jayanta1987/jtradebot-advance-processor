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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    
    // Cache to track last 10 volume and OI values for delta calculation
    private final Map<String, List<Long>> volumeHistoryMap = new ConcurrentHashMap<>();
    private final Map<String, List<Double>> oiHistoryMap = new ConcurrentHashMap<>();
    private static final int HISTORY_SIZE = 10;
    
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
            
            // Calculate volume and OI deltas for better market insight
            // Index ticks (like Nifty50) don't have meaningful volume/OI data, so use future data
            if (isFutureInstrument(latestTick.getInstrumentToken())) {
                // Use current tick data for future instruments
                calculateAndAddVolumeOIDeltas(additionalMetrics, latestTick, String.valueOf(latestTick.getInstrumentToken()));
            } else {
                // For index instruments, get volume and OI deltas from corresponding future
                try {
                    String niftyFutureToken = kiteInstrumentHandler.getNifty50FutureToken().toString();
                    Tick futureTick = tickDataManager.getLastTick(niftyFutureToken);
                    if (futureTick != null) {
                        calculateAndAddVolumeOIDeltas(additionalMetrics, futureTick, niftyFutureToken);
                        additionalMetrics.put("futureInstrumentToken", futureTick.getInstrumentToken());
                    } else {
                        additionalMetrics.put("volumeDelta", 0L);
                        additionalMetrics.put("oiDelta", 0.0);
                        additionalMetrics.put("volume", 0L);
                        additionalMetrics.put("oi", 0.0);
                        additionalMetrics.put("note", "No future tick data available");
                    }
                } catch (Exception e) {
                    log.warn("Could not get future tick data for volume/OI: {}", e.getMessage());
                    additionalMetrics.put("volumeDelta", 0L);
                    additionalMetrics.put("oiDelta", 0.0);
                    additionalMetrics.put("volume", 0L);
                    additionalMetrics.put("oi", 0.0);
                    additionalMetrics.put("note", "Error retrieving future tick data");
                }
            }
            
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
    
    /**
     * Calculate and add volume and OI deltas to additional metrics
     * Deltas are calculated over the last 10 ticks for better trend analysis
     */
    private void calculateAndAddVolumeOIDeltas(Map<String, Object> additionalMetrics, Tick tick, String instrumentToken) {
        try {
            // Get current values
            long currentVolume = tick.getVolumeTradedToday();
            double currentOI = tick.getOi();
            
            // Get or create history lists
            List<Long> volumeHistory = volumeHistoryMap.computeIfAbsent(instrumentToken, k -> new ArrayList<>());
            List<Double> oiHistory = oiHistoryMap.computeIfAbsent(instrumentToken, k -> new ArrayList<>());
            
            // Add current values to history
            volumeHistory.add(currentVolume);
            oiHistory.add(currentOI);
            
            // Keep only last 10 values
            if (volumeHistory.size() > HISTORY_SIZE) {
                volumeHistory.remove(0);
            }
            if (oiHistory.size() > HISTORY_SIZE) {
                oiHistory.remove(0);
            }
            
            // Calculate deltas based on available history
            long volumeDelta = 0L;
            double oiDelta = 0.0;
            String volumeTrend = "Stable";
            String oiTrend = "Stable";
            
            if (volumeHistory.size() >= 2) {
                // Calculate delta from first to last value in history
                long oldestVolume = volumeHistory.get(0);
                volumeDelta = currentVolume - oldestVolume;
                volumeTrend = getVolumeTrendDescription(volumeDelta);
            }
            
            if (oiHistory.size() >= 2) {
                // Calculate delta from first to last value in history
                double oldestOI = oiHistory.get(0);
                oiDelta = currentOI - oldestOI;
                oiTrend = getOITrendDescription(oiDelta);
            }
            
            // Add to metrics
            additionalMetrics.put("volume", currentVolume);
            additionalMetrics.put("oi", currentOI);
            additionalMetrics.put("volumeDelta", volumeDelta);
            additionalMetrics.put("oiDelta", oiDelta);
            additionalMetrics.put("volumeTrend", volumeTrend);
            additionalMetrics.put("oiTrend", oiTrend);
            additionalMetrics.put("volumeHistorySize", volumeHistory.size());
            additionalMetrics.put("oiHistorySize", oiHistory.size());
            
            log.debug("Volume/OI Deltas (10-tick) - Instrument: {}, Volume: {} (Δ: {} over {} ticks), OI: {} (Δ: {} over {} ticks)", 
                    instrumentToken, currentVolume, volumeDelta, volumeHistory.size(), currentOI, oiDelta, oiHistory.size());
                    
        } catch (Exception e) {
            log.warn("Error calculating volume/OI deltas for instrument {}: {}", instrumentToken, e.getMessage());
            // Fallback to raw values without deltas
            additionalMetrics.put("volume", tick.getVolumeTradedToday());
            additionalMetrics.put("oi", tick.getOi());
            additionalMetrics.put("volumeDelta", 0L);
            additionalMetrics.put("oiDelta", 0.0);
            additionalMetrics.put("volumeTrend", "Stable");
            additionalMetrics.put("oiTrend", "Stable");
            additionalMetrics.put("note", "Error calculating deltas");
        }
    }
    
    /**
     * Get volume trend description based on delta
     */
    private String getVolumeTrendDescription(long volumeDelta) {
        if (volumeDelta > 0) {
            return "Increasing";
        } else if (volumeDelta < 0) {
            return "Decreasing";
        } else {
            return "Stable";
        }
    }
    
    /**
     * Get OI trend description based on delta
     */
    private String getOITrendDescription(double oiDelta) {
        if (oiDelta > 0) {
            return "Building Up";
        } else if (oiDelta < 0) {
            return "Unwinding";
        } else {
            return "Stable";
        }
    }
    
    /**
     * Check if the instrument token is a future instrument
     * Index instruments (like Nifty50) don't have meaningful volume/OI data
     */
    private boolean isFutureInstrument(Long instrumentToken) {
        try {
            // Nifty50 Index token is 256265L - this is not a future
            Long niftyIndexToken = kiteInstrumentHandler.getNifty50Token();
            if (instrumentToken.equals(niftyIndexToken)) {
                return false; // This is an index instrument
            }
            
            // Check if it's the Nifty future token
            Long niftyFutureToken = kiteInstrumentHandler.getNifty50FutureToken();
            if (instrumentToken.equals(niftyFutureToken)) {
                return true; // This is a future instrument
            }
            
            // For other instruments, we could add more sophisticated logic
            // For now, assume anything that's not the Nifty index is a future/option
            return !instrumentToken.equals(niftyIndexToken);
            
        } catch (Exception e) {
            log.warn("Error determining instrument type for token {}: {}", instrumentToken, e.getMessage());
            // Default to false (index) if we can't determine
            return false;
        }
    }
}
