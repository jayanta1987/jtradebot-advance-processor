package com.jtradebot.processor.controller;

import com.jtradebot.processor.config.TradingConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/trading-config")
@RequiredArgsConstructor
@Slf4j
public class TradingConfigurationController {

    private final TradingConfigurationService tradingConfigService;

    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrentConfiguration() {
        try {
            Map<String, Object> config = new HashMap<>();
            
            // Account balance
            config.put("accountBalance", tradingConfigService.getAccountBalance());
            
            // Quantity settings
            Map<String, Object> quantitySettings = new HashMap<>();
            quantitySettings.put("defaultQuantity", tradingConfigService.getDefaultQuantity());
            quantitySettings.put("minLotSize", tradingConfigService.getMinLotSize());
            quantitySettings.put("maxQuantityPerTrade", tradingConfigService.getMaxQuantityPerTrade());
            quantitySettings.put("quantityIncrement", tradingConfigService.getQuantityIncrement());
            config.put("quantitySettings", quantitySettings);
            
            // Investment limits
            Map<String, Object> investmentLimits = new HashMap<>();
            investmentLimits.put("maxInvestmentPerTrade", tradingConfigService.getMaxInvestmentPerTrade());
            investmentLimits.put("maxInvestmentPercentage", tradingConfigService.getMaxInvestmentPercentage());
            investmentLimits.put("maxDailyInvestment", tradingConfigService.getMaxDailyInvestment());
            investmentLimits.put("maxDailyLoss", tradingConfigService.getMaxDailyLoss());
            investmentLimits.put("maxDailyProfit", tradingConfigService.getMaxDailyProfit());
            config.put("investmentLimits", investmentLimits);
            
            // Risk management
            Map<String, Object> riskManagement = new HashMap<>();
            riskManagement.put("maxRiskPerTradePercentage", tradingConfigService.getMaxRiskPerTradePercentage());
            riskManagement.put("maxRiskPerDayPercentage", tradingConfigService.getMaxRiskPerDayPercentage());
            riskManagement.put("maxProfitPerDayPercentage", tradingConfigService.getMaxProfitPerDayPercentage());
            config.put("riskManagement", riskManagement);
            
            // Trade settings
            Map<String, Object> tradeSettings = new HashMap<>();
            tradeSettings.put("maxTradeHoldingTimeInSec", tradingConfigService.getMaxTradeHoldingTimeInSec());
            config.put("tradeSettings", tradeSettings);
            
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            log.error("Error getting trading configuration", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to get configuration"));
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getConfigurationSummary() {
        try {
            Map<String, Object> summary = new HashMap<>();
            
            summary.put("currentQuantity", tradingConfigService.getDefaultQuantity());
            summary.put("maxQuantityPerTrade", tradingConfigService.getMaxQuantityPerTrade());
            summary.put("accountBalance", tradingConfigService.getAccountBalance());
            summary.put("maxInvestmentPerTrade", tradingConfigService.getMaxInvestmentPerTrade());
            summary.put("maxDailyInvestment", tradingConfigService.getMaxDailyInvestment());
            summary.put("maxDailyLoss", tradingConfigService.getMaxDailyLoss());
            summary.put("maxDailyProfit", tradingConfigService.getMaxDailyProfit());
            
            // Calculate maximum investment per trade
            double maxInvestmentPerTrade = tradingConfigService.getMaxInvestmentPerTrade();
            double maxInvestmentPercentage = tradingConfigService.getMaxInvestmentPercentage();
            double accountBalance = tradingConfigService.getAccountBalance();
            double maxInvestmentByPercentage = accountBalance * (maxInvestmentPercentage / 100.0);
            
            summary.put("maxInvestmentByPercentage", maxInvestmentByPercentage);
            summary.put("effectiveMaxInvestment", Math.min(maxInvestmentPerTrade, maxInvestmentByPercentage));
            
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Error getting configuration summary", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to get summary"));
        }
    }
}
