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
    private static final int STRIKE_DIFFERENCE = 100;
    private static final int OTM_THRESHOLD = 15; // If price is within 20 points of next strike, go OTM
    
    private final InstrumentRepository instrumentRepository;

    /**
     * Get ATM strike price for current Nifty index
     * 
     * Strike prices are divisible by 100. If current price is too close to next strike (within 20 points),
     * we go OTM to avoid strikes divisible by 50.
     * 
     * - CE (CALL): Strike closest to but below current price, or next strike if within 20 points
     * - PE (PUT): Strike closest to but above current price, or previous strike if within 20 points
     * 
     * Example 1: For index 24990 with CE:
     * - Base strike: 24900, Next strike: 25000
     * - Distance to next: 25000 - 24990 = 10 (≤ 20 threshold)
     * - Result: 25000 CE (go OTM)
     * 
     * Example 2: For index 24975 with CE:
     * - Base strike: 24900, Next strike: 25000
     * - Distance to next: 25000 - 24975 = 25 (> 20 threshold)
     * - Result: 24900 CE (stay at base)
     */
    public int getATMStrikePrice(double niftyIndexPrice, String optionType) {
        if ("CE".equals(optionType)) {
            // For CALL options: base strike is below current price
            int baseStrike = (int) Math.floor(niftyIndexPrice / STRIKE_DIFFERENCE) * STRIKE_DIFFERENCE;
            int nextStrike = baseStrike + STRIKE_DIFFERENCE;
            
            // If too close to next strike, go OTM (use next strike)
            if ((nextStrike - niftyIndexPrice) <= OTM_THRESHOLD) {
                log.debug("🎯 CE Strike Selection: Index {} too close to next strike {}, going OTM", 
                        niftyIndexPrice, nextStrike);
                return nextStrike;
            }
            
            log.debug("🎯 CE Strike Selection: Index {} using base strike {}", niftyIndexPrice, baseStrike);
            return baseStrike;
            
        } else if ("PE".equals(optionType)) {
            // For PUT options: base strike is above current price
            int baseStrike = (int) Math.ceil(niftyIndexPrice / STRIKE_DIFFERENCE) * STRIKE_DIFFERENCE;
            int previousStrike = baseStrike - STRIKE_DIFFERENCE;
            
            // If too close to previous strike, go OTM (use previous strike)
            if ((niftyIndexPrice - previousStrike) <= OTM_THRESHOLD) {
                log.debug("🎯 PE Strike Selection: Index {} too close to previous strike {}, going OTM", 
                        niftyIndexPrice, previousStrike);
                return previousStrike;
            }
            
            log.debug("🎯 PE Strike Selection: Index {} using base strike {}", niftyIndexPrice, baseStrike);
            return baseStrike;
            
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
            
            // Use optimized query with index: instrumentType_1_name_1_segment_1_expiry_1
            // Query by instrumentType first to leverage the compound index
            List<String> optionTypes = List.of("CE", "PE", "OPT");
            List<Instrument> allNiftyOptions = instrumentRepository.findByInstrumentTypeInAndName(optionTypes, "NIFTY");
            
            List<Instrument> niftyOptions = allNiftyOptions.stream()
                .filter(instrument -> {
                    // Filter out expired options - include today's expiry if today is expiry date
                    try {
                        LocalDate expiryDate = LocalDate.parse(instrument.getExpiry(), DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                        boolean isValidExpiry = expiryDate.isAfter(currentDate) || expiryDate.isEqual(currentDate);
                        if (!isValidExpiry) {
                            log.debug("⏰ FILTERING OUT EXPIRED OPTION - Symbol: {}, Expiry: {}, Current: {}", 
                                    instrument.getTradingSymbol(), expiryDate, currentDate);
                        } else if (expiryDate.isEqual(currentDate)) {
                            log.info("📅 INCLUDING TODAY'S EXPIRY - Symbol: {}, Expiry: {}, Current: {}", 
                                    instrument.getTradingSymbol(), expiryDate, currentDate);
                        }
                        return isValidExpiry;
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
                List<Instrument> allOptionsWithStrike = allNiftyOptions.stream()
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