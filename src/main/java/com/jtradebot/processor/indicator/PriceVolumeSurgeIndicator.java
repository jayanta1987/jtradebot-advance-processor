package com.jtradebot.processor.indicator;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class PriceVolumeSurgeIndicator {
    private final TickDataManager tickDataManager;
    private final DynamicStrategyConfigService configService;
    
    // Cache for volume surge calculations to prevent redundant calls
    private final Map<String, VolumeSurgeCacheEntry> volumeSurgeCache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION_MS = 1000; // 1 second cache

    public VolumeSurgeResult calculateVolumeSurge(String instrumentToken, CandleTimeFrameEnum timeframe, long currentVolume) {
        try {
            BarSeries barSeries = tickDataManager.getBarSeriesForTimeFrame(instrumentToken, timeframe);
            if (barSeries == null || barSeries.getBarCount() < 20) {
                log.warn("Insufficient data for volume surge calculation: instrument={}, timeframe={}, bars={}",
                        instrumentToken, timeframe, barSeries != null ? barSeries.getBarCount() : 0);
                return VolumeSurgeResult.noSurge();
            }
            double avgVol = calculateAverageVolume(barSeries, 20);
            double recentAvgVol = calculateAverageVolume(barSeries, 5);
            final double MIN_AVG_VOL = 1.0, MAX_VOL_MULT = 100.0;
            log.debug("Volume Analysis - Instrument: {}, Timeframe: {}, Current: {}, Avg(20): {}, Avg(5): {}",
                    instrumentToken, timeframe, currentVolume, avgVol, recentAvgVol);
            if (avgVol < MIN_AVG_VOL || recentAvgVol < MIN_AVG_VOL) {
                log.warn("Very low average volume detected - Instrument: {}, Timeframe: {}, Avg(20): {}, Avg(5): {} - Using default multiplier",
                        instrumentToken, timeframe, avgVol, recentAvgVol);
            }
            double volMult = avgVol > MIN_AVG_VOL ? currentVolume / avgVol : 1.0;
            double recentVolMult = recentAvgVol > MIN_AVG_VOL ? currentVolume / recentAvgVol : 1.0;
            volMult = Math.min(volMult, MAX_VOL_MULT);
            recentVolMult = Math.min(recentVolMult, MAX_VOL_MULT);
            if (volMult >= MAX_VOL_MULT || recentVolMult >= MAX_VOL_MULT) {
                log.warn("Volume multiplier capped to {} - Instrument: {}, Timeframe: {}, Current: {}, Avg(20): {}, Avg(5): {}",
                        MAX_VOL_MULT, instrumentToken, timeframe, currentVolume, avgVol, recentAvgVol);
            }
            VolumeSurgeStrength strength = determineSurgeStrength(volMult, recentVolMult);
            boolean trendingUp = checkVolumeTrend(barSeries);
            double momentum = calculateVolumeMomentum(barSeries);
            double threshold = configService.getCallVolumeSurgeMultiplier();
            return VolumeSurgeResult.builder()
                    .surge(volMult >= threshold)
                    .volumeMultiplier(volMult)
                    .recentVolumeMultiplier(recentVolMult)
                    .averageVolume(avgVol)
                    .recentAverageVolume(recentAvgVol)
                    .currentVolume(currentVolume)
                    .strength(strength)
                    .isVolumeTrendingUp(trendingUp)
                    .volumeMomentum(momentum)
                    .timeframe(timeframe)
                    .build();
        } catch (Exception e) {
            log.error("Error calculating volume surge for instrument: {}, timeframe: {}", instrumentToken, timeframe, e);
            return VolumeSurgeResult.noSurge();
        }
    }

    /**
     * Optimized method to calculate volume surge for all timeframes in a single call
     * This prevents multiple redundant calculations and improves performance
     */
    public MultiTimeframeVolumeSurge calculateMultiTimeframeVolumeSurge(String instrumentToken, long currentVolume) {
        String cacheKey = instrumentToken + "_" + currentVolume;
        long currentTime = System.currentTimeMillis();
        
        // Check cache first
        VolumeSurgeCacheEntry cachedEntry = volumeSurgeCache.get(cacheKey);
        if (cachedEntry != null && (currentTime - cachedEntry.getTimestamp()) < CACHE_DURATION_MS) {
            log.debug("Using cached volume surge data for instrument: {}", instrumentToken);
            return cachedEntry.getResult();
        }
        
        try {
            // Calculate for all timeframes in parallel
            VolumeSurgeResult surge1min = calculateVolumeSurge(instrumentToken, ONE_MIN, currentVolume);
            VolumeSurgeResult surge5min = calculateVolumeSurge(instrumentToken, FIVE_MIN, currentVolume);
            VolumeSurgeResult surge15min = calculateVolumeSurge(instrumentToken, FIFTEEN_MIN, currentVolume);
            
            // Find the highest volume multiplier across all timeframes
            double maxVolumeMultiplier = Math.max(
                Math.max(surge1min.getVolumeMultiplier(), surge5min.getVolumeMultiplier()),
                surge15min.getVolumeMultiplier()
            );
            
            MultiTimeframeVolumeSurge result = MultiTimeframeVolumeSurge.builder()
                    .surge1min(surge1min)
                    .surge5min(surge5min)
                    .surge15min(surge15min)
                    .maxVolumeMultiplier(maxVolumeMultiplier)
                    .hasAnySurge(surge1min.isSurge() || surge5min.isSurge() || surge15min.isSurge())
                    .build();
            
            // Cache the result
            volumeSurgeCache.put(cacheKey, new VolumeSurgeCacheEntry(result, currentTime));
            
            log.debug("Calculated multi-timeframe volume surge for instrument: {} - 1min: {}x, 5min: {}x, 15min: {}x, max: {}x",
                    instrumentToken, surge1min.getVolumeMultiplier(), surge5min.getVolumeMultiplier(), 
                    surge15min.getVolumeMultiplier(), maxVolumeMultiplier);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error calculating multi-timeframe volume surge for instrument: {}", instrumentToken, e);
            return MultiTimeframeVolumeSurge.noSurge();
        }
    }

    public NiftyVolumeAnalysis analyzeNiftyVolume(String niftyFutureToken, long futureVolume) {
        try {
            VolumeSurgeResult futureSurge = calculateVolumeSurge(niftyFutureToken, FIVE_MIN, futureVolume);
            VolumeSurgeResult indexSurge = VolumeSurgeResult.noSurge();
            double divergence = futureSurge.getVolumeMultiplier();
            return NiftyVolumeAnalysis.builder()
                    .indexSurge(indexSurge)
                    .futureSurge(futureSurge)
                    .volumeDivergence(divergence)
                    .totalVolume(futureVolume)
                    .build();
        } catch (Exception e) {
            log.error("Error analyzing Nifty volume for future: {}",  niftyFutureToken, e);
            return NiftyVolumeAnalysis.noAnalysis();
        }
    }

    private double calculateAverageVolume(BarSeries barSeries, int periods) {
        if (barSeries.getBarCount() < periods) return 0.0;
        double sum = 0.0;
        int start = barSeries.getBarCount() - periods;
        for (int i = start; i < barSeries.getBarCount(); i++)
            sum += barSeries.getBar(i).getVolume().doubleValue();
        return sum / periods;
    }

    private VolumeSurgeStrength determineSurgeStrength(double volMult, double recentVolMult) {
        double base = configService.getCallVolumeSurgeMultiplier();
        if (volMult >= base * 2.5 || recentVolMult >= base * 2.5) return VolumeSurgeStrength.EXTREME;
        if (volMult >= base * 1.8 || recentVolMult >= base * 1.8) return VolumeSurgeStrength.HIGH;
        if (volMult >= base * 1.4 || recentVolMult >= base * 1.4) return VolumeSurgeStrength.MEDIUM;
        if (volMult >= base || recentVolMult >= base) return VolumeSurgeStrength.LOW;
        return VolumeSurgeStrength.NONE;
    }

    private boolean checkVolumeTrend(BarSeries barSeries) {
        if (barSeries.getBarCount() < 10) return false;
        double recent = calculateAverageVolume(barSeries, 5);
        double older = 0.0;
        int start = barSeries.getBarCount() - 10, end = barSeries.getBarCount() - 5;
        for (int i = start; i < end; i++)
            older += barSeries.getBar(i).getVolume().doubleValue();
        older /= 5.0;
        return recent > older * 1.05;
    }

    private double calculateVolumeMomentum(BarSeries barSeries) {
        if (barSeries.getBarCount() < 3) return 0.0;
        double curr = barSeries.getBar(barSeries.getBarCount() - 1).getVolume().doubleValue();
        double prev = barSeries.getBar(barSeries.getBarCount() - 2).getVolume().doubleValue();
        if (prev == 0) return 0.0;
        return ((curr - prev) / prev) * 100;
    }


    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolumeSurgeResult {
        private boolean surge;
        private double volumeMultiplier;
        private double recentVolumeMultiplier;
        private double averageVolume;
        private double recentAverageVolume;
        private long currentVolume;
        private VolumeSurgeStrength strength;
        private boolean isVolumeTrendingUp;
        private double volumeMomentum;
        private CandleTimeFrameEnum timeframe;
        
        public boolean isSurge() {
            return surge;
        }

        public static VolumeSurgeResult noSurge() {
            return VolumeSurgeResult.builder().surge(false).volumeMultiplier(1.0).strength(VolumeSurgeStrength.NONE).build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NiftyVolumeAnalysis {
        private VolumeSurgeResult indexSurge;
        private VolumeSurgeResult futureSurge;
        private double volumeCorrelation;
        private boolean isCoordinatedSurge;
        private double volumeDivergence;
        private long totalVolume;
        public static NiftyVolumeAnalysis noAnalysis() {
            return NiftyVolumeAnalysis.builder()
                    .indexSurge(VolumeSurgeResult.noSurge())
                    .futureSurge(VolumeSurgeResult.noSurge())
                    .volumeCorrelation(0.0)
                    .isCoordinatedSurge(false)
                    .volumeDivergence(1.0)
                    .totalVolume(0)
                    .build();
        }
    }

    public enum VolumeSurgeStrength { NONE, LOW, MEDIUM, HIGH, EXTREME }

    /**
     * Cache entry for volume surge calculations
     */
    @Data
    @AllArgsConstructor
    public static class VolumeSurgeCacheEntry {
        private MultiTimeframeVolumeSurge result;
        private long timestamp;
    }

    /**
     * Result containing volume surge data for all timeframes
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MultiTimeframeVolumeSurge {
        private VolumeSurgeResult surge1min;
        private VolumeSurgeResult surge5min;
        private VolumeSurgeResult surge15min;
        private double maxVolumeMultiplier;
        private boolean hasAnySurge;
        
        public static MultiTimeframeVolumeSurge noSurge() {
            return MultiTimeframeVolumeSurge.builder()
                    .surge1min(VolumeSurgeResult.noSurge())
                    .surge5min(VolumeSurgeResult.noSurge())
                    .surge15min(VolumeSurgeResult.noSurge())
                    .maxVolumeMultiplier(1.0)
                    .hasAnySurge(false)
                    .build();
        }
    }
}
