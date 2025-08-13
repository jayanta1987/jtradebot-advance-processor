package com.jtradebot.processor.controller;

import com.jtradebot.processor.model.enums.OrderTypeEnum;

import com.jtradebot.processor.repository.document.JtradeOrder;
import com.jtradebot.processor.service.ExitStrategyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exit-strategy")
@Slf4j
@RequiredArgsConstructor
public class ExitStrategyController {
    
    private final ExitStrategyService exitStrategyService;
    
    /**
     * Create a new order entry
     */
    @PostMapping("/order")
    public ResponseEntity<JtradeOrder> createOrder(@RequestBody CreateOrderRequest request) {
        try {
            JtradeOrder order = exitStrategyService.createOrderEntry(
                request.getOrderType(),
                request.getTradingSymbol(),
                request.getInstrumentToken(),
                request.getEntryPrice(),
                request.getEntryIndexPrice(),
                request.getStopLossPrice(),
                request.getTargetPrice(),
                request.getQuantity()
            );
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            log.error("Error creating order: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Create a new order entry with realistic option pricing (1% of index price)
     */
    @PostMapping("/order/realistic")
    public ResponseEntity<JtradeOrder> createRealisticOrder(@RequestBody CreateRealisticOrderRequest request) {
        try {
            JtradeOrder order = exitStrategyService.createOrderEntryWithRealisticPricing(
                request.getOrderType(),
                request.getTradingSymbol(),
                request.getInstrumentToken(),
                request.getCurrentIndexPrice(),
                request.getStopLossPercentage(),
                request.getTargetPercentage(),
                request.getQuantity()
            );
            
            if (order == null) {
                return ResponseEntity.badRequest().build();
            }
            
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            log.error("Error creating realistic order: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get all active orders
     */
    @GetMapping("/orders/active")
    public ResponseEntity<List<JtradeOrder>> getActiveOrders() {
        try {
            List<JtradeOrder> orders = exitStrategyService.getActiveOrders();
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            log.error("Error getting active orders: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get active orders by type
     */
    @GetMapping("/orders/active/{orderType}")
    public ResponseEntity<List<JtradeOrder>> getActiveOrdersByType(@PathVariable OrderTypeEnum orderType) {
        try {
            List<JtradeOrder> orders = exitStrategyService.getActiveOrdersByType(orderType);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            log.error("Error getting active orders by type: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Force update orders to database
     */
    @PostMapping("/update-database")
    public ResponseEntity<Void> updateOrdersToDatabase() {
        try {
            exitStrategyService.updateOrdersToDatabase();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error updating orders to database: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    // Request/Response DTOs
    public static class CreateOrderRequest {
        private OrderTypeEnum orderType;
        private String tradingSymbol;
        private Long instrumentToken;
        private Double entryPrice;
        private Double entryIndexPrice;
        private Double stopLossPrice;
        private Double targetPrice;
        private Integer quantity;
        
        // Getters and setters
        public OrderTypeEnum getOrderType() { return orderType; }
        public void setOrderType(OrderTypeEnum orderType) { this.orderType = orderType; }
        
        public String getTradingSymbol() { return tradingSymbol; }
        public void setTradingSymbol(String tradingSymbol) { this.tradingSymbol = tradingSymbol; }
        
        public Long getInstrumentToken() { return instrumentToken; }
        public void setInstrumentToken(Long instrumentToken) { this.instrumentToken = instrumentToken; }
        
        public Double getEntryPrice() { return entryPrice; }
        public void setEntryPrice(Double entryPrice) { this.entryPrice = entryPrice; }
        
        public Double getEntryIndexPrice() { return entryIndexPrice; }
        public void setEntryIndexPrice(Double entryIndexPrice) { this.entryIndexPrice = entryIndexPrice; }
        
        public Double getStopLossPrice() { return stopLossPrice; }
        public void setStopLossPrice(Double stopLossPrice) { this.stopLossPrice = stopLossPrice; }
        
        public Double getTargetPrice() { return targetPrice; }
        public void setTargetPrice(Double targetPrice) { this.targetPrice = targetPrice; }
        
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
    
    public static class CreateRealisticOrderRequest {
        private OrderTypeEnum orderType;
        private String tradingSymbol;
        private Long instrumentToken;
        private Double currentIndexPrice;
        private Double stopLossPercentage;
        private Double targetPercentage;
        private Integer quantity;
        
        // Getters and setters
        public OrderTypeEnum getOrderType() { return orderType; }
        public void setOrderType(OrderTypeEnum orderType) { this.orderType = orderType; }
        
        public String getTradingSymbol() { return tradingSymbol; }
        public void setTradingSymbol(String tradingSymbol) { this.tradingSymbol = tradingSymbol; }
        
        public Long getInstrumentToken() { return instrumentToken; }
        public void setInstrumentToken(Long instrumentToken) { this.instrumentToken = instrumentToken; }
        
        public Double getCurrentIndexPrice() { return currentIndexPrice; }
        public void setCurrentIndexPrice(Double currentIndexPrice) { this.currentIndexPrice = currentIndexPrice; }
        
        public Double getStopLossPercentage() { return stopLossPercentage; }
        public void setStopLossPercentage(Double stopLossPercentage) { this.stopLossPercentage = stopLossPercentage; }
        
        public Double getTargetPercentage() { return targetPercentage; }
        public void setTargetPercentage(Double targetPercentage) { this.targetPercentage = targetPercentage; }
        
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}
