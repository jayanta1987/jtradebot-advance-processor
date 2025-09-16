package com.jtradebot.processor.service.price;

import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.repository.InstrumentRepository;
import com.jtradebot.processor.repository.document.Instrument;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedStrikeSelectionService {

    private final InstrumentRepository instrumentRepository;
    private final TickDataManager tickDataManager;
    private final StrikePriceCalculator strikePriceCalculator;

    /**
     * Find the best strike for scalping within ±100 range from current index price
     * Automatically evaluates all available strikes in the range and selects the best one
     * 
     * @param niftyIndexPrice Current Nifty index price
     * @param optionType CE or PE
     * @return Best strike option instrument for scalping
     */
    public Optional<Instrument> findBestStrikeInRange(double niftyIndexPrice, String optionType) {
        try {
            log.info("🎯 FINDING BEST STRIKE IN ±100 RANGE - Index: {}, Type: {}", niftyIndexPrice, optionType);

            // Define the range: ±100 from current index price
            double minStrike = niftyIndexPrice - 100;
            double maxStrike = niftyIndexPrice + 100;
            
            // Get all valid Nifty options in the range
            List<Instrument> validOptions = getValidNiftyOptionsInRange(optionType, minStrike, maxStrike);
            if (validOptions.isEmpty()) {
                log.warn("No valid Nifty options found in range {}-{} for type: {}", minStrike, maxStrike, optionType);
                return Optional.empty();
            }

            log.debug("Found {} valid options in range {}-{}", validOptions.size(), minStrike, maxStrike);

            // Get current tick data for all options
            Map<Long, Tick> tickDataMap = getTickDataForInstruments(validOptions);

            // Score each option and find the best one
            Optional<StrikeScore> bestStrike = findBestStrikeByScore(validOptions, tickDataMap, niftyIndexPrice, optionType);

            if (bestStrike.isEmpty()) {
                log.warn("No scored strikes found, falling back to ATM selection");
                return strikePriceCalculator.findOptionInstrument(niftyIndexPrice, optionType);
            }

            StrikeScore best = bestStrike.get();
            log.info("🏆 BEST STRIKE SELECTED - Symbol: {}, Score: {:.2f}, Strike: {}, Volume: {}, OI: {}, LTP: {}", 
                    best.getInstrument().getTradingSymbol(), best.getTotalScore(),
                    best.getInstrument().getStrike(), best.getVolume(), best.getOi(), best.getLtp());

            return Optional.of(best.getInstrument());

        } catch (Exception e) {
            log.error("Error in enhanced strike selection", e);
            // Fallback to original ATM selection
            return strikePriceCalculator.findOptionInstrument(niftyIndexPrice, optionType);
        }
    }


    /**
     * Get valid Nifty options within the specified strike range
     */
    private List<Instrument> getValidNiftyOptionsInRange(String optionType, double minStrike, double maxStrike) {
        LocalDate currentDate = LocalDate.now();
        
        return instrumentRepository.findAll().stream()
                .filter(instrument -> "NIFTY".equals(instrument.getName()))
                .filter(instrument -> optionType.equals(instrument.getInstrumentType()))
                .filter(instrument -> {
                    try {
                        // Check if strike is within range
                        int strike = Integer.parseInt(instrument.getStrike());
                        boolean inRange = strike >= minStrike && strike <= maxStrike;
                        
                        // Check if not expired
                        LocalDate expiryDate = LocalDate.parse(instrument.getExpiry(), 
                                DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                        boolean notExpired = expiryDate.isAfter(currentDate);
                        
                        return inRange && notExpired;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .sorted((i1, i2) -> {
                    try {
                        // Sort by expiry first (earliest first)
                        LocalDate expiry1 = LocalDate.parse(i1.getExpiry(), 
                                DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                        LocalDate expiry2 = LocalDate.parse(i2.getExpiry(), 
                                DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                        int expiryComparison = expiry1.compareTo(expiry2);
                        
                        if (expiryComparison != 0) {
                            return expiryComparison;
                        }
                        
                        // Then sort by strike price
                        int strike1 = Integer.parseInt(i1.getStrike());
                        int strike2 = Integer.parseInt(i2.getStrike());
                        return Integer.compare(strike1, strike2);
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * Get tick data for multiple instruments
     */
    private Map<Long, Tick> getTickDataForInstruments(List<Instrument> instruments) {
        Map<Long, Tick> tickDataMap = new HashMap<>();
        
        for (Instrument instrument : instruments) {
            try {
                Tick tick = tickDataManager.getLastTick(String.valueOf(instrument.getInstrumentToken()));
                if (tick != null) {
                    tickDataMap.put(instrument.getInstrumentToken(), tick);
                }
            } catch (Exception e) {
                log.debug("Could not get tick data for instrument: {}", instrument.getTradingSymbol());
            }
        }
        
        return tickDataMap;
    }

    /**
     * Find the best strike by scoring all available options
     */
    private Optional<StrikeScore> findBestStrikeByScore(List<Instrument> instruments, 
                                                       Map<Long, Tick> tickDataMap,
                                                       double niftyIndexPrice, String optionType) {
        List<StrikeScore> scores = new ArrayList<>();
        
        for (Instrument instrument : instruments) {
            Tick tick = tickDataMap.get(instrument.getInstrumentToken());
            if (tick == null) continue;
            
            StrikeScore score = calculateStrikeScore(instrument, tick, niftyIndexPrice, optionType);
            if (score != null) {
                scores.add(score);
            }
        }
        
        if (scores.isEmpty()) {
            return Optional.empty();
        }
        
        // Sort by total score (highest first) and return the best one
        scores.sort((a, b) -> Double.compare(b.getTotalScore(), a.getTotalScore()));
        return Optional.of(scores.get(0));
    }

    /**
     * Calculate comprehensive score for a strike
     * Uses automatic scoring without pre-configured criteria
     */
    private StrikeScore calculateStrikeScore(Instrument instrument, Tick tick, 
                                           double niftyIndexPrice, String optionType) {
        try {
            int strikePrice = Integer.parseInt(instrument.getStrike());
            
            StrikeScore score = new StrikeScore();
            score.setInstrument(instrument);
            score.setVolume(tick.getVolumeTradedToday());
            score.setOi(tick.getOi());
            score.setLtp(tick.getLastTradedPrice());
            score.setStrikePrice(strikePrice);
            
            // 1. Volume Score (0-100) - Higher volume = better liquidity
            double volumeScore = calculateVolumeScore(tick.getVolumeTradedToday());
            score.setVolumeScore(volumeScore);
            
            // 2. OI Score (0-100) - Higher OI = more participation
            double oiScore = calculateOIScore(tick.getOi());
            score.setOiScore(oiScore);
            
            // 3. Moneyness Score (0-100) - Optimal distance from current price
            double moneynessScore = calculateMoneynessScore(strikePrice, niftyIndexPrice, optionType);
            score.setMoneynessScore(moneynessScore);
            
            // 4. Price Score (0-100) - Reasonable option price for scalping
            double priceScore = calculatePriceScore(tick.getLastTradedPrice());
            score.setPriceScore(priceScore);
            
            // 5. Liquidity Score (0-100) - Based on buy/sell quantities
            double liquidityScore = calculateLiquidityScore(tick);
            score.setLiquidityScore(liquidityScore);
            
            // Calculate weighted total score (optimized for scalping)
            double totalScore = (volumeScore * 0.35) +      // Volume is most important for scalping
                               (oiScore * 0.25) +           // OI shows participation
                               (moneynessScore * 0.20) +    // Distance from current price
                               (priceScore * 0.10) +        // Option price reasonableness
                               (liquidityScore * 0.10);     // Buy/sell balance
            
            score.setTotalScore(totalScore);
            
            log.debug("📊 STRIKE SCORE - Symbol: {}, Strike: {}, Total: {:.2f} (V:{:.1f}, OI:{:.1f}, M:{:.1f}, P:{:.1f}, L:{:.1f})",
                    instrument.getTradingSymbol(), strikePrice, totalScore, 
                    volumeScore, oiScore, moneynessScore, priceScore, liquidityScore);
            
            return score;
            
        } catch (Exception e) {
            log.warn("Error calculating score for instrument: {}", instrument.getTradingSymbol(), e);
            return null;
        }
    }

    /**
     * Calculate volume score (0-100) - automatically scaled
     */
    private double calculateVolumeScore(long volume) {
        if (volume == 0) return 0;
        
        // Use logarithmic scaling for volume to handle wide ranges
        // Volume of 1000+ gets good score, 10000+ gets excellent score
        double logVolume = Math.log10(volume);
        double score = Math.min(100, (logVolume / 5.0) * 100); // log10(100000) ≈ 5
        
        return Math.max(0, score);
    }

    /**
     * Calculate OI score (0-100) - automatically scaled
     */
    private double calculateOIScore(double oi) {
        if (oi == 0) return 0;
        
        // Use logarithmic scaling for OI
        double logOi = Math.log10(oi);
        double score = Math.min(100, (logOi / 5.0) * 100);
        
        return Math.max(0, score);
    }

    /**
     * Calculate moneyness score (0-100) - optimal distance from current price
     */
    private double calculateMoneynessScore(int strikePrice, double niftyIndexPrice, String optionType) {
        double distanceFromCurrent = Math.abs(strikePrice - niftyIndexPrice);
        
        // For scalping, we want strikes that are not too close to current price
        // but also not too far. Optimal range is typically 25-75 points from current price
        
        if (distanceFromCurrent < 25) {
            return 50; // Too close to current price
        }
        
        if (distanceFromCurrent > 100) {
            return 0; // Outside our range
        }
        
        // Optimal distance: 50 points from current price gets highest score
        double optimalDistance = 50;
        double distanceScore = 100 - Math.abs(distanceFromCurrent - optimalDistance) / optimalDistance * 50;
        
        return Math.max(0, distanceScore);
    }

    /**
     * Calculate price score (0-100) - reasonable option price for scalping
     */
    private double calculatePriceScore(double optionPrice) {
        if (optionPrice <= 0) return 0;
        
        // For scalping, we want options that are not too cheap (low liquidity)
        // and not too expensive (high risk). Optimal range is typically 20-100
        
        if (optionPrice < 10) return 20;  // Too cheap, likely low liquidity
        if (optionPrice > 200) return 20; // Too expensive, high risk
        
        // Optimal price range: 30-80 gets highest score
        if (optionPrice >= 30 && optionPrice <= 80) {
            return 100;
        }
        
        // Gradual decrease outside optimal range
        if (optionPrice < 30) {
            return 50 + (optionPrice - 10) / 20 * 50; // 20-50 range
        } else {
            return 100 - (optionPrice - 80) / 120 * 80; // 80-200 range
        }
    }

    /**
     * Calculate liquidity score based on buy/sell quantities
     */
    private double calculateLiquidityScore(Tick tick) {
        double totalBuyQuantity = tick.getTotalBuyQuantity();
        double totalSellQuantity = tick.getTotalSellQuantity();
        
        if (totalBuyQuantity == 0 && totalSellQuantity == 0) return 0;
        
        // Good liquidity when both buy and sell quantities are high and balanced
        double totalQuantity = totalBuyQuantity + totalSellQuantity;
        double liquidityRatio = Math.min(totalBuyQuantity, totalSellQuantity) / totalQuantity;
        
        // Score based on total quantity and balance
        double quantityScore = Math.min(100, totalQuantity / 500); // Scale based on expected quantities
        double balanceScore = liquidityRatio * 100;
        
        return (quantityScore + balanceScore) / 2;
    }

    /**
     * Strike score data class
     */
    @lombok.Data
    public static class StrikeScore {
        private Instrument instrument;
        private long volume;
        private double oi;
        private double ltp;
        private int strikePrice;
        private double volumeScore;
        private double oiScore;
        private double moneynessScore;
        private double priceScore;
        private double liquidityScore;
        private double totalScore;
    }
}
