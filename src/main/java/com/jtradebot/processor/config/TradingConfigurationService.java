package com.jtradebot.processor.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtradebot.processor.model.strategy.ScalpingEntryConfig;
import com.jtradebot.processor.service.config.MongoConfigurationService;
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
    private final MongoConfigurationService mongoConfigurationService;
    @Getter
    private TradingConfig tradingConfig;
    @Getter
    private ScoringConfig scoringConfig;
    @Getter
    private ThresholdConfig thresholdConfig;
    @Getter
    private TimingConfig timingConfig;
    @Getter
    private ScalpingEntryConfig scalpingEntryConfig;

    public TradingConfigurationService(ObjectMapper objectMapper, MongoConfigurationService mongoConfigurationService) {
        this.objectMapper = objectMapper;
        this.mongoConfigurationService = mongoConfigurationService;
    }

    @Override
    public void afterPropertiesSet() {
        loadTradingConfiguration();
        loadScoringConfiguration();
        loadScalpingEntryConfiguration();
    }

    private void loadTradingConfiguration() {
        try {
            ClassPathResource resource = new ClassPathResource("rules/trading-config.json");
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

    private void loadScoringConfiguration() {
        try {
            ClassPathResource resource = new ClassPathResource("rules/scoring-config.json");
            JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
            
            if (rootNode.has("scoringConfiguration")) {
                JsonNode scoringNode = rootNode.get("scoringConfiguration");
                scoringConfig = objectMapper.treeToValue(scoringNode, ScoringConfig.class);
                log.info("Scoring configuration loaded successfully");
            } else {
                throw new RuntimeException("No scoringConfiguration found in JSON file");
            }
            
            if (rootNode.has("thresholdConfiguration")) {
                JsonNode thresholdNode = rootNode.get("thresholdConfiguration");
                thresholdConfig = objectMapper.treeToValue(thresholdNode, ThresholdConfig.class);
                log.info("Threshold configuration loaded successfully");
            } else {
                throw new RuntimeException("No thresholdConfiguration found in JSON file");
            }
            
            if (rootNode.has("timingConfiguration")) {
                JsonNode timingNode = rootNode.get("timingConfiguration");
                timingConfig = objectMapper.treeToValue(timingNode, TimingConfig.class);
                log.info("Timing configuration loaded successfully");
            } else {
                throw new RuntimeException("No timingConfiguration found in JSON file");
            }
            
        } catch (IOException e) {
            log.error("Failed to load configuration from JSON file", e);
            throw new RuntimeException("Failed to load scoring configuration from JSON file", e);
        }
    }

    private void loadScalpingEntryConfiguration() {
        try {
            // Load configuration entirely from MongoDB
            scalpingEntryConfig = ScalpingEntryConfig.builder()
                    .strategy("SCALPING_ENTRY_LOGIC")
                    .version("2.1")
                    .description("Clean scalping entry logic with individual indicator weightage-based scoring system")
                    .build();

            // Load configurable sections from MongoDB
            scalpingEntryConfig.setScenarios(mongoConfigurationService.getScenariosFromMongoDB());
            log.info("Loaded {} scenarios from MongoDB", scalpingEntryConfig.getScenarios().size());
            
            scalpingEntryConfig.setCategoryScoring(mongoConfigurationService.getCategoryScoringFromMongoDB());
            log.info("Loaded category scoring from MongoDB");
            
            scalpingEntryConfig.setNoTradeZones(mongoConfigurationService.getNoTradeZonesFromMongoDB());
            log.info("Loaded no-trade zones from MongoDB");

            // Set default values for non-configurable sections
            scalpingEntryConfig.setFuturesignalsConfig(ScalpingEntryConfig.FuturesignalsConfig.builder()
                    .enabledTimeframes(java.util.Arrays.asList("1min", "5min"))
                    .build());

            log.info("Scalping entry configuration loaded successfully from MongoDB");
            log.info("Loaded {} scenarios: {}",
                    scalpingEntryConfig.getScenarios().size(),
                    scalpingEntryConfig.getScenarios().stream()
                            .map(ScalpingEntryConfig.Scenario::getName)
                            .toList());
        } catch (Exception e) {
            log.error("Failed to load scalping entry configuration from MongoDB", e);
            throw new RuntimeException("Failed to load scalping entry configuration from MongoDB", e);
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
    public double getMinMilestonePoints() {
        return tradingConfig.getRiskManagement().getMinMilestonePoints();
    }

    public double getMaxMilestonePoints() {
        return tradingConfig.getRiskManagement().getMaxMilestonePoints();
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

    public java.util.List<ScalpingEntryConfig.Scenario> getScenarios() {
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

    public java.util.Map<String, java.util.List<String>> getCallCategories() {
        return scalpingEntryConfig.getCallCategories();
    }

    public java.util.Map<String, java.util.List<String>> getPutCategories() {
        return scalpingEntryConfig.getPutCategories();
    }

    // Market end scheduler configuration
    public boolean isMarketEndSchedulerEnabled() {
        // Default to enabled - this can be made configurable in MongoDB if needed
        return true;
    }

    // Trading hours configuration
    public int getMarketStartHour() {
        try {
            // Get trading hours from MongoDB no-trade zone filters
            if (scalpingEntryConfig != null && scalpingEntryConfig.getNoTradeZones() != null) {
                ScalpingEntryConfig.NoTradeFilter tradingHoursFilter = 
                        scalpingEntryConfig.getNoTradeZones().getFilters().get("tradingHours");
                if (tradingHoursFilter != null && tradingHoursFilter.getStartHour() != null) {
                    return tradingHoursFilter.getStartHour();
                }
            }
            return 9; // Default
        } catch (Exception e) {
            log.error("Error getting market start hour: {}", e.getMessage(), e);
            return 9; // Default
        }
    }

    public int getMarketStartMinute() {
        try {
            // Get trading hours from MongoDB no-trade zone filters
            if (scalpingEntryConfig != null && scalpingEntryConfig.getNoTradeZones() != null) {
                ScalpingEntryConfig.NoTradeFilter tradingHoursFilter = 
                        scalpingEntryConfig.getNoTradeZones().getFilters().get("tradingHours");
                if (tradingHoursFilter != null && tradingHoursFilter.getStartMinute() != null) {
                    return tradingHoursFilter.getStartMinute();
                }
            }
            return 15; // Default
        } catch (Exception e) {
            log.error("Error getting market start minute: {}", e.getMessage(), e);
            return 15; // Default
        }
    }

    public int getMarketEndHour() {
        try {
            // Get trading hours from MongoDB no-trade zone filters
            if (scalpingEntryConfig != null && scalpingEntryConfig.getNoTradeZones() != null) {
                ScalpingEntryConfig.NoTradeFilter tradingHoursFilter = 
                        scalpingEntryConfig.getNoTradeZones().getFilters().get("tradingHours");
                if (tradingHoursFilter != null && tradingHoursFilter.getEndHour() != null) {
                    return tradingHoursFilter.getEndHour();
                }
            }
            return 15; // Default
        } catch (Exception e) {
            log.error("Error getting market end hour: {}", e.getMessage(), e);
            return 15; // Default
        }
    }

    public int getMarketEndMinute() {
        try {
            // Get trading hours from MongoDB no-trade zone filters
            if (scalpingEntryConfig != null && scalpingEntryConfig.getNoTradeZones() != null) {
                ScalpingEntryConfig.NoTradeFilter tradingHoursFilter = 
                        scalpingEntryConfig.getNoTradeZones().getFilters().get("tradingHours");
                if (tradingHoursFilter != null && tradingHoursFilter.getEndMinute() != null) {
                    return tradingHoursFilter.getEndMinute();
                }
            }
            return 30; // Default
        } catch (Exception e) {
            log.error("Error getting market end minute: {}", e.getMessage(), e);
            return 30; // Default
        }
    }

    /**
     * Refresh configuration from MongoDB
     * This method can be called to reload the configurable sections from MongoDB
     */
    public void refreshConfigurationFromMongoDB() {
        try {
            log.info("Refreshing configuration from MongoDB...");
            
            // Reload configurable sections from MongoDB
            scalpingEntryConfig.setScenarios(mongoConfigurationService.getScenariosFromMongoDB());
            scalpingEntryConfig.setCategoryScoring(mongoConfigurationService.getCategoryScoringFromMongoDB());
            scalpingEntryConfig.setNoTradeZones(mongoConfigurationService.getNoTradeZonesFromMongoDB());
            
            log.info("Configuration refreshed successfully from MongoDB");
        } catch (Exception e) {
            log.error("Failed to refresh configuration from MongoDB", e);
            throw new RuntimeException("Failed to refresh configuration from MongoDB", e);
        }
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
        private double minMilestonePoints;
        private double maxMilestonePoints;
        private RsiThresholds rsiThresholds;
        private double volumeSurgeMultiplier;
        private double signalStrength;
        private double stopLossPercentage;
        private double targetPercentage;
        private double stopLossPoints;
        private double targetPoints;
    }

    @Data
    public static class RsiThresholds {
        private double callRsiThreshold;
        private double putRsiThreshold;
        private int rsiMaPeriod;
        private boolean enableRsiMaComparison;
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

    // Scoring Configuration Data Classes
    @Data
    public static class ScoringConfig {
        private ConfidenceScoring confidenceScoring;
        private EmaScoring emaScoring;
        private RsiScoring rsiScoring;
        private VolumeScoring volumeScoring;
        private PriceActionScoring priceActionScoring;
        private FuturesignalScoring futuresignalScoring;
        private MomentumScoring momentumScoring;
        private QualityScoring qualityScoring;
    }

    @Data
    public static class ThresholdConfig {
        private ScoringRsiThresholds rsiThresholds;
        private VolumeThresholds volumeThresholds;
        private PriceThresholds priceThresholds;
        private QualityThresholds qualityThresholds;
    }

    @Data
    public static class TimingConfig {
        private long entryCooldownMs;
        private int minDataBars;
        private String description;
    }

    // Scoring classes
    @Data
    public static class ConfidenceScoring {
        private double emaConfidence;
        private double rsiConfidence;
        private double volumeConfidence;
        private double priceActionConfidence;
        private double resistanceConfidence;
        private double maxConfidence;
        private String description;
    }

    @Data
    public static class EmaScoring {
        private double bullishScore;
        private double bearishScore;
        private double maxScore;
        private double minScore;
        private String description;
    }

    @Data
    public static class RsiScoring {
        private double bullishScore;
        private double bearishScore;
        private double maxScore;
        private double minScore;
        private String description;
    }

    @Data
    public static class VolumeScoring {
        private double volume5minPoints;
        private double volume1minPoints;
        private VolumeMultiplierThresholds volumeMultiplierThresholds;
        private double maxScore;
        private double minScore;
        private String description;
    }

    @Data
    public static class PriceActionScoring {
        private double vwapBullishScore;
        private double vwapBearishScore;
        private double resistanceScore;
        private double supportScore;
        private BreakoutStrength breakoutStrength;
        private BreakdownStrength breakdownStrength;
        private double maxScore;
        private double minScore;
        private String description;
    }

    @Data
    public static class FuturesignalScoring {
        private double bullishScore;
        private double bearishScore;
        private double maxScore;
        private double minScore;
        private String description;
    }

    @Data
    public static class MomentumScoring {
        private double weight;
        private double perfectAlignmentBonus;
        private double majorityAlignmentBonus;
        private double rsiAlignmentBonus;
        private double maxScore;
        private double minScore;
        private String description;
    }

    @Data
    public static class QualityScoring {
        private double emaQuality;
        private double rsiQuality;
        private VolumeQuality volumeQuality;
        private double priceActionQuality;
        private double futuresignalQuality;
        private MomentumQuality momentumQuality;
        private CandlestickQuality candlestickQuality;
        private double minQualityThreshold;
        private String description;
    }

    // Threshold classes
    @Data
    public static class ScoringRsiThresholds {
        private double callBullish;
        private double putBearish;
        private double overbought;
        private double oversold;
        private int rsiMaPeriod;
        private boolean enableRsiMaComparison;
        private double tolerance;
        private String description;
    }

    @Data
    public static class VolumeThresholds {
        private double surgeMultiplier;
        private double highVolumeMultiplier;
        private double veryHighVolumeMultiplier;
        private String description;
    }

    @Data
    public static class PriceThresholds {
        private double supportResistanceTolerance;
        private double breakoutThreshold;
        private double breakdownThreshold;
        private double emaTouchTolerance;
        private String description;
    }

    @Data
    public static class QualityThresholds {
        private double minConfidenceScore;
        private double minQualityScore;
        private double minCandlestickScore;
        private String description;
    }

    // Supporting classes
    @Data
    public static class VolumeMultiplierThresholds {
        private VolumeThreshold veryHigh;
        private VolumeThreshold high;
        private VolumeThreshold standard;
    }

    @Data
    public static class VolumeThreshold {
        private double threshold;
        private double bonus;

        public VolumeThreshold() {}

        public VolumeThreshold(double threshold, double bonus) {
            this.threshold = threshold;
            this.bonus = bonus;
        }
    }

    @Data
    public static class BreakoutStrength {
        private BreakoutThreshold strongBreakout;
        private BreakoutThreshold standardBreakout;
    }

    @Data
    public static class BreakdownStrength {
        private BreakoutThreshold strongBreakdown;
        private BreakoutThreshold standardBreakdown;
    }

    @Data
    public static class BreakoutThreshold {
        private double threshold;
        private double bonus;

        public BreakoutThreshold() {}

        public BreakoutThreshold(double threshold, double bonus) {
            this.threshold = threshold;
            this.bonus = bonus;
        }
    }

    @Data
    public static class VolumeQuality {
        private double volume5min;
        private double volume1min;
        private double volumeMultiplier;
        private double volumeMultiplierThreshold;
    }

    @Data
    public static class MomentumQuality {
        private double perfectAlignment;
        private double majorityAlignment;
        private double singleAlignment;
        private double rsiDivergenceBonus;
    }

    @Data
    public static class CandlestickQuality {
        private double highReliability;
        private double mediumReliability;
        private double lowReliability;
        private double maxScore;
    }
}
