package com.jtradebot.processor.service;

import com.jtradebot.processor.common.ProfileUtil;
import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.config.TradingHoursConfig;
import com.jtradebot.processor.handler.DateTimeHandler;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.kafka.KafkaTickProducer;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.jtradebot.processor.model.enums.ExitReasonEnum;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.strategy.DetailedCategoryScore;
import com.jtradebot.processor.model.strategy.ScalpingEntryDecision;
import com.jtradebot.processor.model.strategy.ScalpingEntryConfig;
import com.jtradebot.processor.repository.document.JtradeOrder;
import com.jtradebot.processor.service.analysis.MarketDirectionService;
import com.jtradebot.processor.service.entry.DynamicRuleEvaluatorService;
import com.jtradebot.processor.service.entry.UnstableMarketConditionAnalysisService;
import com.jtradebot.processor.service.order.ActiveOrderTrackingService;
import com.jtradebot.processor.service.order.OrderManagementService;
import com.jtradebot.processor.service.scheduler.TickEventTracker;
import com.jtradebot.processor.service.scheduler.DailyLimitsSchedulerService;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Comparator;

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
    private final OrderManagementService orderManagementService;
    private final ActiveOrderTrackingService activeOrderTrackingService;
    private final DynamicStrategyConfigService configService;
    private final KafkaTickProducer kafkaTickProducer;
    private final Environment environment;
    private final DailyLimitsSchedulerService dailyLimitsSchedulerService;

    public void processLiveTicks(List<Tick> ticks, boolean skipMarketHoursCheck) {

        if (dailyLimitsSchedulerService.isDailyLimitReached()) {
            log.info("DAILY LIMITS HIT - Only new Orders are blocked. But Tick processing and tick storing will continue.");
        }

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

        long startTime = System.currentTimeMillis();

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
                    if (inTradingZone) {
                        log.debug("✅ IN TRADING ZONE - All no-trade zone conditions clear");
                    }

                    // Step 3: Calculate Detailed Category Scores and Quality Score
                    Map<String, DetailedCategoryScore> detailedCallScores = marketDirectionService.getDetailedCategoryScores(indicators, "CALL");
                    Map<String, DetailedCategoryScore> detailedPutScores = marketDirectionService.getDetailedCategoryScores(indicators, "PUT");

                    double callTotal = detailedCallScores.values().stream().mapToDouble(DetailedCategoryScore::getTotalScore).sum();
                    double putTotal = detailedPutScores.values().stream().mapToDouble(DetailedCategoryScore::getTotalScore).sum();
                    double qualityScore = calculateQualityScore(callTotal, putTotal);
                    // Show quality-based evaluation using calculated quality score
                    String dominantTrend = callTotal > putTotal ? "CALL" : "PUT";

                    // step 4: Log comprehensive indicator analysis
                    logComprehensiveIndicatorAnalysis(tick, qualityScore, detailedCallScores, detailedPutScores, dominantTrend);

                    // Step 5: Block entries after recent stop-loss hits
                    CandleTimeFrameEnum timeframe = CandleTimeFrameEnum.ONE_MIN;
                    if (activeOrderTrackingService.shouldBlockEntryAfterStopLoss(tick.getInstrumentToken(), timeframe, ExitReasonEnum.STOPLOSS_HIT)) {
                        log.warn("🚫 ORDER CREATION BLOCKED - Recent stoploss exit in same {} candle", timeframe);
                        return;
                    }

                    boolean filtersPassed = isEligibleForEntryCheck(qualityScore, inTradingZone);

                    // Step 6: Execute orders if signals are generated
                    if (filtersPassed) {
                        // Get entry decision directly from DynamicRuleEvaluatorService
                        ScalpingEntryDecision scenarioDecision;
                        try {
                            scenarioDecision = dynamicRuleEvaluatorService.getEntryDecision(tick, indicators, result, qualityScore, dominantTrend, detailedCallScores, detailedPutScores);
                        } catch (Exception e) {
                            log.error("Error getting entry decision for order execution: {}", e.getMessage());
                            return;
                        }

                        if (scenarioDecision != null && scenarioDecision.isShouldEntry()) {
                            // Check daily P&L limits first - if limits are hit, stop processing
                            if (dailyLimitsSchedulerService.isDailyLimitReached()) {
                                log.warn("🚫 DAILY LIMITS HIT - Stopping tick processing for the day");
                                return;
                            }
                            orderManagementService.entryOrder(tick, scenarioDecision, result.isConditionsMet(), dominantTrend, qualityScore, detailedCallScores, detailedPutScores, result);
                        }
                    }

                    // Step 7: Handle  active orders - exits, trailing SL, P&L updates
                    try {
                        if (activeOrderTrackingService.hasActiveOrder()) {
                            activeOrderTrackingService.updateLivePnL(tick);
                            List<JtradeOrder> ordersToExit = activeOrderTrackingService.getOrdersForExit(tick, qualityScore, dominantTrend);

                            Double currentIndexPrice = tick.getLastTradedPrice(); // Use current tick price as index price
                            // Process exits
                            for (JtradeOrder order : ordersToExit) {
                                orderManagementService.exitOrder(tick, order, currentIndexPrice);
                            }
                        }
                    } catch (KiteException e) {
                        log.error("Error updating live P&L for tick: {}", tick.getInstrumentToken(), e);
                    }

                } catch (Exception e) {
                    log.error("Error processing tick for instrument {}: {}", tick.getInstrumentToken(), e.getMessage());
                }
            }
            
            // Only send ticks to Kafka for live profile
            if (ProfileUtil.isProfileActive(environment, "live")) {
                kafkaTickProducer.sendTickDetails(tick);
            }
        }

        long endTime = System.currentTimeMillis();
        log.debug("Tick processing time: {} ms for {} ticks", (endTime - startTime), ticks.size());
    }

    private boolean isEligibleForEntryCheck(double qualityScore, boolean inTradingZone) {
        // Check if we're in a trading zone (no-trade zones filtering)
        if (!inTradingZone) {
            log.debug("🚫 Entry blocked - Not in trading zone");
            return false;
        }

        double minQualityThreshold = configService.getMinQualityThresholdFromLeastRestrictiveScenario();
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

    private void logComprehensiveIndicatorAnalysis(Tick tick, double qualityScore, Map<String, DetailedCategoryScore> detailedCallScores, Map<String, DetailedCategoryScore> detailedPutScores, String dominantTrend) {
        try {
            // Generate trend info using calculated quality score
            String trendInfo = generateTrendInfo(detailedCallScores, detailedPutScores, dominantTrend, qualityScore);

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
    private double calculateQualityScore(double callTotal, double putTotal) {
        double winningScore = Math.max(callTotal, putTotal);
        double totalPossibleScore = callTotal + putTotal;
        return totalPossibleScore > 0 ? (double) winningScore / totalPossibleScore * 10.0 : 0.0;
    }

    /**
     * Generate trend info for logging (moved from UnifiedIndicatorService)
     */
    private String generateTrendInfo(Map<String, DetailedCategoryScore> detailedCallScores, Map<String, DetailedCategoryScore> detailedPutScores, String dominantTrend, double qualityScore) {
        try {

            // Get category breakdown with requirements
            String categoryBreakdown = getCategoryBreakdownWithRequirements(detailedCallScores, detailedPutScores);

            return String.format("🎯 %s (%.1f/10) | %s", dominantTrend, qualityScore, categoryBreakdown);

        } catch (Exception e) {
            return "🎯 ERROR";
        }
    }

    /**
     * Get category breakdown with requirements (moved from UnifiedIndicatorService)
     */
    private String getCategoryBreakdownWithRequirements(Map<String, DetailedCategoryScore> detailedCallScores, Map<String, DetailedCategoryScore> detailedPutScores) {
        try {
            // Get requirements from the least restrictive scenario for display purposes
            List<ScalpingEntryConfig.Scenario> scenarios = configService.getScenarios();
            if (scenarios.isEmpty()) {
                return "";
            }

            // Find the least restrictive scenario (lowest quality score requirement)
            Optional<ScalpingEntryConfig.Scenario> leastRestrictive = scenarios.stream()
                    .filter(scenario -> scenario.getRequirements() != null && scenario.getRequirements().getMinQualityScore() != null)
                    .min(Comparator.comparing(scenario -> scenario.getRequirements().getMinQualityScore()));

            if (!leastRestrictive.isPresent()) {
                return "";
            }

            ScalpingEntryConfig.Scenario scenario = leastRestrictive.get();

            ScalpingEntryConfig.ScenarioRequirements requirements = scenario.getRequirements();

            // Get percentage requirements
            double emaPerRequired = requirements.getMin_ema_per() != null ? requirements.getMin_ema_per() : 0.0;
            double fvPerRequired = requirements.getMin_future_signal_per() != null ? requirements.getMin_future_signal_per() : 0.0;
            double csPerRequired = requirements.getMin_candlestick_per() != null ? requirements.getMin_candlestick_per() : 0.0;
            double mPerRequired = requirements.getMin_momentum_per() != null ? requirements.getMin_momentum_per() : 0.0;

            // Format percentage breakdown only (min scores removed)
            String callEmaPer = String.format("%.1f", detailedCallScores.getOrDefault("ema", new DetailedCategoryScore()).getScorePercentage() != null ? detailedCallScores.get("ema").getScorePercentage() : 0.0) + "%/" + emaPerRequired + "%";
            String callFvPer = String.format("%.1f", detailedCallScores.getOrDefault("futureAndVolume", new DetailedCategoryScore()).getScorePercentage() != null ? detailedCallScores.get("futureAndVolume").getScorePercentage() : 0.0) + "%/" + fvPerRequired + "%";
            String callCsPer = String.format("%.1f", detailedCallScores.getOrDefault("candlestick", new DetailedCategoryScore()).getScorePercentage() != null ? detailedCallScores.get("candlestick").getScorePercentage() : 0.0) + "%/" + csPerRequired + "%";
            String callMPer = String.format("%.1f", detailedCallScores.getOrDefault("momentum", new DetailedCategoryScore()).getScorePercentage() != null ? detailedCallScores.get("momentum").getScorePercentage() : 0.0) + "%/" + mPerRequired + "%";

            String putEmaPer = String.format("%.1f", detailedPutScores.getOrDefault("ema", new DetailedCategoryScore()).getScorePercentage() != null ? detailedPutScores.get("ema").getScorePercentage() : 0.0) + "%/" + emaPerRequired + "%";
            String putFvPer = String.format("%.1f", detailedPutScores.getOrDefault("futureAndVolume", new DetailedCategoryScore()).getScorePercentage() != null ? detailedPutScores.get("futureAndVolume").getScorePercentage() : 0.0) + "%/" + fvPerRequired + "%";
            String putCsPer = String.format("%.1f", detailedPutScores.getOrDefault("candlestick", new DetailedCategoryScore()).getScorePercentage() != null ? detailedPutScores.get("candlestick").getScorePercentage() : 0.0) + "%/" + csPerRequired + "%";
            String putMPer = String.format("%.1f", detailedPutScores.getOrDefault("momentum", new DetailedCategoryScore()).getScorePercentage() != null ? detailedPutScores.get("momentum").getScorePercentage() : 0.0) + "%/" + mPerRequired + "%";

            return String.format("Call: EMA=%s, FV=%s, CS=%s, M=%s | Put: EMA=%s, FV=%s, CS=%s, M=%s",
                    callEmaPer, callFvPer, callCsPer, callMPer,
                    putEmaPer, putFvPer, putCsPer, putMPer);

        } catch (Exception e) {
            log.error("Error getting category breakdown with requirements: {}", e.getMessage());
            return "Error getting requirements";
        }
    }

}
