package com.jtradebot.processor.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtradebot.processor.model.strategy.ScalpingEntryConfig;
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
    @Getter
    private ScoringConfig scoringConfig;
    @Getter
    private ThresholdConfig thresholdConfig;
    @Getter
    private TimingConfig timingConfig;
    @Getter
    private ScalpingEntryConfig scalpingEntryConfig;

    public TradingConfigurationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterPropertiesSet() {
        loadTradingConfiguration();
        loadScoringConfiguration();
        loadScalpingEntryConfiguration();
    }

    private void loadTradingConfiguration() {
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
            ClassPathResource entryResource = new ClassPathResource("rules/scalping-entry-config.json");
            JsonNode rootNode = objectMapper.readTree(entryResource.getInputStream());

            // Load scalping entry config
            scalpingEntryConfig = objectMapper.treeToValue(rootNode, ScalpingEntryConfig.class);

            log.info("Scalping entry configuration loaded successfully");
            log.info("Loaded {} scenarios: {}",
                    scalpingEntryConfig.getScenarios().size(),
                    scalpingEntryConfig.getScenarios().stream()
                            .map(ScalpingEntryConfig.Scenario::getName)
                            .toList());
        } catch (Exception e) {
            log.error("Failed to load scalping entry configuration", e);
            throw new RuntimeException("Failed to load scalping entry configuration", e);
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

    // Convenience methods for scoring configuration
    public double getEmaQuality() { return scoringConfig.getQualityScoring().getEmaQuality(); }
    public double getRsiQuality() { return scoringConfig.getQualityScoring().getRsiQuality(); }
    public double getPriceActionQuality() { return scoringConfig.getQualityScoring().getPriceActionQuality(); }
    public double getFuturesignalQuality() { return scoringConfig.getQualityScoring().getFuturesignalQuality(); }
    public double getMinQualityThreshold() { return scoringConfig.getQualityScoring().getMinQualityThreshold(); }

    // Convenience methods for volume thresholds
    public double getSurgeMultiplier() { return thresholdConfig.getVolumeThresholds().getSurgeMultiplier(); }
    public double getHighVolumeMultiplier() { return thresholdConfig.getVolumeThresholds().getHighVolumeMultiplier(); }

    // Convenience methods for quality thresholds
    public double getMinQualityScore() { return thresholdConfig.getQualityThresholds().getMinQualityScore(); }

    // Convenience methods for timing configuration
    public int getMinDataBars() { return timingConfig.getMinDataBars(); }

    // Convenience methods for scalping entry configuration
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

    public java.util.Map<String, ScalpingEntryConfig.NoTradeFilter> getNoTradeFilters() {
        return scalpingEntryConfig.getNoTradeZones() != null ?
                scalpingEntryConfig.getNoTradeZones().getFilters() : new java.util.HashMap<>();
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
        try {
            ClassPathResource resource = new ClassPathResource("rules/scalping-entry-config.json");
            JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
            
            JsonNode marketEndScheduler = rootNode.get("marketEndScheduler");
            if (marketEndScheduler != null && marketEndScheduler.has("enabled")) {
                return marketEndScheduler.get("enabled").asBoolean();
            }
            return true; // Default to enabled if not specified
        } catch (IOException e) {
            log.error("Error checking if market end scheduler is enabled: {}", e.getMessage(), e);
            return true; // Default to enabled on error
        }
    }

    // Trading hours configuration
    public int getMarketStartHour() {
        try {
            ClassPathResource resource = new ClassPathResource("rules/scalping-entry-config.json");
            JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
            
            JsonNode tradingHours = rootNode.get("noTradeZones").get("filters").get("tradingHours");
            if (tradingHours != null && tradingHours.has("startHour")) {
                return tradingHours.get("startHour").asInt();
            }
            return 9; // Default
        } catch (IOException e) {
            log.error("Error getting market start hour: {}", e.getMessage(), e);
            return 9; // Default
        }
    }

    public int getMarketStartMinute() {
        try {
            ClassPathResource resource = new ClassPathResource("rules/scalping-entry-config.json");
            JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
            
            JsonNode tradingHours = rootNode.get("noTradeZones").get("filters").get("tradingHours");
            if (tradingHours != null && tradingHours.has("startMinute")) {
                return tradingHours.get("startMinute").asInt();
            }
            return 15; // Default
        } catch (IOException e) {
            log.error("Error getting market start minute: {}", e.getMessage(), e);
            return 15; // Default
        }
    }

    public int getMarketEndHour() {
        try {
            ClassPathResource resource = new ClassPathResource("rules/scalping-entry-config.json");
            JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
            
            JsonNode tradingHours = rootNode.get("noTradeZones").get("filters").get("tradingHours");
            if (tradingHours != null && tradingHours.has("endHour")) {
                return tradingHours.get("endHour").asInt();
            }
            return 15; // Default
        } catch (IOException e) {
            log.error("Error getting market end hour: {}", e.getMessage(), e);
            return 15; // Default
        }
    }

    public int getMarketEndMinute() {
        try {
            ClassPathResource resource = new ClassPathResource("rules/scalping-entry-config.json");
            JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
            
            JsonNode tradingHours = rootNode.get("noTradeZones").get("filters").get("tradingHours");
            if (tradingHours != null && tradingHours.has("endMinute")) {
                return tradingHours.get("endMinute").asInt();
            }
            return 30; // Default
        } catch (IOException e) {
            log.error("Error getting market end minute: {}", e.getMessage(), e);
            return 30; // Default
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
