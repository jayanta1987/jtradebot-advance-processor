package com.jtradebot.processor.service;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.model.DynamicFlattenedIndicators;
import com.jtradebot.processor.model.DynamicIndicatorConfig;
import com.jtradebot.processor.model.StrategyScore;
import com.jtradebot.processor.repository.document.TickDocument;
import com.zerodhatech.models.Tick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TickMonitoringServiceTest {

    @Mock
    private DynamicStrategyConfigService configService;
    
    @Mock
    private DynamicIndicatorFlattenerService dynamicIndicatorFlattenerService;
    
    @Mock
    private ScalpingVolumeSurgeService scalpingVolumeSurgeService;
    
    private TickMonitoringService tickMonitoringService;
    
    @BeforeEach
    void setUp() {
        tickMonitoringService = new TickMonitoringService(
            configService, 
            dynamicIndicatorFlattenerService, 
            scalpingVolumeSurgeService
        );
    }
    
    @Test
    void testMonitorTickWithScoreAndIndicators() {
        // Given
        Tick tick = createSampleTick();
        TickDocument tickDocument = createSampleTickDocument();
        StrategyScore strategyScore = createSampleStrategyScore();
        DynamicFlattenedIndicators indicators = createSampleIndicators();
        
        // When
        when(scalpingVolumeSurgeService.calculateStrategyScore(any(Tick.class)))
            .thenReturn(strategyScore);
        when(dynamicIndicatorFlattenerService.flattenIndicators(any(TickDocument.class), any(DynamicIndicatorConfig.class)))
            .thenReturn(indicators);
        
        // Then
        tickMonitoringService.monitorTickWithScoreAndIndicators(tick, tickDocument);
        
        // Verify that the monitoring service was called
        verify(scalpingVolumeSurgeService).calculateStrategyScore(tick);
        verify(dynamicIndicatorFlattenerService).flattenIndicators(tickDocument, any());
    }
    
    private Tick createSampleTick() {
        Tick tick = new Tick();
        tick.setInstrumentToken(256265L); // Nifty 50
        tick.setLastTradedPrice(18950.0);
        tick.setHighPrice(18980.0);
        tick.setLowPrice(18920.0);
        tick.setVolumeTradedToday(1000000L);
        tick.setTickTimestamp(new Date());
        return tick;
    }
    
    private TickDocument createSampleTickDocument() {
        TickDocument document = new TickDocument();
        document.setInstrumentToken(256265L);
        document.setLastTradedPrice(18950.0);
        document.setVolumeTradedToday(1000000L);
        document.setTickTimestamp(new Date());
        return document;
    }
    
    private StrategyScore createSampleStrategyScore() {
        return StrategyScore.builder()
            .score(5.5)
            .confidence(0.85)
            .emaScore(2.0)
            .rsiScore(1.5)
            .volumeScore(1.0)
            .priceActionScore(0.5)
            .futuresignalScore(0.5)
            .shouldMakeCallEntry(true)
            .shouldMakePutEntry(false)
            .strength("STRONG")
            .sentiment("BULLISH")
            .build();
    }
    
    private DynamicFlattenedIndicators createSampleIndicators() {
        DynamicFlattenedIndicators indicators = new DynamicFlattenedIndicators();
        indicators.setBooleanIndicator("ema_1min_bullish", true);
        indicators.setBooleanIndicator("ema_5min_bullish", true);
        indicators.setBooleanIndicator("rsi_1min_oversold", false);
        indicators.setBooleanIndicator("volume_surge_1min", true);
        indicators.setBooleanIndicator("price_action_bullish", true);
        return indicators;
    }
}
