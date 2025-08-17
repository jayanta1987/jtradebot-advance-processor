package com.jtradebot.processor.service;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.config.ScoringConfigurationService;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.strategy.ScalpingEntryDecision;
import com.jtradebot.processor.service.impl.ScalpingEntryServiceImpl;
import com.zerodhatech.models.Tick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScalpingEntryLogicDebugTest {
    
    @Mock
    private DynamicStrategyConfigService configService;
    
    @Mock
    private ScoringConfigurationService scoringConfigService;
    
    private ScalpingEntryServiceImpl scalpingEntryService;
    
    @BeforeEach
    void setUp() {
        scalpingEntryService = new ScalpingEntryServiceImpl(configService, scoringConfigService);
        
        // Mock the configuration to return the scenarios
        when(configService.getScenarios()).thenReturn(java.util.List.of(
            createHighQualityScoreScenario()
        ));
        
        when(configService.getCategories()).thenReturn(java.util.Map.of(
            "ema", java.util.List.of("ema5_5min_gt_ema34_5min", "ema5_1min_gt_ema34_1min"),
            "futureAndVolume", java.util.List.of("volume_5min_surge", "price_gt_vwap_5min"),
            "candlestick", java.util.List.of("green_candle_5min", "green_candle_1min"),
            "momentum", java.util.List.of("rsi_5min_gt_56", "rsi_1min_gt_56")
        ));
        
        // Mock scoring configuration
        setupScoringConfigMocks();
    }
    
    @Test
    void testHighQualityScoreScenarioWithLowScore() {
        // Given - Create indicators with low quality score
        FlattenedIndicators indicators = createLowQualityIndicators();
        Tick tick = createMockTick();
        
        // When
        ScalpingEntryDecision decision = scalpingEntryService.evaluateEntry(tick, indicators);
        
        // Then - Should NOT trigger entry with low quality score
        System.out.println("Decision: " + decision);
        System.out.println("Should Entry: " + decision.isShouldEntry());
        System.out.println("Scenario: " + decision.getScenarioName());
        System.out.println("Confidence: " + decision.getConfidence());
        System.out.println("Reason: " + decision.getReason());
        
        // The decision should be FALSE because quality score is below 8.0
        assertFalse(decision.isShouldEntry(), "Should not enter with low quality score");
        assertNull(decision.getScenarioName(), "Should not have scenario name when entry is false");
    }
    
    @Test
    void testHighQualityScoreScenarioWithHighScore() {
        // Given - Create indicators with high quality score
        FlattenedIndicators indicators = createHighQualityIndicators();
        Tick tick = createMockTick();
        
        // When
        ScalpingEntryDecision decision = scalpingEntryService.evaluateEntry(tick, indicators);
        
        // Then - Should trigger entry with high quality score
        System.out.println("Decision: " + decision);
        System.out.println("Should Entry: " + decision.isShouldEntry());
        System.out.println("Scenario: " + decision.getScenarioName());
        System.out.println("Confidence: " + decision.getConfidence());
        System.out.println("Reason: " + decision.getReason());
        
        // The decision should be TRUE because quality score is above 8.0
        assertTrue(decision.isShouldEntry(), "Should enter with high quality score");
        assertEquals("HIGH_QUALITY_SCORE_SIGNAL", decision.getScenarioName());
        assertTrue(decision.getConfidence() >= 8.0, "Confidence should be >= 8.0");
    }
    
    private com.jtradebot.processor.model.strategy.ScalpingEntryConfig.Scenario createHighQualityScoreScenario() {
        com.jtradebot.processor.model.strategy.ScalpingEntryConfig.Scenario scenario = 
            new com.jtradebot.processor.model.strategy.ScalpingEntryConfig.Scenario();
        scenario.setName("HIGH_QUALITY_SCORE_SIGNAL");
        scenario.setDescription("Only depends on QualityScore");
        
        com.jtradebot.processor.model.strategy.ScalpingEntryConfig.ScenarioRequirements requirements = 
            new com.jtradebot.processor.model.strategy.ScalpingEntryConfig.ScenarioRequirements();
        requirements.setMinQualityScore(8.0);
        scenario.setRequirements(requirements);
        
        return scenario;
    }
    
    private FlattenedIndicators createLowQualityIndicators() {
        FlattenedIndicators indicators = new FlattenedIndicators();
        
        // Set only a few indicators to true to get low quality score
        indicators.setPrice_gt_vwap_5min(true);
        indicators.setGreen_candle_1min(true);
        
        // Most indicators should be false/null to get low score
        indicators.setEma5_5min_gt_ema34_5min(false);
        indicators.setEma5_1min_gt_ema34_1min(false);
        indicators.setRsi_5min_gt_56(false);
        indicators.setRsi_1min_gt_56(false);
        indicators.setVolume_5min_surge(false);
        indicators.setGreen_candle_5min(false);
        
        return indicators;
    }
    
    private FlattenedIndicators createHighQualityIndicators() {
        FlattenedIndicators indicators = new FlattenedIndicators();
        
        // Set many indicators to true to get high quality score
        indicators.setEma5_5min_gt_ema34_5min(true);
        indicators.setEma5_1min_gt_ema34_1min(true);
        indicators.setRsi_5min_gt_56(true);
        indicators.setRsi_1min_gt_56(true);
        indicators.setVolume_5min_surge(true);
        indicators.setPrice_gt_vwap_5min(true);
        indicators.setGreen_candle_5min(true);
        indicators.setGreen_candle_1min(true);
        
        return indicators;
    }
    
    private Tick createMockTick() {
        Tick tick = new Tick();
        tick.setInstrumentToken(256265L);
        tick.setLastTradedPrice(24745.75);
        return tick;
    }
    
    private void setupScoringConfigMocks() {
        // Mock the scoring configuration to return reasonable values
        when(scoringConfigService.getEmaQuality()).thenReturn(5.0);
        when(scoringConfigService.getRsiQuality()).thenReturn(5.0);
        when(scoringConfigService.getPriceActionQuality()).thenReturn(5.0);
        when(scoringConfigService.getFuturesignalQuality()).thenReturn(10.0);
        
        // Mock nested objects
        com.jtradebot.processor.config.ScoringConfigurationService.ScoringConfig mockScoringConfig = 
            mock(com.jtradebot.processor.config.ScoringConfigurationService.ScoringConfig.class);
        when(scoringConfigService.getScoringConfig()).thenReturn(mockScoringConfig);
        
        com.jtradebot.processor.config.ScoringConfigurationService.QualityScoring mockQualityScoring = 
            mock(com.jtradebot.processor.config.ScoringConfigurationService.QualityScoring.class);
        when(mockScoringConfig.getQualityScoring()).thenReturn(mockQualityScoring);
        
        com.jtradebot.processor.config.ScoringConfigurationService.VolumeQuality mockVolumeQuality = 
            mock(com.jtradebot.processor.config.ScoringConfigurationService.VolumeQuality.class);
        when(mockQualityScoring.getVolumeQuality()).thenReturn(mockVolumeQuality);
        when(mockVolumeQuality.getVolume5min()).thenReturn(5.0);
        when(mockVolumeQuality.getVolume1min()).thenReturn(3.0);
        
        com.jtradebot.processor.config.ScoringConfigurationService.CandlestickQuality mockCandlestickQuality = 
            mock(com.jtradebot.processor.config.ScoringConfigurationService.CandlestickQuality.class);
        when(mockQualityScoring.getCandlestickQuality()).thenReturn(mockCandlestickQuality);
        when(mockCandlestickQuality.getMediumReliability()).thenReturn(2.0);
        when(mockCandlestickQuality.getLowReliability()).thenReturn(1.0);
        when(mockCandlestickQuality.getMaxScore()).thenReturn(10.0);
    }
}
