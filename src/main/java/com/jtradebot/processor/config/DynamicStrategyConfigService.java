package com.jtradebot.processor.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtradebot.processor.model.strategy.ScalpingEntryConfig;
import com.jtradebot.processor.model.strategy.ProfitableTradeFilterConfig;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicStrategyConfigService {

    private final TradingConfigurationService tradingConfigurationService;
    @Getter
    private ScalpingEntryConfig scalpingEntryConfig;

    @PostConstruct
    public void loadConfiguration() {
        try {
            ObjectMapper mapper = new ObjectMapper();

            ClassPathResource entryResource = new ClassPathResource("rules/scalping-entry-config.json");
            JsonNode rootNode = mapper.readTree(entryResource.getInputStream());

            // Load scalping entry config
            scalpingEntryConfig = mapper.treeToValue(rootNode, ScalpingEntryConfig.class);

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

    // Dynamic Configuration Methods - Dynamic indicator config removed as it was not being used

    // Flat Market Filtering Configuration Methods


    // No-Trade Zones Configuration Methods
    public ScalpingEntryConfig.NoTradeZonesConfig getNoTradeZonesConfig() {
        return scalpingEntryConfig.getNoTradeZones();
    }

    public boolean isNoTradeZonesEnabled() {
        return scalpingEntryConfig.getNoTradeZones() != null &&
                scalpingEntryConfig.getNoTradeZones().getEnabled();
    }

    public int getMaxOptionalFiltersToIgnore() {
        return getMaxAllowedNTP();
    }
    
    public int getMaxAllowedNTP() {
        return scalpingEntryConfig.getNoTradeZones() != null ?
                scalpingEntryConfig.getNoTradeZones().getMaxAllowedNTP() : 2;
    }
    
    // Legacy method for backward compatibility
    public int getMaxFiltersToIgnore() {
        return getMaxOptionalFiltersToIgnore();
    }

    public Map<String, ScalpingEntryConfig.NoTradeFilter> getNoTradeFilters() {
        return scalpingEntryConfig.getNoTradeZones() != null ?
                scalpingEntryConfig.getNoTradeZones().getFilters() : new HashMap<>();
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
        return tradingConfigurationService.getCallRsiThreshold();
    }

    public double getCallVolumeSurgeMultiplier() {
        return tradingConfigurationService.getVolumeSurgeMultiplier();
    }

    public double getCallSignalStrength() {
        return tradingConfigurationService.getSignalStrength();
    }

    public double getCallStopLossPercentage() {
        return tradingConfigurationService.getStopLossPercentage();
    }

    public double getCallTargetPercentage() {
        return tradingConfigurationService.getTargetPercentage();
    }

    public double getCallStopLossPoints() {
        return tradingConfigurationService.getStopLossPoints();
    }

    public double getCallTargetPoints() {
        return tradingConfigurationService.getTargetPoints();
    }


    public double getCallMilestonePoints() {
        return tradingConfigurationService.getMilestonePoints();
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
        return tradingConfigurationService.getPutRsiThreshold();
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
        return tradingConfigurationService.getMilestonePoints();
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

    public int getRsiMaPeriod() {
        return tradingConfigurationService.getRsiMaPeriod();
    }

    public boolean isEnableRsiMaComparison() {
        return tradingConfigurationService.isEnableRsiMaComparison();
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


    public double getMinQualityThresholdFromScenario(String scenarioName) {
        try {
            var scenario = getScenarioByName(scenarioName);
            if (scenario == null) {
                throw new IllegalStateException("SAFE_ENTRY_SIGNAL scenario not found in configuration");
            }
            if (scenario.getRequirements() == null) {
                throw new IllegalStateException("Requirements not found in SAFE_ENTRY_SIGNAL scenario");
            }
            if (scenario.getRequirements().getMinQualityScore() == null) {
                throw new IllegalStateException("minQualityScore not found in SAFE_ENTRY_SIGNAL scenario requirements");
            }

            return scenario.getRequirements().getMinQualityScore();
        } catch (Exception e) {
            log.error("❌ CRITICAL ERROR: Cannot read minQualityScore from scenario configuration: {}", e.getMessage());
            throw new RuntimeException("Failed to read quality score threshold from configuration", e);
        }
    }


}
