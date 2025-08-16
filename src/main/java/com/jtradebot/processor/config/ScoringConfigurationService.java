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
public class ScoringConfigurationService implements InitializingBean {

    private final ObjectMapper objectMapper;
    private ScoringConfig scoringConfig;
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
                log.warn("No scoringConfiguration found in JSON, using default values");
                scoringConfig = getDefaultScoringConfig();
            }
            
            if (rootNode.has("thresholdConfiguration")) {
                JsonNode thresholdNode = rootNode.get("thresholdConfiguration");
                thresholdConfig = objectMapper.treeToValue(thresholdNode, ThresholdConfig.class);
                log.info("Threshold configuration loaded successfully");
            } else {
                log.warn("No thresholdConfiguration found in JSON, using default values");
                thresholdConfig = getDefaultThresholdConfig();
            }
            
            if (rootNode.has("timingConfiguration")) {
                JsonNode timingNode = rootNode.get("timingConfiguration");
                timingConfig = objectMapper.treeToValue(timingNode, TimingConfig.class);
                log.info("Timing configuration loaded successfully");
            } else {
                log.warn("No timingConfiguration found in JSON, using default values");
                timingConfig = getDefaultTimingConfig();
            }
            
        } catch (IOException e) {
            log.error("Failed to load configuration from JSON, using default values", e);
            scoringConfig = getDefaultScoringConfig();
            thresholdConfig = getDefaultThresholdConfig();
            timingConfig = getDefaultTimingConfig();
        }
    }

    private ScoringConfig getDefaultScoringConfig() {
        ScoringConfig config = new ScoringConfig();
        
        // Confidence scoring
        ConfidenceScoring confidenceScoring = new ConfidenceScoring();
        confidenceScoring.setEmaConfidence(0.2);
        confidenceScoring.setRsiConfidence(0.2);
        confidenceScoring.setVolumeConfidence(0.2);
        confidenceScoring.setPriceActionConfidence(0.2);
        confidenceScoring.setResistanceConfidence(0.2);
        confidenceScoring.setMaxConfidence(1.0);
        config.setConfidenceScoring(confidenceScoring);
        
        // EMA scoring
        EmaScoring emaScoring = new EmaScoring();
        emaScoring.setBullishScore(1.5);
        emaScoring.setBearishScore(-1.5);
        emaScoring.setMaxScore(3.0);
        emaScoring.setMinScore(-3.0);
        config.setEmaScoring(emaScoring);
        
        // RSI scoring
        RsiScoring rsiScoring = new RsiScoring();
        rsiScoring.setBullishScore(1.5);
        rsiScoring.setBearishScore(-1.5);
        rsiScoring.setMaxScore(3.0);
        rsiScoring.setMinScore(-3.0);
        config.setRsiScoring(rsiScoring);
        
        // Volume scoring
        VolumeScoring volumeScoring = new VolumeScoring();
        volumeScoring.setVolume5minPoints(2.0);
        volumeScoring.setVolume1minPoints(1.0);
        volumeScoring.setMaxScore(5.0);
        volumeScoring.setMinScore(-5.0);
        
        VolumeMultiplierThresholds volumeThresholds = new VolumeMultiplierThresholds();
        volumeThresholds.setVeryHigh(new VolumeThreshold(3.0, 2.0));
        volumeThresholds.setHigh(new VolumeThreshold(2.0, 1.0));
        volumeThresholds.setStandard(new VolumeThreshold(1.5, 0.5));
        volumeScoring.setVolumeMultiplierThresholds(volumeThresholds);
        config.setVolumeScoring(volumeScoring);
        
        // Price action scoring
        PriceActionScoring priceActionScoring = new PriceActionScoring();
        priceActionScoring.setVwapBullishScore(1.5);
        priceActionScoring.setVwapBearishScore(-1.5);
        priceActionScoring.setResistanceScore(1.5);
        priceActionScoring.setSupportScore(-1.5);
        priceActionScoring.setMaxScore(5.0);
        priceActionScoring.setMinScore(-5.0);
        
        BreakoutStrength breakoutStrength = new BreakoutStrength();
        breakoutStrength.setStrongBreakout(new BreakoutThreshold(2.0, 2.0));
        breakoutStrength.setStandardBreakout(new BreakoutThreshold(0.0, 1.0));
        priceActionScoring.setBreakoutStrength(breakoutStrength);
        
        BreakdownStrength breakdownStrength = new BreakdownStrength();
        breakdownStrength.setStrongBreakdown(new BreakoutThreshold(2.0, 2.0));
        breakdownStrength.setStandardBreakdown(new BreakoutThreshold(0.0, 1.0));
        priceActionScoring.setBreakdownStrength(breakdownStrength);
        config.setPriceActionScoring(priceActionScoring);
        
        // Futuresignal scoring
        FuturesignalScoring futuresignalScoring = new FuturesignalScoring();
        futuresignalScoring.setBullishScore(2.0);
        futuresignalScoring.setBearishScore(-2.0);
        futuresignalScoring.setMaxScore(2.0);
        futuresignalScoring.setMinScore(-2.0);
        config.setFuturesignalScoring(futuresignalScoring);
        
        // Momentum scoring
        MomentumScoring momentumScoring = new MomentumScoring();
        momentumScoring.setWeight(0.15);
        momentumScoring.setPerfectAlignmentBonus(2.0);
        momentumScoring.setMajorityAlignmentBonus(1.0);
        momentumScoring.setRsiAlignmentBonus(1.0);
        momentumScoring.setMaxScore(3.0);
        momentumScoring.setMinScore(-3.0);
        config.setMomentumScoring(momentumScoring);
        
        // Quality scoring
        QualityScoring qualityScoring = new QualityScoring();
        qualityScoring.setEmaQuality(5.0);
        qualityScoring.setRsiQuality(5.0);
        qualityScoring.setPriceActionQuality(5.0);
        qualityScoring.setFuturesignalQuality(10.0);
        qualityScoring.setMinQualityThreshold(0.7);
        
        VolumeQuality volumeQuality = new VolumeQuality();
        volumeQuality.setVolume5min(5.0);
        volumeQuality.setVolume1min(3.0);
        volumeQuality.setVolumeMultiplier(2.0);
        volumeQuality.setVolumeMultiplierThreshold(3.0);
        qualityScoring.setVolumeQuality(volumeQuality);
        
        MomentumQuality momentumQuality = new MomentumQuality();
        momentumQuality.setPerfectAlignment(10.0);
        momentumQuality.setMajorityAlignment(7.0);
        momentumQuality.setSingleAlignment(3.0);
        qualityScoring.setMomentumQuality(momentumQuality);
        
        CandlestickQuality candlestickQuality = new CandlestickQuality();
        candlestickQuality.setHighReliability(3.0);
        candlestickQuality.setMediumReliability(2.0);
        candlestickQuality.setLowReliability(1.0);
        candlestickQuality.setMaxScore(10.0);
        qualityScoring.setCandlestickQuality(candlestickQuality);
        config.setQualityScoring(qualityScoring);
        
        return config;
    }

    private ThresholdConfig getDefaultThresholdConfig() {
        ThresholdConfig config = new ThresholdConfig();
        
        RsiThresholds rsiThresholds = new RsiThresholds();
        rsiThresholds.setCallBullish(56.0);
        rsiThresholds.setPutBearish(44.0);
        rsiThresholds.setOverbought(70.0);
        rsiThresholds.setOversold(30.0);
        rsiThresholds.setTolerance(0.1);
        config.setRsiThresholds(rsiThresholds);
        
        VolumeThresholds volumeThresholds = new VolumeThresholds();
        volumeThresholds.setSurgeMultiplier(1.5);
        volumeThresholds.setHighVolumeMultiplier(2.0);
        volumeThresholds.setVeryHighVolumeMultiplier(3.0);
        config.setVolumeThresholds(volumeThresholds);
        
        PriceThresholds priceThresholds = new PriceThresholds();
        priceThresholds.setSupportResistanceTolerance(0.01);
        priceThresholds.setBreakoutThreshold(0.99);
        priceThresholds.setBreakdownThreshold(1.01);
        priceThresholds.setEmaTouchTolerance(0.001);
        config.setPriceThresholds(priceThresholds);
        
        QualityThresholds qualityThresholds = new QualityThresholds();
        qualityThresholds.setMinConfidenceScore(0.7);
        qualityThresholds.setMinQualityScore(0.7);
        qualityThresholds.setMinCandlestickScore(4.0);
        config.setQualityThresholds(qualityThresholds);
        
        return config;
    }

    private TimingConfig getDefaultTimingConfig() {
        TimingConfig config = new TimingConfig();
        config.setEntryCooldownMs(30000);
        config.setMinDataBars(20);
        return config;
    }

    // Getters for scoring configuration
    public ScoringConfig getScoringConfig() { return scoringConfig; }
    public ThresholdConfig getThresholdConfig() { return thresholdConfig; }
    public TimingConfig getTimingConfig() { return timingConfig; }

    // Convenience methods for confidence scoring
    public double getEmaConfidence() { return scoringConfig.getConfidenceScoring().getEmaConfidence(); }
    public double getRsiConfidence() { return scoringConfig.getConfidenceScoring().getRsiConfidence(); }
    public double getVolumeConfidence() { return scoringConfig.getConfidenceScoring().getVolumeConfidence(); }
    public double getPriceActionConfidence() { return scoringConfig.getConfidenceScoring().getPriceActionConfidence(); }
    public double getResistanceConfidence() { return scoringConfig.getConfidenceScoring().getResistanceConfidence(); }
    public double getMaxConfidence() { return scoringConfig.getConfidenceScoring().getMaxConfidence(); }

    // Convenience methods for EMA scoring
    public double getEmaBullishScore() { return scoringConfig.getEmaScoring().getBullishScore(); }
    public double getEmaBearishScore() { return scoringConfig.getEmaScoring().getBearishScore(); }
    public double getEmaMaxScore() { return scoringConfig.getEmaScoring().getMaxScore(); }
    public double getEmaMinScore() { return scoringConfig.getEmaScoring().getMinScore(); }

    // Convenience methods for RSI scoring
    public double getRsiBullishScore() { return scoringConfig.getRsiScoring().getBullishScore(); }
    public double getRsiBearishScore() { return scoringConfig.getRsiScoring().getBearishScore(); }
    public double getRsiMaxScore() { return scoringConfig.getRsiScoring().getMaxScore(); }
    public double getRsiMinScore() { return scoringConfig.getRsiScoring().getMinScore(); }

    // Convenience methods for volume scoring
    public double getVolume5minPoints() { return scoringConfig.getVolumeScoring().getVolume5minPoints(); }
    public double getVolume1minPoints() { return scoringConfig.getVolumeScoring().getVolume1minPoints(); }
    public double getVolumeMaxScore() { return scoringConfig.getVolumeScoring().getMaxScore(); }
    public double getVolumeMinScore() { return scoringConfig.getVolumeScoring().getMinScore(); }

    // Convenience methods for price action scoring
    public double getVwapBullishScore() { return scoringConfig.getPriceActionScoring().getVwapBullishScore(); }
    public double getVwapBearishScore() { return scoringConfig.getPriceActionScoring().getVwapBearishScore(); }
    public double getResistanceScore() { return scoringConfig.getPriceActionScoring().getResistanceScore(); }
    public double getSupportScore() { return scoringConfig.getPriceActionScoring().getSupportScore(); }
    public double getPriceActionMaxScore() { return scoringConfig.getPriceActionScoring().getMaxScore(); }
    public double getPriceActionMinScore() { return scoringConfig.getPriceActionScoring().getMinScore(); }

    // Convenience methods for futuresignal scoring
    public double getFuturesignalBullishScore() { return scoringConfig.getFuturesignalScoring().getBullishScore(); }
    public double getFuturesignalBearishScore() { return scoringConfig.getFuturesignalScoring().getBearishScore(); }
    public double getFuturesignalMaxScore() { return scoringConfig.getFuturesignalScoring().getMaxScore(); }
    public double getFuturesignalMinScore() { return scoringConfig.getFuturesignalScoring().getMinScore(); }

    // Convenience methods for momentum scoring
    public double getMomentumWeight() { return scoringConfig.getMomentumScoring().getWeight(); }
    public double getPerfectAlignmentBonus() { return scoringConfig.getMomentumScoring().getPerfectAlignmentBonus(); }
    public double getMajorityAlignmentBonus() { return scoringConfig.getMomentumScoring().getMajorityAlignmentBonus(); }
    public double getRsiAlignmentBonus() { return scoringConfig.getMomentumScoring().getRsiAlignmentBonus(); }
    public double getMomentumMaxScore() { return scoringConfig.getMomentumScoring().getMaxScore(); }
    public double getMomentumMinScore() { return scoringConfig.getMomentumScoring().getMinScore(); }

    // Convenience methods for quality scoring
    public double getEmaQuality() { return scoringConfig.getQualityScoring().getEmaQuality(); }
    public double getRsiQuality() { return scoringConfig.getQualityScoring().getRsiQuality(); }
    public double getPriceActionQuality() { return scoringConfig.getQualityScoring().getPriceActionQuality(); }
    public double getFuturesignalQuality() { return scoringConfig.getQualityScoring().getFuturesignalQuality(); }
    public double getMinQualityThreshold() { return scoringConfig.getQualityScoring().getMinQualityThreshold(); }

    // Convenience methods for RSI thresholds
    public double getCallBullishRsi() { return thresholdConfig.getRsiThresholds().getCallBullish(); }
    public double getPutBearishRsi() { return thresholdConfig.getRsiThresholds().getPutBearish(); }
    public double getOverboughtRsi() { return thresholdConfig.getRsiThresholds().getOverbought(); }
    public double getOversoldRsi() { return thresholdConfig.getRsiThresholds().getOversold(); }
    public double getRsiTolerance() { return thresholdConfig.getRsiThresholds().getTolerance(); }

    // Convenience methods for volume thresholds
    public double getSurgeMultiplier() { return thresholdConfig.getVolumeThresholds().getSurgeMultiplier(); }
    public double getHighVolumeMultiplier() { return thresholdConfig.getVolumeThresholds().getHighVolumeMultiplier(); }
    public double getVeryHighVolumeMultiplier() { return thresholdConfig.getVolumeThresholds().getVeryHighVolumeMultiplier(); }

    // Convenience methods for price thresholds
    public double getSupportResistanceTolerance() { return thresholdConfig.getPriceThresholds().getSupportResistanceTolerance(); }
    public double getBreakoutThreshold() { return thresholdConfig.getPriceThresholds().getBreakoutThreshold(); }
    public double getBreakdownThreshold() { return thresholdConfig.getPriceThresholds().getBreakdownThreshold(); }
    public double getEmaTouchTolerance() { return thresholdConfig.getPriceThresholds().getEmaTouchTolerance(); }

    // Convenience methods for quality thresholds
    public double getMinConfidenceScore() { return thresholdConfig.getQualityThresholds().getMinConfidenceScore(); }
    public double getMinQualityScore() { return thresholdConfig.getQualityThresholds().getMinQualityScore(); }
    public double getMinCandlestickScore() { return thresholdConfig.getQualityThresholds().getMinCandlestickScore(); }

    // Convenience methods for timing configuration
    public long getEntryCooldownMs() { return timingConfig.getEntryCooldownMs(); }
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
