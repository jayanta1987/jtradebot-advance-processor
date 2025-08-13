package com.jtradebot.processor.service.impl;

import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.model.enums.ExitReasonEnum;
import com.jtradebot.processor.repository.JtradeOrderRepository;
import com.jtradebot.processor.repository.document.JtradeOrder;
import com.jtradebot.processor.service.ExitStrategyService;
import com.jtradebot.processor.service.OptionPricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExitStrategyServiceImpl implements ExitStrategyService {
    
    private final JtradeOrderRepository jtradeOrderRepository;
    private final OptionPricingService optionPricingService;
    
    // In-memory storage for active orders
    private final Map<String, JtradeOrder> activeOrdersMap = new ConcurrentHashMap<>();
    
    // Flag to track if orders need database update
    private final AtomicBoolean needsUpdate = new AtomicBoolean(false);
    
    @PostConstruct
    public void initializeService() {
        log.info("Initializing ExitStrategyService...");
        loadActiveOrdersFromDatabase();
        log.info("ExitStrategyService initialized with {} active orders", activeOrdersMap.size());
    }
    
    @Override
    public JtradeOrder createOrderEntry(OrderTypeEnum orderType, String tradingSymbol, Long instrumentToken,
                                       Double entryPrice, Double entryIndexPrice, Double stopLossPrice,
                                       Double targetPrice, Integer quantity) {
        
        // Check if there's already an active order
        if (hasActiveOrder()) {
            log.warn("Cannot create new order - there's already an active order. Please exit existing order first.");
            return null;
        }
        
        JtradeOrder order = new JtradeOrder();
        order.setId(UUID.randomUUID().toString());
        order.setOrderType(orderType);
        order.setTradingSymbol(tradingSymbol);
        order.setInstrumentToken(instrumentToken);
        order.setEntryPrice(entryPrice);
        order.setEntryIndexPrice(entryIndexPrice);
        order.setStopLossPrice(stopLossPrice);
        order.setTargetPrice(targetPrice);
        order.setQuantity(quantity);
        order.setStatus("ACTIVE");
        order.setEntryTime(new Date());
        order.setCreatedAt(new Date());
        order.setLastUpdated(new Date());
        
        // Store in memory
        activeOrdersMap.put(order.getId(), order);
        needsUpdate.set(true);
        
        log.info("Created new order entry: {} - {} @ {} (SL: {}, Target: {})", 
                orderType, tradingSymbol, entryPrice, stopLossPrice, targetPrice);
        
        return order;
    }
    
    @Override
    public JtradeOrder createOrderEntryWithRealisticPricing(OrderTypeEnum orderType, String tradingSymbol,
                                                          Long instrumentToken, Double currentIndexPrice,
                                                          Double stopLossPercentage, Double targetPercentage,
                                                          Integer quantity) {
        
        // Check if there's already an active order
        if (hasActiveOrder()) {
            log.warn("Cannot create new order - there's already an active order. Please exit existing order first.");
            return null;
        }
        
        // Calculate realistic entry price (1% of index price)
        Double entryPrice = optionPricingService.calculateEntryPrice(currentIndexPrice);
        if (entryPrice == null) {
            log.error("Failed to calculate entry price for index price: {}", currentIndexPrice);
            return null;
        }
        
        // Calculate stop loss and target prices
        Double stopLossPrice = optionPricingService.calculateStopLossPrice(entryPrice, stopLossPercentage, orderType);
        Double targetPrice = optionPricingService.calculateTargetPrice(entryPrice, targetPercentage, orderType);
        
        if (stopLossPrice == null || targetPrice == null) {
            log.error("Failed to calculate stop loss or target price for order type: {}", orderType);
            return null;
        }
        
        log.info("Creating realistic order: {} - {} @ {} (Index: {}, SL: {}, Target: {})", 
                orderType, tradingSymbol, entryPrice, currentIndexPrice, stopLossPrice, targetPrice);
        
        // Create order with calculated prices
        return createOrderEntry(orderType, tradingSymbol, instrumentToken, entryPrice, currentIndexPrice, 
                              stopLossPrice, targetPrice, quantity);
    }
    
    @Override
    public void exitOrder(String orderId, ExitReasonEnum exitReason, Double exitPrice, Double exitIndexPrice) {
        JtradeOrder order = activeOrdersMap.get(orderId);
        if (order == null) {
            log.warn("Order not found for exit: {}", orderId);
            return;
        }
        
        order.markExited(exitReason, exitPrice, exitIndexPrice);
        
        // Calculate profit/loss using option pricing service
        Double points = optionPricingService.calculateProfitLoss(
            order.getEntryPrice(), 
            exitPrice, 
            order.getOrderType()
        );
        
        order.setTotalPoints(points);
        order.setTotalProfit(points * order.getQuantity());
        
        // Remove from active orders
        activeOrdersMap.remove(orderId);
        needsUpdate.set(true);
        
        log.info("Exited order: {} - {} @ {} (Reason: {}, Points: {}, Profit: {})", 
                order.getOrderType(), order.getTradingSymbol(), exitPrice, 
                exitReason, points, order.getTotalProfit());
    }
    
    @Override
    public List<JtradeOrder> getActiveOrders() {
        return new ArrayList<>(activeOrdersMap.values());
    }
    
    @Override
    public List<JtradeOrder> getActiveOrdersByType(OrderTypeEnum orderType) {
        return activeOrdersMap.values().stream()
                .filter(order -> order.getOrderType() == orderType)
                .toList();
    }
    
    @Override
    public void checkAndProcessExits(Double currentPrice, Double currentIndexPrice) {
        if (activeOrdersMap.isEmpty()) {
            return;
        }
        
        // Log current profit/loss for all active orders
        logCurrentProfitLoss(currentIndexPrice);
        
        List<JtradeOrder> ordersToExit = new ArrayList<>();
        
        for (JtradeOrder order : activeOrdersMap.values()) {
            // Calculate current LTP based on index movement
            Double currentLTP = calculateCurrentLTP(order, currentIndexPrice);
            
            if (shouldExitOrder(order, currentLTP, currentIndexPrice)) {
                ordersToExit.add(order);
            }
        }
        
        // Process exits
        for (JtradeOrder order : ordersToExit) {
            Double currentLTP = calculateCurrentLTP(order, currentIndexPrice);
            ExitReasonEnum exitReason = determineExitReason(order, currentLTP, currentIndexPrice);
            exitOrder(order.getId(), exitReason, currentLTP, currentIndexPrice);
        }
    }
    
    /**
     * Calculate current LTP based on index movement
     * LTP = Entry Price + (Current Index Price - Entry Index Price)
     */
    private Double calculateCurrentLTP(JtradeOrder order, Double currentIndexPrice) {
        return optionPricingService.calculateCurrentLTP(
            order.getEntryPrice(), 
            order.getEntryIndexPrice(), 
            currentIndexPrice
        );
    }
    
    @Override
    public void updateOrdersToDatabase() {
        if (!needsUpdate.get()) {
            return;
        }
        
        try {
            // Update all active orders in database
            for (JtradeOrder order : activeOrdersMap.values()) {
                order.updateLastUpdated();
                jtradeOrderRepository.save(order);
            }
            
            needsUpdate.set(false);
            log.debug("Updated {} active orders to database", activeOrdersMap.size());
            
        } catch (Exception e) {
            log.error("Error updating orders to database: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public void initialize() {
        loadActiveOrdersFromDatabase();
    }
    
    private void loadActiveOrdersFromDatabase() {
        try {
            List<JtradeOrder> activeOrders = jtradeOrderRepository.findAllActiveOrders();
            activeOrdersMap.clear();
            
            for (JtradeOrder order : activeOrders) {
                activeOrdersMap.put(order.getId(), order);
            }
            
            log.info("Loaded {} active orders from database", activeOrders.size());
            
        } catch (Exception e) {
            log.error("Error loading active orders from database: {}", e.getMessage(), e);
        }
    }
    
    private boolean shouldExitOrder(JtradeOrder order, Double currentLTP, Double currentIndexPrice) {
        if (order.getStopLossPrice() != null && order.getTargetPrice() != null) {
            // Check stop loss
            if (order.getOrderType() == OrderTypeEnum.CALL_BUY) {
                if (currentLTP <= order.getStopLossPrice()) {
                    return true;
                }
            } else if (order.getOrderType() == OrderTypeEnum.PUT_BUY) {
                if (currentLTP >= order.getStopLossPrice()) {
                    return true;
                }
            }
            
            // Check target
            if (order.getOrderType() == OrderTypeEnum.CALL_BUY) {
                if (currentLTP >= order.getTargetPrice()) {
                    return true;
                }
            } else if (order.getOrderType() == OrderTypeEnum.PUT_BUY) {
                if (currentLTP <= order.getTargetPrice()) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private ExitReasonEnum determineExitReason(JtradeOrder order, Double currentLTP, Double currentIndexPrice) {
        if (order.getStopLossPrice() != null && order.getTargetPrice() != null) {
            // Check if it's a stop loss exit
            if (order.getOrderType() == OrderTypeEnum.CALL_BUY) {
                if (currentLTP <= order.getStopLossPrice()) {
                    return ExitReasonEnum.STOPLOSS_HIT;
                }
            } else if (order.getOrderType() == OrderTypeEnum.PUT_BUY) {
                if (currentLTP >= order.getStopLossPrice()) {
                    return ExitReasonEnum.STOPLOSS_HIT;
                }
            }
            
            // Check if it's a target exit
            if (order.getOrderType() == OrderTypeEnum.CALL_BUY) {
                if (currentLTP >= order.getTargetPrice()) {
                    return ExitReasonEnum.TARGET_HIT;
                }
            } else if (order.getOrderType() == OrderTypeEnum.PUT_BUY) {
                if (currentLTP <= order.getTargetPrice()) {
                    return ExitReasonEnum.TARGET_HIT;
                }
            }
        }
        
        return ExitReasonEnum.FORCE_EXIT;
    }
    
    @Override
    public boolean hasActiveOrder() {
        return !activeOrdersMap.isEmpty();
    }
    
    @Override
    public void logCurrentProfitLoss(Double currentIndexPrice) {
        if (activeOrdersMap.isEmpty()) {
            return;
        }
        
        for (JtradeOrder order : activeOrdersMap.values()) {
            Double currentLTP = calculateCurrentLTP(order, currentIndexPrice);
            Double profitLoss = optionPricingService.calculateProfitLoss(
                order.getEntryPrice(), 
                currentLTP, 
                order.getOrderType()
            );
            
            Double totalProfitLoss = profitLoss * order.getQuantity();
            
            log.info("LIVE P&L - {} {}: Entry={}, Current LTP={}, P&L={}, Total P&L={}, Index={}", 
                order.getOrderType(), 
                order.getTradingSymbol(),
                order.getEntryPrice(),
                currentLTP,
                profitLoss,
                totalProfitLoss,
                currentIndexPrice);
        }
    }
}
