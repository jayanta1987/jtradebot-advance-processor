package com.jtradebot.processor.service;

import com.jtradebot.processor.handler.DateTimeHandler;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.manager.EmaCrossTrackingManager;
import com.jtradebot.processor.service.TickMonitoringService;
import com.jtradebot.processor.service.ScalpingVolumeSurgeService;
import com.jtradebot.processor.model.FlattenedIndicators;
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
import java.util.ArrayList;
import com.jtradebot.processor.model.StrategyScore;
import com.jtradebot.processor.model.EntryQuality;
import com.jtradebot.processor.indicator.MultiEmaIndicator;
import com.jtradebot.processor.indicator.RsiIndicator;
import com.jtradebot.processor.model.EmaInfo;
import org.ta4j.core.BarSeries;
import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TickProcessService {

    private final TickDataManager tickDataManager;
    private final TickSetupService tickSetupService;
    private final TickEventTracker tickEventTracker;
    private final KiteInstrumentHandler kiteInstrumentHandler;
    private final EmaCrossTrackingManager emaCrossTrackingManager;
    private final ScalpingVolumeSurgeService scalpingVolumeSurgeService;
    private final BacktestDataCollectorService backtestDataCollectorService;
    
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
            
            // Process strategy for index ticks only - future data will be fetched from map when needed
            if (instrumentToken.equals(niftyToken)) {
                if (enableScalpingVolumeSurge) {
                    processWithScalpingVolumeSurgeStrategy(tick);
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
     * Future data will be fetched from map when needed
     */
    private void processWithScalpingVolumeSurgeStrategy(Tick indexTick) {
        try {
            // Get future tick from map for volume calculations
            String niftyFutureToken = kiteInstrumentHandler.getNifty50FutureToken().toString();
            Tick futureTick = tickDataManager.getLastTick(niftyFutureToken);
            
            // 🔥 REAL ENTRY LOGIC - This is what actually matters for trading
            logRealEntryLogic(indexTick);
            
            // 📊 Collect backtest data
            backtestDataCollectorService.collectTickData(indexTick);
            
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
    
    /**
     * Log the REAL entry logic from ScalpingVolumeSurgeService - CONCISE ONE-LINER
     */
    private void logRealEntryLogic(Tick indexTick) {
        try {
            // Get the REAL indicators - future data will be fetched from map when needed
            StrategyScore strategyScore = scalpingVolumeSurgeService.calculateStrategyScore(indexTick);
            
            // Get flattened indicators - future data will be fetched from map when needed
            FlattenedIndicators realIndicators = scalpingVolumeSurgeService.getFlattenedIndicators(indexTick);
            
            // Check REAL entry conditions using the enhanced strategy score
            boolean shouldCall = strategyScore.getShouldMakeCallEntry() != null ? strategyScore.getShouldMakeCallEntry() : false;
            boolean shouldPut = strategyScore.getShouldMakePutEntry() != null ? strategyScore.getShouldMakePutEntry() : false;
            
            // Get essential indicator data with actual values
            String emaStatus = getDetailedEmaStatus(realIndicators, indexTick);
            String rsiStatus = getDetailedRsiStatus(realIndicators, indexTick);
            String volumeStatus = getVolumeStatus(realIndicators);
            String entrySignal = getEntrySignal(shouldCall, shouldPut);
            
            // Calculate entry proximity
            String entryProximity = getEntryProximity(realIndicators, indexTick);
            
            // SIMPLIFIED LOG: Show only trend and entry conditions
            String trendInfo = getTrendAndConditionsInfo(strategyScore, realIndicators, indexTick);
            log.info("📊 {} | 💰 {} | {}", 
                indexTick.getTickTimestamp(), 
                indexTick.getLastTradedPrice(), 
                trendInfo);
            
            // Log actual entry signals and execute orders (only when signals are generated)
            if (shouldCall) {
                // Get the actual quality score used for entry decision
                EntryQuality callQuality = scalpingVolumeSurgeService.evaluateCallEntryQuality(scalpingVolumeSurgeService.getFlattenedIndicators(indexTick), indexTick);
                log.info("🚀 CALL ENTRY SIGNAL - Instrument: {}, Price: {}, Quality: {}/10, Time: {}", 
                    indexTick.getInstrumentToken(), indexTick.getLastTradedPrice(), callQuality.getQualityScore(), indexTick.getTickTimestamp());
                
                // Check if we can execute the order (no active orders)
                if (!exitStrategyService.hasActiveOrder()) {
                    // 🔥 EXECUTE CALL ORDER
                    log.info("🎯 EXECUTING CALL ORDER - Quality Score: {}/10 meets threshold", callQuality.getQualityScore());
                    createTradeOrder(indexTick, "CALL_BUY");
                } else {
                    log.warn("⚠️ CALL SIGNAL IGNORED - Active order already exists (Risk management: only one trade at a time)");
                }
            }
            if (shouldPut) {
                // Get the actual quality score used for entry decision
                EntryQuality putQuality = scalpingVolumeSurgeService.evaluatePutEntryQuality(scalpingVolumeSurgeService.getFlattenedIndicators(indexTick), indexTick);
                log.info("📉 PUT ENTRY SIGNAL - Instrument: {}, Price: {}, Quality: {}/10, Time: {}", 
                    indexTick.getInstrumentToken(), indexTick.getLastTradedPrice(), putQuality.getQualityScore(), indexTick.getTickTimestamp());
                
                // Check if we can execute the order (no active orders)
                if (!exitStrategyService.hasActiveOrder()) {
                    // 🔥 EXECUTE PUT ORDER
                    log.info("🎯 EXECUTING PUT ORDER - Quality Score: {}/10 meets threshold", putQuality.getQualityScore());
                    createTradeOrder(indexTick, "PUT_BUY");
                } else {
                    log.warn("⚠️ PUT SIGNAL IGNORED - Active order already exists (Risk management: only one trade at a time)");
                }
            }
            
        } catch (Exception e) {
            log.error("Error logging real entry logic: {}", e.getMessage());
        }
    }
    
    /**
     * Helper method to get detailed EMA status with actual values
     */
    private String getDetailedEmaStatus(FlattenedIndicators indicators, Tick indexTick) {
        if (indicators == null) return "EMA:---";
        
        try {
            // Get BarSeries for different timeframes
            BarSeries oneMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(indexTick.getInstrumentToken()), ONE_MIN);
            BarSeries fiveMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(indexTick.getInstrumentToken()), FIVE_MIN);
            BarSeries fifteenMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(indexTick.getInstrumentToken()), FIFTEEN_MIN);
            
            // Calculate EMA values using MultiEmaIndicator
            MultiEmaIndicator multiEmaIndicator = new MultiEmaIndicator();
            
            String ema1min = "---";
            String ema5min = "---";
            String ema15min = "---";
            
            if (oneMinSeries != null && oneMinSeries.getBarCount() >= 20) {
                EmaInfo emaInfo = multiEmaIndicator.calculateEmaValues(oneMinSeries, ONE_MIN);
                if (emaInfo != null) {
                    double ema9 = emaInfo.getEma9() != null ? emaInfo.getEma9() : 0.0;
                    double ema20 = emaInfo.getEma20() != null ? emaInfo.getEma20() : 0.0;
                    ema1min = String.format("%.1f/%.1f", ema9, ema20);
                }
            }
            
            if (fiveMinSeries != null && fiveMinSeries.getBarCount() >= 20) {
                EmaInfo emaInfo = multiEmaIndicator.calculateEmaValues(fiveMinSeries, FIVE_MIN);
                if (emaInfo != null) {
                    double ema9 = emaInfo.getEma9() != null ? emaInfo.getEma9() : 0.0;
                    double ema20 = emaInfo.getEma20() != null ? emaInfo.getEma20() : 0.0;
                    ema5min = String.format("%.1f/%.1f", ema9, ema20);
                }
            }
            
            if (fifteenMinSeries != null && fifteenMinSeries.getBarCount() >= 20) {
                EmaInfo emaInfo = multiEmaIndicator.calculateEmaValues(fifteenMinSeries, FIFTEEN_MIN);
                if (emaInfo != null) {
                    double ema9 = emaInfo.getEma9() != null ? emaInfo.getEma9() : 0.0;
                    double ema20 = emaInfo.getEma20() != null ? emaInfo.getEma20() : 0.0;
                    ema15min = String.format("%.1f/%.1f", ema9, ema20);
                }
            }
            
            return String.format("EMA:%s|%s|%s", ema1min, ema5min, ema15min);
            
        } catch (Exception e) {
            log.debug("Error getting detailed EMA status: {}", e.getMessage());
            return "EMA:---";
        }
    }
    
    /**
     * Helper method to get detailed RSI status with actual values
     */
    private String getDetailedRsiStatus(FlattenedIndicators indicators, Tick indexTick) {
        if (indicators == null) return "RSI:---";
        
        try {
            // Get BarSeries for different timeframes
            BarSeries oneMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(indexTick.getInstrumentToken()), ONE_MIN);
            BarSeries fiveMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(indexTick.getInstrumentToken()), FIVE_MIN);
            BarSeries fifteenMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(indexTick.getInstrumentToken()), FIFTEEN_MIN);
            
            // Calculate RSI values using RsiIndicator
            RsiIndicator rsiIndicator = new RsiIndicator();
            
            String rsi1min = "---";
            String rsi5min = "---";
            String rsi15min = "---";
            
            if (oneMinSeries != null && oneMinSeries.getBarCount() >= 14) {
                Double rsiValue = rsiIndicator.getRsiValue(oneMinSeries, 14);
                if (rsiValue != null) {
                    rsi1min = String.format("%.1f", rsiValue);
                }
            }
            
            if (fiveMinSeries != null && fiveMinSeries.getBarCount() >= 14) {
                Double rsiValue = rsiIndicator.getRsiValue(fiveMinSeries, 14);
                if (rsiValue != null) {
                    rsi5min = String.format("%.1f", rsiValue);
                }
            }
            
            if (fifteenMinSeries != null && fifteenMinSeries.getBarCount() >= 14) {
                Double rsiValue = rsiIndicator.getRsiValue(fifteenMinSeries, 14);
                if (rsiValue != null) {
                    rsi15min = String.format("%.1f", rsiValue);
                }
            }
            
            return String.format("RSI:%s|%s|%s", rsi1min, rsi5min, rsi15min);
            
        } catch (Exception e) {
            log.debug("Error getting detailed RSI status: {}", e.getMessage());
            return "RSI:---";
        }
    }
    
    /**
     * Helper method to get Volume status in concise format
     */
    private String getVolumeStatus(FlattenedIndicators indicators) {
        if (indicators == null) return "VOL:---";
        
        int surgeCount = 0;
        // Check for volume surge conditions
        if (indicators.getVolume_1min_surge() != null && indicators.getVolume_1min_surge()) surgeCount++;
        if (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) surgeCount++;
        if (indicators.getVolume_15min_surge() != null && indicators.getVolume_15min_surge()) surgeCount++;
        
        String multiplier = "";
        if (indicators.getVolume_surge_multiplier() != null) {
            multiplier = String.format("(%.1fx)", indicators.getVolume_surge_multiplier());
        }
        
        return String.format("VOL:%d/3%s", surgeCount, multiplier);
    }
    
    /**
     * Helper method to get entry signal in concise format
     */
    private String getEntrySignal(boolean shouldCall, boolean shouldPut) {
        if (shouldCall && shouldPut) return "CALL+PUT";
        if (shouldCall) return "CALL";
        if (shouldPut) return "PUT";
        return "NONE";
    }
    
    /**
     * Helper method to calculate entry proximity - how close to entry conditions
     */
    private String getEntryProximity(FlattenedIndicators indicators, Tick indexTick) {
        if (indicators == null) return "PROX:---";
        
        try {
            // Get current price
            double currentPrice = indexTick.getLastTradedPrice();
            
            // Get BarSeries for different timeframes
            BarSeries oneMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(indexTick.getInstrumentToken()), ONE_MIN);
            BarSeries fiveMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(indexTick.getInstrumentToken()), FIVE_MIN);
            
            // Calculate EMA values
            MultiEmaIndicator multiEmaIndicator = new MultiEmaIndicator();
            RsiIndicator rsiIndicator = new RsiIndicator();
            
            // Track conditions for CALL and PUT
            int callConditions = 0;
            int putConditions = 0;
            int totalConditions = 0;
            
            // Check 1min timeframe
            if (oneMinSeries != null && oneMinSeries.getBarCount() >= 20) {
                totalConditions++;
                
                // EMA crossover check
                EmaInfo emaInfo = multiEmaIndicator.calculateEmaValues(oneMinSeries, ONE_MIN);
                if (emaInfo != null && emaInfo.getEma9() != null && emaInfo.getEma20() != null) {
                    if (emaInfo.getEma9() > emaInfo.getEma20()) {
                        callConditions++;
                    } else {
                        putConditions++;
                    }
                }
                
                // RSI check
                Double rsiValue = rsiIndicator.getRsiValue(oneMinSeries, 14);
                if (rsiValue != null) {
                    if (rsiValue > 56) {
                        callConditions++;
                    } else if (rsiValue < 40) {
                        putConditions++;
                    }
                }
            }
            
            // Check 5min timeframe
            if (fiveMinSeries != null && fiveMinSeries.getBarCount() >= 20) {
                totalConditions++;
                
                // EMA crossover check
                EmaInfo emaInfo = multiEmaIndicator.calculateEmaValues(fiveMinSeries, FIVE_MIN);
                if (emaInfo != null && emaInfo.getEma9() != null && emaInfo.getEma20() != null) {
                    if (emaInfo.getEma9() > emaInfo.getEma20()) {
                        callConditions++;
                    } else {
                        putConditions++;
                    }
                }
                
                // RSI check
                Double rsiValue = rsiIndicator.getRsiValue(fiveMinSeries, 14);
                if (rsiValue != null) {
                    if (rsiValue > 56) {
                        callConditions++;
                    } else if (rsiValue < 40) {
                        putConditions++;
                    }
                }
            }
            
            // Volume surge check
            if (indicators.getVolume_1min_surge() != null && indicators.getVolume_1min_surge()) {
                callConditions++;
                putConditions++;
            }
            if (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) {
                callConditions++;
                putConditions++;
            }
            
            // Calculate proximity percentages
            double callProximity = totalConditions > 0 ? (double) callConditions / totalConditions * 100 : 0;
            double putProximity = totalConditions > 0 ? (double) putConditions / totalConditions * 100 : 0;
            
            // Determine which direction is closer to entry
            String direction = callProximity > putProximity ? "CALL" : "PUT";
            double maxProximity = Math.max(callProximity, putProximity);
            
            // Format proximity string
            if (maxProximity >= 80) {
                return String.format("PROX:%s %.0f%% 🔥", direction, maxProximity);
            } else if (maxProximity >= 60) {
                return String.format("PROX:%s %.0f%% ⚡", direction, maxProximity);
            } else if (maxProximity >= 40) {
                return String.format("PROX:%s %.0f%% ⚠️", direction, maxProximity);
            } else {
                return String.format("PROX:%s %.0f%%", direction, maxProximity);
            }
            
        } catch (Exception e) {
            log.debug("Error calculating entry proximity: {}", e.getMessage());
            return "PROX:---";
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
            
            // Capture all conditions that led to this order entry
            List<String> entryConditions = captureEntryConditions(tick, orderType);
            
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
            
            // For both CALL and PUT orders, profit is based on option price movement
            // When option price goes up = profit, when it goes down = loss
            stopLossPrice = optionEntryPrice - stopLossPoints;  // Stop loss below entry for both
            targetPrice = optionEntryPrice + targetPoints;      // Target above entry for both
            
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
                // Store the entry conditions in the order
                order.setEntryConditions(entryConditions);
                
                // Add to active trades cache for P&L tracking
                addActiveTrade(instrumentToken, order);
                
                // Force database update to ensure order is saved
                exitStrategyService.updateOrdersToDatabase();
                
                log.info("📝 TRADE CREATED - {} {} @ {}", 
                        orderType, order.getTradingSymbol(), optionEntryPrice);
                log.info("📊 ORDER DETAILS - ID: {}, Status: {}, StopLoss: {}, Target: {}", 
                        order.getId(), order.getStatus(), order.getStopLossPrice(), order.getTargetPrice());
                log.info("🎯 OPTION PRICE LEVELS - Entry: {}, StopLoss: {} (-{} points), Target: {} (+{} points) - Same logic for CALL/PUT", 
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
    
    /**
     * Get trend and entry conditions info for simplified logging - aligned with actual entry logic
     */
    private String getTrendAndConditionsInfo(StrategyScore strategyScore, FlattenedIndicators indicators, Tick tick) {
        try {
            // Get the actual entry quality scores to align with entry logic
            EntryQuality callQuality = scalpingVolumeSurgeService.evaluateCallEntryQuality(indicators, tick);
            EntryQuality putQuality = scalpingVolumeSurgeService.evaluatePutEntryQuality(indicators, tick);
            
            // Determine dominant trend based on quality scores
            String dominantTrend;
            double dominantQuality;
            String conditionList;
            
            if (callQuality.getQualityScore() > putQuality.getQualityScore()) {
                dominantTrend = "CALL";
                dominantQuality = callQuality.getQualityScore();
                conditionList = String.format("Quality:%.1f/10 | Conditions: %s", 
                    callQuality.getQualityScore(), getConditionCounts(indicators, "CALL"));
            } else if (putQuality.getQualityScore() > callQuality.getQualityScore()) {
                dominantTrend = "PUT";
                dominantQuality = putQuality.getQualityScore();
                conditionList = String.format("Quality:%.1f/10 | Conditions: %s", 
                    putQuality.getQualityScore(), getConditionCounts(indicators, "PUT"));
            } else {
                dominantTrend = "NEUTRAL";
                dominantQuality = Math.max(callQuality.getQualityScore(), putQuality.getQualityScore());
                conditionList = String.format("Quality:%.1f/10 | Conditions: %s", 
                    dominantQuality, getConditionCounts(indicators, "NEUTRAL"));
            }
            
            return String.format("🎯 %s (%.1f/10): %s", dominantTrend, dominantQuality, conditionList);
            
        } catch (Exception e) {
            return "🎯 ERROR";
        }
    }
    
    /**
     * Count mandatory and optional conditions matched for the given strategy
     */
    private String getConditionCounts(FlattenedIndicators indicators, String strategy) {
        try {
            int mandatoryCount = 0;
            int optionalCount = 0;
            
            if ("CALL".equals(strategy)) {
                // Count mandatory conditions for CALL
                if (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) mandatoryCount++;
                if (indicators.getRsi_5min_gt_56() != null && indicators.getRsi_5min_gt_56()) mandatoryCount++;
                if (indicators.getPrice_gt_vwap_5min() != null && indicators.getPrice_gt_vwap_5min()) mandatoryCount++;
                
                // Count optional conditions for CALL
                if (indicators.getEma9_5min_gt_ema21_5min() != null && indicators.getEma9_5min_gt_ema21_5min()) optionalCount++;
                if (indicators.getEma9_1min_gt_ema21_1min() != null && indicators.getEma9_1min_gt_ema21_1min()) optionalCount++;
                if (indicators.getEma9_15min_gt_ema21_15min() != null && indicators.getEma9_15min_gt_ema21_15min()) optionalCount++;
                if (indicators.getVolume_1min_surge() != null && indicators.getVolume_1min_surge()) optionalCount++;
                if (indicators.getVolume_15min_surge() != null && indicators.getVolume_15min_surge()) optionalCount++;
                if (indicators.getRsi_1min_gt_56() != null && indicators.getRsi_1min_gt_56()) optionalCount++;
                if (indicators.getRsi_15min_gt_56() != null && indicators.getRsi_15min_gt_56()) optionalCount++;
                if (indicators.getPrice_gt_vwap_1min() != null && indicators.getPrice_gt_vwap_1min()) optionalCount++;
                if (indicators.getPrice_gt_vwap_15min() != null && indicators.getPrice_gt_vwap_15min()) optionalCount++;
                if (indicators.getPrice_above_resistance() != null && indicators.getPrice_above_resistance()) optionalCount++;
                if (indicators.getBullish_engulfing_5min() != null && indicators.getBullish_engulfing_5min()) optionalCount++;
                if (indicators.getBullish_engulfing_1min() != null && indicators.getBullish_engulfing_1min()) optionalCount++;
                if (indicators.getBullish_morning_star_5min() != null && indicators.getBullish_morning_star_5min()) optionalCount++;
                if (indicators.getBullish_morning_star_1min() != null && indicators.getBullish_morning_star_1min()) optionalCount++;
                if (indicators.getHammer_5min() != null && indicators.getHammer_5min()) optionalCount++;
                if (indicators.getHammer_1min() != null && indicators.getHammer_1min()) optionalCount++;
                if (indicators.getInverted_hammer_5min() != null && indicators.getInverted_hammer_5min()) optionalCount++;
                if (indicators.getInverted_hammer_1min() != null && indicators.getInverted_hammer_1min()) optionalCount++;
                if (indicators.getBullish_harami_5min() != null && indicators.getBullish_harami_5min()) optionalCount++;
                if (indicators.getBullish_harami_1min() != null && indicators.getBullish_harami_1min()) optionalCount++;
                if (indicators.getBullish_marubozu_5min() != null && indicators.getBullish_marubozu_5min()) optionalCount++;
                if (indicators.getBullish_marubozu_1min() != null && indicators.getBullish_marubozu_1min()) optionalCount++;
                if (indicators.getLong_lower_shadow_5min() != null && indicators.getLong_lower_shadow_5min()) optionalCount++;
                if (indicators.getLong_lower_shadow_1min() != null && indicators.getLong_lower_shadow_1min()) optionalCount++;
                if (indicators.getLong_body_5min() != null && indicators.getLong_body_5min()) optionalCount++;
                if (indicators.getLong_body_1min() != null && indicators.getLong_body_1min()) optionalCount++;
                
            } else if ("PUT".equals(strategy)) {
                // Count mandatory conditions for PUT
                if (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) mandatoryCount++;
                if (indicators.getRsi_5min_lt_44() != null && indicators.getRsi_5min_lt_44()) mandatoryCount++;
                if (indicators.getPrice_lt_vwap_5min() != null && indicators.getPrice_lt_vwap_5min()) mandatoryCount++;
                
                // Count optional conditions for PUT
                if (indicators.getEma9_5min_gt_ema21_5min() != null && !indicators.getEma9_5min_gt_ema21_5min()) optionalCount++;
                if (indicators.getEma9_1min_gt_ema21_1min() != null && !indicators.getEma9_1min_gt_ema21_1min()) optionalCount++;
                if (indicators.getEma9_15min_gt_ema21_15min() != null && !indicators.getEma9_15min_gt_ema21_15min()) optionalCount++;
                if (indicators.getVolume_1min_surge() != null && indicators.getVolume_1min_surge()) optionalCount++;
                if (indicators.getVolume_15min_surge() != null && indicators.getVolume_15min_surge()) optionalCount++;
                if (indicators.getRsi_1min_lt_44() != null && indicators.getRsi_1min_lt_44()) optionalCount++;
                if (indicators.getRsi_15min_lt_44() != null && indicators.getRsi_15min_lt_44()) optionalCount++;
                if (indicators.getPrice_lt_vwap_1min() != null && indicators.getPrice_lt_vwap_1min()) optionalCount++;
                if (indicators.getPrice_lt_vwap_15min() != null && indicators.getPrice_lt_vwap_15min()) optionalCount++;
                if (indicators.getPrice_below_support() != null && indicators.getPrice_below_support()) optionalCount++;
                if (indicators.getBearish_engulfing_5min() != null && indicators.getBearish_engulfing_5min()) optionalCount++;
                if (indicators.getBearish_engulfing_1min() != null && indicators.getBearish_engulfing_1min()) optionalCount++;
                if (indicators.getBearish_evening_star_5min() != null && indicators.getBearish_evening_star_5min()) optionalCount++;
                if (indicators.getBearish_evening_star_1min() != null && indicators.getBearish_evening_star_1min()) optionalCount++;
                if (indicators.getShooting_star_5min() != null && indicators.getShooting_star_5min()) optionalCount++;
                if (indicators.getShooting_star_1min() != null && indicators.getShooting_star_1min()) optionalCount++;
                if (indicators.getHanging_man_5min() != null && indicators.getHanging_man_5min()) optionalCount++;
                if (indicators.getHanging_man_1min() != null && indicators.getHanging_man_1min()) optionalCount++;
                if (indicators.getBearish_harami_5min() != null && indicators.getBearish_harami_5min()) optionalCount++;
                if (indicators.getBearish_harami_1min() != null && indicators.getBearish_harami_1min()) optionalCount++;
                if (indicators.getBearish_marubozu_5min() != null && indicators.getBearish_marubozu_5min()) optionalCount++;
                if (indicators.getBearish_marubozu_1min() != null && indicators.getBearish_marubozu_1min()) optionalCount++;
                if (indicators.getLong_upper_shadow_5min() != null && indicators.getLong_upper_shadow_5min()) optionalCount++;
                if (indicators.getLong_upper_shadow_1min() != null && indicators.getLong_upper_shadow_1min()) optionalCount++;
                if (indicators.getShort_body_5min() != null && indicators.getShort_body_5min()) optionalCount++;
                if (indicators.getShort_body_1min() != null && indicators.getShort_body_1min()) optionalCount++;
            }
            
            return String.format("M:%d/%d O:%d/%d", mandatoryCount, 3, optionalCount, 23);
            
        } catch (Exception e) {
            return "M:?/? O:?/?";
        }
    }
    
    /**
     * Capture all conditions that led to the order entry
     */
    private List<String> captureEntryConditions(Tick tick, String orderType) {
        List<String> conditions = new ArrayList<>();
        
        try {
            // Get flattened indicators
            FlattenedIndicators indicators = scalpingVolumeSurgeService.getFlattenedIndicators(tick);
            
            if (indicators == null) {
                conditions.add("ERROR: No indicators available");
                return conditions;
            }
            
            // Add quality scores
            if ("CALL_BUY".equals(orderType)) {
                EntryQuality callQuality = scalpingVolumeSurgeService.evaluateCallEntryQuality(indicators, tick);
                conditions.add(String.format("Quality Score: %.1f/10", callQuality.getQualityScore()));
                conditions.add(String.format("EMA Score: %.1f/10", callQuality.getEmaScore()));
                conditions.add(String.format("RSI Score: %.1f/10", callQuality.getRsiScore()));
                conditions.add(String.format("Volume Score: %.1f/10", callQuality.getVolumeScore()));
                conditions.add(String.format("Price Action Score: %.1f/10", callQuality.getPriceActionScore()));
                conditions.add(String.format("Futuresignal Score: %.1f/10", callQuality.getFuturesignalScore()));
                conditions.add(String.format("Momentum Score: %.1f/10", callQuality.getMomentumScore()));
                conditions.add(String.format("Candlestick Score: %.1f/10", callQuality.getCandlestickScore()));
            } else if ("PUT_BUY".equals(orderType)) {
                EntryQuality putQuality = scalpingVolumeSurgeService.evaluatePutEntryQuality(indicators, tick);
                conditions.add(String.format("Quality Score: %.1f/10", putQuality.getQualityScore()));
                conditions.add(String.format("EMA Score: %.1f/10", putQuality.getEmaScore()));
                conditions.add(String.format("RSI Score: %.1f/10", putQuality.getRsiScore()));
                conditions.add(String.format("Volume Score: %.1f/10", putQuality.getVolumeScore()));
                conditions.add(String.format("Price Action Score: %.1f/10", putQuality.getPriceActionScore()));
                conditions.add(String.format("Futuresignal Score: %.1f/10", putQuality.getFuturesignalScore()));
                conditions.add(String.format("Momentum Score: %.1f/10", putQuality.getMomentumScore()));
                conditions.add(String.format("Candlestick Score: %.1f/10", putQuality.getCandlestickScore()));
            }
            
            // Add mandatory conditions
            conditions.add("--- MANDATORY CONDITIONS ---");
            if ("CALL_BUY".equals(orderType)) {
                if (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) {
                    conditions.add("✓ volume_5min_surge");
                } else {
                    conditions.add("✗ volume_5min_surge");
                }
                if (indicators.getRsi_5min_gt_56() != null && indicators.getRsi_5min_gt_56()) {
                    conditions.add("✓ rsi_5min_gt_56");
                } else {
                    conditions.add("✗ rsi_5min_gt_56");
                }
                if (indicators.getPrice_gt_vwap_5min() != null && indicators.getPrice_gt_vwap_5min()) {
                    conditions.add("✓ price_5min_gt_vwap");
                } else {
                    conditions.add("✗ price_5min_gt_vwap");
                }
            } else if ("PUT_BUY".equals(orderType)) {
                if (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) {
                    conditions.add("✓ volume_5min_surge");
                } else {
                    conditions.add("✗ volume_5min_surge");
                }
                if (indicators.getRsi_5min_lt_44() != null && indicators.getRsi_5min_lt_44()) {
                    conditions.add("✓ rsi_5min_lt_44");
                } else {
                    conditions.add("✗ rsi_5min_lt_44");
                }
                if (indicators.getPrice_lt_vwap_5min() != null && indicators.getPrice_lt_vwap_5min()) {
                    conditions.add("✓ price_5min_lt_vwap");
                } else {
                    conditions.add("✗ price_5min_lt_vwap");
                }
            }
            
            // Add optional conditions
            conditions.add("--- OPTIONAL CONDITIONS ---");
            if ("CALL_BUY".equals(orderType)) {
                // EMA conditions
                if (indicators.getEma9_5min_gt_ema21_5min() != null && indicators.getEma9_5min_gt_ema21_5min()) {
                    conditions.add("✓ ema9_5min_gt_ema21_5min");
                }
                if (indicators.getEma9_1min_gt_ema21_1min() != null && indicators.getEma9_1min_gt_ema21_1min()) {
                    conditions.add("✓ ema9_1min_gt_ema21_1min");
                }
                if (indicators.getEma9_15min_gt_ema21_15min() != null && indicators.getEma9_15min_gt_ema21_15min()) {
                    conditions.add("✓ ema9_15min_gt_ema21_15min");
                }
                
                // Volume conditions
                if (indicators.getVolume_1min_surge() != null && indicators.getVolume_1min_surge()) {
                    conditions.add("✓ volume_1min_surge");
                }
                if (indicators.getVolume_15min_surge() != null && indicators.getVolume_15min_surge()) {
                    conditions.add("✓ volume_15min_surge");
                }
                
                // RSI conditions
                if (indicators.getRsi_1min_gt_56() != null && indicators.getRsi_1min_gt_56()) {
                    conditions.add("✓ rsi_1min_gt_56");
                }
                if (indicators.getRsi_15min_gt_56() != null && indicators.getRsi_15min_gt_56()) {
                    conditions.add("✓ rsi_15min_gt_56");
                }
                
                // Price action conditions
                if (indicators.getPrice_gt_vwap_1min() != null && indicators.getPrice_gt_vwap_1min()) {
                    conditions.add("✓ price_gt_vwap_1min");
                }
                if (indicators.getPrice_gt_vwap_15min() != null && indicators.getPrice_gt_vwap_15min()) {
                    conditions.add("✓ price_gt_vwap_15min");
                }
                if (indicators.getPrice_above_resistance() != null && indicators.getPrice_above_resistance()) {
                    conditions.add("✓ price_above_resistance");
                }
                
                // Candlestick patterns
                if (indicators.getBullish_engulfing_5min() != null && indicators.getBullish_engulfing_5min()) {
                    conditions.add("✓ bullish_engulfing_5min");
                }
                if (indicators.getBullish_engulfing_1min() != null && indicators.getBullish_engulfing_1min()) {
                    conditions.add("✓ bullish_engulfing_1min");
                }
                if (indicators.getBullish_morning_star_5min() != null && indicators.getBullish_morning_star_5min()) {
                    conditions.add("✓ bullish_morning_star_5min");
                }
                if (indicators.getBullish_morning_star_1min() != null && indicators.getBullish_morning_star_1min()) {
                    conditions.add("✓ bullish_morning_star_1min");
                }
                if (indicators.getHammer_5min() != null && indicators.getHammer_5min()) {
                    conditions.add("✓ hammer_5min");
                }
                if (indicators.getHammer_1min() != null && indicators.getHammer_1min()) {
                    conditions.add("✓ hammer_1min");
                }
                if (indicators.getInverted_hammer_5min() != null && indicators.getInverted_hammer_5min()) {
                    conditions.add("✓ inverted_hammer_5min");
                }
                if (indicators.getInverted_hammer_1min() != null && indicators.getInverted_hammer_1min()) {
                    conditions.add("✓ inverted_hammer_1min");
                }
                if (indicators.getBullish_harami_5min() != null && indicators.getBullish_harami_5min()) {
                    conditions.add("✓ bullish_harami_5min");
                }
                if (indicators.getBullish_harami_1min() != null && indicators.getBullish_harami_1min()) {
                    conditions.add("✓ bullish_harami_1min");
                }
                if (indicators.getBullish_marubozu_5min() != null && indicators.getBullish_marubozu_5min()) {
                    conditions.add("✓ bullish_marubozu_5min");
                }
                if (indicators.getBullish_marubozu_1min() != null && indicators.getBullish_marubozu_1min()) {
                    conditions.add("✓ bullish_marubozu_1min");
                }
                if (indicators.getLong_lower_shadow_5min() != null && indicators.getLong_lower_shadow_5min()) {
                    conditions.add("✓ long_lower_shadow_5min");
                }
                if (indicators.getLong_lower_shadow_1min() != null && indicators.getLong_lower_shadow_1min()) {
                    conditions.add("✓ long_lower_shadow_1min");
                }
                if (indicators.getLong_body_5min() != null && indicators.getLong_body_5min()) {
                    conditions.add("✓ long_body_5min");
                }
                if (indicators.getLong_body_1min() != null && indicators.getLong_body_1min()) {
                    conditions.add("✓ long_body_1min");
                }
                
            } else if ("PUT_BUY".equals(orderType)) {
                // EMA conditions (bearish)
                if (indicators.getEma9_5min_gt_ema21_5min() != null && !indicators.getEma9_5min_gt_ema21_5min()) {
                    conditions.add("✓ ema9_5min_lt_ema21_5min (bearish)");
                }
                if (indicators.getEma9_1min_gt_ema21_1min() != null && !indicators.getEma9_1min_gt_ema21_1min()) {
                    conditions.add("✓ ema9_1min_lt_ema21_1min (bearish)");
                }
                if (indicators.getEma9_15min_gt_ema21_15min() != null && !indicators.getEma9_15min_gt_ema21_15min()) {
                    conditions.add("✓ ema9_15min_lt_ema21_15min (bearish)");
                }
                
                // Volume conditions
                if (indicators.getVolume_1min_surge() != null && indicators.getVolume_1min_surge()) {
                    conditions.add("✓ volume_1min_surge");
                }
                if (indicators.getVolume_15min_surge() != null && indicators.getVolume_15min_surge()) {
                    conditions.add("✓ volume_15min_surge");
                }
                
                // RSI conditions
                if (indicators.getRsi_1min_lt_44() != null && indicators.getRsi_1min_lt_44()) {
                    conditions.add("✓ rsi_1min_lt_44");
                }
                if (indicators.getRsi_15min_lt_44() != null && indicators.getRsi_15min_lt_44()) {
                    conditions.add("✓ rsi_15min_lt_44");
                }
                
                // Price action conditions
                if (indicators.getPrice_lt_vwap_1min() != null && indicators.getPrice_lt_vwap_1min()) {
                    conditions.add("✓ price_lt_vwap_1min");
                }
                if (indicators.getPrice_lt_vwap_15min() != null && indicators.getPrice_lt_vwap_15min()) {
                    conditions.add("✓ price_lt_vwap_15min");
                }
                if (indicators.getPrice_below_support() != null && indicators.getPrice_below_support()) {
                    conditions.add("✓ price_below_support");
                }
                
                // Candlestick patterns
                if (indicators.getBearish_engulfing_5min() != null && indicators.getBearish_engulfing_5min()) {
                    conditions.add("✓ bearish_engulfing_5min");
                }
                if (indicators.getBearish_engulfing_1min() != null && indicators.getBearish_engulfing_1min()) {
                    conditions.add("✓ bearish_engulfing_1min");
                }
                if (indicators.getBearish_evening_star_5min() != null && indicators.getBearish_evening_star_5min()) {
                    conditions.add("✓ bearish_evening_star_5min");
                }
                if (indicators.getBearish_evening_star_1min() != null && indicators.getBearish_evening_star_1min()) {
                    conditions.add("✓ bearish_evening_star_1min");
                }
                if (indicators.getShooting_star_5min() != null && indicators.getShooting_star_5min()) {
                    conditions.add("✓ shooting_star_5min");
                }
                if (indicators.getShooting_star_1min() != null && indicators.getShooting_star_1min()) {
                    conditions.add("✓ shooting_star_1min");
                }
                if (indicators.getHanging_man_5min() != null && indicators.getHanging_man_5min()) {
                    conditions.add("✓ hanging_man_5min");
                }
                if (indicators.getHanging_man_1min() != null && indicators.getHanging_man_1min()) {
                    conditions.add("✓ hanging_man_1min");
                }
                if (indicators.getBearish_harami_5min() != null && indicators.getBearish_harami_5min()) {
                    conditions.add("✓ bearish_harami_5min");
                }
                if (indicators.getBearish_harami_1min() != null && indicators.getBearish_harami_1min()) {
                    conditions.add("✓ bearish_harami_1min");
                }
                if (indicators.getBearish_marubozu_5min() != null && indicators.getBearish_marubozu_5min()) {
                    conditions.add("✓ bearish_marubozu_5min");
                }
                if (indicators.getBearish_marubozu_1min() != null && indicators.getBearish_marubozu_1min()) {
                    conditions.add("✓ bearish_marubozu_1min");
                }
                if (indicators.getLong_upper_shadow_5min() != null && indicators.getLong_upper_shadow_5min()) {
                    conditions.add("✓ long_upper_shadow_5min");
                }
                if (indicators.getLong_upper_shadow_1min() != null && indicators.getLong_upper_shadow_1min()) {
                    conditions.add("✓ long_upper_shadow_1min");
                }
                if (indicators.getShort_body_5min() != null && indicators.getShort_body_5min()) {
                    conditions.add("✓ short_body_5min");
                }
                if (indicators.getShort_body_1min() != null && indicators.getShort_body_1min()) {
                    conditions.add("✓ short_body_1min");
                }
            }
            
            // Add futuresignal information
            if (indicators.getFuturesignals() != null) {
                conditions.add("--- FUTURESIGNALS ---");
                if (indicators.getFuturesignals().getAllTimeframesBullish()) {
                    conditions.add("✓ All timeframes bullish");
                } else if (indicators.getFuturesignals().getAllTimeframesBearish()) {
                    conditions.add("✓ All timeframes bearish");
                } else {
                    conditions.add("Mixed futuresignals");
                }
            }
            
            // Add volume surge multiplier if available
            if (indicators.getVolume_surge_multiplier() != null) {
                conditions.add(String.format("Volume Surge Multiplier: %.2fx", indicators.getVolume_surge_multiplier()));
            }
            
        } catch (Exception e) {
            conditions.add("ERROR: Failed to capture conditions - " + e.getMessage());
            log.error("Error capturing entry conditions: {}", e.getMessage());
        }
        
        return conditions;
    }
    
    
}
