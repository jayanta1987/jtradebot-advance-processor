package com.jtradebot.processor.service.impl;

import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.FlattenedIndicators;
import com.jtradebot.processor.model.FuturesignalData;
import com.jtradebot.processor.model.ScalpingVolumeSurgeCallRule;
import com.jtradebot.processor.model.ScalpingVolumeSurgePutRule;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.jtradebot.processor.service.ScalpingVolumeSurgeService;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;

import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScalpingVolumeSurgeServiceImpl implements ScalpingVolumeSurgeService {
    
    private final TickDataManager tickDataManager;
    
    // Default rules - these can be made configurable later
    private final ScalpingVolumeSurgeCallRule callRule = ScalpingVolumeSurgeCallRule.builder()
            .ruleName("SCALPING_FUTURE_VOLUME_SURGE_BULLISH")
            .strategyType("SCALPING_FUTURE_VOLUME_SURGE_BULLISH")
            .isActive(true)
            .requireAllTimeframesBullish(true)
            .requireVolumeSurge(true)
            .requireEmaCrossover(true)
            .requireRsiOverbought(true)
            .requirePriceAboveVwap(true)
            .requirePriceAboveResistance(true)
            .minVolumeSurgeMultiplier(2.0)
            .minRsiThreshold(70.0)
            .minSignalStrength(7.0)
            .check1Min(true)
            .check5Min(true)
            .check15Min(true)
            .stopLossPercentage(0.5)
            .targetPercentage(1.0)
            .maxHoldingTimeMinutes(30)
            .build();
    
    private final ScalpingVolumeSurgePutRule putRule = ScalpingVolumeSurgePutRule.builder()
            .ruleName("SCALPING_FUTURE_VOLUME_SURGE_BEARISH")
            .strategyType("SCALPING_FUTURE_VOLUME_SURGE_BEARISH")
            .isActive(true)
            .requireAllTimeframesBearish(true)
            .requireVolumeSurge(true)
            .requireEmaCrossover(true)
            .requireRsiOversold(true)
            .requirePriceBelowVwap(true)
            .requirePriceBelowSupport(true)
            .minVolumeSurgeMultiplier(2.0)
            .maxRsiThreshold(30.0)
            .minSignalStrength(7.0)
            .check1Min(true)
            .check5Min(true)
            .check15Min(true)
            .stopLossPercentage(0.5)
            .targetPercentage(1.0)
            .maxHoldingTimeMinutes(30)
            .build();
    
    @Override
    public boolean shouldMakeCallEntry(Tick tick) {
        try {
            FlattenedIndicators indicators = getFlattenedIndicators(tick);
            
            // Check if all required conditions are met for CALL entry
            boolean emaCrossover = indicators.getEma9_5min_gt_ema21_5min() && 
                                 indicators.getEma9_1min_gt_ema21_1min();
            
            boolean rsiOverbought = indicators.getRsi_5min_gt_70() && 
                                   indicators.getRsi_1min_gt_70();
            
            boolean volumeSurge = indicators.getVolume_5min_surge() && 
                                 indicators.getVolume_surge_multiplier() >= callRule.getMinVolumeSurgeMultiplier();
            
            boolean priceAction = indicators.getPrice_gt_vwap_5min() && 
                                 indicators.getPrice_above_resistance();
            
            boolean futuresignals = indicators.getFuturesignals() != null && 
                                   indicators.getFuturesignals().getAllTimeframesBullish();
            
            return emaCrossover && rsiOverbought && volumeSurge && priceAction && futuresignals;
            
        } catch (Exception e) {
            log.error("Error evaluating CALL entry for tick: {}", tick.getInstrumentToken(), e);
            return false;
        }
    }
    
    @Override
    public boolean shouldMakePutEntry(Tick tick) {
        try {
            FlattenedIndicators indicators = getFlattenedIndicators(tick);
            
            // Check if all required conditions are met for PUT entry
            boolean emaCrossover = !indicators.getEma9_5min_gt_ema21_5min() && 
                                 !indicators.getEma9_1min_gt_ema21_1min();
            
            boolean rsiOversold = indicators.getRsi_5min_lt_30() && 
                                 indicators.getRsi_1min_lt_30();
            
            boolean volumeSurge = indicators.getVolume_5min_surge() && 
                                 indicators.getVolume_surge_multiplier() >= putRule.getMinVolumeSurgeMultiplier();
            
            boolean priceAction = indicators.getPrice_lt_vwap_5min() && 
                                 indicators.getPrice_below_support();
            
            boolean futuresignals = indicators.getFuturesignals() != null && 
                                   indicators.getFuturesignals().getAllTimeframesBearish();
            
            return emaCrossover && rsiOversold && volumeSurge && priceAction && futuresignals;
            
        } catch (Exception e) {
            log.error("Error evaluating PUT entry for tick: {}", tick.getInstrumentToken(), e);
            return false;
        }
    }
    
    @Override
    public FlattenedIndicators getFlattenedIndicators(Tick tick) {
        try {
            FlattenedIndicators indicators = new FlattenedIndicators();
            indicators.setInstrumentToken(String.valueOf(tick.getInstrumentToken()));
            
            // Get BarSeries for different timeframes
            BarSeries oneMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tick.getInstrumentToken()), ONE_MIN);
            BarSeries fiveMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tick.getInstrumentToken()), FIVE_MIN);
            BarSeries fifteenMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tick.getInstrumentToken()), FIFTEEN_MIN);
            
            // Flatten EMA indicators
            flattenEmaIndicators(indicators, oneMinSeries, fiveMinSeries, fifteenMinSeries);
            
            // Flatten RSI indicators
            flattenRsiIndicators(indicators, oneMinSeries, fiveMinSeries, fifteenMinSeries);
            
            // Flatten volume indicators
            flattenVolumeIndicators(indicators, oneMinSeries, fiveMinSeries, fifteenMinSeries);
            
            // Flatten price action indicators
            flattenPriceActionIndicators(indicators, oneMinSeries, fiveMinSeries, fifteenMinSeries, tick);
            
            // Calculate futuresignals
            indicators.setFuturesignals(calculateFuturesignals(indicators));
            
            return indicators;
            
        } catch (Exception e) {
            log.error("Error getting flattened indicators for tick: {}", tick.getInstrumentToken(), e);
            return new FlattenedIndicators();
        }
    }
    
    @Override
    public String getRecommendedStrategy(Tick tick) {
        if (shouldMakeCallEntry(tick)) {
            return "CALL";
        } else if (shouldMakePutEntry(tick)) {
            return "PUT";
        } else {
            return "NO_TRADE";
        }
    }
    
    @Override
    public Double getStrategyConfidence(Tick tick) {
        try {
            FlattenedIndicators indicators = getFlattenedIndicators(tick);
            double confidence = 0.0;
            
            // Calculate confidence based on how many conditions are met
            if (indicators.getEma9_5min_gt_ema21_5min()) confidence += 0.2;
            if (indicators.getRsi_5min_gt_70()) confidence += 0.2;
            if (indicators.getVolume_5min_surge()) confidence += 0.2;
            if (indicators.getPrice_gt_vwap_5min()) confidence += 0.2;
            if (indicators.getPrice_above_resistance()) confidence += 0.2;
            
            return Math.min(confidence, 1.0);
            
        } catch (Exception e) {
            log.error("Error calculating strategy confidence for tick: {}", tick.getInstrumentToken(), e);
            return 0.0;
        }
    }
    
    private void flattenEmaIndicators(FlattenedIndicators indicators, BarSeries oneMinSeries, BarSeries fiveMinSeries, BarSeries fifteenMinSeries) {
        try {
            // This is a simplified approach - in a real implementation, you'd get actual EMA values
            // For now, we'll set default values
            indicators.setEma9_1min_gt_ema21_1min(false);
            indicators.setEma9_5min_gt_ema21_5min(false);
            indicators.setEma9_15min_gt_ema21_15min(false);
            
            // TODO: Implement actual EMA calculation using the existing MultiEmaIndicator
            // This would require getting the actual EMA values from the BarSeries
            
        } catch (Exception e) {
            log.error("Error flattening EMA indicators", e);
        }
    }
    
    private void flattenRsiIndicators(FlattenedIndicators indicators, BarSeries oneMinSeries, BarSeries fiveMinSeries, BarSeries fifteenMinSeries) {
        try {
            // This is a simplified approach - in a real implementation, you'd get actual RSI values
            // For now, we'll set default values
            indicators.setRsi_1min_gt_70(false);
            indicators.setRsi_5min_gt_70(false);
            indicators.setRsi_15min_gt_70(false);
            indicators.setRsi_1min_lt_30(false);
            indicators.setRsi_5min_lt_30(false);
            indicators.setRsi_15min_lt_30(false);
            
            // TODO: Implement actual RSI calculation using the existing RsiIndicator
            // This would require getting the actual RSI values from the BarSeries
            
        } catch (Exception e) {
            log.error("Error flattening RSI indicators", e);
        }
    }
    
    private void flattenVolumeIndicators(FlattenedIndicators indicators, BarSeries oneMinSeries, BarSeries fiveMinSeries, BarSeries fifteenMinSeries) {
        try {
            // This is a simplified approach - in a real implementation, you'd get actual volume surge values
            // For now, we'll set default values
            indicators.setVolume_1min_surge(false);
            indicators.setVolume_5min_surge(false);
            indicators.setVolume_15min_surge(false);
            indicators.setVolume_surge_multiplier(1.0);
            
            // TODO: Implement actual volume surge calculation using the existing PriceVolumeSurgeIndicator
            // This would require getting the actual volume data from the BarSeries
            
        } catch (Exception e) {
            log.error("Error flattening volume indicators", e);
        }
    }
    
    private void flattenPriceActionIndicators(FlattenedIndicators indicators, BarSeries oneMinSeries, BarSeries fiveMinSeries, BarSeries fifteenMinSeries, Tick tick) {
        try {
            // This is a simplified approach - in a real implementation, you'd get actual VWAP and S/R values
            // For now, we'll set default values
            indicators.setPrice_gt_vwap_1min(false);
            indicators.setPrice_gt_vwap_5min(false);
            indicators.setPrice_gt_vwap_15min(false);
            indicators.setPrice_lt_vwap_1min(false);
            indicators.setPrice_lt_vwap_5min(false);
            indicators.setPrice_lt_vwap_15min(false);
            indicators.setPrice_above_resistance(false);
            indicators.setPrice_below_support(false);
            
            // TODO: Implement actual VWAP and Support/Resistance calculation using existing indicators
            // This would require getting the actual values from the BarSeries
            
        } catch (Exception e) {
            log.error("Error flattening price action indicators", e);
        }
    }
    
    private FuturesignalData calculateFuturesignals(FlattenedIndicators indicators) {
        // This is a simplified futuresignal calculation
        // In a real implementation, this would be more sophisticated
        return FuturesignalData.builder()
                .oneMinBullishSurge(indicators.getEma9_1min_gt_ema21_1min() && indicators.getRsi_1min_gt_70())
                .fiveMinBullishSurge(indicators.getEma9_5min_gt_ema21_5min() && indicators.getRsi_5min_gt_70())
                .fifteenMinBullishSurge(indicators.getEma9_15min_gt_ema21_15min() && indicators.getRsi_15min_gt_70())
                .oneMinBearishSurge(indicators.getEma9_1min_gt_ema21_1min() == false && indicators.getRsi_1min_lt_30())
                .fiveMinBearishSurge(indicators.getEma9_5min_gt_ema21_5min() == false && indicators.getRsi_5min_lt_30())
                .fifteenMinBearishSurge(indicators.getEma9_15min_gt_ema21_15min() == false && indicators.getRsi_15min_lt_30())
                .allTimeframesBullish(false) // Will be calculated based on actual data
                .allTimeframesBearish(false) // Will be calculated based on actual data
                .build();
    }
}
