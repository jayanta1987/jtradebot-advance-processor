package com.jtradebot.processor.service;

import com.jtradebot.processor.model.enums.OrderTypeEnum;

public interface OptionPricingService {
    
    /**
     * Calculate realistic option entry price (1% of current index price)
     */
    Double calculateEntryPrice(Double currentIndexPrice);
    
    /**
     * Calculate stop loss price based on entry price and percentage
     */
    Double calculateStopLossPrice(Double entryPrice, Double stopLossPercentage, OrderTypeEnum orderType);
    
    /**
     * Calculate target price based on entry price and percentage
     */
    Double calculateTargetPrice(Double entryPrice, Double targetPercentage, OrderTypeEnum orderType);
    
    /**
     * Calculate current LTP based on index movement
     * LTP = Entry Price + (Current Index Price - Entry Index Price)
     */
    Double calculateCurrentLTP(Double entryPrice, Double entryIndexPrice, Double currentIndexPrice);
    
    /**
     * Calculate profit/loss points based on order type
     */
    Double calculateProfitLoss(Double entryPrice, Double exitPrice, OrderTypeEnum orderType);
}
