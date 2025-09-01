package com.jtradebot.processor.handler;

import com.jtradebot.processor.repository.InstrumentRepository;
import com.jtradebot.processor.repository.document.Instrument;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static com.jtradebot.processor.handler.DateTimeHandler.formatDate;

import org.springframework.cache.annotation.Cacheable;


@Service
@RequiredArgsConstructor
@Slf4j
public class KiteInstrumentHandler {
    private final KiteConnect kiteConnect;
    private final InstrumentRepository instrumentRepository;

    private volatile Long cachedFutureToken;
    private volatile LocalDateTime lastCacheTime;
    private static final Duration CACHE_DURATION = Duration.ofHours(1);

    public Long getNifty50Token() {
        return 256265L;
    }

    public Long getNifty50FutureToken() {
        Optional<Long> token = getDynamicNifty50FutureToken();
        if (token.isPresent()) {
            return token.get();
        } else {
            throw new RuntimeException("No valid Nifty 50 future token found in database");
        }
    }

    /*public Long getNifty50FutureToken() {
        return 16410370L;
    }*/


    public Optional<Long> getDynamicNifty50FutureToken() {
        // Manual caching implementation
        if (cachedFutureToken != null && lastCacheTime != null) {
            Duration timeSinceLastCache = Duration.between(lastCacheTime, LocalDateTime.now());
            if (timeSinceLastCache.compareTo(CACHE_DURATION) < 0) {
                log.debug("Using cached Nifty future token: {} (cached {} ago)", 
                        cachedFutureToken, timeSinceLastCache);
                return Optional.of(cachedFutureToken);
            }
        }

        try {
            LocalDate now = LocalDate.now();

            log.info("Fetching Nifty futures from database to determine the appropriate future contract...");
            // Find Nifty future instruments using efficient database query
            List<Instrument> niftyFutures = instrumentRepository.findByNameAndInstrumentTypeAndSegmentOrderByExpiryAsc("NIFTY", "FUT", "NFO-FUT");

            if (niftyFutures.isEmpty()) {
                log.warn("No Nifty futures found in database");
                return Optional.empty();
            }

            // Find the appropriate future based on expiry date
            // First, try to find current month's future
            String currentMonth = now.format(DateTimeFormatter.ofPattern("MMM")).toUpperCase();
            String currentYear = String.valueOf(now.getYear()).substring(2);

            // First, try to find current month's future
            Optional<Instrument> currentMonthFuture = niftyFutures.stream()
                    .filter(instrument -> {
                        String tradingSymbol = instrument.getTradingSymbol();
                        return tradingSymbol != null &&
                                tradingSymbol.contains(currentMonth) &&
                                tradingSymbol.contains(currentYear);
                    })
                    .findFirst();

            if (currentMonthFuture.isPresent()) {
                Instrument currentMonthInstrument = currentMonthFuture.get();
                try {
                    LocalDate expiryDate = LocalDate.parse(currentMonthInstrument.getExpiry(), DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                    long daysUntilExpiry = java.time.temporal.ChronoUnit.DAYS.between(now, expiryDate);

                    if (daysUntilExpiry > 7) {
                        // Update cache for current month future
                        cachedFutureToken = currentMonthInstrument.getInstrumentToken();
                        lastCacheTime = LocalDateTime.now();
                        log.info("Cached current month Nifty future token: {} (expires in {} days)", 
                                cachedFutureToken, daysUntilExpiry);
                        return Optional.of(currentMonthInstrument.getInstrumentToken());
                    }
                } catch (Exception e) {
                    log.warn("Error parsing expiry for current month future: {}", e.getMessage());
                }
            }

            // If current month is not suitable, find the next available future
            // Sort by expiry date first, then filter
            Optional<Instrument> selectedFuture = niftyFutures.stream()
                    .sorted((i1, i2) -> {
                        try {
                            LocalDate expiry1 = LocalDate.parse(i1.getExpiry(), DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                            LocalDate expiry2 = LocalDate.parse(i2.getExpiry(), DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                            return expiry1.compareTo(expiry2);
                        } catch (Exception e) {
                            log.warn("Error sorting by expiry date: {}", e.getMessage());
                            return 0;
                        }
                    })
                    .filter(instrument -> {
                        try {
                            LocalDate expiryDate = LocalDate.parse(instrument.getExpiry(), DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                            long daysUntilExpiry = java.time.temporal.ChronoUnit.DAYS.between(now, expiryDate);

                            // If expiry is more than 7 days away, use this future


                            return daysUntilExpiry > 7;
                        } catch (Exception e) {
                            log.warn("Error parsing expiry date for instrument {}: {}",
                                    instrument.getTradingSymbol(), e.getMessage());
                            return false;
                        }
                    })
                    .findFirst();

            if (selectedFuture.isPresent()) {
                Instrument future = selectedFuture.get();
                try {
                    LocalDate expiryDate = LocalDate.parse(future.getExpiry(), DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                    long daysUntilExpiry = java.time.temporal.ChronoUnit.DAYS.between(now, expiryDate);

                    // Update cache
                    cachedFutureToken = future.getInstrumentToken();
                    lastCacheTime = LocalDateTime.now();
                    log.info("Cached selected Nifty future token: {} (expires in {} days)", 
                            cachedFutureToken, daysUntilExpiry);

                    return Optional.of(future.getInstrumentToken());
                } catch (Exception e) {
                    log.error("Error calculating days until expiry for selected future: {}", e.getMessage());
                    // Still update cache even if there's an error parsing expiry
                    cachedFutureToken = future.getInstrumentToken();
                    lastCacheTime = LocalDateTime.now();
                    return Optional.of(future.getInstrumentToken());
                }
            } else {
                // If no future is more than 7 days away, use the first available future
                Instrument firstFuture = niftyFutures.get(0);
                try {
                    LocalDate expiryDate = LocalDate.parse(firstFuture.getExpiry(), DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                    long daysUntilExpiry = java.time.temporal.ChronoUnit.DAYS.between(now, expiryDate);

                    // Update cache
                    cachedFutureToken = firstFuture.getInstrumentToken();
                    lastCacheTime = LocalDateTime.now();
                    log.info("Cached first available Nifty future token: {} (expires in {} days)", 
                            cachedFutureToken, daysUntilExpiry);

                    return Optional.of(firstFuture.getInstrumentToken());
                } catch (Exception e) {
                    log.error("Error calculating days until expiry for first future: {}", e.getMessage());
                    // Still update cache even if there's an error parsing expiry
                    cachedFutureToken = firstFuture.getInstrumentToken();
                    lastCacheTime = LocalDateTime.now();
                    return Optional.of(firstFuture.getInstrumentToken());
                }
            }

        } catch (Exception e) {
            log.error("Error getting dynamic Nifty 50 future token: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Gets the current month's Nifty 50 future trading symbol
     *
     * @return Optional containing the trading symbol if found, empty otherwise
     */
    public Optional<String> getCurrentMonthNiftyFutureSymbol() {
        try {
            // Get current month and year
            LocalDate now = LocalDate.now();
            String currentMonth = now.format(DateTimeFormatter.ofPattern("MMM")).toUpperCase();
            String currentYear = String.valueOf(now.getYear()).substring(2); // Get last 2 digits of year

            // Find Nifty future instruments using efficient database query
            List<Instrument> niftyFutures = instrumentRepository.findByNameAndInstrumentTypeAndSegmentOrderByExpiryAsc("NIFTY", "FUT", "NFO-FUT");

            // Find the current month's future
            Optional<Instrument> currentMonthFuture = niftyFutures.stream()
                    .filter(instrument -> {
                        String tradingSymbol = instrument.getTradingSymbol();
                        // Check if trading symbol contains current month and year
                        return tradingSymbol != null &&
                                tradingSymbol.contains(currentMonth) &&
                                tradingSymbol.contains(currentYear);
                    })
                    .findFirst();

            return currentMonthFuture.map(Instrument::getTradingSymbol);

        } catch (Exception e) {
            log.error("Error getting current month Nifty 50 future symbol: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    public void setInstrumentTokens(String exchange) throws IOException, KiteException {
        List<com.zerodhatech.models.Instrument> instruments = kiteConnect.getInstruments(exchange);
        instruments
                .forEach(instrument -> {
                    if (instrument.name.equalsIgnoreCase("NIFTY") &&
                            (instrument.segment.equalsIgnoreCase("NFO-OPT") || instrument.segment.equalsIgnoreCase("NFO-FUT"))) {
                        com.jtradebot.processor.repository.document.Instrument newInstrument = new com.jtradebot.processor.repository.document.Instrument();
                        newInstrument.setInstrumentToken(instrument.instrument_token);
                        newInstrument.setTradingSymbol(instrument.tradingsymbol);
                        newInstrument.setExpiry(formatDate(instrument.expiry));
                        newInstrument.setExchange(instrument.exchange);
                        newInstrument.setExchangeToken(String.valueOf(instrument.exchange_token));
                        newInstrument.setInstrumentType(instrument.instrument_type);
                        newInstrument.setName(instrument.name);
                        newInstrument.setSegment(instrument.segment);
                        newInstrument.setStrike(instrument.strike);
                        instrumentRepository.save(newInstrument);
                    }
                });
    }

    public void deleteInstruments() {
        instrumentRepository.deleteAll();
    }

    /**
     * Manually refresh the cache for Nifty future token
     * This can be useful when instruments are updated or when cache needs to be refreshed
     */
    public void refreshNiftyFutureTokenCache() {
        log.info("Manually refreshing Nifty future token cache");
        cachedFutureToken = null;
        lastCacheTime = null;
    }

    /**
     * Get cache status information for debugging
     */
    public String getCacheStatus() {
        if (cachedFutureToken == null || lastCacheTime == null) {
            return "Cache is empty";
        }
        Duration timeSinceLastCache = Duration.between(lastCacheTime, LocalDateTime.now());
        Duration remainingTime = CACHE_DURATION.minus(timeSinceLastCache);
        return String.format("Cached token: %s, Last updated: %s, Remaining cache time: %s", 
                cachedFutureToken, lastCacheTime, remainingTime);
    }

}
