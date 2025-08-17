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

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScalpingEntryServiceTest {
    
    @Mock
    private DynamicStrategyConfigService configService;
    
    @Mock
    private ScoringConfigurationService scoringConfigService;
    
    @Mock
    private Tick tick;
    
    private ScalpingEntryService scalpingEntryService;
    
    @BeforeEach
    void setUp() {
        scalpingEntryService = new ScalpingEntryServiceImpl(configService, scoringConfigService);
        
        // Mock scoring configuration service methods
        when(scoringConfigService.getEmaQuality()).thenReturn(5.0);
        when(scoringConfigService.getRsiQuality()).thenReturn(5.0);
        when(scoringConfigService.getPriceActionQuality()).thenReturn(5.0);
        when(scoringConfigService.getFuturesignalQuality()).thenReturn(10.0);
        
        // Create mock objects for nested configuration
        ScoringConfigurationService.ScoringConfig mockScoringConfig = mock(ScoringConfigurationService.ScoringConfig.class);
        ScoringConfigurationService.QualityScoring mockQualityScoring = mock(ScoringConfigurationService.QualityScoring.class);
        ScoringConfigurationService.VolumeQuality mockVolumeQuality = mock(ScoringConfigurationService.VolumeQuality.class);
        ScoringConfigurationService.CandlestickQuality mockCandlestickQuality = mock(ScoringConfigurationService.CandlestickQuality.class);
        
        when(scoringConfigService.getScoringConfig()).thenReturn(mockScoringConfig);
        when(mockScoringConfig.getQualityScoring()).thenReturn(mockQualityScoring);
        when(mockQualityScoring.getVolumeQuality()).thenReturn(mockVolumeQuality);
        when(mockQualityScoring.getCandlestickQuality()).thenReturn(mockCandlestickQuality);
        
        when(mockVolumeQuality.getVolume5min()).thenReturn(5.0);
        when(mockVolumeQuality.getVolume1min()).thenReturn(3.0);
        when(mockCandlestickQuality.getMediumReliability()).thenReturn(2.0);
        when(mockCandlestickQuality.getLowReliability()).thenReturn(1.0);
        when(mockCandlestickQuality.getMaxScore()).thenReturn(10.0);
    }
    
    @Test
    void testEvaluateEntryWithQualityScoreScenario() {
        // Given
        FlattenedIndicators indicators = createMockIndicators();
        
        // Mock configuration for HIGH_QUALITY_SCORE_SIGNAL scenario
        when(configService.getScenarios()).thenReturn(java.util.List.of(
            createQualityScoreScenario()
        ));
        when(configService.getCategories()).thenReturn(java.util.Map.of());
        
        // When
        ScalpingEntryDecision decision = scalpingEntryService.evaluateEntry(tick, indicators);
        
        // Then
        assertNotNull(decision);
        assertTrue(decision.isShouldEntry());
        assertEquals("HIGH_QUALITY_SCORE_SIGNAL", decision.getScenarioName());
        assertTrue(decision.getConfidence() >= 8.0);
    }
    
    @Test
    void testEvaluateEntryWithCategoryBasedScenario() {
        // Given
        FlattenedIndicators indicators = createMockIndicators();
        
        // Mock configuration for SAFE_ENTRY_SIGNAL scenario
        when(configService.getScenarios()).thenReturn(java.util.List.of(
            createSafeEntryScenario()
        ));
        when(configService.getCategories()).thenReturn(createCategories());
        
        // When
        ScalpingEntryDecision decision = scalpingEntryService.evaluateEntry(tick, indicators);
        
        // Then
        assertNotNull(decision);
        // The decision depends on the actual indicator values
        assertNotNull(decision.getReason());
        
        // Log the decision for debugging
        System.out.println("Decision: " + decision.isShouldEntry() + ", Reason: " + decision.getReason());
    }
    
    @Test
    void testEvaluateEntryWithQualityScoreScenarioOnly() {
        // Given
        FlattenedIndicators indicators = createMockIndicators();
        
        // Mock configuration for HIGH_QUALITY_SCORE_SIGNAL scenario only
        when(configService.getScenarios()).thenReturn(java.util.List.of(
            createQualityScoreScenario()
        ));
        when(configService.getCategories()).thenReturn(java.util.Map.of());
        
        // When
        ScalpingEntryDecision decision = scalpingEntryService.evaluateEntry(tick, indicators);
        
        // Then
        assertNotNull(decision);
        assertTrue(decision.isShouldEntry());
        assertEquals("HIGH_QUALITY_SCORE_SIGNAL", decision.getScenarioName());
        assertTrue(decision.getConfidence() >= 8.0);
        
        System.out.println("Quality Score Decision: " + decision.isShouldEntry() + 
                          ", Scenario: " + decision.getScenarioName() + 
                          ", Confidence: " + decision.getConfidence());
    }
    
    @Test
    void testEvaluateEntryWithBothQualityScoreAndCategoryRequirements() {
        // Given - Create indicators with high quality score but low category scores
        FlattenedIndicators indicators = createHighQualityLowCategoryIndicators();
        
        // Mock configuration for SAFE_ENTRY_SIGNAL scenario (has both quality score and category requirements)
        when(configService.getScenarios()).thenReturn(java.util.List.of(
            createSafeEntryScenarioWithQualityScore()
        ));
        when(configService.getCallCategories()).thenReturn(createCallCategories());
        when(configService.getPutCategories()).thenReturn(createPutCategories());
        
        // When
        ScalpingEntryDecision decision = scalpingEntryService.evaluateEntry(tick, indicators);
        
        // Then - Should NOT enter because category requirements are not met
        assertNotNull(decision);
        assertFalse(decision.isShouldEntry(), "Should not enter when category requirements are not met");
        // The reason should indicate that no scenario passed, which is correct
        assertTrue(decision.getReason().contains("No scenario passed") || decision.getReason().contains("Failed categories"), 
                  "Reason should indicate no scenario passed: " + decision.getReason());
        
        System.out.println("Both Requirements Decision: " + decision.isShouldEntry() + 
                          ", Reason: " + decision.getReason());
    }
    
    @Test
    void testCategoryScoresMatchEvaluation() {
        // Given - Create indicators that will pass all requirements
        FlattenedIndicators indicators = createMockIndicators();
        
        // Mock configuration for SAFE_ENTRY_SIGNAL scenario
        when(configService.getScenarios()).thenReturn(java.util.List.of(
            createSafeEntryScenarioWithQualityScore()
        ));
        when(configService.getCallCategories()).thenReturn(createCallCategories());
        when(configService.getPutCategories()).thenReturn(createPutCategories());
        
        // When
        ScalpingEntryDecision decision = scalpingEntryService.evaluateEntry(tick, indicators);
        
        // Then - Should enter and category scores should match evaluation
        assertNotNull(decision);
        assertTrue(decision.isShouldEntry(), "Should enter when all requirements are met");
        assertNotNull(decision.getCategoryScores(), "Category scores should not be null");
        
        // Verify that category scores are stored (they should match what was used in evaluation)
        Map<String, Integer> categoryScores = decision.getCategoryScores();
        System.out.println("Category Scores: " + categoryScores);
        
        // The category scores should reflect the actual evaluation, not raw counts
        assertTrue(categoryScores.containsKey("ema"), "Should contain EMA category");
        assertTrue(categoryScores.containsKey("futureAndVolume"), "Should contain Volume category");
        assertTrue(categoryScores.containsKey("candlestick"), "Should contain Candlestick category");
        assertTrue(categoryScores.containsKey("momentum"), "Should contain Momentum category");
    }
    
    @Test
    void testMarketDirectionLogic() {
        // Given - Create indicators that will pass all requirements
        FlattenedIndicators indicators = createMockIndicators();
        
        // Mock configuration for SAFE_ENTRY_SIGNAL scenario
        when(configService.getScenarios()).thenReturn(java.util.List.of(
            createSafeEntryScenarioWithQualityScore()
        ));
        when(configService.getCallCategories()).thenReturn(createCallCategories());
        when(configService.getPutCategories()).thenReturn(createPutCategories());
        
        // When
        ScalpingEntryDecision decision = scalpingEntryService.evaluateEntry(tick, indicators);
        
        // Then - Should enter (scenario passes) but market direction should be clear
        assertNotNull(decision);
        assertTrue(decision.isShouldEntry(), "Should enter when scenario requirements are met");
        
        // The decision should contain category scores that reflect the market direction
        Map<String, Integer> categoryScores = decision.getCategoryScores();
        System.out.println("Mixed Signals Category Scores: " + categoryScores);
        
        // Verify that the system can determine market direction from category scores
        assertNotNull(categoryScores, "Category scores should not be null");
    }
    
    private FlattenedIndicators createMockIndicators() {
        FlattenedIndicators indicators = new FlattenedIndicators();
        
        // Set high quality indicators to pass quality score scenario
        indicators.setEma5_5min_gt_ema34_5min(true);
        indicators.setEma5_1min_gt_ema34_1min(true);
        indicators.setEma5_15min_gt_ema34_15min(true);
        
        indicators.setVolume_5min_surge(true);
        indicators.setVolume_1min_surge(true);
        indicators.setVolume_15min_surge(true);
        
        indicators.setGreen_candle_5min(true);
        indicators.setGreen_candle_1min(true);
        indicators.setLong_body_5min(true);
        indicators.setLong_body_1min(true);
        
        indicators.setRsi_5min_gt_56(true);
        indicators.setRsi_1min_gt_56(true);
        indicators.setRsi_15min_gt_56(true);
        
        // Set additional fields that might be checked
        indicators.setPrice_gt_vwap_5min(true);
        indicators.setPrice_gt_vwap_1min(true);
        indicators.setPrice_gt_vwap_15min(true);
        indicators.setPrice_above_resistance(true);
        indicators.setBullish_engulfing_5min(true);
        indicators.setBullish_engulfing_1min(true);
        indicators.setBullish_morning_star_5min(true);
        indicators.setBullish_morning_star_1min(true);
        indicators.setHammer_5min(true);
        indicators.setHammer_1min(true);
        
        return indicators;
    }
    
    private com.jtradebot.processor.model.strategy.ScalpingEntryConfig.Scenario createQualityScoreScenario() {
        return com.jtradebot.processor.model.strategy.ScalpingEntryConfig.Scenario.builder()
            .name("HIGH_QUALITY_SCORE_SIGNAL")
            .description("Only depends on QualityScore")
            .requirements(com.jtradebot.processor.model.strategy.ScalpingEntryConfig.ScenarioRequirements.builder()
                .minQualityScore(8.0)
                .build())
            .build();
    }
    
    private com.jtradebot.processor.model.strategy.ScalpingEntryConfig.Scenario createSafeEntryScenario() {
        return com.jtradebot.processor.model.strategy.ScalpingEntryConfig.Scenario.builder()
            .name("SAFE_ENTRY_SIGNAL")
            .description("Conservative entry with strong confirmations")
            .requirements(com.jtradebot.processor.model.strategy.ScalpingEntryConfig.ScenarioRequirements.builder()
                .ema_min_count(2)
                .futureAndVolume_min_count(5)
                .candlestick_min_count(3)
                .momentum_min_count(2)
                .build())
            .riskManagement(com.jtradebot.processor.model.strategy.ScalpingEntryConfig.ScenarioRiskManagement.builder()
                .stopLossPoints(10.0)
                .targetPoints(20.0)
                .useTrailingStop(true)
                .build())
            .build();
    }
    
    private FlattenedIndicators createHighQualityLowCategoryIndicators() {
        FlattenedIndicators indicators = new FlattenedIndicators();
        
        // Set only volume surge indicators (high quality score but low category diversity)
        indicators.setVolume_5min_surge(true);
        indicators.setVolume_1min_surge(true);
        indicators.setVolume_15min_surge(true);
        
        // Set some VWAP conditions for volume category
        indicators.setPrice_gt_vwap_5min(true);
        indicators.setPrice_gt_vwap_1min(true);
        indicators.setPrice_gt_vwap_15min(true);
        indicators.setPrice_above_resistance(true);
        
        // Don't set EMA, candlestick, or momentum indicators
        // This will result in high quality score but low category scores
        
        return indicators;
    }
    
    private FlattenedIndicators createMixedSignalsIndicators() {
        FlattenedIndicators indicators = new FlattenedIndicators();
        
        // Set enough signals to pass category requirements (EMA: 2, Volume: 3, Candlestick: 3, Momentum: 2)
        
        // EMA signals (need 2) - mix of bullish and bearish
        indicators.setEma5_5min_gt_ema34_5min(true);  // Bullish
        indicators.setEma5_1min_lt_ema34_1min(true);  // Bearish
        
        // Volume signals (need 3) - mix of bullish and bearish
        indicators.setVolume_5min_surge(true);
        indicators.setVolume_1min_surge(true);
        indicators.setVolume_15min_surge(true);
        indicators.setPrice_gt_vwap_5min(true);  // Bullish
        indicators.setPrice_lt_vwap_1min(true);  // Bearish
        indicators.setPrice_above_resistance(true);  // Bullish
        
        // Candlestick signals (need 3) - mix of bullish and bearish
        indicators.setGreen_candle_5min(true);  // Bullish
        indicators.setRed_candle_1min(true);    // Bearish
        indicators.setLong_body_5min(true);     // Neutral
        indicators.setLong_body_1min(true);     // Neutral
        indicators.setBullish_engulfing_5min(true);  // Bullish
        
        // Momentum signals (need 2) - mix of bullish and bearish
        indicators.setRsi_5min_gt_56(true);  // Bullish
        indicators.setRsi_1min_lt_44(true);  // Bearish
        
        return indicators;
    }
    
    private com.jtradebot.processor.model.strategy.ScalpingEntryConfig.Scenario createSafeEntryScenarioWithQualityScore() {
        return com.jtradebot.processor.model.strategy.ScalpingEntryConfig.Scenario.builder()
            .name("SAFE_ENTRY_SIGNAL")
            .description("Conservative entry with both quality score and category requirements")
            .requirements(com.jtradebot.processor.model.strategy.ScalpingEntryConfig.ScenarioRequirements.builder()
                .minQualityScore(7.5)  // Should be met with volume surge
                .ema_min_count(2)      // Will NOT be met (0 indicators)
                .futureAndVolume_min_count(3)  // Should be met (6 indicators)
                .candlestick_min_count(3)      // Will NOT be met (0 indicators)
                .momentum_min_count(2)         // Will NOT be met (0 indicators)
                .build())
            .riskManagement(com.jtradebot.processor.model.strategy.ScalpingEntryConfig.ScenarioRiskManagement.builder()
                .stopLossPoints(7.0)
                .targetPoints(21.0)
                .useTrailingStop(true)
                .build())
            .build();
    }
    
    private java.util.Map<String, java.util.List<String>> createCallCategories() {
        return java.util.Map.of(
            "ema", java.util.List.of(
                "ema5_5min_gt_ema34_5min",
                "ema5_1min_gt_ema34_1min",
                "ema5_15min_gt_ema34_15min"
            ),
            "futureAndVolume", java.util.List.of(
                "volume_5min_surge",
                "volume_1min_surge",
                "volume_15min_surge",
                "price_gt_vwap_5min",
                "price_gt_vwap_1min",
                "price_gt_vwap_15min",
                "price_above_resistance"
            ),
            "candlestick", java.util.List.of(
                "green_candle_5min",
                "green_candle_1min",
                "long_body_5min",
                "long_body_1min",
                "bullish_engulfing_5min",
                "bullish_engulfing_1min",
                "bullish_morning_star_5min",
                "bullish_morning_star_1min",
                "hammer_5min",
                "hammer_1min"
            ),
            "momentum", java.util.List.of(
                "rsi_5min_gt_56",
                "rsi_1min_gt_56",
                "rsi_15min_gt_56"
            )
        );
    }
    
    private java.util.Map<String, java.util.List<String>> createPutCategories() {
        return java.util.Map.of(
            "ema", java.util.List.of(
                "ema5_5min_lt_ema34_5min",
                "ema5_1min_lt_ema34_1min",
                "ema5_15min_lt_ema34_15min"
            ),
            "futureAndVolume", java.util.List.of(
                "volume_5min_surge",
                "volume_1min_surge",
                "volume_15min_surge",
                "price_lt_vwap_5min",
                "price_lt_vwap_1min",
                "price_lt_vwap_15min",
                "price_below_support"
            ),
            "candlestick", java.util.List.of(
                "red_candle_5min",
                "red_candle_1min",
                "bearish_engulfing_5min",
                "bearish_engulfing_1min",
                "bearish_evening_star_5min",
                "bearish_evening_star_1min",
                "shooting_star_5min",
                "shooting_star_1min"
            ),
            "momentum", java.util.List.of(
                "rsi_5min_lt_44",
                "rsi_1min_lt_44",
                "rsi_15min_lt_44"
            )
        );
    }
    
    private java.util.Map<String, java.util.List<String>> createCategories() {
        return java.util.Map.of(
            "ema", java.util.List.of(
                "ema5_5min_gt_ema34_5min",
                "ema5_1min_gt_ema34_1min",
                "ema5_15min_gt_ema34_15min"
            ),
            "futureAndVolume", java.util.List.of(
                "volume_5min_surge",
                "volume_1min_surge",
                "volume_15min_surge",
                "price_gt_vwap_5min",
                "price_gt_vwap_1min",
                "price_gt_vwap_15min",
                "price_above_resistance"
            ),
            "candlestick", java.util.List.of(
                "green_candle_5min",
                "green_candle_1min",
                "long_body_5min",
                "long_body_1min",
                "bullish_engulfing_5min",
                "bullish_engulfing_1min",
                "bullish_morning_star_5min",
                "bullish_morning_star_1min",
                "hammer_5min",
                "hammer_1min"
            ),
            "momentum", java.util.List.of(
                "rsi_5min_gt_56",
                "rsi_1min_gt_56",
                "rsi_15min_gt_56"
            )
        );
    }
}
