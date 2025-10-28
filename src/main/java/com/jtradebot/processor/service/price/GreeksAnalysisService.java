package com.jtradebot.processor.service.price;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.repository.InstrumentRepository;
import com.jtradebot.processor.repository.document.Instrument;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.LTPQuote;
import com.zerodhatech.models.Tick;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service for analyzing Greeks parameters for multiple strike prices
 * Works in both local and live profiles, reuses existing calculation logic
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GreeksAnalysisService {

    private final InstrumentRepository instrumentRepository;
    private final OptionGreeksCalculator greeksCalculator;
    private final KiteConnect kiteConnect;
    
    @Autowired
    private TickDataManager tickDataManager;
    
    @Autowired
    private com.jtradebot.processor.handler.KiteInstrumentHandler kiteInstrumentHandler;
    
    // Cache for best strike prices
    private volatile BestStrikeCache bestStrikeCache = BestStrikeCache.builder().build();
    private volatile boolean isCacheInitialized = false;
    private volatile double lastCachedIndexPrice = 0.0;
    private volatile long lastCacheRefreshTime = 0L;
    private static final double PRICE_CHANGE_THRESHOLD = 10.0; // Recalculate if index moves ≥10 points
    private static final long TIME_REFRESH_INTERVAL = 10000L; // Refresh every 10 seconds (10,000ms)
    
    // Cache for instruments (doesn't change during the day)
    private volatile List<Instrument> cachedInstruments = new ArrayList<>();
    private volatile boolean instrumentsCacheInitialized = false;
    private volatile String lastCacheDate = "";

    /**
     * Get Greeks analysis for ±200 strike prices around current Nifty index
     * Works in both local and live profiles
     */
    public GreeksAnalysisResult getGreeksAnalysis(double niftyIndexPrice, String optionType) {
        try {
            log.info("🔍 GREEKS ANALYSIS - Index: {}, Type: {}, Range: ±200", niftyIndexPrice, optionType);

            // Define strike range: ±200 from current index price
            int minStrike = (int) Math.floor((niftyIndexPrice - 200) / 100) * 100;
            int maxStrike = (int) Math.ceil((niftyIndexPrice + 200) / 100) * 100;

            // Get all valid options in the range
            List<Instrument> validOptions = getValidOptionsInRange(optionType, minStrike, maxStrike);
            if (validOptions.isEmpty()) {
                log.warn("No valid options found in range {}-{} for type: {}", minStrike, maxStrike, optionType);
                return GreeksAnalysisResult.builder()
                        .niftyIndexPrice(niftyIndexPrice)
                        .optionType(optionType)
                        .strikeRange(minStrike + "-" + maxStrike)
                        .strikes(new ArrayList<>())
                        .build();
            }

            log.debug("Found {} valid options in range {}-{}", validOptions.size(), minStrike, maxStrike);

            // Calculate Greeks for each strike using Kite API for LTP
            List<StrikeGreeksData> strikesData = new ArrayList<>();
            for (Instrument instrument : validOptions) {
                try {
                    StrikeGreeksData strikeData = calculateStrikeGreeks(instrument, niftyIndexPrice, optionType);
                    if (strikeData != null) {
                        strikesData.add(strikeData);
                    }
                } catch (Exception e) {
                    log.warn("Error calculating Greeks for instrument: {}", instrument.getTradingSymbol(), e);
                }
            }

            // Sort by strike price
            strikesData.sort(Comparator.comparing(StrikeGreeksData::getStrikePrice));

            log.info("✅ GREEKS ANALYSIS COMPLETED - Found {} strikes with Greeks data", strikesData.size());

            return GreeksAnalysisResult.builder()
                    .niftyIndexPrice(niftyIndexPrice)
                    .optionType(optionType)
                    .strikeRange(minStrike + "-" + maxStrike)
                    .strikes(strikesData)
                    .totalStrikes(strikesData.size())
                    .timestamp(System.currentTimeMillis())
                    .build();

        } catch (Exception e) {
            log.error("Error in Greeks analysis", e);
            return GreeksAnalysisResult.builder()
                    .niftyIndexPrice(niftyIndexPrice)
                    .optionType(optionType)
                    .strikeRange("error")
                    .strikes(new ArrayList<>())
                    .error(e.getMessage())
                    .build();
        }
    }

    /**
     * Calculate Greeks for a single strike - reuses existing calculation logic
     * Uses Kite API to get LTP directly
     */
    private StrikeGreeksData calculateStrikeGreeks(Instrument instrument, double niftyIndexPrice, String optionType) {
        try {
            int strikePrice = Integer.parseInt(instrument.getStrike());
            
            // Get LTP directly from Kite API
            double optionPrice = getLTPFromKite(instrument.getInstrumentToken());
            if (optionPrice <= 0) {
                log.debug("Invalid option price for instrument: {} (LTP: {})", instrument.getTradingSymbol(), optionPrice);
                return null;
            }

            // Reuse existing calculation logic from OptionGreeksCalculator
            double timeToExpiry = greeksCalculator.calculateTimeToExpiry(instrument.getExpiry());
            if (timeToExpiry <= 0) {
                log.debug("Invalid time to expiry for instrument: {}", instrument.getTradingSymbol());
                return null;
            }

            // Reuse existing implied volatility calculation
            double impliedVolatility = greeksCalculator.estimateImpliedVolatility(
                    niftyIndexPrice, strikePrice, optionPrice, timeToExpiry, optionType);

            // Reuse existing Greeks calculation
            OptionGreeksCalculator.OptionGreeks greeks = greeksCalculator.calculateGreeks(
                    niftyIndexPrice, strikePrice, timeToExpiry, impliedVolatility, optionType, optionPrice);

            // Create strike data
            StrikeGreeksData strikeData = StrikeGreeksData.builder()
                    .tradingSymbol(instrument.getTradingSymbol())
                    .strikePrice(strikePrice)
                    .expiry(instrument.getExpiry())
                    .instrumentToken(instrument.getInstrumentToken())
                    .optionPrice(optionPrice)
                    .lastTradedPrice(optionPrice)
                    .greeks(greeks)
                    .timeToExpiry(timeToExpiry)
                    .impliedVolatility(impliedVolatility)
                    .build();

            log.debug("📊 GREEKS CALCULATED - Strike: {}, Delta: {:.3f}, Gamma: {:.4f}, Theta: {:.3f}, Vega: {:.3f}, IV: {:.2f}%",
                    strikePrice, greeks.getDelta(), greeks.getGamma(), greeks.getTheta(), 
                    greeks.getVega(), impliedVolatility * 100);

            return strikeData;

        } catch (Exception e) {
            log.warn("Error calculating Greeks for instrument: {}", instrument.getTradingSymbol(), e);
            return null;
        }
    }

    /**
     * Get valid options within the specified strike range
     */
    private List<Instrument> getValidOptionsInRange(String optionType, int minStrike, int maxStrike) {
        try {
            LocalDate currentDate = LocalDate.now();

            // Get cached instruments (refreshes daily)
            List<Instrument> allInstruments = getCachedInstruments();
            
            return allInstruments.stream()
                    .filter(instrument -> {
                        try {
                            // Filter by specific option type (CE or PE)
                            if (!optionType.equals(instrument.getInstrumentType())) {
                                return false;
                            }

                            // Filter by strike price range
                            int strike = Integer.parseInt(instrument.getStrike());
                            if (strike < minStrike || strike > maxStrike) {
                                return false;
                            }

                            // Filter by expiry (only current and next expiry)
                            String expiry = instrument.getExpiry();
                            if (expiry == null || expiry.isEmpty()) {
                                return false;
                            }

                            // Accept next expiry only (exclude expiry day for better scalping)
                            LocalDate expiryDate = LocalDate.parse(expiry, DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                            long daysToExpiry = java.time.temporal.ChronoUnit.DAYS.between(currentDate, expiryDate);
                            
                            // Exclude expiry day (daysToExpiry = 0) and focus on next expiry
                            // Accept options with 1-7 days to expiry for better scalping conditions
                            boolean isValidExpiry = daysToExpiry >= 1 && daysToExpiry <= 7;
                            
                            if (daysToExpiry == 0) {
                                log.info("🚫 EXCLUDING EXPIRY DAY OPTION - Symbol: {}, Expiry: {}, Days: {}", 
                                        instrument.getTradingSymbol(), expiry, daysToExpiry);
                            }
                            
                            return isValidExpiry;

                        } catch (Exception e) {
                            log.debug("Error filtering instrument: {}", instrument.getTradingSymbol(), e);
                            return false;
                        }
                    })
                    .collect(java.util.stream.Collectors.toList());

        } catch (Exception e) {
            log.error("Error getting valid options in range", e);
            return new ArrayList<>();
        }
    }

    /**
     * Scheduled task to refresh best strike cache every 10 seconds
     * This ensures fresh data even if price doesn't move much
     */
    @Scheduled(fixedRate = 5000) // Every 5 seconds
    public void scheduledRefreshBestStrikeCache() {
        try {
            double currentIndexPrice = getCurrentNiftyIndexPrice();
            if (currentIndexPrice > 0) {
                long currentTime = System.currentTimeMillis();
                long timeSinceLastRefresh = currentTime - lastCacheRefreshTime;
                
                // Check if we need to refresh based on time (10 seconds) or price (10 points)
                boolean needsTimeRefresh = timeSinceLastRefresh >= TIME_REFRESH_INTERVAL;
                boolean needsPriceRefresh = !isCacheInitialized || 
                                         Math.abs(currentIndexPrice - lastCachedIndexPrice) >= PRICE_CHANGE_THRESHOLD;
                
                if (needsTimeRefresh || needsPriceRefresh) {
                    String reason = needsTimeRefresh ? "TIME" : "PRICE";
                    log.info("🔄 SCHEDULED CACHE REFRESH - Reason: {}, Index: {} (was: {}), Time: {}ms", 
                            reason, currentIndexPrice, lastCachedIndexPrice, timeSinceLastRefresh);
                    
                    refreshBestStrikeCache(currentIndexPrice);
                }
            }
        } catch (Exception e) {
            log.error("Error in scheduled cache refresh", e);
        }
    }

    /**
     * Get best strike for scalping (cached version - fast for order creation)
     * Uses both price-based and time-based refresh logic
     */
    public BestStrikeResult getBestStrikeForScalping(String optionType) {
        try {
            log.info("🔍 GETTING BEST STRIKE - Option Type: {}", optionType);
            
            // Get current Nifty index price
            double currentIndexPrice = getCurrentNiftyIndexPrice();
            log.info("📊 CURRENT NIFTY PRICE - {}", currentIndexPrice);
            
            if (currentIndexPrice <= 0) {
                log.error("❌ NO NIFTY INDEX DATA - Price: {}", currentIndexPrice);
                return BestStrikeResult.error("No Nifty index data available");
            }

            // Check if cache needs refresh (both price and time based)
            long currentTime = System.currentTimeMillis();
            long timeSinceLastRefresh = currentTime - lastCacheRefreshTime;
            
            boolean needsTimeRefresh = timeSinceLastRefresh >= TIME_REFRESH_INTERVAL;
            boolean needsPriceRefresh = !isCacheInitialized || 
                                     Math.abs(currentIndexPrice - lastCachedIndexPrice) >= PRICE_CHANGE_THRESHOLD;

            if (needsTimeRefresh || needsPriceRefresh) {
                String reason = needsTimeRefresh ? "TIME" : "PRICE";
                log.info("🔄 REFRESHING BEST STRIKE CACHE - Reason: {}, Index: {} (was: {}), Change: {:.2f} points, Time: {}ms", 
                        reason, currentIndexPrice, lastCachedIndexPrice, 
                        Math.abs(currentIndexPrice - lastCachedIndexPrice), timeSinceLastRefresh);
                
                refreshBestStrikeCache(currentIndexPrice);
            }

            // Return cached result
            StrikeGreeksData bestStrike = "CE".equals(optionType) ? 
                    bestStrikeCache.getBestCallStrike() : bestStrikeCache.getBestPutStrike();

            if (bestStrike == null) {
                return BestStrikeResult.error("No suitable strike found for " + optionType);
            }

            return BestStrikeResult.success(bestStrike, currentIndexPrice, optionType);

        } catch (Exception e) {
            log.error("Error getting best strike for scalping", e);
            return BestStrikeResult.error("Error occurred: " + e.getMessage());
        }
    }

    /**
     * Get cached instruments (refreshes daily)
     */
    private synchronized List<Instrument> getCachedInstruments() {
        try {
            String currentDate = LocalDate.now().toString();
            
            // Check if cache needs refresh (new day)
            if (!instrumentsCacheInitialized || !currentDate.equals(lastCacheDate)) {
                log.info("🔄 REFRESHING INSTRUMENTS CACHE - Date: {} (was: {})", currentDate, lastCacheDate);
                
                // Fetch all instruments from database
                List<Instrument> allInstruments = instrumentRepository.findAll();
                
                // Filter for NIFTY options only
                List<Instrument> niftyOptions = allInstruments.stream()
                        .filter(instrument -> "NIFTY".equals(instrument.getName()) &&
                                ("CE".equals(instrument.getInstrumentType()) || "PE".equals(instrument.getInstrumentType())) &&
                                "NFO-OPT".equals(instrument.getSegment()))
                        .filter(instrument -> {
                            // Check if expiry is valid (not expired)
                            try {
                                LocalDate expiryDate = LocalDate.parse(instrument.getExpiry(), 
                                        DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                                return !expiryDate.isBefore(LocalDate.now());
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .collect(java.util.stream.Collectors.toList());
                
                // Update cache
                cachedInstruments = niftyOptions;
                instrumentsCacheInitialized = true;
                lastCacheDate = currentDate;
                
                log.info("✅ INSTRUMENTS CACHE UPDATED - Total NIFTY options: {} (CE: {}, PE: {})", 
                        niftyOptions.size(),
                        niftyOptions.stream().filter(i -> "CE".equals(i.getInstrumentType())).count(),
                        niftyOptions.stream().filter(i -> "PE".equals(i.getInstrumentType())).count());
            }
            
            return cachedInstruments;
            
        } catch (Exception e) {
            log.error("Error getting cached instruments", e);
            return new ArrayList<>();
        }
    }

    /**
     * Get LTP directly from Kite API
     */
    private double getLTPFromKite(Long instrumentToken) {
        try {
            String token = String.valueOf(instrumentToken);
            Map<String, LTPQuote> ltpData = kiteConnect.getLTP(new String[]{token});
            LTPQuote ltpQuote = ltpData.get(token);
            if (ltpQuote != null) {
                return ltpQuote.lastPrice;
            }
            return 0.0;
        } catch (KiteException e) {
            log.debug("Could not get LTP from Kite for instrument: {}", instrumentToken, e);
            return 0.0;
        } catch (Exception e) {
            log.debug("Unexpected error getting LTP for instrument: {}", instrumentToken, e);
            return 0.0;
        }
    }

    /**
     * Refresh the best strike cache with current index price
     */
    private synchronized void refreshBestStrikeCache(double currentIndexPrice) {
        try {
            // Get Greeks analysis for both CE and PE
            GreeksAnalysisResult callAnalysis = getGreeksAnalysis(currentIndexPrice, "CE");
            GreeksAnalysisResult putAnalysis = getGreeksAnalysis(currentIndexPrice, "PE");

            // Find best strikes using the same scoring logic as controller
            StrikeGreeksData bestCallStrike = findBestStrikeForScalping(callAnalysis.getStrikes(), currentIndexPrice, "CE");
            StrikeGreeksData bestPutStrike = findBestStrikeForScalping(putAnalysis.getStrikes(), currentIndexPrice, "PE");

            // Update cache
            bestStrikeCache = BestStrikeCache.builder()
                    .bestCallStrike(bestCallStrike)
                    .bestPutStrike(bestPutStrike)
                    .indexPrice(currentIndexPrice)
                    .lastUpdated(System.currentTimeMillis())
                    .build();

            lastCachedIndexPrice = currentIndexPrice;
            lastCacheRefreshTime = System.currentTimeMillis();
            isCacheInitialized = true;

            log.info("✅ BEST STRIKE CACHE UPDATED - Index: {}, Call: {} ({}), Put: {} ({})", 
                    currentIndexPrice,
                    bestCallStrike != null ? bestCallStrike.getStrikePrice() : "N/A",
                    bestCallStrike != null ? bestCallStrike.getTradingSymbol() : "N/A",
                    bestPutStrike != null ? bestPutStrike.getStrikePrice() : "N/A",
                    bestPutStrike != null ? bestPutStrike.getTradingSymbol() : "N/A");

        } catch (Exception e) {
            log.error("Error refreshing best strike cache", e);
        }
    }

    /**
     * Get current Nifty index price
     */
    private double getCurrentNiftyIndexPrice() {
        try {
            // Get Nifty index price from tick data manager
            Tick niftyTick = tickDataManager.getLastTick(String.valueOf(kiteInstrumentHandler.getNifty50Token()));
            if (niftyTick != null) {
                double price = niftyTick.getLastTradedPrice();
                log.debug("📊 NIFTY INDEX PRICE - {}", price);
                return price;
            } else {
                log.warn("No Nifty index tick data available");
                return 0.0;
            }
        } catch (Exception e) {
            log.error("Error getting current Nifty index price", e);
            return 0.0;
        }
    }

    /**
     * Find best strike using the same scoring logic as controller
     */
    private StrikeGreeksData findBestStrikeForScalping(List<StrikeGreeksData> strikes, double niftyIndexPrice, String optionType) {
        if (strikes.isEmpty()) {
            return null;
        }

        StrikeGreeksData bestStrike = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (StrikeGreeksData strike : strikes) {
            double score = calculateScalpingScore(strike, niftyIndexPrice, optionType);
            
            if (score > bestScore) {
                bestScore = score;
                bestStrike = strike;
            }
        }

        return bestStrike;
    }

    /**
     * Calculate scalping score (same logic as controller)
     */
    private double calculateScalpingScore(StrikeGreeksData strike, double niftyIndexPrice, String optionType) {
        try {
            double delta = strike.getGreeks().getDelta();
            double optionPrice = strike.getOptionPrice();
            double impliedVolatility = strike.getImpliedVolatility();
            double theta = strike.getGreeks().getTheta();
            int strikePrice = strike.getStrikePrice();
            
            // Calculate moneyness (how close to ATM)
            double moneyness = Math.abs(niftyIndexPrice - strikePrice) / niftyIndexPrice;
            
            // Score components (0-100 each)
            double deltaScore = calculateDeltaScore(Math.abs(delta), optionType);
            double priceScore = calculatePriceScore(optionPrice);
            double moneynessScore = calculateMoneynessScore(moneyness);
            double ivScore = calculateIVScore(impliedVolatility);
            double thetaScore = calculateThetaScore(theta);
            
            // Weighted total score
            double totalScore = (deltaScore * 0.30) +      // Delta is most important for scalping
                               (priceScore * 0.25) +      // Reasonable price
                               (moneynessScore * 0.20) +  // Close to ATM
                               (ivScore * 0.15) +         // Not overpriced
                               (thetaScore * 0.10);       // Manageable time decay
            
            return totalScore;
            
        } catch (Exception e) {
            log.warn("Error calculating scalping score for strike: {}", strike.getStrikePrice(), e);
            return 0.0;
        }
    }

    // Scoring methods (same as controller)
    private double calculateDeltaScore(double absDelta, String optionType) {
        if (absDelta < 0.3 || absDelta > 0.7) return 0;
        if (absDelta >= 0.45 && absDelta <= 0.65) return 100;
        if (absDelta >= 0.4 && absDelta <= 0.7) return 80;
        return 50;
    }

    private double calculatePriceScore(double optionPrice) {
        if (optionPrice < 50) return 0;
        if (optionPrice > 500) return 0;
        if (optionPrice >= 150 && optionPrice <= 300) return 100;
        if (optionPrice >= 100 && optionPrice <= 400) return 80;
        return 60;
    }

    private double calculateMoneynessScore(double moneyness) {
        if (moneyness <= 0.005) return 100;
        if (moneyness <= 0.01) return 90;
        if (moneyness <= 0.02) return 70;
        if (moneyness <= 0.03) return 50;
        return 20;
    }

    private double calculateIVScore(double impliedVolatility) {
        double ivPercent = impliedVolatility * 100;
        if (ivPercent < 10) return 60;
        if (ivPercent >= 12 && ivPercent <= 18) return 100;
        if (ivPercent >= 10 && ivPercent <= 20) return 80;
        if (ivPercent > 25) return 40;
        return 60;
    }

    private double calculateThetaScore(double theta) {
        double absTheta = Math.abs(theta);
        if (absTheta < 3000) return 100;
        if (absTheta < 5000) return 80;
        if (absTheta < 7000) return 60;
        return 40;
    }


    // Data classes
    @Data
    @lombok.Builder
    public static class GreeksAnalysisResult {
        private double niftyIndexPrice;
        private String optionType;
        private String strikeRange;
        private List<StrikeGreeksData> strikes;
        private int totalStrikes;
        private long timestamp;
        private String error;
    }

    @Data
    @lombok.Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StrikeGreeksData {
        private String tradingSymbol;
        private int strikePrice;
        private String expiry;
        private Long instrumentToken;
        private double optionPrice;
        private Long volume; // Optional - will be null if not available
        private Double oi; // Optional - will be null if not available
        private Double bid; // Optional - will be null if not available
        private Double ask; // Optional - will be null if not available
        private double lastTradedPrice;
        private OptionGreeksCalculator.OptionGreeks greeks;
        private double timeToExpiry;
        private double impliedVolatility;
    }

    @Data
    @lombok.Builder
    public static class BestStrikeCache {
        private StrikeGreeksData bestCallStrike;
        private StrikeGreeksData bestPutStrike;
        private double indexPrice;
        private long lastUpdated;
    }

    @Data
    @lombok.Builder
    public static class BestStrikeResult {
        private boolean success;
        private StrikeGreeksData bestStrike;
        private double niftyIndexPrice;
        private String optionType;
        private String error;
        private long timestamp;

        public static BestStrikeResult success(StrikeGreeksData bestStrike, double niftyIndexPrice, String optionType) {
            return BestStrikeResult.builder()
                    .success(true)
                    .bestStrike(bestStrike)
                    .niftyIndexPrice(niftyIndexPrice)
                    .optionType(optionType)
                    .timestamp(System.currentTimeMillis())
                    .build();
        }

        public static BestStrikeResult error(String error) {
            return BestStrikeResult.builder()
                    .success(false)
                    .error(error)
                    .timestamp(System.currentTimeMillis())
                    .build();
        }
    }

}
