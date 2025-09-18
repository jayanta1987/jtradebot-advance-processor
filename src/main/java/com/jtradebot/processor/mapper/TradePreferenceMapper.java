package com.jtradebot.processor.mapper;

import com.jtradebot.processor.repository.document.TradeConfig;
import org.springframework.stereotype.Component;

@Component
public class TradePreferenceMapper {
    
    public static TradeConfig.TradePreference getDefaultTradePreference() {
        TradeConfig.TradePreference tradePreference = new TradeConfig.TradePreference();
        
        // Set default values for the new database-driven configuration
        tradePreference.setMaxInvestmentPercentage(99.0); // 99% of available balance
        tradePreference.setMinQuantity(75); // Minimum 1 lot
        tradePreference.setMaxQuantity(600); // Maximum 40 lots (safety limit)
        tradePreference.setMaxLossPercentagePerDay(5.0);
        tradePreference.setMaxProfitPercentagePerDay(10.0);
        tradePreference.setMaxTradeHoldingTimeInSec(150);
        
        return tradePreference;
    }
}
