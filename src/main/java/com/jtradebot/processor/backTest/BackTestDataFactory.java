package com.jtradebot.processor.backTest;

import com.jtradebot.processor.backTest.source.SourceHistoricalDataService;
import com.jtradebot.processor.backTest.source.SourceDBService;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Queue;

@Component
@RequiredArgsConstructor
public class BackTestDataFactory {

    public enum SourceType {
        HISTORICAL_DATA,
        DATABASE
    }

    private final SourceHistoricalDataService historicalDataService;
    private final SourceDBService dbService;

    public Queue<Tick> getBackTestData(SourceType sourceType, Date fromDate, Date toDate, List<Long> instrumentTokens)
            throws KiteException, IOException {
        return switch (sourceType) {
            case HISTORICAL_DATA -> historicalDataService.getBackTestData(instrumentTokens, fromDate, toDate);
            case DATABASE -> dbService.getBackTestData(fromDate, toDate, instrumentTokens);
        };
    }
}
