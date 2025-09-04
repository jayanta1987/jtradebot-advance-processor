package com.jtradebot.processor.indicator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

@Component
@Slf4j
public class MACDIndicator {
    
    /**
     * Calculate MACD values for the given timeframe
     */
    public MACDResult calculateMACD(BarSeries barSeries) {
        try {
            if (barSeries == null || barSeries.getBarCount() < 26) {
                log.debug("Insufficient data for MACD calculation: bars={}", 
                         barSeries != null ? barSeries.getBarCount() : 0);
                return MACDResult.noSignal();
            }
            
            ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
            
            // Calculate MACD (12-period EMA - 26-period EMA)
            EMAIndicator ema12 = new EMAIndicator(closePrice, 12);
            EMAIndicator ema26 = new EMAIndicator(closePrice, 26);
            
            // Calculate MACD line manually
            int endIndex = barSeries.getEndIndex();
            double currentEma12 = ema12.getValue(endIndex).doubleValue();
            double currentEma26 = ema26.getValue(endIndex).doubleValue();
            double currentMacd = currentEma12 - currentEma26;
            
            double previousEma12 = endIndex > 0 ? ema12.getValue(endIndex - 1).doubleValue() : currentEma12;
            double previousEma26 = endIndex > 0 ? ema26.getValue(endIndex - 1).doubleValue() : currentEma26;
            double previousMacd = previousEma12 - previousEma26;
            
            // Calculate Signal line (9-period average of MACD)
            double signalLine = calculateSignalLine(barSeries, endIndex);
            double previousSignalLine = endIndex > 0 ? calculateSignalLine(barSeries, endIndex - 1) : signalLine;
            
            // Determine signals
            boolean bullishCrossover = currentMacd > signalLine && previousMacd <= previousSignalLine;
            boolean bearishCrossover = currentMacd < signalLine && previousMacd >= previousSignalLine;
            boolean aboveZero = currentMacd > 0;
            boolean belowZero = currentMacd < 0;
            
            log.debug("MACD Analysis - MACD: {}, Signal: {}, Bullish Crossover: {}, Bearish Crossover: {}, Above Zero: {}", 
                     currentMacd, signalLine, bullishCrossover, bearishCrossover, aboveZero);
            
            return MACDResult.builder()
                    .macdLine(currentMacd)
                    .signalLine(signalLine)
                    .isBullishCrossover(bullishCrossover)
                    .isBearishCrossover(bearishCrossover)
                    .isAboveZero(aboveZero)
                    .isBelowZero(belowZero)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error calculating MACD: {}", e.getMessage(), e);
            return MACDResult.noSignal();
        }
    }
    
    /**
     * Calculate simple signal line (9-period average of MACD)
     */
    private double calculateSignalLine(BarSeries barSeries, int endIndex) {
        try {
            ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
            EMAIndicator ema12 = new EMAIndicator(closePrice, 12);
            EMAIndicator ema26 = new EMAIndicator(closePrice, 26);
            
            double sum = 0.0;
            int periods = Math.min(9, endIndex + 1);
            
            for (int i = endIndex - periods + 1; i <= endIndex; i++) {
                double ema12Value = ema12.getValue(i).doubleValue();
                double ema26Value = ema26.getValue(i).doubleValue();
                sum += (ema12Value - ema26Value);
            }
            
            return sum / periods;
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    // Result class
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MACDResult {
        private double macdLine;
        private double signalLine;
        private boolean isBullishCrossover;
        private boolean isBearishCrossover;
        private boolean isAboveZero;
        private boolean isBelowZero;
        
        public static MACDResult noSignal() {
            return MACDResult.builder()
                    .macdLine(0.0)
                    .signalLine(0.0)
                    .isBullishCrossover(false)
                    .isBearishCrossover(false)
                    .isAboveZero(false)
                    .isBelowZero(false)
                    .build();
        }
    }
}
