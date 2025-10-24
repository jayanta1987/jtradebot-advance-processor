package com.jtradebot.processor.service.manual;

import com.jtradebot.processor.common.CommonUtils;
import com.jtradebot.processor.config.DayTradingSettingService;
import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.config.TradingConfigurationService;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.MilestoneSystem;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.repository.JtradeOrderRepository;
import com.jtradebot.processor.repository.document.JtradeOrder;
import com.jtradebot.processor.service.order.ActiveOrderTrackingService;
import com.jtradebot.processor.service.order.KiteOrderService;
import com.jtradebot.processor.service.order.OrderManagementService;
import com.jtradebot.processor.service.notification.OrderNotificationService;
import com.jtradebot.processor.service.price.LiveOptionPricingService;
import com.jtradebot.processor.service.price.MockOptionPricingService;
import com.jtradebot.processor.service.quantity.DynamicQuantityService;
import com.jtradebot.processor.service.scheduler.DailyLimitsSchedulerService;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.jtradebot.processor.handler.DateTimeHandler.formatDateToIST;
import static com.jtradebot.processor.handler.DateTimeHandler.getCurrentISTTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final LiveOptionPricingService liveOptionPricingService;
    private final MockOptionPricingService mockOptionPricingService;
    private final TradingConfigurationService tradingConfigService;
    private final DayTradingSettingService dayTradingSettingService;
    private final KiteInstrumentHandler kiteInstrumentHandler;
    private final TickDataManager tickDataManager;
    private final ActiveOrderTrackingService activeOrderTrackingService;
    private final DynamicStrategyConfigService configService;
    private final OrderNotificationService orderNotificationService;
    private final JtradeOrderRepository jtradeOrderRepository;
    private final KiteOrderService kiteOrderService;
    private final OrderManagementService orderManagementService;
    private final DynamicQuantityService dynamicQuantityService;
    private final DailyLimitsSchedulerService dailyLimitsSchedulerService;

    /**
     * Place a manual order immediately, skipping all filters, NTP checks, and category scores
     */
    public JtradeOrder placeOrder(OrderTypeEnum orderType) throws KiteException {
        log.info("🎯 PLACING MANUAL ORDER - Type: {}", orderType);

        // Check daily P&L limits first - if limits are hit, stop processing
        if (dailyLimitsSchedulerService.isDailyLimitReached()) {
            log.warn("🚫 DAILY LIMITS HIT - Stopping tick processing for the day");
            return null;
        }
        
        // Check if there's already an active order
        if (activeOrderTrackingService.hasActiveOrder()) {
            log.warn("⚠️ CANNOT PLACE MANUAL ORDER - Active order already exists");
            return null;
        }

        try {
            // Get current Nifty tick for pricing
            String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();
            Tick niftyTick = tickDataManager.getLastTick(niftyToken);

            if (niftyTick == null) {
                log.error("❌ No Nifty index data available for manual order creation");
                return null;
            }

            double currentIndexPrice = niftyTick.getLastTradedPrice();
            log.info("📊 Current Nifty Index Price: {}", currentIndexPrice);

            // Create order with manual entry decision
            JtradeOrder order = createTradeOrder(niftyTick, orderType, currentIndexPrice);
            
            if (order != null && order.getId() != null) {
                // Place the order through Kite
                kiteOrderService.placeOrder(order, Constants.TRANSACTION_TYPE_BUY);
                
                // Store in memory
                activeOrderTrackingService.setActiveOrderMap(order);
                activeOrderTrackingService.setNeedUpdateFlag(true);
                
                // Save to database
                updateOrdersToDatabase();
                
                log.info("✅ MANUAL ORDER PLACED SUCCESSFULLY - ID: {}, Type: {}, Symbol: {}, Price: {}", 
                        order.getId(), order.getOrderType(), order.getTradingSymbol(), order.getEntryPrice());
                
                // Send notification
                try {
                    orderNotificationService.sendOrderCreationNotification(order);
                } catch (Exception e) {
                    log.error("Failed to send order creation notification for manual order: {}", order.getId(), e);
                }
            }

            return order;

        } catch (Exception e) {
            log.error("❌ ERROR PLACING MANUAL ORDER: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Create a manual trade order with simplified logic (no filters, no category scores)
     */
    private JtradeOrder createTradeOrder(Tick tick, OrderTypeEnum orderType, double currentIndexPrice) throws KiteException {
        JtradeOrder order = new JtradeOrder();
        
        try {
            // Calculate dynamic quantity based on balance and option price
            int dynamicQuantity = dynamicQuantityService.calculateDynamicQuantity(orderType.name());
            
            // Validate quantity using centralized service with email notification
            if (!dynamicQuantityService.validateQuantity(dynamicQuantity, orderType.name(), "Manual Order")) {
                return order;
            }
            // Get option pricing
            Optional<LiveOptionPricingService.LiveOptionPricingInfo> liveOptionPricing = 
                    liveOptionPricingService.getLiveOptionPricing(orderType.name());

            double optionEntryPrice;
            String optionSymbol;
            long optionInstrumentToken;

            if (liveOptionPricing.isPresent()) {
                LiveOptionPricingService.LiveOptionPricingInfo pricingInfo = liveOptionPricing.get();
                optionEntryPrice = pricingInfo.getOptionLTP();
                optionSymbol = pricingInfo.getOptionInstrument().getTradingSymbol();
                optionInstrumentToken = pricingInfo.getOptionInstrument().getInstrumentToken();
                log.info("🎯 USING LIVE OPTION PRICING - Symbol: {}, LTP: {}, Strike: {}", 
                        optionSymbol, optionEntryPrice, pricingInfo.getStrikePrice());
            } else {
                optionEntryPrice = mockOptionPricingService.calculateEntryLTP(currentIndexPrice);
                optionSymbol = orderType == OrderTypeEnum.CALL_BUY ? "MANUAL_CALL_CE" : "MANUAL_PUT_PE";
                optionInstrumentToken = 0L;
                log.info("📊 USING PLACEHOLDER PRICING - Index: {}, Premium: {}", 
                        currentIndexPrice, optionEntryPrice);
            }

            // Calculate stop loss and target
            double stopLossPercentage = tradingConfigService.getStopLossPercentage();
            double stopLossPoints = optionEntryPrice * (stopLossPercentage / 100.0);
            
            // Use percentage-based target for manual orders (simpler)
            double targetPercentage = tradingConfigService.getTargetPercentage();
            double targetPoints = optionEntryPrice * (targetPercentage / 100.0);

            double stopLossPrice = Math.max(0.0, optionEntryPrice - stopLossPoints);
            double targetPrice = optionEntryPrice + targetPoints;

            // Set order details
            order.setId(UUID.randomUUID().toString());
            order.setOrderType(orderType);
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

            // Set manual order specific details
            order.setEntryScenarioName("MANUAL_ORDER");
            order.setEntryScenarioDescription("Manual order placed by user, skipping all filters");
            order.setEntryScenarioConfidence(1.0); // Manual orders have 100% confidence
            order.setEntryMarketConditionSuitable(true); // Skip market condition checks
            order.setEntryQualityScore(10.0); // Manual orders bypass quality checks
            order.setEntryDominantTrend(orderType.name().replace("_BUY", ""));

            // Initialize milestone system only if milestone-based exit is enabled
            if (dayTradingSettingService.isMilestoneBasedExitEnabled()) {
                initializeMilestoneSystem(order, tick);
            } else {
                log.info("Milestone system initialization skipped for manual order - milestoneBasedExitEnabled is false");
            }

            log.info("📝 MANUAL ORDER CREATED - {} {} @ {} (SL: {}, Target: {})",
                    orderType, optionSymbol, optionEntryPrice, stopLossPrice, targetPrice);

        } catch (Exception e) {
            log.error("Error creating manual trade order: {}", e.getMessage(), e);
            return null;
        }

        return order;
    }

    /**
     * Initialize milestone system for manual orders
     */
    private void initializeMilestoneSystem(JtradeOrder order, Tick tick) {
        try {
            // Get milestone configuration from strategy config
            double minMilestonePoints = order.getOrderType() == OrderTypeEnum.CALL_BUY ?
                    configService.getCallMinMilestonePoints() : configService.getPutMinMilestonePoints();
            double baseMilestonePoints = configService.getBaseMilestonePoints();
            

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

            log.info("🎯 Manual order decremental milestone system initialized - Milestones: {}, Base: {}, Min: {}", 
                    targetMilestones.size(), baseMilestonePoints, minMilestonePoints);

        } catch (Exception e) {
            log.error("Error initializing milestone system for manual order: {}", order.getId(), e);
            order.setTargetMilestones(null);
            order.setMilestoneHistory(null);
        }
    }

    /**
     * Update orders to database
     */
    private void updateOrdersToDatabase() {
        if (!activeOrderTrackingService.getNeedUpdateFlag()) {
            return;
        }

        try {
            for (JtradeOrder order : activeOrderTrackingService.getActiveOrders()) {
                order.updateLastUpdated();
                jtradeOrderRepository.save(order);
            }
            activeOrderTrackingService.setNeedUpdateFlag(false);
            log.info("💾 Manual orders updated to database");
        } catch (Exception e) {
            log.error("Error updating manual orders to database: {}", e.getMessage(), e);
        }
    }

    /**
     * Get status of manual orders
     */
    public Map<String, Object> getOrderStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            boolean hasActiveOrder = activeOrderTrackingService.hasActiveOrder();
            status.put("hasActiveOrder", hasActiveOrder);
            
            if (hasActiveOrder) {
                activeOrderTrackingService.getActiveOrders().stream()
                        .filter(order -> "MANUAL_ORDER".equals(order.getEntryScenarioName()))
                        .findFirst().ifPresent(activeOrder -> status.put("activeOrder", Map.of(
                                "id", activeOrder.getId(),
                                "orderType", activeOrder.getOrderType(),
                                "tradingSymbol", activeOrder.getTradingSymbol(),
                                "entryPrice", activeOrder.getEntryPrice(),
                                "stopLossPrice", activeOrder.getStopLossPrice(),
                                "targetPrice", activeOrder.getTargetPrice(),
                                "quantity", activeOrder.getQuantity(),
                                "status", activeOrder.getStatus(),
                                "entryTime", activeOrder.getEntryTime()
                        )));

            }
            
            status.put("success", true);
            status.put("message", "Manual orders status retrieved successfully");
            
        } catch (Exception e) {
            log.error("Error getting manual orders status: {}", e.getMessage(), e);
            status.put("success", false);
            status.put("message", "Error getting manual orders status: " + e.getMessage());
        }
        
        return status;
    }

    /**
     * Get comprehensive order status with detailed information
     * Includes active orders with current profit and closed orders total profit
     */
    public Map<String, Object> getDetailedOrderStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            // Get active orders from memory map
            List<JtradeOrder> activeOrders = activeOrderTrackingService.getActiveOrders();
            List<Map<String, Object>> activeOrderDetails = new ArrayList<>();
            
            double totalActiveProfit = 0.0;
            
            for (JtradeOrder order : activeOrders) {
                Map<String, Object> orderDetail = new HashMap<>();
                orderDetail.put("id", order.getId());
                orderDetail.put("orderType", order.getOrderType());
                orderDetail.put("tradingSymbol", order.getTradingSymbol());
                orderDetail.put("entryPrice", order.getEntryPrice());
                orderDetail.put("stopLossPrice", order.getStopLossPrice());
                orderDetail.put("targetPrice", order.getTargetPrice());
                orderDetail.put("quantity", order.getQuantity());
                orderDetail.put("status", order.getStatus());
                orderDetail.put("entryTime", order.getEntryTime());
                orderDetail.put("entryIndexPrice", order.getEntryIndexPrice());
                
                // Calculate current profit
                double currentProfit = calculateCurrentProfit(order);
                orderDetail.put("currentProfit", currentProfit);
                orderDetail.put("currentProfitPoints", currentProfit / order.getQuantity());
                
                // Get current price
                double currentPrice = getCurrentPrice(order);
                orderDetail.put("currentPrice", currentPrice);
                
                totalActiveProfit += currentProfit;
                activeOrderDetails.add(orderDetail);
            }
            
            // Get closed orders total profit from database
            double totalClosedProfit = getTotalClosedOrdersProfit();
            
            status.put("activeOrdersCount", activeOrders.size());
            status.put("activeOrders", activeOrderDetails);
            status.put("totalActiveProfit", totalActiveProfit);
            status.put("totalClosedOrdersProfit", totalClosedProfit);
            status.put("overallProfit", totalActiveProfit + totalClosedProfit);
            
            // Add daily limit flags
            status.put("dailyProfitLimitReached", dailyLimitsSchedulerService.isDailyProfitLimitReached());
            status.put("dailyLossLimitReached", dailyLimitsSchedulerService.isDailyLossLimitReached());
            status.put("dailyLimitReached", dailyLimitsSchedulerService.isDailyLimitReached());
            
            status.put("success", true);
            status.put("message", "Detailed order status retrieved successfully");
            
        } catch (Exception e) {
            log.error("Error getting detailed order status: {}", e.getMessage(), e);
            status.put("success", false);
            status.put("message", "Error getting detailed order status: " + e.getMessage());
        }
        
        return status;
    }

    /**
     * Calculate current profit for an active order
     */
    private double calculateCurrentProfit(JtradeOrder order) {
        try {
            double currentPrice = getCurrentPrice(order);
            double entryPrice = order.getEntryPrice();
            double quantity = order.getQuantity();
            
            // Calculate points difference
            double points = currentPrice - entryPrice;
            
            // Calculate total profit (points * quantity)
            return points * quantity;
            
        } catch (Exception e) {
            log.error("Error calculating current profit for order: {}", order.getId(), e);
            return 0.0;
        }
    }

    /**
     * Get current price for an order
     */
    private double getCurrentPrice(JtradeOrder order) {
        try {
            // Get current Nifty tick for pricing
            String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();
            Tick niftyTick = tickDataManager.getLastTick(niftyToken);
            
            if (niftyTick == null) {
                log.warn("No Nifty index data available for current price calculation");
                return order.getEntryPrice(); // Fallback to entry price
            }
            
            double currentIndexPrice = niftyTick.getLastTradedPrice();
            
            // Use the same logic as ActiveOrderTrackingService
            return activeOrderTrackingService.getCurrentPrice(order, currentIndexPrice);
            
        } catch (Exception e) {
            log.error("Error getting current price for order: {}", order.getId(), e);
            return order.getEntryPrice(); // Fallback to entry price
        }
    }

    /**
     * Get total profit from all closed orders
     */
    private double getTotalClosedOrdersProfit() {
        try {
            // Get all closed orders from database
            List<JtradeOrder> closedOrders = jtradeOrderRepository.findAll().stream()
                    .filter(order -> "CLOSED".equals(order.getStatus()))
                    .toList();
            
            double totalProfit = 0.0;
            for (JtradeOrder order : closedOrders) {
                if (order.getTotalProfit() != null) {
                    totalProfit += order.getTotalProfit();
                }
            }
            
            log.info("Retrieved {} closed orders with total profit: {}", closedOrders.size(), totalProfit);
            return totalProfit;
            
        } catch (Exception e) {
            log.error("Error getting total closed orders profit: {}", e.getMessage(), e);
            return 0.0;
        }
    }

    /**
     * Exit all active orders immediately
     * This will find all ACTIVE orders and close them with FORCE_EXIT reason
     * Uses OrderManagementService.exitOrder method for consistency
     * 
     * Note: After manual exit (FORCE_EXIT), new entry will be restricted for the current 5-minute candle
     * to prevent immediate re-entry after manual exit
     */
    public Map<String, Object> exitAllActiveOrders() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get all active orders from memory map
            List<JtradeOrder> activeOrders = activeOrderTrackingService.getActiveOrders();
            
            if (activeOrders.isEmpty()) {
                log.info("🚪 EXIT ALL REQUEST - No active orders found");
                result.put("success", true);
                result.put("message", "No active orders found to exit");
                result.put("exitedOrdersCount", 0);
                result.put("exitedOrders", new ArrayList<>());
                return result;
            }
            
            log.info("🚪 EXIT ALL REQUEST - Found {} active orders to exit", activeOrders.size());
            
            // Get current Nifty tick for exit processing
            String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();
            Tick currentTick = tickDataManager.getLastTick(niftyToken);
            
            if (currentTick == null) {
                log.error("❌ EXIT ALL FAILED - No current tick data available");
                result.put("success", false);
                result.put("message", "No current tick data available for exit processing");
                result.put("exitedOrdersCount", 0);
                result.put("exitedOrders", new ArrayList<>());
                return result;
            }
            
            List<Map<String, Object>> exitedOrdersDetails = new ArrayList<>();
            int successCount = 0;
            int failureCount = 0;
            
            for (JtradeOrder order : activeOrders) {
                try {
                    // Store order details before exit for response
                    Map<String, Object> orderDetail = new HashMap<>();
                    orderDetail.put("id", order.getId());
                    orderDetail.put("orderType", order.getOrderType());
                    orderDetail.put("tradingSymbol", order.getTradingSymbol());
                    orderDetail.put("entryPrice", order.getEntryPrice());
                    orderDetail.put("quantity", order.getQuantity());
                    orderDetail.put("exitReason", "FORCE_EXIT");
                    
                    // Set FORCE_EXIT reason before calling exitOrder to ensure proper tracking
                    order.setExitReason(com.jtradebot.processor.model.enums.ExitReasonEnum.FORCE_EXIT);
                    
                    // Use OrderManagementService.exitOrder for consistent exit processing
                    orderManagementService.exitOrder(currentTick, order, currentTick.getLastTradedPrice());
                    
                    // Get exit details after processing
                    orderDetail.put("exitPrice", order.getExitPrice());
                    orderDetail.put("points", order.getTotalPoints());
                    orderDetail.put("profit", order.getTotalProfit());
                    orderDetail.put("exitTime", order.getExitTime());
                    
                    exitedOrdersDetails.add(orderDetail);
                    successCount++;
                    
                    log.info("✅ ORDER EXITED - ID: {}, Type: {}, Symbol: {}, Entry: {}, Exit: {}, Points: {}, Profit: {}",
                            order.getId(), order.getOrderType(), order.getTradingSymbol(), 
                            order.getEntryPrice(), order.getExitPrice(), order.getTotalPoints(), order.getTotalProfit());
                    
                } catch (Exception e) {
                    log.error("❌ ERROR EXITING ORDER - ID: {}, Error: {}", order.getId(), e.getMessage(), e);
                    failureCount++;
                }
            }
            
            result.put("success", true);
            result.put("message", String.format("Exit all completed. Success: %d, Failures: %d", successCount, failureCount));
            result.put("exitedOrdersCount", successCount);
            result.put("failedOrdersCount", failureCount);
            result.put("exitedOrders", exitedOrdersDetails);
            
            log.info("🏁 EXIT ALL COMPLETED - Success: {}, Failures: {}", successCount, failureCount);
            
        } catch (Exception e) {
            log.error("❌ ERROR IN EXIT ALL OPERATION: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "Error during exit all operation: " + e.getMessage());
            result.put("exitedOrdersCount", 0);
            result.put("exitedOrders", new ArrayList<>());
        }
        
        return result;
    }

    /**
     * Delete all orders from the jtrade_orders collection
     * Use with caution - this will permanently delete all orders
     */
    public Map<String, Object> deleteAllOrders() {
        try {
            log.info("🗑️ DELETE ALL ORDERS REQUEST - Starting deletion process");
            
            long count = jtradeOrderRepository.count();
            log.info("📊 Found {} orders to delete", count);
            
            jtradeOrderRepository.deleteAll();
            
            log.info("✅ Successfully deleted {} orders from jtrade_orders collection", count);
            
            return Map.of(
                "success", true,
                "message", "Successfully deleted all orders from jtrade_orders collection",
                "deletedCount", count,
                "timestamp", System.currentTimeMillis()
            );
        } catch (Exception e) {
            log.error("❌ ERROR DELETING ALL ORDERS: {}", e.getMessage(), e);
            return Map.of(
                "success", false,
                "message", "Failed to delete all orders: " + e.getMessage(),
                "timestamp", System.currentTimeMillis()
            );
        }
    }

}
