package com.jtradebot.processor.service;

import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.CPManager;
import com.jtradebot.processor.manager.EmaCrossTrackingManager;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.BarSeriesData;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class TickEventTracker {
    private final TickDataManager tickDataManager;
    private final CPManager scoreManager;
    private final EmaCrossTrackingManager emaCrossTrackingManager;
    private final KiteInstrumentHandler kiteInstrumentHandler;
    private final ConcurrentHashMap<String, Long> lastTickEventTimeStampMap = new ConcurrentHashMap<>();

    private static final long CLEANUP_INTERVAL = 10000;

    @Scheduled(fixedRate = 10000)
    public void cleanUp() {
        List<String> instruments = new ArrayList<>();
        instruments.add(kiteInstrumentHandler.getNifty50Token().toString());
        instruments.add(kiteInstrumentHandler.getNifty50FutureToken().toString());

        AtomicBoolean isReset = new AtomicBoolean(false);
        instruments.forEach(instrumentToken -> {
            Long lastTickEventTimestamp = lastTickEventTimeStampMap.get(instrumentToken);
            if (lastTickEventTimestamp !=null && !tickDataManager.isNotInitialized(instrumentToken)
                    && lastTickEventTimestamp < System.currentTimeMillis() - CLEANUP_INTERVAL) {
                log.info("...........Resetting all data as no tick event received in last 10 seconds for instrument: {}", instrumentToken);

                isReset.set(true);
            }
        });

        if (isReset.get()) {
            tickDataManager.reset();
            scoreManager.reset();
            emaCrossTrackingManager.reset();
        }
    }

    public void setLastTickEventTimestamp(String instrumentToken, Long timestamp) {
        lastTickEventTimeStampMap.put(instrumentToken, timestamp);
    }

}
