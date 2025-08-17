package com.jtradebot.processor.indicator;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.FIVE_MIN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceVolumeSurgeIndicatorTest {

    @Mock
    private TickDataManager tickDataManager;

    @Mock
    private DynamicStrategyConfigService configService;

    private PriceVolumeSurgeIndicator indicator;

    @BeforeEach
    void setUp() {
        indicator = new PriceVolumeSurgeIndicator(tickDataManager, configService);
    }

    @Test
    void testVolumeSurgeMultiplier_WithExtremeValues_ShouldBeCapped() {
        // Given
        String instrumentToken = "256265";
        long currentVolume = 1000000; // High current volume
        double veryLowAverageVolume = 0.1; // Very low average volume (would cause 10,000,000x multiplier)
        
        // Create BarSeries with very low historical volumes
        BarSeries barSeries = createBarSeriesWithLowVolume(veryLowAverageVolume);
        when(tickDataManager.getBarSeriesForTimeFrame(anyString(), any(CandleTimeFrameEnum.class)))
                .thenReturn(barSeries);
        when(configService.getCallVolumeSurgeMultiplier()).thenReturn(15.0);

        // When
        PriceVolumeSurgeIndicator.VolumeSurgeResult result = indicator.calculateVolumeSurge(
                instrumentToken, FIVE_MIN, currentVolume);

        // Then
        assertNotNull(result);
        // The multiplier should be capped to 100.0, not the extreme value
        assertTrue(result.getVolumeMultiplier() <= 100.0, 
                "Volume multiplier should be capped to 100.0, but was: " + result.getVolumeMultiplier());
        assertTrue(result.getRecentVolumeMultiplier() <= 100.0,
                "Recent volume multiplier should be capped to 100.0, but was: " + result.getRecentVolumeMultiplier());
    }

    @Test
    void testVolumeSurgeMultiplier_WithNormalValues_ShouldCalculateCorrectly() {
        // Given
        String instrumentToken = "256265";
        long currentVolume = 1500; // Normal current volume
        double averageVolume = 1000; // Normal average volume (should give 1.5x multiplier)
        
        // Create BarSeries with normal historical volumes
        BarSeries barSeries = createBarSeriesWithNormalVolume(averageVolume);
        when(tickDataManager.getBarSeriesForTimeFrame(anyString(), any(CandleTimeFrameEnum.class)))
                .thenReturn(barSeries);
        when(configService.getCallVolumeSurgeMultiplier()).thenReturn(15.0);

        // When
        PriceVolumeSurgeIndicator.VolumeSurgeResult result = indicator.calculateVolumeSurge(
                instrumentToken, FIVE_MIN, currentVolume);

        // Then
        assertNotNull(result);
        // Should calculate normally without capping
        assertEquals(1.5, result.getVolumeMultiplier(), 0.01);
        assertEquals(1.5, result.getRecentVolumeMultiplier(), 0.01);
    }

    @Test
    void testVolumeSurgeMultiplier_WithZeroAverageVolume_ShouldUseDefault() {
        // Given
        String instrumentToken = "256265";
        long currentVolume = 1000;
        double zeroAverageVolume = 0.0; // Zero average volume
        
        // Create BarSeries with zero historical volumes
        BarSeries barSeries = createBarSeriesWithLowVolume(zeroAverageVolume);
        when(tickDataManager.getBarSeriesForTimeFrame(anyString(), any(CandleTimeFrameEnum.class)))
                .thenReturn(barSeries);
        when(configService.getCallVolumeSurgeMultiplier()).thenReturn(15.0);

        // When
        PriceVolumeSurgeIndicator.VolumeSurgeResult result = indicator.calculateVolumeSurge(
                instrumentToken, FIVE_MIN, currentVolume);

        // Then
        assertNotNull(result);
        // Should use default multiplier of 1.0 when average volume is too low
        assertEquals(1.0, result.getVolumeMultiplier(), 0.01);
        assertEquals(1.0, result.getRecentVolumeMultiplier(), 0.01);
    }

    private BarSeries createBarSeriesWithLowVolume(double averageVolume) {
        BarSeries series = new org.ta4j.core.BaseBarSeriesBuilder().withName("TEST").build();
        ZonedDateTime now = ZonedDateTime.now();
        
        // Create 25 bars with very low volume (to simulate the extreme case)
        for (int i = 0; i < 25; i++) {
            series.addBar(
                    java.time.Duration.ofMinutes(5),
                    now.minusMinutes((25 - i) * 5), // 5-minute intervals
                    100.0, 101.0, 99.0, 100.5, // OHLC
                    averageVolume // Very low volume
            );
        }
        
        return series;
    }

    private BarSeries createBarSeriesWithNormalVolume(double averageVolume) {
        BarSeries series = new org.ta4j.core.BaseBarSeriesBuilder().withName("TEST").build();
        ZonedDateTime now = ZonedDateTime.now();
        
        // Create 25 bars with normal volume
        for (int i = 0; i < 25; i++) {
            series.addBar(
                    java.time.Duration.ofMinutes(5),
                    now.minusMinutes((25 - i) * 5), // 5-minute intervals
                    100.0, 101.0, 99.0, 100.5, // OHLC
                    averageVolume // Normal volume
            );
        }
        
        return series;
    }
}
