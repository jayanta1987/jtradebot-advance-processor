package com.jtradebot.processor.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtradebot.processor.model.indicator.DynamicIndicatorConfig;
import com.jtradebot.processor.model.strategy.ScalpingEntryConfig;
import com.jtradebot.processor.model.strategy.ProfitableTradeFilterConfig;
import com.jtradebot.processor.model.strategy.FlatMarketFilteringConfig;
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
            log.info("Loaded {} scenarios: {}", 
                scalpingEntryConfig.getScenarios().size(),
                scalpingEntryConfig.getScenarios().stream()
                    .map(ScalpingEntryConfig.Scenario::getName)
                    .toList());
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
    
    // Flat Market Filtering Configuration Methods
    public FlatMarketFilteringConfig getFlatMarketFilteringConfig() {
        return scalpingEntryConfig.getFlatMarketFiltering();
    }
    
    public boolean isFlatMarketFilteringEnabled() {
        return scalpingEntryConfig.getFlatMarketFiltering() != null && 
               scalpingEntryConfig.getFlatMarketFiltering().isEnabled();
    }
    
    // New Scenario-based Methods
    public List<ScalpingEntryConfig.Scenario> getScenarios() {
        return scalpingEntryConfig.getScenarios();
    }
    
    public ScalpingEntryConfig.Scenario getScenarioByName(String scenarioName) {
        return scalpingEntryConfig.getScenarios().stream()
                .filter(scenario -> scenario.getName().equals(scenarioName))
                .findFirst()
                .orElse(null);
    }
    
    public Map<String, List<String>> getCallCategories() {
        return scalpingEntryConfig.getCallCategories();
    }
    
    public Map<String, List<String>> getPutCategories() {
        return scalpingEntryConfig.getPutCategories();
    }
    
    public List<String> getCallCategoryConditions(String categoryName) {
        return scalpingEntryConfig.getCallCategories().get(categoryName);
    }
    
    public List<String> getPutCategoryConditions(String categoryName) {
        return scalpingEntryConfig.getPutCategories().get(categoryName);
    }
    
    // Legacy compatibility method
    public Map<String, List<String>> getCategories() {
        // Return call categories as default for backward compatibility
        return scalpingEntryConfig.getCallCategories();
    }
    
    public List<String> getCategoryConditions(String categoryName) {
        // Return call category conditions as default for backward compatibility
        return scalpingEntryConfig.getCallCategories().get(categoryName);
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
        // Get from first scenario that has volume requirements
        return scalpingEntryConfig.getScenarios().stream()
                .filter(scenario -> scenario.getRequirements().getFutureAndVolume_min_count() != null)
                .findFirst()
                .map(scenario -> 10.0) // Default value
                .orElse(10.0);
    }
    
    public double getCallSignalStrength() {
        // Get from first scenario that has quality score requirements
        return scalpingEntryConfig.getScenarios().stream()
                .filter(scenario -> scenario.getRequirements().getMinQualityScore() != null)
                .findFirst()
                .map(scenario -> scenario.getRequirements().getMinQualityScore())
                .orElse(7.5);
    }
    
    public double getCallStopLossPercentage() {
        // Get from first scenario that has risk management
        return scalpingEntryConfig.getScenarios().stream()
                .filter(scenario -> scenario.getRiskManagement() != null && scenario.getRiskManagement().getStopLossPercentage() != null)
                .findFirst()
                .map(scenario -> scenario.getRiskManagement().getStopLossPercentage())
                .orElse(0.3);
    }
    
    public double getCallTargetPercentage() {
        // Get from first scenario that has risk management
        return scalpingEntryConfig.getScenarios().stream()
                .filter(scenario -> scenario.getRiskManagement() != null && scenario.getRiskManagement().getTargetPercentage() != null)
                .findFirst()
                .map(scenario -> scenario.getRiskManagement().getTargetPercentage())
                .orElse(0.8);
    }
    
    public double getCallStopLossPoints() {
        // Get from first scenario that has risk management
        return scalpingEntryConfig.getScenarios().stream()
                .filter(scenario -> scenario.getRiskManagement() != null && scenario.getRiskManagement().getStopLossPoints() != null)
                .findFirst()
                .map(scenario -> scenario.getRiskManagement().getStopLossPoints())
                .orElse(10.0);
    }
    
    public double getCallTargetPoints() {
        // Get from first scenario that has risk management
        return scalpingEntryConfig.getScenarios().stream()
                .filter(scenario -> scenario.getRiskManagement() != null && scenario.getRiskManagement().getTargetPoints() != null)
                .findFirst()
                .map(scenario -> scenario.getRiskManagement().getTargetPoints())
                .orElse(20.0);
    }
    

    
    public double getCallMilestonePoints() {
        // Get from first scenario that has risk management
        return scalpingEntryConfig.getScenarios().stream()
                .filter(scenario -> scenario.getRiskManagement() != null && scenario.getRiskManagement().getMilestonePoints() != null)
                .findFirst()
                .map(scenario -> scenario.getRiskManagement().getMilestonePoints())
                .orElse(5.0); // Default 5.0 points if not configured
    }
    
    public double getCallMaxStopLossPoints() {
        return getCallStopLossPoints();
    }
    

    
    public boolean isCallCheck1Min() {
        return scalpingEntryConfig.getFuturesignalsConfig().getEnabledTimeframes().contains("1min");
    }
    
    public boolean isCallCheck5Min() {
        return scalpingEntryConfig.getFuturesignalsConfig().getEnabledTimeframes().contains("5min");
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
        return getCallVolumeSurgeMultiplier(); // Same as call for now
    }
    
    public double getPutSignalStrength() {
        return getCallSignalStrength(); // Same as call for now
    }
    
    public double getPutStopLossPercentage() {
        return getCallStopLossPercentage(); // Same as call for now
    }
    
    public double getPutTargetPercentage() {
        return getCallTargetPercentage(); // Same as call for now
    }
    
    public double getPutTargetPoints() {
        return getCallTargetPoints(); // Same as call for now
    }
    
    public double getPutStopLossPoints() {
        return getCallStopLossPoints(); // Same as call for now
    }
    

    
    public double getPutMilestonePoints() {
        return 5.0; // Default milestone points
    }
    
    public double getPutMaxStopLossPoints() {
        return getPutStopLossPoints();
    }
    

    
    public boolean isPutCheck1Min() {
        return isCallCheck1Min(); // Same as call for now
    }
    
    public boolean isPutCheck5Min() {
        return isCallCheck5Min(); // Same as call for now
    }
    
    public boolean isPutCheck15Min() {
        return false; // Disable 15min RSI check as requested
    }
    
    // Common Configuration Methods
    public double getMinVolumeSurgeMultiplier() {
        return getCallVolumeSurgeMultiplier();
    }
    
    public double getMinSignalStrength() {
        return getCallSignalStrength();
    }
    
    public double getMaxSpreadPercentage() {
        return 0.5; // Default value
    }
    
    public double getMinLiquidityThreshold() {
        return 1000000.0; // Default value
    }
    
    public List<String> getAvoidTimeSlots() {
        return List.of("09:15-09:30", "15:15-15:30"); // Default time slots to avoid
    }
    
    public boolean isRequireVolumeConfirmation() {
        return true; // Always required in new system
    }
    
    public boolean isRequirePriceActionConfirmation() {
        return true; // Always required in new system
    }
    
    public double getMinConfidenceScore() {
        return 7.0; // Default value
    }
    
    public int getMinMandatoryCount() {
        return 2; // Default value
    }
    
    public int getMinOptionalCount() {
        return 1; // Default value
    }
    
    // Risk Management Methods
    public double getMaxRiskPerTrade() {
        return scalpingEntryConfig.getTradingConfiguration().getRiskManagement().getMaxRiskPerDayPercentage();
    }
    
    public double getPositionSize() {
        return 1.0; // Default value
    }
    

    
    public double getTrailingStopPercentage() {
        return 0.2; // Default value
    }
    
    public boolean isUseBreakEven() {
        return true; // Default value
    }
    
    public double getBreakEvenTrigger() {
        return 0.4; // Default value
    }
    
    // Quality Assessment Methods
    public double getMinVolumeSurge() {
        return getCallVolumeSurgeMultiplier();
    }
    
    public double getMinRsiStrength() {
        return 56.0; // Default value
    }
    
    public double getMinEmaAlignment() {
        return 0.7; // Default value
    }
    
    public double getMinPriceMomentum() {
        return 0.5; // Default value
    }
    
    public boolean isRequireTrendAlignment() {
        return true; // Default value
    }
    
    public boolean isRequireSupportResistance() {
        return true; // Default value
    }
    
    public boolean isRequireVwapAlignment() {
        return true; // Default value
    }
    
    // Market Condition Methods
    public String getMarketSession() {
        return "ALL"; // Default value
    }
    
    public double getMinVolatility() {
        return 0.5; // Default value
    }
    
    public double getMaxVolatility() {
        return 5.0; // Default value
    }
    
    public boolean isAvoidHighSpread() {
        return true; // Default value
    }
    
    public boolean isAvoidNewsTime() {
        return true; // Default value
    }
    
    public boolean isRequireLiquidity() {
        return true; // Default value
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
        return scalpingEntryConfig.getStrategy();
    }
    
    public String getStrategyVersion() {
        return scalpingEntryConfig.getVersion();
    }
    
    public String getStrategyDescription() {
        return scalpingEntryConfig.getDescription();
    }
    
    public List<String> getFuturesignalTimeframes() {
        return scalpingEntryConfig.getFuturesignalsConfig().getEnabledTimeframes();
    }
    
    public Map<String, Object> getFullConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("strategy", getStrategyName());
        config.put("version", getStrategyVersion());
        config.put("description", getStrategyDescription());
        config.put("timeframes", getFuturesignalTimeframes());
        config.put("dynamicIndicatorConfig", dynamicIndicatorConfig);
        config.put("profitableTradeFilterConfig", profitableTradeFilterConfig);
        config.put("scenarios", scalpingEntryConfig.getScenarios());
        config.put("callCategories", scalpingEntryConfig.getCallCategories());
        config.put("putCategories", scalpingEntryConfig.getPutCategories());
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
        return scalpingEntryConfig.getWeights().getEmaCrossoverWeight();
    }
    
    public double getRsiConditionWeight() {
        return scalpingEntryConfig.getWeights().getRsiConditionWeight();
    }
    
    public double getVolumeSurgeWeight() {
        return scalpingEntryConfig.getWeights().getVolumeSurgeWeight();
    }
    
    public double getPriceActionWeight() {
        return scalpingEntryConfig.getWeights().getPriceActionWeight();
    }
    
    public double getFuturesignalsWeight() {
        return scalpingEntryConfig.getWeights().getFuturesignalsWeight();
    }
}
