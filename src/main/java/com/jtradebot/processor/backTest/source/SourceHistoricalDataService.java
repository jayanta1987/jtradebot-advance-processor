package com.jtradebot.processor.backTest.source;

import com.jtradebot.processor.backTest.model.Candle;
import com.jtradebot.processor.handler.DateTimeHandler;
import com.jtradebot.processor.manager.BarSeriesManager;
import com.jtradebot.processor.model.enums.KiteHistoricalDataTimeframeEnum;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SourceHistoricalDataService {
    private final BarSeriesManager barSeriesManager;

    public Queue<Tick> getBackTestData(List<Long> instrumentTokens, Date fromDate, Date toDate) {
        Queue<Tick> allTicks = new LinkedList<>();

        for (Long instrumentToken : instrumentTokens) {
            HistoricalData historicalData = barSeriesManager.fetchHistoricalData(instrumentToken.toString(),
                    KiteHistoricalDataTimeframeEnum.MINUTE.getTimeframe(), fromDate, toDate);
            Queue<Tick> ticks = convertToTicks(historicalData, instrumentToken);
            allTicks.addAll(ticks);
        }

        return allTicks.stream()
                .sorted(Comparator.comparing(Tick::getTickTimestamp))
                .collect(Collectors.toCollection(LinkedList::new));
    }

    private Queue<Tick> convertToTicks(HistoricalData historicalData, Long instrumentToken) {
        Queue<Tick> tickQueue = new LinkedList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
        historicalData.dataArrayList.forEach(data -> {
            Tick tick = new Tick();
            tick.setLastTradedPrice(data.close);
            tick.setVolumeTradedToday(data.volume);
            tick.setInstrumentToken(instrumentToken);
            tick.setTradable(data.volume > 0);
            tick.setOi(data.oi);
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(data.timeStamp, formatter);
            tick.setLastTradedTime(Date.from(zonedDateTime.toInstant()));
            tick.setTickTimestamp(tick.getLastTradedTime());
            tickQueue.add(tick);
        });
        return tickQueue;
    }

}
