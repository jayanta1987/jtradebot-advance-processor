package com.jtradebot.processor.handler;

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

    private static final String STRIKE_TYPE = "ITM"; // Changed to ITM for live trading
    private static final int STRIKE_DIFFERENCE = 100;
    
    private final InstrumentRepository instrumentRepository;

    public static String calculateStrikePrice(Double ltp, String instrumentType) {
        String strikePrice = getBaseStrikePrice(ltp, instrumentType);

        if (STRIKE_TYPE.equals("ITM")) {
            if (instrumentType.equals("PE")) {
                strikePrice = String.valueOf(Integer.parseInt(strikePrice) + STRIKE_DIFFERENCE);
            } else {
                strikePrice = String.valueOf(Integer.parseInt(strikePrice) - STRIKE_DIFFERENCE);
            }
        }

        return strikePrice;
    }

    private static String getBaseStrikePrice(Double ltp, String instrumentType) {
        if (instrumentType.equals("PE")) {
            return String.valueOf((int) Math.ceil(ltp / STRIKE_DIFFERENCE) * STRIKE_DIFFERENCE);
        } else {
            return String.valueOf((int) Math.floor(ltp / STRIKE_DIFFERENCE) * STRIKE_DIFFERENCE);
        }
    }

    /**
     * Get ITM strike price for current Nifty index
     */
    public int getITMStrikePrice(double niftyIndexPrice, String optionType) {
        if ("CE".equals(optionType)) {
            // For CALL options, ITM means strike < current price
            return (int) Math.floor(niftyIndexPrice / STRIKE_DIFFERENCE) * STRIKE_DIFFERENCE - STRIKE_DIFFERENCE;
        } else if ("PE".equals(optionType)) {
            // For PUT options, ITM means strike > current price
            return (int) Math.ceil(niftyIndexPrice / STRIKE_DIFFERENCE) * STRIKE_DIFFERENCE + STRIKE_DIFFERENCE;
        } else {
            throw new IllegalArgumentException("Invalid option type: " + optionType);
        }
    }

    /**
     * Find the actual option instrument for live trading (next available expiry)
     */
    public Optional<Instrument> findOptionInstrument(double niftyIndexPrice, String optionType) {
        try {
            int strikePrice = getITMStrikePrice(niftyIndexPrice, optionType);
            
            log.info("🔍 FINDING OPTION INSTRUMENT - Strike: {}, Type: {}", strikePrice, optionType);
            
            // Find all instruments and filter for Nifty options (same approach as API)
            List<Instrument> allInstruments = instrumentRepository.findAll();
            List<Instrument> niftyOptions = allInstruments.stream()
                .filter(instrument -> "NIFTY".equals(instrument.getName()) && 
                        ("OPT".equals(instrument.getInstrumentType()) || "CE".equals(instrument.getInstrumentType()) || "PE".equals(instrument.getInstrumentType())))
                .sorted((i1, i2) -> {
                    try {
                        java.time.LocalDate expiry1 = java.time.LocalDate.parse(i1.getExpiry(), java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                        java.time.LocalDate expiry2 = java.time.LocalDate.parse(i2.getExpiry(), java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                        return expiry1.compareTo(expiry2);
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .collect(java.util.stream.Collectors.toList());
            
            // Debug: Show first few instruments
            log.info("📊 TOTAL NIFTY OPTIONS FOUND: {}", niftyOptions.size());
            if (!niftyOptions.isEmpty()) {
                log.info("📋 SAMPLE INSTRUMENTS (sorted by expiry):");
                niftyOptions.stream().limit(5).forEach(instrument -> 
                    log.info("   - {} (Strike: {}, Type: {}, Expiry: {})", 
                        instrument.getTradingSymbol(), instrument.getStrike(), instrument.getInstrumentType(), instrument.getExpiry()));
            }
            
            // Filter by strike price and option type, then get the first one (earliest expiry)
            Optional<Instrument> optionInstrument = niftyOptions.stream()
                    .filter(instrument -> {
                        String tradingSymbol = instrument.getTradingSymbol();
                        return tradingSymbol != null && 
                               tradingSymbol.contains(String.valueOf(strikePrice)) &&
                               tradingSymbol.endsWith(optionType);
                    })
                    .findFirst(); // This will get the first one (earliest expiry) after proper date sorting
            
            if (optionInstrument.isPresent()) {
                Instrument instrument = optionInstrument.get();
                log.info("✅ FOUND OPTION INSTRUMENT - Symbol: {}, Token: {}, Strike: {}, Expiry: {}", 
                        instrument.getTradingSymbol(), instrument.getInstrumentToken(), 
                        instrument.getStrike(), instrument.getExpiry());
                return optionInstrument;
            } else {
                log.warn("⚠️ NO OPTION INSTRUMENT FOUND - Strike: {}, Type: {}", strikePrice, optionType);
                return Optional.empty();
            }
            
        } catch (Exception e) {
            log.error("Error finding option instrument for strike: {}, type: {}", getITMStrikePrice(niftyIndexPrice, optionType), optionType, e);
            return Optional.empty();
        }
    }



    /**
     * Get the next available option instrument if current month is not available
     */
    public Optional<Instrument> findNextAvailableOptionInstrument(double niftyIndexPrice, String optionType) {
        try {
            int strikePrice = getITMStrikePrice(niftyIndexPrice, optionType);
            
            log.info("🔍 FINDING NEXT AVAILABLE OPTION - Strike: {}, Type: {}", strikePrice, optionType);
            
            // Find all instruments and filter for Nifty options (same approach as API)
            List<Instrument> allInstruments = instrumentRepository.findAll();
            List<Instrument> niftyOptions = allInstruments.stream()
                .filter(instrument -> "NIFTY".equals(instrument.getName()) && 
                        ("OPT".equals(instrument.getInstrumentType()) || "CE".equals(instrument.getInstrumentType()) || "PE".equals(instrument.getInstrumentType())))
                .sorted((i1, i2) -> {
                    try {
                        java.time.LocalDate expiry1 = java.time.LocalDate.parse(i1.getExpiry(), java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                        java.time.LocalDate expiry2 = java.time.LocalDate.parse(i2.getExpiry(), java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                        return expiry1.compareTo(expiry2);
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .collect(java.util.stream.Collectors.toList());
            
            // Filter by strike price and option type, then get the first one (earliest expiry)
            Optional<Instrument> optionInstrument = niftyOptions.stream()
                    .filter(instrument -> {
                        String tradingSymbol = instrument.getTradingSymbol();
                        return tradingSymbol != null && 
                               tradingSymbol.contains(String.valueOf(strikePrice)) &&
                               tradingSymbol.endsWith(optionType);
                    })
                    .findFirst();
            
            if (optionInstrument.isPresent()) {
                Instrument instrument = optionInstrument.get();
                log.info("✅ FOUND NEXT AVAILABLE OPTION - Symbol: {}, Token: {}, Strike: {}, Expiry: {}", 
                        instrument.getTradingSymbol(), instrument.getInstrumentToken(), 
                        instrument.getStrike(), instrument.getExpiry());
                return optionInstrument;
            } else {
                log.warn("⚠️ NO NEXT AVAILABLE OPTION FOUND - Strike: {}, Type: {}", strikePrice, optionType);
                return Optional.empty();
            }
            
        } catch (Exception e) {
            log.error("Error finding next available option instrument for strike: {}, type: {}", getITMStrikePrice(niftyIndexPrice, optionType), optionType, e);
            return Optional.empty();
        }
    }
}