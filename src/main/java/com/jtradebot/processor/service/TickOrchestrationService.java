package com.jtradebot.processor.service;

import com.jtradebot.processor.config.TradingHoursConfig;
import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.handler.DateTimeHandler;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.service.entry.DynamicRuleEvaluatorService;
import com.jtradebot.processor.service.entry.UnstableMarketConditionAnalysisService;
import com.jtradebot.processor.service.analysis.MarketDirectionService;
import com.jtradebot.processor.service.order.ExitStrategyService;
import com.jtradebot.processor.service.order.OrderExecutionService;
import com.jtradebot.processor.service.scheduler.TickEventTracker;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TickOrchestrationService {

    private final TickDataManager tickDataManager;
    private final TickSetupService tickSetupService;
    private final TickEventTracker tickEventTracker;
    private final KiteInstrumentHandler kiteInstrumentHandler;
    private final TradingHoursConfig tradingHoursConfig;

    private final DynamicRuleEvaluatorService dynamicRuleEvaluatorService;
    private final MarketDirectionService marketDirectionService;
    private final UnstableMarketConditionAnalysisService unstableMarketConditionAnalysisService;
    private final OrderExecutionService orderExecutionService;
    private final ExitStrategyService exitStrategyService;
    private final DynamicStrategyConfigService configService;

    public void processLiveTicks(List<Tick> ticks, boolean skipMarketHoursCheck) {

        // Get trading hours from configuration
        int startHour = tradingHoursConfig.getMarketStartHour();
        int startMinute = tradingHoursConfig.getMarketStartMinute();
        int endHour = tradingHoursConfig.getMarketEndHour();
        int endMinute = tradingHoursConfig.getMarketEndMinute();

        if (ticks.isEmpty()) {
            log.warn("No ticks to process");
            return;
        }
        Date lastTickTimestamp = ticks.get(0).getTickTimestamp();
        tickDataManager.setLastTickTime(lastTickTimestamp);

        if (!skipMarketHoursCheck
                & !DateTimeHandler.isMarketOpen(tickDataManager.getLastTickTime(), startHour, startMinute, endHour, endMinute)) {
            log.info("Market is closed. Skipping tick processing. Current time: {}", lastTickTimestamp);
            return;
        }
        processLiveTicks(ticks);
    }

    private void processLiveTicks(List<Tick> ticks) {

        Map<Long, Tick> latestTicks = new HashMap<>();
        for (Tick tick : ticks) {
            latestTicks.put(tick.getInstrumentToken(), tick);
        }

        // Get Nifty token identifiers
        String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();

        for (Tick tick : latestTicks.values()) {
            String instrumentToken = String.valueOf(tick.getInstrumentToken());

            // Initialize on first tick for both instruments
            initializeOnFirstTick(tick);

            // Add tick to data manager for both instruments
            tickEventTracker.setLastTickEventTimestamp(instrumentToken, System.currentTimeMillis());
            tickDataManager.add(instrumentToken, tick);

            // Process strategy for index ticks only - future data will be fetched from map when needed
            if (instrumentToken.equals(niftyToken)) {
                try {
                    // Step 1: Get market data and calculate indicators
                    FlattenedIndicators indicators = dynamicRuleEvaluatorService.getFlattenedIndicators(tick);

                    // Step 2: Analyze no trade zones filter checks
                    UnstableMarketConditionAnalysisService.FlexibleFilteringResult result = unstableMarketConditionAnalysisService.checkFlexibleFilteringConditions(tick, indicators);
                    boolean inTradingZone = result.isConditionsMet();

                    // Step 3: Calculate Category Scores and Quality Score
                    Map<String, Integer> callScores = marketDirectionService.getWeightedCategoryScores(indicators, "CALL");
                    Map<String, Integer> putScores = marketDirectionService.getWeightedCategoryScores(indicators, "PUT");

                    int callTotal = callScores.values().stream().mapToInt(Integer::intValue).sum();
                    int putTotal = putScores.values().stream().mapToInt(Integer::intValue).sum();
                    double qualityScore = calculateQualityScore(callTotal, putTotal);
                    // Show quality-based evaluation using calculated quality score
                    String dominantTrend = callTotal > putTotal ? "CALL" : "PUT";

                    // step 4: Log comprehensive indicator analysis
                    logComprehensiveIndicatorAnalysis(tick, qualityScore, callScores, putScores, dominantTrend);

                    // Step 5: Block entries after recent stop-loss hits
                    if (exitStrategyService.shouldBlockEntryAfterStopLoss(tick.getInstrumentToken())) {
                        log.warn("🚫 ORDER CREATION BLOCKED - Recent STOPLOSS_HIT exit in same 5-min candle");
                        return;
                    }

                    boolean filtersPassed = isEligibleForEntryCheck(tick, qualityScore, inTradingZone);

                    // Step 6: Execute orders if signals are generated
                    if (filtersPassed) {
                        orderExecutionService.executeOrdersIfSignalsGenerated(tick, indicators, result, qualityScore, dominantTrend, callScores, putScores);
                    }

                    // Step 7: Handle order management
                    orderExecutionService.handleOrderManagement(tick);

                } catch (Exception e) {
                    log.error("Error processing tick for instrument {}: {}", tick.getInstrumentToken(), e.getMessage());
                }
            }
        }
    }

    private boolean isEligibleForEntryCheck(Tick tick, double qualityScore, boolean inTradingZone) {
        // Check if we're in a trading zone (no-trade zones filtering)
        if (!inTradingZone) {
            log.debug("🚫 Entry blocked - Not in trading zone");
            return false;
        }

        double minQualityThreshold = configService.getMinQualityThresholdFromScenario("SAFE_ENTRY_SIGNAL");
        if (qualityScore < minQualityThreshold) {
            log.debug("🚫 Entry blocked - Quality score too low: {}/10 (threshold: {})",
                    String.format("%.1f", qualityScore), minQualityThreshold);
            return false;
        }

        log.info("✅ Entry check passed - In trading zone: {}, Quality score: {}/10",
                inTradingZone, String.format("%.1f", qualityScore));
        return true;
    }


    private void initializeOnFirstTick(Tick tick) {
        if (tickDataManager.isNotInitialized(String.valueOf(tick.getInstrumentToken()))) {
            log.info("Initializing tickDataManager for instrument: {}", tick.getInstrumentToken());
            tickSetupService.connect();
            tickDataManager.initialize(String.valueOf(tick.getInstrumentToken()), tradingHoursConfig.getLastMarketTime(tick.getTickTimestamp()));
        }
    }

    private void logComprehensiveIndicatorAnalysis(Tick tick, double qualityScore, Map<String, Integer> callScores, Map<String, Integer> putScores, String dominantTrend) {
        try {
            // Generate trend info using calculated quality score
            String trendInfo = generateTrendInfo(callScores, putScores, dominantTrend, qualityScore);

            // Log the comprehensive analysis
            log.info("📊 {} | 💰 {} | {}",
                    tick.getTickTimestamp(),
                    tick.getLastTradedPrice(),
                    trendInfo);

        } catch (Exception e) {
            log.error("Error in comprehensive indicator logging: {}", e.getMessage(), e);
        }
    }

    /**
     * Calculate quality score from category totals (same logic as checkQualityFilter)
     */
    private double calculateQualityScore(int callTotal, int putTotal) {
        int winningScore = Math.max(callTotal, putTotal);
        int totalPossibleScore = callTotal + putTotal;
        return totalPossibleScore > 0 ? (double) winningScore / totalPossibleScore * 10.0 : 0.0;
    }

    /**
     * Generate trend info for logging (moved from UnifiedIndicatorService)
     */
    private String generateTrendInfo(Map<String, Integer> callScores, Map<String, Integer> putScores, String dominantTrend, double qualityScore) {
        try {

            // Get category breakdown with requirements
            String categoryBreakdown = getCategoryBreakdownWithRequirements(callScores, putScores);

            return String.format("🎯 %s (%.1f/10) | %s", dominantTrend, qualityScore, categoryBreakdown);

        } catch (Exception e) {
            return "🎯 ERROR";
        }
    }

    /**
     * Get category breakdown with requirements (moved from UnifiedIndicatorService)
     */
    private String getCategoryBreakdownWithRequirements(Map<String, Integer> callScores, Map<String, Integer> putScores) {
        try {
            // Get requirements from SAFE_ENTRY_SIGNAL scenario
            var scenario = configService.getScenarioByName("SAFE_ENTRY_SIGNAL");
            if (scenario == null || scenario.getRequirements() == null) {
                return "";
            }

            var requirements = scenario.getRequirements();
            int emaRequired = requirements.getEma_min_score() != null ? requirements.getEma_min_score() : 0;
            int fvRequired = requirements.getFutureAndVolume_min_score() != null ? requirements.getFutureAndVolume_min_score() : 0;
            int csRequired = requirements.getCandlestick_min_score() != null ? requirements.getCandlestick_min_score() : 0;
            int mRequired = requirements.getMomentum_min_score() != null ? requirements.getMomentum_min_score() : 0;

            // Format the breakdown with actual scores for both directions
            String callEma = callScores.getOrDefault("ema", 0) + "/" + emaRequired;
            String callFv = callScores.getOrDefault("futureAndVolume", 0) + "/" + fvRequired;
            String callCs = callScores.getOrDefault("candlestick", 0) + "/" + csRequired;
            String callM = callScores.getOrDefault("momentum", 0) + "/" + mRequired;

            String putEma = putScores.getOrDefault("ema", 0) + "/" + emaRequired;
            String putFv = putScores.getOrDefault("futureAndVolume", 0) + "/" + fvRequired;
            String putCs = putScores.getOrDefault("candlestick", 0) + "/" + csRequired;
            String putM = putScores.getOrDefault("momentum", 0) + "/" + mRequired;

            return String.format("Call: EMA=%s, FV=%s, CS=%s, M=%s | Put: EMA=%s, FV=%s, CS=%s, M=%s",
                    callEma, callFv, callCs, callM, putEma, putFv, putCs, putM);

        } catch (Exception e) {
            return "";
        }
    }

}
