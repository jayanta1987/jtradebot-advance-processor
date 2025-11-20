package com.jtradebot.processor.service.price;

import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.jtradebot.processor.repository.InstrumentRepository;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Quote;
import com.zerodhatech.models.Tick;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Service for analyzing Open Interest (OI) data from Kite Connect getQuote API
 * Fetches OI data for ±200 strikes every 5 seconds and calculates OI signals
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OIAnalysisService {

    private final KiteConnect kiteConnect;
    private final InstrumentRepository instrumentRepository;
    private final TickDataManager tickDataManager;
    private final KiteInstrumentHandler kiteInstrumentHandler;
    private final com.jtradebot.processor.service.config.ConfigCategoryScoringService configCategoryScoringService;

    // Cache for OI data per strike
    private final Map<String, List<OIDataPoint>> oiDataHistory = new ConcurrentHashMap<>();
    
    // Cache for current OI signals
    private volatile OISignalsCache oiSignalsCache = OISignalsCache.builder().build();
    private volatile boolean isCacheInitialized = false;
    private volatile long lastCacheRefreshTime = 0L;
    private static final long REFRESH_INTERVAL = 5000L; // 5 seconds
    
    // ReadWriteLock for thread-safe access
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    // Cache for instruments (refreshes daily)
    private volatile List<com.jtradebot.processor.repository.document.Instrument> cachedInstruments = new ArrayList<>();
    private volatile boolean instrumentsCacheInitialized = false;
    private volatile String lastCacheDate = "";

    /**
     * Scheduled task to fetch OI data every 5 seconds
     */
    @Scheduled(fixedRate = 5000) // Every 5 seconds
    public void scheduledFetchOIData() {
        try {
            double currentIndexPrice = getCurrentNiftyIndexPrice();
            if (currentIndexPrice > 0) {
                long currentTime = System.currentTimeMillis();
                long timeSinceLastRefresh = currentTime - lastCacheRefreshTime;
                
                if (timeSinceLastRefresh >= REFRESH_INTERVAL) {
                    log.debug("🔄 SCHEDULED OI FETCH - Index: {}, Time: {}ms", currentIndexPrice, timeSinceLastRefresh);
                    fetchAndStoreOIData(currentIndexPrice);
                }
            }
        } catch (Exception e) {
            log.error("Error in scheduled OI data fetch", e);
        }
    }

    /**
     * Fetch OI data for ±200 strikes and store it
     */
    private void fetchAndStoreOIData(double niftyIndexPrice) {
        if (!refreshInProgress.compareAndSet(false, true)) {
            log.debug("⏭️ SKIPPING OI FETCH - Already in progress");
            return;
        }

        try {
            cacheLock.writeLock().lock();
            try {
                // Get valid options in ±200 range
                List<com.jtradebot.processor.repository.document.Instrument> instruments = getValidOptionsInRange(niftyIndexPrice);
                
                if (instruments.isEmpty()) {
                    log.warn("⚠️ No instruments found in ±200 range for index: {}", niftyIndexPrice);
                    return;
                }

                // Prepare trading symbols with "NFO:" prefix
                List<String> tradingSymbols = instruments.stream()
                        .map(instrument -> "NFO:" + instrument.getTradingSymbol())
                        .collect(Collectors.toList());

                // Batch fetch quotes (max 8-10 symbols in ±200 range, so batch size of 10 is sufficient)
                int batchSize = 10;
                int totalFetched = 0;
                
                for (int i = 0; i < tradingSymbols.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, tradingSymbols.size());
                    List<String> batch = tradingSymbols.subList(i, end);
                    
                    try {
                        Map<String, Quote> quotes = kiteConnect.getQuote(batch.toArray(new String[0]));
                        
                        if (quotes != null && !quotes.isEmpty()) {
                            // Store OI data for each strike
                            for (Map.Entry<String, Quote> entry : quotes.entrySet()) {
                                String tradingSymbol = entry.getKey();
                                Quote quote = entry.getValue();
                                
                                if (quote != null) {
                                    double oiValue = quote.oi;
                                    if (oiValue > 0) {
                                        String timestamp = quote.timestamp != null ? quote.timestamp.toString() : String.valueOf(System.currentTimeMillis());
                                        storeOIDataPoint(tradingSymbol, oiValue, quote.lastPrice, timestamp);
                                        totalFetched++;
                                    }
                                }
                            }
                        }
                    } catch (KiteException e) {
                        log.warn("Error fetching quotes for batch {}-{}: {}", i, end, e.getMessage());
                    }
                }

                // Calculate OI signals after storing data
                calculateOISignals(niftyIndexPrice);
                
                lastCacheRefreshTime = System.currentTimeMillis();
                isCacheInitialized = true;
                
                log.info("✅ OI DATA FETCHED - Index: {}, Instruments: {}, Quotes fetched: {}", 
                        niftyIndexPrice, instruments.size(), totalFetched);
                
            } finally {
                cacheLock.writeLock().unlock();
                refreshInProgress.set(false);
            }
        } catch (Exception e) {
            log.error("Error fetching and storing OI data", e);
            refreshInProgress.set(false);
        }
    }

    /**
     * Store OI data point with timestamp
     */
    private void storeOIDataPoint(String tradingSymbol, double oi, double price, String timestamp) {
        oiDataHistory.computeIfAbsent(tradingSymbol, k -> new ArrayList<>()).add(
                OIDataPoint.builder()
                        .oi(oi)
                        .price(price)
                        .timestamp(timestamp)
                        .fetchTime(System.currentTimeMillis())
                        .build()
        );
        
        // Keep only last 1000 data points per symbol to avoid memory issues
        List<OIDataPoint> history = oiDataHistory.get(tradingSymbol);
        if (history.size() > 1000) {
            history.remove(0);
        }
    }

    /**
     * Calculate OI signals for different timeframes based on accumulated data
     */
    private void calculateOISignals(double niftyIndexPrice) {
        try {
            // Get Nifty Future tick for price movement
            Tick niftyFutureTick = getNiftyFutureTick();
            if (niftyFutureTick == null) {
                log.warn("⚠️ No Nifty Future tick available for OI signal calculation");
                return;
            }

            // Get BarSeries for different timeframes
            String futureToken = String.valueOf(niftyFutureTick.getInstrumentToken());
            BarSeries oneMinSeries = tickDataManager.getBarSeriesForTimeFrame(futureToken, CandleTimeFrameEnum.ONE_MIN);
            BarSeries fiveMinSeries = tickDataManager.getBarSeriesForTimeFrame(futureToken, CandleTimeFrameEnum.FIVE_MIN);
            BarSeries fifteenMinSeries = tickDataManager.getBarSeriesForTimeFrame(futureToken, CandleTimeFrameEnum.FIFTEEN_MIN);

            // Calculate aggregate OI change for all strikes
            OISignalResult signal1min = calculateAggregateOISignal(oneMinSeries, "1min");
            OISignalResult signal5min = calculateAggregateOISignal(fiveMinSeries, "5min");
            OISignalResult signal15min = calculateAggregateOISignal(fifteenMinSeries, "15min");

            // Update cache
            oiSignalsCache = OISignalsCache.builder()
                    .oiBullishSignal1min(signal1min.isBullish())
                    .oiBearishSignal1min(signal1min.isBearish())
                    .oiBullishSignal5min(signal5min.isBullish())
                    .oiBearishSignal5min(signal5min.isBearish())
                    .oiBullishSignal15min(signal15min.isBullish())
                    .oiBearishSignal15min(signal15min.isBearish())
                    .callBuyScore(calculateCallBuyScore(signal1min, signal5min, signal15min))
                    .putBuyScore(calculatePutBuyScore(signal1min, signal5min, signal15min))
                    .lastUpdated(System.currentTimeMillis())
                    .build();

            log.debug("📊 OI SIGNALS CALCULATED - 1min: {}/{}, 5min: {}/{}, 15min: {}/{}", 
                    signal1min.isBullish(), signal1min.isBearish(),
                    signal5min.isBullish(), signal5min.isBearish(),
                    signal15min.isBullish(), signal15min.isBearish());

        } catch (Exception e) {
            log.error("Error calculating OI signals", e);
        }
    }

    /**
     * Calculate aggregate OI signal for a timeframe
     */
    private OISignalResult calculateAggregateOISignal(BarSeries series, String timeframe) {
        if (series == null || series.getBarCount() < 2) {
            return OISignalResult.builder().bullish(false).bearish(false).build();
        }

        try {
            Bar currentBar = series.getLastBar();
            boolean isBullishCandle = currentBar.getClosePrice().isGreaterThan(currentBar.getOpenPrice());
            boolean isBearishCandle = currentBar.getClosePrice().isLessThan(currentBar.getOpenPrice());

            // Calculate aggregate OI change across all strikes
            long timeWindow = getTimeWindowForTimeframe(timeframe);
            long currentTime = System.currentTimeMillis();
            long windowStart = currentTime - timeWindow;

            double totalOIChange = 0.0;
            int strikeCount = 0;

            for (Map.Entry<String, List<OIDataPoint>> entry : oiDataHistory.entrySet()) {
                List<OIDataPoint> history = entry.getValue();
                if (history.size() < 2) continue;

                // Get OI values within the time window
                OIDataPoint latest = history.get(history.size() - 1);
                OIDataPoint previous = findPreviousOIDataPoint(history, windowStart);

                if (previous != null && latest.getFetchTime() >= windowStart) {
                    double oiChange = latest.getOi() - previous.getOi();
                    totalOIChange += oiChange;
                    strikeCount++;
                }
            }

            if (strikeCount == 0) {
                return OISignalResult.builder().bullish(false).bearish(false).build();
            }

            double avgOIChange = totalOIChange / strikeCount;
            boolean oiIncreasing = avgOIChange > 0;

            // Determine signal based on OI change and price movement
            boolean bullish = false;
            boolean bearish = false;

            if (oiIncreasing && isBullishCandle) {
                bullish = true; // LONG_BUILDUP
            } else if (oiIncreasing && isBearishCandle) {
                bearish = true; // SHORT_BUILDUP
            } else if (!oiIncreasing && isBullishCandle) {
                bullish = true; // SHORT_COVERING
            } else if (!oiIncreasing && isBearishCandle) {
                bearish = true; // LONG_UNWINDING
            }

            return OISignalResult.builder()
                    .bullish(bullish)
                    .bearish(bearish)
                    .avgOIChange(avgOIChange)
                    .strikeCount(strikeCount)
                    .build();

        } catch (Exception e) {
            log.error("Error calculating aggregate OI signal for {}", timeframe, e);
            return OISignalResult.builder().bullish(false).bearish(false).build();
        }
    }

    /**
     * Find previous OI data point before the given timestamp
     */
    private OIDataPoint findPreviousOIDataPoint(List<OIDataPoint> history, long beforeTime) {
        for (int i = history.size() - 1; i >= 0; i--) {
            OIDataPoint point = history.get(i);
            if (point.getFetchTime() < beforeTime) {
                return point;
            }
        }
        return !history.isEmpty() ? history.get(0) : null;
    }

    /**
     * Get time window in milliseconds for timeframe
     */
    private long getTimeWindowForTimeframe(String timeframe) {
        return switch (timeframe) {
            case "1min" -> 60_000L; // 1 minute
            case "5min" -> 300_000L; // 5 minutes
            case "15min" -> 900_000L; // 15 minutes
            default -> 60_000L;
        };
    }

    /**
     * Calculate CALL buy score based on OI signals using MongoDB configuration
     */
    private double calculateCallBuyScore(OISignalResult signal1min, OISignalResult signal5min, OISignalResult signal15min) {
        double score = 0.0;
        
        // Get scoring configuration from MongoDB
        Map<String, Double> callScores = getOIScoringFromConfig("callCategories", "futureAndVolume");
        
        // Get scores from config, with fallback defaults
        double score5min = callScores.getOrDefault("oi_bullish_signal_5min", 2.0);
        double score1min = callScores.getOrDefault("oi_bullish_signal_1min", 0.5);
        double score15min = callScores.getOrDefault("oi_bullish_signal_15min", 0.5);
        
        // Apply positive scores for bullish signals
        if (signal5min.isBullish()) score += score5min;
        if (signal1min.isBullish()) score += score1min;
        if (signal15min.isBullish()) score += score15min;
        
        // Apply negative scores for bearish signals (half of bullish scores)
        if (signal5min.isBearish()) score -= (score5min / 2.0);
        if (signal1min.isBearish()) score -= (score1min / 2.0);
        if (signal15min.isBearish()) score -= (score15min / 2.0);
        
        return score;
    }

    /**
     * Calculate PUT buy score based on OI signals using MongoDB configuration
     */
    private double calculatePutBuyScore(OISignalResult signal1min, OISignalResult signal5min, OISignalResult signal15min) {
        double score = 0.0;
        
        // Get scoring configuration from MongoDB - try putCategories first, fallback to callCategories
        Map<String, Double> putScores = getOIScoringFromConfig("putCategories", "futureAndVolume");
        
        // If putCategories not found, use callCategories as fallback (same scoring values)
        if (putScores.isEmpty() || !putScores.containsKey("oi_bearish_signal_5min")) {
            putScores = getOIScoringFromConfig("callCategories", "futureAndVolume");
        }
        
        // Get scores from config, with fallback defaults
        // For PUT, we use bearish signal scores (oi_bearish_signal_*)
        // If bearish signals not in config, use bullish signal scores as fallback
        double score5min = putScores.getOrDefault("oi_bearish_signal_5min", 
                putScores.getOrDefault("oi_bullish_signal_5min", 2.0));
        double score1min = putScores.getOrDefault("oi_bearish_signal_1min", 
                putScores.getOrDefault("oi_bullish_signal_1min", 0.5));
        double score15min = putScores.getOrDefault("oi_bearish_signal_15min", 
                putScores.getOrDefault("oi_bullish_signal_15min", 0.5));
        
        // Apply positive scores for bearish signals
        if (signal5min.isBearish()) score += score5min;
        if (signal1min.isBearish()) score += score1min;
        if (signal15min.isBearish()) score += score15min;
        
        // Apply negative scores for bullish signals (half of bearish scores)
        if (signal5min.isBullish()) score -= (score5min / 2.0);
        if (signal1min.isBullish()) score -= (score1min / 2.0);
        if (signal15min.isBullish()) score -= (score15min / 2.0);
        
        return score;
    }

    /**
     * Get OI scoring configuration from MongoDB
     */
    private Map<String, Double> getOIScoringFromConfig(String categoryType, String categoryName) {
        try {
            Optional<com.jtradebot.processor.repository.document.CategoryScoring> categoryScoring = 
                    configCategoryScoringService.getCategoryScoring(categoryType, categoryName);
            
            if (categoryScoring.isPresent() && categoryScoring.get().getIndicators() != null) {
                Map<String, Double> indicators = categoryScoring.get().getIndicators();
                log.debug("📊 Loaded OI scoring from config - Type: {}, Name: {}, Scores: {}", 
                        categoryType, categoryName, indicators);
                return new HashMap<>(indicators); // Return a copy to avoid modification
            } else {
                log.debug("⚠️ OI scoring config not found - Type: {}, Name: {}", 
                        categoryType, categoryName);
            }
        } catch (Exception e) {
            log.warn("⚠️ Error loading OI scoring config - Type: {}, Name: {}, error: {}", 
                    categoryType, categoryName, e.getMessage());
        }
        
        // Return empty map if config not found (caller will use defaults)
        return new HashMap<>();
    }

    /**
     * Get OI signals for FlattenedIndicators
     */
    public OISignalsCache getOISignals() {
        cacheLock.readLock().lock();
        try {
            return oiSignalsCache;
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Get valid options in ±200 range
     */
    private List<com.jtradebot.processor.repository.document.Instrument> getValidOptionsInRange(double niftyIndexPrice) {
        try {
            // Round to nearest 50
            final int minStrike = ((int) (niftyIndexPrice - 200) / 50) * 50;
            final int maxStrike = (((int) (niftyIndexPrice + 200) / 50) + 1) * 50;

            List<com.jtradebot.processor.repository.document.Instrument> allInstruments = getCachedInstruments();
            
            LocalDate currentDate = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

            return allInstruments.stream()
                    .filter(instrument -> {
                        try {
                            int strike = Integer.parseInt(instrument.getStrike());
                            if (strike < minStrike || strike > maxStrike) {
                                return false;
                            }
                            
                            // Check if expiry is valid
                            LocalDate expiryDate = LocalDate.parse(instrument.getExpiry(), formatter);
                            return !expiryDate.isBefore(currentDate);
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error getting valid options in range", e);
            return new ArrayList<>();
        }
    }

    /**
     * Get cached instruments (refreshes daily)
     */
    private synchronized List<com.jtradebot.processor.repository.document.Instrument> getCachedInstruments() {
        try {
            String currentDate = LocalDate.now().toString();
            
            if (!instrumentsCacheInitialized || !currentDate.equals(lastCacheDate)) {
                log.info("🔄 REFRESHING INSTRUMENTS CACHE - Date: {} (was: {})", currentDate, lastCacheDate);
                
                List<com.jtradebot.processor.repository.document.Instrument> allInstruments = instrumentRepository.findAll();
                
                List<com.jtradebot.processor.repository.document.Instrument> niftyOptions = allInstruments.stream()
                        .filter(instrument -> "NIFTY".equals(instrument.getName()) &&
                                ("CE".equals(instrument.getInstrumentType()) || "PE".equals(instrument.getInstrumentType())) &&
                                "NFO-OPT".equals(instrument.getSegment()))
                        .filter(instrument -> {
                            try {
                                LocalDate expiryDate = LocalDate.parse(instrument.getExpiry(), 
                                        DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                                return !expiryDate.isBefore(LocalDate.now());
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .collect(Collectors.toList());
                
                cachedInstruments = niftyOptions;
                instrumentsCacheInitialized = true;
                lastCacheDate = currentDate;
                
                log.info("✅ INSTRUMENTS CACHE UPDATED - Total NIFTY options: {}", niftyOptions.size());
            }
            
            return cachedInstruments;
            
        } catch (Exception e) {
            log.error("Error getting cached instruments", e);
            return new ArrayList<>();
        }
    }

    /**
     * Get current Nifty index price
     */
    private double getCurrentNiftyIndexPrice() {
        try {
            Tick niftyTick = tickDataManager.getLastTick(String.valueOf(kiteInstrumentHandler.getNifty50Token()));
            if (niftyTick != null) {
                return niftyTick.getLastTradedPrice();
            }
            return 0.0;
        } catch (Exception e) {
            log.error("Error getting current Nifty index price", e);
            return 0.0;
        }
    }

    /**
     * Get Nifty Future tick
     */
    private Tick getNiftyFutureTick() {
        try {
            Long niftyFutureToken = kiteInstrumentHandler.getNifty50FutureToken();
            if (niftyFutureToken != null) {
                return tickDataManager.getLastTick(String.valueOf(niftyFutureToken));
            }
            return null;
        } catch (Exception e) {
            log.error("Error getting Nifty Future tick", e);
            return null;
        }
    }

    // Data classes
    @Data
    @Builder
    public static class OIDataPoint {
        private double oi;
        private double price;
        private String timestamp;
        private long fetchTime;
    }

    @Data
    @Builder
    public static class OISignalResult {
        private boolean bullish;
        private boolean bearish;
        private double avgOIChange;
        private int strikeCount;
    }

    @Data
    @Builder
    public static class OISignalsCache {
        private Boolean oiBullishSignal1min;
        private Boolean oiBearishSignal1min;
        private Boolean oiBullishSignal5min;
        private Boolean oiBearishSignal5min;
        private Boolean oiBullishSignal15min;
        private Boolean oiBearishSignal15min;
        private double callBuyScore;
        private double putBuyScore;
        private long lastUpdated;
    }
}

