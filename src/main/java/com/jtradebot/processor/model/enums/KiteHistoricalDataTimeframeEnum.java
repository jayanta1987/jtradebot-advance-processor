package com.jtradebot.processor.model.enums;

public enum KiteHistoricalDataTimeframeEnum {
    MINUTE("minute"),
    DAY("day"),
    THREE_MINUTE("3minute"),
    FIVE_MINUTE("5minute"),
    TEN_MINUTE("10minute"),
    FIFTEEN_MINUTE("15minute"),
    THIRTY_MINUTE("30minute"),
    SIXTY_MINUTE("60minute");

    private final String timeframe;

    KiteHistoricalDataTimeframeEnum(String timeframe) {
        this.timeframe = timeframe;
    }

    public String getTimeframe() {
        return this.timeframe;
    }
}