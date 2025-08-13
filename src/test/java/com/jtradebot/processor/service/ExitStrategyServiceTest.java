package com.jtradebot.processor.service;

import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.model.enums.ExitReasonEnum;

import com.jtradebot.processor.repository.document.JtradeOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class ExitStrategyServiceTest {

    @Autowired
    private ExitStrategyService exitStrategyService;

    @Test
    public void testCreateOrderEntry() {
        // Create a test order
        JtradeOrder order = exitStrategyService.createOrderEntry(
            OrderTypeEnum.CALL_BUY,
            "TEST_SYMBOL",
            123456L,
            100.0,  // entry price
            19000.0, // entry index price
            90.0,    // stop loss
            120.0,   // target
            50       // quantity
        );

        assertNotNull(order);
        assertNotNull(order.getId());
        assertEquals(OrderTypeEnum.CALL_BUY, order.getOrderType());
        assertEquals("TEST_SYMBOL", order.getTradingSymbol());
        assertEquals(100.0, order.getEntryPrice());
        assertEquals(90.0, order.getStopLossPrice());
        assertEquals(120.0, order.getTargetPrice());
        assertEquals("ACTIVE", order.getStatus());
    }

    @Test
    public void testGetActiveOrders() {
        // Create a test order
        JtradeOrder order = exitStrategyService.createOrderEntry(
            OrderTypeEnum.PUT_BUY,
            "TEST_PUT_SYMBOL",
            789012L,
            80.0,   // entry price
            19000.0, // entry index price
            100.0,   // stop loss
            60.0,    // target
            25       // quantity
        );

        // Get active orders
        var activeOrders = exitStrategyService.getActiveOrders();
        assertFalse(activeOrders.isEmpty());
        
        // Find our test order
        boolean found = activeOrders.stream()
            .anyMatch(o -> o.getId().equals(order.getId()));
        assertTrue(found);
    }

    @Test
    public void testExitOrder() {
        // Create a test order
        JtradeOrder order = exitStrategyService.createOrderEntry(
            OrderTypeEnum.CALL_BUY,
            "EXIT_TEST_SYMBOL",
            345678L,
            100.0,  // entry price
            19000.0, // entry index price
            90.0,    // stop loss
            120.0,   // target
            50       // quantity
        );

        // Exit the order
        exitStrategyService.exitOrder(
            order.getId(),
            ExitReasonEnum.FORCE_EXIT,
            110.0,   // exit price
            19100.0  // exit index price
        );

        // Verify order is no longer active
        var activeOrders = exitStrategyService.getActiveOrders();
        boolean stillActive = activeOrders.stream()
            .anyMatch(o -> o.getId().equals(order.getId()));
        assertFalse(stillActive);
    }

    @Test
    public void testCheckAndProcessExits() {
        // Create a CALL BUY order with stop loss at 90
        JtradeOrder callOrder = exitStrategyService.createOrderEntry(
            OrderTypeEnum.CALL_BUY,
            "CALL_TEST",
            111111L,
            100.0,  // entry price
            19000.0, // entry index price
            90.0,    // stop loss
            120.0,   // target
            50       // quantity
        );

        // Create a PUT BUY order with stop loss at 110
        JtradeOrder putOrder = exitStrategyService.createOrderEntry(
            OrderTypeEnum.PUT_BUY,
            "PUT_TEST",
            222222L,
            100.0,  // entry price
            19000.0, // entry index price
            110.0,   // stop loss
            80.0,    // target
            50       // quantity
        );

        // Check exits with price that should trigger CALL stop loss
        exitStrategyService.checkAndProcessExits(85.0, 18900.0);

        // Verify CALL order was exited (stop loss hit)
        var activeOrders = exitStrategyService.getActiveOrders();
        boolean callStillActive = activeOrders.stream()
            .anyMatch(o -> o.getId().equals(callOrder.getId()));
        assertFalse(callStillActive);

        // Verify PUT order is still active
        boolean putStillActive = activeOrders.stream()
            .anyMatch(o -> o.getId().equals(putOrder.getId()));
        assertTrue(putStillActive);

        // Check exits with price that should trigger PUT stop loss
        exitStrategyService.checkAndProcessExits(115.0, 19100.0);

        // Verify PUT order was also exited
        activeOrders = exitStrategyService.getActiveOrders();
        putStillActive = activeOrders.stream()
            .anyMatch(o -> o.getId().equals(putOrder.getId()));
        assertFalse(putStillActive);
    }
}
