package com.jtradebot.processor.service.entry;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.model.strategy.ScalpingEntryConfig;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.zerodhatech.models.Tick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlexibleFilteringIntegrationTest {

    @Mock
    private DynamicStrategyConfigService configService;

    @InjectMocks
    private UnstableMarketConditionAnalysisService service;

    @BeforeEach
    void setUp() {
        // Basic setup
    }

    @Test
    void testFlexibleFilteringDisabled() {
        // Given: No-trade-zones filtering is disabled
        when(configService.isNoTradeZonesEnabled()).thenReturn(false);


        // When: Check market condition suitability
        Tick tick = createMockTick();
        FlattenedIndicators indicators = createMockIndicators();
        
        boolean result = service.isMarketConditionSuitable(tick, indicators);

        // Then: Should pass because filtering is disabled
        assertTrue(result, "Should pass when filtering is disabled");
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

    private Tick createMockTick() {
        Tick tick = new Tick();
        tick.setInstrumentToken(256265L);
        tick.setLastTradedPrice(19500.0);
        return tick;
    }

    private FlattenedIndicators createMockIndicators() {
        FlattenedIndicators indicators = new FlattenedIndicators();
        indicators.setVolume_surge_multiplier(15.0);
        indicators.setEma200_distance_5min(50.0);
        indicators.setEma200_5min(19500.0);
        indicators.setEma34_5min(19450.0);
        return indicators;
    }
}
