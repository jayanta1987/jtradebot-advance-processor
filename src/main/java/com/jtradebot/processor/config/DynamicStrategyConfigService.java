package com.jtradebot.processor.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtradebot.processor.model.DynamicIndicatorConfig;
import com.jtradebot.processor.model.ScalpingEntryConfig;
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
    private JsonNode legacyConfig; // For backward compatibility during transition
    
    @PostConstruct
    public void loadConfiguration() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            
            // Load new dynamic configurations
            ClassPathResource indicatorResource = new ClassPathResource("rules/dynamic-indicators-config.json");
            dynamicIndicatorConfig = mapper.readValue(indicatorResource.getInputStream(), DynamicIndicatorConfig.class);
            
            ClassPathResource entryResource = new ClassPathResource("rules/scalping-entry-config.json");
            scalpingEntryConfig = mapper.readValue(entryResource.getInputStream(), ScalpingEntryConfig.class);
            
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
        return scalpingEntryConfig.getCallStrategy().getEntryQuality().getMinRsiStrength();
    }
    
    public double getCallVolumeSurgeMultiplier() {
        return scalpingEntryConfig.getCallStrategy().getEntryQuality().getMinVolumeSurge();
    }
    
    public double getCallSignalStrength() {
        return scalpingEntryConfig.getCallStrategy().getEntryQuality().getMinSignalStrength();
    }
    
    public double getCallStopLossPercentage() {
        return scalpingEntryConfig.getCallStrategy().getRiskManagement().getStopLossPercentage();
    }
    
    public double getCallTargetPercentage() {
        return scalpingEntryConfig.getCallStrategy().getRiskManagement().getTargetPercentage();
    }
    
    public double getCallStopLossPoints() {
        return scalpingEntryConfig.getCallStrategy().getRiskManagement().getStopLossPoints();
    }
    
    public double getCallTargetPoints() {
        return scalpingEntryConfig.getCallStrategy().getRiskManagement().getTargetPoints();
    }
    
    public int getCallMaxHoldingTimeMinutes() {
        return scalpingEntryConfig.getCallStrategy().getRiskManagement().getMaxHoldingTimeMinutes();
    }
    
    public double getCallMilestonePoints() {
        return 5.0; // Default milestone points
    }
    
    public double getCallMaxStopLossPoints() {
        return scalpingEntryConfig.getCallStrategy().getRiskManagement().getStopLossPoints();
    }
    
    public boolean isCallTrailingStopLoss() {
        return scalpingEntryConfig.getCallStrategy().getRiskManagement().isUseTrailingStop();
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
        return scalpingEntryConfig.getPutStrategy().getEntryQuality().getMinRsiStrength();
    }
    
    public double getPutVolumeSurgeMultiplier() {
        return scalpingEntryConfig.getPutStrategy().getEntryQuality().getMinVolumeSurge();
    }
    
    public double getPutSignalStrength() {
        return scalpingEntryConfig.getPutStrategy().getEntryQuality().getMinSignalStrength();
    }
    
    public double getPutStopLossPercentage() {
        return scalpingEntryConfig.getPutStrategy().getRiskManagement().getStopLossPercentage();
    }
    
    public double getPutTargetPercentage() {
        return scalpingEntryConfig.getPutStrategy().getRiskManagement().getTargetPercentage();
    }
    
    public double getPutTargetPoints() {
        return scalpingEntryConfig.getPutStrategy().getRiskManagement().getTargetPoints();
    }
    
    public double getPutStopLossPoints() {
        return scalpingEntryConfig.getPutStrategy().getRiskManagement().getStopLossPoints();
    }
    
    public int getPutMaxHoldingTimeMinutes() {
        return scalpingEntryConfig.getPutStrategy().getRiskManagement().getMaxHoldingTimeMinutes();
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
        config.put("scalpingEntryConfig", scalpingEntryConfig);
        return config;
    }
    
    // Additional legacy compatibility methods
    public boolean isCallRequireAllTimeframesBullish() {
        return scalpingEntryConfig.getCallStrategy().getEntryConditions().isRequireAllTimeframes();
    }
    
    public boolean isCallRequireVolumeSurge() {
        return scalpingEntryConfig.getCallStrategy().getEntryConditions().isRequireVolumeConfirmation();
    }
    
    public boolean isCallRequireEmaCrossover() {
        return true; // Always required in new system
    }
    
    public boolean isCallRequireRsiOverbought() {
        return true; // Always required in new system
    }
    
    public boolean isCallRequirePriceAboveVwap() {
        return scalpingEntryConfig.getCallStrategy().getEntryConditions().isRequirePriceActionConfirmation();
    }
    
    public boolean isCallRequirePriceAboveResistance() {
        return scalpingEntryConfig.getCallStrategy().getEntryQuality().isRequireSupportResistance();
    }
    
    public boolean isPutRequireAllTimeframesBearish() {
        return scalpingEntryConfig.getPutStrategy().getEntryConditions().isRequireAllTimeframes();
    }
    
    public boolean isPutRequireVolumeSurge() {
        return scalpingEntryConfig.getPutStrategy().getEntryConditions().isRequireVolumeConfirmation();
    }
    
    public boolean isPutRequireEmaCrossover() {
        return true; // Always required in new system
    }
    
    public boolean isPutRequireRsiOversold() {
        return true; // Always required in new system
    }
    
    public boolean isPutRequirePriceBelowVwap() {
        return scalpingEntryConfig.getPutStrategy().getEntryConditions().isRequirePriceActionConfirmation();
    }
    
    public boolean isPutRequirePriceBelowSupport() {
        return scalpingEntryConfig.getPutStrategy().getEntryQuality().isRequireSupportResistance();
    }
    
    // Weight methods for backward compatibility
    public double getEmaCrossoverWeight() {
        return 0.25; // Default weight for EMA crossover
    }
    
    public double getRsiConditionWeight() {
        return 0.20; // Default weight for RSI conditions
    }
    
    public double getVolumeSurgeWeight() {
        return 0.25; // Default weight for volume surge
    }
    
    public double getPriceActionWeight() {
        return 0.15; // Default weight for price action
    }
    
    public double getFuturesignalsWeight() {
        return 0.15; // Default weight for futures signals
    }
}
