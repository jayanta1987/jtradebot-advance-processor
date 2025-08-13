package com.jtradebot.processor.service;

import com.jtradebot.processor.handler.DateTimeHandler;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.manager.EmaCrossTrackingManager;
import com.jtradebot.processor.service.ScalpingVolumeSurgeService;
import com.jtradebot.processor.model.FlattenedIndicators;

import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TickProcessService {

    private final TickDataManager tickDataManager;
    private final TickSetupService tickSetupService;
    private final TickEventTracker tickEventTracker;
    private final KiteInstrumentHandler kiteInstrumentHandler;
    private final EmaCrossTrackingManager emaCrossTrackingManager;
    
    // ScalpingVolumeSurgeService for strategy evaluation
    private final ScalpingVolumeSurgeService scalpingVolumeSurgeService;
    private final ExitStrategyService exitStrategyService;

    @Value("${jtradebot.strategy.enable-scalping-volume-surge:true}")
    private boolean enableScalpingVolumeSurge;

    public void processLiveTicks(List<Tick> ticks) {
        // IGNORE IF MARKET NOT STARTED YET
        if (!DateTimeHandler.isMarketOpen()) {
            log.info("Market not started yet. Skipping tick processing. Current time: {}", new Date());
            return;
        }

        Map<Long, Tick> latestTicks = new HashMap<>();
        for (Tick tick : ticks) {
            latestTicks.put(tick.getInstrumentToken(), tick);
        }
        
        for (Tick tick : latestTicks.values()) {
            log.info("Processing tick: {} : {} -> {}, isTradable = {}", tick.getInstrumentToken(), tick.getTickTimestamp(), tick.getLastTradedPrice(), tick.isTradable());
            processFutureTick(tick);
            processIndexTick(tick);
        }
    }

    public void processFutureTick(Tick tick) {
        if (tick.isTradable()) {
            initializeOnFirstTick(tick);
            tickEventTracker.setLastTickEventTimestamp(String.valueOf(tick.getInstrumentToken()), System.currentTimeMillis());
            tickDataManager.add(String.valueOf(tick.getInstrumentToken()), tick);
            
            // Process with ScalpingVolumeSurge strategy if enabled
            if (enableScalpingVolumeSurge) {
                processWithScalpingVolumeSurgeStrategy(tick);
            }
        }
    }

    public void processIndexTick(Tick tick) {
        if (tick.isTradable()) {
            return;
        }
        initializeOnFirstTick(tick);
        tickEventTracker.setLastTickEventTimestamp(String.valueOf(tick.getInstrumentToken()), System.currentTimeMillis());
        tickDataManager.add(String.valueOf(tick.getInstrumentToken()), tick);
        
        // Process with ScalpingVolumeSurge strategy if enabled
        if (enableScalpingVolumeSurge) {
            processWithScalpingVolumeSurgeStrategy(tick);
        }
    }
    
    /**
     * Process tick using ScalpingVolumeSurge strategy
     */
    private void processWithScalpingVolumeSurgeStrategy(Tick tick) {
        try {
            // Print current Nifty tick information on every tick
            emaCrossTrackingManager.printCurrentNiftyTickInfo();
            
            // Get flattened indicators
            FlattenedIndicators indicators = scalpingVolumeSurgeService.getFlattenedIndicators(tick);
            
            // Get strategy recommendation
            String recommendedStrategy = scalpingVolumeSurgeService.getRecommendedStrategy(tick);
            
            // Get strategy confidence
            Double strategyConfidence = scalpingVolumeSurgeService.getStrategyConfidence(tick);
            
            // Check entry conditions
            boolean shouldMakeCallEntry = scalpingVolumeSurgeService.shouldMakeCallEntry(tick);
            boolean shouldMakePutEntry = scalpingVolumeSurgeService.shouldMakePutEntry(tick);
            

            
            // Log flattened indicators for debugging
            if (log.isDebugEnabled()) {
                log.debug("Flattened Indicators for instrument {}: {}", tick.getInstrumentToken(), indicators);
            }
            
            // Check and process exits for existing orders
            checkAndProcessExits(tick);
            
            // TODO: Add order placement logic here when ready
            // TODO: Add performance tracking for backtesting
            // TODO: Add Kafka producer for strategy decisions if needed
            
        } catch (Exception e) {
            log.error("Error processing tick with SCALPING_FUTURE_VOLUME_SURGE strategy for instrument: {}", tick.getInstrumentToken(), e);
        }
    }

    private void initializeOnFirstTick(Tick tick) {
        if (tickDataManager.isNotInitialized(String.valueOf(tick.getInstrumentToken()))) {
            log.info("Initializing tickDataManager for instrument: {}", tick.getInstrumentToken());
            tickSetupService.connect();
            tickDataManager.initialize(String.valueOf(tick.getInstrumentToken()), DateTimeHandler.getLastMarketTime(tick.getTickTimestamp()));
        }
    }
    
    /**
     * Check and process exits for existing orders based on current tick data
     */
    private void checkAndProcessExits(Tick tick) {
        try {
            // Get current price and index price from tick
            Double currentPrice = tick.getLastTradedPrice();
            Double currentIndexPrice = tick.getLastTradedPrice(); // For now using same price, can be enhanced later
            
            // Check and process exits
            exitStrategyService.checkAndProcessExits(currentPrice, currentIndexPrice);
            
        } catch (Exception e) {
            log.error("Error checking and processing exits for tick: {}", tick.getInstrumentToken(), e);
        }
    }
    
    
}
