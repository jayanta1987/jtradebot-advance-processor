package com.jtradebot.processor.manager;

import com.jtradebot.processor.model.indicator.EmaCrossOverConfirmation;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.jtradebot.processor.model.enums.OrderTypeEnum.CALL_BUY;
import static com.jtradebot.processor.model.enums.OrderTypeEnum.PUT_BUY;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmaCrossTrackingManager {
    private final TickDataManager tickDataManager;
    private final KiteInstrumentHandler kiteInstrumentHandler;
    private final Map<OrderTypeEnum, EmaCrossOverConfirmation> entryDataMap = new ConcurrentHashMap<>();

    // TODO: Old CP-based scoring system removed - this method needs refactoring for new strategy
    public EmaCrossOverConfirmation getEmaCrossOverConfirmation(OrderTypeEnum orderType, CandleTimeFrameEnum candleTimeFrame) {
        log.warn("getEmaCrossOverConfirmation called but old CP-based scoring system has been removed - returning stub data");
        
        // Print current Nifty tick timestamp
        try {
            String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();
            String niftyFutureToken = kiteInstrumentHandler.getNifty50FutureToken().toString();
            
            Tick lastNiftyTick = tickDataManager.getLastTick(niftyToken);
            Tick lastNiftyFutureTick = tickDataManager.getLastTick(niftyFutureToken);
            
            if (lastNiftyTick != null && lastNiftyFutureTick != null) {
                log.info("Current Nifty: Time={}, LTP={}, Vol(Fut)={}", 
                    lastNiftyTick.getTickTimestamp(),
                    lastNiftyTick.getLastTradedPrice(),
                    lastNiftyFutureTick.getVolumeTradedToday());
            } else {
                log.warn("No Nifty tick data available - Spot: {}, Future: {}", 
                    lastNiftyTick != null, lastNiftyFutureTick != null);
            }
        } catch (Exception e) {
            log.error("Error getting Nifty tick timestamp: {}", e.getMessage());
        }
        
        EmaCrossOverConfirmation emaCrossOverConfirmation = getEntryData(orderType);
        // Return stub data until refactored for new strategy
        return emaCrossOverConfirmation;
    }

    public EmaCrossOverConfirmation getEntryData(OrderTypeEnum orderType) {
        if (entryDataMap.containsKey(orderType)) {
            return entryDataMap.get(orderType);
        }
        return new EmaCrossOverConfirmation();
    }

    public void updateEntryData(OrderTypeEnum orderType, EmaCrossOverConfirmation newData) {
        entryDataMap.put(orderType, newData);
    }

    public void resetEntryData() {
        entryDataMap.clear();
    }

    public boolean isEntryDataForOrderType(OrderTypeEnum orderType) {
        return entryDataMap.containsKey(orderType);
    }

    public Map<OrderTypeEnum, EmaCrossOverConfirmation> getEntryDataMap() {
        return entryDataMap;
    }

    public void reset() {
        entryDataMap.clear();
    }
    
    /**
     * Print current Nifty tick timestamp and LTP
     */
    public void printCurrentNiftyTickInfo() {
        try {
            String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();
            String niftyFutureToken = kiteInstrumentHandler.getNifty50FutureToken().toString();
            
            Tick lastNiftyTick = tickDataManager.getLastTick(niftyToken);
            Tick lastNiftyFutureTick = tickDataManager.getLastTick(niftyFutureToken);
            
            if (lastNiftyTick != null && lastNiftyFutureTick != null) {
                log.info("Nifty: Time={}, LTP={}, Vol(Fut)={}, H={}, L={}, O={}, C={}", 
                    lastNiftyTick.getTickTimestamp(),
                    lastNiftyTick.getLastTradedPrice(),
                    lastNiftyFutureTick.getVolumeTradedToday(),
                    lastNiftyTick.getHighPrice(),
                    lastNiftyTick.getLowPrice(),
                    lastNiftyTick.getOpenPrice(),
                    lastNiftyTick.getClosePrice());
            } else {
                log.warn("No Nifty tick data available - Spot: {}, Future: {}", 
                    lastNiftyTick != null, lastNiftyFutureTick != null);
            }
        } catch (Exception e) {
            log.error("Error getting Nifty tick info: {}", e.getMessage());
        }
    }
}
