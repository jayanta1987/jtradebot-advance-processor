package com.jtradebot.processor.mapper;

import com.jtradebot.processor.config.TradingConfigurationService;
import com.jtradebot.processor.repository.document.TradeConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TradePreferenceMapper {
    
    private static TradingConfigurationService tradingConfigService;
    
    @Autowired
    public void setTradingConfigurationService(TradingConfigurationService tradingConfigService) {
        TradePreferenceMapper.tradingConfigService = tradingConfigService;
    }
    
    public static TradeConfig.TradePreference getDefaultTradePreference() {
        TradeConfig.TradePreference tradePreference = new TradeConfig.TradePreference();
        
        // Use configuration service if available, otherwise use defaults
        if (tradingConfigService != null) {
            // Quantity settings from JSON
            tradePreference.setMinLotSize(tradingConfigService.getMinLotSize());
            tradePreference.setMaxQuantityPerTradeSafe(tradingConfigService.getMaxQuantityPerTrade());
            
            // Investment limits from JSON
            tradePreference.setMaxInvestmentPercentage((int) tradingConfigService.getMaxInvestmentPercentage());
            tradePreference.setMaxLossPercentagePerDay((int) tradingConfigService.getMaxRiskPerDayPercentage());
            tradePreference.setMaxProfitPercentagePerDay((int) tradingConfigService.getMaxProfitPerDayPercentage());
            
            // Trade settings from JSON
            tradePreference.setMaxTradeHoldingTimeInSec((int) tradingConfigService.getMaxTradeHoldingTimeInSec());
        } else {
            // Fallback defaults if configuration service is not available
            tradePreference.setMinLotSize(75);
            tradePreference.setMaxQuantityPerTradeSafe(150);
            tradePreference.setMaxInvestmentPercentage(50);
            tradePreference.setMaxLossPercentagePerDay(12);
            tradePreference.setMaxProfitPercentagePerDay(30);
            tradePreference.setMaxTradeHoldingTimeInSec(150);
        }
        
        return tradePreference;
    }
}
