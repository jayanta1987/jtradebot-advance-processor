package com.jtradebot.processor.indicator;

import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.util.Map;

import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.*;

@Component
@Slf4j
public class PriceVolumeSurgeIndicator {

    Map<CandleTimeFrameEnum, Double> timeframeToMinMove = Map.of(
            ONE_MIN, 15.0,
            THREE_MIN, 20.0,
            FIVE_MIN, 30.0,
            FIFTEEN_MIN, 50.0
    );


    /**
     * Detects a bullish price-volume surge.
     * @param series BarSeries input
     * @return true if price increased >1% in last 3 bars and current volume is >50% higher than 3-bar average
     */
    public boolean isBullishSurge(BarSeries series) {
        int endIndex = series.getEndIndex();
        if (endIndex < 3) return false;

        Bar current = series.getBar(endIndex);
        Bar past = series.getBar(endIndex - 3);

        double priceChange = (current.getClosePrice().doubleValue() - past.getClosePrice().doubleValue());
        double minPriceMove = timeframeToMinMove.getOrDefault(CandleTimeFrameEnum.valueOf(series.getName()), 10.0);


        double avgVolume = (
                series.getBar(endIndex - 3).getVolume().doubleValue() +
                        series.getBar(endIndex - 2).getVolume().doubleValue() +
                        series.getBar(endIndex - 1).getVolume().doubleValue()
        ) / 3.0;

        double volumeChange = (current.getVolume().doubleValue() - avgVolume) / avgVolume;

        return priceChange > minPriceMove && volumeChange > 0.5;
    }

    /**
     * Detects a bearish price-volume surge.
     * @param series BarSeries input
     * @return true if price dropped >1% in last 3 bars and current volume is >50% higher than 3-bar average
     */
    public boolean isBearishSurge(BarSeries series) {
        int endIndex = series.getEndIndex();
        if (endIndex < 3) return false;

        Bar current = series.getBar(endIndex);
        Bar past = series.getBar(endIndex - 3);

        double priceChange = (current.getClosePrice().doubleValue() - past.getClosePrice().doubleValue());
        double minPriceMove = timeframeToMinMove.getOrDefault(CandleTimeFrameEnum.valueOf(series.getName()), 10.0);

        double avgVolume = (
                series.getBar(endIndex - 3).getVolume().doubleValue() +
                        series.getBar(endIndex - 2).getVolume().doubleValue() +
                        series.getBar(endIndex - 1).getVolume().doubleValue()
        ) / 3.0;

        double volumeChange = (current.getVolume().doubleValue() - avgVolume) / avgVolume;

        return priceChange < -minPriceMove && volumeChange > 0.5;
    }
}
