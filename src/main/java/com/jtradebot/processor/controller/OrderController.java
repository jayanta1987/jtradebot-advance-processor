package com.jtradebot.processor.controller;

import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.enums.EntryReason;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.model.enums.TradeMode;
import com.jtradebot.processor.repository.document.OrderInfo;
import com.zerodhatech.models.Tick;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final TickDataManager tickDataManager;
    private final com.jtradebot.processor.handler.KiteInstrumentHandler kiteInstrumentHandler;

    @PostMapping("/forceOrder")
    public ResponseEntity<OrderResponse> forceOrder(@RequestBody ForceOrderRequest request) {
        try {
            var lastTick = tickDataManager.getLastTick(kiteInstrumentHandler.getNifty50Token().toString());
            OrderInfo orderInfo = new OrderInfo();
            orderInfo.setEntryReason(request.getEntryReason());
            
            // TODO: Implement actual order creation logic
            if (request.getOrderType() == OrderTypeEnum.CALL_BUY || request.getOrderType() == OrderTypeEnum.PUT_BUY) {
                log.info("Order request received: {} for {}", request.getOrderType(), request.getTradeMode());
                return ResponseEntity.ok(new OrderResponse(true, "Order request received successfully", orderInfo));
            } else {
                return ResponseEntity.badRequest().body(new OrderResponse(false, "Invalid orderType", null));
            }
        } catch (Exception e) {
            log.error("Error in forceOrder", e);
            return ResponseEntity.internalServerError().body(new OrderResponse(false, "Error processing order: " + e.getMessage(), null));
        }
    }

    @GetMapping("/liveData")
    public ResponseEntity<LiveDataResponse> getLiveData() {
        try {
            Tick lastTick = tickDataManager.getLastTick(kiteInstrumentHandler.getNifty50Token().toString());
            
            LiveDataResponse response = new LiveDataResponse();
            response.setSuccess(true);
            response.setMessage("Live data retrieved successfully");
            
            // Tick details
            TickDetails tickDetails = new TickDetails();
            tickDetails.setInstrumentToken(lastTick.getInstrumentToken());
            tickDetails.setLastTradedPrice(lastTick.getLastTradedPrice());
            tickDetails.setHighPrice(lastTick.getHighPrice());
            tickDetails.setLowPrice(lastTick.getLowPrice());
            tickDetails.setOpenPrice(lastTick.getOpenPrice());
            tickDetails.setClosePrice(lastTick.getClosePrice());
            tickDetails.setLastTradedQuantity(lastTick.getLastTradedQuantity());
            tickDetails.setVolumeTradedToday(lastTick.getVolumeTradedToday());
            tickDetails.setTotalBuyQuantity(lastTick.getTotalBuyQuantity());
            tickDetails.setTotalSellQuantity(lastTick.getTotalSellQuantity());
            tickDetails.setOi(lastTick.getOi());
            tickDetails.setOiDayHigh(lastTick.getOpenInterestDayHigh());
            tickDetails.setOiDayLow(lastTick.getOpenInterestDayLow());
            tickDetails.setTickTimestamp(lastTick.getTickTimestamp());
            tickDetails.setLastTradedTime(lastTick.getLastTradedTime());
            response.setTickDetails(tickDetails);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error in getLiveData", e);
            return ResponseEntity.internalServerError().body(new LiveDataResponse(false, "Error retrieving live data: " + e.getMessage(), null));
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ForceOrderRequest {
        private OrderTypeEnum orderType;
        private TradeMode tradeMode;
        private EntryReason entryReason;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OrderResponse {
        private boolean success;
        private String message;
        private OrderInfo orderInfo;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LiveDataResponse {
        private boolean success;
        private String message;
        private TickDetails tickDetails;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TickDetails {
        private Long instrumentToken;
        private Double lastTradedPrice;
        private Double highPrice;
        private Double lowPrice;
        private Double openPrice;
        private Double closePrice;
        private Double lastTradedQuantity;
        private Long volumeTradedToday;
        private Double totalBuyQuantity;
        private Double totalSellQuantity;
        private Double oi;
        private Double oiDayHigh;
        private Double oiDayLow;
        private Date tickTimestamp;
        private Date lastTradedTime;
    }
} 