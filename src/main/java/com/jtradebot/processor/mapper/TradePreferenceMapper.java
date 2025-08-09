package com.jtradebot.processor.mapper;


import com.jtradebot.processor.repository.document.TradeConfig;

public class TradePreferenceMapper {
    public static TradeConfig.TradePreference getDefaultTradePreference() {
        TradeConfig.TradePreference tradePreference = new TradeConfig.TradePreference();
        tradePreference.setMaxTradePrice(24000);
        tradePreference.setMaxInvestmentPercentage(99);
        tradePreference.setMaxTradeHoldingTimeInSec(150);
        tradePreference.setMaxLossPercentagePerDay(12);
        tradePreference.setMaxProfitPercentagePerDay(30);
        tradePreference.setMinLotSize(75);
        tradePreference.setMaxQuantityPerTradeSafe(75);
        return tradePreference;
    }
}
