package com.jtradebot.processor.model.indicator;

import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

@Data
public class Resistance implements Comparable<Resistance>{

    private Integer resistanceValue;
    private CandleTimeFrameEnum candleTimeFrame;
    private boolean isEma;

    @Override
    public int compareTo(@NotNull Resistance other) {
        return Double.compare(this.getResistanceValue(), other.getResistanceValue());
    }
}
