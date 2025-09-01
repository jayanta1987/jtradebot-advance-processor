package com.jtradebot.processor.service.scheduler;

import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import lombok.RequiredArgsConstructor;
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
    private final KiteInstrumentHandler kiteInstrumentHandler;
    private final ConcurrentHashMap<String, Long> lastTickEventTimeStampMap = new ConcurrentHashMap<>();

    private static final long CLEANUP_INTERVAL = 10000;
    
    // Cache for instrument tokens to avoid repeated calls
    private volatile String cachedNiftyToken;
    private volatile String cachedNiftyFutureToken;
    private volatile long lastTokenCacheTime;
    private static final long TOKEN_CACHE_DURATION = 60000; // 1 minute cache

    @Scheduled(fixedRate = 10000)
    public void cleanUp() {
        List<String> instruments = getCachedInstruments();

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
        }
    }

    /**
     * Get cached instrument tokens to avoid repeated calls to KiteInstrumentHandler
     */
    private List<String> getCachedInstruments() {
        long currentTime = System.currentTimeMillis();
        
        // Check if we need to refresh the cache
        if (cachedNiftyToken == null || cachedNiftyFutureToken == null || 
            (currentTime - lastTokenCacheTime) > TOKEN_CACHE_DURATION) {
            
            log.debug("Refreshing instrument token cache");
            cachedNiftyToken = kiteInstrumentHandler.getNifty50Token().toString();
            cachedNiftyFutureToken = kiteInstrumentHandler.getNifty50FutureToken().toString();
            lastTokenCacheTime = currentTime;
        }
        
        List<String> instruments = new ArrayList<>();
        instruments.add(cachedNiftyToken);
        instruments.add(cachedNiftyFutureToken);
        
        return instruments;
    }

    public void setLastTickEventTimestamp(String instrumentToken, Long timestamp) {
        lastTickEventTimeStampMap.put(instrumentToken, timestamp);
    }

}
