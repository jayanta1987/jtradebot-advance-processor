package com.jtradebot.processor.model.trading;

import com.jtradebot.processor.model.enums.EntryReason;
import com.jtradebot.processor.model.enums.ExitReasonEnum;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import lombok.Data;

@Data
public class Order {
    private OrderTypeEnum orderType;

    private String entryTime;
    private String exitTime;

    private Double entryPrice;
    private Double exitPrice;

    private double entryIndexPrice;
    private double exitIndexPrice;

    private Double ntp;
    private Double riskFactor;

    // CP-related fields removed - no longer used in new strategy
    // The new strategy will track different metrics

    private EntryReason entryReason;
    private ExitReasonEnum exitReason;

    private double totalPoints;
    private double totalProfit;
    private double totalReleasedProfit;
}
