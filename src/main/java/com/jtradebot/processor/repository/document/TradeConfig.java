package com.jtradebot.processor.repository.document;


import lombok.Data;
import lombok.ToString;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@Document(collection = "advance_trade_config")
@ToString
public class TradeConfig {
    @MongoId
    private String id;
    @Indexed(unique = true)
    private String date;
    private String accessToken;
    private String createdAt;
    private String updatedAt;
    private TradePreference tradePreference;

    @Data
    @ToString
    public static class TradePreference {

        private double maxInvestment;
        private int minQuantity;
        private int maxQuantity;
        private double maxLossPercentagePerDay;
        private double maxProfitPercentagePerDay;
        private long maxTradeHoldingTimeInSec;
    }
}
