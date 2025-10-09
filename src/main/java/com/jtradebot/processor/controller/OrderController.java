package com.jtradebot.processor.controller;

import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.repository.document.JtradeOrder;
import com.jtradebot.processor.service.manual.OrderService;
import com.jtradebot.processor.service.scheduler.BalanceTrackerSchedulerService;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = {"http://localhost:5173", "https://jtradebot.com", "https://www.jtradebot.com"})
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;
    private final BalanceTrackerSchedulerService balanceTrackerSchedulerService;

    /**
     * Place a manual order immediately, skipping all filters and checks
     * @param orderType The type of order to place (CALL_BUY or PUT_BUY)
     */
    @PostMapping({"", "/"})
    public ResponseEntity<Map<String, Object>> placeManualOrder(@RequestParam String orderType) {
        try {
            // Validate order type
            OrderTypeEnum orderTypeEnum;
            try {
                orderTypeEnum = OrderTypeEnum.valueOf(orderType.toUpperCase());
                if (orderTypeEnum != OrderTypeEnum.CALL_BUY && orderTypeEnum != OrderTypeEnum.PUT_BUY) {
                    return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Invalid order type. Must be CALL_BUY or PUT_BUY"
                    ));
                }
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Invalid order type. Must be CALL_BUY or PUT_BUY"
                ));
            }

            log.info("🚀 MANUAL {} ORDER REQUEST RECEIVED", orderTypeEnum);
            JtradeOrder order = orderService.placeOrder(orderTypeEnum);
            
            if (order != null && order.getId() != null) {
                log.info("✅ MANUAL {} ORDER PLACED SUCCESSFULLY - ID: {}", orderTypeEnum, order.getId());
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Manual " + orderTypeEnum + " order placed successfully",
                    "orderId", order.getId(),
                    "orderType", order.getOrderType(),
                    "tradingSymbol", order.getTradingSymbol(),
                    "entryPrice", order.getEntryPrice(),
                    "stopLossPrice", order.getStopLossPrice(),
                    "targetPrice", order.getTargetPrice(),
                    "quantity", order.getQuantity(),
                    "status", order.getStatus()
                ));
            } else {
                log.error("❌ MANUAL {} ORDER FAILED - Order creation returned null", orderTypeEnum);
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to place manual " + orderTypeEnum + " order"
                ));
            }
        } catch (KiteException e) {
            log.error("❌ KITE EXCEPTION PLACING MANUAL ORDER: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Kite API error placing manual order: " + e.getMessage()
            ));
        } catch (Exception e) {
            log.error("❌ ERROR PLACING MANUAL ORDER: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error placing manual order: " + e.getMessage()
            ));
        }
    }

    /**
     * Get status of manual orders
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getManualOrdersStatus() {
        try {
            Map<String, Object> status = orderService.getOrderStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("❌ ERROR GETTING MANUAL ORDERS STATUS: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error getting manual orders status: " + e.getMessage()
            ));
        }
    }

    /**
     * Get detailed status of all orders with comprehensive information
     * Includes active orders with current profit and closed orders total profit
     */
    @GetMapping("/detailed-status")
    public ResponseEntity<Map<String, Object>> getDetailedOrdersStatus() {
        try {
            log.info("📊 DETAILED ORDER STATUS REQUEST RECEIVED");
            Map<String, Object> status = orderService.getDetailedOrderStatus();
            log.info("✅ DETAILED ORDER STATUS RETRIEVED SUCCESSFULLY");
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("❌ ERROR GETTING DETAILED ORDERS STATUS: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error getting detailed orders status: " + e.getMessage()
            ));
        }
    }

    /**
     * Exit all active orders immediately
     * This will find all ACTIVE orders and close them with FORCE_EXIT reason
     */
    @PostMapping("/exitAll")
    public ResponseEntity<Map<String, Object>> exitAllActiveOrders() {
        try {
            log.info("🚪 EXIT ALL ORDERS REQUEST RECEIVED");
            
            Map<String, Object> result = orderService.exitAllActiveOrders();
            
            log.info("✅ EXIT ALL ORDERS COMPLETED");
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("❌ ERROR IN EXIT ALL ORDERS: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error during exit all orders: " + e.getMessage()
            ));
        }
    }

    /**
     * Manually trigger balance check for both CALL and PUT options
     * This will check current balance against required amount for minimum quantity
     * Returns detailed results instead of just logging
     */
    @GetMapping("/balance/check")
    public ResponseEntity<Map<String, Object>> checkBalance() {
        try {
            log.info("🔍 MANUAL BALANCE CHECK REQUEST RECEIVED");
            
            // Get detailed balance check results
            Map<String, Object> result = balanceTrackerSchedulerService.checkBalanceWithResults();
            
            log.info("✅ MANUAL BALANCE CHECK COMPLETED");
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("❌ ERROR IN MANUAL BALANCE CHECK: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error during manual balance check: " + e.getMessage()
            ));
        }
    }

    /**
     * Delete all orders from the jtrade_orders collection
     * WARNING: This will permanently delete all orders
     * Use with caution - typically used for testing or cleanup
     */
    @DeleteMapping("/deleteAll")
    public ResponseEntity<Map<String, Object>> deleteAllOrders() {
        try {
            log.info("🗑️ DELETE ALL ORDERS REQUEST RECEIVED");
            
            Map<String, Object> result = orderService.deleteAllOrders();
            
            boolean success = (boolean) result.get("success");
            if (success) {
                log.info("✅ DELETE ALL ORDERS COMPLETED SUCCESSFULLY");
                return ResponseEntity.ok(result);
            } else {
                log.error("❌ DELETE ALL ORDERS FAILED");
                return ResponseEntity.internalServerError().body(result);
            }
            
        } catch (Exception e) {
            log.error("❌ ERROR IN DELETE ALL ORDERS: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error during delete all orders: " + e.getMessage()
            ));
        }
    }
}
