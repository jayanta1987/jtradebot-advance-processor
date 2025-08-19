package com.jtradebot.processor.service;

import com.jtradebot.processor.handler.DateTimeHandler;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.manager.EmaCrossTrackingManager;
import com.jtradebot.processor.service.TickMonitoringService;
import com.jtradebot.processor.service.ScalpingVolumeSurgeService;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.repository.document.JtradeOrder;
import com.jtradebot.processor.repository.document.TickDocument;
import com.jtradebot.processor.repository.JtradeOrderRepository;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.service.OptionPricingService;
import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.config.TradingConfigurationService;
import com.jtradebot.processor.service.EntryConditionAnalysisService;
import com.jtradebot.processor.service.MarketConditionAnalysisService;
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
import java.util.Optional;
import com.jtradebot.processor.model.strategy.StrategyScore;
import com.jtradebot.processor.model.strategy.ScalpingEntryDecision;
import com.jtradebot.processor.model.strategy.ScalpingEntryConfig;
import com.jtradebot.processor.model.indicator.EntryQuality;
import com.jtradebot.processor.service.ProfitableTradeFilterService;
import com.jtradebot.processor.model.strategy.ProfitableTradeFilterResult;
import com.jtradebot.processor.indicator.MultiEmaIndicator;
import com.jtradebot.processor.indicator.RsiIndicator;
import com.jtradebot.processor.model.indicator.EmaInfo;
import org.ta4j.core.BarSeries;
import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.*;
import com.jtradebot.processor.model.strategy.FlatMarketFilteringConfig;
import org.springframework.core.env.Environment;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Quote;

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
    private final TradingConfigurationService tradingConfigService;
    private final EntryConditionAnalysisService entryConditionAnalysisService;
    private final TickMonitoringService tickMonitoringService;
    private final ProfitableTradeFilterService profitableTradeFilterService;
    private final MarketConditionAnalysisService marketConditionAnalysisService;
    private final LiveOptionPricingService liveOptionPricingService;
    private final Environment environment;
    private final KiteConnect kiteConnect;
    
    // Active trades cache
    private final Map<String, JtradeOrder> activeTrades = new ConcurrentHashMap<>();

    @Value("${jtradebot.strategy.enable-scalping-volume-surge:true}")
    private boolean enableScalpingVolumeSurge;

    public void processLiveTicks(List<Tick> ticks) throws KiteException {
        processLiveTicks(ticks, false);
    }
    
    public void processLiveTicks(List<Tick> ticks, boolean skipMarketHoursCheck) throws KiteException {
        // IGNORE IF MARKET NOT STARTED YET (unless backtesting)
        if (!skipMarketHoursCheck && !DateTimeHandler.isMarketOpen()) {
            log.info("Market not started yet. Skipping tick processing. Current time: {}", new Date());
            return;
        }
        
        if (skipMarketHoursCheck) {
            // Removed verbose debug logging
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
            
            // 🔥 OPTIMIZATION: Calculate indicators ONCE per tick to avoid redundant calculations
            FlattenedIndicators indicators = scalpingVolumeSurgeService.getFlattenedIndicators(indexTick);
            
            // 🔥 OPTIMIZATION: Calculate market condition analysis ONCE per tick to avoid redundant calculations
            boolean isMarketSuitable = marketConditionAnalysisService.isMarketConditionSuitable(indexTick, indicators);
            String detailedFlatMarketReason = null;
            if (!isMarketSuitable) {
                detailedFlatMarketReason = marketConditionAnalysisService.getDetailedFlatMarketReason(indexTick, indicators);
            }
            
            // 🔥 REAL ENTRY LOGIC - This is what actually matters for trading
            logRealEntryLogic(indexTick, indicators, isMarketSuitable, detailedFlatMarketReason);
            
            // 📊 Collect backtest data
            //backtestDataCollectorService.collectTickData(indexTick);
            
            // Check and process exits for existing orders
            checkAndProcessExits(indexTick);
            
            // Process new entry signals and manage active trades (using index tick for price data)
            processEntrySignals(indexTick);
            
            // Update live P&L for active trades (using index tick for price data)
            try {
                updateLivePnL(indexTick);
            } catch (KiteException e) {
                log.error("Error updating live P&L: {}", e.getMessage());
            }
            
        } catch (Exception e) {
            log.error("Error processing tick with SCALPING_FUTURE_VOLUME_SURGE strategy for index instrument: {}", indexTick.getInstrumentToken(), e);
        }
    }
    
    /**
     * Log the REAL entry logic from ScalpingVolumeSurgeService - CONCISE ONE-LINER
     */
    private void logRealEntryLogic(Tick indexTick, FlattenedIndicators indicators, boolean isMarketSuitable, String detailedFlatMarketReason) {
        try {
            // Use the passed indicators instead of recalculating
            FlattenedIndicators realIndicators = indicators;
            
            // Use NEW scenario-based entry logic instead of old StrategyScore approach
            ScalpingEntryDecision entryDecision = scalpingVolumeSurgeService.getEntryDecision(indexTick, indicators);
            
            // 🔥 OPTIMIZATION: Calculate entry quality scores ONCE to avoid redundant calculations
            EntryQuality callQuality = scalpingVolumeSurgeService.evaluateCallEntryQuality(indicators, indexTick);
            EntryQuality putQuality = scalpingVolumeSurgeService.evaluatePutEntryQuality(indicators, indexTick);
            
            // Check entry conditions using the new scenario-based logic
            // If scenario passes, determine market direction to decide order type
            boolean scenarioPassed = entryDecision.isShouldEntry();
            
            // Debug entry decisions (only log success to reduce noise)
            if (scenarioPassed) {
                log.info("✅ ENTRY SCENARIO PASSED - Scenario: {}, Confidence: {}/10", 
                    entryDecision.getScenarioName(), entryDecision.getConfidence());
            }
            // Removed failed scenario logging to reduce noise
            
            // Use the quality score from entryDecision to avoid duplicate calculations
            double qualityScore = entryDecision.getQualityScore();
            
            // For now, use a simple heuristic for direction - in a real implementation,
            // you'd want to store both call and put quality scores in the entryDecision
            // For now, assume CALL direction if we have a valid quality score
            boolean isCallDominant = qualityScore > 0;
            boolean isPutDominant = false; // Simplified for now
            
            // Only create orders if scenario passes AND dominant direction is clear
            boolean shouldCall = scenarioPassed && isCallDominant;
            boolean shouldPut = scenarioPassed && isPutDominant;
            
            // Market direction info will be added to the main log
            
            // Removed verbose scenario passed log
            
            // Get essential indicator data with actual values
            String emaStatus = getDetailedEmaStatus(realIndicators, indexTick);
            String rsiStatus = getDetailedRsiStatus(realIndicators, indexTick);
            String volumeStatus = getVolumeStatus(realIndicators);
            String entrySignal = getEntrySignal(shouldCall, shouldPut);
            
            // Calculate entry proximity
            String entryProximity = getEntryProximity(realIndicators, indexTick);
            
            // Get trend and conditions info
            String trendInfo = getTrendAndConditionsInfo(entryDecision, realIndicators, indexTick, isMarketSuitable, detailedFlatMarketReason, callQuality, putQuality);
            
            // LOG: Show trend analysis with category conditions and market direction
            // Add market direction info to all logs using dominant trend logic with colors
            String callStatus = isCallDominant ? "🟢" : "⚫";
            String putStatus = isPutDominant ? "🟢" : "⚫";
            String marketDirectionInfo = String.format(" | 🎯 Call:%s Put:%s", callStatus, putStatus);
            log.info("📊 {} | 💰 {} | {}{}", 
                indexTick.getTickTimestamp(), 
                indexTick.getLastTradedPrice(), 
                trendInfo, marketDirectionInfo);
            
            // Log actual entry signals and execute orders (only when signals are generated)
            if (shouldCall || shouldPut) {
                // Reuse the already computed scenario-based entry decision to avoid mismatch
                ScalpingEntryDecision scenarioDecision = entryDecision;
                
                if (scenarioDecision != null && scenarioDecision.isShouldEntry()) {
                    String orderType = shouldCall ? "CALL" : "PUT";
                    log.info("🚀 {} ENTRY SIGNAL - Instrument: {}, Price: {}, Scenario: {}, Confidence: {}/10, Time: {}", 
                        orderType, indexTick.getInstrumentToken(), indexTick.getLastTradedPrice(), 
                        scenarioDecision.getScenarioName(), scenarioDecision.getConfidence(), indexTick.getTickTimestamp());
                    
                    // Check if we can execute the order (no active orders)
                    if (!exitStrategyService.hasActiveOrder()) {
                        // 🔥 EXECUTE ORDER
                        log.info("🎯 EXECUTING {} ORDER - Scenario: {}, Confidence: {}/10", 
                            orderType, scenarioDecision.getScenarioName(), scenarioDecision.getConfidence());
                        try {
                            createTradeOrder(indexTick, shouldCall ? "CALL_BUY" : "PUT_BUY", entryDecision, indicators, callQuality, putQuality);
                        } catch (KiteException e) {
                            log.error("Error creating trade order: {}", e.getMessage());
                        }
                    } else {
                        log.warn("⚠️ ACTIVE ORDER EXISTS - Cannot create new order. Active orders: {}", 
                            exitStrategyService.hasActiveOrder());
                    }
                } else {
                    log.debug("⏸️ ENTRY SKIPPED - Scenario decision no longer valid at execution time (shouldCall: {}, shouldPut: {}, decisionPresent: {}, decisionAllows: {})", 
                        shouldCall, shouldPut, scenarioDecision != null, scenarioDecision != null && scenarioDecision.isShouldEntry());
                }
            }
            
        } catch (Exception e) {
            log.error("Error logging real entry logic: {}", e.getMessage());
        }
    }
    

    
    /**
     * Determine if market conditions are bullish for CALL entries
     */
    boolean isMarketConditionBullish(FlattenedIndicators indicators) {
        if (indicators == null) return false;
        
        int bullishSignals = 0;
        int totalSignals = 0;
        
        // EMA conditions (bullish when EMA9 > EMA21)
        if (Boolean.TRUE.equals(indicators.getEma5_5min_gt_ema34_5min())) bullishSignals++;
        if (Boolean.TRUE.equals(indicators.getEma5_1min_gt_ema34_1min())) bullishSignals++;
        if (Boolean.TRUE.equals(indicators.getEma5_15min_gt_ema34_15min())) bullishSignals++;
        totalSignals += 3;
        
        // Price action conditions (bullish when price > VWAP)
        if (Boolean.TRUE.equals(indicators.getPrice_gt_vwap_5min())) bullishSignals++;
        if (Boolean.TRUE.equals(indicators.getPrice_gt_vwap_1min())) bullishSignals++;
        if (Boolean.TRUE.equals(indicators.getPrice_gt_vwap_15min())) bullishSignals++;
        totalSignals += 3;
        
        // RSI conditions (bullish when RSI > 56)
        if (Boolean.TRUE.equals(indicators.getRsi_5min_gt_56())) bullishSignals++;
        if (Boolean.TRUE.equals(indicators.getRsi_1min_gt_56())) bullishSignals++;
        if (Boolean.TRUE.equals(indicators.getRsi_15min_gt_56())) bullishSignals++;
        totalSignals += 3;
        
        // Candlestick conditions (bullish patterns)
        if (Boolean.TRUE.equals(indicators.getGreen_candle_5min())) bullishSignals++;
        if (Boolean.TRUE.equals(indicators.getGreen_candle_1min())) bullishSignals++;
        totalSignals += 2;
        
        // Require at least 60% of signals to be bullish
        boolean isBullish = totalSignals > 0 && (double) bullishSignals / totalSignals >= 0.6;
        
        // Debug logging for bullish signals - REMOVED
        
        return isBullish;
    }
    
    /**
     * Determine if market conditions are bearish for PUT entries
     */
    boolean isMarketConditionBearish(FlattenedIndicators indicators) {
        if (indicators == null) return false;
        
        int bearishSignals = 0;
        int totalSignals = 0;
        
        // EMA conditions (bearish when EMA9 < EMA21)
        if (Boolean.TRUE.equals(indicators.getEma5_5min_lt_ema34_5min())) bearishSignals++;
        if (Boolean.TRUE.equals(indicators.getEma5_1min_lt_ema34_1min())) bearishSignals++;
        if (Boolean.TRUE.equals(indicators.getEma5_15min_lt_ema34_15min())) bearishSignals++;
        totalSignals += 3;
        
        // Price action conditions (bearish when price < VWAP)
        if (Boolean.TRUE.equals(indicators.getPrice_lt_vwap_5min())) bearishSignals++;
        if (Boolean.TRUE.equals(indicators.getPrice_lt_vwap_1min())) bearishSignals++;
        if (Boolean.TRUE.equals(indicators.getPrice_lt_vwap_15min())) bearishSignals++;
        totalSignals += 3;
        
        // RSI conditions (bearish when RSI < 44)
        if (Boolean.TRUE.equals(indicators.getRsi_5min_lt_44())) bearishSignals++;
        if (Boolean.TRUE.equals(indicators.getRsi_1min_lt_44())) bearishSignals++;
        if (Boolean.TRUE.equals(indicators.getRsi_15min_lt_44())) bearishSignals++;
        totalSignals += 3;
        
        // Candlestick conditions (bearish patterns)
        if (Boolean.TRUE.equals(indicators.getBearish_engulfing_5min())) bearishSignals++;
        if (Boolean.TRUE.equals(indicators.getBearish_engulfing_1min())) bearishSignals++;
        totalSignals += 2;
        
        // Require at least 60% of signals to be bearish
        boolean isBearish = totalSignals > 0 && (double) bearishSignals / totalSignals >= 0.6;
        
        // Debug logging for bearish signals - REMOVED
        
        return isBearish;
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
            
            // Removed verbose debug logging
            
            // Check: if ExitStrategyService has any active order, skip (only one active order allowed globally)
            if (exitStrategyService.hasActiveOrder()) {
                // Removed verbose active order debug log
                return;
            }
            
            // Note: Entry decisions are now handled by the dynamic configuration system
            // This method is kept for compatibility but actual entry logic is in the dynamic system
            // Removed verbose debug logging
            
        } catch (Exception e) {
            log.error("Error processing entry signals for tick: {}", tick.getInstrumentToken(), e);
        }
    }
    
    /**
     * Update live P&L for active trades
     */
    private void updateLivePnL(Tick tick) throws KiteException {
        try {
            // Get active order from ExitStrategyService (global check)
            List<JtradeOrder> activeOrders = exitStrategyService.getActiveOrders();
            if (activeOrders.isEmpty()) {
                return;
            }
            
            JtradeOrder activeOrder = activeOrders.get(0); // Get the first active order
            
            double currentOptionPrice;
            double currentIndexPrice;
            
            // Check if we're in live profile and have real option instrument token
            if (isLiveProfile() && activeOrder.getInstrumentToken() != null && activeOrder.getInstrumentToken() > 0) {
                // Get real option LTP for live profile using Kite Connect API
                String instrumentToken = String.valueOf(activeOrder.getInstrumentToken());
                currentOptionPrice = kiteConnect.getLTP(new String[]{instrumentToken}).get(instrumentToken).lastPrice;
                
                // Get current Nifty index price for reference
                String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();
                Tick niftyTick = tickDataManager.getLastTick(niftyToken);
                currentIndexPrice = niftyTick != null ? niftyTick.getLastTradedPrice() : activeOrder.getEntryIndexPrice();
                
                log.debug("🎯 USING REAL OPTION LTP FROM KITE API - Token: {}, Symbol: {}, LTP: {}, Index: {}", 
                        activeOrder.getInstrumentToken(), activeOrder.getTradingSymbol(), currentOptionPrice, currentIndexPrice);
            } else {
                // Use calculated option price for local profile or when no real instrument token
                String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();
                Tick niftyTick = tickDataManager.getLastTick(niftyToken);
                if (niftyTick == null) {
                    log.warn("No Nifty index data available for P&L calculation");
                    return;
                }
                
                currentIndexPrice = niftyTick.getLastTradedPrice();
                double entryIndexPrice = activeOrder.getEntryIndexPrice();
                double entryOptionPrice = activeOrder.getEntryPrice();
                
                // Calculate current option price based on index movement
                currentOptionPrice = optionPricingService.calculateCurrentLTP(entryOptionPrice, entryIndexPrice, currentIndexPrice, activeOrder.getOrderType());
                
                log.debug("📊 USING CALCULATED OPTION PRICE - Entry: {}, Current: {}, Index: {}", 
                        entryOptionPrice, currentOptionPrice, currentIndexPrice);
            }
            
            double entryOptionPrice = activeOrder.getEntryPrice();
            double points = currentOptionPrice - entryOptionPrice;
            double pnl = points * activeOrder.getQuantity();
            
            // Simplified live P&L log (only points and P&L)
            String orderTypeDisplay = OrderTypeEnum.CALL_BUY.equals(activeOrder.getOrderType()) ? "CALL" : "PUT";
            log.info("_________________________ 💰 LIVE P&L - {} | Points: {}, P&L: ₹{} _________________________", 
                    orderTypeDisplay, String.format("%+.2f", points), String.format("%.2f", pnl));
            
        } catch (Exception e) {
            log.error("Error updating live P&L for tick: {}", tick.getInstrumentToken(), e);
        }
    }
    
    /**
     * Check if we're in live profile
     */
    private boolean isLiveProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if ("live".equals(profile)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Create a new trade order and save to DB
     */
    private void createTradeOrder(Tick tick, String orderType, ScalpingEntryDecision entryDecision, FlattenedIndicators indicators, EntryQuality callQuality, EntryQuality putQuality) throws KiteException {
        try {
            String instrumentToken = String.valueOf(tick.getInstrumentToken());
            
            // Capture all conditions that led to this order entry (reuse entryDecision)
            List<String> entryConditions = entryConditionAnalysisService.captureEntryConditions(tick, orderType, entryDecision, indicators);
            
            // For scalping, use point-based targets from JSON configuration
            double stopLossPoints, targetPoints;
            
            if ("CALL_BUY".equals(orderType)) {
                stopLossPoints = configService.getCallStopLossPoints();
                targetPoints = configService.getCallTargetPoints();
            } else {
                stopLossPoints = configService.getPutStopLossPoints();
                targetPoints = configService.getPutTargetPoints();
            }
            
            // Try to get live option pricing first (for live profile)
            Optional<LiveOptionPricingService.LiveOptionPricingInfo> livePricing = liveOptionPricingService.getLiveOptionPricing(orderType);
            
            Double optionEntryPrice, stopLossPrice, targetPrice;
            String optionSymbol;
            Long optionInstrumentToken;
            double currentIndexPrice;
            
            if (livePricing.isPresent()) {
                // Use live option pricing
                LiveOptionPricingService.LiveOptionPricingInfo pricingInfo = livePricing.get();
                optionEntryPrice = pricingInfo.getOptionLTP();
                stopLossPrice = Math.max(0.0, optionEntryPrice - stopLossPoints);
                targetPrice = optionEntryPrice + targetPoints;
                optionSymbol = pricingInfo.getOptionInstrument().getTradingSymbol();
                optionInstrumentToken = pricingInfo.getOptionInstrument().getInstrumentToken();
                currentIndexPrice = pricingInfo.getNiftyIndexPrice();
                
                log.info("🎯 USING LIVE OPTION PRICING - Symbol: {}, LTP: {}, Strike: {}, Index: {}", 
                        optionSymbol, optionEntryPrice, pricingInfo.getStrikePrice(), currentIndexPrice);
            } else {
                // Fallback to placeholder pricing (for local profile or when live pricing fails)
                String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();
                Tick niftyTick = tickDataManager.getLastTick(niftyToken);
                if (niftyTick == null) {
                    log.error("No Nifty index data available for order creation");
                    return;
                }
                
                currentIndexPrice = niftyTick.getLastTradedPrice();
                
                // Calculate option entry price (1% of index price as premium)
                optionEntryPrice = optionPricingService.calculateEntryPrice(currentIndexPrice);
                if (optionEntryPrice == null) {
                    log.error("Failed to calculate option entry price for index: {}", currentIndexPrice);
                    return;
                }
                
                // Calculate stop loss and target prices directly (point-based, not percentage-based)
                stopLossPrice = Math.max(0.0, optionEntryPrice - stopLossPoints);
                targetPrice = optionEntryPrice + targetPoints;
                
                // Use placeholder option symbols since we're analyzing with index/future tokens
                optionSymbol = "CALL_BUY".equals(orderType) ? "TEST_OPTION_CE" : "TEST_OPTION_PE";
                optionInstrumentToken = 0L; // No instrument token (using placeholder symbols)
                
                log.info("📊 USING PLACEHOLDER PRICING - Index: {}, Premium: {} (1% of index)", 
                        currentIndexPrice, optionEntryPrice);
            }
            
            if (stopLossPrice == null || targetPrice == null) {
                log.error("Failed to calculate stop loss or target price for option");
                return;
            }
            
            // Use the passed entryDecision (no need to recalculate)
            
            JtradeOrder order;
            if (entryDecision.isShouldEntry() && entryDecision.getScenarioName() != null) {
                // Create order with scenario information
                order = exitStrategyService.createOrderEntryWithScenario(
                    OrderTypeEnum.valueOf(orderType),
                    optionSymbol,
                    optionInstrumentToken,
                    optionEntryPrice, // Option entry price (premium)
                    currentIndexPrice, // Entry index price (Nifty level)
                    stopLossPrice,
                    targetPrice,
                    tradingConfigService.getMinLotSize(), // Get configured minimum lot size from JSON
                    entryDecision.getScenarioName(),
                    entryDecision.getReason(), // Use reason as description
                    entryDecision.getConfidence(),
                    entryDecision.getCategoryScores(),
                    entryDecision.getMatchedConditions(),
                    tick.getTickTimestamp() // Use tick timestamp instead of current time
                );
            } else {
                // Fallback to regular order creation if no scenario info
                order = exitStrategyService.createOrderEntry(
                    OrderTypeEnum.valueOf(orderType),
                    optionSymbol,
                    optionInstrumentToken,
                    optionEntryPrice, // Option entry price (premium)
                    currentIndexPrice, // Entry index price (Nifty level)
                    stopLossPrice,
                    targetPrice,
                    tradingConfigService.getMinLotSize(), // Get configured minimum lot size from JSON
                    tick.getTickTimestamp() // Use tick timestamp instead of current time
                );
            }
            
            if (order != null) {
                // Store the entry conditions in the order
                order.setEntryConditions(entryConditions);
                
                // Store profitable trade filter information
                storeProfitableTradeFilterInfo(order, tick, orderType, indicators, callQuality, putQuality);
                
                // Add to active trades cache for P&L tracking
                addActiveTrade(instrumentToken, order);
                
                // Force database update to ensure order is saved
                exitStrategyService.updateOrdersToDatabase();
                
                log.info("📝 TRADE CREATED - {} {} @ {}", 
                        orderType, order.getTradingSymbol(), optionEntryPrice);
                log.info("📊 ORDER DETAILS - ID: {}, Status: {}, StopLoss: {}, Target: {}", 
                        order.getId(), order.getStatus(), order.getStopLossPrice(), order.getTargetPrice());
                
                // Log scenario information if available
                if (order.getEntryScenarioName() != null) {
                    log.info("🎯 SCENARIO INFO - Name: {}, Description: {}, Confidence: {}/10", 
                            order.getEntryScenarioName(), order.getEntryScenarioDescription(), order.getEntryScenarioConfidence());
                    if (order.getEntryCategoryScores() != null) {
                        log.info("📈 CATEGORY SCORES - {}", order.getEntryCategoryScores());
                    }
                    if (order.getEntryMatchedConditions() != null) {
                        log.info("✅ MATCHED CONDITIONS - {}", order.getEntryMatchedConditions());
                    }
                }
                if (livePricing.isPresent()) {
                    log.info("🎯 LIVE OPTION PRICE LEVELS - Entry: {}, StopLoss: {} (-{} points), Target: {} (+{} points)", 
                            optionEntryPrice, stopLossPrice, stopLossPoints, targetPrice, targetPoints);
                    log.info("📊 LIVE INDEX LEVELS - Entry Index: {}, Option Premium: {} (real LTP)", 
                            currentIndexPrice, optionEntryPrice);
                    log.info("🔍 ANALYSIS - Using real option instruments with live pricing for live profile");
                } else {
                    log.info("🎯 PLACEHOLDER OPTION PRICE LEVELS - Entry: {}, StopLoss: {} (-{} points), Target: {} (+{} points)", 
                            optionEntryPrice, stopLossPrice, stopLossPoints, targetPrice, targetPoints);
                    log.info("📊 PLACEHOLDER INDEX LEVELS - Entry Index: {}, Option Premium: {} (1% of index)", 
                            currentIndexPrice, optionEntryPrice);
                    log.info("🔍 ANALYSIS - Using placeholder option symbols for local profile or when live pricing fails");
                }
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
     * Store profitable trade filter information in the order
     */
    private void storeProfitableTradeFilterInfo(JtradeOrder order, Tick tick, String orderType, FlattenedIndicators indicators, EntryQuality callQuality, EntryQuality putQuality) {
        try {
            // Use the passed indicators instead of recalculating
            // FlattenedIndicators indicators = scalpingVolumeSurgeService.getFlattenedIndicators(tick);
            
            if (indicators == null) {
                order.setProfitableTradeFilterEnabled(false);
                order.setProfitableTradeFilterPassed(false);
                order.setProfitableTradeFilterRejectionReason("No indicators available");
                return;
            }
            
            // Check if profitable trade filter is enabled
            boolean filterEnabled = profitableTradeFilterService.isFilterEnabled();
            order.setProfitableTradeFilterEnabled(filterEnabled);
            
            if (!filterEnabled) {
                order.setProfitableTradeFilterPassed(true);
                order.setProfitableTradeFilterRejectionReason("Filter disabled");
                return;
            }
            
            // 🔥 OPTIMIZATION: Use passed entry quality instead of recalculating
            EntryQuality entryQuality;
            if ("CALL_BUY".equals(orderType)) {
                entryQuality = callQuality;
            } else {
                entryQuality = putQuality;
            }
            
            // Evaluate profitable trade filter
            ProfitableTradeFilterResult filterResult;
            if ("CALL_BUY".equals(orderType)) {
                filterResult = profitableTradeFilterService.evaluateCallEntry(indicators, entryQuality, tick);
            } else {
                filterResult = profitableTradeFilterService.evaluatePutEntry(indicators, entryQuality, tick);
            }
            
            // Store filter results
            order.setProfitableTradeFilterPassed(filterResult.getIsProfitableEntry());
            order.setProfitableTradeFilterRejectionReason(filterResult.getRejectionReason());
            order.setProfitableTradeFilterQualityScore(filterResult.getQualityScore());
            order.setProfitableTradeFilterCandlestickScore(filterResult.getCandlestickScore());
            order.setProfitableTradeFilterVolumeSurgeMultiplier(filterResult.getVolumeSurgeMultiplier());
            
            // Store category results
            if (filterResult.getEmaCategory() != null) {
                order.setProfitableTradeFilterOptionalConditionsCount(filterResult.getEmaCategory().getPassedCount());
                order.setProfitableTradeFilterPassedRequiredConditions(filterResult.getEmaCategory().getPassedConditions());
                order.setProfitableTradeFilterFailedRequiredConditions(filterResult.getEmaCategory().getFailedConditions());
            }
            
            // For now, store the first category's results in the existing fields
            // In the future, we can add separate fields for each category
            if (filterResult.getFutureAndVolumeCategory() != null) {
                order.setProfitableTradeFilterPassedOptionalConditions(filterResult.getFutureAndVolumeCategory().getPassedConditions());
                order.setProfitableTradeFilterFailedOptionalConditions(filterResult.getFutureAndVolumeCategory().getFailedConditions());
            }
            
        } catch (Exception e) {
            log.error("Error storing profitable trade filter info for order: {}", order.getId(), e);
            order.setProfitableTradeFilterEnabled(false);
            order.setProfitableTradeFilterPassed(false);
            order.setProfitableTradeFilterRejectionReason("Error: " + e.getMessage());
        }
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
    private String getTrendAndConditionsInfo(ScalpingEntryDecision entryDecision, FlattenedIndicators indicators, Tick tick, boolean isMarketSuitable, String detailedFlatMarketReason, EntryQuality callQuality, EntryQuality putQuality) {
        try {
            // Use scenario information from entry decision
            if (entryDecision.isShouldEntry() && entryDecision.getScenarioName() != null) {
                String scenarioInfo = String.format("Scenario: %s (%.1f/10)", 
                    entryDecision.getScenarioName(), entryDecision.getQualityScore());
                
                if (entryDecision.getCategoryScores() != null) {
                    // Get scenario requirements to check if categories pass
                    Map<String, Integer> categoryScores = entryDecision.getCategoryScores();
                    
                    // Get scenario requirements from config
                    List<ScalpingEntryConfig.Scenario> scenarios = configService.getScenarios();
                    ScalpingEntryConfig.ScenarioRequirements requirements = null;
                    
                    for (ScalpingEntryConfig.Scenario scenario : scenarios) {
                        if (scenario.getName().equals(entryDecision.getScenarioName())) {
                            requirements = scenario.getRequirements();
                            break;
                        }
                    }
                    
                    // Check if flat market filtering would adjust these requirements
                    boolean requirementsAdjusted = false;
                    int categoryIncrement = 0;
                    String flatMarketReason = "";
                    if (requirements != null && requirements.getFlatMarketFilter() != null && requirements.getFlatMarketFilter()) {
                        // Use the passed market condition analysis instead of recalculating
                        if (!isMarketSuitable) {
                            requirementsAdjusted = true;
                            // Get the actual category increment from configuration
                            FlatMarketFilteringConfig config = configService.getFlatMarketFilteringConfig();
                            categoryIncrement = config.getThresholds().getFlatMarketAdjustments().getCategoryIncrement();
                            
                            // Get flat market reason from market condition analysis
                            // Use the passed detailed flat market reason
                            if (detailedFlatMarketReason != null) {
                                flatMarketReason = " | Flat: " + detailedFlatMarketReason;
                            } else {
                                flatMarketReason = " | Flat: Market condition unsuitable";
                            }
                        }
                    }
                    
                    // Build category info with green ticks
                    StringBuilder categoryInfo = new StringBuilder();
                    
                    // Quality Score
                    if (requirements != null && requirements.getMinQualityScore() != null) {
                        boolean qsPass = entryDecision.getQualityScore() >= requirements.getMinQualityScore();
                        categoryInfo.append(String.format("QS:%.1f/%.1f%s ", 
                            entryDecision.getQualityScore(), requirements.getMinQualityScore(), qsPass ? "✅" : ""));
                    }
                    
                    // EMA
                    int emaCount = categoryScores.getOrDefault("ema", 0);
                    boolean emaPass = requirements != null && requirements.getEma_min_count() != null && 
                                   emaCount >= requirements.getEma_min_count();
                    String emaAdjustment = requirementsAdjusted ? " (+" + categoryIncrement + ")" : "";
                    categoryInfo.append(String.format("EMA:%d/%d%s%s ", 
                        emaCount, requirements != null ? requirements.getEma_min_count() : 0, emaPass ? "✅" : "", emaAdjustment));
                    
                    // FutureAndVolume
                    int fvCount = categoryScores.getOrDefault("futureAndVolume", 0);
                    boolean fvPass = requirements != null && requirements.getFutureAndVolume_min_count() != null && 
                                   fvCount >= requirements.getFutureAndVolume_min_count();
                    String fvAdjustment = requirementsAdjusted ? " (+" + categoryIncrement + ")" : "";
                    categoryInfo.append(String.format("FV:%d/%d%s%s ", 
                        fvCount, requirements != null ? requirements.getFutureAndVolume_min_count() : 0, fvPass ? "✅" : "", fvAdjustment));
                    
                    // Candlestick
                    int csCount = categoryScores.getOrDefault("candlestick", 0);
                    boolean csPass = requirements != null && requirements.getCandlestick_min_count() != null && 
                                   csCount >= requirements.getCandlestick_min_count();
                    String csAdjustment = requirementsAdjusted ? " (+" + categoryIncrement + ")" : "";
                    categoryInfo.append(String.format("CS:%d/%d%s%s ", 
                        csCount, requirements != null ? requirements.getCandlestick_min_count() : 0, csPass ? "✅" : "", csAdjustment));
                    
                    // Momentum
                    int mCount = categoryScores.getOrDefault("momentum", 0);
                    boolean mPass = requirements != null && requirements.getMomentum_min_count() != null && 
                                   mCount >= requirements.getMomentum_min_count();
                    String mAdjustment = requirementsAdjusted ? " (+" + categoryIncrement + ")" : "";
                    categoryInfo.append(String.format("M:%d/%d%s%s ", 
                        mCount, requirements != null ? requirements.getMomentum_min_count() : 0, mPass ? "✅" : "", mAdjustment));
                    
                    return String.format("🎯 %s | %s%s", scenarioInfo, categoryInfo.toString().trim(), flatMarketReason);
                } else {
                    return String.format("🎯 %s", scenarioInfo);
                }
            } else {
                // Show scenario-based evaluation when no scenario is triggered
                String evaluation = getScenarioBasedEvaluation(indicators, tick, isMarketSuitable, detailedFlatMarketReason, callQuality, putQuality);
                // Add market direction info to non-entry logs as well
                return evaluation;
            }
            
        } catch (Exception e) {
            return "🎯 ERROR";
        }
    }
    
    /**
     * Get scenario-based evaluation showing which scenarios are being checked and their requirements
     */
    private String getScenarioBasedEvaluation(FlattenedIndicators indicators, Tick tick, boolean isMarketSuitable, String detailedFlatMarketReason, EntryQuality callQuality, EntryQuality putQuality) {
        try {
            // 🔥 OPTIMIZATION: Use passed quality scores instead of recalculating
            String dominantTrend = callQuality.getQualityScore() > putQuality.getQualityScore() ? "CALL" : "PUT";
            double dominantQuality = Math.max(callQuality.getQualityScore(), putQuality.getQualityScore());
            
            // Get scenarios from configuration
            List<ScalpingEntryConfig.Scenario> scenarios = configService.getScenarios();
            
            // Find the best scenario that could pass
            String bestScenarioInfo = "";
            for (ScalpingEntryConfig.Scenario scenario : scenarios) {
                ScalpingEntryConfig.ScenarioRequirements requirements = scenario.getRequirements();
                
                // Check if flat market filtering would adjust these requirements
                boolean requirementsAdjusted = false;
                int categoryIncrement = 0;
                String flatMarketReason = "";
                if (requirements != null && requirements.getFlatMarketFilter() != null && requirements.getFlatMarketFilter()) {
                    // Use the passed market condition analysis instead of recalculating
                    if (!isMarketSuitable) {
                        requirementsAdjusted = true;
                        // Get the actual category increment from configuration
                        FlatMarketFilteringConfig config = configService.getFlatMarketFilteringConfig();
                        categoryIncrement = config.getThresholds().getFlatMarketAdjustments().getCategoryIncrement();
                        
                        // Get flat market reason from market condition analysis
                        // Use the passed detailed flat market reason
                        if (detailedFlatMarketReason != null) {
                            flatMarketReason = " | Flat: " + detailedFlatMarketReason;
                        } else {
                            flatMarketReason = " | Flat: Market condition unsuitable";
                        }
                    }
                }
                
                // Get category counts for the dominant trend
                Map<String, Integer> categoryCounts = getCategoryCountsMap(indicators, dominantTrend);
                
                // Check if this scenario could pass
                boolean couldPass = true;
                StringBuilder scenarioDetails = new StringBuilder();
                
                // Check quality score requirement
                if (requirements.getMinQualityScore() != null) {
                    boolean qualityPass = dominantQuality >= requirements.getMinQualityScore();
                    scenarioDetails.append(String.format("QS:%.1f/%.1f%s ", 
                        dominantQuality, requirements.getMinQualityScore(), qualityPass ? "✅" : ""));
                    if (!qualityPass) couldPass = false;
                }
                
                // Check category requirements
                if (requirements.getEma_min_count() != null) {
                    int emaCount = categoryCounts.getOrDefault("ema", 0);
                    boolean emaPass = emaCount >= requirements.getEma_min_count();
                    String emaAdjustment = requirementsAdjusted ? " (+" + categoryIncrement + ")" : "";
                    scenarioDetails.append(String.format("EMA:%d/%d%s%s ", 
                        emaCount, requirements.getEma_min_count(), emaPass ? "✅" : "", emaAdjustment));
                    if (!emaPass) couldPass = false;
                }
                
                if (requirements.getFutureAndVolume_min_count() != null) {
                    int fvCount = categoryCounts.getOrDefault("futureAndVolume", 0);
                    boolean fvPass = fvCount >= requirements.getFutureAndVolume_min_count();
                    String fvAdjustment = requirementsAdjusted ? " (+" + categoryIncrement + ")" : "";
                    scenarioDetails.append(String.format("FV:%d/%d%s%s ", 
                        fvCount, requirements.getFutureAndVolume_min_count(), fvPass ? "✅" : "", fvAdjustment));
                    if (!fvPass) couldPass = false;
                }
                
                if (requirements.getCandlestick_min_count() != null) {
                    int csCount = categoryCounts.getOrDefault("candlestick", 0);
                    boolean csPass = csCount >= requirements.getCandlestick_min_count();
                    String csAdjustment = requirementsAdjusted ? " (+" + categoryIncrement + ")" : "";
                    scenarioDetails.append(String.format("CS:%d/%d%s%s ", 
                        csCount, requirements.getCandlestick_min_count(), csPass ? "✅" : "", csAdjustment));
                    if (!csPass) couldPass = false;
                }
                
                if (requirements.getMomentum_min_count() != null) {
                    int mCount = categoryCounts.getOrDefault("momentum", 0);
                    boolean mPass = mCount >= requirements.getMomentum_min_count();
                    String mAdjustment = requirementsAdjusted ? " (+" + categoryIncrement + ")" : "";
                    scenarioDetails.append(String.format("M:%d/%d%s%s ", 
                        mCount, requirements.getMomentum_min_count(), mPass ? "✅" : "", mAdjustment));
                    if (!mPass) couldPass = false;
                }
                
                // If this scenario could pass, show it
                if (couldPass) {
                    bestScenarioInfo = String.format("🎯 %s (%.1f/10) | %s: %s%s", 
                        dominantTrend, dominantQuality, scenario.getName(), scenarioDetails.toString().trim(), flatMarketReason);
                    break;
                }
            }
            
            // If no scenario could pass, show all scenarios being evaluated
            if (bestScenarioInfo.isEmpty() && !scenarios.isEmpty()) {
                StringBuilder allScenariosInfo = new StringBuilder();
                
                for (int i = 0; i < scenarios.size(); i++) {
                    ScalpingEntryConfig.Scenario scenario = scenarios.get(i);
                    ScalpingEntryConfig.ScenarioRequirements requirements = scenario.getRequirements();
                    Map<String, Integer> categoryCounts = getCategoryCountsMap(indicators, dominantTrend);
                    
                    // Check if flat market filtering would adjust these requirements
                    boolean requirementsAdjusted = false;
                    int categoryIncrement = 0;
                    String flatMarketReason = "";
                    if (requirements != null && requirements.getFlatMarketFilter() != null && requirements.getFlatMarketFilter()) {
                        // Use the passed market condition analysis instead of recalculating
                        if (!isMarketSuitable) {
                            requirementsAdjusted = true;
                            // Get the actual category increment from configuration
                            FlatMarketFilteringConfig config = configService.getFlatMarketFilteringConfig();
                            categoryIncrement = config.getThresholds().getFlatMarketAdjustments().getCategoryIncrement();
                            
                            // Get flat market reason from market condition analysis
                            // Use the passed detailed flat market reason
                            if (detailedFlatMarketReason != null) {
                                flatMarketReason = " | Flat: " + detailedFlatMarketReason;
                            } else {
                                flatMarketReason = " | Flat: Market condition unsuitable";
                            }
                        }
                    }
                    
                    StringBuilder scenarioDetails = new StringBuilder();
                    if (requirements.getMinQualityScore() != null) {
                        boolean qsPass = dominantQuality >= requirements.getMinQualityScore();
                        scenarioDetails.append(String.format("QS:%.1f/%.1f%s ", 
                            dominantQuality, requirements.getMinQualityScore(), qsPass ? "✅" : ""));
                    }
                    if (requirements.getEma_min_count() != null) {
                        int emaCount = categoryCounts.getOrDefault("ema", 0);
                        boolean emaPass = emaCount >= requirements.getEma_min_count();
                        String emaAdjustment = requirementsAdjusted ? " (+" + categoryIncrement + ")" : "";
                        scenarioDetails.append(String.format("EMA:%d/%d%s%s ", 
                            emaCount, requirements.getEma_min_count(), emaPass ? "✅" : "", emaAdjustment));
                    }
                    if (requirements.getFutureAndVolume_min_count() != null) {
                        int fvCount = categoryCounts.getOrDefault("futureAndVolume", 0);
                        boolean fvPass = fvCount >= requirements.getFutureAndVolume_min_count();
                        String fvAdjustment = requirementsAdjusted ? " (+" + categoryIncrement + ")" : "";
                        scenarioDetails.append(String.format("FV:%d/%d%s%s ", 
                            fvCount, requirements.getFutureAndVolume_min_count(), fvPass ? "✅" : "", fvAdjustment));
                    }
                    if (requirements.getCandlestick_min_count() != null) {
                        int csCount = categoryCounts.getOrDefault("candlestick", 0);
                        boolean csPass = csCount >= requirements.getCandlestick_min_count();
                        String csAdjustment = requirementsAdjusted ? " (+" + categoryIncrement + ")" : "";
                        scenarioDetails.append(String.format("CS:%d/%d%s%s ", 
                            csCount, requirements.getCandlestick_min_count(), csPass ? "✅" : "", csAdjustment));
                    }
                    if (requirements.getMomentum_min_count() != null) {
                        int mCount = categoryCounts.getOrDefault("momentum", 0);
                        boolean mPass = mCount >= requirements.getMomentum_min_count();
                        String mAdjustment = requirementsAdjusted ? " (+" + categoryIncrement + ")" : "";
                        scenarioDetails.append(String.format("M:%d/%d%s%s ", 
                            mCount, requirements.getMomentum_min_count(), mPass ? "✅" : "", mAdjustment));
                    }
                    
                    if (i > 0) allScenariosInfo.append(" | ");
                    allScenariosInfo.append(String.format("%s: %s%s", 
                        scenario.getName(), scenarioDetails.toString().trim(), flatMarketReason));
                }
                
                bestScenarioInfo = String.format("🎯 %s (%.1f/10) | %s", 
                    dominantTrend, dominantQuality, allScenariosInfo.toString());
            }
            
            return bestScenarioInfo;
            
        } catch (Exception e) {
            return "🎯 ERROR";
        }
    }
    
    /**
     * Get category counts as a map for scenario evaluation
     */
    private Map<String, Integer> getCategoryCountsMap(FlattenedIndicators indicators, String strategy) {
        Map<String, Integer> categoryCounts = new HashMap<>();
        
        if ("CALL".equals(strategy)) {
            categoryCounts.put("ema", getEmaCount(indicators, true));
            categoryCounts.put("futureAndVolume", getFutureVolumeCount(indicators, true));
            categoryCounts.put("candlestick", getCandlestickCount(indicators, true));
            categoryCounts.put("momentum", getMomentumCount(indicators, true));
        } else if ("PUT".equals(strategy)) {
            categoryCounts.put("ema", getEmaCount(indicators, false));
            categoryCounts.put("futureAndVolume", getFutureVolumeCount(indicators, false));
            categoryCounts.put("candlestick", getCandlestickCount(indicators, false));
            categoryCounts.put("momentum", getMomentumCount(indicators, false));
        }
        
        return categoryCounts;
    }
    
    /**
     * Get EMA count for the given strategy
     */
    private int getEmaCount(FlattenedIndicators indicators, boolean isCall) {
        int emaCount = 0;
        if (isCall) {
            // EMA5 vs EMA34 logic
            if (indicators.getEma5_5min_gt_ema34_5min() != null && indicators.getEma5_5min_gt_ema34_5min()) emaCount++;
            if (indicators.getEma5_1min_gt_ema34_1min() != null && indicators.getEma5_1min_gt_ema34_1min()) emaCount++;
            if (indicators.getEma5_15min_gt_ema34_15min() != null && indicators.getEma5_15min_gt_ema34_15min()) emaCount++;
        } else {
            // EMA5 vs EMA34 logic
            if (indicators.getEma5_5min_lt_ema34_5min() != null && indicators.getEma5_5min_lt_ema34_5min()) emaCount++;
            if (indicators.getEma5_1min_lt_ema34_1min() != null && indicators.getEma5_1min_lt_ema34_1min()) emaCount++;
            if (indicators.getEma5_15min_lt_ema34_15min() != null && indicators.getEma5_15min_lt_ema34_15min()) emaCount++;
        }
        return emaCount;
    }
    
    /**
     * Get Future & Volume count for the given strategy
     */
    private int getFutureVolumeCount(FlattenedIndicators indicators, boolean isCall) {
        int futureVolumeCount = 0;
        if (isCall) {
            if (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) futureVolumeCount++;
            if (indicators.getVolume_1min_surge() != null && indicators.getVolume_1min_surge()) futureVolumeCount++;
            if (indicators.getVolume_15min_surge() != null && indicators.getVolume_15min_surge()) futureVolumeCount++;
            if (indicators.getPrice_gt_vwap_5min() != null && indicators.getPrice_gt_vwap_5min()) futureVolumeCount++;
            if (indicators.getPrice_gt_vwap_1min() != null && indicators.getPrice_gt_vwap_1min()) futureVolumeCount++;
            if (indicators.getPrice_gt_vwap_15min() != null && indicators.getPrice_gt_vwap_15min()) futureVolumeCount++;
            if (indicators.getPrice_above_resistance() != null && indicators.getPrice_above_resistance()) futureVolumeCount++;
        } else {
            if (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) futureVolumeCount++;
            if (indicators.getVolume_1min_surge() != null && indicators.getVolume_1min_surge()) futureVolumeCount++;
            if (indicators.getVolume_15min_surge() != null && indicators.getVolume_15min_surge()) futureVolumeCount++;
            if (indicators.getPrice_lt_vwap_5min() != null && indicators.getPrice_lt_vwap_5min()) futureVolumeCount++;
            if (indicators.getPrice_lt_vwap_1min() != null && indicators.getPrice_lt_vwap_1min()) futureVolumeCount++;
            if (indicators.getPrice_lt_vwap_15min() != null && indicators.getPrice_lt_vwap_15min()) futureVolumeCount++;
            if (indicators.getPrice_below_support() != null && indicators.getPrice_below_support()) futureVolumeCount++;
        }
        return futureVolumeCount;
    }
    
    /**
     * Get Candlestick count for the given strategy
     */
    private int getCandlestickCount(FlattenedIndicators indicators, boolean isCall) {
        int candlestickCount = 0;
        if (isCall) {
            if (indicators.getGreen_candle_5min() != null && indicators.getGreen_candle_5min()) candlestickCount++;
            if (indicators.getGreen_candle_1min() != null && indicators.getGreen_candle_1min()) candlestickCount++;
            if (indicators.getLong_body_5min() != null && indicators.getLong_body_5min()) candlestickCount++;
            if (indicators.getLong_body_1min() != null && indicators.getLong_body_1min()) candlestickCount++;
            if (indicators.getBullish_engulfing_5min() != null && indicators.getBullish_engulfing_5min()) candlestickCount++;
            if (indicators.getBullish_engulfing_1min() != null && indicators.getBullish_engulfing_1min()) candlestickCount++;
            if (indicators.getBullish_morning_star_5min() != null && indicators.getBullish_morning_star_5min()) candlestickCount++;
            if (indicators.getBullish_morning_star_1min() != null && indicators.getBullish_morning_star_1min()) candlestickCount++;
            if (indicators.getHammer_5min() != null && indicators.getHammer_5min()) candlestickCount++;
            if (indicators.getHammer_1min() != null && indicators.getHammer_1min()) candlestickCount++;
        } else {
            if (indicators.getRed_candle_5min() != null && indicators.getRed_candle_5min()) candlestickCount++;
            if (indicators.getRed_candle_1min() != null && indicators.getRed_candle_1min()) candlestickCount++;
            if (indicators.getLong_body_5min() != null && indicators.getLong_body_5min()) candlestickCount++;
            if (indicators.getLong_body_1min() != null && indicators.getLong_body_1min()) candlestickCount++;
            if (indicators.getBearish_engulfing_5min() != null && indicators.getBearish_engulfing_5min()) candlestickCount++;
            if (indicators.getBearish_engulfing_1min() != null && indicators.getBearish_engulfing_1min()) candlestickCount++;
            if (indicators.getBearish_evening_star_5min() != null && indicators.getBearish_evening_star_5min()) candlestickCount++;
            if (indicators.getBearish_evening_star_1min() != null && indicators.getBearish_evening_star_1min()) candlestickCount++;
            if (indicators.getShooting_star_5min() != null && indicators.getShooting_star_5min()) candlestickCount++;
            if (indicators.getShooting_star_1min() != null && indicators.getShooting_star_1min()) candlestickCount++;
        }
        return candlestickCount;
    }
    
    /**
     * Get Momentum count for the given strategy
     */
    private int getMomentumCount(FlattenedIndicators indicators, boolean isCall) {
        int momentumCount = 0;
        if (isCall) {
            if (indicators.getRsi_5min_gt_56() != null && indicators.getRsi_5min_gt_56()) momentumCount++;
            if (indicators.getRsi_1min_gt_56() != null && indicators.getRsi_1min_gt_56()) momentumCount++;
            if (indicators.getRsi_15min_gt_56() != null && indicators.getRsi_15min_gt_56()) momentumCount++;
        } else {
            if (indicators.getRsi_5min_lt_44() != null && indicators.getRsi_5min_lt_44()) momentumCount++;
            if (indicators.getRsi_1min_lt_44() != null && indicators.getRsi_1min_lt_44()) momentumCount++;
            if (indicators.getRsi_15min_lt_44() != null && indicators.getRsi_15min_lt_44()) momentumCount++;
        }
        return momentumCount;
    }
    
    /**
     * Get category-based condition counts for the given strategy
     */
    private String getConditionCounts(FlattenedIndicators indicators, String strategy) {
        try {
            if ("CALL".equals(strategy)) {
                return getCallCategoryCounts(indicators);
            } else if ("PUT".equals(strategy)) {
                return getPutCategoryCounts(indicators);
            } else {
                return "Categories: EMA:0/2 FV:0/4 CS:0/3 M:0/2";
            }
        } catch (Exception e) {
            return "Categories: EMA:0/2 FV:0/4 CS:0/3 M:0/2";
        }
    }
    
    /**
     * Get category counts for CALL strategy
     */
    private String getCallCategoryCounts(FlattenedIndicators indicators) {
        int emaCount = 0;
        int futureVolumeCount = 0;
        int candlestickCount = 0;
        int momentumCount = 0;
        
        // EMA Category (need 2/3)
        if (indicators.getEma5_5min_gt_ema34_5min() != null && indicators.getEma5_5min_gt_ema34_5min()) emaCount++;
        if (indicators.getEma5_1min_gt_ema34_1min() != null && indicators.getEma5_1min_gt_ema34_1min()) emaCount++;
        if (indicators.getEma5_15min_gt_ema34_15min() != null && indicators.getEma5_15min_gt_ema34_15min()) emaCount++;
        
        // Future and Volume Category (need 4/7)
        if (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) futureVolumeCount++;
        if (indicators.getVolume_1min_surge() != null && indicators.getVolume_1min_surge()) futureVolumeCount++;
        if (indicators.getVolume_15min_surge() != null && indicators.getVolume_15min_surge()) futureVolumeCount++;
        if (indicators.getPrice_gt_vwap_5min() != null && indicators.getPrice_gt_vwap_5min()) futureVolumeCount++;
        if (indicators.getPrice_gt_vwap_1min() != null && indicators.getPrice_gt_vwap_1min()) futureVolumeCount++;
        if (indicators.getPrice_gt_vwap_15min() != null && indicators.getPrice_gt_vwap_15min()) futureVolumeCount++;
        if (indicators.getPrice_above_resistance() != null && indicators.getPrice_above_resistance()) futureVolumeCount++;
        
        // Candlestick Category (need 3/10)
        if (indicators.getGreen_candle_5min() != null && indicators.getGreen_candle_5min()) candlestickCount++;
        if (indicators.getGreen_candle_1min() != null && indicators.getGreen_candle_1min()) candlestickCount++;
        if (indicators.getLong_body_5min() != null && indicators.getLong_body_5min()) candlestickCount++;
        if (indicators.getLong_body_1min() != null && indicators.getLong_body_1min()) candlestickCount++;
        if (indicators.getBullish_engulfing_5min() != null && indicators.getBullish_engulfing_5min()) candlestickCount++;
        if (indicators.getBullish_engulfing_1min() != null && indicators.getBullish_engulfing_1min()) candlestickCount++;
        if (indicators.getBullish_morning_star_5min() != null && indicators.getBullish_morning_star_5min()) candlestickCount++;
        if (indicators.getBullish_morning_star_1min() != null && indicators.getBullish_morning_star_1min()) candlestickCount++;
        if (indicators.getHammer_5min() != null && indicators.getHammer_5min()) candlestickCount++;
        if (indicators.getHammer_1min() != null && indicators.getHammer_1min()) candlestickCount++;
        
        // Momentum Category (need 2/3)
        if (indicators.getRsi_5min_gt_56() != null && indicators.getRsi_5min_gt_56()) momentumCount++;
        if (indicators.getRsi_1min_gt_56() != null && indicators.getRsi_1min_gt_56()) momentumCount++;
        if (indicators.getRsi_15min_gt_56() != null && indicators.getRsi_15min_gt_56()) momentumCount++;
        
        // Get actual scenario requirements instead of hardcoded values
        List<ScalpingEntryConfig.Scenario> scenarios = configService.getScenarios();
        ScalpingEntryConfig.ScenarioRequirements requirements = null;
        
        if (!scenarios.isEmpty()) {
            requirements = scenarios.get(0).getRequirements(); // Use first scenario (SAFE_ENTRY_SIGNAL)
        }
        
        // Use actual requirements or fallback to hardcoded if config not available
        int emaRequired = requirements != null && requirements.getEma_min_count() != null ? requirements.getEma_min_count() : 2;
        int fvRequired = requirements != null && requirements.getFutureAndVolume_min_count() != null ? requirements.getFutureAndVolume_min_count() : 3;
        int csRequired = requirements != null && requirements.getCandlestick_min_count() != null ? requirements.getCandlestick_min_count() : 3;
        int mRequired = requirements != null && requirements.getMomentum_min_count() != null ? requirements.getMomentum_min_count() : 2;
        
        return String.format("Categories: EMA:%d/%d%s FV:%d/%d%s CS:%d/%d%s M:%d/%d%s", 
                           emaCount, emaRequired, emaCount >= emaRequired ? "✅" : "",
                           futureVolumeCount, fvRequired, futureVolumeCount >= fvRequired ? "✅" : "",
                           candlestickCount, csRequired, candlestickCount >= csRequired ? "✅" : "",
                           momentumCount, mRequired, momentumCount >= mRequired ? "✅" : "");
    }
    
    /**
     * Get category counts for PUT strategy
     */
    private String getPutCategoryCounts(FlattenedIndicators indicators) {
        int emaCount = 0;
        int futureVolumeCount = 0;
        int candlestickCount = 0;
        int momentumCount = 0;
        
        // EMA Category (need 2/3) - Use proper PUT EMA fields
        if (indicators.getEma5_5min_lt_ema34_5min() != null && indicators.getEma5_5min_lt_ema34_5min()) emaCount++;
        if (indicators.getEma5_1min_lt_ema34_1min() != null && indicators.getEma5_1min_lt_ema34_1min()) emaCount++;
        if (indicators.getEma5_15min_lt_ema34_15min() != null && indicators.getEma5_15min_lt_ema34_15min()) emaCount++;
        
        // Future and Volume Category (need 4/7)
        if (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) futureVolumeCount++;
        if (indicators.getVolume_1min_surge() != null && indicators.getVolume_1min_surge()) futureVolumeCount++;
        if (indicators.getVolume_15min_surge() != null && indicators.getVolume_15min_surge()) futureVolumeCount++;
        if (indicators.getPrice_lt_vwap_5min() != null && indicators.getPrice_lt_vwap_5min()) futureVolumeCount++;
        if (indicators.getPrice_lt_vwap_1min() != null && indicators.getPrice_lt_vwap_1min()) futureVolumeCount++;
        if (indicators.getPrice_lt_vwap_15min() != null && indicators.getPrice_lt_vwap_15min()) futureVolumeCount++;
        if (indicators.getPrice_below_support() != null && indicators.getPrice_below_support()) futureVolumeCount++;
        
        // Candlestick Category (need 3/10)
        if (indicators.getRed_candle_5min() != null && indicators.getRed_candle_5min()) candlestickCount++;
        if (indicators.getRed_candle_1min() != null && indicators.getRed_candle_1min()) candlestickCount++;
        if (indicators.getLong_body_5min() != null && indicators.getLong_body_5min()) candlestickCount++;
        if (indicators.getLong_body_1min() != null && indicators.getLong_body_1min()) candlestickCount++;
        if (indicators.getBearish_engulfing_5min() != null && indicators.getBearish_engulfing_5min()) candlestickCount++;
        if (indicators.getBearish_engulfing_1min() != null && indicators.getBearish_engulfing_1min()) candlestickCount++;
        if (indicators.getBearish_evening_star_5min() != null && indicators.getBearish_evening_star_5min()) candlestickCount++;
        if (indicators.getBearish_evening_star_1min() != null && indicators.getBearish_evening_star_1min()) candlestickCount++;
        if (indicators.getShooting_star_5min() != null && indicators.getShooting_star_5min()) candlestickCount++;
        if (indicators.getShooting_star_1min() != null && indicators.getShooting_star_1min()) candlestickCount++;
        
        // Momentum Category (need 2/3)
        if (indicators.getRsi_5min_lt_44() != null && indicators.getRsi_5min_lt_44()) momentumCount++;
        if (indicators.getRsi_1min_lt_44() != null && indicators.getRsi_1min_lt_44()) momentumCount++;
        if (indicators.getRsi_15min_lt_44() != null && indicators.getRsi_15min_lt_44()) momentumCount++;
        
        // Get actual scenario requirements instead of hardcoded values
        List<ScalpingEntryConfig.Scenario> scenarios = configService.getScenarios();
        ScalpingEntryConfig.ScenarioRequirements requirements = null;
        
        if (!scenarios.isEmpty()) {
            requirements = scenarios.get(0).getRequirements(); // Use first scenario (SAFE_ENTRY_SIGNAL)
        }
        
        // Use actual requirements or fallback to hardcoded if config not available
        int emaRequired = requirements != null && requirements.getEma_min_count() != null ? requirements.getEma_min_count() : 2;
        int fvRequired = requirements != null && requirements.getFutureAndVolume_min_count() != null ? requirements.getFutureAndVolume_min_count() : 3;
        int csRequired = requirements != null && requirements.getCandlestick_min_count() != null ? requirements.getCandlestick_min_count() : 3;
        int mRequired = requirements != null && requirements.getMomentum_min_count() != null ? requirements.getMomentum_min_count() : 2;
        
        return String.format("Categories: EMA:%d/%d%s FV:%d/%d%s CS:%d/%d%s M:%d/%d%s", 
                           emaCount, emaRequired, emaCount >= emaRequired ? "✅" : "",
                           futureVolumeCount, fvRequired, futureVolumeCount >= fvRequired ? "✅" : "",
                           candlestickCount, csRequired, candlestickCount >= csRequired ? "✅" : "",
                           momentumCount, mRequired, momentumCount >= mRequired ? "✅" : "");
    }
    
}
