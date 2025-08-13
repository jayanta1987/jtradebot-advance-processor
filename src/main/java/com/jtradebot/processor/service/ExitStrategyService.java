package com.jtradebot.processor.service;

import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.model.enums.ExitReasonEnum;

import com.jtradebot.processor.repository.document.JtradeOrder;

import java.util.List;

public interface ExitStrategyService {
    
    /**
     * Create a new order entry
     */
    JtradeOrder createOrderEntry(OrderTypeEnum orderType, String tradingSymbol, Long instrumentToken, 
                                Double entryPrice, Double entryIndexPrice, Double stopLossPrice, 
                                Double targetPrice, Integer quantity);
    
    /**
     * Create a new order entry with realistic option pricing (1% of index price)
     */
    JtradeOrder createOrderEntryWithRealisticPricing(OrderTypeEnum orderType, String tradingSymbol, 
                                                    Long instrumentToken, Double currentIndexPrice, 
                                                    Double stopLossPercentage, Double targetPercentage, 
                                                    Integer quantity);
    
    /**
     * Exit an order with specified reason and prices
     */
    void exitOrder(String orderId, ExitReasonEnum exitReason, Double exitPrice, Double exitIndexPrice);
    
    /**
     * Check if there's already an active order (to prevent multiple orders)
     */
    boolean hasActiveOrder();
    
    /**
     * Get current profit/loss for active orders
     */
    void logCurrentProfitLoss(Double currentIndexPrice);
    
    /**
     * Get all active orders from memory
     */
    List<JtradeOrder> getActiveOrders();
    
    /**
     * Get active orders by order type
     */
    List<JtradeOrder> getActiveOrdersByType(OrderTypeEnum orderType);
    
    /**
     * Check if any orders need to be exited based on current market conditions
     */
    void checkAndProcessExits(Double currentPrice, Double currentIndexPrice);
    
    /**
     * Force update all orders to database (called by scheduler)
     */
    void updateOrdersToDatabase();
    
    /**
     * Initialize the service by loading active orders from database
     */
    void initialize();
}
