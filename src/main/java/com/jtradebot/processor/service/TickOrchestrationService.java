package com.jtradebot.processor.service;

import com.jtradebot.processor.handler.DateTimeHandler;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.service.entry.DynamicRuleEvaluatorService;
import com.jtradebot.processor.service.logging.UnifiedIndicatorService;
import com.jtradebot.processor.service.entry.UnstableMarketConditionAnalysisService;
import com.jtradebot.processor.service.order.ExitStrategyService;
import com.jtradebot.processor.service.order.OrderExecutionService;
import com.jtradebot.processor.service.scheduler.TickEventTracker;
import com.zerodhatech.models.Tick;
import lombok.Getter;
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

    private final DynamicRuleEvaluatorService dynamicRuleEvaluatorService;
    private final UnstableMarketConditionAnalysisService unstableMarketConditionAnalysisService;
    private final OrderExecutionService orderExecutionService;
    private final UnifiedIndicatorService unifiedIndicatorService;
    private final ExitStrategyService exitStrategyService;

    public void processLiveTicks(List<Tick> ticks, boolean skipMarketHoursCheck) throws Exception {
        if (!skipMarketHoursCheck && !DateTimeHandler.isMarketOpen()) {
            log.info("Market not started yet. Skipping tick processing. Current time: {}", new Date());
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

                    // Step 2: Analyze market conditions
                    MarketConditionAnalysis marketConditions = analyzeMarketConditions(tick, indicators);

                    // Step 3: Process entry logic and generate signals using unified service
                    unifiedIndicatorService.logUnifiedIndicatorAnalysis(tick, indicators, marketConditions.isMarketSuitable());

                    if (exitStrategyService.shouldBlockEntryAfterStopLoss(tick.getInstrumentToken())) {
                        log.warn("🚫 ORDER CREATION BLOCKED - Recent STOPLOSS_HIT exit in same 5-min candle");
                        return;
                    }

                    // Step 4: Execute orders if signals are generated
                    orderExecutionService.executeOrdersIfSignalsGenerated(tick, indicators, marketConditions);

                    // Step 5: Handle order management
                    orderExecutionService.handleOrderManagement(tick, indicators, marketConditions);

                } catch (Exception e) {
                    log.error("Error processing tick for instrument {}: {}", tick.getInstrumentToken(), e.getMessage());
                }
            }
        }
    }

    private void initializeOnFirstTick(Tick tick) {
        if (tickDataManager.isNotInitialized(String.valueOf(tick.getInstrumentToken()))) {
            log.info("Initializing tickDataManager for instrument: {}", tick.getInstrumentToken());
            tickSetupService.connect();
            tickDataManager.initialize(String.valueOf(tick.getInstrumentToken()), DateTimeHandler.getLastMarketTime(tick.getTickTimestamp()));
        }
    }

    private MarketConditionAnalysis analyzeMarketConditions(Tick indexTick, FlattenedIndicators indicators) {
        // 🔥 OPTIMIZATION: Calculate market condition analysis ONCE per tick to avoid redundant calculations
        boolean isMarketSuitable = unstableMarketConditionAnalysisService.isMarketConditionSuitable(indexTick, indicators);

        // Always get market condition details for order storage (entry logic is handled separately)
        //String detailedFlatMarketReason = marketConditionAnalysisService.getDetailedFlatMarketReason(indexTick, indicators);

        return new MarketConditionAnalysis(isMarketSuitable);
    }

    @Getter
    public static class MarketConditionAnalysis {
        private final boolean isMarketSuitable;
        //private final String detailedFlatMarketReason;

        public MarketConditionAnalysis(boolean isMarketSuitable) {
            this.isMarketSuitable = isMarketSuitable;
        }

    }


}
