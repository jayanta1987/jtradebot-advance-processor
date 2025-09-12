package com.jtradebot.processor.config;

import com.jtradebot.processor.handler.DateTimeHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.Date;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class TradingHoursConfig {

    private final TradingConfigurationService tradingConfigurationService;

    public void loadTradingHoursConfiguration() {
        log.info("✅ TRADING HOURS CONFIG - Configuration loaded successfully using TradingConfigurationService");
    }

    /**
     * Get market start hour from TradingConfigurationService
     */
    public int getMarketStartHour() {
        return tradingConfigurationService.getMarketStartHour();
    }

    /**
     * Get market start minute from TradingConfigurationService
     */
    public int getMarketStartMinute() {
        return tradingConfigurationService.getMarketStartMinute();
    }

    /**
     * Get market end hour from TradingConfigurationService
     */
    public int getMarketEndHour() {
        return tradingConfigurationService.getMarketEndHour();
    }

    /**
     * Get market end minute from TradingConfigurationService
     */
    public int getMarketEndMinute() {
        return tradingConfigurationService.getMarketEndMinute();
    }

    public Date getLastMarketTime(Date date) {
        return DateTimeHandler.getLastMarketTime(date, getMarketStartHour(), getMarketStartMinute(),
                getMarketEndHour(), getMarketEndMinute());
    }
}
