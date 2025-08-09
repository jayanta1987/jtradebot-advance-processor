package com.jtradebot.processor.repository.document;

import com.zerodhatech.models.Depth;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

@Data
@Document(collection = "ticks")
@CompoundIndex(name = "instrument_lastTradedTime_idx", def = "{'instrumentToken': 1, 'lastTradedTime': 1}")
public class TickDocument {
    @Id
    private String id;
    private Long instrumentToken;
    private Double lastTradedPrice;

    @Indexed(direction = IndexDirection.ASCENDING)
    private Date lastTradedTime;

    private Double lastTradedQuantity;
    private Double averageTradePrice;
    private Long volumeTradedToday;
    private Double totalBuyQuantity;
    private Double totalSellQuantity;
    private Double oi;
    private Double oiDayHigh;
    private Double oiDayLow;
    @Indexed(direction = IndexDirection.ASCENDING)
    private Date tickTimestamp;
    private Map<String, ArrayList<Depth>> depth;
}
