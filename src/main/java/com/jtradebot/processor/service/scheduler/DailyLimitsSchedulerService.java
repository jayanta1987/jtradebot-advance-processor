package com.jtradebot.processor.service.scheduler;

import com.jtradebot.processor.config.TradingConfigurationService;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.enums.ExitReasonEnum;
import com.jtradebot.processor.repository.document.JtradeOrder;
import com.jtradebot.processor.service.order.ActiveOrderTrackingService;
import com.jtradebot.processor.service.order.OrderManagementService;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyLimitsSchedulerService {

    private final TradingConfigurationService tradingConfigurationService;
    private final OrderManagementService orderManagementService;
    private final ActiveOrderTrackingService activeOrderTrackingService;
    private final KiteInstrumentHandler kiteInstrumentHandler;
    private final TickDataManager tickDataManager;

    // Daily limits flag - set to true when limits are reached
    private final AtomicBoolean dailyLimitReached = new AtomicBoolean(false);
    private final AtomicBoolean dailyProfitLimitReached = new AtomicBoolean(false);
    private final AtomicBoolean dailyLossLimitReached = new AtomicBoolean(false);
    private final AtomicBoolean dailyPointsLimitReached = new AtomicBoolean(false);

    /**
     * Check daily P&L limits every 30 sec
     * Runs every 30 sec
     */
    @Scheduled(fixedRate = 30000)
    public void checkDailyPnLLimits() {
        try {
            resetDailyLimits();
            log.debug("Starting daily P&L limits check");

            // Get daily limits from configuration with error handling
            double maxProfitPerDay;
            double maxLossPerDay;
            double maxPointsPerDay;
            
            try {
                maxProfitPerDay = tradingConfigurationService.getMaxProfitPerDay();
                maxLossPerDay = tradingConfigurationService.getMaxLossPerDay();
                maxPointsPerDay = tradingConfigurationService.getMaxPointsPerDay();
                log.debug("Daily limits loaded - Max Profit: {}, Max Loss: {}, Max Points: {}", 
                         String.format("%.2f", maxProfitPerDay), String.format("%.2f", maxLossPerDay), 
                         String.format("%.2f", maxPointsPerDay));
            } catch (Exception e) {
                log.error("❌ CRITICAL CONFIGURATION ERROR: Failed to load daily limits from configuration: {}", e.getMessage());
                log.error("❌ Daily P&L limits scheduler will be disabled until configuration is fixed");
                // Don't return - let the exception propagate to stop the scheduler
                throw new RuntimeException("Daily limits configuration is missing or invalid. Please fix configuration before starting the system.", e);
            }
            
            // Calculate today's P&L (including both closed and active orders)
            double todayPnL = calculateTodayPnL();
            
            log.debug("Daily P&L check - Today's Total P&L (Closed + Active): {}, Max Profit: {}, Max Loss: {}",
                     String.format("%.2f", todayPnL), String.format("%.2f", maxProfitPerDay), String.format("%.2f", maxLossPerDay));
            
            // Check if profit limit is exceeded
            if (todayPnL >= maxProfitPerDay) {
                log.debug("🎯🎯🎯🎯🎯🎯🎯🎯🎯🎯🎯🎯🎯🎯🎯🎯🎯🎯🎯🎯\n" +
                        "🚨 PROFIT LIMIT HIT! 🚨\n" +
                        "💰 Today's Profit: {} >= Max Allowed: {}\n" +
                        "🎉 EXCELLENT TRADING DAY! 🎉\n" +
                        "🎯🎯🎯🎯🎯🎯🎯🎯🎯🎯🎯🎯🎯🎯🎯🎯🎯🎯🎯🎯", 
                        String.format("%.2f", todayPnL), String.format("%.2f", maxProfitPerDay));
                dailyProfitLimitReached.set(true);
                dailyLimitReached.set(true);
                closeAllActiveOrdersWhenLimitsHit("DAILY_PROFIT_LIMIT_HIT");
                return;
            }
            
            // Check if loss limit is exceeded
            if (todayPnL <= -maxLossPerDay) {
                log.debug("⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️\n" +
                        "🚨 LOSS LIMIT HIT! 🚨\n" +
                        "💸 Today's Loss: {} >= Max Allowed: {}\n" +
                        "🛑 TRADING STOPPED FOR RISK MANAGEMENT 🛑\n" +
                        "⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️⚠️", 
                        String.format("%.2f", Math.abs(todayPnL)), String.format("%.2f", maxLossPerDay));
                dailyLossLimitReached.set(true);
                dailyLimitReached.set(true);
                closeAllActiveOrdersWhenLimitsHit("DAILY_LOSS_LIMIT_HIT");
                return;
            }
            
            // Check if points limit is exceeded
            double todayTotalPoints = calculateTodayTotalPoints();
            if (todayTotalPoints >= maxPointsPerDay) {
                log.debug("📊📊📊📊📊📊📊📊📊📊📊📊📊📊📊📊📊📊📊📊\n" +
                        "🚨 POINTS LIMIT HIT! 🚨\n" +
                        "📈 Today's Total Points: {} >= Max Allowed: {}\n" +
                        "🎯 EXCELLENT TRADING PERFORMANCE! 🎯\n" +
                        "📊📊📊📊📊📊📊📊📊📊📊📊📊📊📊📊📊📊📊📊", 
                        String.format("%.2f", todayTotalPoints), String.format("%.2f", maxPointsPerDay));
                dailyPointsLimitReached.set(true);
                dailyLimitReached.set(true);
                closeAllActiveOrdersWhenLimitsHit("DAILY_POINTS_LIMIT_HIT");
                return;
            }
            
            log.debug("All limits within bounds, continuing trading");
            
        } catch (Exception e) {
            log.error("Error checking daily P&L limits: {}", e.getMessage(), e);
        }
    }

    /**
     * Reset daily limits flag at the start of each trading day
     * This should be called when the trading day starts
     */
    public void resetDailyLimits() {
        log.info("Resetting daily limits flags for new trading day");
        dailyLimitReached.set(false);
        dailyProfitLimitReached.set(false);
        dailyLossLimitReached.set(false);
        dailyPointsLimitReached.set(false);
    }

    /**
     * Check if daily limits have been reached
     * @return true if limits are reached, false otherwise
     */
    public boolean isDailyLimitReached() {
        return dailyLimitReached.get();
    }

    /**
     * Check if daily profit limit has been reached
     * @return true if profit limit is reached, false otherwise
     */
    public boolean isDailyProfitLimitReached() {
        return dailyProfitLimitReached.get();
    }

    /**
     * Check if daily loss limit has been reached
     * @return true if loss limit is reached, false otherwise
     */
    public boolean isDailyLossLimitReached() {
        return dailyLossLimitReached.get();
    }

    /**
     * Check if daily points limit has been reached
     * @return true if points limit is reached, false otherwise
     */
    public boolean isDailyPointsLimitReached() {
        return dailyPointsLimitReached.get();
    }


    /**
     * Calculate today's P&L from completed orders and active orders
     * @return today's total P&L including both closed and active orders
     */
    private double calculateTodayPnL() {
        try {
            double totalPnL = 0.0;
            
            // 1. Get all completed orders for today
            List<JtradeOrder> completedOrders = orderManagementService.getCompletedOrdersForToday();
            double closedOrdersPnL = 0.0;
            for (JtradeOrder order : completedOrders) {
                if (order.getTotalProfit() != null) {
                    closedOrdersPnL += order.getTotalProfit();
                }
            }
            
            // 2. Get all active orders and calculate their current P&L
            double activeOrdersPnL = 0.0;
            List<JtradeOrder> activeOrders = activeOrderTrackingService.getActiveOrders();
            for (JtradeOrder activeOrder : activeOrders) {
                try {
                    // Get current index price (assuming Nifty token for index price)
                    Double currentIndexPrice = getCurrentIndexPrice();
                    if (currentIndexPrice != null) {
                        // Get current option price for the active order
                        Double currentOptionPrice = activeOrderTrackingService.getCurrentPrice(activeOrder, currentIndexPrice);
                        if (currentOptionPrice != null) {
                            // Calculate current P&L for this active order
                            double points = currentOptionPrice - activeOrder.getEntryPrice();
                            double orderPnL = points * activeOrder.getQuantity();
                            activeOrdersPnL += orderPnL;
                            
                            log.debug("Active order P&L - ID: {}, Entry: {}, Current: {}, Points: {}, P&L: {}", 
                                     activeOrder.getId(), activeOrder.getEntryPrice(), currentOptionPrice, 
                                     String.format("%.2f", points), String.format("%.2f", orderPnL));
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error calculating P&L for active order: {} - {}", activeOrder.getId(), e.getMessage());
                }
            }
            
            // 3. Calculate total P&L
            totalPnL = closedOrdersPnL + activeOrdersPnL;
            
            // Create profit visualization for logs
            log.debug("📊 Closed Orders: {} ({} orders) " +
                     "📈 Active Orders: {} ({} orders) " +
                     "💎 Total P&L: {}",
                     String.format("%.2f", closedOrdersPnL), completedOrders.size(),
                     String.format("%.2f", activeOrdersPnL), activeOrders.size(),
                     String.format("%.2f", totalPnL));
            
            return totalPnL;
            
        } catch (Exception e) {
            log.error("Error calculating today's P&L: {}", e.getMessage(), e);
            return 0.0; // Return 0 on error to avoid false positives
        }
    }
    
    /**
     * Calculate today's total points from closed orders and active orders
     * Note: Active orders are retrieved from the in-memory map (not DB) for performance
     * @return today's total points including both closed and active orders
     */
    private double calculateTodayTotalPoints() {
        try {
            double totalPoints = 0.0;
            
            // 1. Get all closed orders for today and sum their totalPoints
            List<JtradeOrder> closedOrders = orderManagementService.getCompletedOrdersForToday();
            double closedOrdersPoints = 0.0;
            for (JtradeOrder order : closedOrders) {
                if (order.getTotalPoints() != null) {
                    closedOrdersPoints += order.getTotalPoints();
                }
            }
            
            // 2. Get all active orders from in-memory map and calculate their current points
            // Points are calculated from index price movement with sign adjustment for CALL vs PUT
            double activeOrdersPoints = 0.0;
            List<JtradeOrder> activeOrders = activeOrderTrackingService.getActiveOrders();
            for (JtradeOrder activeOrder : activeOrders) {
                try {
                    // Get current index price
                    Double currentIndexPrice = getCurrentIndexPrice();
                    if (currentIndexPrice != null && activeOrder.getEntryIndexPrice() != null) {
                        // Calculate points based on index price movement for daily points tracking
                        double points = calculatePointsFromIndexMovement(
                                activeOrder.getEntryIndexPrice(), 
                                currentIndexPrice, 
                                activeOrder.getOrderType()
                        );
                        
                        activeOrdersPoints += points;
                        
                        log.debug("Active order points - ID: {}, Order Type: {}, Entry Index: {}, Current Index: {}, Points: {}", 
                                 activeOrder.getId(), activeOrder.getOrderType(), 
                                 activeOrder.getEntryIndexPrice(), currentIndexPrice, 
                                 String.format("%.2f", points));
                    }
                } catch (Exception e) {
                    log.warn("Error calculating points for active order: {} - {}", activeOrder.getId(), e.getMessage());
                }
            }
            
            // 3. Calculate total points
            totalPoints = closedOrdersPoints + activeOrdersPoints;
            
            // Create points visualization for logs
            log.debug("📊 Closed Orders Points: {} ({} orders) " +
                     "📈 Active Orders Points: {} ({} orders) " +
                     "💎 Total Points: {}",
                     String.format("%.2f", closedOrdersPoints), closedOrders.size(),
                     String.format("%.2f", activeOrdersPoints), activeOrders.size(),
                     String.format("%.2f", totalPoints));
            
            return totalPoints;
            
        } catch (Exception e) {
            log.error("Error calculating today's total points: {}", e.getMessage(), e);
            return 0.0; // Return 0 on error to avoid false positives
        }
    }
    
    /**
     * Calculate points from index price movement for daily points tracking
     * This is used to track reasonable target points per day to prevent overtrading
     * CALL: points = exitIndexPrice - entryIndexPrice (positive when index goes up)
     * PUT: points = entryIndexPrice - exitIndexPrice (positive when index goes down)
     * 
     * @param entryIndexPrice Entry index price
     * @param exitIndexPrice Exit index price (or current index price for active orders)
     * @param orderType Order type (CALL_BUY or PUT_BUY)
     * @return Calculated points based on index movement
     */
    public static double calculatePointsFromIndexMovement(Double entryIndexPrice, Double exitIndexPrice, com.jtradebot.processor.model.enums.OrderTypeEnum orderType) {
        if (entryIndexPrice == null || exitIndexPrice == null) {
            return 0.0;
        }
        
        if (orderType == null) {
            return 0.0;
        }
        
        if (orderType == com.jtradebot.processor.model.enums.OrderTypeEnum.CALL_BUY) {
            // CALL: positive when index goes up
            return exitIndexPrice - entryIndexPrice;
        } else if (orderType == com.jtradebot.processor.model.enums.OrderTypeEnum.PUT_BUY) {
            // PUT: positive when index goes down
            return entryIndexPrice - exitIndexPrice;
        } else {
            // Default to CALL calculation for unknown types
            return exitIndexPrice - entryIndexPrice;
        }
    }
    
    /**
     * Get current index price for active order P&L calculation
     * @return current Nifty index price
     */
    private Double getCurrentIndexPrice() {
        try {
            // Get current Nifty index price from tick data manager
            String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();
            Tick niftyTick = tickDataManager.getLastTick(niftyToken);
            
            if (niftyTick != null) {
                return niftyTick.getLastTradedPrice();
            } else {
                log.warn("No Nifty index tick data available for active order P&L calculation");
                return null;
            }
        } catch (Exception e) {
            log.error("Error getting current index price: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Close all active orders when daily limits are hit
     * @param reason reason for closure
     */
    private void closeAllActiveOrdersWhenLimitsHit(String reason) {
        try {
            log.warn("Daily limits closure - Reason: {}", reason);
            
            if (!activeOrderTrackingService.hasActiveOrder()) {
                log.info("Daily limits closure - No active orders to close");
                return;
            }
            
            List<JtradeOrder> activeOrders = activeOrderTrackingService.getActiveOrders();
            log.warn("Daily limits closure - Found {} active orders to close", activeOrders.size());
            
            // Close each active order using the same pattern as MarketEndSchedulerService
            for (JtradeOrder order : activeOrders) {
                try {
                    log.warn("Daily limits closure - Closing order: {} (Type: {}, Symbol: {})", 
                            order.getId(), order.getOrderType(), order.getTradingSymbol());

                    // Use the same exitOrder method as MarketEndSchedulerService
                    ExitReasonEnum exitReason;
                    if (reason.equals("DAILY_PROFIT_LIMIT_HIT")) {
                        exitReason = ExitReasonEnum.MAX_DAY_PROFIT_REACHED;
                    } else if (reason.equals("DAILY_POINTS_LIMIT_HIT")) {
                        exitReason = ExitReasonEnum.MAX_POINTS_PER_DAY_REACHED;
                    } else {
                        exitReason = ExitReasonEnum.MAX_DAY_LOSS_REACHED;
                    }
                    
                    orderManagementService.exitOrder(
                            order.getId(), 
                            exitReason, 
                            null, // Current price - will be fetched by exitOrder method
                            null, // Current index price - will be fetched by exitOrder method
                            new Date()
                    );
                    
                    log.warn("Daily limits closure - Successfully closed order: {}", order.getId());
                    
                } catch (Exception e) {
                    log.error("Daily limits closure - Error closing order: {} - {}", 
                            order.getId(), e.getMessage(), e);
                }
            }
            
            log.warn("Daily limits closure - Order closure process completed");
            
        } catch (Exception e) {
            log.error("Error in daily limits order closure: {}", e.getMessage(), e);
        }
    }

}
