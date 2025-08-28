package com.jtradebot.processor.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.InitializingBean;
import java.io.IOException;

@Service
@Slf4j
public class TradingConfigurationService implements InitializingBean {

    private final ObjectMapper objectMapper;
    @Getter
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
                throw new IllegalStateException("tradingConfiguration section not found in JSON configuration");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load trading configuration from JSON", e);
        }
    }

    public int getMinLotSize() {
        return tradingConfig.getQuantitySettings().getMinLotSize();
    }

    public int getMaxQuantityPerTrade() {
        return tradingConfig.getQuantitySettings().getMaxQuantityPerTrade();
    }

    public double getMaxInvestmentPercentage() {
        return tradingConfig.getInvestmentLimits().getMaxInvestmentPercentage();
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

    // Risk Management Methods
    public double getMilestonePoints() {
        return tradingConfig.getRiskManagement().getMilestonePoints();
    }

    // RSI Thresholds
    public double getCallRsiThreshold() {
        return tradingConfig.getRiskManagement().getRsiThresholds().getCallRsiThreshold();
    }

    public double getPutRsiThreshold() {
        return tradingConfig.getRiskManagement().getRsiThresholds().getPutRsiThreshold();
    }

    public int getRsiMaPeriod() {
        return tradingConfig.getRiskManagement().getRsiThresholds().getRsiMaPeriod();
    }

    public boolean isEnableRsiMaComparison() {
        return tradingConfig.getRiskManagement().getRsiThresholds().isEnableRsiMaComparison();
    }

    // Volume and Signal Configuration
    public double getVolumeSurgeMultiplier() {
        return tradingConfig.getRiskManagement().getVolumeSurgeMultiplier();
    }

    public double getSignalStrength() {
        return tradingConfig.getRiskManagement().getSignalStrength();
    }

    // Legacy Risk Management (for backward compatibility)
    public double getStopLossPercentage() {
        return tradingConfig.getRiskManagement().getStopLossPercentage();
    }

    public double getTargetPercentage() {
        return tradingConfig.getRiskManagement().getTargetPercentage();
    }

    public double getStopLossPoints() {
        return tradingConfig.getRiskManagement().getStopLossPoints();
    }

    public double getTargetPoints() {
        return tradingConfig.getRiskManagement().getTargetPoints();
    }

    public boolean isDynamicRiskManagementEnabled() {
        return tradingConfig.getRiskManagement().getDynamicRiskManagement().isEnabled();
    }

    public double getDynamicStopLossPercentage() {
        return tradingConfig.getRiskManagement().getDynamicRiskManagement().getStopLossPercentage();
    }

    public double getDynamicTargetMultiplier() {
        return tradingConfig.getRiskManagement().getDynamicRiskManagement().getTargetMultiplier();
    }

    public double getDynamicMinStopLoss() {
        return tradingConfig.getRiskManagement().getDynamicRiskManagement().getMinStopLoss();
    }

    public double getDynamicMaxStopLoss() {
        return tradingConfig.getRiskManagement().getDynamicRiskManagement().getMaxStopLoss();
    }

    public double getDynamicMinTarget() {
        return tradingConfig.getRiskManagement().getDynamicRiskManagement().getMinTarget();
    }

    public double getDynamicMaxTarget() {
        return tradingConfig.getRiskManagement().getDynamicRiskManagement().getMaxTarget();
    }

    public double getDefaultStopLossWhenDisabled() {
        return tradingConfig.getRiskManagement().getDynamicRiskManagement().getFallbackValues().getDefaultStopLossWhenDisabled();
    }

    public double getDefaultTargetWhenDisabled() {
        return tradingConfig.getRiskManagement().getDynamicRiskManagement().getFallbackValues().getDefaultTargetWhenDisabled();
    }

    public double getDefaultVolatilityScore() {
        return tradingConfig.getRiskManagement().getDynamicRiskManagement().getFallbackValues().getDefaultVolatilityScore();
    }

    public int getMinRequiredBars() {
        return tradingConfig.getRiskManagement().getDynamicRiskManagement().getTechnicalParameters().getMinRequiredBars();
    }

    public int getVolatilityLookbackPeriod() {
        return tradingConfig.getRiskManagement().getDynamicRiskManagement().getTechnicalParameters().getVolatilityLookbackPeriod();
    }

    public String getCandleTimeframe() {
        return tradingConfig.getRiskManagement().getDynamicRiskManagement().getTechnicalParameters().getCandleTimeframe();
    }

    @Data
    public static class TradingConfig {
        private QuantitySettings quantitySettings;
        private InvestmentLimits investmentLimits;
        private RiskManagement riskManagement;
        private TradeSettings tradeSettings;
        private ExitSignalConfiguration exitSignalConfiguration;
    }

    @Data
    public static class QuantitySettings {
        private int minLotSize;
        private int maxQuantityPerTrade;
    }

    @Data
    public static class InvestmentLimits {
        private double maxInvestmentPercentage;
    }

    @Data
    public static class RiskManagement {
        private double maxRiskPerDayPercentage;
        private double maxProfitPerDayPercentage;
        private double milestonePoints;
        private RsiThresholds rsiThresholds;
        private double volumeSurgeMultiplier;
        private double signalStrength;
        private double stopLossPercentage;
        private double targetPercentage;
        private double stopLossPoints;
        private double targetPoints;
        private DynamicRiskManagement dynamicRiskManagement;
    }

    @Data
    public static class RsiThresholds {
        private double callRsiThreshold;
        private double putRsiThreshold;
        private int rsiMaPeriod;
        private boolean enableRsiMaComparison;
    }

    @Data
    public static class DynamicRiskManagement {
        private boolean enabled;
        private double stopLossPercentage;
        private double targetMultiplier;
        private double minStopLoss;
        private double maxStopLoss;
        private double minTarget;
        private double maxTarget;
        private FallbackValues fallbackValues;
        private TechnicalParameters technicalParameters;
    }

    @Data
    public static class FallbackValues {
        private double defaultStopLossWhenDisabled;
        private double defaultTargetWhenDisabled;
        private double defaultVolatilityScore;
    }

    @Data
    public static class TechnicalParameters {
        private int minRequiredBars;
        private int volatilityLookbackPeriod;
        private String candleTimeframe;
    }

    @Data
    public static class TradeSettings {
        private long maxTradeHoldingTimeInSec;
    }

    @Data
    public static class ExitSignalConfiguration {
        private boolean enabled;
        private String description;
        private ExitThresholds exitThresholds;
        private RsiDivergenceExit rsiDivergenceExit;
        private MarketConditionExit marketConditionExit;
    }

    @Data
    public static class ExitThresholds {
        private double callExitThreshold;
        private double putExitThreshold;
        private String description;
    }

    @Data
    public static class RsiDivergenceExit {
        private boolean enabled;
        private String description;
    }

    @Data
    public static class MarketConditionExit {
        private boolean enabled;
        private String description;
    }
}
