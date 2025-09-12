package com.jtradebot.processor.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScoringConfigurationService {

    private final TradingConfigurationService tradingConfigurationService;

    @Getter
    private TradingConfigurationService.ScoringConfig scoringConfig;
    @Getter
    private TradingConfigurationService.ThresholdConfig thresholdConfig;
    private TradingConfigurationService.TimingConfig timingConfig;

    @PostConstruct
    public void initialize() {
        scoringConfig = tradingConfigurationService.getScoringConfig();
        thresholdConfig = tradingConfigurationService.getThresholdConfig();
        timingConfig = tradingConfigurationService.getTimingConfig();
        log.info("Scoring configuration service initialized using TradingConfigurationService");
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
}
