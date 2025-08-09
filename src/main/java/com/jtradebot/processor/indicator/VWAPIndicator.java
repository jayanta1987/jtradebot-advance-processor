package com.jtradebot.processor.indicator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

@Component
@Slf4j
public class VWAPIndicator {

    public double calculateVWAP(BarSeries series) {
        int endIndex = series.getEndIndex();
        if (endIndex < 0) return 0.0;

        double cumulativeTPV = 0.0; // TPV = Typical Price * Volume
        double cumulativeVolume = 0.0;

        for (int i = 0; i <= endIndex; i++) {
            Bar bar = series.getBar(i);
            double typicalPrice = (bar.getHighPrice().doubleValue() +
                    bar.getLowPrice().doubleValue() +
                    bar.getClosePrice().doubleValue()) / 3;
            double volume = bar.getVolume().doubleValue();

            cumulativeTPV += typicalPrice * volume;
            cumulativeVolume += volume;
        }

        return cumulativeVolume == 0 ? 0 : (cumulativeTPV / cumulativeVolume);
    }

    public boolean isVWAPSignalForCall(BarSeries barSeries) {
        int endIndex = barSeries.getEndIndex();
        if (endIndex < 2) return false;

        Bar currentBar = barSeries.getBar(endIndex);
        Bar previousBar = barSeries.getBar(endIndex - 1);
        Bar beforePreviousBar = barSeries.getBar(endIndex - 2);

        double currentVWAP = calculateVWAP(barSeries);

        // Calculate VWAP of previous candle by excluding current candle
        BarSeries subSeries = barSeries.getSubSeries(0, endIndex); // excludes current
        double previousVWAP = calculateVWAP(subSeries);

        // Check if price has crossed above VWAP
        boolean crossedAboveVWAP =
                previousBar.getClosePrice().doubleValue() < previousVWAP &&
                        currentBar.getClosePrice().doubleValue() > currentVWAP;

        boolean volumeIncreasing = currentBar.getVolume().doubleValue() >= 1.2 * previousBar.getVolume().doubleValue()
                && previousBar.getVolume().doubleValue() >= 1.2 * beforePreviousBar.getVolume().doubleValue();

        boolean isBullish = currentBar.getClosePrice().isGreaterThan(currentBar.getOpenPrice());

        return crossedAboveVWAP && volumeIncreasing && isBullish;
    }

    public boolean isVWAPSignalForPut(BarSeries barSeries) {
        int endIndex = barSeries.getEndIndex();
        if (endIndex < 2) return false;

        Bar currentBar = barSeries.getBar(endIndex);
        Bar previousBar = barSeries.getBar(endIndex - 1);
        Bar beforePreviousBar = barSeries.getBar(endIndex - 2);

        double currentVWAP = calculateVWAP(barSeries);

        BarSeries subSeries = barSeries.getSubSeries(0, endIndex);
        double previousVWAP = calculateVWAP(subSeries);

        boolean crossedBelowVWAP =
                previousBar.getClosePrice().doubleValue() > previousVWAP &&
                        currentBar.getClosePrice().doubleValue() < currentVWAP;

        boolean volumeIncreasing = currentBar.getVolume().doubleValue() >= 1.2 * previousBar.getVolume().doubleValue()
                && previousBar.getVolume().doubleValue() >= 1.2 * beforePreviousBar.getVolume().doubleValue();

        boolean isBearish = currentBar.getClosePrice().isLessThan(currentBar.getOpenPrice());

        return crossedBelowVWAP && volumeIncreasing && isBearish;
    }


}
