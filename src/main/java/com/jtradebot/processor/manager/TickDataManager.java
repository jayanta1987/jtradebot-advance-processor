package com.jtradebot.processor.manager;

import com.jtradebot.processor.indicator.MultiEmaIndicator;
import com.jtradebot.processor.indicator.RsiIndicator;
import com.jtradebot.processor.indicator.SupportResistanceIndicator;
import com.jtradebot.processor.model.indicator.EmaIndicatorInfo;
import com.jtradebot.processor.model.indicator.EmaInfo;
import com.jtradebot.processor.model.IndexData;
import com.jtradebot.processor.model.indicator.Resistance;
import com.jtradebot.processor.model.indicator.Support;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.jtradebot.processor.model.enums.TrendEnum;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
@Slf4j
public class TickDataManager {

    private final BarSeriesManager barSeriesManager;
    private final SupportResistanceIndicator supportResistanceIndicator;
    private final MultiEmaIndicator multiEmaIndicator;
    private final RsiIndicator rsiIndicator;
    private final com.jtradebot.processor.handler.KiteInstrumentHandler kiteInstrumentHandler;

    private final ConcurrentHashMap<CandleTimeFrameEnum, IndexData> indexDataMap = new ConcurrentHashMap<>();

    private final ReentrantLock lock = new ReentrantLock();

    public void initialize(String instrumentToken, Date latestWorkingDay) {
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
                addIndexData(instrumentToken, tick, CandleTimeFrameEnum.ONE_MIN);
                addIndexData(instrumentToken, tick, CandleTimeFrameEnum.THREE_MIN);
                addIndexData(instrumentToken, tick, CandleTimeFrameEnum.FIVE_MIN);
                addIndexData(instrumentToken, tick, CandleTimeFrameEnum.FIFTEEN_MIN);
                addIndexData(instrumentToken, tick, CandleTimeFrameEnum.ONE_HOUR);
                addIndexData(instrumentToken, tick, CandleTimeFrameEnum.ONE_DAY);
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

        IndexData indexData = IndexData.builder()
                .emaIndicatorInfo(emaIndicatorInfo)
                .emaValues(emaValues)
                .barSeries(barSeriesForTimeFrame)
                .supports(supports)
                .resistances(resistances)
                .rsiValue(rsiValue)
                .trend(trend)
                .build();
        indexDataMap.put(timeFrame, indexData);
    }

    public BarSeries getBarSeriesForTimeFrame(String instrumentToken, CandleTimeFrameEnum timeFrame) {
        return barSeriesManager.getBarSeriesForTimeFrame(instrumentToken, timeFrame);
    }


    public Tick getLastTick(String instrumentToken) {
        return barSeriesManager.getLastTick(instrumentToken);
    }


    public void reset() {
        barSeriesManager.reset();
        indexDataMap.clear();
    }
}
