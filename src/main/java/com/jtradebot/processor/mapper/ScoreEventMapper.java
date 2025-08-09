package com.jtradebot.processor.mapper;

import com.jtradebot.processor.model.CpDetails;
import com.jtradebot.processor.model.Resistance;
import com.jtradebot.processor.model.Support;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.jtradebot.processor.model.event.CpDetailsEvent;
import com.jtradebot.processor.model.event.ProcessedScoreEvent;
import com.zerodhatech.models.Tick;

import java.util.Date;
import java.util.Map;
import java.util.Set;

public class ScoreEventMapper {
    public static ProcessedScoreEvent mapToProcessedScoreEvent(CpDetails cpDetails, Tick tick, Map<CandleTimeFrameEnum,
            Set<Support>> supportMap, Map<CandleTimeFrameEnum, Set<Resistance>> resistanceMap) {
        ProcessedScoreEvent scoreEvent = new ProcessedScoreEvent();
        scoreEvent.setEventTimeStamp(new Date());
        scoreEvent.setTick(tick);
        scoreEvent.setCpDetailsEvent(mapToCpDetailsEvent(cpDetails));
        scoreEvent.setInstrumentToken(tick.getInstrumentToken());
        scoreEvent.setSupportMap(supportMap);
        scoreEvent.setResistanceMap(resistanceMap);
        return scoreEvent;
    }

    private static CpDetailsEvent mapToCpDetailsEvent(CpDetails cpDetails) {
        CpDetailsEvent cpDetailsEvent = new CpDetailsEvent();
        cpDetailsEvent.setOrderType(cpDetails.getOrderType());
        cpDetailsEvent.setCp(cpDetails.getCp());
        cpDetailsEvent.setShortAvgCp(cpDetails.getShortAvgCp());
        cpDetailsEvent.setMidAvgCp(cpDetails.getMidAvgCp());
        cpDetailsEvent.setCpAbs(cpDetails.getCpAbs());
        cpDetailsEvent.setShortAvgCpAbs(cpDetails.getShortAvgCpAbs());
        cpDetailsEvent.setMidAvgCpAbs(cpDetails.getMidAvgCpAbs());
        cpDetailsEvent.setOneMinCp(cpDetails.getOneMinCp());
        cpDetailsEvent.setOneMinAvgCp(cpDetails.getOneMinAvgCp());
        cpDetailsEvent.setOneMinCpAbs(cpDetails.getOneMinCpAbs());
        cpDetailsEvent.setOneMinAvgCpAbs(cpDetails.getOneMinAvgCpAbs());
        cpDetailsEvent.setLongTrendCp(cpDetails.getLongTrendCp());
        cpDetailsEvent.setLongTrendAvgCp(cpDetails.getLongTrendAvgCp());
        cpDetailsEvent.setLongTrendCpAbs(cpDetails.getLongTrendCpAbs());
        cpDetailsEvent.setLongTrendAvgCpAbs(cpDetails.getLongTrendAvgCpAbs());
        cpDetailsEvent.setCpDelta(cpDetails.getCpDelta());
        cpDetailsEvent.setOneMinCpDelta(cpDetails.getOneMinCpDelta());
        cpDetailsEvent.setLongCpDelta(cpDetails.getLongCpDelta());
        cpDetailsEvent.setTotalCpDelta(cpDetails.getTotalCpDelta());
        cpDetailsEvent.setAllCpInSameDirection(cpDetails.isAllCpInSameDirection());
        cpDetailsEvent.setNtp(cpDetails.getNtp());
        cpDetailsEvent.setNoTradeScores(cpDetails.getNoTradeScores());
        return cpDetailsEvent;
    }
}
