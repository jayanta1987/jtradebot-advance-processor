package com.jtradebot.processor.config;

import com.jtradebot.processor.model.strategy.ScalpingEntryConfig;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicStrategyConfigService {

    private final TradingConfigurationService tradingConfigurationService;
    private final ScoringConfigurationService scoringConfigService;
    @Getter
    private ScalpingEntryConfig scalpingEntryConfig;

    @PostConstruct
    public void loadConfiguration() {
        scalpingEntryConfig = tradingConfigurationService.getScalpingEntryConfig();
        log.info("Dynamic strategy configuration loaded successfully using TradingConfigurationService");
        log.info("Loaded {} scenarios: {}",
                scalpingEntryConfig.getScenarios().size(),
                scalpingEntryConfig.getScenarios().stream()
                        .map(ScalpingEntryConfig.Scenario::getName)
                        .toList());
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

    public String getTargetModeForScenario(String scenarioName) {
        ScalpingEntryConfig.Scenario scenario = getScenarioByName(scenarioName);
        return scenario != null ? scenario.getTargetMode() : "PER"; // Default to PER if not found
    }

    public Map<String, List<String>> getCallCategories() {
        return scalpingEntryConfig.getCallCategories();
    }

    public Map<String, List<String>> getPutCategories() {
        return scalpingEntryConfig.getPutCategories();
    }

    public double getCallVolumeSurgeMultiplier() {
        return tradingConfigurationService.getVolumeSurgeMultiplierMin();
    }

    public double getCallMinMilestonePointPercentage() {
        return tradingConfigurationService.getMinMilestonePointPercentage();
    }

    public double getPutMinMilestonePointPercentage() {
        return tradingConfigurationService.getMinMilestonePointPercentage();
    }

    public double getBaseMilestonePointPercentage() {
        return tradingConfigurationService.getBaseMilestonePointPercentage();
    }


    public int getRsiMaPeriod() {
        return tradingConfigurationService.getRsiMaPeriod();
    }

    public boolean isEnableRsiMaComparison() {
        return tradingConfigurationService.isEnableRsiMaComparison();
    }


    /**
     * Get the minimum quality threshold from the least restrictive scenario
     * This is used for initial filtering before dynamic scenario evaluation
     * We use the lowest threshold to avoid blocking valid scenarios
     */
    public double getMinQualityThresholdFromLeastRestrictiveScenario() {
        try {
            List<ScalpingEntryConfig.Scenario> scenarios = getScenarios();
            if (scenarios.isEmpty()) {
                throw new IllegalStateException("No scenarios found in configuration");
            }

            // Find the scenario with the lowest quality score requirement (least restrictive)
            Optional<ScalpingEntryConfig.Scenario> leastRestrictive = scenarios.stream()
                    .filter(scenario -> scenario.getRequirements() != null && scenario.getRequirements().getMinQualityScore() != null)
                    .min(Comparator.comparing(scenario -> scenario.getRequirements().getMinQualityScore()));

            if (leastRestrictive.isPresent()) {
                return leastRestrictive.get().getRequirements().getMinQualityScore();
            } else {
                // Fallback to scoring config if no scenario has quality score requirement
                return scoringConfigService.getMinQualityScore();
            }
        } catch (Exception e) {
            log.error("❌ CRITICAL ERROR: Cannot read minQualityScore from scenario configuration: {}", e.getMessage());
            throw new RuntimeException("Failed to read quality score threshold from configuration", e);
        }
    }

    public int getMaxNTPFromAllScenarios() {
        try {
            return scalpingEntryConfig.getScenarios().stream()
                    .filter(scenario -> scenario.getRequirements() != null && scenario.getRequirements().getMaxNTP() != null)
                    .max(Comparator.comparing(scenario -> scenario.getRequirements().getMaxNTP()))
                    .map(scenario -> scenario.getRequirements().getMaxNTP())
                    .orElseThrow(() -> new IllegalStateException("No scenarios with MaxNTP found in configuration"));
        } catch (Exception e) {
            log.error("❌ CRITICAL ERROR: Cannot read maxNTP from scenario configuration: {}", e.getMessage());
            throw new RuntimeException("Failed to read maxNTP from configuration", e);
        }
    }


    public int getMaxNTPForScenario(String scenarioName) {
        ScalpingEntryConfig.Scenario scenario = getScenarioByName(scenarioName);
        if (scenario != null && scenario.getRequirements() != null && scenario.getRequirements().getMaxNTP() != null) {
            return scenario.getRequirements().getMaxNTP();
        } else {
            return 0; // Default max NTP
        }
    }
}
