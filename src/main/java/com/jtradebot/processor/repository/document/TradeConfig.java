package com.jtradebot.processor.repository.document;

import com.jtradebot.processor.model.ExitSettings;
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
    private ExitSettings exitSettings;

    @Data
    @ToString
    public static class TradePreference {

        private double maxInvestment;
        private int minQuantity;
        private int maxQuantity;
        private double maxLossPerDay;  // Fixed value instead of percentage
        private double maxProfitPerDay;  // Fixed value instead of percentage
        private double maxPointsPerDay;  // Maximum total points allowed per day (sum of closed + active order points)
        private long maxTradeHoldingTimeInSec;
        private boolean enableTradeAfterStopLossHit;  // Flag to enable/disable trading after stop loss hit
        private String stopLossBlockTimeframe;  // Candle timeframe to block entries after stop loss hit (e.g., "ONE_MIN", "THREE_MIN", "FIVE_MIN") - used if enableTradeAfterStopLossHit is false
    }
}
