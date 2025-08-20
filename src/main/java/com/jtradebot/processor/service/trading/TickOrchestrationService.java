package com.jtradebot.processor.service.trading;

import com.jtradebot.processor.handler.DateTimeHandler;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.service.TickSetupService;
import com.jtradebot.processor.service.scheduler.TickEventTracker;
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
public class TickOrchestrationService {

    private final TickDataManager tickDataManager;
    private final TickSetupService tickSetupService;
    private final TickEventTracker tickEventTracker;
    private final KiteInstrumentHandler kiteInstrumentHandler;
    private final TradingSignalProcessorService tradingSignalProcessorService;

    @Value("${jtradebot.strategy.enable-scalping-volume-surge:true}")
    private boolean enableScalpingVolumeSurge;

    /**
     * Main entry point for processing live ticks
     * This orchestrates the entire tick processing workflow
     */
    public void processLiveTicks(List<Tick> ticks) throws Exception {
        processLiveTicks(ticks, false);
    }
    
    /**
     * Process live ticks with optional market hours check bypass
     */
    public void processLiveTicks(List<Tick> ticks, boolean skipMarketHoursCheck) throws Exception {
        // IGNORE IF MARKET NOT STARTED YET (unless backtesting)
        if (!skipMarketHoursCheck && !DateTimeHandler.isMarketOpen()) {
            log.info("Market not started yet. Skipping tick processing. Current time: {}", new Date());
            return;
        }

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
                if (enableScalpingVolumeSurge) {
                    // 🔥 DELEGATE TO TRADING SIGNAL PROCESSOR
                    tradingSignalProcessorService.processTradingSignals(tick);
                }
            }
        }
    }

    /**
     * Initialize tick data manager on first tick
     */
    private void initializeOnFirstTick(Tick tick) {
        if (tickDataManager.isNotInitialized(String.valueOf(tick.getInstrumentToken()))) {
            log.info("Initializing tickDataManager for instrument: {}", tick.getInstrumentToken());
            tickSetupService.connect();
            tickDataManager.initialize(String.valueOf(tick.getInstrumentToken()), DateTimeHandler.getLastMarketTime(tick.getTickTimestamp()));
        }
    }


}
