package com.jtradebot.processor.service;

import com.jtradebot.processor.model.FlattenedIndicators;
import com.jtradebot.processor.model.StrategyScore;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.manager.EmaCrossTrackingManager;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.repository.JtradeOrderRepository;
import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.service.BacktestDataCollectorService;
import com.jtradebot.processor.service.ExitStrategyService;
import com.jtradebot.processor.service.OptionPricingService;
import com.jtradebot.processor.service.TickMonitoringService;
import com.zerodhatech.models.Tick;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TickProcessServiceTest {

    @Mock
    private TickDataManager tickDataManager;
    
    @Mock
    private TickSetupService tickSetupService;
    
    @Mock
    private TickEventTracker tickEventTracker;
    
    @Mock
    private KiteInstrumentHandler kiteInstrumentHandler;
    
    @Mock
    private EmaCrossTrackingManager emaCrossTrackingManager;
    
    @Mock
    private ScalpingVolumeSurgeService scalpingVolumeSurgeService;
    
    @Mock
    private BacktestDataCollectorService backtestDataCollectorService;
    
    @Mock
    private ExitStrategyService exitStrategyService;
    
    @Mock
    private JtradeOrderRepository jtradeOrderRepository;
    
    @Mock
    private OptionPricingService optionPricingService;
    
    @Mock
    private DynamicStrategyConfigService configService;
    
    @Mock
    private TickMonitoringService tickMonitoringService;

    private TickProcessService tickProcessService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tickProcessService = new TickProcessService(
            tickDataManager, tickSetupService, tickEventTracker, kiteInstrumentHandler,
            emaCrossTrackingManager, scalpingVolumeSurgeService, backtestDataCollectorService,
            exitStrategyService, jtradeOrderRepository, optionPricingService, configService, tickMonitoringService
        );
    }

    @Test
    void testGetConditionCountsForCall() throws Exception {
        // Create a FlattenedIndicators object with some conditions set to true
        FlattenedIndicators indicators = FlattenedIndicators.builder()
            .instrumentToken("256265")
            .timestamp(LocalDateTime.now())
            .timeframe("5min")
            // Mandatory conditions for CALL
            .volume_5min_surge(true)
            .rsi_5min_gt_56(true)
            .price_gt_vwap_5min(true)
            // Optional conditions for CALL
            .ema9_5min_gt_ema21_5min(true)
            .ema9_1min_gt_ema21_1min(true)
            .volume_1min_surge(true)
            .rsi_1min_gt_56(true)
            .price_gt_vwap_1min(true)
            .price_above_resistance(true)
            .bullish_engulfing_5min(true)
            .hammer_5min(true)
            .build();

        // Use reflection to access the private method
        Method getConditionCountsMethod = TickProcessService.class.getDeclaredMethod("getConditionCounts", FlattenedIndicators.class, String.class);
        getConditionCountsMethod.setAccessible(true);
        
        String result = (String) getConditionCountsMethod.invoke(tickProcessService, indicators, "CALL");
        
        // Expected: M:3/3 O:8/23 (3 mandatory + 8 optional conditions matched)
        assertEquals("M:3/3 O:8/23", result);
    }

    @Test
    void testGetConditionCountsForPut() throws Exception {
        // Create a FlattenedIndicators object with some conditions set to true
        FlattenedIndicators indicators = FlattenedIndicators.builder()
            .instrumentToken("256265")
            .timestamp(LocalDateTime.now())
            .timeframe("5min")
            // Mandatory conditions for PUT
            .volume_5min_surge(true)
            .rsi_5min_lt_44(true)
            .price_lt_vwap_5min(true)
            // Optional conditions for PUT
            .ema9_5min_gt_ema21_5min(false) // For PUT, EMA should be bearish
            .ema9_1min_gt_ema21_1min(false)
            .volume_1min_surge(true)
            .rsi_1min_lt_44(true)
            .price_lt_vwap_1min(true)
            .price_below_support(true)
            .bearish_engulfing_5min(true)
            .shooting_star_5min(true)
            .build();

        // Use reflection to access the private method
        Method getConditionCountsMethod = TickProcessService.class.getDeclaredMethod("getConditionCounts", FlattenedIndicators.class, String.class);
        getConditionCountsMethod.setAccessible(true);
        
        String result = (String) getConditionCountsMethod.invoke(tickProcessService, indicators, "PUT");
        
        // Expected: M:3/3 O:8/23 (3 mandatory + 8 optional conditions matched)
        assertEquals("M:3/3 O:8/23", result);
    }

    @Test
    void testGetConditionCountsWithNoConditions() throws Exception {
        // Create a FlattenedIndicators object with no conditions set
        FlattenedIndicators indicators = FlattenedIndicators.builder()
            .instrumentToken("256265")
            .timestamp(LocalDateTime.now())
            .timeframe("5min")
            .build();

        // Use reflection to access the private method
        Method getConditionCountsMethod = TickProcessService.class.getDeclaredMethod("getConditionCounts", FlattenedIndicators.class, String.class);
        getConditionCountsMethod.setAccessible(true);
        
        String result = (String) getConditionCountsMethod.invoke(tickProcessService, indicators, "CALL");
        
        // Expected: M:0/3 O:0/23 (no conditions matched)
        assertEquals("M:0/3 O:0/23", result);
    }
}
