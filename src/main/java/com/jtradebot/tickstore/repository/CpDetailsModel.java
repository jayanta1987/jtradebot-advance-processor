package com.jtradebot.tickstore.repository;

import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.model.enums.TrendEnum;
import com.jtradebot.processor.repository.document.Ntp;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@ToString
public class CpDetailsModel {

    private OrderTypeEnum orderType;

    private Double cp, shortAvgCp, midAvgCp;
    private Double cpAbs, shortAvgCpAbs, midAvgCpAbs;

    private Double oneMinCp, oneMinAvgCp;
    private Double oneMinCpAbs, oneMinAvgCpAbs;

    private Double longTrendCp, longTrendAvgCp;
    private Double longTrendCpAbs, longTrendAvgCpAbs;

    private double cpDelta;
    private double oneMinCpDelta;
    private double longCpDelta;
    private double totalCpDelta;

    private boolean allCpInSameDirection;

    private boolean futureSignal;
    private TrendEnum futureTrend;
    private boolean futureBullishSurge;
    private boolean futureBearishSurge;


    private Ntp ntp;

}