package com.jtradebot.processor.model;

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

    private Double cp;
    private Double shortAvgCp;

    private Double longTrendCp;
    private Double longTrendAvgCp;

    private Double oneMinCp;
    private Double oneMinAvgCp;

    private EntryReason entryReason;
    private ExitReasonEnum exitReason;

    private double totalPoints;
    private double totalProfit;
    private double totalReleasedProfit;
}
