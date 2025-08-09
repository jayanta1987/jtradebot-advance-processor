package com.jtradebot.processor.controller;

import com.jtradebot.processor.manager.CPManager;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.CpDetails;
import com.jtradebot.processor.model.NoTradeScores;
import com.jtradebot.processor.model.enums.EntryReason;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.model.enums.TradeMode;
import com.jtradebot.processor.model.enums.TrendEnum;
import com.jtradebot.processor.order.CallEntryService;
import com.jtradebot.processor.order.PutEntryService;
import com.jtradebot.processor.repository.document.OrderInfo;
import com.jtradebot.processor.order.ManualEntryRejectedException;
import com.zerodhatech.models.Tick;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final TickDataManager tickDataManager;
    private final CPManager cpManager;
    private final CallEntryService callEntryService;
    private final PutEntryService putEntryService;
    private final com.jtradebot.processor.handler.KiteInstrumentHandler kiteInstrumentHandler;

    @PostMapping("/forceOrder")
    public ResponseEntity<OrderResponse> forceOrder(@RequestBody ForceOrderRequest request) {
        try {
            var lastTick = tickDataManager.getLastTick(kiteInstrumentHandler.getNifty50Token().toString());
            var noTradeScoresList = tickDataManager.getNoTradeScoresList();
            var cpDetailsOpt = cpManager.getCpDetails(lastTick, noTradeScoresList);
            if (cpDetailsOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(new OrderResponse(false, "Could not fetch latest CpDetails", null));
            }
            CpDetails cpDetails = cpDetailsOpt.get();
            cpDetails.setOrderType(request.getOrderType());
            OrderInfo orderInfo = new OrderInfo();
            orderInfo.setCpDetails(cpDetails);
            orderInfo.setEntryReason(request.getEntryReason());
            if (request.getOrderType() == OrderTypeEnum.CALL_BUY) {
                callEntryService.createOrder(orderInfo, request.getTradeMode());
            } else if (request.getOrderType() == OrderTypeEnum.PUT_BUY) {
                putEntryService.createOrder(orderInfo, request.getTradeMode());
            } else {
                return ResponseEntity.badRequest().body(new OrderResponse(false, "Invalid orderType", null));
            }
            return ResponseEntity.ok(new OrderResponse(true, "Order triggered successfully", orderInfo));
        } catch (ManualEntryRejectedException e) {
            log.warn("Manual entry rejected: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new OrderResponse(false, e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error in forceOrder", e);
            return ResponseEntity.internalServerError().body(new OrderResponse(false, "Error triggering order: " + e.getMessage(), null));
        }
    }

    @GetMapping("/liveData")
    public ResponseEntity<LiveDataResponse> getLiveData() {
        try {
            Tick lastTick = tickDataManager.getLastTick(kiteInstrumentHandler.getNifty50Token().toString());
            List<NoTradeScores> noTradeScoresList = tickDataManager.getNoTradeScoresList();
            Optional<CpDetails> cpDetailsOpt = cpManager.getCpDetails(lastTick, noTradeScoresList);
            
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
            
            // NTP details
            if (cpDetailsOpt.isPresent()) {
                CpDetails cpDetails = cpDetailsOpt.get();
                NtpDetails ntpDetails = new NtpDetails();
                if (cpDetails.getNtp() != null) {
                    ntpDetails.setNtp3Min(cpDetails.getNtp().getNtp3Min());
                    ntpDetails.setNtp5Min(cpDetails.getNtp().getNtp5Min());
                    ntpDetails.setNtp15Min(cpDetails.getNtp().getNtp15Min());
                    ntpDetails.setNoTradeZone(cpDetails.getNtp().isNoTradeZone(cpDetails));
                    ntpDetails.setDynamicNoTradeZone(cpDetails.getNtp().isDynamicNoTradeZone(cpDetails));
                }
                response.setNtpDetails(ntpDetails);
                
                // CP details
                CpDetailsResponse cpDetailsResponse = new CpDetailsResponse();
                cpDetailsResponse.setOrderType(cpDetails.getOrderType());
                cpDetailsResponse.setCp(cpDetails.getCp());
                cpDetailsResponse.setCpAbs(cpDetails.getCpAbs());
                cpDetailsResponse.setShortAvgCp(cpDetails.getShortAvgCp());
                cpDetailsResponse.setShortAvgCpAbs(cpDetails.getShortAvgCpAbs());
                cpDetailsResponse.setMidAvgCp(cpDetails.getMidAvgCp());
                cpDetailsResponse.setMidAvgCpAbs(cpDetails.getMidAvgCpAbs());
                cpDetailsResponse.setOneMinCp(cpDetails.getOneMinCp());
                cpDetailsResponse.setOneMinCpAbs(cpDetails.getOneMinCpAbs());
                cpDetailsResponse.setOneMinAvgCp(cpDetails.getOneMinAvgCp());
                cpDetailsResponse.setOneMinAvgCpAbs(cpDetails.getOneMinAvgCpAbs());
                cpDetailsResponse.setLongTrendCp(cpDetails.getLongTrendCp());
                cpDetailsResponse.setLongTrendCpAbs(cpDetails.getLongTrendCpAbs());
                cpDetailsResponse.setLongTrendAvgCp(cpDetails.getLongTrendAvgCp());
                cpDetailsResponse.setLongTrendAvgCpAbs(cpDetails.getLongTrendAvgCpAbs());
                cpDetailsResponse.setCpDelta(cpDetails.getCpDelta());
                cpDetailsResponse.setOneMinCpDelta(cpDetails.getOneMinCpDelta());
                cpDetailsResponse.setLongCpDelta(cpDetails.getLongCpDelta());
                cpDetailsResponse.setTotalCpDelta(cpDetails.getTotalCpDelta());
                cpDetailsResponse.setAllCpInSameDirection(cpDetails.isAllCpInSameDirection());
                cpDetailsResponse.setFutureSignal(cpDetails.isFutureSignal());
                cpDetailsResponse.setFutureTrend(cpDetails.getFutureTrend());
                cpDetailsResponse.setFutureBullishSurge(cpDetails.isFutureBullishSurge());
                cpDetailsResponse.setFutureBearishSurge(cpDetails.isFutureBearishSurge());
                cpDetailsResponse.setHighCP(cpDetails.isHighCP());
                response.setCpDetails(cpDetailsResponse);
            } else {
                response.setMessage("Live data retrieved but CP details not available");
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error in getLiveData", e);
            return ResponseEntity.internalServerError().body(new LiveDataResponse(false, "Error retrieving live data: " + e.getMessage(), null, null, null));
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
        private NtpDetails ntpDetails;
        private CpDetailsResponse cpDetails;
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

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class NtpDetails {
        private double ntp3Min;
        private double ntp5Min;
        private double ntp15Min;
        private boolean noTradeZone;
        private boolean dynamicNoTradeZone;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CpDetailsResponse {
        private OrderTypeEnum orderType;
        private Double cp;
        private Double cpAbs;
        private Double shortAvgCp;
        private Double shortAvgCpAbs;
        private Double midAvgCp;
        private Double midAvgCpAbs;
        private Double oneMinCp;
        private Double oneMinCpAbs;
        private Double oneMinAvgCp;
        private Double oneMinAvgCpAbs;
        private Double longTrendCp;
        private Double longTrendCpAbs;
        private Double longTrendAvgCp;
        private Double longTrendAvgCpAbs;
        private double cpDelta;
        private double oneMinCpDelta;
        private double longCpDelta;
        private double totalCpDelta;
        private boolean allCpInSameDirection;
        private boolean futureSignal;
        private TrendEnum futureTrend;
        private boolean futureBullishSurge;
        private boolean futureBearishSurge;
        private boolean highCP;
    }
} 