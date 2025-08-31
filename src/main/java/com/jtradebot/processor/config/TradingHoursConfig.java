package com.jtradebot.processor.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtradebot.processor.handler.DateTimeHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

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

    /**
     * Get market start hour from configuration
     */
    public int getMarketStartHour() {
        try {
            ClassPathResource resource = getConfigResource();
            InputStream inputStream = resource.getInputStream();
            JsonNode rootNode = objectMapper.readTree(inputStream);
            
            JsonNode tradingHours = rootNode.get("noTradeZones").get("filters").get("tradingHours");
            if (tradingHours != null && tradingHours.has("startHour")) {
                int startHour = tradingHours.get("startHour").asInt();
                log.debug("✅ TRADING HOURS CONFIG - Market start hour loaded: {}", startHour);
                return startHour;
            }
            log.warn("⚠️ TRADING HOURS CONFIG - No start hour found in config, using default: {}", DEFAULT_START_HOUR);
            return DEFAULT_START_HOUR;
            
        } catch (IOException e) {
            log.error("❌ TRADING HOURS CONFIG - Error loading start hour: {}", e.getMessage(), e);
            return DEFAULT_START_HOUR;
        }
    }

    /**
     * Get market start minute from configuration
     */
    public int getMarketStartMinute() {
        try {
            ClassPathResource resource = getConfigResource();
            InputStream inputStream = resource.getInputStream();
            JsonNode rootNode = objectMapper.readTree(inputStream);
            
            JsonNode tradingHours = rootNode.get("noTradeZones").get("filters").get("tradingHours");
            if (tradingHours != null && tradingHours.has("startMinute")) {
                int startMinute = tradingHours.get("startMinute").asInt();
                log.debug("✅ TRADING HOURS CONFIG - Market start minute loaded: {}", startMinute);
                return startMinute;
            }
            log.warn("⚠️ TRADING HOURS CONFIG - No start minute found in config, using default: {}", DEFAULT_START_MINUTE);
            return DEFAULT_START_MINUTE;
            
        } catch (IOException e) {
            log.error("❌ TRADING HOURS CONFIG - Error loading start minute: {}", e.getMessage(), e);
            return DEFAULT_START_MINUTE;
        }
    }

    /**
     * Get market end hour from configuration
     */
    public int getMarketEndHour() {
        try {
            ClassPathResource resource = getConfigResource();
            InputStream inputStream = resource.getInputStream();
            JsonNode rootNode = objectMapper.readTree(inputStream);
            
            JsonNode tradingHours = rootNode.get("noTradeZones").get("filters").get("tradingHours");
            if (tradingHours != null && tradingHours.has("endHour")) {
                int endHour = tradingHours.get("endHour").asInt();
                log.debug("✅ TRADING HOURS CONFIG - Market end hour loaded: {}", endHour);
                return endHour;
            }
            log.warn("⚠️ TRADING HOURS CONFIG - No end hour found in config, using default: {}", DEFAULT_END_HOUR);
            return DEFAULT_END_HOUR;
            
        } catch (IOException e) {
            log.error("❌ TRADING HOURS CONFIG - Error loading end hour: {}", e.getMessage(), e);
            return DEFAULT_END_HOUR;
        }
    }

    /**
     * Get market end minute from configuration
     */
    public int getMarketEndMinute() {
        try {
            ClassPathResource resource = getConfigResource();
            InputStream inputStream = resource.getInputStream();
            JsonNode rootNode = objectMapper.readTree(inputStream);
            
            JsonNode tradingHours = rootNode.get("noTradeZones").get("filters").get("tradingHours");
            if (tradingHours != null && tradingHours.has("endMinute")) {
                int endMinute = tradingHours.get("endMinute").asInt();
                log.debug("✅ TRADING HOURS CONFIG - Market end minute loaded: {}", endMinute);
                return endMinute;
            }
            log.warn("⚠️ TRADING HOURS CONFIG - No end minute found in config, using default: {}", DEFAULT_END_MINUTE);
            return DEFAULT_END_MINUTE;
            
        } catch (IOException e) {
            log.error("❌ TRADING HOURS CONFIG - Error loading end minute: {}", e.getMessage(), e);
            return DEFAULT_END_MINUTE;
        }
    }

    private ClassPathResource getConfigResource() {
        return new ClassPathResource("rules/scalping-entry-config.json");
    }

    public Date getLastMarketTime(Date date) {
        return DateTimeHandler.getLastMarketTime(date, DEFAULT_START_HOUR, DEFAULT_START_MINUTE,
                DEFAULT_END_HOUR, DEFAULT_END_MINUTE);
    }
}
