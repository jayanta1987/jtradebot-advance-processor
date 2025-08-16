package com.jtradebot.processor.service;

import com.jtradebot.processor.model.indicator.DynamicFlattenedIndicators;
import com.jtradebot.processor.model.indicator.DynamicIndicatorConfig;
import com.jtradebot.processor.model.strategy.ScalpingEntryLogic;
import com.jtradebot.processor.model.strategy.ScalpingEntryDecision;
import com.jtradebot.processor.model.trading.StopLossTargetLevels;
import com.jtradebot.processor.model.indicator.MarketConditionValidation;

public interface ScalpingEntryService {
    
    /**
     * Evaluate scalping entry for CALL strategy
     * @param flattenedIndicators The flattened indicators
     * @param config The indicator configuration
     * @param currentPrice Current market price
     * @param currentTime Current market time
     * @return Scalping entry decision
     */
    ScalpingEntryDecision evaluateCallEntry(DynamicFlattenedIndicators flattenedIndicators, 
                                          DynamicIndicatorConfig config, 
                                          double currentPrice, 
                                          java.time.LocalDateTime currentTime);
    
    /**
     * Evaluate scalping entry for PUT strategy
     * @param flattenedIndicators The flattened indicators
     * @param config The indicator configuration
     * @param currentPrice Current market price
     * @param currentTime Current market time
     * @return Scalping entry decision
     */
    ScalpingEntryDecision evaluatePutEntry(DynamicFlattenedIndicators flattenedIndicators, 
                                         DynamicIndicatorConfig config, 
                                         double currentPrice, 
                                         java.time.LocalDateTime currentTime);
    
    /**
     * Calculate optimal position size based on risk management
     * @param accountBalance Account balance
     * @param riskPerTrade Risk per trade percentage
     * @param stopLossPoints Stop loss in points
     * @param currentPrice Current market price
     * @return Position size in quantity
     */
    int calculatePositionSize(double accountBalance, double riskPerTrade, 
                            double stopLossPoints, double currentPrice);
    
    /**
     * Calculate stop loss and target levels
     * @param entryPrice Entry price
     * @param strategyType CALL or PUT
     * @param stopLossPoints Stop loss in points
     * @param targetPoints Target in points
     * @return Stop loss and target levels
     */
    StopLossTargetLevels calculateStopLossTarget(double entryPrice, String strategyType, 
                                               double stopLossPoints, double targetPoints);
    
    /**
     * Validate market conditions for entry
     * @param currentTime Current market time
     * @param currentPrice Current market price
     * @param bidPrice Bid price
     * @param askPrice Ask price
     * @param volume Current volume
     * @return Market condition validation result
     */
    MarketConditionValidation validateMarketConditions(java.time.LocalDateTime currentTime, 
                                                      double currentPrice, 
                                                      double bidPrice, 
                                                      double askPrice, 
                                                      long volume);
    
    /**
     * Load scalping entry logic configuration
     * @param configPath Path to configuration file
     * @return Scalping entry logic configuration
     */
    ScalpingEntryLogic loadEntryLogic(String configPath);
}
