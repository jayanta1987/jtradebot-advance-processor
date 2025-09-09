package com.jtradebot.processor.controller;

import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.repository.document.JtradeOrder;
import com.jtradebot.processor.service.manual.ManualOrderService;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/manual")
@RequiredArgsConstructor
@Slf4j
public class ManualOrderController {

    private final ManualOrderService manualOrderService;

    /**
     * Place a manual order immediately, skipping all filters and checks
     * @param orderType The type of order to place (CALL_BUY or PUT_BUY)
     */
    @PostMapping("/order")
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
            JtradeOrder order = manualOrderService.placeManualOrder(orderTypeEnum);
            
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
    @GetMapping("/orders/status")
    public ResponseEntity<Map<String, Object>> getManualOrdersStatus() {
        try {
            Map<String, Object> status = manualOrderService.getManualOrdersStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("❌ ERROR GETTING MANUAL ORDERS STATUS: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error getting manual orders status: " + e.getMessage()
            ));
        }
    }
}
