package com.jtradebot.processor.model.indicator;

import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import lombok.Data;
import org.ta4j.core.indicators.EMAIndicator;

@Data
public class EmaIndicatorInfo {
    private CandleTimeFrameEnum timeFrame;
    private EMAIndicator ema5;
    private EMAIndicator ema9;
    private EMAIndicator ema14;
    private EMAIndicator ema20;
    private EMAIndicator ema34;
    private EMAIndicator ema200;

}
