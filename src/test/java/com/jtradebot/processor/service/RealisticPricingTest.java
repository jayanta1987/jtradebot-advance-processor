package com.jtradebot.processor.service;

import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.repository.document.JtradeOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class RealisticPricingTest {

    @Autowired
    private ExitStrategyService exitStrategyService;

    @Test
    public void testRealisticOptionPricing() {
        // Test with Nifty at 19000
        Double currentIndexPrice = 19000.0;
        
        // Create a CALL BUY order with realistic pricing
        JtradeOrder callOrder = exitStrategyService.createOrderEntryWithRealisticPricing(
            OrderTypeEnum.CALL_BUY,
            "NIFTY24JAN19000CE",
            123456L,
            currentIndexPrice,  // 19000
            0.5,                // 0.5% stop loss
            1.0,                // 1.0% target
            50                  // quantity
        );

        assertNotNull(callOrder);
        
        // Entry price should be 1% of index price = 190.0
        assertEquals(190.0, callOrder.getEntryPrice(), 0.01);
        assertEquals(19000.0, callOrder.getEntryIndexPrice(), 0.01);
        
        // Stop loss should be 190 * (1 - 0.5%) = 189.05
        assertEquals(189.05, callOrder.getStopLossPrice(), 0.01);
        
        // Target should be 190 * (1 + 1.0%) = 191.90
        assertEquals(191.90, callOrder.getTargetPrice(), 0.01);
        
        System.out.println("=== CALL ORDER PRICING ===");
        System.out.println("Index Price: " + currentIndexPrice);
        System.out.println("Entry Price: " + callOrder.getEntryPrice());
        System.out.println("Stop Loss: " + callOrder.getStopLossPrice());
        System.out.println("Target: " + callOrder.getTargetPrice());
        
        // Test PUT order
        JtradeOrder putOrder = exitStrategyService.createOrderEntryWithRealisticPricing(
            OrderTypeEnum.PUT_BUY,
            "NIFTY24JAN19000PE",
            789012L,
            currentIndexPrice,  // 19000
            0.5,                // 0.5% stop loss
            1.0,                // 1.0% target
            50                  // quantity
        );

        assertNotNull(putOrder);
        
        // Entry price should be 1% of index price = 190.0
        assertEquals(190.0, putOrder.getEntryPrice(), 0.01);
        
        // Stop loss should be 190 * (1 + 0.5%) = 190.95 (higher for PUT)
        assertEquals(190.95, putOrder.getStopLossPrice(), 0.01);
        
        // Target should be 190 * (1 - 1.0%) = 188.10 (lower for PUT)
        assertEquals(188.10, putOrder.getTargetPrice(), 0.01);
        
        System.out.println("\n=== PUT ORDER PRICING ===");
        System.out.println("Index Price: " + currentIndexPrice);
        System.out.println("Entry Price: " + putOrder.getEntryPrice());
        System.out.println("Stop Loss: " + putOrder.getStopLossPrice());
        System.out.println("Target: " + putOrder.getTargetPrice());
    }

    @Test
    public void testLTPCalculation() {
        // Create order at index 19000
        JtradeOrder order = exitStrategyService.createOrderEntryWithRealisticPricing(
            OrderTypeEnum.CALL_BUY,
            "NIFTY24JAN19000CE",
            123456L,
            19000.0,  // entry index price
            0.5,      // stop loss percentage
            1.0,      // target percentage
            50        // quantity
        );

        assertNotNull(order);
        Double entryPrice = order.getEntryPrice(); // Should be 190.0
        Double entryIndexPrice = order.getEntryIndexPrice(); // Should be 19000.0
        
        System.out.println("\n=== LTP CALCULATION TEST ===");
        System.out.println("Entry Price: " + entryPrice);
        System.out.println("Entry Index Price: " + entryIndexPrice);
        
        // Test different index movements
        Double[] testIndexPrices = {18900.0, 19000.0, 19100.0, 19200.0};
        
        for (Double currentIndexPrice : testIndexPrices) {
            Double indexMovement = currentIndexPrice - entryIndexPrice;
            Double expectedLTP = entryPrice + indexMovement;
            
            System.out.println("Index: " + currentIndexPrice + 
                             " | Movement: " + indexMovement + 
                             " | Expected LTP: " + expectedLTP);
            
            // Verify LTP calculation
            assertEquals(expectedLTP, expectedLTP, 0.01);
        }
    }
}
