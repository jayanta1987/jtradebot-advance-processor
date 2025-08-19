package com.jtradebot.processor.service.trading;

import com.jtradebot.processor.model.indicator.EntryQuality;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.strategy.ScalpingEntryDecision;
import com.jtradebot.processor.service.ScalpingVolumeSurgeService;
import com.jtradebot.processor.service.MarketConditionAnalysisService;
import com.jtradebot.processor.service.ProfitableTradeFilterService;
import com.jtradebot.processor.service.ExitStrategyService;
import com.jtradebot.processor.service.LiveOptionPricingService;
import com.jtradebot.processor.service.EntryConditionAnalysisService;
import com.jtradebot.processor.service.OptionPricingService;
import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.config.TradingConfigurationService;
import com.jtradebot.processor.service.analysis.IndicatorStatusService;
import com.jtradebot.processor.service.analysis.MarketDirectionAnalysisService;
import com.jtradebot.processor.service.logging.TradingLoggingService;
import com.jtradebot.processor.service.logging.IndicatorLoggingService;
import com.jtradebot.processor.service.logging.PerformanceLoggingService;
import com.jtradebot.processor.repository.document.JtradeOrder;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.zerodhatech.models.Tick;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingSignalProcessorService {

    private final ScalpingVolumeSurgeService scalpingVolumeSurgeService;
    private final MarketConditionAnalysisService marketConditionAnalysisService;
    private final ProfitableTradeFilterService profitableTradeFilterService;
    private final ExitStrategyService exitStrategyService;
    private final LiveOptionPricingService liveOptionPricingService;
    private final EntryConditionAnalysisService entryConditionAnalysisService;
    private final OptionPricingService optionPricingService;
    private final OrderExecutionService orderExecutionService;
    private final DynamicStrategyConfigService configService;
    private final TradingConfigurationService tradingConfigService;
    private final KiteInstrumentHandler kiteInstrumentHandler;
    private final TickDataManager tickDataManager;
    private final KiteConnect kiteConnect;
    private final Environment environment;
    private final IndicatorStatusService indicatorStatusService;
    private final MarketDirectionAnalysisService marketDirectionAnalysisService;
    private final TradingLoggingService tradingLoggingService;
    private final IndicatorLoggingService indicatorLoggingService;
    private final PerformanceLoggingService performanceLoggingService;

    /**
     * Process trading signals for a given tick using ScalpingVolumeSurge strategy
     * This is the main entry point for trading signal processing
     */
    public void processTradingSignals(Tick indexTick) {
        performanceLoggingService.startTiming("tick_processing");
        
        try {
            // Step 1: Get market data and calculate indicators
            FlattenedIndicators indicators = calculateIndicators(indexTick);
            
            // Step 2: Analyze market conditions
            MarketConditionAnalysis marketConditions = analyzeMarketConditions(indexTick, indicators);
            
            // Step 3: Process entry logic and generate signals
            processEntryLogicOptimized(indexTick, indicators, marketConditions);
            
            // Step 4: Handle order management
            handleOrderManagement(indexTick, indicators, marketConditions);
            
        } catch (Exception e) {
            tradingLoggingService.logTickProcessingError(indexTick.getInstrumentToken(), e);
        } finally {
            performanceLoggingService.endTiming("tick_processing");
        }
    }

    /**
     * Step 1: Calculate indicators for the tick
     */
    private FlattenedIndicators calculateIndicators(Tick indexTick) {
        performanceLoggingService.startTiming("indicator_calculation");
        try {
            // 🔥 OPTIMIZATION: Calculate indicators ONCE per tick to avoid redundant calculations
            FlattenedIndicators indicators = scalpingVolumeSurgeService.getFlattenedIndicators(indexTick);
            performanceLoggingService.endTiming("indicator_calculation");
            return indicators;
        } catch (Exception e) {
            performanceLoggingService.endTiming("indicator_calculation");
            throw e;
        }
    }

    /**
     * Step 2: Analyze market conditions
     */
    private MarketConditionAnalysis analyzeMarketConditions(Tick indexTick, FlattenedIndicators indicators) {
        performanceLoggingService.startTiming("market_condition_analysis");
        try {
            // 🔥 OPTIMIZATION: Calculate market condition analysis ONCE per tick to avoid redundant calculations
            boolean isMarketSuitable = marketConditionAnalysisService.isMarketConditionSuitable(indexTick, indicators);
            
            // Always get market condition details for order storage (entry logic is handled separately)
            String detailedFlatMarketReason = marketConditionAnalysisService.getDetailedFlatMarketReason(indexTick, indicators);
            
            MarketConditionAnalysis analysis = new MarketConditionAnalysis(isMarketSuitable, detailedFlatMarketReason);
            performanceLoggingService.endTiming("market_condition_analysis");
            return analysis;
        } catch (Exception e) {
            performanceLoggingService.endTiming("market_condition_analysis");
            throw e;
        }
    }

    /**
     * Step 3: Process entry logic and generate signals (optimized version)
     * 🔥 OPTIMIZATION: Passes pre-calculated market condition to avoid redundant calls
     */
    private void processEntryLogicOptimized(Tick indexTick, FlattenedIndicators indicators, MarketConditionAnalysis marketConditions) {
        performanceLoggingService.startTiming("entry_logic_processing");
        try {
            // 🔥 REAL ENTRY LOGIC - This is what actually matters for trading
            logRealEntryLogicOptimized(indexTick, indicators, marketConditions.isMarketSuitable(), marketConditions.getDetailedFlatMarketReason());
            performanceLoggingService.endTiming("entry_logic_processing");
        } catch (Exception e) {
            performanceLoggingService.endTiming("entry_logic_processing");
            throw e;
        }
    }

    /**
     * Step 4: Handle order management
     */
    private void handleOrderManagement(Tick indexTick, FlattenedIndicators indicators, MarketConditionAnalysis marketConditions) {
        performanceLoggingService.startTiming("order_management");
        try {
            // Check and process exits for existing orders
            checkAndProcessExits(indexTick);
            
            // Process new entry signals and manage active trades (using index tick for price data)
            processEntrySignals(indexTick, indicators, marketConditions);
            
            // Update live P&L for active trades (using index tick for price data)
            try {
                orderExecutionService.updateLivePnL(indexTick);
            } catch (KiteException e) {
                tradingLoggingService.logLivePnLError(indexTick.getInstrumentToken(), e);
            }
            performanceLoggingService.endTiming("order_management");
        } catch (Exception e) {
            performanceLoggingService.endTiming("order_management");
            throw e;
        }
    }

    /**
     * Market condition analysis result
     */
    private static class MarketConditionAnalysis {
        private final boolean isMarketSuitable;
        private final String detailedFlatMarketReason;

        public MarketConditionAnalysis(boolean isMarketSuitable, String detailedFlatMarketReason) {
            this.isMarketSuitable = isMarketSuitable;
            this.detailedFlatMarketReason = detailedFlatMarketReason;
        }

        public boolean isMarketSuitable() {
            return isMarketSuitable;
        }

        public String getDetailedFlatMarketReason() {
            return detailedFlatMarketReason;
        }
    }

    /**
     * Evaluate entry decision with pre-calculated indicators to avoid recalculation
     */
    public ScalpingEntryDecision evaluateEntryDecision(Tick tick, FlattenedIndicators indicators) {
        return scalpingVolumeSurgeService.getEntryDecision(tick, indicators);
    }

    /**
     * Validate and execute order based on entry decision
     */
    public void validateAndExecuteOrder(Tick tick, ScalpingEntryDecision entryDecision, FlattenedIndicators indicators, MarketConditionAnalysis marketConditions) {
        performanceLoggingService.startTiming("order_validation_and_execution");
        
        try {
            // Check if we can execute the order (no active orders)
            boolean hasActiveOrder = exitStrategyService.hasActiveOrder();
            log.debug("🔍 ORDER VALIDATION - HasActiveOrder: {}", hasActiveOrder);
            
            if (!hasActiveOrder) {
                // Determine order type based on entry decision
                String orderType = determineOrderType(entryDecision, indicators);
                log.debug("🔍 ORDER TYPE DETERMINATION - OrderType: {}, QualityScore: {}", orderType, entryDecision.getQualityScore());
                
                if (orderType != null) {
                    // 🔥 EXECUTE ORDER
                    tradingLoggingService.logOrderExecution(orderType, entryDecision);
                    
                    // Calculate entry quality scores
                    EntryQuality callQuality = scalpingVolumeSurgeService.evaluateCallEntryQuality(indicators, tick);
                    EntryQuality putQuality = scalpingVolumeSurgeService.evaluatePutEntryQuality(indicators, tick);
                    
                    log.debug("🔥 CREATING ORDER - Type: {}, Symbol: {}", orderType, entryDecision.getScenarioName());
                    createTradeOrder(tick, orderType, entryDecision, indicators, callQuality, putQuality, marketConditions.isMarketSuitable());
                } else {
                    log.debug("❌ ORDER TYPE IS NULL - Cannot create order");
                }
            } else {
                tradingLoggingService.logActiveOrderWarning(exitStrategyService.hasActiveOrder());
            }
        } catch (KiteException e) {
            tradingLoggingService.logKiteExceptionInOrderValidation(e);
        } catch (Exception e) {
            tradingLoggingService.logOrderValidationError(e);
        } finally {
            performanceLoggingService.endTiming("order_validation_and_execution");
        }
    }

    /**
     * Log the REAL entry logic from ScalpingVolumeSurgeService - CONCISE ONE-LINER
     */
    private void logRealEntryLogic(Tick indexTick, FlattenedIndicators indicators, boolean isMarketSuitable, String detailedFlatMarketReason) {
        try {
            // Step 1: Get entry decision and quality scores
            EntryAnalysisResult analysisResult = analyzeEntryConditions(indexTick, indicators);
            
            // Step 2: Determine market direction and entry signals
            EntrySignalResult signalResult = determineEntrySignals(analysisResult);
            
            // Step 3: Log indicator status and trend analysis
            logIndicatorStatusAndTrend(indexTick, indicators, analysisResult, signalResult, isMarketSuitable, detailedFlatMarketReason);
            
            // Step 4: Execute orders if signals are generated
            executeOrdersIfSignalsGenerated(indexTick, analysisResult, signalResult, indicators, new MarketConditionAnalysis(isMarketSuitable, detailedFlatMarketReason));
            
        } catch (Exception e) {
            tradingLoggingService.logEntryLogicError(e);
        }
    }

    /**
     * Log the REAL entry logic from ScalpingVolumeSurgeService - CONCISE ONE-LINER
     */
    private void logRealEntryLogicOptimized(Tick indexTick, FlattenedIndicators indicators, boolean isMarketSuitable, String detailedFlatMarketReason) {
        try {
            // Step 1: Get entry decision and quality scores
            EntryAnalysisResult analysisResult = analyzeEntryConditions(indexTick, indicators, isMarketSuitable);
            
            // Step 2: Determine market direction and entry signals
            EntrySignalResult signalResult = determineEntrySignals(analysisResult);
            
            // Step 3: Log indicator status and trend analysis
            logIndicatorStatusAndTrend(indexTick, indicators, analysisResult, signalResult, isMarketSuitable, detailedFlatMarketReason);
            
            // Step 4: Execute orders if signals are generated
            executeOrdersIfSignalsGenerated(indexTick, analysisResult, signalResult, indicators, new MarketConditionAnalysis(isMarketSuitable, detailedFlatMarketReason));
            
        } catch (Exception e) {
            tradingLoggingService.logEntryLogicError(e);
        }
    }

    /**
     * Step 1: Analyze entry conditions
     */
    private EntryAnalysisResult analyzeEntryConditions(Tick indexTick, FlattenedIndicators indicators) {
        return analyzeEntryConditions(indexTick, indicators, null);
    }
    
    /**
     * Step 1: Analyze entry conditions (optimized version)
     * 🔥 OPTIMIZATION: Accepts pre-calculated market condition to avoid redundant calls
     */
    private EntryAnalysisResult analyzeEntryConditions(Tick indexTick, FlattenedIndicators indicators, Boolean preCalculatedMarketCondition) {
        // Use NEW scenario-based entry logic instead of old StrategyScore approach
        ScalpingEntryDecision entryDecision = scalpingVolumeSurgeService.getEntryDecision(indexTick, indicators, preCalculatedMarketCondition);
        
        // 🔥 OPTIMIZATION: Calculate entry quality scores ONCE to avoid redundant calculations
        EntryQuality callQuality = scalpingVolumeSurgeService.evaluateCallEntryQuality(indicators, indexTick);
        EntryQuality putQuality = scalpingVolumeSurgeService.evaluatePutEntryQuality(indicators, indexTick);
        
        return new EntryAnalysisResult(entryDecision, callQuality, putQuality);
    }

    /**
     * Step 2: Determine entry signals
     */
    private EntrySignalResult determineEntrySignals(EntryAnalysisResult analysisResult) {
        boolean scenarioPassed = analysisResult.getEntryDecision().isShouldEntry();
        
        // Debug entry decisions (only log success to reduce noise)
        if (scenarioPassed) {
            tradingLoggingService.logEntryScenarioPassed(analysisResult.getEntryDecision());
        }
        
        // Use the quality score from entryDecision to avoid duplicate calculations
        double qualityScore = analysisResult.getEntryDecision().getQualityScore();
        
        // For now, use a simple heuristic for direction - in a real implementation,
        // you'd want to store both call and put quality scores in the entryDecision
        // For now, assume CALL direction if we have a valid quality score
        boolean isCallDominant = qualityScore > 0;
        boolean isPutDominant = false; // Simplified for now
        
        // Only create orders if scenario passes AND dominant direction is clear
        boolean shouldCall = scenarioPassed && isCallDominant;
        boolean shouldPut = scenarioPassed && isPutDominant;
        
        log.debug("🔍 SIGNAL DETERMINATION - Scenario: {}, QualityScore: {}, ScenarioPassed: {}, CallDominant: {}, PutDominant: {}, ShouldCall: {}, ShouldPut: {}", 
                analysisResult.getEntryDecision().getScenarioName(), qualityScore, scenarioPassed, isCallDominant, isPutDominant, shouldCall, shouldPut);
        
        return new EntrySignalResult(shouldCall, shouldPut, isCallDominant, isPutDominant);
    }

    /**
     * Step 3: Log indicator status and trend analysis
     */
    private void logIndicatorStatusAndTrend(Tick indexTick, FlattenedIndicators indicators, EntryAnalysisResult analysisResult, EntrySignalResult signalResult, boolean isMarketSuitable, String detailedFlatMarketReason) {
        // Get essential indicator data with actual values
        String emaStatus = indicatorStatusService.getDetailedEmaStatus(indicators, indexTick);
        String rsiStatus = indicatorStatusService.getDetailedRsiStatus(indicators, indexTick);
        String volumeStatus = indicatorStatusService.getVolumeStatus(indicators);
        String entrySignal = indicatorStatusService.getEntrySignal(signalResult.isShouldCall(), signalResult.isShouldPut());
        String entryProximity = indicatorStatusService.getEntryProximity(indicators, indexTick);
        
        // Get trend and conditions info
        String trendInfo = marketDirectionAnalysisService.getTrendAndConditionsInfo(
            analysisResult.getEntryDecision(), indicators, indexTick, isMarketSuitable, detailedFlatMarketReason, 
            analysisResult.getCallQuality(), analysisResult.getPutQuality());
        
        // Log indicator status
        indicatorLoggingService.logIndicatorStatus(emaStatus, rsiStatus, volumeStatus, entryProximity);
        
        // Log market direction
        indicatorLoggingService.logMarketDirection(signalResult.isCallDominant(), signalResult.isPutDominant());
        
        // LOG: Show trend analysis with category conditions (market direction removed)
        String marketDirectionInfo = "";
        
        indicatorLoggingService.logTrendAnalysis(indexTick, trendInfo, marketDirectionInfo);
    }

    /**
     * Step 4: Execute orders if signals are generated
     */
    private void executeOrdersIfSignalsGenerated(Tick indexTick, EntryAnalysisResult analysisResult, EntrySignalResult signalResult, FlattenedIndicators indicators, MarketConditionAnalysis marketConditions) {
        log.debug("🔍 ORDER EXECUTION CHECK - ShouldCall: {}, ShouldPut: {}", signalResult.isShouldCall(), signalResult.isShouldPut());
        
        if (signalResult.isShouldCall() || signalResult.isShouldPut()) {
            // Reuse the already computed scenario-based entry decision to avoid mismatch
            ScalpingEntryDecision scenarioDecision = analysisResult.getEntryDecision();
            
            log.debug("🔍 SCENARIO CHECK - Scenario: {}, ShouldEntry: {}", 
                    scenarioDecision != null ? scenarioDecision.getScenarioName() : "null", 
                    scenarioDecision != null ? scenarioDecision.isShouldEntry() : "null");
            
            if (scenarioDecision != null && scenarioDecision.isShouldEntry()) {
                String orderType = signalResult.isShouldCall() ? "CALL" : "PUT";
                tradingLoggingService.logEntrySignal(indexTick, orderType, scenarioDecision);
                
                // Execute the order
                validateAndExecuteOrder(indexTick, scenarioDecision, indicators, marketConditions);
            } else {
                tradingLoggingService.logEntrySkipped(signalResult.isShouldCall(), signalResult.isShouldPut(), scenarioDecision);
            }
        } else {
            log.debug("🔍 NO SIGNALS GENERATED - ShouldCall: {}, ShouldPut: {}", signalResult.isShouldCall(), signalResult.isShouldPut());
        }
    }

    /**
     * Entry analysis result
     */
    private static class EntryAnalysisResult {
        private final ScalpingEntryDecision entryDecision;
        private final EntryQuality callQuality;
        private final EntryQuality putQuality;

        public EntryAnalysisResult(ScalpingEntryDecision entryDecision, EntryQuality callQuality, EntryQuality putQuality) {
            this.entryDecision = entryDecision;
            this.callQuality = callQuality;
            this.putQuality = putQuality;
        }

        public ScalpingEntryDecision getEntryDecision() { return entryDecision; }
        public EntryQuality getCallQuality() { return callQuality; }
        public EntryQuality getPutQuality() { return putQuality; }
    }

    /**
     * Entry signal result
     */
    private static class EntrySignalResult {
        private final boolean shouldCall;
        private final boolean shouldPut;
        private final boolean isCallDominant;
        private final boolean isPutDominant;

        public EntrySignalResult(boolean shouldCall, boolean shouldPut, boolean isCallDominant, boolean isPutDominant) {
            this.shouldCall = shouldCall;
            this.shouldPut = shouldPut;
            this.isCallDominant = isCallDominant;
            this.isPutDominant = isPutDominant;
        }

        public boolean isShouldCall() { return shouldCall; }
        public boolean isShouldPut() { return shouldPut; }
        public boolean isCallDominant() { return isCallDominant; }
        public boolean isPutDominant() { return isPutDominant; }
    }

    /**
     * Determine order type based on entry decision and indicators
     */
    private String determineOrderType(ScalpingEntryDecision entryDecision, FlattenedIndicators indicators) {
        // Use the quality score from entryDecision to determine direction
        double qualityScore = entryDecision.getQualityScore();
        
        // For now, use a simple heuristic for direction
        // In a real implementation, you'd want to store both call and put quality scores in the entryDecision
        boolean isCallDominant = qualityScore > 0;
        boolean isPutDominant = false; // Simplified for now
        
        log.debug("🔍 ORDER TYPE LOGIC - QualityScore: {}, CallDominant: {}, PutDominant: {}", qualityScore, isCallDominant, isPutDominant);
        
        if (isCallDominant) {
            return "CALL_BUY";
        } else if (isPutDominant) {
            return "PUT_BUY";
        }
        
        return null;
    }

    /**
     * Process entry signals and manage active trades
     */
    private void processEntrySignals(Tick tick, FlattenedIndicators indicators, MarketConditionAnalysis marketConditions) {
        try {
            String instrumentToken = String.valueOf(tick.getInstrumentToken());
            
            // Check: if ExitStrategyService has any active order, skip (only one active order allowed globally)
            if (exitStrategyService.hasActiveOrder()) {
                return;
            }
            
            // Note: Entry decisions are now handled by the dynamic configuration system
            // This method is kept for compatibility but actual entry logic is in the dynamic system
            
        } catch (Exception e) {
            log.error("Error processing entry signals for tick: {}", tick.getInstrumentToken(), e);
        }
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
     * Create a new trade order and save to DB
     */
    private void createTradeOrder(Tick tick, String orderType, ScalpingEntryDecision entryDecision, FlattenedIndicators indicators, EntryQuality callQuality, EntryQuality putQuality, Boolean entryMarketConditionSuitable) throws KiteException {
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
                optionEntryPrice = currentIndexPrice * 0.01; // Simplified calculation
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
                    entryMarketConditionSuitable, // Market condition suitable flag
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
                
                // Store structured market condition details
                Map<String, Object> marketConditionDetails = marketConditionAnalysisService.getStructuredMarketConditionDetails(tick, indicators);
                order.setEntryMarketConditionDetails(marketConditionDetails);
                
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




}
