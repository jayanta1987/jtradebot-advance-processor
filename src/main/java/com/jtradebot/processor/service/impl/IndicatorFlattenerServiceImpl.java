package com.jtradebot.processor.service.impl;

import com.jtradebot.processor.indicator.MultiEmaIndicator;
import com.jtradebot.processor.indicator.PriceVolumeSurgeIndicator;
import com.jtradebot.processor.indicator.RsiIndicator;
import com.jtradebot.processor.indicator.SupportResistanceIndicator;
import com.jtradebot.processor.indicator.VWAPIndicator;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.indicator.EmaInfo;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.jtradebot.processor.repository.document.TickDocument;
import com.jtradebot.processor.service.IndicatorFlattenerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndicatorFlattenerServiceImpl implements IndicatorFlattenerService {

    private final MultiEmaIndicator multiEmaIndicator;
    private final RsiIndicator rsiIndicator;
    private final PriceVolumeSurgeIndicator priceVolumeSurgeIndicator;
    private final VWAPIndicator vwapIndicator;
    private final SupportResistanceIndicator supportResistanceIndicator;
    private final TickDataManager tickDataManager;

    @Override
    public FlattenedIndicators flattenIndicators(TickDocument tickDocument) {
        log.info("Flattening indicators for instrument: {}", tickDocument.getInstrumentToken());
        
        FlattenedIndicators flattenedIndicators = FlattenedIndicators.builder()
                .instrumentToken(String.valueOf(tickDocument.getInstrumentToken()))
                .timestamp(LocalDateTime.ofInstant(tickDocument.getTickTimestamp().toInstant(), ZoneId.systemDefault()))
                .timeframe("5min") // Default timeframe for scalping
                .build();
        
        // Flatten all indicator types
        flattenEmaIndicators(tickDocument, flattenedIndicators);
        flattenRsiIndicators(tickDocument, flattenedIndicators);
        flattenVolumeIndicators(tickDocument, flattenedIndicators);
        flattenPriceActionIndicators(tickDocument, flattenedIndicators);
        
        log.info("Indicators flattened successfully for instrument: {}", tickDocument.getInstrumentToken());
        return flattenedIndicators;
    }

    @Override
    public void flattenEmaIndicators(TickDocument tickDocument, FlattenedIndicators flattenedIndicators) {
        log.info("🚀 STARTING EMA CALCULATION - Instrument: {}, Timestamp: {}", 
            tickDocument.getInstrumentToken(), tickDocument.getTickTimestamp());
        
        try {
            // Get BarSeries for different timeframes
            BarSeries oneMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tickDocument.getInstrumentToken()), ONE_MIN);
            BarSeries fiveMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tickDocument.getInstrumentToken()), FIVE_MIN);
            BarSeries fifteenMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tickDocument.getInstrumentToken()), FIFTEEN_MIN);

            // Calculate EMA values using BarSeries
            EmaInfo emaInfo_1min = multiEmaIndicator.calculateEmaValues(oneMinSeries, ONE_MIN);
            EmaInfo emaInfo_5min = multiEmaIndicator.calculateEmaValues(fiveMinSeries, FIVE_MIN);
            EmaInfo emaInfo_15min = multiEmaIndicator.calculateEmaValues(fifteenMinSeries, FIFTEEN_MIN);
            
            // Check if EMA values are null
            if (emaInfo_1min == null || emaInfo_5min == null || emaInfo_15min == null) {
                log.error("❌ EMA CALCULATION FAILED - One or more EmaInfo objects are null");
                return;
            }
            
            if (emaInfo_1min.getEma5() == null || emaInfo_1min.getEma34() == null) {
                log.error("❌ EMA VALUES NULL - 1min EMA5: {}, EMA34: {}", emaInfo_1min.getEma5(), emaInfo_1min.getEma34());
                return;
            }

            // Log EMA values for debugging
            log.info("📈 EMA VALUES - Instrument: {}, Timestamp: {}", 
                tickDocument.getInstrumentToken(), tickDocument.getTickTimestamp());
            log.info("   1min - EMA5: {:.2f}, EMA34: {:.2f}, EMA5>EMA34: {}", 
                emaInfo_1min.getEma5(), emaInfo_1min.getEma34(), 
                emaInfo_1min.getEma5() > emaInfo_1min.getEma34());
            log.info("   5min - EMA5: {:.2f}, EMA34: {:.2f}, EMA5>EMA34: {}", 
                emaInfo_5min.getEma5(), emaInfo_5min.getEma34(), 
                emaInfo_5min.getEma5() > emaInfo_5min.getEma34());
            log.info("   15min - EMA5: {:.2f}, EMA34: {:.2f}, EMA5>EMA34: {}", 
                emaInfo_15min.getEma5(), emaInfo_15min.getEma34(), 
                emaInfo_15min.getEma5() > emaInfo_15min.getEma34());
            
            // Flatten to boolean values (EMA5 vs EMA34)
            flattenedIndicators.setEma5_1min_gt_ema34_1min(emaInfo_1min.getEma5() > emaInfo_1min.getEma34());
            flattenedIndicators.setEma5_5min_gt_ema34_5min(emaInfo_5min.getEma5() > emaInfo_5min.getEma34());
            flattenedIndicators.setEma5_15min_gt_ema34_15min(emaInfo_15min.getEma5() > emaInfo_15min.getEma34());
            
            log.info("✅ EMA CALCULATION COMPLETED - Instrument: {}", tickDocument.getInstrumentToken());

        } catch (Exception e) {
            log.error("Error flattening EMA indicators for instrument: {}", tickDocument.getInstrumentToken(), e);
            // Set default values on error (EMA5 vs EMA34)
            flattenedIndicators.setEma5_1min_gt_ema34_1min(false);
            flattenedIndicators.setEma5_5min_gt_ema34_5min(false);
            flattenedIndicators.setEma5_15min_gt_ema34_15min(false);
        }
    }

    @Override
    public void flattenRsiIndicators(TickDocument tickDocument, FlattenedIndicators flattenedIndicators) {
        try {
            // Get BarSeries for different timeframes
            BarSeries oneMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tickDocument.getInstrumentToken()), ONE_MIN);
            BarSeries fiveMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tickDocument.getInstrumentToken()), FIVE_MIN);
            BarSeries fifteenMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tickDocument.getInstrumentToken()), FIFTEEN_MIN);

            // Calculate RSI values using BarSeries (using 14 as the standard RSI period)
            Double rsi_1min = rsiIndicator.getRsiValue(oneMinSeries, 14);
            Double rsi_5min = rsiIndicator.getRsiValue(fiveMinSeries, 14);
            Double rsi_15min = rsiIndicator.getRsiValue(fifteenMinSeries, 14);

            // Flatten to boolean values
            flattenedIndicators.setRsi_1min_gt_70(rsi_1min != null && rsi_1min > 70);
            flattenedIndicators.setRsi_5min_gt_70(rsi_5min != null && rsi_5min > 70);
            flattenedIndicators.setRsi_15min_gt_70(rsi_15min != null && rsi_15min > 70);
            flattenedIndicators.setRsi_1min_lt_30(rsi_1min != null && rsi_1min < 30);
            flattenedIndicators.setRsi_5min_lt_30(rsi_5min != null && rsi_5min < 30);
            flattenedIndicators.setRsi_15min_lt_30(rsi_15min != null && rsi_15min < 30);

        } catch (Exception e) {
            log.error("Error flattening RSI indicators for instrument: {}", tickDocument.getInstrumentToken(), e);
            // Set default values on error
            flattenedIndicators.setRsi_1min_gt_70(false);
            flattenedIndicators.setRsi_5min_gt_70(false);
            flattenedIndicators.setRsi_15min_gt_70(false);
            flattenedIndicators.setRsi_1min_lt_30(false);
            flattenedIndicators.setRsi_5min_lt_30(false);
            flattenedIndicators.setRsi_15min_lt_30(false);
        }
    }

    @Override
    public void flattenVolumeIndicators(TickDocument tickDocument, FlattenedIndicators flattenedIndicators) {
        try {
            // Get current volume from the tick document
            long currentVolume = tickDocument.getVolumeTradedToday() != null ? tickDocument.getVolumeTradedToday() : 0;
            
            // Log the instrument token and volume being processed
            log.info("🔍 VOLUME INDICATOR PROCESSING - Instrument Token: {}, Current Volume: {}, Timestamp: {}", 
                    tickDocument.getInstrumentToken(), currentVolume, tickDocument.getTickTimestamp());
            
            // Get BarSeries for different timeframes
            BarSeries oneMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tickDocument.getInstrumentToken()), ONE_MIN);
            BarSeries fiveMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tickDocument.getInstrumentToken()), FIVE_MIN);
            BarSeries fifteenMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tickDocument.getInstrumentToken()), FIFTEEN_MIN);

            // Get volume surge indicators using enhanced calculation
            Boolean volume_1min_surge = false;
            Boolean volume_5min_surge = false;
            Boolean volume_15min_surge = false;
            Double volumeMultiplier = 1.0;
            
            // 1-minute volume surge
            if (oneMinSeries != null && oneMinSeries.getBarCount() >= 20) {
                try {
                    log.info("📊 CALCULATING 1MIN VOLUME SURGE - Instrument: {}, Volume: {}, BarCount: {}", 
                            tickDocument.getInstrumentToken(), currentVolume, oneMinSeries.getBarCount());
                    
                    PriceVolumeSurgeIndicator.VolumeSurgeResult surge1min = priceVolumeSurgeIndicator.calculateVolumeSurge(
                        String.valueOf(tickDocument.getInstrumentToken()), ONE_MIN, currentVolume);
                    volume_1min_surge = surge1min.hasSurge();
                    if (volumeMultiplier < surge1min.getVolumeMultiplier()) {
                        volumeMultiplier = surge1min.getVolumeMultiplier();
                    }
                } catch (Exception e) {
                    log.warn("Error calculating 1min volume surge", e);
                }
            }
            
            // 5-minute volume surge
            if (fiveMinSeries != null && fiveMinSeries.getBarCount() >= 20) {
                try {
                    log.info("📊 CALCULATING 5MIN VOLUME SURGE - Instrument: {}, Volume: {}, BarCount: {}", 
                            tickDocument.getInstrumentToken(), currentVolume, fiveMinSeries.getBarCount());
                    
                    PriceVolumeSurgeIndicator.VolumeSurgeResult surge5min = priceVolumeSurgeIndicator.calculateVolumeSurge(
                        String.valueOf(tickDocument.getInstrumentToken()), FIVE_MIN, currentVolume);
                    volume_5min_surge = surge5min.hasSurge();
                    if (volumeMultiplier < surge5min.getVolumeMultiplier()) {
                        volumeMultiplier = surge5min.getVolumeMultiplier();
                    }
                } catch (Exception e) {
                    log.warn("Error calculating 5min volume surge", e);
                }
            }
            
            // 15-minute volume surge
            if (fifteenMinSeries != null && fifteenMinSeries.getBarCount() >= 20) {
                try {
                    log.info("📊 CALCULATING 15MIN VOLUME SURGE - Instrument: {}, Volume: {}, BarCount: {}", 
                            tickDocument.getInstrumentToken(), currentVolume, fifteenMinSeries.getBarCount());
                    
                    PriceVolumeSurgeIndicator.VolumeSurgeResult surge15min = priceVolumeSurgeIndicator.calculateVolumeSurge(
                        String.valueOf(tickDocument.getInstrumentToken()), FIFTEEN_MIN, currentVolume);
                    volume_15min_surge = surge15min.hasSurge();
                    if (volumeMultiplier < surge15min.getVolumeMultiplier()) {
                        volumeMultiplier = surge15min.getVolumeMultiplier();
                    }
                } catch (Exception e) {
                    log.warn("Error calculating 15min volume surge", e);
                }
            }

            // Flatten to boolean values
            flattenedIndicators.setVolume_1min_surge(volume_1min_surge != null ? volume_1min_surge : false);
            flattenedIndicators.setVolume_5min_surge(volume_5min_surge != null ? volume_5min_surge : false);
            flattenedIndicators.setVolume_15min_surge(volume_15min_surge != null ? volume_15min_surge : false);
            flattenedIndicators.setVolume_surge_multiplier(volumeMultiplier);

        } catch (Exception e) {
            log.error("Error flattening volume indicators for instrument: {}", tickDocument.getInstrumentToken(), e);
            // Set default values on error
            flattenedIndicators.setVolume_1min_surge(false);
            flattenedIndicators.setVolume_5min_surge(false);
            flattenedIndicators.setVolume_15min_surge(false);
            flattenedIndicators.setVolume_surge_multiplier(1.0);
        }
    }

    @Override
    public void flattenPriceActionIndicators(TickDocument tickDocument, FlattenedIndicators flattenedIndicators) {
        try {
            // Get BarSeries for different timeframes
            BarSeries oneMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tickDocument.getInstrumentToken()), ONE_MIN);
            BarSeries fiveMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tickDocument.getInstrumentToken()), FIVE_MIN);
            BarSeries fifteenMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tickDocument.getInstrumentToken()), FIFTEEN_MIN);

            // Get VWAP values using BarSeries
            Double vwap_1min = vwapIndicator.calculateVWAP(oneMinSeries);
            Double vwap_5min = vwapIndicator.calculateVWAP(fiveMinSeries);
            Double vwap_15min = vwapIndicator.calculateVWAP(fifteenMinSeries);

            Double currentPrice = tickDocument.getLastTradedPrice();

            // Flatten price vs VWAP comparisons
            flattenedIndicators.setPrice_gt_vwap_1min(vwap_1min != null && currentPrice > vwap_1min);
            flattenedIndicators.setPrice_gt_vwap_5min(vwap_5min != null && currentPrice > vwap_5min);
            flattenedIndicators.setPrice_gt_vwap_15min(vwap_15min != null && currentPrice > vwap_15min);

            flattenedIndicators.setPrice_lt_vwap_1min(vwap_1min != null && currentPrice < vwap_1min);
            flattenedIndicators.setPrice_lt_vwap_5min(vwap_5min != null && currentPrice < vwap_5min);
            flattenedIndicators.setPrice_lt_vwap_15min(vwap_15min != null && currentPrice < vwap_15min);

            // For support/resistance, we'll use a simplified approach since the existing methods require more complex parameters
            // We'll check if price is near recent highs/lows as a proxy for support/resistance
            Boolean priceAboveResistance = isPriceNearRecentHigh(fiveMinSeries, currentPrice);
            Boolean priceBelowSupport = isPriceNearRecentLow(fiveMinSeries, currentPrice);

            flattenedIndicators.setPrice_above_resistance(priceAboveResistance != null ? priceAboveResistance : false);
            flattenedIndicators.setPrice_below_support(priceBelowSupport != null ? priceBelowSupport : false);

        } catch (Exception e) {
            log.error("Error flattening price action indicators for instrument: {}", tickDocument.getInstrumentToken(), e);
            // Set default values on error
            flattenedIndicators.setPrice_gt_vwap_1min(false);
            flattenedIndicators.setPrice_gt_vwap_5min(false);
            flattenedIndicators.setPrice_gt_vwap_15min(false);
            flattenedIndicators.setPrice_lt_vwap_1min(false);
            flattenedIndicators.setPrice_lt_vwap_5min(false);
            flattenedIndicators.setPrice_lt_vwap_15min(false);
            flattenedIndicators.setPrice_above_resistance(false);
            flattenedIndicators.setPrice_below_support(false);
        }
    }



    /**
     * Check if price is near recent high (resistance)
     */
    private Boolean isPriceNearRecentHigh(BarSeries series, Double currentPrice) {
        try {
            int endIndex = series.getEndIndex();
            if (endIndex < 5) return false;

            double recentHigh = 0.0;
            for (int i = endIndex - 5; i <= endIndex; i++) {
                double high = series.getBar(i).getHighPrice().doubleValue();
                if (high > recentHigh) {
                    recentHigh = high;
                }
            }

            // Check if current price is within 1% of recent high
            return Math.abs(currentPrice - recentHigh) / recentHigh < 0.01;
        } catch (Exception e) {
            log.error("Error checking price near recent high", e);
            return false;
        }
    }

    /**
     * Check if price is near recent low (support)
     */
    private Boolean isPriceNearRecentLow(BarSeries series, Double currentPrice) {
        try {
            int endIndex = series.getEndIndex();
            if (endIndex < 5) return false;

            double recentLow = Double.MAX_VALUE;
            for (int i = endIndex - 5; i <= endIndex; i++) {
                double low = series.getBar(i).getLowPrice().doubleValue();
                if (low < recentLow) {
                    recentLow = low;
                }
            }

            // Check if current price is within 1% of recent low
            return Math.abs(currentPrice - recentLow) / recentLow < 0.01;
        } catch (Exception e) {
            log.error("Error checking price near recent low", e);
            return false;
        }
    }
}
