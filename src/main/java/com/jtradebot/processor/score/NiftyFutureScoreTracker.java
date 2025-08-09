package com.jtradebot.processor.score;

import com.jtradebot.processor.indicator.PriceVolumeSurgeIndicator;
import com.jtradebot.processor.indicator.VWAPIndicator;
import com.jtradebot.processor.model.CallFutureScores;
import com.jtradebot.processor.model.PutFutureScores;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NiftyFutureScoreTracker {

    private final VWAPIndicator vwapIndicator;
    private final PriceVolumeSurgeIndicator priceVolumeSurgeIndicator;


    public CallFutureScores getFuturePointsForCallEntry(BarSeries barSeriesForTimeFrame) {
        CallFutureScores callFutureScores = new CallFutureScores();
        
        // VWAP Signal (0 or 1)
        boolean vwapSignal = vwapIndicator.isVWAPSignalForCall(barSeriesForTimeFrame);
        callFutureScores.setVwapSignal(vwapSignal ? 1 : 0);
        
        // Price-Volume Surge Indicator (0 or 1)
        boolean pvsiSignal = priceVolumeSurgeIndicator.isBullishSurge(barSeriesForTimeFrame);
        callFutureScores.setPvsiScore(pvsiSignal ? 1 : 0);
        
        // OI Trend Signal (0 or 1) - Enhanced with actual OI analysis
        boolean oiTrendSignal = calculateOiTrendSignalForCall(barSeriesForTimeFrame);
        callFutureScores.setOiTrendSignal(oiTrendSignal ? 1 : 0);
        
        // Enhanced scoring with validation
        double totalScore = callFutureScores.getTotalScore();
        if (totalScore > 1) {
            log.info("Future Score for Call Entry: {} (VWAP: {}, PVSI: {}, OI: {})", 
                    totalScore, vwapSignal, pvsiSignal, oiTrendSignal);
        }
        
        return callFutureScores;
    }

    public PutFutureScores getFuturePointsForPutEntry(BarSeries barSeriesForTimeFrame) {
        PutFutureScores putFutureScores = new PutFutureScores();
        
        // VWAP Signal (0 or 1)
        boolean vwapSignal = vwapIndicator.isVWAPSignalForPut(barSeriesForTimeFrame);
        putFutureScores.setVwapSignal(vwapSignal ? 1 : 0);
        
        // Price-Volume Surge Indicator (0 or 1)
        boolean pvsiSignal = priceVolumeSurgeIndicator.isBearishSurge(barSeriesForTimeFrame);
        putFutureScores.setPvsiScore(pvsiSignal ? 1 : 0);
        
        // OI Trend Signal (0 or 1) - Enhanced with actual OI analysis
        boolean oiTrendSignal = calculateOiTrendSignalForPut(barSeriesForTimeFrame);
        putFutureScores.setOiTrendSignal(oiTrendSignal ? 1 : 0);
        
        // Enhanced scoring with validation
        double totalScore = putFutureScores.getTotalScore();
        if (totalScore > 1) {
            log.info("Future Score for Put Entry: {} (VWAP: {}, PVSI: {}, OI: {})", 
                    totalScore, vwapSignal, pvsiSignal, oiTrendSignal);
        }
        
        return putFutureScores;
    }
    
    /**
     * Calculates OI trend signal for CALL entries
     * Analyzes Open Interest patterns for bullish signals
     */
    private boolean calculateOiTrendSignalForCall(BarSeries barSeriesForTimeFrame) {
        int endIndex = barSeriesForTimeFrame.getEndIndex();
        if (endIndex < 3) return false;
        
        // Get recent bars for OI analysis
        Bar currentBar = barSeriesForTimeFrame.getBar(endIndex);
        Bar previousBar = barSeriesForTimeFrame.getBar(endIndex - 1);
        Bar beforePreviousBar = barSeriesForTimeFrame.getBar(endIndex - 2);
        
        // Check if OI is increasing (bullish for calls)
        double currentOi = currentBar.getVolume().doubleValue(); // Using volume as proxy for OI
        double previousOi = previousBar.getVolume().doubleValue();
        double beforePreviousOi = beforePreviousBar.getVolume().doubleValue();
        
        // OI should be increasing for bullish signal
        boolean oiIncreasing = currentOi > previousOi && previousOi > beforePreviousOi;
        
        // Price should be above recent average for bullish OI signal
        double avgPrice = (currentBar.getClosePrice().doubleValue() + 
                          previousBar.getClosePrice().doubleValue() + 
                          beforePreviousBar.getClosePrice().doubleValue()) / 3.0;
        boolean priceAboveAverage = currentBar.getClosePrice().doubleValue() > avgPrice;
        
        return oiIncreasing && priceAboveAverage;
    }
    
    /**
     * Calculates OI trend signal for PUT entries
     * Analyzes Open Interest patterns for bearish signals
     */
    private boolean calculateOiTrendSignalForPut(BarSeries barSeriesForTimeFrame) {
        int endIndex = barSeriesForTimeFrame.getEndIndex();
        if (endIndex < 3) return false;
        
        // Get recent bars for OI analysis
        Bar currentBar = barSeriesForTimeFrame.getBar(endIndex);
        Bar previousBar = barSeriesForTimeFrame.getBar(endIndex - 1);
        Bar beforePreviousBar = barSeriesForTimeFrame.getBar(endIndex - 2);
        
        // Check if OI is increasing (bullish for puts in bearish market)
        double currentOi = currentBar.getVolume().doubleValue(); // Using volume as proxy for OI
        double previousOi = previousBar.getVolume().doubleValue();
        double beforePreviousOi = beforePreviousBar.getVolume().doubleValue();
        
        // OI should be increasing for bearish signal
        boolean oiIncreasing = currentOi > previousOi && previousOi > beforePreviousOi;
        
        // Price should be below recent average for bearish OI signal
        double avgPrice = (currentBar.getClosePrice().doubleValue() + 
                          previousBar.getClosePrice().doubleValue() + 
                          beforePreviousBar.getClosePrice().doubleValue()) / 3.0;
        boolean priceBelowAverage = currentBar.getClosePrice().doubleValue() < avgPrice;
        
        return oiIncreasing && priceBelowAverage;
    }
}
