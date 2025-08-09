package com.jtradebot.processor.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntraDayPreference {
    private double maxDayLoss;
    private double maxDayProfit;
    private int maxTransactionCount;
    private int maxDayLossTradeCount;
    private int maxTradeQuantity;
    private long maxTradeHoldingTime;
    private double maxTradePrice;
    private int maxTradeStopLossPercentage;
    private int maxTradeTargetPercentage;
    private int minTradeTargetPercentage;

    private LocalTime tradeStartTime;
    private LocalTime tradeEndTime;

}
