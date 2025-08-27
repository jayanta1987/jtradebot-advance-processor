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
public class ScoringConfigurationService implements InitializingBean {

    private final ObjectMapper objectMapper;

    @Getter
    private ScoringConfig scoringConfig;
    @Getter
    private ThresholdConfig thresholdConfig;
    private TimingConfig timingConfig;

    public ScoringConfigurationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterPropertiesSet() {
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


    // Convenience methods for quality scoring
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

    // Data classes
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
        private RsiThresholds rsiThresholds;
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
    public static class RsiThresholds {
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
    }

    @Data
    public static class CandlestickQuality {
        private double highReliability;
        private double mediumReliability;
        private double lowReliability;
        private double maxScore;
    }
}
