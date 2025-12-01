package com.jtradebot.processor.service.order;

import com.jtradebot.processor.common.CommonUtils;
import com.jtradebot.processor.config.DayTradingSettingService;
import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.config.TradingConfigurationService;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.MilestoneSystem;
import com.jtradebot.processor.model.enums.ExitReasonEnum;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.model.strategy.DetailedCategoryScore;
import com.jtradebot.processor.model.strategy.ScalpingEntryDecision;
import com.jtradebot.processor.repository.JtradeOrderRepository;
import com.jtradebot.processor.repository.document.JtradeOrder;
import com.jtradebot.processor.service.entry.DynamicRuleEvaluatorService;
import com.jtradebot.processor.service.entry.UnstableMarketConditionAnalysisService;
import com.jtradebot.processor.service.notification.OrderNotificationService;
import com.jtradebot.processor.service.price.LiveOptionPricingService;
import com.jtradebot.processor.service.price.MockOptionPricingService;
import com.jtradebot.processor.service.price.OIAnalysisService;
import com.jtradebot.processor.service.price.OptionGreeksCalculator;
import com.jtradebot.processor.service.quantity.DynamicQuantityService;
import com.jtradebot.processor.service.tracking.OptionLTPTrackingService;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.indicator.SupportResistanceIndicator;
import com.jtradebot.processor.model.indicator.Support;
import com.jtradebot.processor.model.indicator.Resistance;
import com.jtradebot.processor.model.indicator.EmaIndicatorInfo;
import com.jtradebot.processor.model.NtpDetails;
import com.jtradebot.processor.manager.BarSeriesManager;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import org.ta4j.core.BarSeries;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.ArrayList;

import static com.jtradebot.processor.handler.DateTimeHandler.formatDateToIST;
import static com.jtradebot.processor.handler.DateTimeHandler.getCurrentISTTime;
import static com.jtradebot.processor.service.order.LogOrderService.logScores;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderManagementService {

    private final LiveOptionPricingService liveOptionPricingService;
    private final MockOptionPricingService mockOptionPricingService;
    private final OIAnalysisService oiAnalysisService;
    private final TradingConfigurationService tradingConfigService;
    private final DayTradingSettingService dayTradingSettingService;
    private final KiteInstrumentHandler kiteInstrumentHandler;
    private final TickDataManager tickDataManager;
    private final ActiveOrderTrackingService activeOrderTrackingService;
    private final DynamicStrategyConfigService configService;
    private final OrderNotificationService orderNotificationService;
    private final JtradeOrderRepository jtradeOrderRepository;
    private final KiteOrderService kiteOrderService;
    private final DynamicRuleEvaluatorService dynamicRuleEvaluatorService;
    private final UnstableMarketConditionAnalysisService unstableMarketConditionAnalysisService;
    private final SupportResistanceIndicator supportResistanceIndicator;
    private final BarSeriesManager barSeriesManager;
    private final DynamicQuantityService dynamicQuantityService;
    private final OptionLTPTrackingService optionLTPTrackingService;

    public JtradeOrder createTradeOrder(Tick tick, String orderType, ScalpingEntryDecision entryDecision, Boolean entryMarketConditionSuitable,
                                        double qualityScore, String dominantTrend,
                                        Map<String, DetailedCategoryScore> detailedCallScores, Map<String, DetailedCategoryScore> detailedPutScores,
                                        UnstableMarketConditionAnalysisService.FlexibleFilteringResult ntpFilterResult) throws KiteException {
        JtradeOrder order = new JtradeOrder();
        double stopLossPoints = 0.0, targetPoints = 0.0;
        try {
            String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();
            Tick niftyTick = tickDataManager.getLastTick(niftyToken);

            // Calculate dynamic quantity based on balance and option price
            int dynamicQuantity = dynamicQuantityService.calculateDynamicQuantity(orderType);
            
            // Validate quantity using centralized service with email notification
            if (!dynamicQuantityService.validateQuantity(dynamicQuantity, orderType, "Auto Order")) {
                return order;
            }

            // Check 1-minute candle cooldown period after any order exit
            if (activeOrderTrackingService.shouldBlockEntryAfterOneMinuteCandleCooldown(Long.valueOf(niftyToken))) {
                log.warn("🚫 CANNOT CREATE AUTO ORDER - 1-minute candle cooldown active after last order exit");
                return order;
            }

            if (niftyTick == null) {
                log.error("No Nifty index data available for order creation");
                return order;
            }
            double currentIndexPrice = niftyTick.getLastTradedPrice();

            // Try to get live option pricing first (for live profile)
            Optional<LiveOptionPricingService.LiveOptionPricingInfo> liveOptionPricing = liveOptionPricingService.getLiveOptionPricing(orderType);

            double optionEntryPrice;
            String optionSymbol;
            long optionInstrumentToken;
            
            // Variables to store Greeks data for order
            OIAnalysisService.StrikeOIData bestStrikeData = null;
            String optionType = "CALL_BUY".equals(orderType) ? "CE" : "PE";

            if (liveOptionPricing.isPresent()) { // live pricing available
                LiveOptionPricingService.LiveOptionPricingInfo pricingInfo = liveOptionPricing.get();
                optionEntryPrice = pricingInfo.getOptionLTP();
                optionSymbol = pricingInfo.getOptionInstrument().getTradingSymbol();
                optionInstrumentToken = pricingInfo.getOptionInstrument().getInstrumentToken();
                
                // Try to get OI-based strike data for live pricing
                try {
                    OIAnalysisService.BestStrikeResult bestStrikeResult = oiAnalysisService.getBestStrikeForScalping(optionType);
                    if (bestStrikeResult.isSuccess() && bestStrikeResult.getBestStrike() != null) {
                        bestStrikeData = bestStrikeResult.getBestStrike();
                        log.info("🎯 USING LIVE OPTION PRICING WITH OI DATA - Symbol: {}, LTP: {}, Strike: {}, Index: {}, OI: {}",
                                optionSymbol, optionEntryPrice, pricingInfo.getStrikePrice(), currentIndexPrice,
                                bestStrikeData.getOi());
                    } else {
                        log.info("🎯 USING LIVE OPTION PRICING - Symbol: {}, LTP: {}, Strike: {}, Index: {} (No OI data)",
                                optionSymbol, optionEntryPrice, pricingInfo.getStrikePrice(), currentIndexPrice);
                    }
                } catch (Exception e) {
                    log.warn("🎯 USING LIVE OPTION PRICING - Symbol: {}, LTP: {}, Strike: {}, Index: {} (OI fetch failed: {})",
                            optionSymbol, optionEntryPrice, pricingInfo.getStrikePrice(), currentIndexPrice, e.getMessage());
                }
            } else { // local or fallback to mock pricing
                // Try to get best strike data from OI analysis for more realistic local testing
                try {
                    OIAnalysisService.BestStrikeResult bestStrikeResult = oiAnalysisService.getBestStrikeForScalping(optionType);
                    
                    if (bestStrikeResult.isSuccess() && bestStrikeResult.getBestStrike() != null) {
                        bestStrikeData = bestStrikeResult.getBestStrike();
                        optionEntryPrice = bestStrikeData.getOptionPrice();
                        optionSymbol = bestStrikeData.getTradingSymbol();
                        optionInstrumentToken = bestStrikeData.getInstrumentToken();
                        log.info("🎯 USING OI-BASED LOCAL PRICING - Symbol: {}, LTP: {}, Strike: {}, Index: {}, OI: {}",
                                optionSymbol, optionEntryPrice, bestStrikeData.getStrikePrice(), currentIndexPrice, 
                                bestStrikeData.getOi());
                    } else {
                        // Fallback to simple mock pricing
                        optionEntryPrice = mockOptionPricingService.calculateEntryLTP(currentIndexPrice);
                        optionSymbol = "CALL_BUY".equals(orderType) ? "LOCAL_CALL_CE" : "LOCAL_PUT_PE";
                        optionInstrumentToken = 0L;
                        log.info("📊 USING FALLBACK LOCAL PRICING - Index: {}, Premium: {} (OI data unavailable)",
                                currentIndexPrice, optionEntryPrice);
                    }
                } catch (Exception e) {
                    // Fallback to simple mock pricing if OI analysis fails
                    optionEntryPrice = mockOptionPricingService.calculateEntryLTP(currentIndexPrice);
                    optionSymbol = "CALL_BUY".equals(orderType) ? "LOCAL_CALL_CE" : "LOCAL_PUT_PE";
                    optionInstrumentToken = 0L;
                    log.warn("📊 USING FALLBACK LOCAL PRICING - Index: {}, Premium: {} (Error: {})",
                            currentIndexPrice, optionEntryPrice, e.getMessage());
                }
            }

            // Always calculate stoploss as percentage from JSON
            double stopLossPercentage = tradingConfigService.getStopLossPercentage();
            stopLossPoints = optionEntryPrice * (stopLossPercentage / 100.0);
            
            // Get the targetMode from the scenario
            String targetMode = configService.getTargetModeForScenario(entryDecision.getScenarioName());
            
            if ("SR".equals(targetMode)) {
                // Calculate target based on Support/Resistance and store SR data
                SupportResistanceData srData = calculateSupportResistanceTargetWithData(tick, currentIndexPrice, orderType);
                targetPoints = srData.getTargetPoints();
                
                // Store support and resistance data in order
                order.setEntrySupports(srData.getSupports());
                order.setEntryResistances(srData.getResistances());
                order.setEntryNearestSupport(srData.getNearestSupport());
                order.setEntryNearestResistance(srData.getNearestResistance());
                
                log.info("📊 SR DATA STORED - Supports: {} levels, Resistances: {} levels, Nearest Support: {}, Nearest Resistance: {}",
                        srData.getSupports().size(), srData.getResistances().size(), 
                        srData.getNearestSupport(), srData.getNearestResistance());
                
                log.info("🎯 MIXED CALCULATION - Entry Price: {} | SL %: {}% | Target Mode: SR | SL Points: {} | Target Points: {}",
                        String.format("%.2f", optionEntryPrice),
                        String.format("%.1f", stopLossPercentage),
                        String.format("%.2f", stopLossPoints),
                        String.format("%.2f", targetPoints));
            } else {
                // Default to PER (Percentage) mode for target
                double targetPercentage = tradingConfigService.getTargetPercentage();
                targetPoints = optionEntryPrice * (targetPercentage / 100.0);
                
                log.info("🎯 PERCENTAGE CALCULATION - Entry Price: {} | SL %: {}% | Target %: {}% | SL Points: {} | Target Points: {}",
                        String.format("%.2f", optionEntryPrice),
                        String.format("%.1f", stopLossPercentage),
                        String.format("%.1f", targetPercentage),
                        String.format("%.2f", stopLossPoints),
                        String.format("%.2f", targetPoints));
            }

            if (entryDecision.isShouldEntry() && entryDecision.getScenarioName() != null) {

                // Check if there's already an active order
                if (activeOrderTrackingService.hasActiveOrder()) {
                    log.warn("Cannot create new order - there's already an active order. Please exit existing order first.");
                    return order;
                }

                double stopLossPrice = Math.max(0.0, optionEntryPrice - stopLossPoints);
                double targetPrice = optionEntryPrice + targetPoints;

                order.setId(UUID.randomUUID().toString());
                order.setOrderType(OrderTypeEnum.valueOf(orderType));
                order.setTradingSymbol(optionSymbol);
                order.setInstrumentToken(optionInstrumentToken);
                order.setEntryPrice(optionEntryPrice);
                order.setEntryIndexPrice(currentIndexPrice);
                order.setStopLossPrice(stopLossPrice);
                order.setTargetPrice(targetPrice);

                order.setQuantity(dynamicQuantity);
                order.setStatus("ACTIVE");
                order.setEntryTime(formatDateToIST(tick.getTickTimestamp()));
                order.setCreatedAt(getCurrentISTTime());
                order.setLastUpdated(getCurrentISTTime());

                // Store scenario information
                order.setEntryScenarioName(entryDecision.getScenarioName());
                order.setEntryScenarioDescription(entryDecision.getReason());
                order.setEntryScenarioConfidence(entryDecision.getConfidence());
                order.setEntryCategoryScores(entryDecision.getCategoryScores());
                order.setEntryMatchedConditions(entryDecision.getMatchedConditions());

                // 🔥 NEW: Store detailed category scores with individual indicator breakdowns
                if (detailedCallScores != null) {
                    order.setEntryDetailedCallScores(new HashMap<>(detailedCallScores));
                }
                if (detailedPutScores != null) {
                    order.setEntryDetailedPutScores(new HashMap<>(detailedPutScores));
                }

                // 🔥 NEW: Store quality score and direction scores
                order.setEntryQualityScore(qualityScore);
                order.setEntryDominantTrend(dominantTrend);
                
                // Store OI-based strike data at entry time
                if (bestStrikeData != null) {
                    order.setEntryStrikePrice(bestStrikeData.getStrikePrice());
                    order.setEntryOptionType(optionType);
                    // Note: Greeks data (Delta, Gamma, Theta, Vega, IV) not available with OI-based selection
                    // These fields will remain null/zero in the order
                    
                    log.info("📊 OI DATA STORED - Strike: {}, OI: {}, Volume: {}, Price: {}",
                            bestStrikeData.getStrikePrice(),
                            bestStrikeData.getOi(),
                            bestStrikeData.getVolume() != null ? bestStrikeData.getVolume() : 0L,
                            bestStrikeData.getOptionPrice());
                } else {
                    log.info("📊 NO OI DATA AVAILABLE - Using fallback pricing without OI details");
                }

                // 🔥 NEW: Store NTP filter results at entry time
                if (ntpFilterResult != null) {
                    List<UnstableMarketConditionAnalysisService.FilterResult> allFilters = ntpFilterResult.getFilterResults();
                    long passedFilters = allFilters.stream().filter(UnstableMarketConditionAnalysisService.FilterResult::isPassed).count();
                    long failedFilters = allFilters.stream().filter(f -> !f.isPassed()).count();
                    
                    // Build list of failed filter names with NTP values
                    List<String> failedFilterDetails = allFilters.stream()
                            .filter(f -> !f.isPassed())
                            .map(f -> String.format("%s (NTP: %.1f)", f.getName(), f.getNtp()))
                            .toList();
                    
                    // Create comprehensive NTP details
                    NtpDetails ntpDetails = NtpDetails.builder()
                            .filtersPassed((int) passedFilters)
                            .filtersFailed((int) failedFilters)
                            .totalFiltersChecked(allFilters.size())
                            .totalNtpValue((double) ntpFilterResult.getTotalNTP())
                            .maxAllowedNtpValue((double) ntpFilterResult.getMaxAllowedNTP())
                            .failedFilterNames(failedFilterDetails)
                            .ntpReason(ntpFilterResult.getReason())
                            .conditionsMet(ntpFilterResult.isConditionsMet())
                            .build();
                    
                    order.setEntryNtpDetails(ntpDetails);
                    
                    log.info("📊 NTP ENTRY TRACKING - Passed: {}, Failed: {}, Total: {}, NTP Value: {}/{}", 
                            passedFilters, failedFilters, allFilters.size(), 
                            ntpFilterResult.getTotalNTP(), ntpFilterResult.getMaxAllowedNTP());
                }

                // Store market condition details at entry time
                order.setEntryMarketConditionSuitable(entryMarketConditionSuitable);

                // Populate market condition details with ATR values and other metrics
                try {
                    // Get flattened indicators for the current tick
                    FlattenedIndicators indicators = dynamicRuleEvaluatorService.getFlattenedIndicators(tick);
                    if (indicators != null) {
                        Map<String, Object> marketConditionDetails = unstableMarketConditionAnalysisService.getStructuredMarketConditionDetails(tick, indicators);
                        order.setEntryMarketConditionDetails(marketConditionDetails);
                    }
                } catch (Exception e) {
                    log.error("Error populating market condition details for order: {}", order.getId(), e);
                }

                // Initialize milestone system only if milestone-based exit is enabled
                if (dayTradingSettingService.isMilestoneBasedExitEnabled()) {
                    initializeMilestoneSystem(order, tick);
                } else {
                    log.info("Milestone system initialization skipped - milestoneBasedExitEnabled is false");
                }

                // Store in memory
                activeOrderTrackingService.setActiveOrderMap(order);
                activeOrderTrackingService.setNeedUpdateFlag(true);

                log.info("Created new order entry with scenario: {} - {} @ {} (SL: {}, Target: {}) - Scenario: {} (Confidence: {})",
                        orderType, optionSymbol, optionEntryPrice, stopLossPrice, targetPrice, entryDecision.getScenarioName(), entryDecision.getConfidence());

                // Send notification for order creation
                try {
                    orderNotificationService.sendOrderCreationNotification(order);
                } catch (Exception e) {
                    log.error("Failed to send order creation notification for order: {}", order.getId(), e);
                }

                // Database update to ensure order is saved
                updateOrdersToDatabase();
                log.info("📝 TRADE CREATED - {} {} @ {}",
                        orderType, order.getTradingSymbol(), optionEntryPrice);
                log.info("📊 ORDER DETAILS - ID: {}, Status: {}, StopLoss: {}, Target: {}",
                        order.getId(), order.getStatus(), order.getStopLossPrice(), order.getTargetPrice());
                logScores(order);

            } else {
                log.warn("❌ ENTRY DECISION INVALID - Cannot create order without valid scenario");
            }

        } catch (Exception e) {
            log.error("Error creating trade order for tick: {}", tick.getInstrumentToken(), e);
        }
        return order;
    }


    public void entryOrder(Tick tick, ScalpingEntryDecision entryDecision, boolean inTradingZone, String dominantTrend,
                           double qualityScore, Map<String, DetailedCategoryScore> detailedCallScores, Map<String, DetailedCategoryScore> detailedPutScores,
                           UnstableMarketConditionAnalysisService.FlexibleFilteringResult ntpFilterResult) {
        try {
            boolean hasActiveOrder = activeOrderTrackingService.hasActiveOrder();
            if (!hasActiveOrder) {
                // Determine order type based on entry decision
                String orderType = determineOrderType(dominantTrend);
                JtradeOrder jtradeOrder = createTradeOrder(tick, orderType, entryDecision, inTradingZone, qualityScore, dominantTrend, detailedCallScores, detailedPutScores, ntpFilterResult);
                if (jtradeOrder.getId() == null) {
                    log.warn("Order creation failed - Order ID is null.");
                    return;
                }
                kiteOrderService.placeOrder(jtradeOrder, Constants.TRANSACTION_TYPE_BUY);
            } else {
                log.warn("⚠️ ACTIVE ORDER EXISTS - Cannot create new order.");
            }
        } catch (KiteException e) {
            log.error("KiteException while validating and executing order: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error validating and executing order: {}", e.getMessage());
        }
    }

    /**
     * Initialize milestone system for a new order with dynamic milestone calculation
     */
    private void initializeMilestoneSystem(JtradeOrder order, Tick tick) {
        try {
            // Get milestone configuration from strategy config
            double minMilestonePoints = order.getOrderType() == OrderTypeEnum.CALL_BUY ?
                    configService.getCallMinMilestonePoints() : configService.getPutMinMilestonePoints();
            
            // Determine Base Milestone Points based on EMA200 condition
            // If current price is above EMA200 for 1min, 5min, and 15min, use DB value
            // Otherwise, use Min Milestone Points as Base Milestone Points
            double baseMilestonePointsFromDB = configService.getBaseMilestonePoints();
            double baseMilestonePoints = shouldUseBaseMilestonePointsFromDB(tick) 
                    ? baseMilestonePointsFromDB 
                    : minMilestonePoints;
            
            log.info("📊 Base Milestone Points Decision - Price above EMA200 (1min, 5min, 15min): {}, Using Base: {} (DB: {}, Min: {})",
                    shouldUseBaseMilestonePointsFromDB(tick), baseMilestonePoints, baseMilestonePointsFromDB, minMilestonePoints);
            
            // Use the actual calculated target points from the order instead of JSON value
            double totalTargetPoints = order.getTargetPrice() - order.getEntryPrice();

            // Create target milestones using decremental approach
            List<MilestoneSystem.Milestone> targetMilestones = new ArrayList<>();
            List<String> milestoneHistory = new ArrayList<>();

            if (baseMilestonePoints > 0 && minMilestonePoints > 0) {
                double currentPrice = order.getEntryPrice();
                int milestoneNumber = 1;
                
                // Create milestones until we reach or exceed the target price
                while (currentPrice < order.getTargetPrice()) {
                    // Calculate decremental milestone points for this milestone
                    double milestonePoints = CommonUtils.calculateDecrementalMilestonePoints(milestoneNumber, baseMilestonePoints, minMilestonePoints);
                    
                    // Calculate target price for this milestone
                    double targetPrice = currentPrice + milestonePoints;
                    
                    // Don't exceed the final target price
                    if (targetPrice > order.getTargetPrice()) {
                        targetPrice = order.getTargetPrice();
                    }
                    
                    // Calculate actual points for this milestone
                    double actualPoints = targetPrice - order.getEntryPrice();
                    
                    MilestoneSystem.Milestone milestone = MilestoneSystem.Milestone.builder()
                            .milestoneNumber(milestoneNumber)
                            .points(actualPoints)
                            .targetPrice(targetPrice)
                            .targetHit(false)
                            .profitAtMilestone(0.0)
                            .build();

                    targetMilestones.add(milestone);
                    
                    // Move to next milestone
                    currentPrice = targetPrice;
                    milestoneNumber++;
                    
                    // Safety check to prevent infinite loop
                    if (milestoneNumber > 100) {
                        log.warn("⚠️ Too many milestones created, stopping at milestone {}", milestoneNumber - 1);
                        break;
                    }
                }
            }

            // Set milestone data in order
            order.setTargetMilestones(targetMilestones);
            order.setMilestoneHistory(milestoneHistory);

            // Initialize index price tracking
            order.setMinIndexPrice(order.getEntryIndexPrice());
            order.setMaxIndexPrice(order.getEntryIndexPrice());

            log.info("🎯 Decremental milestone system initialized for {} order - Milestones: {}, Base: {}, Min: {}, Total Target: {} (Entry: {}, Target: {})",
                    order.getOrderType(), targetMilestones.size(), baseMilestonePoints, minMilestonePoints, totalTargetPoints, order.getEntryPrice(), order.getTargetPrice());

        } catch (Exception e) {
            log.error("Error initializing milestone system for order: {}", order.getId(), e);
            // Fallback to traditional system
            order.setTargetMilestones(null);
            order.setMilestoneHistory(null);
        }
    }

    /**
     * Check if current price is above EMA200 for 1min, 5min, and 15min timeframes
     * @param tick Current tick data
     * @return true if price is above EMA200 for all three timeframes, false otherwise
     */
    private boolean shouldUseBaseMilestonePointsFromDB(Tick tick) {
        try {
            // Get flattened indicators to check EMA200 values
            FlattenedIndicators indicators = dynamicRuleEvaluatorService.getFlattenedIndicators(tick);
            if (indicators == null) {
                log.warn("⚠️ FlattenedIndicators not available, defaulting to Min Milestone Points");
                return false;
            }

            // Get current price
            double currentPrice = tick.getLastTradedPrice();

            // Check EMA200 for 1min timeframe
            Double ema200_1min = indicators.getEma200_1min();
            boolean priceAboveEma200_1min = ema200_1min != null && currentPrice > ema200_1min;

            // Check EMA200 for 5min timeframe
            Double ema200_5min = indicators.getEma200_5min();
            boolean priceAboveEma200_5min = ema200_5min != null && currentPrice > ema200_5min;

            // Check EMA200 for 15min timeframe using distance (if available)
            // If distance > 0, price is above EMA200
            Double ema200Distance_15min = indicators.getEma200_distance_15min();
            boolean priceAboveEma200_15min = ema200Distance_15min != null && ema200Distance_15min > 0;

            // All three timeframes must have price above EMA200
            boolean result = priceAboveEma200_1min && priceAboveEma200_5min && priceAboveEma200_15min;

            log.debug("📊 EMA200 Check - 1min: {} (price: {} > ema200: {}), 5min: {} (price: {} > ema200: {}), 15min: {} (distance: {}) - Result: {}",
                    priceAboveEma200_1min, currentPrice, ema200_1min,
                    priceAboveEma200_5min, currentPrice, ema200_5min,
                    priceAboveEma200_15min, ema200Distance_15min, result);

            return result;

        } catch (Exception e) {
            log.error("Error checking EMA200 condition for Base Milestone Points: {}", e.getMessage(), e);
            // On error, default to using Min Milestone Points (safer approach)
            return false;
        }
    }

    public void exitOrder(Tick tick, JtradeOrder order, Double currentIndexPrice) {
        Double currentLTP = activeOrderTrackingService.getCurrentPrice(order, currentIndexPrice);
        exitOrder(order.getId(), order.getExitReason(), currentLTP, currentIndexPrice, tick.getTickTimestamp()); // Use tick timestamp for accurate backtesting
    }

    public void exitOrder(String orderId, ExitReasonEnum exitReason, Double exitPrice, Double exitIndexPrice, Date exitTime) {
        JtradeOrder jtradeOrder = activeOrderTrackingService.getOrderById(orderId);
        if (jtradeOrder == null) {
            log.warn("Order not found for exit: {}", orderId);
            return;
        }

        jtradeOrder.markClosed(exitReason, exitPrice, exitIndexPrice, exitTime);

        // Calculate points from index price movement for daily points tracking
        // This is used to track reasonable target points per day to prevent overtrading
        // CALL: points = exitIndexPrice - entryIndexPrice (positive when index goes up)
        // PUT: points = entryIndexPrice - exitIndexPrice (positive when index goes down)
        Double points = com.jtradebot.processor.service.scheduler.DailyLimitsSchedulerService.calculatePointsFromIndexMovement(
                jtradeOrder.getEntryIndexPrice(), 
                exitIndexPrice, 
                jtradeOrder.getOrderType()
        );

        jtradeOrder.setTotalPoints(points);
        
        // Calculate actual P&L using option prices (for profit tracking)
        Double optionPoints = liveOptionPricingService.calculateProfitLoss(jtradeOrder.getEntryPrice(), exitPrice);
        jtradeOrder.setTotalProfit(optionPoints * jtradeOrder.getQuantity());

        activeOrderTrackingService.updateExitTracking(exitReason, exitTime);

        // Save exited order to database immediately
        try {
            jtradeOrder.updateLastUpdated();
            jtradeOrderRepository.save(jtradeOrder);
            log.info("💾 EXITED ORDER SAVED TO DATABASE - ID: {}, Status: {}", jtradeOrder.getId(), jtradeOrder.getStatus());
        } catch (Exception e) {
            log.error("Error saving exited order to database: {}", e.getMessage(), e);
        }

        activeOrderTrackingService.removeActiveOrderMap(orderId);

        // Clear LTP tracking history for this order
        optionLTPTrackingService.clearLTPHistory(orderId);

        // Enhanced exit logging with clear visual indicator
        log.info("<<<<<<<<<EXIT>>>>>>>>> - Order: {} - {} @ {} (Reason: {}, Points: {}, Profit: {})",
                jtradeOrder.getOrderType(), jtradeOrder.getTradingSymbol(), exitPrice,
                exitReason, points, jtradeOrder.getTotalProfit());

        // Log exit details for analysis
        log.info("EXIT Details - Entry: {}, Exit: {}, Index Entry: {}, Index Exit: {}, Duration: {} minutes",
                jtradeOrder.getEntryPrice(), exitPrice, jtradeOrder.getEntryIndexPrice(), exitIndexPrice,
                calculateOrderDurationMinutes(jtradeOrder));

        kiteOrderService.placeOrder(jtradeOrder, Constants.TRANSACTION_TYPE_SELL);
        // Send notification for order exit
        try {
            orderNotificationService.sendOrderExitNotification(jtradeOrder, exitReason, exitPrice, exitIndexPrice);
        } catch (Exception e) {
            log.error("Failed to send order exit notification for order: {}", jtradeOrder.getId(), e);
        }
    }


    private String determineOrderType(String dominantTrend) {
        return dominantTrend.concat("_BUY");
    }

    /**
     * Calculate order duration in minutes
     */
    private long calculateOrderDurationMinutes(JtradeOrder order) {
        if (order.getEntryTime() == null || order.getExitTime() == null) {
            return 0;
        }

        try {
            // Parse the IST time strings
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss z");
            ZonedDateTime entryTime = ZonedDateTime.parse(order.getEntryTime(), formatter);
            ZonedDateTime exitTime = ZonedDateTime.parse(order.getExitTime(), formatter);

            // Calculate duration in minutes
            return java.time.Duration.between(entryTime, exitTime).toMinutes();
        } catch (Exception e) {
            log.error("Error calculating order duration for order: {} - Entry: {}, Exit: {}",
                    order.getId(), order.getEntryTime(), order.getExitTime(), e);
            return 0;
        }
    }

    public void updateOrdersToDatabase() {
        if (!activeOrderTrackingService.getNeedUpdateFlag()) {
            return;
        }

        try {
            // Update all active orders in database
            for (JtradeOrder order : activeOrderTrackingService.getActiveOrders()) {
                order.updateLastUpdated();
                jtradeOrderRepository.save(order);
            }
            activeOrderTrackingService.setNeedUpdateFlag(false);

        } catch (Exception e) {
            log.error("Error updating orders to database: {}", e.getMessage(), e);
        }
    }


    /**
     * Calculate target based on Support/Resistance levels and return data for storage
     * @return SupportResistanceData containing target points and all SR data
     */
    private SupportResistanceData calculateSupportResistanceTargetWithData(Tick tick, double currentIndexPrice, String orderType) {
        try {
            String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();
            
            // Get 5-minute bar series for support/resistance calculation
            BarSeries fiveMinSeries = barSeriesManager.getBarSeriesForTimeFrame(niftyToken, CandleTimeFrameEnum.FIVE_MIN);
            
            if (fiveMinSeries == null || fiveMinSeries.getBarCount() < 20) {
                log.warn("Insufficient 5-minute data for support/resistance calculation, falling back to percentage mode");
                // Fallback to percentage mode
                double targetPercentage = tradingConfigService.getTargetPercentage();
                double fallbackTarget = currentIndexPrice * (targetPercentage / 100.0);
                return new SupportResistanceData(fallbackTarget, new HashSet<>(), new HashSet<>(), null, null);
            }

            // Create EMA indicator info for support/resistance calculation
            EmaIndicatorInfo emaInfo = new EmaIndicatorInfo();
            emaInfo.setEma9(new org.ta4j.core.indicators.EMAIndicator(new org.ta4j.core.indicators.helpers.ClosePriceIndicator(fiveMinSeries), 9));
            emaInfo.setEma14(new org.ta4j.core.indicators.EMAIndicator(new org.ta4j.core.indicators.helpers.ClosePriceIndicator(fiveMinSeries), 14));
            emaInfo.setEma20(new org.ta4j.core.indicators.EMAIndicator(new org.ta4j.core.indicators.helpers.ClosePriceIndicator(fiveMinSeries), 20));
            emaInfo.setEma34(new org.ta4j.core.indicators.EMAIndicator(new org.ta4j.core.indicators.helpers.ClosePriceIndicator(fiveMinSeries), 34));
            emaInfo.setEma200(new org.ta4j.core.indicators.EMAIndicator(new org.ta4j.core.indicators.helpers.ClosePriceIndicator(fiveMinSeries), 200));

            // Calculate support and resistance levels
            Set<Resistance> resistances = supportResistanceIndicator.calculateResistances(CandleTimeFrameEnum.FIVE_MIN, fiveMinSeries, currentIndexPrice, emaInfo, new int[]{20, 50, 100});
            Set<Support> supports = supportResistanceIndicator.calculateSupports(CandleTimeFrameEnum.FIVE_MIN, fiveMinSeries, currentIndexPrice, emaInfo, new int[]{20, 50, 100});

            // Get nearest resistance and support levels
            double nearestResistance = resistances.isEmpty() ? currentIndexPrice * 1.02 : resistances.iterator().next().getResistanceValue();
            double nearestSupport = supports.isEmpty() ? currentIndexPrice * 0.98 : supports.iterator().next().getSupportValue();

            // Calculate target based on order type
            double calculatedTarget;
            
            // Log all resistances and supports for debugging
            log.info("🔍 ALL RESISTANCES FOUND:");
            int resistanceCount = 0;
            for (Resistance resistance : resistances) {
                resistanceCount++;
                double distance = resistance.getResistanceValue() - currentIndexPrice;
                log.info("   Resistance {}: {} (Distance: {})", 
                        resistanceCount, 
                        resistance.getResistanceValue(),
                        String.format("%.2f", distance));
            }
            
            log.info("🔍 ALL SUPPORTS FOUND:");
            int supportCount = 0;
            for (Support support : supports) {
                supportCount++;
                double distance = currentIndexPrice - support.getSupportValue();
                log.info("   Support {}: {} (Distance: {})", 
                        supportCount, 
                        support.getSupportValue(),
                        String.format("%.2f", distance));
            }

            if ("CALL_BUY".equals(orderType)) {
                // For CALL orders: target at resistance
                double resistanceDistance = nearestResistance - currentIndexPrice;
                calculatedTarget = Math.max(resistanceDistance, 10.0); // Minimum 10 points
                
                log.info("🎯 CALL SR TARGET - Current: {} | Resistance: {} | Target Points: {}",
                        String.format("%.2f", currentIndexPrice),
                        String.format("%.2f", nearestResistance),
                        String.format("%.2f", calculatedTarget));
                        
            } else if ("PUT_BUY".equals(orderType)) {
                // For PUT orders: target at support
                double supportDistance = currentIndexPrice - nearestSupport;
                calculatedTarget = Math.max(supportDistance, 10.0); // Minimum 10 points
                
                log.info("🎯 PUT SR TARGET - Current: {} | Support: {} | Target Points: {}",
                        String.format("%.2f", currentIndexPrice),
                        String.format("%.2f", nearestSupport),
                        String.format("%.2f", calculatedTarget));
            } else {
                // Default fallback
                calculatedTarget = 10.0;
            }
            
            return new SupportResistanceData(calculatedTarget, supports, resistances, nearestSupport, nearestResistance);

        } catch (Exception e) {
            log.error("Error calculating support/resistance target: {}", e.getMessage(), e);
            // Fallback to percentage mode
            double targetPercentage = tradingConfigService.getTargetPercentage();
            double fallbackTarget = currentIndexPrice * (targetPercentage / 100.0);
            return new SupportResistanceData(fallbackTarget, new HashSet<>(), new HashSet<>(), null, null);
        }
    }


    /**
     * Get all completed orders (since all orders are from today only)
     * @return list of all completed orders
     */
    public List<JtradeOrder> getCompletedOrdersForToday() {
        try {
            // Since all orders are from today only, just get all completed orders
            return jtradeOrderRepository.findByStatus("CLOSED");
        } catch (Exception e) {
            log.error("Error getting completed orders: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }


}
