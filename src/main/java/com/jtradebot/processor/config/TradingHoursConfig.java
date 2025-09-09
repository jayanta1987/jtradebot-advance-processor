package com.jtradebot.processor.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtradebot.processor.handler.DateTimeHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;


@Configuration
@Slf4j
public class TradingHoursConfig {

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final int DEFAULT_START_HOUR = 9;
    private static final int DEFAULT_START_MINUTE = 15;
    private static final int DEFAULT_END_HOUR = 15;
    private static final int DEFAULT_END_MINUTE = 30;
    
    // Cached configuration values
    private int marketStartHour = DEFAULT_START_HOUR;
    private int marketStartMinute = DEFAULT_START_MINUTE;
    private int marketEndHour = DEFAULT_END_HOUR;
    private int marketEndMinute = DEFAULT_END_MINUTE;

    @PostConstruct
    public void loadTradingHoursConfiguration() {
        try {
            ClassPathResource resource = getConfigResource();
            InputStream inputStream = resource.getInputStream();
            JsonNode rootNode = objectMapper.readTree(inputStream);
            
            JsonNode tradingHours = rootNode.get("noTradeZones").get("filters").get("tradingHours");
            if (tradingHours != null) {
                // Load start hour
                if (tradingHours.has("startHour")) {
                    marketStartHour = tradingHours.get("startHour").asInt();
                    log.info("✅ TRADING HOURS CONFIG - Market start hour loaded: {}", marketStartHour);
                } else {
                    log.warn("⚠️ TRADING HOURS CONFIG - No start hour found in config, using default: {}", DEFAULT_START_HOUR);
                }
                
                // Load start minute
                if (tradingHours.has("startMinute")) {
                    marketStartMinute = tradingHours.get("startMinute").asInt();
                    log.info("✅ TRADING HOURS CONFIG - Market start minute loaded: {}", marketStartMinute);
                } else {
                    log.warn("⚠️ TRADING HOURS CONFIG - No start minute found in config, using default: {}", DEFAULT_START_MINUTE);
                }
                
                // Load end hour
                if (tradingHours.has("endHour")) {
                    marketEndHour = tradingHours.get("endHour").asInt();
                    log.info("✅ TRADING HOURS CONFIG - Market end hour loaded: {}", marketEndHour);
                } else {
                    log.warn("⚠️ TRADING HOURS CONFIG - No end hour found in config, using default: {}", DEFAULT_END_HOUR);
                }
                
                // Load end minute
                if (tradingHours.has("endMinute")) {
                    marketEndMinute = tradingHours.get("endMinute").asInt();
                    log.info("✅ TRADING HOURS CONFIG - Market end minute loaded: {}", marketEndMinute);
                } else {
                    log.warn("⚠️ TRADING HOURS CONFIG - No end minute found in config, using default: {}", DEFAULT_END_MINUTE);
                }
            } else {
                log.warn("⚠️ TRADING HOURS CONFIG - No tradingHours section found in config, using defaults");
            }
            
            log.info("✅ TRADING HOURS CONFIG - Configuration loaded successfully: {}:{} - {}:{}", 
                    marketStartHour, marketStartMinute, marketEndHour, marketEndMinute);
            
        } catch (IOException e) {
            log.error("❌ TRADING HOURS CONFIG - Error loading configuration: {}", e.getMessage(), e);
            log.warn("⚠️ TRADING HOURS CONFIG - Using default values due to configuration loading error");
        }
    }

    /**
     * Get market start hour from cached configuration
     */
    public int getMarketStartHour() {
        return marketStartHour;
    }

    /**
     * Get market start minute from cached configuration
     */
    public int getMarketStartMinute() {
        return marketStartMinute;
    }

    /**
     * Get market end hour from cached configuration
     */
    public int getMarketEndHour() {
        return marketEndHour;
    }

    /**
     * Get market end minute from cached configuration
     */
    public int getMarketEndMinute() {
        return marketEndMinute;
    }

    private ClassPathResource getConfigResource() {
        return new ClassPathResource("rules/scalping-entry-config.json");
    }

    public Date getLastMarketTime(Date date) {
        return DateTimeHandler.getLastMarketTime(date, marketStartHour, marketStartMinute,
                marketEndHour, marketEndMinute);
    }
}
