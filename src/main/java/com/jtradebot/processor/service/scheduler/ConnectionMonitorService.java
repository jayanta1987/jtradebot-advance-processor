package com.jtradebot.processor.service.scheduler;

import com.jtradebot.processor.common.ProfileUtil;
import com.jtradebot.processor.config.TradingHoursConfig;
import com.jtradebot.processor.connector.KiteSetupHandler;
import com.jtradebot.processor.connector.KiteTickerHandler;
import com.jtradebot.processor.handler.DateTimeHandler;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.service.TickSetupService;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectionMonitorService {

    private final KiteTickerHandler kiteTickerHandler;
    private final KiteSetupHandler kiteSetupHandler;
    private final TradingHoursConfig tradingHoursConfig;
    private final TickSetupService tickSetupService;
    private final Environment environment;
    private final TickDataManager tickDataManager;

    @Scheduled(cron = "*/30 * * * * *") // Run every 30 seconds
    public void checkConnection() {
        // Skip if not live profile or access tokens are not present
        if (!ProfileUtil.isProfileActive(environment, "live")) {
            log.warn("Skipping connection check as live profile is not active");
            return;
        }

        if (!tickSetupService.isAccessTokensPresent()) {
            log.warn("access tokens are not present.");
            return;
        }

        // Get trading hours from configuration
        int startHour = tradingHoursConfig.getMarketStartHour();
        int startMinute = tradingHoursConfig.getMarketStartMinute();
        int endHour = tradingHoursConfig.getMarketEndHour();
        int endMinute = tradingHoursConfig.getMarketEndMinute();


        if (tickDataManager.getLastTickTime() != null
                && !DateTimeHandler.isMarketOpen(tickDataManager.getLastTickTime(), startHour, startMinute, endHour, endMinute)) {
            log.warn("Market is not open. Skipping connection check.");
            return;
        }

        // Check if connection is open and recent ticks are being received
        if (ProfileUtil.isProfileActive(environment, "live")
                && kiteTickerHandler.isConnectionOpen() && tickSetupService.isAccessTokensPresent()) {
            log.info("Connection is open. Checking for recent tick data...");
            long timeSinceLastTick = System.currentTimeMillis() - kiteTickerHandler.getLastTickReceivedTime();
            if (timeSinceLastTick > 30000) { // 30 seconds without ticks
                log.warn("No ticks received in the last 30 seconds. Re-subscribing tokens.");
                kiteTickerHandler.reSubscribeTokens(); // Attempt to re-subscribe
            } else {
                log.info("Ticks are being received normally.");
            }
        } else {
            log.warn("Connection lost. Reinitializing...");
            try {
                kiteSetupHandler.init();
            } catch (KiteException | IOException e) {
                log.error("Failed to reinitialize connection: {}", e.getMessage());
            }
        }
    }

}