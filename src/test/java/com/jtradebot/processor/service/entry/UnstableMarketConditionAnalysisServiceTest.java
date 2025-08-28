package com.jtradebot.processor.service.entry;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.config.ScoringConfigurationService;
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

    private UnstableMarketConditionAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new UnstableMarketConditionAnalysisService(tickDataManager, configService, scoringConfigService);
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
                .maxOptionalFiltersToIgnore(2)
                .build();

        // When & Then: Verify configuration structure
        assertNotNull(config);
        assertTrue(config.getEnabled());
        assertEquals("Test config", config.getDescription());
        assertEquals(2, config.getMaxOptionalFiltersToIgnore());
    }

    @Test
    void testFilterStructure() {
        // Given: Test filter structure
        ScalpingEntryConfig.NoTradeFilter filter = ScalpingEntryConfig.NoTradeFilter.builder()
                .enabled(true)
                .mandatory(true)
                .name("Test Filter")
                .description("Test filter description")
                .threshold(10.0)
                .priority(1)
                .build();

        // When & Then: Verify filter structure
        assertNotNull(filter);
        assertTrue(filter.getEnabled());
        assertTrue(filter.getMandatory());
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
}
