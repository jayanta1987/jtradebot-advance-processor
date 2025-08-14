package com.jtradebot.processor.indicator;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;

import java.util.List;
import java.util.OptionalDouble;

import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class PriceVolumeSurgeIndicator {
    
    private final TickDataManager tickDataManager;
    private final DynamicStrategyConfigService configService;
    
    /**
     * Enhanced volume surge calculation with proper historical baseline
     */
    public VolumeSurgeResult calculateVolumeSurge(String instrumentToken, CandleTimeFrameEnum timeframe, long currentVolume) {
        try {
            BarSeries barSeries = tickDataManager.getBarSeriesForTimeFrame(instrumentToken, timeframe);
            
            if (barSeries == null || barSeries.getBarCount() < 20) {
                log.warn("Insufficient data for volume surge calculation: instrument={}, timeframe={}, bars={}", 
                        instrumentToken, timeframe, barSeries != null ? barSeries.getBarCount() : 0);
                return VolumeSurgeResult.noSurge();
            }
            
            // Calculate historical volume averages
            double averageVolume = calculateAverageVolume(barSeries, 20); // Last 20 bars
            double recentAverageVolume = calculateAverageVolume(barSeries, 5); // Last 5 bars
            
            // Log volume data for debugging
            log.debug("Volume Analysis - Instrument: {}, Timeframe: {}, Current: {}, Avg(20): {}, Avg(5): {}", 
                    instrumentToken, timeframe, currentVolume, averageVolume, recentAverageVolume);
            
            // Calculate volume surge multiplier with safety checks
            double volumeMultiplier = averageVolume > 0 ? currentVolume / averageVolume : 1.0;
            double recentVolumeMultiplier = recentAverageVolume > 0 ? currentVolume / recentAverageVolume : 1.0;
            
            // Determine surge strength
            VolumeSurgeStrength strength = determineSurgeStrength(volumeMultiplier, recentVolumeMultiplier);
            
            // Check for volume trend consistency
            boolean isVolumeTrendingUp = checkVolumeTrend(barSeries);
            
            // Calculate volume momentum (rate of change)
            double volumeMomentum = calculateVolumeMomentum(barSeries);
            
            // Use config service for volume surge threshold (instead of hardcoded 1.3)
            double volumeSurgeThreshold = configService.getCallVolumeSurgeMultiplier();
            
            return VolumeSurgeResult.builder()
                    .hasSurge(volumeMultiplier >= volumeSurgeThreshold)  // Use config instead of hardcoded
                    .volumeMultiplier(volumeMultiplier)
                    .recentVolumeMultiplier(recentVolumeMultiplier)
                    .averageVolume(averageVolume)
                    .recentAverageVolume(recentAverageVolume)
                    .currentVolume(currentVolume)
                    .strength(strength)
                    .isVolumeTrendingUp(isVolumeTrendingUp)
                    .volumeMomentum(volumeMomentum)
                    .timeframe(timeframe)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error calculating volume surge for instrument: {}, timeframe: {}", instrumentToken, timeframe, e);
            return VolumeSurgeResult.noSurge();
        }
    }
    
    /**
     * Calculate volume surge for Nifty index and future comparison
     * Note: Nifty Index doesn't have volume data, so we only analyze future volume
     * and correlate it with index price movements
     */
    public NiftyVolumeAnalysis analyzeNiftyVolume(String niftyIndexToken, String niftyFutureToken, 
                                                 long futureVolume) {
        try {
            // Calculate volume surge for future instrument only (index has no volume)
            VolumeSurgeResult futureSurge = calculateVolumeSurge(niftyFutureToken, FIVE_MIN, futureVolume);
            
            // Create a dummy index surge result since index has no volume
            VolumeSurgeResult indexSurge = VolumeSurgeResult.noSurge();
            
            // Calculate volume correlation between index and future (uses historical data)
            double volumeCorrelation = calculateVolumeCorrelation(niftyIndexToken, niftyFutureToken);
            
            // Determine if this is a coordinated volume surge
            // Since index has no volume, coordinated surge is based on future surge + correlation
            boolean isCoordinatedSurge = futureSurge.hasSurge() && volumeCorrelation > 0.7;
            
            // Volume divergence is not meaningful since index has no volume
            // We'll use a fixed value or calculate based on future volume strength
            double volumeDivergence = futureSurge.getVolumeMultiplier(); // Use future surge strength instead
            
            return NiftyVolumeAnalysis.builder()
                    .indexSurge(indexSurge)
                    .futureSurge(futureSurge)
                    .volumeCorrelation(volumeCorrelation)
                    .isCoordinatedSurge(isCoordinatedSurge)
                    .volumeDivergence(volumeDivergence)
                    .totalVolume(futureVolume) // Only future volume since index has no volume
                    .build();
                    
        } catch (Exception e) {
            log.error("Error analyzing Nifty volume for index: {}, future: {}", niftyIndexToken, niftyFutureToken, e);
            return NiftyVolumeAnalysis.noAnalysis();
        }
    }
    
    private double calculateAverageVolume(BarSeries barSeries, int periods) {
        if (barSeries.getBarCount() < periods) {
            return 0.0;
        }
        
        double sum = 0.0;
        int startIndex = barSeries.getBarCount() - periods;
        
        for (int i = startIndex; i < barSeries.getBarCount(); i++) {
            sum += barSeries.getBar(i).getVolume().doubleValue();
        }
        
        return sum / periods;
    }
    
    private VolumeSurgeStrength determineSurgeStrength(double volumeMultiplier, double recentVolumeMultiplier) {
        // Use config service for thresholds instead of hardcoded values
        double baseThreshold = configService.getCallVolumeSurgeMultiplier();
        
        if (volumeMultiplier >= baseThreshold * 2.5 || recentVolumeMultiplier >= baseThreshold * 2.5) {
            return VolumeSurgeStrength.EXTREME;
        } else if (volumeMultiplier >= baseThreshold * 1.8 || recentVolumeMultiplier >= baseThreshold * 1.8) {
            return VolumeSurgeStrength.HIGH;
        } else if (volumeMultiplier >= baseThreshold * 1.4 || recentVolumeMultiplier >= baseThreshold * 1.4) {
            return VolumeSurgeStrength.MEDIUM;
        } else if (volumeMultiplier >= baseThreshold || recentVolumeMultiplier >= baseThreshold) {
            return VolumeSurgeStrength.LOW;
        } else {
            return VolumeSurgeStrength.NONE;
        }
    }
    
    private boolean checkVolumeTrend(BarSeries barSeries) {
        if (barSeries.getBarCount() < 10) {
            return false;
        }
        
        // Check if volume is trending upward in last 10 bars
        double recentVolume = calculateAverageVolume(barSeries, 5);
        
        // Calculate older volume manually since subseries method doesn't exist
        double olderVolume = 0.0;
        int startIndex = barSeries.getBarCount() - 10;
        int endIndex = barSeries.getBarCount() - 5;
        for (int i = startIndex; i < endIndex; i++) {
            olderVolume += barSeries.getBar(i).getVolume().doubleValue();
        }
        olderVolume = olderVolume / 5.0;
        
        return recentVolume > olderVolume * 1.05; // 5% increase (relaxed from 10%)
    }
    
    private double calculateVolumeMomentum(BarSeries barSeries) {
        if (barSeries.getBarCount() < 3) {
            return 0.0;
        }
        
        // Calculate rate of change in volume
        double currentVolume = barSeries.getBar(barSeries.getBarCount() - 1).getVolume().doubleValue();
        double previousVolume = barSeries.getBar(barSeries.getBarCount() - 2).getVolume().doubleValue();
        
        if (previousVolume == 0) {
            return 0.0;
        }
        
        return ((currentVolume - previousVolume) / previousVolume) * 100; // Percentage change
    }
    
    private double calculateVolumeCorrelation(String indexToken, String futureToken) {
        try {
            BarSeries indexSeries = tickDataManager.getBarSeriesForTimeFrame(indexToken, FIVE_MIN);
            BarSeries futureSeries = tickDataManager.getBarSeriesForTimeFrame(futureToken, FIVE_MIN);
            
            if (indexSeries == null || futureSeries == null || 
                indexSeries.getBarCount() < 20 || futureSeries.getBarCount() < 20) {
                return 0.0;
            }
            
            // Calculate correlation for last 20 bars
            int periods = Math.min(20, Math.min(indexSeries.getBarCount(), futureSeries.getBarCount()));
            double[] indexVolumes = new double[periods];
            double[] futureVolumes = new double[periods];
            
            for (int i = 0; i < periods; i++) {
                indexVolumes[i] = indexSeries.getBar(indexSeries.getBarCount() - periods + i).getVolume().doubleValue();
                futureVolumes[i] = futureSeries.getBar(futureSeries.getBarCount() - periods + i).getVolume().doubleValue();
            }
            
            return calculateCorrelation(indexVolumes, futureVolumes);
            
        } catch (Exception e) {
            log.error("Error calculating volume correlation", e);
            return 0.0;
        }
    }
    
    private double calculateCorrelation(double[] x, double[] y) {
        if (x.length != y.length || x.length == 0) {
            return 0.0;
        }
        
        double sumX = 0.0, sumY = 0.0, sumXY = 0.0, sumX2 = 0.0, sumY2 = 0.0;
        int n = x.length;
        
        for (int i = 0; i < n; i++) {
            sumX += x[i];
            sumY += y[i];
            sumXY += x[i] * y[i];
            sumX2 += x[i] * x[i];
            sumY2 += y[i] * y[i];
        }
        
        double numerator = n * sumXY - sumX * sumY;
        double denominator = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));
        
        return denominator == 0 ? 0.0 : numerator / denominator;
    }
    
    // Data classes for results
    public static class VolumeSurgeResult {
        private boolean hasSurge;
        private double volumeMultiplier;
        private double recentVolumeMultiplier;
        private double averageVolume;
        private double recentAverageVolume;
        private long currentVolume;
        private VolumeSurgeStrength strength;
        private boolean isVolumeTrendingUp;
        private double volumeMomentum;
        private CandleTimeFrameEnum timeframe;
        
        public static VolumeSurgeResult noSurge() {
            VolumeSurgeResult result = new VolumeSurgeResult();
            result.hasSurge = false;
            result.volumeMultiplier = 1.0;
            result.strength = VolumeSurgeStrength.NONE;
            return result;
        }
        
        // Builder pattern implementation
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private VolumeSurgeResult result = new VolumeSurgeResult();
            
            public Builder hasSurge(boolean hasSurge) {
                result.hasSurge = hasSurge;
                return this;
            }
            
            public Builder volumeMultiplier(double volumeMultiplier) {
                result.volumeMultiplier = volumeMultiplier;
                return this;
            }
            
            public Builder recentVolumeMultiplier(double recentVolumeMultiplier) {
                result.recentVolumeMultiplier = recentVolumeMultiplier;
                return this;
            }
            
            public Builder averageVolume(double averageVolume) {
                result.averageVolume = averageVolume;
                return this;
            }
            
            public Builder recentAverageVolume(double recentAverageVolume) {
                result.recentAverageVolume = recentAverageVolume;
                return this;
            }
            
            public Builder currentVolume(long currentVolume) {
                result.currentVolume = currentVolume;
                return this;
            }
            
            public Builder strength(VolumeSurgeStrength strength) {
                result.strength = strength;
                return this;
            }
            
            public Builder isVolumeTrendingUp(boolean isVolumeTrendingUp) {
                result.isVolumeTrendingUp = isVolumeTrendingUp;
                return this;
            }
            
            public Builder volumeMomentum(double volumeMomentum) {
                result.volumeMomentum = volumeMomentum;
                return this;
            }
            
            public Builder timeframe(CandleTimeFrameEnum timeframe) {
                result.timeframe = timeframe;
                return this;
            }
            
            public VolumeSurgeResult build() {
                return result;
            }
        }
        
        // Getters
        public boolean hasSurge() { return hasSurge; }
        public double getVolumeMultiplier() { return volumeMultiplier; }
        public double getRecentVolumeMultiplier() { return recentVolumeMultiplier; }
        public double getAverageVolume() { return averageVolume; }
        public double getRecentAverageVolume() { return recentAverageVolume; }
        public long getCurrentVolume() { return currentVolume; }
        public VolumeSurgeStrength getStrength() { return strength; }
        public boolean isVolumeTrendingUp() { return isVolumeTrendingUp; }
        public double getVolumeMomentum() { return volumeMomentum; }
        public CandleTimeFrameEnum getTimeframe() { return timeframe; }
    }
    
    public static class NiftyVolumeAnalysis {
        private VolumeSurgeResult indexSurge;
        private VolumeSurgeResult futureSurge;
        private double volumeCorrelation;
        private boolean isCoordinatedSurge;
        private double volumeDivergence;
        private long totalVolume;
        
        public static NiftyVolumeAnalysis noAnalysis() {
            NiftyVolumeAnalysis analysis = new NiftyVolumeAnalysis();
            analysis.indexSurge = VolumeSurgeResult.noSurge();
            analysis.futureSurge = VolumeSurgeResult.noSurge();
            analysis.volumeCorrelation = 0.0;
            analysis.isCoordinatedSurge = false;
            analysis.volumeDivergence = 1.0;
            analysis.totalVolume = 0;
            return analysis;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private NiftyVolumeAnalysis analysis = new NiftyVolumeAnalysis();
            
            public Builder indexSurge(VolumeSurgeResult indexSurge) {
                analysis.indexSurge = indexSurge;
                return this;
            }
            
            public Builder futureSurge(VolumeSurgeResult futureSurge) {
                analysis.futureSurge = futureSurge;
                return this;
            }
            
            public Builder volumeCorrelation(double volumeCorrelation) {
                analysis.volumeCorrelation = volumeCorrelation;
                return this;
            }
            
            public Builder isCoordinatedSurge(boolean isCoordinatedSurge) {
                analysis.isCoordinatedSurge = isCoordinatedSurge;
                return this;
            }
            
            public Builder volumeDivergence(double volumeDivergence) {
                analysis.volumeDivergence = volumeDivergence;
                return this;
            }
            
            public Builder totalVolume(long totalVolume) {
                analysis.totalVolume = totalVolume;
                return this;
            }
            
            public NiftyVolumeAnalysis build() {
                return analysis;
            }
        }
        
        // Getters
        public VolumeSurgeResult getIndexSurge() { return indexSurge; }
        public VolumeSurgeResult getFutureSurge() { return futureSurge; }
        public double getVolumeCorrelation() { return volumeCorrelation; }
        public boolean isCoordinatedSurge() { return isCoordinatedSurge; }
        public double getVolumeDivergence() { return volumeDivergence; }
        public long getTotalVolume() { return totalVolume; }
    }
    
    public enum VolumeSurgeStrength {
        NONE, LOW, MEDIUM, HIGH, EXTREME
    }
}
