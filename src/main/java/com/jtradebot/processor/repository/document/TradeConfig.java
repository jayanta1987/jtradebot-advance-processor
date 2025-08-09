package com.jtradebot.processor.repository.document;


import lombok.Data;
import lombok.ToString;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@Document(collection = "tradeConfig")
@ToString
public class TradeConfig {
    @MongoId
    private String id;
    @Indexed(unique = true)
    private String date;
    private String accessToken;
    private TradePreference tradePreference;

    @Data
    @ToString
    public static class TradePreference {

        private double maxTradePrice;
        private int maxInvestmentPercentage;
        private int minLotSize;
        private double maxLossPercentagePerDay;
        private double maxProfitPercentagePerDay;
        private int maxQuantityPerTradeSafe;
        private long maxTradeHoldingTimeInSec;
    }
}
