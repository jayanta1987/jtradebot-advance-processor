package com.jtradebot.processor.backTest;


import com.jtradebot.processor.handler.DateTimeHandler;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.ScheduleManager;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.service.TickProcessService;
import com.jtradebot.processor.service.TickSetupService;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.jtradebot.processor.constants.SchedulerConstants.BACK_TEST_TICK_PROCESSING_INTERVAL;
import static com.jtradebot.processor.model.enums.SchedulerNameEnum.BACK_TESTING;


@Service
@RequiredArgsConstructor
@Slf4j
public class KiteTickBackTester {

    private final ScheduleManager scheduleManager;
    private final TickDataManager tickDataManager;
    private final TickSetupService tickSetupService;
    private final TickProcessService tickProcessService;
    private final BackTestDataFactory backTestDataFactory;
    private final KiteInstrumentHandler kiteInstrumentHandler;

    private Queue<Tick> tickQueue = new LinkedList<>();

    public void init(String fromDateStr, String toDateStr, BackTestDataFactory.SourceType source) {

        try {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            Date fromDate = formatter.parse(fromDateStr);
            Date toDate = formatter.parse(toDateStr);
            Date lastMarketTime = DateTimeHandler.getLastMarketTime(fromDate);
            tickSetupService.connect();
            //tickDataManager.initialize(kiteInstrumentHandler.getNifty50Token().toString(), DateTimeHandler.getLastMarketTime(lastMarketTime));

            List<Long> instrumentTokens = new ArrayList<>();
            instrumentTokens.add(kiteInstrumentHandler.getNifty50Token());
            instrumentTokens.add(kiteInstrumentHandler.getNifty50FutureToken());

            tickQueue = backTestDataFactory.getBackTestData(source, fromDate, toDate, instrumentTokens);
            scheduleManager.startTaskWithFixedRate(BACK_TESTING, this::processTicks, BACK_TEST_TICK_PROCESSING_INTERVAL, null);

        } catch (ParseException | IOException | KiteException e) {
            throw new RuntimeException(e);
        }
    }

    public void processTicks() {
        if (!tickQueue.isEmpty()) {
            Tick tick = tickQueue.poll();
            if (tick == null) {
                return;
            }
            if (tick.getInstrumentToken() == 0) {
                tick.setInstrumentToken(kiteInstrumentHandler.getNifty50Token());
            }
            
            // Use the new unified tick processing method
            // Create a list with single tick to match the new interface
            // Skip market hours check for backtesting
            List<Tick> tickList = new ArrayList<>();
            tickList.add(tick);
            try {
                tickProcessService.processLiveTicks(tickList, true); // true = skip market hours check
            } catch (KiteException e) {
                log.error("Error processing backtest ticks: {}", e.getMessage());
            }

        } else {
            log.info("No more ticks to process.");
        }
    }


}
