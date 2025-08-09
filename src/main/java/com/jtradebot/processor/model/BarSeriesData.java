package com.jtradebot.processor.model;

import lombok.Builder;
import lombok.Data;
import org.ta4j.core.BarSeries;

@Data
@Builder
public class BarSeriesData {
    private BarSeries series1Min;
    private BarSeries series3Min;
    private BarSeries series5Min;
    private BarSeries series15Min;
    private BarSeries series1Hour;
    private BarSeries series1Day;
}
