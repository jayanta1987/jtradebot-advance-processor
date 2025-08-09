package com.jtradebot.processor.manager;

import com.jtradebot.processor.model.*;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.model.enums.TrendEnum;
import com.jtradebot.processor.repository.document.Ntp;
import com.jtradebot.processor.trend.*;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.*;


@Service
@RequiredArgsConstructor
@Slf4j
public class CPManager {

    private final CallTrendPointFinder callTrendPointFinder;
    private final PutTrendPointFinder putTrendPointFinder;

    private final NoTrendFinder noTrendFinder;
    private final ShortTrend shortTrend;
    private final LongTrend longTrend;
    private final OneMinTrend oneMinTrend;

    private Double calculateCp(CandleTimeFrameEnum[] candleTimeFrameEnums) {
        double callCp = callTrendPointFinder.getFinalCallScore(candleTimeFrameEnums).getFinalScore();
        double putCp = putTrendPointFinder.getFinalPutScore(candleTimeFrameEnums).getFinalScore();
        return calculateCP(callCp, putCp);
    }

    private double calculateCP(double totalCallScore, double totalPutScore) {
        double cp = 0;
        if (totalCallScore > totalPutScore) {
            cp = totalCallScore;
        } else if (totalPutScore > totalCallScore) {
            cp = -totalPutScore;
        }
        return cp;
    }

    public Optional<CpDetails> getCpDetails(Tick lastTick, List<NoTradeScores> noTradeScoresList) {
        CandleTimeFrameEnum[] candleTimeFrameEnums = {THREE_MIN, FIVE_MIN};
        CallData callData = callTrendPointFinder.getFinalCallScore(candleTimeFrameEnums);
        PutData putData = putTrendPointFinder.getFinalPutScore(candleTimeFrameEnums);
        double cp = calculateCP(callData.getFinalScore(), putData.getFinalScore());
        shortTrend.addTrendingPoint(cp);

        CandleTimeFrameEnum[] oneMinTimeframe = {ONE_MIN};
        double oneMinCp = calculateCp(oneMinTimeframe);
        oneMinTrend.addTrendingPoint(oneMinCp);
        double oneMinCpAvg = oneMinTrend.getAverageShortTrendingPoint();

        CandleTimeFrameEnum[] longTrendTimeframes = {FIFTEEN_MIN, ONE_HOUR, ONE_DAY};
        double longTrendCp = calculateCp(longTrendTimeframes);
        longTrend.addTrendingPoint(longTrendCp);
        double longTrendAvg = longTrend.getAverageShortTrendingPoint();

        CandleTimeFrameEnum[] ntpTimeframes = {THREE_MIN, FIVE_MIN, FIFTEEN_MIN};
        Ntp ntp = noTrendFinder.getNoTradeScore(ntpTimeframes);

        double shortAvgCp = shortTrend.getAverageShortTrendingPoint();
        double midAvgCp = shortTrend.getAverageMidTrendingPoint();

        CpDetails cpDetails = new CpDetails();
        cpDetails.setCallData(callData);
        cpDetails.setPutData(putData);

        cpDetails.setNtp(ntp);
        cpDetails.setCp(cp);
        cpDetails.setShortAvgCp(shortAvgCp);
        cpDetails.setMidAvgCp(midAvgCp);
        cpDetails.setOneMinCp(oneMinCp);
        cpDetails.setOneMinAvgCp(oneMinCpAvg);
        cpDetails.setLongTrendCp(longTrendCp);
        cpDetails.setLongTrendAvgCp(longTrendAvg);
        cpDetails.calculateAllCpInSameDirection(getOrderType());

        cpDetails.calculateAllCpDeltas();

        cpDetails.setOrderType(getOrderType());
        cpDetails.setNoTradeScores(noTradeScoresList);



        log.info("Current trends: {} {} -> {}, nTP {} [oneMinCP={} oneMinCpAvg={}] [CP={} shortAvgCP={} midAvgCP={}] [longTrendsCP={} longTrendAvg={}] ", lastTick.getInstrumentToken(),
                lastTick.getTickTimestamp(), lastTick.getLastTradedPrice(), ntp, oneMinCp, oneMinCpAvg, cp, shortAvgCp, midAvgCp, longTrendCp, longTrendAvg);

        return Optional.of(cpDetails);
    }

    public void addFutureCpDetails(CpDetails cpDetails, CallFutureScores callFutureScores, PutFutureScores putFutureScores) {
        cpDetails.calculateFutureSignal(callFutureScores , putFutureScores);
        if (cpDetails.isFutureSignal() || cpDetails.getFutureTrend() != TrendEnum.SIDEWAYS || cpDetails.isFutureBullishSurge() || cpDetails.isFutureBearishSurge()) {
            log.info("[futureSignal={} futureTrend={} futureBullishSurge={} futureBearishSurge={}] ",
                    cpDetails.isFutureSignal(), cpDetails.getFutureTrend(), cpDetails.isFutureBullishSurge(), cpDetails.isFutureBearishSurge());
        }
    }

    private OrderTypeEnum getOrderType() {
        TrendEnum trend = shortTrend.getTrend();
        return switch (trend) {
            case UP -> OrderTypeEnum.CALL_BUY;
            case DOWN -> OrderTypeEnum.PUT_BUY;
            default -> OrderTypeEnum.NO_TRADE;
        };
    }

    public void reset() {
        shortTrend.reset();
        longTrend.reset();
        oneMinTrend.reset();
    }



}
