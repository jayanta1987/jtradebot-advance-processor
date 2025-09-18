package com.jtradebot.processor.controller;

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
            Map<String, Object> response = new HashMap<>();
            
            // Return only the necessary information (exclude sensitive data like access token)
            response.put("date", tradeConfig.getDate());
            response.put("hasAccessToken", tradeConfig.getAccessToken() != null);
            response.put("createdAt", tradeConfig.getCreatedAt());
            response.put("updatedAt", tradeConfig.getUpdatedAt());
            
            if (tradeConfig.getTradePreference() != null) {
                TradeConfig.TradePreference prefs = tradeConfig.getTradePreference();
                Map<String, Object> preferences = new HashMap<>();
                preferences.put("maxInvestmentPercentage", prefs.getMaxInvestmentPercentage());
                preferences.put("minQuantity", prefs.getMinQuantity());
                preferences.put("maxQuantity", prefs.getMaxQuantity());
                preferences.put("maxLossPercentagePerDay", prefs.getMaxLossPercentagePerDay());
                preferences.put("maxProfitPercentagePerDay", prefs.getMaxProfitPercentagePerDay());
                preferences.put("maxTradeHoldingTimeInSec", prefs.getMaxTradeHoldingTimeInSec());
                
                response.put("tradePreference", preferences);
            } else {
                response.put("tradePreference", null);
            }
            
            log.info("TradeConfig API called - returning config for date: {}", tradeConfig.getDate());
            return response;
            
        } catch (Exception e) {
            log.error("Failed to get current trade config: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve trade configuration");
            errorResponse.put("message", e.getMessage());
            return errorResponse;
        }
    }

}
