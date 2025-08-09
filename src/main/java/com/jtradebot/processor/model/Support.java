package com.jtradebot.processor.model;

import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import lombok.Data;

@Data
public class Support implements Comparable<Support>{

    private Integer supportValue;
    private CandleTimeFrameEnum candleTimeFrame;
    private boolean isEma;

    @Override
    public int compareTo(Support other) {
        return Double.compare(this.getSupportValue(), other.getSupportValue());
    }

}
