package com.jtradebot.processor.service.price;

import com.jtradebot.processor.repository.InstrumentRepository;
import com.jtradebot.processor.repository.document.Instrument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StrikePriceCalculator {

    private static final String STRIKE_TYPE = "ATM"; // Changed to ATM for live trading
    private static final int STRIKE_DIFFERENCE = 50;
    
    private final InstrumentRepository instrumentRepository;

    private static String getBaseStrikePrice(Double ltp, String instrumentType) {
        if (instrumentType.equals("PE")) {
            return String.valueOf((int) Math.ceil(ltp / STRIKE_DIFFERENCE) * STRIKE_DIFFERENCE);
        } else {
            return String.valueOf((int) Math.floor(ltp / STRIKE_DIFFERENCE) * STRIKE_DIFFERENCE);
        }
    }

    /**
     * Get ATM strike price for current Nifty index
     * 
     * ATM (At-The-Money) strikes are closest to the current index price:
     * - CE (CALL): Strike closest to but below current price
     * - PE (PUT): Strike closest to but above current price
     * 
     * Example: For index 24702 with 50-point difference:
     * - CE strike: 24700 (closest below 24702)
     * - PE strike: 24750 (closest above 24702)
     */
    public int getATMStrikePrice(double niftyIndexPrice, String optionType) {
        if ("CE".equals(optionType)) {
            // For CALL options, ATM means strike closest to but below current price
            return (int) Math.floor(niftyIndexPrice / STRIKE_DIFFERENCE) * STRIKE_DIFFERENCE;
        } else if ("PE".equals(optionType)) {
            // For PUT options, ATM means strike closest to but above current price
            return (int) Math.ceil(niftyIndexPrice / STRIKE_DIFFERENCE) * STRIKE_DIFFERENCE;
        } else {
            throw new IllegalArgumentException("Invalid option type: " + optionType);
        }
    }

    /**
     * Find the actual option instrument for live trading (next available expiry)
     */
    public Optional<Instrument> findOptionInstrument(double niftyIndexPrice, String optionType) {
        try {
            int strikePrice = getATMStrikePrice(niftyIndexPrice, optionType);
            
            log.info("🔍 FINDING ATM OPTION INSTRUMENT - Strike: {}, Type: {}", strikePrice, optionType);
            
            // Get current date to filter out expired options
            LocalDate currentDate = LocalDate.now();
            log.info("📅 CURRENT DATE: {}", currentDate);
            
            // Find all instruments and filter for Nifty options (same approach as API)
            List<Instrument> allInstruments = instrumentRepository.findAll();
            List<Instrument> niftyOptions = allInstruments.stream()
                .filter(instrument -> "NIFTY".equals(instrument.getName()) && 
                        ("OPT".equals(instrument.getInstrumentType()) || "CE".equals(instrument.getInstrumentType()) || "PE".equals(instrument.getInstrumentType())))
                .filter(instrument -> {
                    // Filter out expired options
                    try {
                        LocalDate expiryDate = LocalDate.parse(instrument.getExpiry(), DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                        boolean isNotExpired = expiryDate.isAfter(currentDate);
                        if (!isNotExpired) {
                            log.debug("⏰ FILTERING OUT EXPIRED OPTION - Symbol: {}, Expiry: {}, Current: {}", 
                                    instrument.getTradingSymbol(), expiryDate, currentDate);
                        }
                        return isNotExpired;
                    } catch (Exception e) {
                        log.warn("⚠️ Could not parse expiry date for instrument: {} - Expiry: {}", 
                                instrument.getTradingSymbol(), instrument.getExpiry());
                        return false; // Skip instruments with invalid expiry dates
                    }
                })
                .sorted((i1, i2) -> {
                    try {
                        LocalDate expiry1 = LocalDate.parse(i1.getExpiry(), DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                        LocalDate expiry2 = LocalDate.parse(i2.getExpiry(), DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                        return expiry1.compareTo(expiry2);
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .toList();
            
            // Debug: Show first few instruments
            log.debug("📊 TOTAL VALID NIFTY OPTIONS FOUND (not expired): {}", niftyOptions.size());
            if (!niftyOptions.isEmpty()) {
                log.debug("📋 SAMPLE VALID INSTRUMENTS (sorted by expiry):");
                niftyOptions.stream().limit(5).forEach(instrument -> 
                    log.debug("   - {} (Strike: {}, Type: {}, Expiry: {})",
                        instrument.getTradingSymbol(), instrument.getStrike(), instrument.getInstrumentType(), instrument.getExpiry()));
            }
            
            // Filter by strike price and option type, then get the first one (earliest valid expiry)
            Optional<Instrument> optionInstrument = niftyOptions.stream()
                    .filter(instrument -> {
                        String tradingSymbol = instrument.getTradingSymbol();
                        return tradingSymbol != null && 
                               tradingSymbol.contains(String.valueOf(strikePrice)) &&
                               tradingSymbol.endsWith(optionType);
                    })
                    .findFirst(); // This will get the first one (earliest valid expiry) after proper date sorting
            
            if (optionInstrument.isPresent()) {
                Instrument instrument = optionInstrument.get();
                log.info("✅ FOUND VALID OPTION INSTRUMENT - Symbol: {}, Token: {}, Strike: {}, Expiry: {}", 
                        instrument.getTradingSymbol(), instrument.getInstrumentToken(), 
                        instrument.getStrike(), instrument.getExpiry());
                return optionInstrument;
            } else {
                log.warn("⚠️ NO VALID OPTION INSTRUMENT FOUND - Strike: {}, Type: {}, Current Date: {}", 
                        strikePrice, optionType, currentDate);
                
                // Try to find any option with this strike (including expired ones) for debugging
                List<Instrument> allOptionsWithStrike = allInstruments.stream()
                    .filter(instrument -> "NIFTY".equals(instrument.getName()) && 
                            ("OPT".equals(instrument.getInstrumentType()) || "CE".equals(instrument.getInstrumentType()) || "PE".equals(instrument.getInstrumentType())))
                    .filter(instrument -> {
                        String tradingSymbol = instrument.getTradingSymbol();
                        return tradingSymbol != null && 
                               tradingSymbol.contains(String.valueOf(strikePrice)) &&
                               tradingSymbol.endsWith(optionType);
                    })
                    .toList();
                
                if (!allOptionsWithStrike.isEmpty()) {
                    log.warn("🔍 DEBUG: Found {} options with strike {} but all are expired:", strikePrice, allOptionsWithStrike.size());
                    allOptionsWithStrike.forEach(instrument -> 
                        log.warn("   - {} (Expiry: {})", instrument.getTradingSymbol(), instrument.getExpiry()));
                }
                
                return Optional.empty();
            }
            
        } catch (Exception e) {
            log.error("Error finding option instrument for strike: {}, type: {}", getATMStrikePrice(niftyIndexPrice, optionType), optionType, e);
            return Optional.empty();
        }
    }

}