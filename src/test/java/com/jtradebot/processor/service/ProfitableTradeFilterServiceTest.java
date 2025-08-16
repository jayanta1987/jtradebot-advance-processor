package com.jtradebot.processor.service;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.model.indicator.EntryQuality;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.strategy.ProfitableTradeFilterConfig;
import com.jtradebot.processor.model.strategy.ProfitableTradeFilterResult;
import com.jtradebot.processor.service.impl.ProfitableTradeFilterServiceImpl;
import com.zerodhatech.models.Tick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfitableTradeFilterServiceTest {

    @Mock
    private DynamicStrategyConfigService configService;

    private ProfitableTradeFilterServiceImpl filterService;

    @BeforeEach
    void setUp() {
        filterService = new ProfitableTradeFilterServiceImpl(configService, null);
    }

    @Test
    void testHighQualityEntryLogic_WhenQualityScore8_0_ShouldApplyRelaxedRequirements() {
        // Given
        ProfitableTradeFilterConfig config = createTestConfig();
        when(configService.getProfitableTradeFilterConfig()).thenReturn(config);

        EntryQuality entryQuality = EntryQuality.builder()
                .qualityScore(8.5) // Above threshold
                .candlestickScore(5.0)
                .build();

        FlattenedIndicators indicators = createTestIndicators();
        Tick tick = new Tick();

        // When
        ProfitableTradeFilterResult result = filterService.evaluateCallEntry(indicators, entryQuality, tick);

        // Then
        assertTrue(result.getHighQualityEntryLogicApplied());
        assertTrue(result.getIsProfitableEntry());
        
        // Verify that relaxed requirements were applied (1 condition per category instead of minCount)
        assertEquals(1, result.getEmaCategory().getRequiredCount());
        assertEquals(1, result.getFutureAndVolumeCategory().getRequiredCount());
        assertEquals(1, result.getCandlestickCategory().getRequiredCount());
        assertEquals(1, result.getMomentumCategory().getRequiredCount());
    }

    @Test
    void testHighQualityEntryLogic_WhenQualityScore7_6_ShouldUseNormalRequirements() {
        // Given
        ProfitableTradeFilterConfig config = createTestConfig();
        when(configService.getProfitableTradeFilterConfig()).thenReturn(config);

        EntryQuality entryQuality = EntryQuality.builder()
                .qualityScore(7.6) // Above minQualityScore (7.5) but below high quality threshold (8.0)
                .candlestickScore(5.0)
                .build();

        FlattenedIndicators indicators = createTestIndicators();
        Tick tick = new Tick();

        // When
        ProfitableTradeFilterResult result = filterService.evaluateCallEntry(indicators, entryQuality, tick);

        // Then
        assertFalse(result.getHighQualityEntryLogicApplied());
        
        // The entry should be rejected because not enough conditions are met with normal requirements
        assertFalse(result.getIsProfitableEntry());
        assertTrue(result.getRejectionReason().contains("Categories failed"));
        
        // Verify that normal requirements were applied (minCount from config)
        assertEquals(2, result.getEmaCategory().getRequiredCount());
        assertEquals(4, result.getFutureAndVolumeCategory().getRequiredCount());
        assertEquals(3, result.getCandlestickCategory().getRequiredCount());
        assertEquals(2, result.getMomentumCategory().getRequiredCount());
    }

    private ProfitableTradeFilterConfig createTestConfig() {
        return ProfitableTradeFilterConfig.builder()
                .enabled(true)
                .highQualityEntryLogic(ProfitableTradeFilterConfig.HighQualityEntryLogic.builder()
                        .enabled(true)
                        .highQualityThreshold(8.0)
                        .relaxedCategoryRequirement(1)
                        .build())
                .callStrategy(ProfitableTradeFilterConfig.CallStrategyFilter.builder()
                        .enabled(true)
                        .minQualityScore(7.5)
                        .minCandlestickScore(4.0)
                        .volumeSurgeMultiplier(ProfitableTradeFilterConfig.VolumeSurgeMultiplier.builder()
                                .min(15.1)
                                .max(16.0)
                                .build())
                        .categories(ProfitableTradeFilterConfig.Categories.builder()
                                .ema(ProfitableTradeFilterConfig.Category.builder()
                                        .conditions(java.util.List.of("ema9_5min_gt_ema21_5min", "ema9_1min_gt_ema21_1min"))
                                        .minCount(2)
                                        .build())
                                .futureAndVolume(ProfitableTradeFilterConfig.Category.builder()
                                        .conditions(java.util.List.of("volume_5min_surge", "price_gt_vwap_5min"))
                                        .minCount(4)
                                        .build())
                                .candlestick(ProfitableTradeFilterConfig.Category.builder()
                                        .conditions(java.util.List.of("green_candle_5min", "long_body_5min"))
                                        .minCount(3)
                                        .build())
                                .momentum(ProfitableTradeFilterConfig.Category.builder()
                                        .conditions(java.util.List.of("rsi_5min_gt_56", "rsi_1min_gt_56"))
                                        .minCount(2)
                                        .build())
                                .build())
                        .build())
                .logging(ProfitableTradeFilterConfig.LoggingConfig.builder()
                        .enabled(true)
                        .logFilteredEntries(true)
                        .logRejectionReasons(true)
                        .build())
                .build();
    }

    private FlattenedIndicators createTestIndicators() {
        FlattenedIndicators indicators = new FlattenedIndicators();
        
        // Set all conditions to true to ensure they pass
        indicators.setEma9_5min_gt_ema21_5min(true);
        indicators.setEma9_1min_gt_ema21_1min(true);
        indicators.setVolume_5min_surge(true);
        indicators.setPrice_gt_vwap_5min(true);
        indicators.setGreen_candle_5min(true);
        indicators.setLong_body_5min(true);
        indicators.setRsi_5min_gt_56(true);
        indicators.setRsi_1min_gt_56(true);
        indicators.setVolume_surge_multiplier(15.5);
        
        // Set additional conditions that might be needed
        indicators.setVolume_1min_surge(true);
        indicators.setVolume_15min_surge(true);
        indicators.setPrice_gt_vwap_1min(true);
        indicators.setPrice_gt_vwap_15min(true);
        indicators.setPrice_above_resistance(true);
        indicators.setGreen_candle_1min(true);
        indicators.setLong_body_1min(true);
        indicators.setBullish_engulfing_5min(true);
        indicators.setBullish_engulfing_1min(true);
        indicators.setBullish_morning_star_5min(true);
        indicators.setBullish_morning_star_1min(true);
        indicators.setHammer_5min(true);
        indicators.setHammer_1min(true);
        indicators.setRsi_15min_gt_56(true);
        
        return indicators;
    }
}
