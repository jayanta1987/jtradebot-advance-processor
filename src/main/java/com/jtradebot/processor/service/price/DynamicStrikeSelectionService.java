package com.jtradebot.processor.service.price;

import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.repository.InstrumentRepository;
import com.jtradebot.processor.repository.document.Instrument;
import com.zerodhatech.models.Tick;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dynamic Strike Selection Service with advanced option analysis
 * Incorporates Greeks, volatility analysis, time decay, and market conditions
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicStrikeSelectionService {

    private final InstrumentRepository instrumentRepository;
    private final TickDataManager tickDataManager;
    private final OptionGreeksCalculator greeksCalculator;
    private final StrikePriceCalculator strikePriceCalculator;

    /**
     * Find the optimal strike for trading based on comprehensive analysis
     * 
     * @param niftyIndexPrice Current Nifty index price
     * @param optionType CE or PE
     * @param tradingStrategy Strategy type: "SCALPING", "SWING", "INTRADAY"
     * @return Best strike option instrument with detailed analysis
     */
    public Optional<DynamicStrikeAnalysis> findOptimalStrike(double niftyIndexPrice, String optionType, String tradingStrategy) {
        try {
            log.info("🎯 DYNAMIC STRIKE SELECTION - Index: {}, Type: {}, Strategy: {}", 
                    niftyIndexPrice, optionType, tradingStrategy);

            // Define range based on strategy
            StrikeRange range = getStrikeRange(niftyIndexPrice, tradingStrategy);
            log.info("🎯 STRIKE RANGE CALCULATION - Index: {}, Strategy: {}, Range: {}-{}", 
                    niftyIndexPrice, tradingStrategy, range.minStrike, range.maxStrike);
            
            // Get valid options in range
            List<Instrument> validOptions = getValidOptionsInRange(optionType, range);
            if (validOptions.isEmpty()) {
                throw new RuntimeException("No valid options found in range " + range.minStrike + " to " + range.maxStrike + 
                        " for option type " + optionType + " with index price " + niftyIndexPrice);
            }

            log.debug("Found {} valid options in range {}-{}", validOptions.size(), range.minStrike, range.maxStrike);

            // Get tick data and calculate comprehensive scores
            Map<Long, Tick> tickDataMap = getTickDataForInstruments(validOptions);
            List<DynamicStrikeScore> scores = calculateDynamicScores(validOptions, tickDataMap, niftyIndexPrice, optionType, tradingStrategy);

            if (scores.isEmpty()) {
                throw new RuntimeException("No valid strikes found in range " + range.minStrike + " to " + range.maxStrike + 
                        " for option type " + optionType + " with index price " + niftyIndexPrice);
            }

            // Sort by total score and get the best one
            scores.sort((a, b) -> Double.compare(b.getTotalScore(), a.getTotalScore()));
            DynamicStrikeScore bestScore = scores.get(0);

            // Create comprehensive analysis
            DynamicStrikeAnalysis analysis = DynamicStrikeAnalysis.builder()
                    .instrument(bestScore.getInstrument())
                    .totalScore(bestScore.getTotalScore())
                    .greeks(bestScore.getGreeks())
                    .marketFactors(bestScore.getMarketFactors())
                    .strategyFactors(bestScore.getStrategyFactors())
                    .riskMetrics(bestScore.getRiskMetrics())
                    .allScores(scores)
                    .analysisTimestamp(System.currentTimeMillis())
                    .build();

            log.info("✅ OPTIMAL STRIKE FOUND - Symbol: {}, Strike: {}, Score: {:.2f}, Delta: {:.3f}, Theta: {:.3f}", 
                    bestScore.getInstrument().getTradingSymbol(), 
                    bestScore.getInstrument().getStrike(),
                    bestScore.getTotalScore(),
                    bestScore.getGreeks().getDelta(),
                    bestScore.getGreeks().getTheta());

            return Optional.of(analysis);

        } catch (Exception e) {
            log.error("Error in dynamic strike selection for index: {}, type: {}, strategy: {}", 
                    niftyIndexPrice, optionType, tradingStrategy, e);
            throw new RuntimeException("Dynamic strike selection failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get strike range based on trading strategy
     * Nifty options use 100-point strike increments, so ranges must align with this
     */
    private StrikeRange getStrikeRange(double niftyIndexPrice, String tradingStrategy) {
        switch (tradingStrategy.toUpperCase()) {
            case "SCALPING":
                // ±100 range for scalping (1 strike on each side of ATM)
                return new StrikeRange(niftyIndexPrice - 100, niftyIndexPrice + 100);
            case "INTRADAY":
                // ±200 range for intraday (2 strikes on each side of ATM)
                return new StrikeRange(niftyIndexPrice - 200, niftyIndexPrice + 200);
            case "SWING":
                // ±400 range for swing trading (4 strikes on each side of ATM)
                return new StrikeRange(niftyIndexPrice - 400, niftyIndexPrice + 400);
            default:
                return new StrikeRange(niftyIndexPrice - 200, niftyIndexPrice + 200);
        }
    }

    /**
     * Get valid options within the specified range
     */
    private List<Instrument> getValidOptionsInRange(String optionType, StrikeRange range) {
        LocalDate currentDate = LocalDate.now();
        log.info("📅 CURRENT DATE: {}", currentDate);
        
        // Use optimized query with index: instrumentType_1_name_1_segment_1_expiry_1
        // Query by instrumentType first to leverage the compound index
        List<Instrument> allNiftyOptions = instrumentRepository.findByInstrumentTypeAndNameOrderByExpiryAsc(optionType, "NIFTY");
        
        log.info("🔍 TOTAL NIFTY {} OPTIONS FOUND: {}", optionType, allNiftyOptions.size());
        
        List<Instrument> validOptions = allNiftyOptions.stream()
                .filter(instrument -> {
                    try {
                        int strike = Integer.parseInt(instrument.getStrike());
                        boolean inRange = strike >= range.minStrike && strike <= range.maxStrike;
                        
                        LocalDate expiryDate = LocalDate.parse(instrument.getExpiry(), 
                                DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                        boolean notExpired = expiryDate.isAfter(currentDate);
                        
                        if (inRange && notExpired) {
                            log.info("✅ VALID OPTION: Strike={}, Expiry={}, Symbol={}", 
                                    strike, instrument.getExpiry(), instrument.getTradingSymbol());
                        } else if (inRange && !notExpired) {
                            log.info("❌ EXPIRED OPTION: Strike={}, Expiry={}, Symbol={}", 
                                    strike, instrument.getExpiry(), instrument.getTradingSymbol());
                        } else {
                            log.info("❌ OUT OF RANGE: Strike={}, Range={}-{}, Symbol={}", 
                                    strike, range.minStrike, range.maxStrike, instrument.getTradingSymbol());
                        }
                        
                        return inRange && notExpired;
                    } catch (Exception e) {
                        log.info("❌ PARSE ERROR: Strike={}, Expiry={}, Error={}", 
                                instrument.getStrike(), instrument.getExpiry(), e.getMessage());
                        return false;
                    }
                })
                .sorted((i1, i2) -> {
                    try {
                        LocalDate expiry1 = LocalDate.parse(i1.getExpiry(), 
                                DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                        LocalDate expiry2 = LocalDate.parse(i2.getExpiry(), 
                                DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                        int expiryComparison = expiry1.compareTo(expiry2);
                        
                        if (expiryComparison != 0) {
                            return expiryComparison;
                        }
                        
                        int strike1 = Integer.parseInt(i1.getStrike());
                        int strike2 = Integer.parseInt(i2.getStrike());
                        return Integer.compare(strike1, strike2);
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .collect(Collectors.toList());
        
        log.info("🎯 VALID OPTIONS FOUND: {} out of {} total NIFTY {} options", 
                validOptions.size(), allNiftyOptions.size(), optionType);
        
        return validOptions;
    }

    /**
     * Get tick data for instruments
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
     * Calculate dynamic scores for all options
     */
    private List<DynamicStrikeScore> calculateDynamicScores(List<Instrument> instruments, 
                                                           Map<Long, Tick> tickDataMap,
                                                           double niftyIndexPrice, String optionType, String tradingStrategy) {
        List<DynamicStrikeScore> scores = new ArrayList<>();
        
        for (Instrument instrument : instruments) {
            Tick tick = tickDataMap.get(instrument.getInstrumentToken());
            if (tick == null) continue;
            
            DynamicStrikeScore score = calculateComprehensiveScore(instrument, tick, niftyIndexPrice, optionType, tradingStrategy);
            if (score != null) {
                scores.add(score);
            }
        }
        
        return scores;
    }

    /**
     * Calculate comprehensive score incorporating all factors
     */
    private DynamicStrikeScore calculateComprehensiveScore(Instrument instrument, Tick tick, 
                                                         double niftyIndexPrice, String optionType, String tradingStrategy) {
        try {
            int strikePrice = Integer.parseInt(instrument.getStrike());
            double optionPrice = tick.getLastTradedPrice();
            
            // Calculate Greeks
            double timeToExpiry = greeksCalculator.calculateTimeToExpiry(instrument.getExpiry());
            double impliedVolatility = greeksCalculator.estimateImpliedVolatility(
                    niftyIndexPrice, strikePrice, optionPrice, timeToExpiry, optionType);
            
            OptionGreeksCalculator.OptionGreeks greeks = greeksCalculator.calculateGreeks(
                    niftyIndexPrice, strikePrice, timeToExpiry, impliedVolatility, optionType, optionPrice);
            
            DynamicStrikeScore score = new DynamicStrikeScore();
            score.setInstrument(instrument);
            score.setGreeks(greeks);
            score.setStrikePrice(strikePrice);
            score.setOptionPrice(optionPrice);
            score.setTimeToExpiry(timeToExpiry);
            score.setImpliedVolatility(impliedVolatility);
            
            // Calculate market factors
            MarketFactors marketFactors = calculateMarketFactors(tick, greeks);
            score.setMarketFactors(marketFactors);
            
            // Calculate strategy-specific factors
            StrategyFactors strategyFactors = calculateStrategyFactors(greeks, tradingStrategy, strikePrice, niftyIndexPrice);
            score.setStrategyFactors(strategyFactors);
            
            // Calculate risk metrics
            RiskMetrics riskMetrics = calculateRiskMetrics(greeks, tick, timeToExpiry);
            score.setRiskMetrics(riskMetrics);
            
            // Calculate weighted total score based on strategy
            double totalScore = calculateWeightedScore(marketFactors, strategyFactors, riskMetrics, tradingStrategy);
            score.setTotalScore(totalScore);
            
            log.debug("📊 DYNAMIC SCORE - Symbol: {}, Strike: {}, Total: {:.2f}, Delta: {:.3f}, Theta: {:.3f}, IV: {:.2f}%",
                    instrument.getTradingSymbol(), strikePrice, totalScore, 
                    greeks.getDelta(), greeks.getTheta(), impliedVolatility * 100);
            
            return score;
            
        } catch (Exception e) {
            log.warn("Error calculating comprehensive score for instrument: {}", instrument.getTradingSymbol(), e);
            return null;
        }
    }

    /**
     * Calculate market factors (liquidity, volume, OI)
     */
    private MarketFactors calculateMarketFactors(Tick tick, OptionGreeksCalculator.OptionGreeks greeks) {
        double volumeScore = Math.min(100, Math.log10(Math.max(1, tick.getVolumeTradedToday())) * 20);
        double oiScore = Math.min(100, Math.log10(Math.max(1, tick.getOi())) * 20);
        double liquidityScore = calculateLiquidityScore(tick);
        double bidAskSpread = calculateBidAskSpread(tick);
        
        return MarketFactors.builder()
                .volumeScore(volumeScore)
                .oiScore(oiScore)
                .liquidityScore(liquidityScore)
                .bidAskSpread(bidAskSpread)
                .totalScore((volumeScore * 0.4) + (oiScore * 0.3) + (liquidityScore * 0.3))
                .build();
    }

    /**
     * Calculate strategy-specific factors
     */
    private StrategyFactors calculateStrategyFactors(OptionGreeksCalculator.OptionGreeks greeks, 
                                                   String tradingStrategy, int strikePrice, double niftyIndexPrice) {
        double deltaScore = calculateDeltaScore(greeks.getDelta(), tradingStrategy);
        double thetaScore = calculateThetaScore(greeks.getTheta(), tradingStrategy);
        double gammaScore = calculateGammaScore(greeks.getGamma(), tradingStrategy);
        double moneynessScore = calculateMoneynessScore(greeks.getMoneyness(), tradingStrategy);
        
        return StrategyFactors.builder()
                .deltaScore(deltaScore)
                .thetaScore(thetaScore)
                .gammaScore(gammaScore)
                .moneynessScore(moneynessScore)
                .totalScore((deltaScore * 0.3) + (thetaScore * 0.3) + (gammaScore * 0.2) + (moneynessScore * 0.2))
                .build();
    }

    /**
     * Calculate risk metrics
     */
    private RiskMetrics calculateRiskMetrics(OptionGreeksCalculator.OptionGreeks greeks, Tick tick, double timeToExpiry) {
        double timeDecayRisk = calculateTimeDecayRisk(greeks.getTheta(), timeToExpiry);
        double volatilityRisk = calculateVolatilityRisk(greeks.getVega());
        double priceRisk = calculatePriceRisk(tick.getLastTradedPrice());
        
        return RiskMetrics.builder()
                .timeDecayRisk(timeDecayRisk)
                .volatilityRisk(volatilityRisk)
                .priceRisk(priceRisk)
                .totalRisk((timeDecayRisk * 0.4) + (volatilityRisk * 0.3) + (priceRisk * 0.3))
                .build();
    }

    // Scoring helper methods
    private double calculateDeltaScore(double delta, String tradingStrategy) {
        double targetDelta;
        switch (tradingStrategy.toUpperCase()) {
            case "SCALPING":
                targetDelta = 0.3; // Moderate delta for scalping
                break;
            case "INTRADAY":
                targetDelta = 0.4; // Slightly higher delta for intraday
                break;
            case "SWING":
                targetDelta = 0.5; // Higher delta for swing trading
                break;
            default:
                targetDelta = 0.4;
        }
        
        double deltaDistance = Math.abs(delta - targetDelta);
        return Math.max(0, 100 - (deltaDistance * 200)); // Penalty for distance from target
    }

    private double calculateThetaScore(double theta, String tradingStrategy) {
        // For long positions, we want lower theta (less time decay)
        double thetaPenalty = Math.abs(theta) * 1000; // Scale theta appropriately
        
        switch (tradingStrategy.toUpperCase()) {
            case "SCALPING":
                return Math.max(0, 100 - thetaPenalty * 0.5); // Less penalty for scalping
            case "INTRADAY":
                return Math.max(0, 100 - thetaPenalty * 0.7);
            case "SWING":
                return Math.max(0, 100 - thetaPenalty * 1.0); // Higher penalty for swing
            default:
                return Math.max(0, 100 - thetaPenalty * 0.7);
        }
    }

    private double calculateGammaScore(double gamma, String tradingStrategy) {
        // Moderate gamma is preferred - not too high (risky) or too low (less responsive)
        double targetGamma = 0.001; // Target gamma value
        double gammaDistance = Math.abs(gamma - targetGamma);
        return Math.max(0, 100 - (gammaDistance * 50000)); // Scale appropriately
    }

    private double calculateMoneynessScore(double moneyness, String tradingStrategy) {
        // Optimal moneyness varies by strategy
        double targetMoneyness;
        switch (tradingStrategy.toUpperCase()) {
            case "SCALPING":
                targetMoneyness = 1.02; // Slightly OTM
                break;
            case "INTRADAY":
                targetMoneyness = 1.05; // More OTM
                break;
            case "SWING":
                targetMoneyness = 1.10; // Further OTM
                break;
            default:
                targetMoneyness = 1.05;
        }
        
        double moneynessDistance = Math.abs(moneyness - targetMoneyness);
        return Math.max(0, 100 - (moneynessDistance * 500));
    }

    private double calculateLiquidityScore(Tick tick) {
        double totalBuyQuantity = tick.getTotalBuyQuantity();
        double totalSellQuantity = tick.getTotalSellQuantity();
        
        if (totalBuyQuantity == 0 && totalSellQuantity == 0) return 0;
        
        double totalQuantity = totalBuyQuantity + totalSellQuantity;
        double liquidityRatio = Math.min(totalBuyQuantity, totalSellQuantity) / totalQuantity;
        double quantityScore = Math.min(100, totalQuantity / 500);
        double balanceScore = liquidityRatio * 100;
        
        return (quantityScore + balanceScore) / 2;
    }

    private double calculateBidAskSpread(Tick tick) {
        if (tick.getLastTradedPrice() == 0) return 0;
        // Simplified bid-ask spread calculation
        // In practice, you'd use actual bid/ask prices
        return 0.5; // Placeholder
    }

    private double calculateTimeDecayRisk(double theta, double timeToExpiry) {
        double dailyTheta = Math.abs(theta);
        double daysToExpiry = timeToExpiry;
        return Math.min(100, (dailyTheta * daysToExpiry) * 100);
    }

    private double calculateVolatilityRisk(double vega) {
        return Math.min(100, Math.abs(vega) * 1000);
    }

    private double calculatePriceRisk(double optionPrice) {
        if (optionPrice < 10) return 80; // High risk - low liquidity
        if (optionPrice > 200) return 70; // High risk - expensive
        if (optionPrice >= 20 && optionPrice <= 100) return 20; // Low risk
        return 50; // Medium risk
    }

    /**
     * Calculate weighted total score based on strategy
     */
    private double calculateWeightedScore(MarketFactors marketFactors, StrategyFactors strategyFactors, 
                                        RiskMetrics riskMetrics, String tradingStrategy) {
        double marketWeight, strategyWeight, riskWeight;
        
        switch (tradingStrategy.toUpperCase()) {
            case "SCALPING":
                marketWeight = 0.5;   // Market factors most important for scalping
                strategyWeight = 0.3;
                riskWeight = 0.2;
                break;
            case "INTRADAY":
                marketWeight = 0.4;
                strategyWeight = 0.4; // Balanced approach
                riskWeight = 0.2;
                break;
            case "SWING":
                marketWeight = 0.3;
                strategyWeight = 0.4;
                riskWeight = 0.3;     // Risk factors more important for swing
                break;
            default:
                marketWeight = 0.4;
                strategyWeight = 0.4;
                riskWeight = 0.2;
        }
        
        return (marketFactors.getTotalScore() * marketWeight) + 
               (strategyFactors.getTotalScore() * strategyWeight) + 
               ((100 - riskMetrics.getTotalRisk()) * riskWeight); // Invert risk (lower risk = higher score)
    }


    // Data classes
    @Data
    private static class StrikeRange {
        private final double minStrike;
        private final double maxStrike;
    }

    @Data
    @lombok.Builder
    public static class DynamicStrikeAnalysis {
        private Instrument instrument;
        private double totalScore;
        private OptionGreeksCalculator.OptionGreeks greeks;
        private MarketFactors marketFactors;
        private StrategyFactors strategyFactors;
        private RiskMetrics riskMetrics;
        private List<DynamicStrikeScore> allScores;
        private long analysisTimestamp;
    }

    @Data
    @lombok.Builder
    public static class MarketFactors {
        private double volumeScore;
        private double oiScore;
        private double liquidityScore;
        private double bidAskSpread;
        private double totalScore;
    }

    @Data
    @lombok.Builder
    public static class StrategyFactors {
        private double deltaScore;
        private double thetaScore;
        private double gammaScore;
        private double moneynessScore;
        private double totalScore;
    }

    @Data
    @lombok.Builder
    public static class RiskMetrics {
        private double timeDecayRisk;
        private double volatilityRisk;
        private double priceRisk;
        private double totalRisk;
    }

    @Data
    public static class DynamicStrikeScore {
        private Instrument instrument;
        private OptionGreeksCalculator.OptionGreeks greeks;
        private MarketFactors marketFactors;
        private StrategyFactors strategyFactors;
        private RiskMetrics riskMetrics;
        private int strikePrice;
        private double optionPrice;
        private double timeToExpiry;
        private double impliedVolatility;
        private double totalScore;
    }
}
