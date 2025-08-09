package com.jtradebot.processor.manager;

import com.jtradebot.processor.model.CallEmaRsiScores;
import com.jtradebot.processor.model.EmaCrossOverConfirmation;
import com.jtradebot.processor.model.IntraDayConfirmationScores;
import com.jtradebot.processor.model.PutEmaRsiScores;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.jtradebot.processor.mapper.IntraDayScoreMapper.mapToIntraDayConfirmationScores;
import static com.jtradebot.processor.model.enums.OrderTypeEnum.CALL_BUY;
import static com.jtradebot.processor.model.enums.OrderTypeEnum.PUT_BUY;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmaCrossTrackingManager {
    private final TickDataManager tickDataManager;
    private final Map<OrderTypeEnum, EmaCrossOverConfirmation> entryDataMap = new ConcurrentHashMap<>(); // Dynamic data map

    public EmaCrossOverConfirmation getEmaCrossOverConfirmation(OrderTypeEnum orderType, CandleTimeFrameEnum candleTimeFrame) {
        IntraDayConfirmationScores intraDayScores = new IntraDayConfirmationScores();
        switch (orderType) {
            case CALL_BUY -> {
                CallEmaRsiScores callEmaRsiScores = tickDataManager.getIndexData(candleTimeFrame).getCallScoresTF().getCallEmaRsiScores();
                intraDayScores = mapToIntraDayConfirmationScores(callEmaRsiScores);
            }
            case PUT_BUY -> {
                PutEmaRsiScores putEmaRsiScores = tickDataManager.getIndexData(candleTimeFrame).getPutScoresTF().getPutEmaRsiScores();
                intraDayScores = mapToIntraDayConfirmationScores(putEmaRsiScores);
            }
        }

        boolean isEmaCrossedEma14 = intraDayScores.getEma5CrossedEma14() > 0;
        boolean isEmaCrossedEma34 = intraDayScores.getEma5CrossedEma34() > 0;
        boolean isCurrentCandleCrossingEma = intraDayScores.getCrossedMultipleEmas() > 0
                || intraDayScores.getLtpCrossedEma5() > 0
                || intraDayScores.getLtpCrossedEma9() > 0
                || intraDayScores.getLtpCrossedEma14() > 0;

        EmaCrossOverConfirmation emaCrossOverConfirmation = getEntryData(orderType);
        if(!emaCrossOverConfirmation.isEma5CrossedEma14()){
            emaCrossOverConfirmation.setEma5CrossedEma14(isEmaCrossedEma14);
        }
        if(!emaCrossOverConfirmation.isEma5CrossedEma34()){
            emaCrossOverConfirmation.setEma5CrossedEma34(isEmaCrossedEma34);
        }
        emaCrossOverConfirmation.setCurrentCandleCrossingEma(isCurrentCandleCrossingEma);
        updateEntryData(orderType, emaCrossOverConfirmation);

        if (emaCrossOverConfirmation.isEma5CrossedEma14() && emaCrossOverConfirmation.isCurrentCandleCrossingEma()) {
            log.info("Ema5 crossed Ema14 and current candle crossing Ema5 for order type: {}", orderType);
        }else if (emaCrossOverConfirmation.isEma5CrossedEma34() && emaCrossOverConfirmation.isCurrentCandleCrossingEma()) {
            log.info("Ema5 crossed Ema34 and current candle crossing Ema5 for order type: {}", orderType);
        }
        return emaCrossOverConfirmation;
    }

    public void checkEmaCrossReversal(double currentAvgCp) {
        if (isEntryDataForOrderType(CALL_BUY) && currentAvgCp < 0) {
            resetEntryData();
        }
        if (isEntryDataForOrderType(PUT_BUY) && currentAvgCp > 0) {
            resetEntryData();
        }
    }

    public EmaCrossOverConfirmation getEntryData(OrderTypeEnum orderType) {
        if (entryDataMap.containsKey(orderType)) {
            return entryDataMap.get(orderType);
        }
        return new EmaCrossOverConfirmation();
    }

    public void updateEntryData(OrderTypeEnum orderType, EmaCrossOverConfirmation newData) {
        entryDataMap.put(orderType, newData); // Update dynamic data if needed during entry execution
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


}
