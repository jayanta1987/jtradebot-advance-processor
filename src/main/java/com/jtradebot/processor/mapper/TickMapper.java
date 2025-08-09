package com.jtradebot.processor.mapper;

import com.jtradebot.tickstore.repository.CalculatedTick;
import com.jtradebot.processor.repository.document.TickDocument;
import com.jtradebot.tickstore.repository.TickModel;
import com.zerodhatech.models.Tick;

import java.util.Collections;
import java.util.Optional;


public class TickMapper {
    public static Tick convertToZerodhaTick(TickModel tick) {
        Tick zerodhaTick = new Tick();
        zerodhaTick.setMode(tick.getMode());
        zerodhaTick.setTradable(tick.isTradable());
        zerodhaTick.setInstrumentToken(tick.getInstrumentToken());
        zerodhaTick.setLastTradedPrice(tick.getLastTradedPrice());
        zerodhaTick.setHighPrice(tick.getHighPrice());
        zerodhaTick.setLowPrice(tick.getLowPrice());
        zerodhaTick.setOpenPrice(tick.getOpenPrice());
        zerodhaTick.setClosePrice(tick.getClosePrice());
        zerodhaTick.setLastTradedQuantity(tick.getLastTradedQuantity());
        zerodhaTick.setAverageTradePrice(tick.getAverageTradePrice());
        zerodhaTick.setVolumeTradedToday(tick.getVolumeTradedToday());
        zerodhaTick.setTotalBuyQuantity(tick.getTotalBuyQuantity());
        zerodhaTick.setTotalSellQuantity(tick.getTotalSellQuantity());
        zerodhaTick.setLastTradedTime(tick.getLastTradedTime());
        zerodhaTick.setOi(tick.getOi());
        zerodhaTick.setOpenInterestDayHigh(tick.getOiDayHigh());
        zerodhaTick.setOpenInterestDayLow(tick.getOiDayLow());
        zerodhaTick.setTickTimestamp(tick.getTickTimestamp());
        zerodhaTick.setLastTradedTime(tick.getLastTradedTime());

        zerodhaTick.setMarketDepth(tick.getDepth());

        return zerodhaTick;
    }

    public static TickModel convertToTickModel(Tick tick) {
        TickModel tickModel = new TickModel();
        tickModel.setMode(tick.getMode());
        tickModel.setTradable(tick.isTradable());
        tickModel.setInstrumentToken(tick.getInstrumentToken());
        tickModel.setLastTradedPrice(tick.getLastTradedPrice());
        tickModel.setHighPrice(tick.getHighPrice());
        tickModel.setLowPrice(tick.getLowPrice());
        tickModel.setOpenPrice(tick.getOpenPrice());
        tickModel.setClosePrice(tick.getClosePrice());
        tickModel.setLastTradedQuantity(tick.getLastTradedQuantity());
        tickModel.setAverageTradePrice(tick.getAverageTradePrice());
        tickModel.setVolumeTradedToday(tick.getVolumeTradedToday());
        tickModel.setTotalBuyQuantity(tick.getTotalBuyQuantity());
        tickModel.setTotalSellQuantity(tick.getTotalSellQuantity());
        tickModel.setTickTimestamp(tick.getTickTimestamp());
        tickModel.setLastTradedTime(tick.getLastTradedTime());

        tickModel.setOi(tick.getOi());
        tickModel.setOiDayHigh(tick.getOpenInterestDayHigh());
        tickModel.setOiDayLow(tick.getOpenInterestDayLow());
        tickModel.setDepth(tick.getMarketDepth());

        return tickModel;
    }

    public static CalculatedTick toCalculatedTick(Tick tick) {
        CalculatedTick calculatedTick = new CalculatedTick();
        calculatedTick.setTick(convertToTickModel(tick));
        return calculatedTick;
    }
}
