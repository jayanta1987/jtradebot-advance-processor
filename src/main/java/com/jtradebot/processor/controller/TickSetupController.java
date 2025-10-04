package com.jtradebot.processor.controller;

import com.jtradebot.processor.config.TradingConfigurationService;
import com.jtradebot.processor.config.DayTradingSettingService;
import com.jtradebot.processor.model.ExitSettings;
import com.jtradebot.processor.repository.document.TradeConfig;
import com.jtradebot.processor.service.TickSetupService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @Data
    public static class TradeConfigUpdateRequest {
        private String field;
        private Object value;
    }

    @Data
    public static class ApiResponse {
        private boolean success;
        private String message;
        
        public ApiResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
    private final TickSetupService tickSetupService;
    private final TradingConfigurationService tradingConfigurationService;
    private final DayTradingSettingService dayTradingSettingService;

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
            ExitSettings exitSettings = dayTradingSettingService.getExitSettings();
            
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
                preferences.put("maxLossPerDay", prefs.getMaxLossPerDay());
                preferences.put("maxProfitPerDay", prefs.getMaxProfitPerDay());
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
            dayTradingSettingService.refreshExitSettings();
            log.info("✅ ExitSettings refreshed from database");
            
            // Step 4: Get the fresh configuration to return
            TradeConfig tradeConfig = tickSetupService.getTradeConfig();
            ExitSettings exitSettings = dayTradingSettingService.getExitSettings();
            
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
                preferences.put("maxLossPerDay", prefs.getMaxLossPerDay());
                preferences.put("maxProfitPerDay", prefs.getMaxProfitPerDay());
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

    @PatchMapping("/trade-config")
    public ResponseEntity<ApiResponse> updateTradeConfig(@RequestBody TradeConfigUpdateRequest request) {
        try {
            log.info("PATCH /trade-config called with field: {} and value: {}", request.getField(), request.getValue());
            
            // Validate request
            if (request.getField() == null || request.getField().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Field cannot be null or empty"));
            }
            
            if (request.getValue() == null) {
                return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Value cannot be null"));
            }
            
            // Update the trade configuration
            boolean updateSuccess = tickSetupService.updateTradePreference(request.getField(), request.getValue());
            
            if (!updateSuccess) {
                return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Invalid field or value. Please check the field name and value format."));
            }
            
            // Refresh configuration after successful update
            log.info("🔄 Refreshing configuration after successful update");
            tickSetupService.invalidateTradeConfigCache();
            tradingConfigurationService.refreshConfigurationFromMongoDB();
            dayTradingSettingService.refreshExitSettings();
            log.info("✅ Configuration refreshed successfully after update");
            
            return ResponseEntity.ok(new ApiResponse(true, "Configuration updated successfully"));
            
        } catch (IllegalArgumentException e) {
            log.error("Validation error in PATCH /trade-config: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(new ApiResponse(false, "Validation error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to update trade config: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse(false, "Internal server error: " + e.getMessage()));
        }
    }

}
