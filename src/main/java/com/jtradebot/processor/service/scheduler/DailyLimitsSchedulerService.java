package com.jtradebot.processor.service.scheduler;

import com.jtradebot.processor.config.TradingConfigurationService;
import com.jtradebot.processor.model.enums.ExitReasonEnum;
import com.jtradebot.processor.repository.document.JtradeOrder;
import com.jtradebot.processor.service.order.ActiveOrderTrackingService;
import com.jtradebot.processor.service.order.OrderManagementService;
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

    // Daily limits flag - set to true when limits are reached
    private final AtomicBoolean dailyLimitReached = new AtomicBoolean(false);
    private final AtomicBoolean dailyProfitLimitReached = new AtomicBoolean(false);
    private final AtomicBoolean dailyLossLimitReached = new AtomicBoolean(false);

    /**
     * Check daily P&L limits every 3 minutes
     * Runs every 5 minutes (300,000 milliseconds)
     */
    @Scheduled(cron = "0 */3 * * * *")
    public void checkDailyPnLLimits() {
        try {
            resetDailyLimits();
            log.debug("Starting daily P&L limits check");

            // Get daily limits from configuration
            double maxProfitPerDay = tradingConfigurationService.getMaxProfitPerDay();
            double maxLossPerDay = tradingConfigurationService.getMaxLossPerDay();
            
            // Calculate today's P&L
            double todayPnL = calculateTodayPnL();
            
            log.info("Daily P&L check - Today's P&L: {}, Max Profit: {}, Max Loss: {}",
                     String.format("%.2f", todayPnL), String.format("%.2f", maxProfitPerDay), String.format("%.2f", maxLossPerDay));
            
            // Check if profit limit is exceeded
            if (todayPnL >= maxProfitPerDay) {
                log.warn("Daily profit limit hit - Today's profit: {} >= Max allowed: {}", 
                        String.format("%.2f", todayPnL), String.format("%.2f", maxProfitPerDay));
                dailyProfitLimitReached.set(true);
                dailyLimitReached.set(true);
                closeAllActiveOrdersWhenLimitsHit("DAILY_PROFIT_LIMIT_HIT");
                return;
            }
            
            // Check if loss limit is exceeded
            if (todayPnL <= -maxLossPerDay) {
                log.warn("Daily loss limit hit - Today's loss: {} >= Max allowed: {}", 
                        String.format("%.2f", Math.abs(todayPnL)), String.format("%.2f", maxLossPerDay));
                dailyLossLimitReached.set(true);
                dailyLimitReached.set(true);
                closeAllActiveOrdersWhenLimitsHit("DAILY_LOSS_LIMIT_HIT");
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
    }

    /**
     * Check if daily limits have been reached
     * @return true if limits are reached, false otherwise
     */
    public boolean isDailyLimitReached() {
        return dailyLimitReached.get();
    }


    /**
     * Calculate today's P&L from completed orders
     * @return today's total P&L
     */
    private double calculateTodayPnL() {
        try {
            // Get all completed orders for today (no date range needed)
            List<JtradeOrder> todayOrders = orderManagementService.getCompletedOrdersForToday();
            
            double totalPnL = 0.0;
            for (JtradeOrder order : todayOrders) {
                if (order.getTotalProfit() != null) {
                    totalPnL += order.getTotalProfit();
                }
            }
            
            log.debug("Found {} completed orders today, Total P&L: {}", 
                     todayOrders.size(), String.format("%.2f", totalPnL));
            
            return totalPnL;
            
        } catch (Exception e) {
            log.error("Error calculating today's P&L: {}", e.getMessage(), e);
            return 0.0; // Return 0 on error to avoid false positives
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
                    ExitReasonEnum exitReason = reason.equals("DAILY_PROFIT_LIMIT_HIT") ? 
                            ExitReasonEnum.MAX_DAY_PROFIT_REACHED : ExitReasonEnum.MAX_DAY_LOSS_REACHED;
                    
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
