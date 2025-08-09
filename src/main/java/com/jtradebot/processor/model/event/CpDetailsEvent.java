package com.jtradebot.processor.model.event;

import com.jtradebot.processor.model.NoTradeScores;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.repository.document.Ntp;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Data
@Slf4j
@ToString
public class CpDetailsEvent {

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
    private Ntp ntp;
    private List<NoTradeScores> noTradeScores;
}
