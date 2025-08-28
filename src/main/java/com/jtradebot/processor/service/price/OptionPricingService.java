package com.jtradebot.processor.service.price;

import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.service.risk.DynamicRiskManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OptionPricingService {
    
    private final DynamicRiskManagementService dynamicRiskManagementService;
    
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
    
    public Double calculateStopLossPrice(Double entryPrice, Double stopLossPercentage, OrderTypeEnum orderType) {
        if (entryPrice == null || stopLossPercentage == null || stopLossPercentage <= 0) {
            log.warn("Invalid parameters for stop loss calculation: entryPrice={}, stopLossPercentage={}", 
                    entryPrice, stopLossPercentage);
            return null;
        }
        
        // For both CALL and PUT orders, stop loss is below entry price
        // When option price goes down, it's a loss for both CALL and PUT
        Double stopLossPrice = entryPrice * (1 - stopLossPercentage / 100.0);
        
        // Ensure stop loss doesn't go below 0
        stopLossPrice = Math.max(stopLossPrice, 0.0);
        
        log.debug("Calculated stop loss: {} (Entry: {}, Type: {}, Percentage: {})", 
                stopLossPrice, entryPrice, orderType, stopLossPercentage);
        return stopLossPrice;
    }
    
    public Double calculateTargetPrice(Double entryPrice, Double targetPercentage, OrderTypeEnum orderType) {
        if (entryPrice == null || targetPercentage == null || targetPercentage <= 0) {
            log.warn("Invalid parameters for target calculation: entryPrice={}, targetPercentage={}", 
                    entryPrice, targetPercentage);
            return null;
        }
        
        // For both CALL and PUT orders, target is above entry price
        // When option price goes up, it's profitable for both CALL and PUT
        Double targetPrice = entryPrice * (1 + targetPercentage / 100.0);
        
        // Ensure target doesn't go below 0
        targetPrice = Math.max(targetPrice, 0.0);
        
        log.debug("Calculated target: {} (Entry: {}, Type: {}, Percentage: {})", 
                targetPrice, entryPrice, orderType, targetPercentage);
        return targetPrice;
    }

    /**
     * Calculate dynamic stop loss price based on 5-minute candle range
     */
    public Double calculateDynamicStopLossPrice(Double entryPrice, String instrumentToken, Double currentIndexPrice) {
        if (entryPrice == null || instrumentToken == null || currentIndexPrice == null) {
            log.warn("Invalid parameters for dynamic stop loss calculation: entryPrice={}, instrumentToken={}, currentIndexPrice={}", 
                    entryPrice, instrumentToken, currentIndexPrice);
            return null;
        }
        
        // Calculate dynamic stop loss points based on 5-minute candle range
        double dynamicStopLossPoints = dynamicRiskManagementService.calculateDynamicStopLoss(instrumentToken, currentIndexPrice);
        
        // Calculate stop loss price
        Double stopLossPrice = entryPrice - dynamicStopLossPoints;
        
        // Ensure stop loss doesn't go below 0
        stopLossPrice = Math.max(stopLossPrice, 0.0);
        
        log.info("🎯 DYNAMIC STOP LOSS PRICE - Entry: {:.2f} | Stop Loss Points: {:.2f} | Stop Loss Price: {:.2f} | Instrument: {}", 
                entryPrice, dynamicStopLossPoints, stopLossPrice, instrumentToken);
        
        return stopLossPrice;
    }

    /**
     * Calculate dynamic target price as double of stop loss
     */
    public Double calculateDynamicTargetPrice(Double entryPrice, String instrumentToken, Double currentIndexPrice) {
        if (entryPrice == null || instrumentToken == null || currentIndexPrice == null) {
            log.warn("Invalid parameters for dynamic target calculation: entryPrice={}, instrumentToken={}, currentIndexPrice={}", 
                    entryPrice, instrumentToken, currentIndexPrice);
            return null;
        }
        
        // Calculate dynamic stop loss points first
        double dynamicStopLossPoints = dynamicRiskManagementService.calculateDynamicStopLoss(instrumentToken, currentIndexPrice);
        
        // Calculate dynamic target points as double of stop loss
        double dynamicTargetPoints = dynamicRiskManagementService.calculateDynamicTarget(dynamicStopLossPoints);
        
        // Calculate target price
        Double targetPrice = entryPrice + dynamicTargetPoints;
        
        log.info("🎯 DYNAMIC TARGET PRICE - Entry: {:.2f} | Target Points: {:.2f} | Target Price: {:.2f} | Instrument: {}", 
                entryPrice, dynamicTargetPoints, targetPrice, instrumentToken);
        
        return targetPrice;
    }

    /**
     * Get dynamic risk management info for logging
     */
    public String getDynamicRiskInfo(String instrumentToken, Double currentIndexPrice) {
        try {
            double stopLossPoints = dynamicRiskManagementService.calculateDynamicStopLoss(instrumentToken, currentIndexPrice);
            double targetPoints = dynamicRiskManagementService.calculateDynamicTarget(stopLossPoints);
            double volatilityScore = dynamicRiskManagementService.getMarketVolatilityScore(instrumentToken);
            
            return String.format("Dynamic Risk - SL: %.2f pts, Target: %.2f pts, Volatility: %.2f%%", 
                    stopLossPoints, targetPoints, volatilityScore);
        } catch (Exception e) {
            log.error("Error getting dynamic risk info for instrument: {}", instrumentToken, e);
            return "Dynamic Risk - Error calculating";
        }
    }
    
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
    
    public Double calculateProfitLoss(Double entryPrice, Double exitPrice, OrderTypeEnum orderType) {
        if (entryPrice == null || exitPrice == null) {
            log.warn("Invalid parameters for profit/loss calculation: entryPrice={}, exitPrice={}", 
                    entryPrice, exitPrice);
            return 0.0;
        }
        
        // For both CALL and PUT orders, profit is always exitPrice - entryPrice
        // The option price (premium) determines profit/loss, not the underlying index
        Double profitLoss = exitPrice - entryPrice;
        
        log.debug("Calculated profit/loss: {} (Entry: {}, Exit: {}, Type: {})", 
                profitLoss, entryPrice, exitPrice, orderType);
        return profitLoss;
    }
}
