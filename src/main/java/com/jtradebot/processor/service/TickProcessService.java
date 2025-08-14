package com.jtradebot.processor.service;

import com.jtradebot.processor.handler.DateTimeHandler;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.manager.EmaCrossTrackingManager;
import com.jtradebot.processor.service.TickMonitoringService;
import com.jtradebot.processor.repository.document.JtradeOrder;
import com.jtradebot.processor.repository.document.TickDocument;
import com.jtradebot.processor.repository.JtradeOrderRepository;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.service.OptionPricingService;
import com.jtradebot.processor.config.DynamicStrategyConfigService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Date;
import java.util.List;

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
    
    private final ExitStrategyService exitStrategyService;
    private final JtradeOrderRepository jtradeOrderRepository;
    private final OptionPricingService optionPricingService;
    private final DynamicStrategyConfigService configService;
    private final TickMonitoringService tickMonitoringService;
    
    // Active trades cache
    private final Map<String, JtradeOrder> activeTrades = new ConcurrentHashMap<>();

    @Value("${jtradebot.strategy.enable-scalping-volume-surge:true}")
    private boolean enableScalpingVolumeSurge;

    public void processLiveTicks(List<Tick> ticks) {
        processLiveTicks(ticks, false);
    }
    
    public void processLiveTicks(List<Tick> ticks, boolean skipMarketHoursCheck) {
        // IGNORE IF MARKET NOT STARTED YET (unless backtesting)
        if (!skipMarketHoursCheck && !DateTimeHandler.isMarketOpen()) {
            log.info("Market not started yet. Skipping tick processing. Current time: {}", new Date());
            return;
        }
        
        if (skipMarketHoursCheck) {
            log.debug("Running in backtesting mode - market hours check bypassed");
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
            log.debug("Processing tick: {} : {} -> {}, isTradable = {}", 
                    instrumentToken, tick.getTickTimestamp(), tick.getLastTradedPrice(), tick.isTradable());
            
            // Initialize on first tick for both instruments
            initializeOnFirstTick(tick);
            
            // Add tick to data manager for both instruments
            tickEventTracker.setLastTickEventTimestamp(instrumentToken, System.currentTimeMillis());
            tickDataManager.add(instrumentToken, tick);
            
            // Process strategy only for index ticks (to avoid duplicate processing)
            // But ensure we have both index and future data available
            if (instrumentToken.equals(niftyToken)) {
                // Check if we have recent future data (within last 5 seconds)
                Tick recentFutureTick = tickDataManager.getLastTick(niftyFutureToken);
                if (recentFutureTick != null && 
                    Math.abs(tick.getTickTimestamp().getTime() - recentFutureTick.getTickTimestamp().getTime()) < 5000) {
                    
                    // Process with ScalpingVolumeSurge strategy if enabled
                    if (enableScalpingVolumeSurge) {
                        processWithScalpingVolumeSurgeStrategy(tick, recentFutureTick);
                    }
                } else {
                    log.debug("No recent future data available for index tick processing");
                }
            }
        }
    }

    public void processFutureTick(Tick tick) {
        // This method is now deprecated - use processLiveTicks instead
        log.warn("processFutureTick is deprecated - use processLiveTicks for proper tick handling");
    }

    public void processIndexTick(Tick tick) {
        // This method is now deprecated - use processLiveTicks instead
        log.warn("processIndexTick is deprecated - use processLiveTicks for proper tick handling");
    }
    
    /**
     * Process tick using ScalpingVolumeSurge strategy
     * Now properly handles both index and future data
     */
    private void processWithScalpingVolumeSurgeStrategy(Tick indexTick, Tick futureTick) {
        try {
            // Get Nifty token for filtering index vs future ticks
            String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();
            String niftyFutureToken = kiteInstrumentHandler.getNifty50FutureToken().toString();
            
            // Validate we have the correct tick types
            boolean isIndexTick = String.valueOf(indexTick.getInstrumentToken()).equals(niftyToken);
            boolean isFutureTick = String.valueOf(futureTick.getInstrumentToken()).equals(niftyFutureToken);
            
            if (!isIndexTick || !isFutureTick) {
                log.error("Invalid tick combination - Index: {}, Future: {}", 
                        String.valueOf(indexTick.getInstrumentToken()), String.valueOf(futureTick.getInstrumentToken()));
                return;
            }
            
            log.debug("Processing combined tick data - Index: {} @ {}, Future: {} @ {}, Future Vol: {}", 
                    indexTick.getLastTradedPrice(), indexTick.getTickTimestamp(),
                    futureTick.getLastTradedPrice(), futureTick.getTickTimestamp(),
                    futureTick.getVolumeTradedToday());
            
            // Enhanced monitoring and logging (for monitoring purposes only)
            // Convert Tick to TickDocument for dynamic indicator analysis
            TickDocument tickDocument = convertTickToDocument(indexTick);
            tickMonitoringService.monitorTickWithScoreAndIndicators(indexTick, tickDocument);
            
            // Check and process exits for existing orders
            checkAndProcessExits(indexTick);
            
            // Process new entry signals and manage active trades (using index tick for price data)
            processEntrySignals(indexTick);
            
            // Update live P&L for active trades (using index tick for price data)
            updateLivePnL(indexTick);
            
        } catch (Exception e) {
            log.error("Error processing tick with SCALPING_FUTURE_VOLUME_SURGE strategy for index instrument: {}", indexTick.getInstrumentToken(), e);
        }
    }

    private void initializeOnFirstTick(Tick tick) {
        if (tickDataManager.isNotInitialized(String.valueOf(tick.getInstrumentToken()))) {
            log.info("Initializing tickDataManager for instrument: {}", tick.getInstrumentToken());
            tickSetupService.connect();
            tickDataManager.initialize(String.valueOf(tick.getInstrumentToken()), DateTimeHandler.getLastMarketTime(tick.getTickTimestamp()));
        }
    }
    
    // Score color coding moved to TickMonitoringService for enhanced monitoring
    
    /**
     * Convert Tick to TickDocument for dynamic indicator analysis
     */
    private TickDocument convertTickToDocument(Tick tick) {
        TickDocument document = new TickDocument();
        document.setInstrumentToken(tick.getInstrumentToken());
        document.setLastTradedPrice(tick.getLastTradedPrice());
        document.setVolumeTradedToday(tick.getVolumeTradedToday());
        document.setTickTimestamp(tick.getTickTimestamp());
        return document;
    }
    
    /**
     * Get Nifty tick information for logging
     */
    private String getNiftyTickInfo() {
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
     * Process entry signals and manage active trades
     */
    private void processEntrySignals(Tick tick) {
        try {
            String instrumentToken = String.valueOf(tick.getInstrumentToken());
            
            log.debug("Processing entry signals for instrument: {}", instrumentToken);
            
            // Check: if ExitStrategyService has any active order, skip (only one active order allowed globally)
            if (exitStrategyService.hasActiveOrder()) {
                log.debug("Active order already exists, skipping new entry for instrument: {} (Risk management: only one active order allowed)", instrumentToken);
                return;
            }
            
            // Note: Entry decisions are now handled by the dynamic configuration system
            // This method is kept for compatibility but actual entry logic is in the dynamic system
            log.debug("Entry decisions are handled by dynamic configuration system for instrument: {}", instrumentToken);
            
        } catch (Exception e) {
            log.error("Error processing entry signals for tick: {}", tick.getInstrumentToken(), e);
        }
    }
    
    /**
     * Update live P&L for active trades
     */
    private void updateLivePnL(Tick tick) {
        try {
            // Get active order from ExitStrategyService (global check)
            List<JtradeOrder> activeOrders = exitStrategyService.getActiveOrders();
            if (activeOrders.isEmpty()) {
                return;
            }
            
            JtradeOrder activeOrder = activeOrders.get(0); // Get the first active order
            
            // Get current Nifty index price (not future price)
            String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();
            Tick niftyTick = tickDataManager.getLastTick(niftyToken);
            if (niftyTick == null) {
                log.warn("No Nifty index data available for P&L calculation");
                return;
            }
            
            double currentIndexPrice = niftyTick.getLastTradedPrice();
            double entryIndexPrice = activeOrder.getEntryIndexPrice();
            double entryOptionPrice = activeOrder.getEntryPrice();
            
            // Calculate current option price based on index movement
            double currentOptionPrice = optionPricingService.calculateCurrentLTP(entryOptionPrice, entryIndexPrice, currentIndexPrice, activeOrder.getOrderType());
            
            // Debug logging for P&L calculation
            log.debug("P&L Debug - Entry Option: {}, Current Option: {}, Entry Index: {}, Current Index: {}, Order Type: {}", 
                    entryOptionPrice, currentOptionPrice, entryIndexPrice, currentIndexPrice, activeOrder.getOrderType());
            
            double points = 0.0;
            double pnl = 0.0;
            
            if (OrderTypeEnum.CALL_BUY.equals(activeOrder.getOrderType())) {
                points = currentOptionPrice - entryOptionPrice;
                pnl = points * activeOrder.getQuantity();
            } else if (OrderTypeEnum.PUT_BUY.equals(activeOrder.getOrderType())) {
                // For PUT: use same calculation as CALL (current - entry)
                // This will give negative points when index goes up (PUT loses)
                points = currentOptionPrice - entryOptionPrice;
                pnl = points * activeOrder.getQuantity();
            }
            
            // Simplified live P&L log (only points and P&L)
            String orderTypeDisplay = OrderTypeEnum.CALL_BUY.equals(activeOrder.getOrderType()) ? "CALL" : "PUT";
            log.info("_________________________ 💰 LIVE P&L - {} | Points: {}, P&L: ₹{} _________________________", 
                    orderTypeDisplay, String.format("%+.2f", points), String.format("%.2f", pnl));
            
        } catch (Exception e) {
            log.error("Error updating live P&L for tick: {}", tick.getInstrumentToken(), e);
        }
    }
    
    /**
     * Create a new trade order and save to DB
     */
    private void createTradeOrder(Tick tick, String orderType) {
        try {
            String instrumentToken = String.valueOf(tick.getInstrumentToken());
            
            // Get current Nifty index price (not future price) for option pricing
            String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();
            Tick niftyTick = tickDataManager.getLastTick(niftyToken);
            if (niftyTick == null) {
                log.error("No Nifty index data available for order creation");
                return;
            }
            
            double currentIndexPrice = niftyTick.getLastTradedPrice(); // Use Nifty index price, not future price
            
            // Calculate option entry price (1% of index price as premium)
            Double optionEntryPrice = optionPricingService.calculateEntryPrice(currentIndexPrice);
            if (optionEntryPrice == null) {
                log.error("Failed to calculate option entry price for index: {}", currentIndexPrice);
                return;
            }
            
            // For scalping, use point-based targets from JSON configuration
            double stopLossPoints, targetPoints;
            
            if ("CALL_BUY".equals(orderType)) {
                stopLossPoints = configService.getCallStopLossPoints();
                targetPoints = configService.getCallTargetPoints();
            } else {
                stopLossPoints = configService.getPutStopLossPoints();
                targetPoints = configService.getPutTargetPoints();
            }
            
            // Calculate stop loss and target prices directly (point-based, not percentage-based)
            Double stopLossPrice, targetPrice;
            
            if ("CALL_BUY".equals(orderType)) {
                // CALL: Stop loss below entry, target above entry
                stopLossPrice = optionEntryPrice - stopLossPoints;
                targetPrice = optionEntryPrice + targetPoints;
            } else {
                // PUT: Stop loss above entry, target below entry
                stopLossPrice = optionEntryPrice + stopLossPoints;
                targetPrice = optionEntryPrice - targetPoints;
            }
            
            // Ensure prices don't go below 0
            stopLossPrice = Math.max(stopLossPrice, 0.0);
            targetPrice = Math.max(targetPrice, 0.0);
            
            if (stopLossPrice == null || targetPrice == null) {
                log.error("Failed to calculate stop loss or target price for option");
                return;
            }
            
            // Use placeholder option symbols since we're analyzing with index/future tokens
            String optionSymbol = "CALL_BUY".equals(orderType) ? "TEST_OPTION_CE" : "TEST_OPTION_PE";
            
            JtradeOrder order = exitStrategyService.createOrderEntry(
                OrderTypeEnum.valueOf(orderType),
                optionSymbol, // Placeholder option symbol
                0L, // No instrument token (using placeholder symbols)
                optionEntryPrice, // Option entry price (premium)
                currentIndexPrice, // Entry index price (Nifty level)
                stopLossPrice,
                targetPrice,
                75 // Minimum quantity for options (1 lot)
            );
            
            if (order != null) {
                // Add to active trades cache for P&L tracking
                addActiveTrade(instrumentToken, order);
                
                // Force database update to ensure order is saved
                exitStrategyService.updateOrdersToDatabase();
                
                log.info("📝 TRADE CREATED - {} {} @ {}", 
                        orderType, order.getTradingSymbol(), optionEntryPrice);
                log.info("📊 ORDER DETAILS - ID: {}, Status: {}, StopLoss: {}, Target: {}", 
                        order.getId(), order.getStatus(), order.getStopLossPrice(), order.getTargetPrice());
                log.info("🎯 OPTION PRICE LEVELS - Entry: {}, StopLoss: {} (-{} points), Target: {} (+{} points)", 
                        optionEntryPrice, stopLossPrice, stopLossPoints,
                        targetPrice, targetPoints);
                log.info("📊 INDEX LEVELS - Entry Index: {}, Option Premium: {} (1% of index)", 
                        currentIndexPrice, optionEntryPrice);
                log.info("🔍 ANALYSIS - Using index/future tokens for strategy, placeholder option symbols for orders (no instrument token)");
                log.info("💾 ORDER SAVED TO DATABASE - ID: {}", order.getId());
            } else {
                log.warn("❌ Failed to create trade order - may already have active trade or ExitStrategyService returned null");
                log.debug("Debug: hasActiveOrder() = {}, instrumentToken = {}", 
                        exitStrategyService.hasActiveOrder(), instrumentToken);
            }
            
        } catch (Exception e) {
            log.error("Error creating trade order for tick: {}", tick.getInstrumentToken(), e);
        }
    }
    
    /**
     * Check if we have an active trade for this instrument
     */
    private boolean hasActiveTrade(String instrumentToken) {
        // Check both our local cache and ExitStrategyService
        return activeTrades.containsKey(instrumentToken) || exitStrategyService.hasActiveOrder();
    }
    
    /**
     * Get active trade for this instrument
     */
    private JtradeOrder getActiveTrade(String instrumentToken) {
        // First check our local cache
        JtradeOrder localOrder = activeTrades.get(instrumentToken);
        if (localOrder != null) {
            return localOrder;
        }
        
        // If not in local cache, get from ExitStrategyService
        List<JtradeOrder> activeOrders = exitStrategyService.getActiveOrders();
        if (!activeOrders.isEmpty()) {
            return activeOrders.get(0); // Return first active order
        }
        
        return null;
    }
    
    /**
     * Add trade to active trades cache
     */
    private void addActiveTrade(String instrumentToken, JtradeOrder order) {
        activeTrades.put(instrumentToken, order);
    }
    
    /**
     * Remove trade from active trades cache
     */
    private void removeActiveTrade(String instrumentToken) {
        activeTrades.remove(instrumentToken);
    }
    
    /**
     * Generate unique order ID
     */
    private String generateOrderId() {
        return "ORDER_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }
    
    /**
     * Check and process exits for existing orders based on current tick data
     */
    private void checkAndProcessExits(Tick tick) {
        try {
            // Use enhanced exit checking with strategy-based and time-based exits
            exitStrategyService.checkAndProcessExitsWithStrategy(tick);
            
        } catch (Exception e) {
            log.error("Error checking and processing exits for tick: {}", tick.getInstrumentToken(), e);
        }
    }
    
    
}
