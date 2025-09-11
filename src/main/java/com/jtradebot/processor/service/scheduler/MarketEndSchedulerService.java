package com.jtradebot.processor.service.scheduler;

import com.jtradebot.processor.config.TradingConfigurationService;
import com.jtradebot.processor.config.TradingHoursConfig;
import com.jtradebot.processor.handler.DateTimeHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.enums.ExitReasonEnum;
import com.jtradebot.processor.service.order.ActiveOrderTrackingService;
import com.jtradebot.processor.service.order.OrderManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketEndSchedulerService {
    
    private final ActiveOrderTrackingService activeOrderTrackingService;
    private final OrderManagementService orderManagementService;
    private final TradingHoursConfig tradingHoursConfig;
    private final TradingConfigurationService tradingConfigurationService;
    private final TickDataManager tickDataManager;

    /**
     * Check trading hours every 5 minutes and close all active trades if market is closed
     * Runs every 5 minutes (300,000 milliseconds)
     */
    @Scheduled(fixedRate = 60000) // 1 minutes = 60,000 milliseconds
    public void checkTradingHoursAndCloseTrades() {
        try {
            // Check if scheduler is enabled in configuration
            if (!isMarketEndSchedulerEnabled()) {
                log.debug("🕒 TRADING HOURS CHECK - Scheduler is disabled in configuration, skipping");
                return;
            }
            
            log.debug("🕒 TRADING HOURS CHECK - Checking if market is still open");
            
            // Get trading hours from configuration
            int startHour = tradingHoursConfig.getMarketStartHour();
            int startMinute = tradingHoursConfig.getMarketStartMinute();
            int endHour = tradingHoursConfig.getMarketEndHour();
            int endMinute = tradingHoursConfig.getMarketEndMinute();

            if (tickDataManager.getLastTickTime() == null) {
                log.warn("🕒 TRADING HOURS CHECK - Last tick time is null, cannot determine market status, skipping");
                return;
            }

            // Check if market is actually closed using configured trading hours
            if (DateTimeHandler.isMarketOpen(tickDataManager.getLastTickTime(), startHour, startMinute, endHour, endMinute)) {
                log.debug("🕒 TRADING HOURS CHECK - Market is still open ({}:{} - {}:{}), no action needed", 
                         startHour, String.format("%02d", startMinute), endHour, String.format("%02d", endMinute));
                return;
            }
            
            log.info("🕒 TRADING HOURS CHECK - Market is closed ({}:{} - {}:{}), starting trade closure process", 
                    startHour, String.format("%02d", startMinute), endHour, String.format("%02d", endMinute));
            
            // Get all active orders
            List<com.jtradebot.processor.repository.document.JtradeOrder> activeOrders = activeOrderTrackingService.getActiveOrders();
            
            if (activeOrders.isEmpty()) {
                log.info("🕒 TRADING HOURS CHECK - No active orders to close");
                return;
            }
            
            log.info("🕒 TRADING HOURS CHECK - Found {} active orders to close", activeOrders.size());
            
            // Close each active order
            for (com.jtradebot.processor.repository.document.JtradeOrder order : activeOrders) {
                try {
                    log.info("🕒 TRADING HOURS CHECK - Closing order: {} (Type: {}, Symbol: {})", 
                            order.getId(), order.getOrderType(), order.getTradingSymbol());

                    orderManagementService.exitOrder(
                            order.getId(), 
                            ExitReasonEnum.MARKET_END_CLOSURE, 
                            null, // TO DO
                            null, // TO DO
                            new Date()
                    );
                    
                    log.info("🕒 TRADING HOURS CHECK - Successfully closed order: {}", order.getId());
                    
                } catch (Exception e) {
                    log.error("🕒 TRADING HOURS CHECK - Error closing order: {} - {}", 
                            order.getId(), e.getMessage(), e);
                }
            }
            
            log.info("🕒 TRADING HOURS CHECK - Trade closure process completed");
            
        } catch (Exception e) {
            log.error("🕒 TRADING HOURS CHECK - Error in trading hours check process: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Alternative method that can be called manually or from other services
     * to close all active trades immediately
     */
    public void closeAllActiveTradesImmediately() {
        log.info("🕒 MANUAL MARKET END - Starting immediate trade closure process");
        checkTradingHoursAndCloseTrades();
    }
    
    /**
     * Check if market end scheduler is enabled in configuration
     */
    private boolean isMarketEndSchedulerEnabled() {
        return tradingConfigurationService.isMarketEndSchedulerEnabled();
    }
}
