package com.jtradebot.processor.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.InitializingBean;
import java.io.IOException;

@Service
@Slf4j
public class TradingConfigurationService implements InitializingBean {

    private final ObjectMapper objectMapper;
    private TradingConfig tradingConfig;

    public TradingConfigurationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterPropertiesSet() {
        try {
            ClassPathResource resource = new ClassPathResource("rules/scalping-entry-config.json");
            JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
            
            if (rootNode.has("tradingConfiguration")) {
                JsonNode tradingNode = rootNode.get("tradingConfiguration");
                tradingConfig = objectMapper.treeToValue(tradingNode, TradingConfig.class);
                log.info("Trading configuration loaded successfully: {}", tradingConfig);
            } else {
                log.warn("No tradingConfiguration found in JSON, using default values");
                tradingConfig = getDefaultConfig();
            }
        } catch (IOException e) {
            log.error("Failed to load trading configuration from JSON, using default values", e);
            tradingConfig = getDefaultConfig();
        }
    }

    private TradingConfig getDefaultConfig() {
        TradingConfig config = new TradingConfig();
        config.setAccountBalance(100000);
        
        QuantitySettings quantitySettings = new QuantitySettings();
        quantitySettings.setDefaultQuantity(75);
        quantitySettings.setMinLotSize(75);
        quantitySettings.setMaxQuantityPerTrade(150);
        quantitySettings.setQuantityIncrement(75);
        config.setQuantitySettings(quantitySettings);
        
        InvestmentLimits investmentLimits = new InvestmentLimits();
        investmentLimits.setMaxInvestmentPerTrade(50000);
        investmentLimits.setMaxInvestmentPercentage(50);
        investmentLimits.setMaxDailyInvestment(200000);
        investmentLimits.setMaxDailyLoss(12000);
        investmentLimits.setMaxDailyProfit(30000);
        config.setInvestmentLimits(investmentLimits);
        
        RiskManagement riskManagement = new RiskManagement();
        riskManagement.setMaxRiskPerTradePercentage(1.0);
        riskManagement.setMaxRiskPerDayPercentage(12.0);
        riskManagement.setMaxProfitPerDayPercentage(30.0);
        config.setRiskManagement(riskManagement);
        
        TradeSettings tradeSettings = new TradeSettings();
        tradeSettings.setMaxTradeHoldingTimeInSec(150);
        config.setTradeSettings(tradeSettings);
        
        return config;
    }

    public TradingConfig getTradingConfig() {
        return tradingConfig;
    }

    public double getAccountBalance() {
        return tradingConfig.getAccountBalance();
    }

    public int getDefaultQuantity() {
        return tradingConfig.getQuantitySettings().getDefaultQuantity();
    }

    public int getMinLotSize() {
        return tradingConfig.getQuantitySettings().getMinLotSize();
    }

    public int getMaxQuantityPerTrade() {
        return tradingConfig.getQuantitySettings().getMaxQuantityPerTrade();
    }

    public int getQuantityIncrement() {
        return tradingConfig.getQuantitySettings().getQuantityIncrement();
    }

    public double getMaxInvestmentPerTrade() {
        return tradingConfig.getInvestmentLimits().getMaxInvestmentPerTrade();
    }

    public double getMaxInvestmentPercentage() {
        return tradingConfig.getInvestmentLimits().getMaxInvestmentPercentage();
    }

    public double getMaxDailyInvestment() {
        return tradingConfig.getInvestmentLimits().getMaxDailyInvestment();
    }

    public double getMaxDailyLoss() {
        return tradingConfig.getInvestmentLimits().getMaxDailyLoss();
    }

    public double getMaxDailyProfit() {
        return tradingConfig.getInvestmentLimits().getMaxDailyProfit();
    }

    public double getMaxRiskPerTradePercentage() {
        return tradingConfig.getRiskManagement().getMaxRiskPerTradePercentage();
    }

    public double getMaxRiskPerDayPercentage() {
        return tradingConfig.getRiskManagement().getMaxRiskPerDayPercentage();
    }

    public double getMaxProfitPerDayPercentage() {
        return tradingConfig.getRiskManagement().getMaxProfitPerDayPercentage();
    }

    public long getMaxTradeHoldingTimeInSec() {
        return tradingConfig.getTradeSettings().getMaxTradeHoldingTimeInSec();
    }

    @Data
    public static class TradingConfig {
        private double accountBalance;
        private QuantitySettings quantitySettings;
        private InvestmentLimits investmentLimits;
        private RiskManagement riskManagement;
        private TradeSettings tradeSettings;
    }

    @Data
    public static class QuantitySettings {
        private int defaultQuantity;
        private int minLotSize;
        private int maxQuantityPerTrade;
        private int quantityIncrement;
        private String description;
    }

    @Data
    public static class InvestmentLimits {
        private double maxInvestmentPerTrade;
        private double maxInvestmentPercentage;
        private double maxDailyInvestment;
        private double maxDailyLoss;
        private double maxDailyProfit;
        private String description;
    }

    @Data
    public static class RiskManagement {
        private double maxRiskPerTradePercentage;
        private double maxRiskPerDayPercentage;
        private double maxProfitPerDayPercentage;
        private String description;
    }

    @Data
    public static class TradeSettings {
        private long maxTradeHoldingTimeInSec;
        private String description;
    }
}
