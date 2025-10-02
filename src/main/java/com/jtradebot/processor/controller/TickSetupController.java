package com.jtradebot.processor.controller;

import com.jtradebot.processor.config.TradingConfigurationService;
import com.jtradebot.processor.config.ExitSettingsService;
import com.jtradebot.processor.model.ExitSettings;
import com.jtradebot.processor.repository.document.TradeConfig;
import com.jtradebot.processor.service.TickSetupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = {"http://localhost:5173", "https://jtradebot.com", "https://www.jtradebot.com"})
@RestController
@RequestMapping("/tick-setup")
@RequiredArgsConstructor
@Slf4j
public class TickSetupController {
    private final TickSetupService tickSetupService;
    private final TradingConfigurationService tradingConfigurationService;
    private final ExitSettingsService exitSettingsService;

    @GetMapping("/tick-dates")
    public List<String> getTickDates() {
        try {
            return tickSetupService.getUniqueDates();
        } catch (Exception e) {
            log.error("Failed to get unique dates: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @GetMapping("/trade-config")
    public Map<String, Object> getCurrentTradeConfig() {
        try {
            TradeConfig tradeConfig = tickSetupService.getTradeConfig();
            ExitSettings exitSettings = exitSettingsService.getExitSettings();
            
            Map<String, Object> response = new HashMap<>();
            
            // Return only the necessary information (exclude sensitive data like access token)
            response.put("date", tradeConfig.getDate());
            response.put("hasAccessToken", tradeConfig.getAccessToken() != null);
            response.put("createdAt", tradeConfig.getCreatedAt());
            response.put("updatedAt", tradeConfig.getUpdatedAt());
            
            if (tradeConfig.getTradePreference() != null) {
                TradeConfig.TradePreference prefs = tradeConfig.getTradePreference();
                Map<String, Object> preferences = new HashMap<>();
                preferences.put("maxInvestment", prefs.getMaxInvestment());
                preferences.put("minQuantity", prefs.getMinQuantity());
                preferences.put("maxQuantity", prefs.getMaxQuantity());
                preferences.put("maxLossPercentagePerDay", prefs.getMaxLossPercentagePerDay());
                preferences.put("maxProfitPercentagePerDay", prefs.getMaxProfitPercentagePerDay());
                preferences.put("maxTradeHoldingTimeInSec", prefs.getMaxTradeHoldingTimeInSec());
                
                response.put("tradePreference", preferences);
            } else {
                response.put("tradePreference", null);
            }
            
            // Add exit settings to response
            if (exitSettings != null) {
                Map<String, Object> exitSettingsMap = new HashMap<>();
                exitSettingsMap.put("milestoneBasedExitEnabled", exitSettings.isMilestoneBasedExitEnabled());
                exitSettingsMap.put("priceMovementExitEnabled", exitSettings.isPriceMovementExitEnabled());
                exitSettingsMap.put("timeBasedExitEnabled", exitSettings.isTimeBasedExitEnabled());
                exitSettingsMap.put("strategyBasedExitEnabled", exitSettings.isStrategyBasedExitEnabled());
                exitSettingsMap.put("stopLossTargetExitEnabled", exitSettings.isStopLossTargetExitEnabled());
                exitSettingsMap.put("milestoneBasedExitDescription", exitSettings.getMilestoneBasedExitDescription());
                exitSettingsMap.put("priceMovementExitDescription", exitSettings.getPriceMovementExitDescription());
                exitSettingsMap.put("lastModifiedBy", exitSettings.getLastModifiedBy());
                exitSettingsMap.put("lastModifiedAt", exitSettings.getLastModifiedAt());
                
                response.put("exitSettings", exitSettingsMap);
            } else {
                response.put("exitSettings", null);
            }
            
            log.info("TradeConfig API called - returning config for date: {} with exit settings", tradeConfig.getDate());
            return response;
            
        } catch (Exception e) {
            log.error("Failed to get current trade config: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve trade configuration");
            errorResponse.put("message", e.getMessage());
            return errorResponse;
        }
    }

    @PostMapping("/refresh-config")
    public Map<String, Object> refreshConfiguration() {
        try {
            log.info("🔄 Configuration refresh requested via API");
            
            // Step 1: Invalidate TradeConfig cache
            tickSetupService.invalidateTradeConfigCache();
            log.info("✅ TradeConfig cache invalidated");
            
            // Step 2: Refresh ScalpingEntryConfig from MongoDB
            tradingConfigurationService.refreshConfigurationFromMongoDB();
            log.info("✅ ScalpingEntryConfig refreshed from MongoDB");
            
            // Step 3: Refresh ExitSettings from database
            exitSettingsService.refreshExitSettings();
            log.info("✅ ExitSettings refreshed from database");
            
            // Step 4: Get the fresh configuration to return
            TradeConfig tradeConfig = tickSetupService.getTradeConfig();
            ExitSettings exitSettings = exitSettingsService.getExitSettings();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Configuration refreshed successfully");
            response.put("timestamp", java.time.LocalDateTime.now().toString());
            
            // Include the refreshed trade configuration
            Map<String, Object> configData = new HashMap<>();
            configData.put("date", tradeConfig.getDate());
            configData.put("hasAccessToken", tradeConfig.getAccessToken() != null);
            configData.put("createdAt", tradeConfig.getCreatedAt());
            configData.put("updatedAt", tradeConfig.getUpdatedAt());
            
            if (tradeConfig.getTradePreference() != null) {
                TradeConfig.TradePreference prefs = tradeConfig.getTradePreference();
                Map<String, Object> preferences = new HashMap<>();
                preferences.put("maxInvestment", prefs.getMaxInvestment());
                preferences.put("minQuantity", prefs.getMinQuantity());
                preferences.put("maxQuantity", prefs.getMaxQuantity());
                preferences.put("maxLossPercentagePerDay", prefs.getMaxLossPercentagePerDay());
                preferences.put("maxProfitPercentagePerDay", prefs.getMaxProfitPercentagePerDay());
                preferences.put("maxTradeHoldingTimeInSec", prefs.getMaxTradeHoldingTimeInSec());
                
                configData.put("tradePreference", preferences);
            } else {
                configData.put("tradePreference", null);
            }
            
            // Include the refreshed exit settings
            if (exitSettings != null) {
                Map<String, Object> exitSettingsMap = new HashMap<>();
                exitSettingsMap.put("milestoneBasedExitEnabled", exitSettings.isMilestoneBasedExitEnabled());
                exitSettingsMap.put("priceMovementExitEnabled", exitSettings.isPriceMovementExitEnabled());
                exitSettingsMap.put("timeBasedExitEnabled", exitSettings.isTimeBasedExitEnabled());
                exitSettingsMap.put("strategyBasedExitEnabled", exitSettings.isStrategyBasedExitEnabled());
                exitSettingsMap.put("stopLossTargetExitEnabled", exitSettings.isStopLossTargetExitEnabled());
                exitSettingsMap.put("milestoneBasedExitDescription", exitSettings.getMilestoneBasedExitDescription());
                exitSettingsMap.put("priceMovementExitDescription", exitSettings.getPriceMovementExitDescription());
                exitSettingsMap.put("lastModifiedBy", exitSettings.getLastModifiedBy());
                exitSettingsMap.put("lastModifiedAt", exitSettings.getLastModifiedAt());
                
                configData.put("exitSettings", exitSettingsMap);
            } else {
                configData.put("exitSettings", null);
            }
            
            response.put("refreshedConfig", configData);
            
            log.info("🎉 Configuration refresh completed successfully");
            return response;
            
        } catch (Exception e) {
            log.error("❌ Failed to refresh configuration: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to refresh configuration");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", java.time.LocalDateTime.now().toString());
            return errorResponse;
        }
    }

}
