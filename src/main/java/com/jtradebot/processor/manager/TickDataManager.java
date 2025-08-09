package com.jtradebot.processor.manager;

import com.jtradebot.processor.indicator.MultiEmaIndicator;
import com.jtradebot.processor.indicator.RsiIndicator;
import com.jtradebot.processor.indicator.SupportResistanceIndicator;
import com.jtradebot.processor.model.*;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.jtradebot.processor.model.enums.TrendEnum;
import com.jtradebot.processor.score.NoTradeScoreTracker;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;


import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TickDataManager {

    private final BarSeriesManager barSeriesManager;
    private final SupportResistanceIndicator supportResistanceIndicator;
    private final MultiEmaIndicator multiEmaIndicator;
    private final RsiIndicator rsiIndicator;
    private final ScoreManager scoreManager;
    private final NoTradeScoreTracker noTradeScoreTracker;
    private final com.jtradebot.processor.handler.KiteInstrumentHandler kiteInstrumentHandler;

    private final ConcurrentHashMap<CandleTimeFrameEnum, IndexData> indexDataMap = new ConcurrentHashMap<>();

    private final ReentrantLock lock = new ReentrantLock();

    public void initialize(String instrumentToken, Date latestWorkingDay) {
        //barSeriesManager.reset();
        barSeriesManager.initializeBarSeriesData(instrumentToken, latestWorkingDay);
    }

    public boolean isNotInitialized(String instrumentToken) {
        return !barSeriesManager.isInitialized(instrumentToken);
    }


    public void add(String instrumentToken, Tick tick) {
        lock.lock();
        try {
            barSeriesManager.addTick(instrumentToken, tick);
            if(instrumentToken.equals(kiteInstrumentHandler.getNifty50Token().toString())){
                addIndexData(instrumentToken, tick, ONE_MIN);
                addIndexData(instrumentToken, tick, THREE_MIN);
                addIndexData(instrumentToken, tick, FIVE_MIN);
                addIndexData(instrumentToken, tick, FIFTEEN_MIN);
                addIndexData(instrumentToken, tick, ONE_HOUR);
                addIndexData(instrumentToken, tick, ONE_DAY);
            }
        } catch (Exception e) {
            log.error("Error adding tick to Nifty50 - {}", e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private void addIndexData(String instrumentToken, Tick tick, CandleTimeFrameEnum timeFrame) {
        BarSeries barSeriesForTimeFrame = getBarSeriesForTimeFrame(instrumentToken, timeFrame);
        EmaIndicatorInfo emaIndicatorInfo = multiEmaIndicator.createEmaInfoForTimeframes(barSeriesForTimeFrame, timeFrame);
        EmaInfo emaValues = multiEmaIndicator.calculateEmaValues(barSeriesForTimeFrame, timeFrame);
        int[] periods = {50, 34, 20, 9};
        Set<Support> supports = supportResistanceIndicator.calculateSupports(timeFrame, barSeriesForTimeFrame, tick.getLastTradedPrice(), emaIndicatorInfo, periods);
        Set<Resistance> resistances = supportResistanceIndicator.calculateResistances(timeFrame, barSeriesForTimeFrame, tick.getLastTradedPrice(), emaIndicatorInfo, periods);
        Double rsiValue = rsiIndicator.getRsiValue(barSeriesForTimeFrame, 14);
        TrendEnum trend = multiEmaIndicator.determineOverallTrendByEma(barSeriesForTimeFrame);

        CallScoresTF callScoresTF = scoreManager.getCallScores(tick, timeFrame, trend, barSeriesForTimeFrame, emaIndicatorInfo, supports, resistances, rsiValue, instrumentToken);
        PutScoresTF putScoresTF = scoreManager.getPutScores(tick, timeFrame, trend, barSeriesForTimeFrame, emaIndicatorInfo, supports, resistances, rsiValue, instrumentToken);
        NoTradeScores noTradeScores = noTradeScoreTracker.getPreConfirmationScore(barSeriesForTimeFrame, emaIndicatorInfo, supports, resistances, tick, instrumentToken);
        noTradeScores.setTimeFrame(timeFrame);

        ATRIndicator atrIndicator = new ATRIndicator(barSeriesForTimeFrame, 14);
        Double atrValue = atrIndicator.getValue(barSeriesForTimeFrame.getEndIndex()).doubleValue();

        IndexData indexData = IndexData.builder()
                .emaIndicatorInfo(emaIndicatorInfo)
                .emaValues(emaValues)
                .barSeries(barSeriesForTimeFrame)
                .supports(supports)
                .resistances(resistances)
                .rsiValue(rsiValue)
                .trend(trend)
                .callScoresTF(callScoresTF)
                .putScoresTF(putScoresTF)
                .noTradeScores(noTradeScores)
                .atrValue(atrValue)
                .build();
        indexDataMap.put(timeFrame, indexData);
    }

    public BarSeries getBarSeriesForTimeFrame(String instrumentToken, CandleTimeFrameEnum timeFrame) {
        return barSeriesManager.getBarSeriesForTimeFrame(instrumentToken, timeFrame);
    }

    public IndexData getIndexData(CandleTimeFrameEnum timeFrame) {
        return indexDataMap.get(timeFrame);
    }

    public Tick getLastTick(String instrumentToken) {
        return barSeriesManager.getLastTick(instrumentToken);
    }

    public List<NoTradeScores> getNoTradeScoresList() {
        List<NoTradeScores> noTradeScoresList = new ArrayList<>();
        for (CandleTimeFrameEnum timeFrame : CandleTimeFrameEnum.values()) {
            IndexData indexData = getIndexData(timeFrame);
            if (indexData != null) {
                noTradeScoresList.add(indexData.getNoTradeScores());
            }
        }
        return noTradeScoresList;
    }

    public Map<CandleTimeFrameEnum, Double> getTimeFrameWiseAtrValues() {
        Map<CandleTimeFrameEnum, Double> atrValues = new HashMap<>();
        for (CandleTimeFrameEnum timeFrame : CandleTimeFrameEnum.values()) {
            IndexData indexData = getIndexData(timeFrame);
            if (indexData != null) {
                atrValues.put(timeFrame, indexData.getAtrValue());
            }
        }
        return atrValues;
    }

    public Map<CandleTimeFrameEnum, Set<Support>> getAllSupportMap() {
        Map<CandleTimeFrameEnum, Set<Support>> supportMap = new HashMap<>();
        for (CandleTimeFrameEnum timeFrame : CandleTimeFrameEnum.values()) {
            IndexData indexData = getIndexData(timeFrame);
            if (indexData != null) {
                supportMap.put(timeFrame, indexData.getSupports());
            }
        }
        return supportMap;
    }

    public Map<CandleTimeFrameEnum, Set<Resistance>> getAllResistanceMap() {
        Map<CandleTimeFrameEnum, Set<Resistance>> resistanceMap = new HashMap<>();
        for (CandleTimeFrameEnum timeFrame : CandleTimeFrameEnum.values()) {
            IndexData indexData = getIndexData(timeFrame);
            if (indexData != null) {
                resistanceMap.put(timeFrame, indexData.getResistances());
            }
        }
        return resistanceMap;
    }

    public void reset() {
        barSeriesManager.reset();
        indexDataMap.clear();
    }
}
