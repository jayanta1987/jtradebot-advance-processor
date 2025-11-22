package com.jtradebot.processor.indicator;

import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.jtradebot.processor.model.indicator.EmaIndicatorInfo;
import com.jtradebot.processor.model.indicator.EmaInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;


@Component
@Slf4j
public class MultiEmaIndicator {

    // Standard EMA periods used across the application
    private static final int EMA_5 = 5;
    private static final int EMA_9 = 9;
    private static final int EMA_14 = 14;
    private static final int EMA_20 = 20;
    private static final int EMA_34 = 34;
    private static final int EMA_200 = 200;

    /**
     * Creates EMA indicator for a given period
     */
    private EMAIndicator createEmaIndicator(ClosePriceIndicator closePrice, int period) {
        return new EMAIndicator(closePrice, period);
    }

    /**
     * Calculates EMA values for all standard periods at the end index
     */
    public EmaInfo calculateEmaValues(BarSeries series, CandleTimeFrameEnum timeframe) {
        EmaIndicatorInfo emaIndicatorInfo = createEmaInfoForTimeframes(series, timeframe);
        int endIndex = series.getEndIndex();
        
        EmaInfo emaInfo = new EmaInfo();
        emaInfo.setTimeFrame(timeframe);
        emaInfo.setEma5(getEmaValueAt(emaIndicatorInfo.getEma5(), endIndex));
        emaInfo.setEma9(getEmaValueAt(emaIndicatorInfo.getEma9(), endIndex));
        emaInfo.setEma14(getEmaValueAt(emaIndicatorInfo.getEma14(), endIndex));
        emaInfo.setEma20(getEmaValueAt(emaIndicatorInfo.getEma20(), endIndex));
        emaInfo.setEma34(getEmaValueAt(emaIndicatorInfo.getEma34(), endIndex));
        emaInfo.setEma200(getEmaValueAt(emaIndicatorInfo.getEma200(), endIndex));
        return emaInfo;
    }

    /**
     * Gets EMA value for a specific period at a specific index
     */
    public double getEmaValue(BarSeries series, int barCount, int endIndex) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        EMAIndicator emaIndicator = createEmaIndicator(closePrice, barCount);
        return getEmaValueAt(emaIndicator, endIndex);
    }

    /**
     * Creates EMA indicator info for all standard periods
     */
    public EmaIndicatorInfo createEmaInfoForTimeframes(BarSeries series, CandleTimeFrameEnum timeframe) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        EmaIndicatorInfo emaIndicatorInfo = new EmaIndicatorInfo();
        emaIndicatorInfo.setTimeFrame(timeframe);
        emaIndicatorInfo.setEma5(createEmaIndicator(closePrice, EMA_5));
        emaIndicatorInfo.setEma9(createEmaIndicator(closePrice, EMA_9));
        emaIndicatorInfo.setEma14(createEmaIndicator(closePrice, EMA_14));
        emaIndicatorInfo.setEma20(createEmaIndicator(closePrice, EMA_20));
        emaIndicatorInfo.setEma34(createEmaIndicator(closePrice, EMA_34));
        emaIndicatorInfo.setEma200(createEmaIndicator(closePrice, EMA_200));
        return emaIndicatorInfo;
    }

    /**
     * Gets EMA value at a specific index
     */
    private double getEmaValueAt(EMAIndicator emaIndicator, int index) {
        return emaIndicator.getValue(index).doubleValue();
    }


}
