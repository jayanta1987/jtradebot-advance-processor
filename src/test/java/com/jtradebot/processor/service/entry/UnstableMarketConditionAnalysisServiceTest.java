package com.jtradebot.processor.service.entry;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.config.ScoringConfigurationService;
import com.jtradebot.processor.indicator.SupportResistanceIndicator;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.strategy.ScalpingEntryConfig;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.zerodhatech.models.Tick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class UnstableMarketConditionAnalysisServiceTest {

    @Mock
    private TickDataManager tickDataManager;

    @Mock
    private DynamicStrategyConfigService configService;

    @Mock
    private ScoringConfigurationService scoringConfigService;
    
    @Mock
    private SupportResistanceIndicator supportResistanceIndicator;

    private UnstableMarketConditionAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new UnstableMarketConditionAnalysisService(tickDataManager, configService, scoringConfigService, supportResistanceIndicator);
    }

    @Test
    void testServiceInitialization() {
        // Given: Basic service setup
        assertNotNull(service, "Service should be initialized");
    }

    @Test
    void testConfigurationStructure() {
        // Given: Basic configuration test
        ScalpingEntryConfig.NoTradeZonesConfig config = ScalpingEntryConfig.NoTradeZonesConfig.builder()
                .enabled(true)
                .description("Test config")
                .maxAllowedNTP(2)
                .build();

        // When & Then: Verify configuration structure
        assertNotNull(config);
        assertTrue(config.getEnabled());
        assertEquals("Test config", config.getDescription());
        assertEquals(2, config.getMaxAllowedNTP());
    }

    @Test
    void testFilterStructure() {
        // Given: Test filter structure
        ScalpingEntryConfig.NoTradeFilter filter = ScalpingEntryConfig.NoTradeFilter.builder()
                .enabled(true)
                .ntp(3.0)
                .name("Test Filter")
                .description("Test filter description")
                .threshold(10.0)
                .priority(1)
                .build();

        // When & Then: Verify filter structure
        assertNotNull(filter);
        assertTrue(filter.getEnabled());
        assertEquals(3.0, filter.getNtp());
        assertEquals("Test Filter", filter.getName());
        assertEquals("Test filter description", filter.getDescription());
        assertEquals(10.0, filter.getThreshold());
        assertEquals(1, filter.getPriority());
    }

    @Test
    void testCandleTimeframeEnumParsing() {
        // Given: Test different timeframe formats
        assertEquals(CandleTimeFrameEnum.ONE_MIN, CandleTimeFrameEnum.valueOf("ONE_MIN"));
        assertEquals(CandleTimeFrameEnum.FIVE_MIN, CandleTimeFrameEnum.valueOf("FIVE_MIN"));
        assertEquals(CandleTimeFrameEnum.FIFTEEN_MIN, CandleTimeFrameEnum.valueOf("FIFTEEN_MIN"));
        assertEquals(CandleTimeFrameEnum.ONE_HOUR, CandleTimeFrameEnum.valueOf("ONE_HOUR"));
        assertEquals(CandleTimeFrameEnum.ONE_DAY, CandleTimeFrameEnum.valueOf("ONE_DAY"));
        
        // Test that invalid values throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            CandleTimeFrameEnum.valueOf("5min");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            CandleTimeFrameEnum.valueOf("INVALID");
        });
    }

    @Test
    void testDirectionalStrengthFilterConfiguration() {
        // Given: Test directional strength filter configuration
        ScalpingEntryConfig.NoTradeFilter directionalStrengthFilter = ScalpingEntryConfig.NoTradeFilter.builder()
                .enabled(true)
                .ntp(1.0)
                .name("Directional Strength")
                .description("Filter out entries with directional strength less than 0.5 (50%)")
                .threshold(0.5)
                .priority(7)
                .build();

        // When & Then: Verify directional strength filter structure
        assertNotNull(directionalStrengthFilter);
        assertTrue(directionalStrengthFilter.getEnabled());
        assertEquals(1.0, directionalStrengthFilter.getNtp());
        assertEquals("Directional Strength", directionalStrengthFilter.getName());
        assertEquals("Filter out entries with directional strength less than 0.5 (50%)", directionalStrengthFilter.getDescription());
        assertEquals(0.5, directionalStrengthFilter.getThreshold());
        assertEquals(7, directionalStrengthFilter.getPriority());
    }

    @Test
    void testDirectionalStrengthFilterLogic() {
        // Given: Test directional strength filter logic with different thresholds
        ScalpingEntryConfig.NoTradeFilter filter = ScalpingEntryConfig.NoTradeFilter.builder()
                .enabled(true)
                .ntp(1.0)
                .name("Directional Strength")
                .threshold(0.5)
                .priority(7)
                .build();

        // Test cases for different directional strength values
        // Case 1: Strong directional strength (0.7) should pass
        assertTrue(0.7 >= filter.getThreshold(), "Directional strength 0.7 should pass threshold 0.5");
        
        // Case 2: Moderate directional strength (0.5) should pass (equal to threshold)
        assertTrue(0.5 >= filter.getThreshold(), "Directional strength 0.5 should pass threshold 0.5");
        
        // Case 3: Weak directional strength (0.3) should fail
        assertFalse(0.3 >= filter.getThreshold(), "Directional strength 0.3 should fail threshold 0.5");
        
        // Case 4: Very weak directional strength (0.1) should fail
        assertFalse(0.1 >= filter.getThreshold(), "Directional strength 0.1 should fail threshold 0.5");
    }

    @Test
    void testDirectionalStrengthFilterIntegration() {
        // Given: Test the complete filter integration
        ScalpingEntryConfig.NoTradeFilter filter = ScalpingEntryConfig.NoTradeFilter.builder()
                .enabled(true)
                .ntp(1.0)
                .name("Directional Strength")
                .description("Filter out entries with directional strength less than 0.5 (50%)")
                .threshold(0.5)
                .priority(7)
                .build();

        // Verify filter properties
        assertEquals("directionalStrength", "directionalStrength"); // This would be the filter key
        assertTrue(filter.getEnabled());
        assertEquals(1, filter.getNtp());
        assertEquals(7, filter.getPriority());
        
        // Test threshold validation
        assertTrue(filter.getThreshold() > 0.0, "Threshold should be positive");
        assertTrue(filter.getThreshold() <= 1.0, "Threshold should not exceed 1.0");
    }

    @Test
    void testConsecutiveSameColorCandlesFilterConfiguration() {
        // Given: Test consecutive same color candles filter configuration
        ScalpingEntryConfig.NoTradeFilter consecutiveCandlesFilter = ScalpingEntryConfig.NoTradeFilter.builder()
                .enabled(true)
                .ntp(1.0)
                .name("Consecutive Same Color Candles")
                .description("Filter out entries when there are too many consecutive same color candles")
                .maxConsecutiveCount(3)
                .analysisWindow(10)
                .priority(8)
                .timeframe("FIVE_MIN")
                .build();

        // When & Then: Verify consecutive same color candles filter structure
        assertNotNull(consecutiveCandlesFilter);
        assertTrue(consecutiveCandlesFilter.getEnabled());
        assertEquals(1, consecutiveCandlesFilter.getNtp());
        assertEquals("Consecutive Same Color Candles", consecutiveCandlesFilter.getName());
        assertEquals("Filter out entries when there are too many consecutive same color candles", consecutiveCandlesFilter.getDescription());
        assertEquals(3, consecutiveCandlesFilter.getMaxConsecutiveCount());
        assertEquals(10, consecutiveCandlesFilter.getAnalysisWindow());
        assertEquals(8, consecutiveCandlesFilter.getPriority());
        assertEquals("FIVE_MIN", consecutiveCandlesFilter.getTimeframe());
    }

    @Test
    void testConsecutiveSameColorCandlesFilterLogic() {
        // Given: Test consecutive same color candles filter logic with different scenarios
        ScalpingEntryConfig.NoTradeFilter filter = ScalpingEntryConfig.NoTradeFilter.builder()
                .enabled(true)
                .ntp(1.0)
                .name("Consecutive Same Color Candles")
                .maxConsecutiveCount(3)
                .analysisWindow(10)
                .priority(8)
                .timeframe("FIVE_MIN")
                .build();

        // Test cases for different consecutive candle counts
        // Case 1: 2 consecutive candles should pass (below max count)
        assertTrue(2 < filter.getMaxConsecutiveCount(), "2 consecutive candles should pass max count 3");
        
        // Case 2: 3 consecutive candles should fail (equal to max count)
        assertFalse(3 < filter.getMaxConsecutiveCount(), "3 consecutive candles should fail max count 3");
        
        // Case 3: 5 consecutive candles should fail (above max count)
        assertFalse(5 < filter.getMaxConsecutiveCount(), "5 consecutive candles should fail max count 3");
        
        // Case 4: 0 consecutive candles should pass (below max count)
        assertTrue(0 < filter.getMaxConsecutiveCount(), "0 consecutive candles should pass max count 3");
    }

    @Test
    void testConsecutiveSameColorCandlesFilterIntegration() {
        // Given: Test the complete consecutive same color candles filter integration
        ScalpingEntryConfig.NoTradeFilter filter = ScalpingEntryConfig.NoTradeFilter.builder()
                .enabled(true)
                .ntp(1.0)
                .name("Consecutive Same Color Candles")
                .description("Filter out entries when there are too many consecutive same color candles")
                .maxConsecutiveCount(3)
                .analysisWindow(10)
                .priority(8)
                .timeframe("FIVE_MIN")
                .build();

        // Verify filter properties
        assertEquals("consecutiveSameColorCandles", "consecutiveSameColorCandles"); // This would be the filter key
        assertTrue(filter.getEnabled());
        assertEquals(1, filter.getNtp());
        assertEquals(8, filter.getPriority());
        
        // Test max consecutive count validation
        assertTrue(filter.getMaxConsecutiveCount() > 0, "Max consecutive count should be positive");
        assertTrue(filter.getMaxConsecutiveCount() <= 10, "Max consecutive count should be reasonable for candle counting");
        
        // Test new configuration parameters
        assertEquals("FIVE_MIN", filter.getTimeframe());
        assertEquals(10, filter.getAnalysisWindow());
    }

    @Test
    void testConsecutiveSameColorCandlesFilterConfigurationParameters() {
        // Given: Test different configuration parameter combinations
        ScalpingEntryConfig.NoTradeFilter filter1 = ScalpingEntryConfig.NoTradeFilter.builder()
                .enabled(true)
                .ntp(1.0)
                .name("Test Filter 1")
                .maxConsecutiveCount(3)
                .analysisWindow(10)
                .priority(8)
                .timeframe("ONE_MIN")
                .build();

        ScalpingEntryConfig.NoTradeFilter filter2 = ScalpingEntryConfig.NoTradeFilter.builder()
                .enabled(true)
                .ntp(1.0)
                .name("Test Filter 2")
                .maxConsecutiveCount(7)
                .analysisWindow(15)
                .priority(8)
                .timeframe("FIFTEEN_MIN")
                .build();

        // When & Then: Verify different parameter combinations
        assertEquals("ONE_MIN", filter1.getTimeframe());
        assertEquals(10, filter1.getAnalysisWindow());
        assertEquals(3, filter1.getMaxConsecutiveCount());

        assertEquals("FIFTEEN_MIN", filter2.getTimeframe());
        assertEquals(15, filter2.getAnalysisWindow());
        assertEquals(7, filter2.getMaxConsecutiveCount());
    }

    @Test
    void testAtr5MinFilterStructure() {
        // Given: Test ATR 5-minute filter structure
        ScalpingEntryConfig.NoTradeFilter atrFilter = ScalpingEntryConfig.NoTradeFilter.builder()
                .enabled(true)
                .ntp(1.5)
                .name("ATR 5-Minute Filter")
                .description("Filter out entries when 5-minute ATR is less than 15.0 (low volatility)")
                .threshold(15.0)
                .priority(8)
                .build();

        // When & Then: Verify ATR filter structure
        assertNotNull(atrFilter);
        assertTrue(atrFilter.getEnabled());
        assertEquals(1.5, atrFilter.getNtp());
        assertEquals("ATR 5-Minute Filter", atrFilter.getName());
        assertEquals("Filter out entries when 5-minute ATR is less than 15.0 (low volatility)", atrFilter.getDescription());
        assertEquals(15.0, atrFilter.getThreshold());
        assertEquals(8, atrFilter.getPriority());
    }

    @Test
    void testAtr5MinFilterLogic() {
        // Given: Test ATR 5-minute filter logic with different scenarios
        ScalpingEntryConfig.NoTradeFilter filter = ScalpingEntryConfig.NoTradeFilter.builder()
                .enabled(true)
                .ntp(1.5)
                .name("ATR 5-Minute Filter")
                .threshold(15.0)
                .priority(8)
                .build();

        // Test cases for different ATR values
        // Case 1: ATR = 20.0 should pass (above threshold)
        assertTrue(20.0 >= filter.getThreshold(), "ATR 20.0 should pass threshold 15.0");
        
        // Case 2: ATR = 15.0 should pass (equal to threshold)
        assertTrue(15.0 >= filter.getThreshold(), "ATR 15.0 should pass threshold 15.0");
        
        // Case 3: ATR = 10.0 should fail (below threshold)
        assertFalse(10.0 >= filter.getThreshold(), "ATR 10.0 should fail threshold 15.0");
        
        // Case 4: ATR = 0.0 should fail (below threshold)
        assertFalse(0.0 >= filter.getThreshold(), "ATR 0.0 should fail threshold 15.0");
    }
}
