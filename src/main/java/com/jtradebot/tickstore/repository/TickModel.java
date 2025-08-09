package com.jtradebot.tickstore.repository;
import com.zerodhatech.models.Depth;
import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

@Data
public class TickModel {

    private String mode;
    private boolean tradable;
    private long instrumentToken;
    private double lastTradedPrice;
    private double highPrice;
    private double lowPrice;
    private double openPrice;
    private double closePrice;
    private double change;
    private double lastTradedQuantity;
    private double averageTradePrice;
    private long volumeTradedToday;
    private double totalBuyQuantity;
    private double totalSellQuantity;
    private Date lastTradedTime;
    private double oi;
    private double oiDayHigh;
    private double oiDayLow;
    private Date tickTimestamp;
    private Map<String, ArrayList<Depth>> depth;
}
