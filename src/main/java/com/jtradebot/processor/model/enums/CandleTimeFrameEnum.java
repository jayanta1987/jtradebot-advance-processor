package com.jtradebot.processor.model.enums;

import lombok.Getter;

@Getter
public enum CandleTimeFrameEnum {
    ONE_MIN(1),
    THREE_MIN(0.75),
    FIVE_MIN(1.5),
    FIFTEEN_MIN(0.75),
    ONE_HOUR(0.50),
    ONE_DAY(0.25);

    private final double tpFactor;

    CandleTimeFrameEnum(double tpFactor) {
        this.tpFactor = tpFactor;
    }

}
