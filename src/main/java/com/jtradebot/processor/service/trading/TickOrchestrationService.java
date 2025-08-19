package com.jtradebot.processor.service.trading;

import com.jtradebot.processor.handler.DateTimeHandler;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.service.TickSetupService;
import com.jtradebot.processor.service.TickEventTracker;
import com.jtradebot.processor.service.BacktestDataCollectorService;
import com.zerodhatech.models.Tick;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
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
    private final OrderExecutionService orderExecutionService;
    private final BacktestDataCollectorService backtestDataCollectorService;

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
        String niftyFutureToken = kiteInstrumentHandler.getNifty50FutureToken().toString();
        
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

    /**
     * Get Nifty tick information for logging
     */
    public String getNiftyTickInfo() {
        try {
            String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();
            String niftyFutureToken = kiteInstrumentHandler.getNifty50FutureToken().toString();
            
            Tick lastNiftyTick = tickDataManager.getLastTick(niftyToken);
            Tick lastNiftyFutureTick = tickDataManager.getLastTick(niftyFutureToken);
            
            if (lastNiftyTick != null && lastNiftyFutureTick != null) {
                return String.format("Nifty: Time=%s, LTP=%.2f, Vol(Fut)=%d, H=%.2f, L=%.2f, O=%.2f, C=%.2f", 
                    lastNiftyTick.getTickTimestamp(),
                    lastNiftyTick.getLastTradedPrice(),
                    lastNiftyFutureTick.getVolumeTradedToday(),
                    lastNiftyTick.getHighPrice(),
                    lastNiftyTick.getLowPrice(),
                    lastNiftyTick.getOpenPrice(),
                    lastNiftyTick.getClosePrice());
            } else {
                return "Nifty: No data available";
            }
        } catch (Exception e) {
            return "Nifty: Error getting data";
        }
    }

    /**
     * Check if we can execute a new order
     */
    public boolean canExecuteNewOrder() {
        return orderExecutionService.canExecuteNewOrder();
    }

    /**
     * Get all active orders
     */
    public List<com.jtradebot.processor.repository.document.JtradeOrder> getActiveOrders() {
        return orderExecutionService.getActiveOrders();
    }

    /**
     * Update live P&L for active trades
     */
    public void updateLivePnL(com.zerodhatech.models.Tick tick) throws Exception, KiteException {
        try {
            orderExecutionService.updateLivePnL(tick);
        } catch (KiteException e) {
            log.error("KiteException while updating live P&L: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error updating live P&L: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Check and process exits for existing orders
     */
    public void checkAndProcessExits(com.zerodhatech.models.Tick tick) {
        orderExecutionService.checkAndProcessExits(tick);
    }
}
