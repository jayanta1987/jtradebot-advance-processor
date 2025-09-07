package com.jtradebot.processor.service.order;

import com.jtradebot.processor.common.CommonUtils;
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
import com.jtradebot.processor.service.risk.DynamicRiskManagementService;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.jtradebot.processor.handler.DateTimeHandler.formatDateToIST;
import static com.jtradebot.processor.handler.DateTimeHandler.getCurrentISTTime;
import static com.jtradebot.processor.service.order.LogOrderService.logScores;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderManagementService {

    private final LiveOptionPricingService liveOptionPricingService;
    private final MockOptionPricingService mockOptionPricingService;
    private final TradingConfigurationService tradingConfigService;
    private final DynamicRiskManagementService dynamicRiskManagementService;
    private final KiteInstrumentHandler kiteInstrumentHandler;
    private final TickDataManager tickDataManager;
    private final ActiveOrderTrackingService activeOrderTrackingService;
    private final DynamicStrategyConfigService configService;
    private final OrderNotificationService orderNotificationService;
    private final JtradeOrderRepository jtradeOrderRepository;
    private final KiteOrderService kiteOrderService;
    private final DynamicRuleEvaluatorService dynamicRuleEvaluatorService;
    private final UnstableMarketConditionAnalysisService unstableMarketConditionAnalysisService;

    public JtradeOrder createTradeOrder(Tick tick, String orderType, ScalpingEntryDecision entryDecision, Boolean entryMarketConditionSuitable,
                                        double qualityScore, Map<String, Double> callScores, Map<String, Double> putScores, String dominantTrend,
                                        Map<String, DetailedCategoryScore> detailedCallScores, Map<String, DetailedCategoryScore> detailedPutScores) throws KiteException {
        JtradeOrder order = new JtradeOrder();
        try {
            double stopLossPoints, targetPoints;
            String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();
            Tick niftyTick = tickDataManager.getLastTick(niftyToken);

            if (niftyTick == null) {
                log.error("No Nifty index data available for order creation");
                return order;
            }
            double currentIndexPrice = niftyTick.getLastTradedPrice();

            // Calculate dynamic stop loss and target based on 5-minute candle range
            stopLossPoints = dynamicRiskManagementService.calculateDynamicStopLoss(niftyToken, currentIndexPrice);
            targetPoints = dynamicRiskManagementService.calculateDynamicTarget(stopLossPoints);

            // Try to get live option pricing first (for live profile)
            Optional<LiveOptionPricingService.LiveOptionPricingInfo> liveOptionPricing = liveOptionPricingService.getLiveOptionPricing(orderType);

            double optionEntryPrice;
            String optionSymbol;
            long optionInstrumentToken;

            if (liveOptionPricing.isPresent()) { // live pricing available
                LiveOptionPricingService.LiveOptionPricingInfo pricingInfo = liveOptionPricing.get();
                optionEntryPrice = pricingInfo.getOptionLTP();
                optionSymbol = pricingInfo.getOptionInstrument().getTradingSymbol();
                optionInstrumentToken = pricingInfo.getOptionInstrument().getInstrumentToken();
                log.info("🎯 USING LIVE OPTION PRICING - Symbol: {}, LTP: {}, Strike: {}, Index: {}",
                        optionSymbol, optionEntryPrice, pricingInfo.getStrikePrice(), currentIndexPrice);
            } else { // local or fallback to mock pricing
                optionEntryPrice = mockOptionPricingService.calculateEntryLTP(currentIndexPrice);
                // Use placeholder option symbols since we're analyzing with index/future tokens
                optionSymbol = "CALL_BUY".equals(orderType) ? "TEST_OPTION_CE" : "TEST_OPTION_PE";
                optionInstrumentToken = 0L; // No instrument token (using placeholder symbols)
                log.info("📊 USING PLACEHOLDER PRICING - Index: {}, Premium: {} (1% of index)",
                        currentIndexPrice, optionEntryPrice);
            }

            if (entryDecision.isShouldEntry() && entryDecision.getScenarioName() != null) {
                if (activeOrderTrackingService.shouldBlockEntryAfterStopLoss(Long.valueOf(niftyToken))) {
                    log.warn("🚫 ORDER CREATION BLOCKED - Recent STOPLOSS_HIT exit in same 5-min candle. Entry time: {}",
                            formatDateToIST(tick.getTickTimestamp()));
                    return order;
                }

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
                order.setQuantity(tradingConfigService.getMinLotSize());
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
                order.setEntryCallScores(callScores);
                order.setEntryPutScores(putScores);
                order.setEntryDominantTrend(dominantTrend);

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

                // Initialize milestone system
                initializeMilestoneSystem(order, tick);

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
                           double qualityScore, Map<String, Double> callScores, Map<String, Double> putScores, Map<String,
                    DetailedCategoryScore> detailedCallScores, Map<String, DetailedCategoryScore> detailedPutScores) {
        try {
            boolean hasActiveOrder = activeOrderTrackingService.hasActiveOrder();
            if (!hasActiveOrder) {
                // Determine order type based on entry decision
                String orderType = determineOrderType(dominantTrend);
                JtradeOrder jtradeOrder = createTradeOrder(tick, orderType, entryDecision, inTradingZone, qualityScore, callScores, putScores, dominantTrend, detailedCallScores, detailedPutScores);
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
            // Get milestone configuration from strategy config (JSON value)
            double jsonMilestonePoints = order.getOrderType() == OrderTypeEnum.CALL_BUY ?
                    configService.getCallMilestonePoints() : configService.getPutMilestonePoints();
            double totalTargetPoints = order.getOrderType() == OrderTypeEnum.CALL_BUY ?
                    configService.getCallTargetPoints() : configService.getPutTargetPoints();

            // Calculate dynamic milestone points using ATR values
            double dynamicMilestonePoints = CommonUtils.calculateDynamicMilestonePoints(tick, tickDataManager, jsonMilestonePoints);
            
            // Use the dynamic milestone points for milestone creation

            // Create target milestones
            List<MilestoneSystem.Milestone> targetMilestones = new ArrayList<>();
            List<String> milestoneHistory = new ArrayList<>();

            if (dynamicMilestonePoints > 0) {
                int milestoneCount = (int) Math.ceil(totalTargetPoints / dynamicMilestonePoints);
                for (int i = 1; i <= milestoneCount; i++) {
                    double points = Math.min(i * dynamicMilestonePoints, totalTargetPoints);
                    double targetPrice = order.getEntryPrice() + points; // Target is always entry + points

                    MilestoneSystem.Milestone milestone = MilestoneSystem.Milestone.builder()
                            .milestoneNumber(i)
                            .points(points)
                            .targetPrice(targetPrice)
                            .targetHit(false)
                            .profitAtMilestone(0.0)
                            .build();

                    targetMilestones.add(milestone);
                }
            }

            // Set milestone data in order
            order.setTargetMilestones(targetMilestones);
            order.setMilestoneHistory(milestoneHistory);

            // Initialize index price tracking
            order.setMinIndexPrice(order.getEntryIndexPrice());
            order.setMaxIndexPrice(order.getEntryIndexPrice());

            log.info("🎯 Dynamic milestone system initialized for {} order - Milestones: {}, Dynamic Step: {} (JSON: {}), Total Target: {}",
                    order.getOrderType(), targetMilestones.size(), dynamicMilestonePoints, jsonMilestonePoints, totalTargetPoints);

        } catch (Exception e) {
            log.error("Error initializing milestone system for order: {}", order.getId(), e);
            // Fallback to traditional system
            order.setTargetMilestones(null);
            order.setMilestoneHistory(null);
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

        jtradeOrder.markExited(exitReason, exitPrice, exitIndexPrice, exitTime);

        // Calculate profit/loss using option pricing service
        Double points = liveOptionPricingService.calculateProfitLoss(jtradeOrder.getEntryPrice(), exitPrice); //

        jtradeOrder.setTotalPoints(points);
        jtradeOrder.setTotalProfit(points * jtradeOrder.getQuantity());

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

}
