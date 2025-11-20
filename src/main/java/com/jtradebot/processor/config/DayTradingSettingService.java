package com.jtradebot.processor.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtradebot.processor.model.ExitSettings;
import com.jtradebot.processor.repository.TradeConfigRepository;
import com.jtradebot.processor.repository.document.TradeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;

import static com.jtradebot.processor.handler.DateTimeHandler.getTodaysDateString;

@Service
@RequiredArgsConstructor
@Slf4j
public class DayTradingSettingService implements InitializingBean {

    private final TradeConfigRepository tradeConfigRepository;
    private final ObjectMapper objectMapper;
    private ExitSettings exitSettings;
    private TradeConfig.TradePreference defaultTradePreference;

    @Override
    public void afterPropertiesSet() {
        loadTradingConfiguration();
    }

    /**
     * Load trading configuration (both exit settings and trade preferences) from JSON at startup
     */
    private void loadTradingConfiguration() {
        log.info("🚀 Loading trading configuration from JSON at startup...");
        loadDefaultConfigurationFromJson();
        log.info("✅ Trading configuration loaded successfully at startup");
    }

    /**
     * Load default configuration from JSON configuration file
     */
    private void loadDefaultConfigurationFromJson() {
        try {
            ClassPathResource resource = new ClassPathResource("rules/trading-day-settings.json");
            JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
            
            // Load exit settings
            loadExitSettingsFromJson(rootNode);
            
            // Load trade preferences
            loadTradePreferencesFromJson(rootNode);
            
            log.info("✅ Trading configuration loaded from JSON - Exit settings and trade preferences initialized");
                
        } catch (IOException e) {
            log.error("❌ Failed to read trading-day-settings.json configuration file", e);
            throw new RuntimeException("Failed to load trading configuration from JSON file", e);
        } catch (Exception e) {
            log.error("❌ Failed to parse trading-day-settings.json configuration", e);
            throw new RuntimeException("Failed to parse trading configuration from JSON", e);
        }
    }

    /**
     * Load exit settings from JSON
     */
    private void loadExitSettingsFromJson(JsonNode rootNode) {
            if (!rootNode.has("exitSettings")) {
                throw new RuntimeException("exitSettings section not found in trading-day-settings.json");
            }
        
        JsonNode exitSettingsNode = rootNode.get("exitSettings");
        
        // Create ExitSettings object from JSON
        this.exitSettings = new ExitSettings();
        this.exitSettings.setMilestoneBasedExitEnabled(exitSettingsNode.get("milestoneBasedExit").get("enabled").asBoolean());
        this.exitSettings.setPriceMovementExitEnabled(exitSettingsNode.get("priceMovementExit").get("enabled").asBoolean());
        this.exitSettings.setTimeBasedExitEnabled(exitSettingsNode.get("timeBasedExit").get("enabled").asBoolean());
        this.exitSettings.setStrategyBasedExitEnabled(exitSettingsNode.get("strategyBasedExit").get("enabled").asBoolean());
        this.exitSettings.setStopLossTargetExitEnabled(exitSettingsNode.get("stopLossTargetExit").get("enabled").asBoolean());
        
        // Set descriptions from JSON
        this.exitSettings.setMilestoneBasedExitDescription(exitSettingsNode.get("milestoneBasedExit").get("description").asText());
        this.exitSettings.setPriceMovementExitDescription(exitSettingsNode.get("priceMovementExit").get("description").asText());
        
        this.exitSettings.setLastModifiedBy("JSON_CONFIG");
        this.exitSettings.updateLastModified();
        
        log.info("✅ Exit settings loaded from JSON - Milestone: {}, PriceMovement: {}, Time: {}, Strategy: {}, StopLoss: {}", 
            exitSettings.isMilestoneBasedExitEnabled(), exitSettings.isPriceMovementExitEnabled(),
            exitSettings.isTimeBasedExitEnabled(), exitSettings.isStrategyBasedExitEnabled(), exitSettings.isStopLossTargetExitEnabled());
    }

    /**
     * Load trade preferences from JSON
     */
    private void loadTradePreferencesFromJson(JsonNode rootNode) {
        if (!rootNode.has("tradePreferences")) {
            throw new RuntimeException("tradePreferences section not found in trading-day-settings.json");
        }
        
        JsonNode tradePrefsNode = rootNode.get("tradePreferences");
        
        this.defaultTradePreference = new TradeConfig.TradePreference();
        this.defaultTradePreference.setMaxInvestment(tradePrefsNode.get("maxInvestment").asDouble());
        this.defaultTradePreference.setMinQuantity(tradePrefsNode.get("minQuantity").asInt());
        this.defaultTradePreference.setMaxQuantity(tradePrefsNode.get("maxQuantity").asInt());
        this.defaultTradePreference.setMaxLossPerDay(tradePrefsNode.get("maxLossPerDay").asDouble());
        this.defaultTradePreference.setMaxProfitPerDay(tradePrefsNode.get("maxProfitPerDay").asDouble());
        this.defaultTradePreference.setMaxTradeHoldingTimeInSec(tradePrefsNode.get("maxTradeHoldingTimeInSec").asLong());
        this.defaultTradePreference.setEnableTradeAfterStopLossHit(tradePrefsNode.has("enableTradeAfterStopLossHit") ? tradePrefsNode.get("enableTradeAfterStopLossHit").asBoolean() : false);
        this.defaultTradePreference.setStopLossBlockTimeframe(tradePrefsNode.has("stopLossBlockTimeframe") ? tradePrefsNode.get("stopLossBlockTimeframe").asText() : "ONE_MIN");
        
        log.info("✅ Trade preferences loaded from JSON - MaxInvestment: {}, MaxLoss: {}, MaxProfit: {}", 
                defaultTradePreference.getMaxInvestment(), defaultTradePreference.getMaxLossPerDay(), defaultTradePreference.getMaxProfitPerDay());
    }

    // ========== EXIT SETTINGS METHODS ==========

    /**
     * Get current exit settings
     */
    public ExitSettings getExitSettings() {
        if (exitSettings == null) {
            throw new RuntimeException("Exit settings not loaded from JSON configuration. Service not properly initialized.");
        }
        return exitSettings;
    }

    /**
     * Update exit settings
     */
    public void updateExitSettings(ExitSettings newExitSettings) {
        try {
            // Update the cached settings
            this.exitSettings = newExitSettings;
            this.exitSettings.updateLastModified();
            
            // Update in database
            String today = getTodaysDateString("Asia/Kolkata", "'IST-'yyyy-MM-dd");
            TradeConfig tradeConfig = tradeConfigRepository.findByDate(today).orElse(null);
            
            if (tradeConfig != null) {
                tradeConfig.setExitSettings(newExitSettings);
                tradeConfigRepository.save(tradeConfig);
                
                log.info("✅ Exit settings updated - Milestone: {}, PriceMovement: {}, Time: {}, Strategy: {}, StopLoss: {}", 
                    newExitSettings.isMilestoneBasedExitEnabled(), newExitSettings.isPriceMovementExitEnabled(),
                    newExitSettings.isTimeBasedExitEnabled(), newExitSettings.isStrategyBasedExitEnabled(), newExitSettings.isStopLossTargetExitEnabled());
            } else {
                log.warn("⚠️ No trade config found for today ({}) to update exit settings", today);
            }
        } catch (Exception e) {
            log.error("❌ Error updating exit settings", e);
        }
    }

    /**
     * Refresh exit settings from database
     */
    public void refreshExitSettings() {
        log.info("🔄 Refreshing exit settings from database");
        try {
            // Get current date and fetch exit settings from database
            String today = getTodaysDateString("Asia/Kolkata", "'IST-'yyyy-MM-dd");
            TradeConfig tradeConfig = tradeConfigRepository.findByDate(today).orElse(null);
            
            if (tradeConfig != null && tradeConfig.getExitSettings() != null) {
                // Update cached exit settings with database version
                this.exitSettings = tradeConfig.getExitSettings();
                log.info("✅ Exit settings refreshed from database - Milestone: {}, PriceMovement: {}, Time: {}, Strategy: {}, StopLoss: {}", 
                    exitSettings.isMilestoneBasedExitEnabled(), exitSettings.isPriceMovementExitEnabled(),
                    exitSettings.isTimeBasedExitEnabled(), exitSettings.isStrategyBasedExitEnabled(), exitSettings.isStopLossTargetExitEnabled());
            } else {
                // Fallback to JSON configuration if no database settings found
                log.warn("⚠️ No exit settings found in database for date: {}, falling back to JSON configuration", today);
                loadTradingConfiguration();
            }
        } catch (Exception e) {
            log.error("❌ Error refreshing exit settings from database, falling back to JSON configuration: {}", e.getMessage(), e);
            // Fallback to JSON configuration in case of any error
            loadTradingConfiguration();
        }
    }

    // ========== TRADE PREFERENCES METHODS ==========

    /**
     * Get default trade preferences from JSON configuration
     */
    public TradeConfig.TradePreference getDefaultTradePreference() {
        if (defaultTradePreference == null) {
            throw new RuntimeException("Trade preferences not loaded from JSON configuration. Service not properly initialized.");
        }
        return defaultTradePreference;
    }

    /**
     * Refresh trading configuration from database
     */
    public void refreshTradingConfiguration() {
        log.info("🔄 Refreshing trading configuration from database");
        loadTradingConfiguration();
    }

    // ========== CONVENIENCE METHODS FOR EXIT SETTINGS ==========

    public boolean isMilestoneBasedExitEnabled() {
        return exitSettings != null && exitSettings.isMilestoneBasedExitEnabled();
    }

    public boolean isPriceMovementExitEnabled() {
        return exitSettings != null && exitSettings.isPriceMovementExitEnabled();
    }

    public boolean isTimeBasedExitEnabled() {
        return exitSettings != null && exitSettings.isTimeBasedExitEnabled();
    }

    public boolean isStrategyBasedExitEnabled() {
        return exitSettings != null && exitSettings.isStrategyBasedExitEnabled();
    }

    public boolean isStopLossTargetExitEnabled() {
        return exitSettings != null && exitSettings.isStopLossTargetExitEnabled();
    }
}
