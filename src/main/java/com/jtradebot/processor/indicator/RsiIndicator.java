package com.jtradebot.processor.indicator;

import com.jtradebot.processor.model.enums.DivergenceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class RsiIndicator {

    public double getRsiValue(BarSeries series, int barCount) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        // RSI 14 Indicator
        RSIIndicator rsi14 = new RSIIndicator(closePrice, barCount);
        int lastIndex = series.getEndIndex();
        return rsi14.getValue(lastIndex).doubleValue();
    }

    // Adjust the lookback period
    private List<Integer> findSwingHighs(Indicator<Num> indicator, int startIndex, int endIndex, int lookback) {
        List<Integer> swingHighs = new ArrayList<>();
        for (int i = startIndex + lookback; i <= endIndex - lookback; i++) {
            boolean isSwingHigh = true;
            Num currentValue = indicator.getValue(i);
            for (int j = i - lookback; j <= i + lookback; j++) {
                if (j == i) continue;
                if (indicator.getValue(j).isGreaterThanOrEqual(currentValue)) {
                    isSwingHigh = false;
                    break;
                }
            }
            if (isSwingHigh) {
                swingHighs.add(i);
            }
        }
        return swingHighs;
    }

    private List<Integer> findSwingLows(Indicator<Num> indicator, int startIndex, int endIndex, int lookback) {
        List<Integer> swingLows = new ArrayList<>();
        for (int i = startIndex + lookback; i <= endIndex - lookback; i++) {
            boolean isSwingLow = true;
            Num currentValue = indicator.getValue(i);
            for (int j = i - lookback; j <= i + lookback; j++) {
                if (j == i) continue;
                if (indicator.getValue(j).isLessThanOrEqual(currentValue)) {
                    isSwingLow = false;
                    break;
                }
            }
            if (isSwingLow) {
                swingLows.add(i);
            }
        }
        return swingLows;
    }

    // Improve the findNearestIndex method
    private int findNearestIndex(List<Integer> indices, int targetIndex, Indicator<Num> indicator, Num targetValue) {
        int nearestIndex = -1;
        Num minDifference = null;
        for (int index : indices) {
            Num difference = indicator.getValue(index).minus(targetValue).abs();
            if (minDifference == null || difference.isLessThan(minDifference)) {
                minDifference = difference;
                nearestIndex = index;
            }
        }
        return nearestIndex;
    }

    // Modify the isRsiDivergence method
    public boolean isRsiDivergence(BarSeries series, int barCount, int lookBackPeriod, DivergenceType divergenceType) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePrice, barCount);
        int endIndex = series.getEndIndex();
        int startIndex = Math.max(series.getBeginIndex(), endIndex - lookBackPeriod);
        List<Integer> priceHighs = findSwingHighs(closePrice, startIndex, endIndex, lookBackPeriod);
        List<Integer> priceLows = findSwingLows(closePrice, startIndex, endIndex, lookBackPeriod);
        List<Integer> rsiHighs = findSwingHighs(rsi, startIndex, endIndex, lookBackPeriod);
        List<Integer> rsiLows = findSwingLows(rsi, startIndex, endIndex, lookBackPeriod);
        if (divergenceType == DivergenceType.BEARISH || divergenceType == DivergenceType.ANY) {
            for (int i = 1; i < priceHighs.size(); i++) {
                int prevPriceHighIndex = priceHighs.get(i - 1);
                int currentPriceHighIndex = priceHighs.get(i);
                Num prevPriceHigh = closePrice.getValue(prevPriceHighIndex);
                Num currentPriceHigh = closePrice.getValue(currentPriceHighIndex);
                if (currentPriceHigh.isGreaterThan(prevPriceHigh)) {
                    int prevRsiHighIndex = findNearestIndex(rsiHighs, prevPriceHighIndex, rsi, prevPriceHigh);
                    int currentRsiHighIndex = findNearestIndex(rsiHighs, currentPriceHighIndex, rsi, currentPriceHigh);
                    if (prevRsiHighIndex != -1 && currentRsiHighIndex != -1) {
                        Num prevRsiHigh = rsi.getValue(prevRsiHighIndex);
                        Num currentRsiHigh = rsi.getValue(currentRsiHighIndex);
                        if (currentRsiHigh.isLessThan(prevRsiHigh)) {
                            log.warn(".....................Bearish RSI divergence detected between indices {} and {}", prevPriceHighIndex, currentPriceHighIndex);
                            return true;
                        }
                    }
                }
            }
        }
        if (divergenceType == DivergenceType.BULLISH || divergenceType == DivergenceType.ANY) {
            for (int i = 1; i < priceLows.size(); i++) {
                int prevPriceLowIndex = priceLows.get(i - 1);
                int currentPriceLowIndex = priceLows.get(i);
                Num prevPriceLow = closePrice.getValue(prevPriceLowIndex);
                Num currentPriceLow = closePrice.getValue(currentPriceLowIndex);
                if (currentPriceLow.isLessThan(prevPriceLow)) {
                    int prevRsiLowIndex = findNearestIndex(rsiLows, prevPriceLowIndex, rsi, prevPriceLow);
                    int currentRsiLowIndex = findNearestIndex(rsiLows, currentPriceLowIndex, rsi, currentPriceLow);
                    if (prevRsiLowIndex != -1 && currentRsiLowIndex != -1) {
                        Num prevRsiLow = rsi.getValue(prevRsiLowIndex);
                        Num currentRsiLow = rsi.getValue(currentRsiLowIndex);
                        if (currentRsiLow.isGreaterThan(prevRsiLow)) {
                            log.warn(".....................Bullish RSI divergence detected between indices {} and {}", prevPriceLowIndex, currentPriceLowIndex);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }


}
