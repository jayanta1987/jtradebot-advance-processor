package com.jtradebot.processor.model.event;

import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.model.enums.TradeMode;
import com.jtradebot.processor.repository.document.Ntp;
import lombok.Data;

import java.util.Date;

@Data
public class OrderEntryEvent {
    private Date eventTimeStamp;
    private String RefOrderId;
    private OrderTypeEnum orderType;
    private Date tickTimeStamp;
    // TODO: EntryRule removed from old CP-based system
    private TradeMode tradeMode;
    private int indexStopLoss;
    private int indexTarget;
    private int indexTarget2;
    private int indexTarget3;
    private Ntp ntp;
    private double entryIndexPrice;
    private int maxAllowedQuantity;
    private double oneMinATR;
    private boolean maxProfitReached;
}