package com.jtradebot.processor.manager;

import com.jtradebot.processor.model.IndexData;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.zerodhatech.models.Tick;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
@Slf4j
public class TickDataManager {

    private final BarSeriesManager barSeriesManager;

    private final ConcurrentHashMap<CandleTimeFrameEnum, IndexData> indexDataMap = new ConcurrentHashMap<>();

    private final ReentrantLock lock = new ReentrantLock();

    @Setter
    @Getter
    private Date lastTickTime;

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
        } catch (Exception e) {
            log.error("Error adding tick to Nifty50 - {}", e.getMessage());
        } finally {
            lock.unlock();
        }
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
