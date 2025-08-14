package com.jtradebot.processor.service.impl;

import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.service.OptionPricingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OptionPricingServiceImpl implements OptionPricingService {
    
    @Override
    public Double calculateEntryPrice(Double currentIndexPrice) {
        if (currentIndexPrice == null || currentIndexPrice <= 0) {
            log.warn("Invalid index price for entry calculation: {}", currentIndexPrice);
            return null;
        }
        
        // Entry price = 1% of current index price
        Double entryPrice = currentIndexPrice * 0.01;
        
        log.debug("Calculated entry price: {} (1% of index price: {})", entryPrice, currentIndexPrice);
        return entryPrice;
    }
    
    @Override
    public Double calculateStopLossPrice(Double entryPrice, Double stopLossPercentage, OrderTypeEnum orderType) {
        if (entryPrice == null || stopLossPercentage == null || stopLossPercentage <= 0) {
            log.warn("Invalid parameters for stop loss calculation: entryPrice={}, stopLossPercentage={}", 
                    entryPrice, stopLossPercentage);
            return null;
        }
        
        Double stopLossPrice;
        
        if (orderType == OrderTypeEnum.CALL_BUY) {
            // For CALL: Stop loss is below entry price
            stopLossPrice = entryPrice * (1 - stopLossPercentage / 100.0);
        } else if (orderType == OrderTypeEnum.PUT_BUY) {
            // For PUT: Stop loss is above entry price
            stopLossPrice = entryPrice * (1 + stopLossPercentage / 100.0);
        } else {
            log.warn("Unsupported order type for stop loss calculation: {}", orderType);
            return null;
        }
        
        // Ensure stop loss doesn't go below 0
        stopLossPrice = Math.max(stopLossPrice, 0.0);
        
        log.debug("Calculated stop loss: {} (Entry: {}, Type: {}, Percentage: {})", 
                stopLossPrice, entryPrice, orderType, stopLossPercentage);
        return stopLossPrice;
    }
    
    @Override
    public Double calculateTargetPrice(Double entryPrice, Double targetPercentage, OrderTypeEnum orderType) {
        if (entryPrice == null || targetPercentage == null || targetPercentage <= 0) {
            log.warn("Invalid parameters for target calculation: entryPrice={}, targetPercentage={}", 
                    entryPrice, targetPercentage);
            return null;
        }
        
        Double targetPrice;
        
        if (orderType == OrderTypeEnum.CALL_BUY) {
            // For CALL: Target is above entry price
            targetPrice = entryPrice * (1 + targetPercentage / 100.0);
        } else if (orderType == OrderTypeEnum.PUT_BUY) {
            // For PUT: Target is below entry price
            targetPrice = entryPrice * (1 - targetPercentage / 100.0);
        } else {
            log.warn("Unsupported order type for target calculation: {}", orderType);
            return null;
        }
        
        // Ensure target doesn't go below 0
        targetPrice = Math.max(targetPrice, 0.0);
        
        log.debug("Calculated target: {} (Entry: {}, Type: {}, Percentage: {})", 
                targetPrice, entryPrice, orderType, targetPercentage);
        return targetPrice;
    }
    
    @Override
    public Double calculateCurrentLTP(Double entryPrice, Double entryIndexPrice, Double currentIndexPrice, OrderTypeEnum orderType) {
        if (entryPrice == null || entryIndexPrice == null || currentIndexPrice == null) {
            log.warn("Invalid parameters for LTP calculation: entryPrice={}, entryIndexPrice={}, currentIndexPrice={}", 
                    entryPrice, entryIndexPrice, currentIndexPrice);
            return entryPrice; // Return entry price if any value is null
        }
        
        // Calculate index movement
        Double indexMovement = currentIndexPrice - entryIndexPrice;
        
        // For options, price movement is 1:1 with index movement
        Double currentLTP;
        
        if (orderType == OrderTypeEnum.CALL_BUY) {
            // CALL: option price increases when index increases
            currentLTP = entryPrice + indexMovement;
        } else if (orderType == OrderTypeEnum.PUT_BUY) {
            // PUT: option price decreases when index increases
            currentLTP = entryPrice - indexMovement;
        } else {
            log.warn("Unsupported order type for LTP calculation: {}", orderType);
            return entryPrice;
        }
        
        // Ensure LTP doesn't go below 0 (minimum option price)
        currentLTP = Math.max(currentLTP, 0.0);
        
        log.debug("Calculated LTP: {} (Entry: {}, Index Movement: {}, Order Type: {}, Entry Index: {}, Current Index: {})", 
                currentLTP, entryPrice, indexMovement, orderType, entryIndexPrice, currentIndexPrice);
        return currentLTP;
    }
    
    @Override
    public Double calculateProfitLoss(Double entryPrice, Double exitPrice, OrderTypeEnum orderType) {
        if (entryPrice == null || exitPrice == null) {
            log.warn("Invalid parameters for profit/loss calculation: entryPrice={}, exitPrice={}", 
                    entryPrice, exitPrice);
            return 0.0;
        }
        
        Double profitLoss;
        
        if (orderType == OrderTypeEnum.CALL_BUY) {
            // For CALL: Profit when exit price > entry price
            profitLoss = exitPrice - entryPrice;
        } else if (orderType == OrderTypeEnum.PUT_BUY) {
            // For PUT: Profit when exit price < entry price
            profitLoss = entryPrice - exitPrice;
        } else {
            log.warn("Unsupported order type for profit/loss calculation: {}", orderType);
            return 0.0;
        }
        
        log.debug("Calculated profit/loss: {} (Entry: {}, Exit: {}, Type: {})", 
                profitLoss, entryPrice, exitPrice, orderType);
        return profitLoss;
    }
}
