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
public class LivePnLTest {

    @Autowired
    private ExitStrategyService exitStrategyService;

    @Test
    public void testLiveProfitLossLogging() {
        // Create a realistic order
        JtradeOrder order = exitStrategyService.createOrderEntryWithRealisticPricing(
            OrderTypeEnum.CALL_BUY,
            "NIFTY24JAN19000CE",
            123456L,
            19000.0, // entry index price
            0.5,     // stop loss percentage
            1.0,     // target percentage
            50       // quantity
        );

        assertNotNull(order);
        assertTrue(exitStrategyService.hasActiveOrder());
        
        System.out.println("\n=== LIVE P&L TEST ===");
        System.out.println("Created order: " + order.getOrderType() + " " + order.getTradingSymbol());
        System.out.println("Entry Price: " + order.getEntryPrice());
        System.out.println("Entry Index: " + order.getEntryIndexPrice());
        
        // Simulate different index movements and check P&L
        Double[] testIndexPrices = {18900.0, 19000.0, 19100.0, 19200.0, 19300.0};
        
        for (Double currentIndexPrice : testIndexPrices) {
            System.out.println("\n--- Index Price: " + currentIndexPrice + " ---");
            
            // This will trigger live P&L logging
            exitStrategyService.checkAndProcessExits(null, currentIndexPrice);
            
            // Check if order is still active
            if (exitStrategyService.hasActiveOrder()) {
                System.out.println("Order still active - checking P&L...");
            } else {
                System.out.println("Order was exited!");
                break;
            }
        }
    }

    @Test
    public void testSingleOrderRule() {
        // Create first order
        JtradeOrder order1 = exitStrategyService.createOrderEntryWithRealisticPricing(
            OrderTypeEnum.CALL_BUY,
            "NIFTY24JAN19000CE",
            123456L,
            19000.0,
            0.5,
            1.0,
            50
        );

        assertNotNull(order1);
        assertTrue(exitStrategyService.hasActiveOrder());
        
        // Try to create second order - should fail
        JtradeOrder order2 = exitStrategyService.createOrderEntryWithRealisticPricing(
            OrderTypeEnum.PUT_BUY,
            "NIFTY24JAN19000PE",
            789012L,
            19000.0,
            0.5,
            1.0,
            50
        );

        assertNull(order2);
        assertTrue(exitStrategyService.hasActiveOrder());
        
        System.out.println("✓ Single order rule working correctly - second order creation blocked");
    }
}
