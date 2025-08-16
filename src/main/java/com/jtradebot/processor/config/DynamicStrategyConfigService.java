package com.jtradebot.processor.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtradebot.processor.model.indicator.DynamicIndicatorConfig;
import com.jtradebot.processor.model.strategy.ScalpingEntryConfig;
import com.jtradebot.processor.model.strategy.ProfitableTradeFilterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class DynamicStrategyConfigService {
    
    private DynamicIndicatorConfig dynamicIndicatorConfig;
    private ScalpingEntryConfig scalpingEntryConfig;
    private ProfitableTradeFilterConfig profitableTradeFilterConfig;
    private JsonNode legacyConfig; // For backward compatibility during transition
    
    @PostConstruct
    public void loadConfiguration() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            
            // Load new dynamic configurations
            ClassPathResource indicatorResource = new ClassPathResource("rules/dynamic-indicators-config.json");
            dynamicIndicatorConfig = mapper.readValue(indicatorResource.getInputStream(), DynamicIndicatorConfig.class);
            
            ClassPathResource entryResource = new ClassPathResource("rules/scalping-entry-config.json");
            JsonNode rootNode = mapper.readTree(entryResource.getInputStream());
            
            // Load scalping entry config
            scalpingEntryConfig = mapper.treeToValue(rootNode, ScalpingEntryConfig.class);
            
            // Load profitable trade filter configuration
            if (rootNode.has("profitableTradeFilter")) {
                profitableTradeFilterConfig = mapper.treeToValue(rootNode.get("profitableTradeFilter"), ProfitableTradeFilterConfig.class);
            } else {
                // Create default config if section doesn't exist
                profitableTradeFilterConfig = ProfitableTradeFilterConfig.builder()
                        .enabled(false)
                        .description("Default config - filtering disabled")
                        .build();
            }
            
            // Legacy config is no longer needed since we've migrated to new system
            legacyConfig = null;
            
            log.info("Dynamic strategy configuration loaded successfully");
        } catch (Exception e) {
            log.error("Failed to load dynamic strategy configuration", e);
            throw new RuntimeException("Failed to load dynamic strategy configuration", e);
        }
    }
    
    // New Dynamic Configuration Methods
    public DynamicIndicatorConfig getDynamicIndicatorConfig() {
        return dynamicIndicatorConfig;
    }
    
    public ScalpingEntryConfig getScalpingEntryConfig() {
        return scalpingEntryConfig;
    }
    
    public ProfitableTradeFilterConfig getProfitableTradeFilterConfig() {
        return profitableTradeFilterConfig;
    }
    
    // Legacy Compatibility Methods (mapped to new config)
    public String getCallRuleName() {
        return "SCALPING_FUTURE_VOLUME_SURGE_BULLISH";
    }
    
    public String getCallStrategyType() {
        return "SCALPING_FUTURE_VOLUME_SURGE_BULLISH";
    }
    
    public boolean isCallRuleActive() {
        return true; // Always active in new system
    }
    
    public double getCallRsiThreshold() {
        return 56.0; // Default value from new config
    }
    
    public double getCallVolumeSurgeMultiplier() {
        return profitableTradeFilterConfig.getCallStrategy().getVolumeSurgeMultiplier().getMin();
    }
    
    public double getCallSignalStrength() {
        return profitableTradeFilterConfig.getCallStrategy().getMinQualityScore();
    }
    
    public double getCallStopLossPercentage() {
        return profitableTradeFilterConfig.getCallStrategy().getRiskManagement().getStopLossPercentage();
    }
    
    public double getCallTargetPercentage() {
        return profitableTradeFilterConfig.getCallStrategy().getRiskManagement().getTargetPercentage();
    }
    
    public double getCallStopLossPoints() {
        return profitableTradeFilterConfig.getCallStrategy().getRiskManagement().getStopLossPoints();
    }
    
    public double getCallTargetPoints() {
        return profitableTradeFilterConfig.getCallStrategy().getRiskManagement().getTargetPoints();
    }
    
    public int getCallMaxHoldingTimeMinutes() {
        return profitableTradeFilterConfig.getCallStrategy().getRiskManagement().getMaxHoldingTimeMinutes();
    }
    
    public double getCallMilestonePoints() {
        return 5.0; // Default milestone points
    }
    
    public double getCallMaxStopLossPoints() {
        return profitableTradeFilterConfig.getCallStrategy().getRiskManagement().getStopLossPoints();
    }
    
    public boolean isCallTrailingStopLoss() {
        return profitableTradeFilterConfig.getCallStrategy().getRiskManagement().getUseTrailingStop();
    }
    
    public boolean isCallCheck1Min() {
        return dynamicIndicatorConfig.getTimeframes().contains("1min");
    }
    
    public boolean isCallCheck5Min() {
        return dynamicIndicatorConfig.getTimeframes().contains("5min");
    }
    
    public boolean isCallCheck15Min() {
        return false; // Disable 15min RSI check as requested
    }
    
    // PUT Rule Methods (mapped to new config)
    public String getPutRuleName() {
        return "SCALPING_FUTURE_VOLUME_SURGE_BEARISH";
    }
    
    public String getPutStrategyType() {
        return "SCALPING_FUTURE_VOLUME_SURGE_BEARISH";
    }
    
    public boolean isPutRuleActive() {
        return true; // Always active in new system
    }
    
    public double getPutRsiThreshold() {
        return 44.0; // Default value from new config
    }
    
    public double getPutVolumeSurgeMultiplier() {
        return profitableTradeFilterConfig.getPutStrategy().getVolumeSurgeMultiplier().getMin();
    }
    
    public double getPutSignalStrength() {
        return profitableTradeFilterConfig.getPutStrategy().getMinQualityScore();
    }
    
    public double getPutStopLossPercentage() {
        return profitableTradeFilterConfig.getPutStrategy().getRiskManagement().getStopLossPercentage();
    }
    
    public double getPutTargetPercentage() {
        return profitableTradeFilterConfig.getPutStrategy().getRiskManagement().getTargetPercentage();
    }
    
    public double getPutTargetPoints() {
        return profitableTradeFilterConfig.getPutStrategy().getRiskManagement().getTargetPoints();
    }
    
    public double getPutStopLossPoints() {
        return profitableTradeFilterConfig.getPutStrategy().getRiskManagement().getStopLossPoints();
    }
    
    public int getPutMaxHoldingTimeMinutes() {
        return profitableTradeFilterConfig.getPutStrategy().getRiskManagement().getMaxHoldingTimeMinutes();
    }
    
    public double getPutMilestonePoints() {
        return 5.0; // Default milestone points
    }
    
    public double getPutMaxStopLossPoints() {
        return scalpingEntryConfig.getPutStrategy().getRiskManagement().getStopLossPoints();
    }
    
    public boolean isPutTrailingStopLoss() {
        return scalpingEntryConfig.getPutStrategy().getRiskManagement().isUseTrailingStop();
    }
    
    public boolean isPutCheck1Min() {
        return dynamicIndicatorConfig.getTimeframes().contains("1min");
    }
    
    public boolean isPutCheck5Min() {
        return dynamicIndicatorConfig.getTimeframes().contains("5min");
    }
    
    public boolean isPutCheck15Min() {
        return false; // Disable 15min RSI check as requested
    }
    
    // Common Configuration Methods
    public double getMinVolumeSurgeMultiplier() {
        return scalpingEntryConfig.getCallStrategy().getEntryQuality().getMinVolumeSurge();
    }
    
    public double getMinSignalStrength() {
        return scalpingEntryConfig.getCallStrategy().getEntryQuality().getMinSignalStrength();
    }
    
    public double getMaxSpreadPercentage() {
        return scalpingEntryConfig.getCallStrategy().getMarketConditions().getMaxSpreadPercentage();
    }
    
    public double getMinLiquidityThreshold() {
        return scalpingEntryConfig.getCallStrategy().getMarketConditions().getMinLiquidityThreshold();
    }
    
    public List<String> getAvoidTimeSlots() {
        return scalpingEntryConfig.getCallStrategy().getMarketConditions().getAvoidTimeSlots();
    }
    
    public boolean isRequireVolumeConfirmation() {
        return scalpingEntryConfig.getCallStrategy().getEntryConditions().isRequireVolumeConfirmation();
    }
    
    public boolean isRequirePriceActionConfirmation() {
        return scalpingEntryConfig.getCallStrategy().getEntryConditions().isRequirePriceActionConfirmation();
    }
    
    public double getMinConfidenceScore() {
        return scalpingEntryConfig.getCallStrategy().getEntryConditions().getMinConfidenceScore();
    }
    
    public int getMinMandatoryCount() {
        return scalpingEntryConfig.getCallStrategy().getEntryConditions().getMinMandatoryCount();
    }
    
    public int getMinOptionalCount() {
        return scalpingEntryConfig.getCallStrategy().getEntryConditions().getMinOptionalCount();
    }
    
    // Risk Management Methods
    public double getMaxRiskPerTrade() {
        return scalpingEntryConfig.getCallStrategy().getRiskManagement().getMaxRiskPerTrade();
    }
    
    public double getPositionSize() {
        return scalpingEntryConfig.getCallStrategy().getRiskManagement().getPositionSize();
    }
    
    public boolean isUseTrailingStop() {
        return scalpingEntryConfig.getCallStrategy().getRiskManagement().isUseTrailingStop();
    }
    
    public double getTrailingStopPercentage() {
        return scalpingEntryConfig.getCallStrategy().getRiskManagement().getTrailingStopPercentage();
    }
    
    public boolean isUseBreakEven() {
        return scalpingEntryConfig.getCallStrategy().getRiskManagement().isUseBreakEven();
    }
    
    public double getBreakEvenTrigger() {
        return scalpingEntryConfig.getCallStrategy().getRiskManagement().getBreakEvenTrigger();
    }
    
    // Quality Assessment Methods
    public double getMinVolumeSurge() {
        return scalpingEntryConfig.getCallStrategy().getEntryQuality().getMinVolumeSurge();
    }
    
    public double getMinRsiStrength() {
        return scalpingEntryConfig.getCallStrategy().getEntryQuality().getMinRsiStrength();
    }
    
    public double getMinEmaAlignment() {
        return scalpingEntryConfig.getCallStrategy().getEntryQuality().getMinEmaAlignment();
    }
    
    public double getMinPriceMomentum() {
        return scalpingEntryConfig.getCallStrategy().getEntryQuality().getMinPriceMomentum();
    }
    
    public boolean isRequireTrendAlignment() {
        return scalpingEntryConfig.getCallStrategy().getEntryQuality().isRequireTrendAlignment();
    }
    
    public boolean isRequireSupportResistance() {
        return scalpingEntryConfig.getCallStrategy().getEntryQuality().isRequireSupportResistance();
    }
    
    public boolean isRequireVwapAlignment() {
        return scalpingEntryConfig.getCallStrategy().getEntryQuality().isRequireVwapAlignment();
    }
    
    // Market Condition Methods
    public String getMarketSession() {
        return scalpingEntryConfig.getCallStrategy().getMarketConditions().getMarketSession();
    }
    
    public double getMinVolatility() {
        return scalpingEntryConfig.getCallStrategy().getMarketConditions().getMinVolatility();
    }
    
    public double getMaxVolatility() {
        return scalpingEntryConfig.getCallStrategy().getMarketConditions().getMaxVolatility();
    }
    
    public boolean isAvoidHighSpread() {
        return scalpingEntryConfig.getCallStrategy().getMarketConditions().isAvoidHighSpread();
    }
    
    public boolean isAvoidNewsTime() {
        return scalpingEntryConfig.getCallStrategy().getMarketConditions().isAvoidNewsTime();
    }
    
    public boolean isRequireLiquidity() {
        return scalpingEntryConfig.getCallStrategy().getMarketConditions().isRequireLiquidity();
    }
    
    // Legacy config is no longer available since we've migrated to new system
    public JsonNode getLegacyConfig() {
        return null; // Legacy config no longer needed
    }
    
    // Health Check
    public boolean isConfigurationLoaded() {
        return dynamicIndicatorConfig != null && scalpingEntryConfig != null;
    }
    
    public String getConfigurationStatus() {
        if (isConfigurationLoaded()) {
            return "Dynamic configuration loaded successfully";
        } else {
            return "Configuration not loaded";
        }
    }
    
    // Additional methods for backward compatibility
    public String getStrategyName() {
        return "SCALPING_FUTURE_VOLUME_SURGE";
    }
    
    public String getStrategyVersion() {
        return "2.0";
    }
    
    public String getStrategyDescription() {
        return "Dynamic scalping strategy with volume surge and technical indicators";
    }
    
    public List<String> getFuturesignalTimeframes() {
        return List.of("1min", "5min", "15min");
    }
    
    public Map<String, Object> getFullConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("strategy", getStrategyName());
        config.put("version", getStrategyVersion());
        config.put("description", getStrategyDescription());
        config.put("timeframes", getFuturesignalTimeframes());
        config.put("dynamicIndicatorConfig", dynamicIndicatorConfig);
        config.put("profitableTradeFilterConfig", profitableTradeFilterConfig);
        return config;
    }
    
    // Additional legacy compatibility methods
    public boolean isCallRequireAllTimeframesBullish() {
        return false; // Not used in new category-based system
    }
    
    public boolean isCallRequireVolumeSurge() {
        return true; // Always required in new system
    }
    
    public boolean isCallRequireEmaCrossover() {
        return true; // Always required in new system
    }
    
    public boolean isCallRequireRsiOverbought() {
        return true; // Always required in new system
    }
    
    public boolean isCallRequirePriceAboveVwap() {
        return true; // Always required in new system
    }
    
    public boolean isCallRequirePriceAboveResistance() {
        return true; // Always required in new system
    }
    
    public boolean isPutRequireAllTimeframesBearish() {
        return false; // Not used in new category-based system
    }
    
    public boolean isPutRequireVolumeSurge() {
        return true; // Always required in new system
    }
    
    public boolean isPutRequireEmaCrossover() {
        return true; // Always required in new system
    }
    
    public boolean isPutRequireRsiOversold() {
        return true; // Always required in new system
    }
    
    public boolean isPutRequirePriceBelowVwap() {
        return true; // Always required in new system
    }
    
    public boolean isPutRequirePriceBelowSupport() {
        return true; // Always required in new system
    }
    
    // Weight methods for backward compatibility
    public double getEmaCrossoverWeight() {
        return profitableTradeFilterConfig.getWeights().getEmaCrossoverWeight();
    }
    
    public double getRsiConditionWeight() {
        return profitableTradeFilterConfig.getWeights().getRsiConditionWeight();
    }
    
    public double getVolumeSurgeWeight() {
        return profitableTradeFilterConfig.getWeights().getVolumeSurgeWeight();
    }
    
    public double getPriceActionWeight() {
        return profitableTradeFilterConfig.getWeights().getPriceActionWeight();
    }
    
    public double getFuturesignalsWeight() {
        return profitableTradeFilterConfig.getWeights().getFuturesignalsWeight();
    }
}
