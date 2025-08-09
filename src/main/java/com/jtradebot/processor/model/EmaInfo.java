package com.jtradebot.processor.model;

import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import lombok.Data;

@Data
public class EmaInfo {
    private CandleTimeFrameEnum timeFrame;
    private Double ema5;
    private Double ema9;
    private Double ema14;
    private Double ema20;
    private Double ema34;
    private Double ema200;

}
