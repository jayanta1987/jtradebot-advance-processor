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
public class ExitSettingsService implements InitializingBean {

    private final TradeConfigRepository tradeConfigRepository;
    private final ObjectMapper objectMapper;
    private ExitSettings exitSettings;

    @Override
    public void afterPropertiesSet() {
        loadExitSettings();
    }

    /**
     * Load exit settings from JSON configuration
     */
    private void loadExitSettings() {
        try {
            // First try to load from database
            String today = getTodaysDateString("Asia/Kolkata", "'IST-'yyyy-MM-dd");
            TradeConfig tradeConfig = tradeConfigRepository.findByDate(today).orElse(null);
            
            if (tradeConfig != null && tradeConfig.getExitSettings() != null) {
                this.exitSettings = tradeConfig.getExitSettings();
                log.info("✅ Exit settings loaded from database - Milestone: {}, PriceMovement: {}, Time: {}, Strategy: {}, StopLoss: {}", 
                    exitSettings.isMilestoneBasedExitEnabled(), exitSettings.isPriceMovementExitEnabled(),
                    exitSettings.isTimeBasedExitEnabled(), exitSettings.isStrategyBasedExitEnabled(), exitSettings.isStopLossTargetExitEnabled());
            } else {
                // Load default settings from JSON configuration
                loadDefaultExitSettingsFromJson();
            }
        } catch (Exception e) {
            log.error("❌ Error loading exit settings from database, falling back to JSON", e);
            loadDefaultExitSettingsFromJson();
        }
    }

    /**
     * Load default exit settings from JSON configuration file
     */
    private void loadDefaultExitSettingsFromJson() {
        try {
            ClassPathResource resource = new ClassPathResource("rules/exit-settings.json");
            JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
            
            if (!rootNode.has("exitSettings")) {
                throw new RuntimeException("exitSettings section not found in exit-settings.json");
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
            
            log.info("✅ Exit settings loaded from JSON configuration - Milestone: {}, PriceMovement: {}, Time: {}, Strategy: {}, StopLoss: {}", 
                exitSettings.isMilestoneBasedExitEnabled(), exitSettings.isPriceMovementExitEnabled(),
                exitSettings.isTimeBasedExitEnabled(), exitSettings.isStrategyBasedExitEnabled(), exitSettings.isStopLossTargetExitEnabled());
                
        } catch (IOException e) {
            log.error("❌ Failed to read exit-settings.json configuration file", e);
            throw new RuntimeException("Failed to load exit settings from JSON configuration", e);
        } catch (Exception e) {
            log.error("❌ Failed to parse exit-settings.json configuration", e);
            throw new RuntimeException("Failed to parse exit settings from JSON configuration", e);
        }
    }

    /**
     * Get current exit settings
     */
    public ExitSettings getExitSettings() {
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
        loadExitSettings();
    }

    // Convenience methods for checking specific exit types
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
