package com.jtradebot.processor.mapper;

import com.jtradebot.processor.model.CpDetails;
import com.jtradebot.tickstore.repository.CalculatedTick;
import com.jtradebot.processor.repository.document.TickDocument;
import com.jtradebot.tickstore.repository.CpDetailsModel;
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

    public static CpDetailsModel convertToCpDetailsModel(CpDetails cpDetails) {
        CpDetailsModel cpDetailsModel = new CpDetailsModel();

        cpDetailsModel.setCp(cpDetails.getCp());
        cpDetailsModel.setCpAbs(cpDetails.getCpAbs());

        cpDetailsModel.setShortAvgCp(cpDetails.getShortAvgCp());
        cpDetailsModel.setShortAvgCpAbs(cpDetails.getShortAvgCpAbs());

        cpDetailsModel.setMidAvgCp(cpDetails.getMidAvgCp());
        cpDetailsModel.setMidAvgCpAbs(cpDetails.getMidAvgCpAbs());

        cpDetailsModel.setLongTrendCp(cpDetails.getLongTrendCp());
        cpDetailsModel.setLongTrendCpAbs(cpDetails.getLongTrendCpAbs());

        cpDetailsModel.setLongTrendAvgCp(cpDetails.getLongTrendAvgCp());
        cpDetailsModel.setLongTrendAvgCpAbs(cpDetails.getLongTrendAvgCpAbs());

        cpDetailsModel.setOneMinCp(cpDetails.getOneMinCp());
        cpDetailsModel.setOneMinCpAbs(cpDetails.getOneMinCpAbs());

        cpDetailsModel.setOneMinAvgCp(cpDetails.getOneMinAvgCp());
        cpDetailsModel.setOneMinAvgCpAbs(cpDetails.getOneMinAvgCpAbs());
        cpDetailsModel.setCpDelta(cpDetails.getCpDelta());
        cpDetailsModel.setOneMinCpDelta(cpDetails.getOneMinCpDelta());
        cpDetailsModel.setLongCpDelta(cpDetails.getLongCpDelta());
        cpDetailsModel.setTotalCpDelta(cpDetails.getTotalCpDelta());
        cpDetailsModel.setAllCpInSameDirection(cpDetails.isAllCpInSameDirection());
        cpDetailsModel.setFutureSignal(cpDetails.isFutureSignal());
        cpDetailsModel.setFutureTrend(cpDetails.getFutureTrend());
        cpDetailsModel.setFutureBullishSurge(cpDetails.isFutureBullishSurge());
        cpDetailsModel.setFutureBearishSurge(cpDetails.isFutureBearishSurge());
        cpDetailsModel.setNtp(cpDetails.getNtp());
        cpDetailsModel.setOrderType(cpDetails.getOrderType());

        return cpDetailsModel;
    }

    public static CalculatedTick toCalculatedTick(Tick tick, CpDetails cpDetails) {
        CalculatedTick calculatedTick = new CalculatedTick();
        if(cpDetails != null) {
            calculatedTick.setCpDetails(convertToCpDetailsModel(cpDetails));
        }
        calculatedTick.setTick(convertToTickModel(tick));

        return calculatedTick;
    }
}
