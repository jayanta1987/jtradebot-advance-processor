package com.jtradebot.processor.service.entry;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.config.ScoringConfigurationService;
import com.jtradebot.processor.handler.DateTimeHandler;
import com.jtradebot.processor.indicator.SupportResistanceIndicator;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.strategy.ScalpingEntryConfig;
import com.zerodhatech.models.Tick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradingHoursFilterTest {

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
    void testTradingHoursFilter_WithinTradingHours_ShouldPass() {
        // Given: Create a tick during trading hours (10:30 AM)
        Tick tick = createTickWithTime(10, 30, 0);
        FlattenedIndicators indicators = new FlattenedIndicators();
        
        // Create trading hours filter configuration
        ScalpingEntryConfig.NoTradeFilter filter = ScalpingEntryConfig.NoTradeFilter.builder()
                .enabled(true)
                .mandatory(true)
                .name("Trading Hours Filter")
                .startHour(9)
                .startMinute(15)
                .endHour(15)
                .endMinute(30)
                .priority(10)
                .build();
        
        // When: Check if within trading hours
        boolean result = DateTimeHandler.withinTradingHours(
                filter.getStartHour(), filter.getStartMinute(),
                filter.getEndHour(), filter.getEndMinute(),
                tick.getTickTimestamp()
        );
        
        // Then: Should pass (within trading hours)
        assertTrue(result, "Should pass when within trading hours (10:30 AM)");
    }

    @Test
    void testTradingHoursFilter_BeforeTradingHours_ShouldFail() {
        // Given: Create a tick before trading hours (8:30 AM)
        Tick tick = createTickWithTime(8, 30, 0);
        FlattenedIndicators indicators = new FlattenedIndicators();
        
        // Create trading hours filter configuration
        ScalpingEntryConfig.NoTradeFilter filter = ScalpingEntryConfig.NoTradeFilter.builder()
                .enabled(true)
                .mandatory(true)
                .name("Trading Hours Filter")
                .startHour(9)
                .startMinute(15)
                .endHour(15)
                .endMinute(30)
                .priority(10)
                .build();
        
        // When: Check if within trading hours
        boolean result = DateTimeHandler.withinTradingHours(
                filter.getStartHour(), filter.getStartMinute(),
                filter.getEndHour(), filter.getEndMinute(),
                tick.getTickTimestamp()
        );
        
        // Then: Should fail (before trading hours)
        assertFalse(result, "Should fail when before trading hours (8:30 AM)");
    }

    @Test
    void testTradingHoursFilter_AfterTradingHours_ShouldFail() {
        // Given: Create a tick after trading hours (4:00 PM)
        Tick tick = createTickWithTime(16, 0, 0);
        FlattenedIndicators indicators = new FlattenedIndicators();
        
        // Create trading hours filter configuration
        ScalpingEntryConfig.NoTradeFilter filter = ScalpingEntryConfig.NoTradeFilter.builder()
                .enabled(true)
                .mandatory(true)
                .name("Trading Hours Filter")
                .startHour(9)
                .startMinute(15)
                .endHour(15)
                .endMinute(30)
                .priority(10)
                .build();
        
        // When: Check if within trading hours
        boolean result = DateTimeHandler.withinTradingHours(
                filter.getStartHour(), filter.getStartMinute(),
                filter.getEndHour(), filter.getEndMinute(),
                tick.getTickTimestamp()
        );
        
        // Then: Should fail (after trading hours)
        assertFalse(result, "Should fail when after trading hours (4:00 PM)");
    }

    @Test
    void testTradingHoursFilter_AtMarketOpen_ShouldPass() {
        // Given: Create a tick exactly at market open (9:15 AM)
        Tick tick = createTickWithTime(9, 15, 0);
        FlattenedIndicators indicators = new FlattenedIndicators();
        
        // Create trading hours filter configuration
        ScalpingEntryConfig.NoTradeFilter filter = ScalpingEntryConfig.NoTradeFilter.builder()
                .enabled(true)
                .mandatory(true)
                .name("Trading Hours Filter")
                .startHour(9)
                .startMinute(15)
                .endHour(15)
                .endMinute(30)
                .priority(10)
                .build();
        
        // When: Check if within trading hours
        boolean result = DateTimeHandler.withinTradingHours(
                filter.getStartHour(), filter.getStartMinute(),
                filter.getEndHour(), filter.getEndMinute(),
                tick.getTickTimestamp()
        );
        
        // Then: Should pass (at market open)
        assertTrue(result, "Should pass when at market open (9:15 AM)");
    }

    @Test
    void testTradingHoursFilter_AtMarketClose_ShouldPass() {
        // Given: Create a tick exactly at market close (3:30 PM)
        Tick tick = createTickWithTime(15, 30, 0);
        FlattenedIndicators indicators = new FlattenedIndicators();
        
        // Create trading hours filter configuration
        ScalpingEntryConfig.NoTradeFilter filter = ScalpingEntryConfig.NoTradeFilter.builder()
                .enabled(true)
                .mandatory(true)
                .name("Trading Hours Filter")
                .startHour(9)
                .startMinute(15)
                .endHour(15)
                .endMinute(30)
                .priority(10)
                .build();
        
        // When: Check if within trading hours
        boolean result = DateTimeHandler.withinTradingHours(
                filter.getStartHour(), filter.getStartMinute(),
                filter.getEndHour(), filter.getEndMinute(),
                tick.getTickTimestamp()
        );
        
        // Then: Should pass (at market close)
        assertTrue(result, "Should pass when at market close (3:30 PM)");
    }

    @Test
    void testTradingHoursFilter_OneMinuteAfterMarketClose_ShouldFail() {
        // Given: Create a tick one minute after market close (3:31 PM)
        Tick tick = createTickWithTime(15, 31, 0);
        FlattenedIndicators indicators = new FlattenedIndicators();
        
        // Create trading hours filter configuration
        ScalpingEntryConfig.NoTradeFilter filter = ScalpingEntryConfig.NoTradeFilter.builder()
                .enabled(true)
                .mandatory(true)
                .name("Trading Hours Filter")
                .startHour(9)
                .startMinute(15)
                .endHour(15)
                .endMinute(30)
                .priority(10)
                .build();
        
        // When: Check if within trading hours
        boolean result = DateTimeHandler.withinTradingHours(
                filter.getStartHour(), filter.getStartMinute(),
                filter.getEndHour(), filter.getEndMinute(),
                tick.getTickTimestamp()
        );
        
        // Then: Should fail (after market close)
        assertFalse(result, "Should fail when one minute after market close (3:31 PM)");
    }

    /**
     * Helper method to create a tick with specific time
     */
    private Tick createTickWithTime(int hour, int minute, int second) {
        Tick tick = new Tick();
        
        // Create a calendar instance and set the time
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, second);
        calendar.set(Calendar.MILLISECOND, 0);
        
        // Set the tick timestamp
        tick.setTickTimestamp(calendar.getTime());
        tick.setLastTradedTime(calendar.getTime());
        
        return tick;
    }
}
