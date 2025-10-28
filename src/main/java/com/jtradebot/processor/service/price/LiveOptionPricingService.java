package com.jtradebot.processor.service.price;

import com.jtradebot.processor.common.ProfileUtil;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.repository.document.Instrument;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LiveOptionPricingService {

    private final TickDataManager tickDataManager;
    private final Environment environment;
    private final KiteConnect kiteConnect;
    private final KiteInstrumentHandler kiteInstrumentHandler;
    private final GreeksAnalysisService greeksAnalysisService;

    /**
     * Get live option pricing information for live profile
     */
    public Optional<LiveOptionPricingInfo> getLiveOptionPricing(String orderType) throws KiteException {
        log.debug("Starting live option pricing for order type: {}", orderType);
        long startTime = System.currentTimeMillis();
        
        try {
            // Check if we're in live profile
            if (!ProfileUtil.isProfileActive(environment, "live")) {
                log.debug("Not in live profile, returning empty pricing info");
                return Optional.empty();
            }

            // Get current Nifty index price
            Tick niftyTick = tickDataManager.getLastTick(String.valueOf(kiteInstrumentHandler.getNifty50Token()));
            if (niftyTick == null) {
                log.warn("No Nifty index data available for live option pricing");
                return Optional.empty();
            }

            double niftyIndexPrice = niftyTick.getLastTradedPrice();
            String optionType = "CALL_BUY".equals(orderType) ? "CE" : "PE";
            log.info("Using Greeks analysis for strike selection - Index Price: {}, Option Type: {}", niftyIndexPrice, optionType);
            
            GreeksAnalysisService.BestStrikeResult bestStrikeResult = greeksAnalysisService.getBestStrikeForScalping(optionType);
            
            if (!bestStrikeResult.isSuccess() || bestStrikeResult.getBestStrike() == null) {
                log.error("Greeks analysis failed - Cannot proceed with order entry. Error: {}", 
                        bestStrikeResult.getError());
                return Optional.empty();
            }

            // Use Greeks analysis result - it already contains all required details
            GreeksAnalysisService.StrikeGreeksData bestStrike = bestStrikeResult.getBestStrike();
            log.info("Greeks analysis successful - Symbol: {}, Strike: {}, Delta: %.3f, Price: %.2f, IV: %.2f%%", 
                    bestStrike.getTradingSymbol(), bestStrike.getStrikePrice(), 
                    bestStrike.getGreeks().getDelta(), bestStrike.getOptionPrice(), 
                    bestStrike.getImpliedVolatility() * 100);
            
            // Get live LTP from Kite API for real-time pricing
            log.debug("Fetching live LTP from Kite API - Token: {}, Symbol: {}", 
                    bestStrike.getInstrumentToken(), bestStrike.getTradingSymbol());
            
            String instrumentToken = String.valueOf(bestStrike.getInstrumentToken());
            double liveLTP = kiteConnect.getLTP(new String[]{instrumentToken}).get(instrumentToken).lastPrice;
            
            log.debug("Live LTP retrieved - Token: {}, Symbol: {}, Live LTP: %.2f, Calculated: %.2f",
                    bestStrike.getInstrumentToken(), bestStrike.getTradingSymbol(), liveLTP, bestStrike.getOptionPrice());
            
            // Create instrument object from Greeks data
            Instrument instrument = new Instrument();
            instrument.setInstrumentToken(bestStrike.getInstrumentToken());
            instrument.setTradingSymbol(bestStrike.getTradingSymbol());
            instrument.setStrike(String.valueOf(bestStrike.getStrikePrice()));
            instrument.setExpiry(bestStrike.getExpiry());
            instrument.setInstrumentType(optionType);
            
            // Use live LTP for order entry, but keep calculated price for reference
            double optionLTP = liveLTP;
            int strikePrice = bestStrike.getStrikePrice();

            LiveOptionPricingInfo pricingInfo = LiveOptionPricingInfo.builder()
                    .optionInstrument(instrument)
                    .optionLTP(optionLTP)
                    .niftyIndexPrice(niftyIndexPrice)
                    .strikePrice(strikePrice)
                    .optionType(optionType)
                    .orderType(orderType)
                    .build();

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Live option pricing completed in {}ms - Symbol: {}, LTP: {}, Strike: {}, Index: {}, Type: {}, Delta: %.3f, IV: %.2f%%", 
                    executionTime, instrument.getTradingSymbol(), optionLTP, strikePrice, niftyIndexPrice, optionType,
                    bestStrike.getGreeks().getDelta(), bestStrike.getImpliedVolatility() * 100);

            return Optional.of(pricingInfo);

        } catch (KiteException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("KiteException getting live option pricing for order type: {} after {}ms - Error: {}", orderType, executionTime, e.getMessage(), e);
            return Optional.empty();
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Unexpected error getting live option pricing for order type: {} after {}ms - Error: {}", orderType, executionTime, e.getMessage(), e);
            return Optional.empty();
        }
    }

    
    /**
     * Get Greeks-enhanced option pricing for order entry
     * Uses comprehensive Greeks analysis for optimal strike selection
     * This is a simplified version that leverages the main getLiveOptionPricing method
     */
    public Optional<GreeksEnhancedPricingInfo> getGreeksEnhancedPricing(String orderType) throws KiteException {
        log.debug("Starting Greeks-enhanced pricing for order type: {}", orderType);
        long startTime = System.currentTimeMillis();
        
        try {
            // Check if we're in live profile
            if (!ProfileUtil.isProfileActive(environment, "live")) {
                log.debug("Not in live profile, returning empty enhanced pricing info");
                return Optional.empty();
            }

            // Get the basic live option pricing (which now uses Greeks analysis)
            Optional<LiveOptionPricingInfo> livePricing = getLiveOptionPricing(orderType);
            if (livePricing.isEmpty()) {
                log.warn("Live option pricing failed - Cannot provide enhanced pricing");
                return Optional.empty();
            }

            LiveOptionPricingInfo liveInfo = livePricing.get();
            
            // Get additional Greeks data from the Greeks analysis service
            String optionType = "CALL_BUY".equals(orderType) ? "CE" : "PE";
            GreeksAnalysisService.BestStrikeResult bestStrikeResult = greeksAnalysisService.getBestStrikeForScalping(optionType);
            
            if (!bestStrikeResult.isSuccess() || bestStrikeResult.getBestStrike() == null) {
                log.warn("Greeks analysis failed - Using basic pricing info only");
                return Optional.empty();
            }

            GreeksAnalysisService.StrikeGreeksData bestStrike = bestStrikeResult.getBestStrike();
            
            // Create enhanced pricing info with all Greeks data
            GreeksEnhancedPricingInfo pricingInfo = GreeksEnhancedPricingInfo.builder()
                    .tradingSymbol(bestStrike.getTradingSymbol())
                    .instrumentToken(bestStrike.getInstrumentToken())
                    .strikePrice(bestStrike.getStrikePrice())
                    .expiry(bestStrike.getExpiry())
                    .optionType(optionType)
                    .orderType(orderType)
                    .liveLTP(liveInfo.getOptionLTP())
                    .calculatedPrice(bestStrike.getOptionPrice())
                    .niftyIndexPrice(liveInfo.getNiftyIndexPrice())
                    .greeks(bestStrike.getGreeks())
                    .impliedVolatility(bestStrike.getImpliedVolatility())
                    .timeToExpiry(bestStrike.getTimeToExpiry())
                    .scalpingAnalysis(null) // Not available in current structure
                    .isCached(false) // Always false for live pricing
                    .build();

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Greeks-enhanced pricing completed in {}ms - Symbol: {}, Strike: {}, Live LTP: %.2f, Delta: %.3f, IV: %.2f%%", 
                    executionTime, bestStrike.getTradingSymbol(), bestStrike.getStrikePrice(), liveInfo.getOptionLTP(),
                    bestStrike.getGreeks().getDelta(), bestStrike.getImpliedVolatility() * 100);

            return Optional.of(pricingInfo);

        } catch (KiteException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("KiteException getting Greeks-enhanced pricing for order type: {} after {}ms - Error: {}", orderType, executionTime, e.getMessage(), e);
            return Optional.empty();
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Unexpected error getting Greeks-enhanced pricing for order type: {} after {}ms - Error: {}", orderType, executionTime, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Get current price for a specific order (profile-aware)
     * @param instrumentToken The instrument token of the order
     * @return Current price from KiteConnect (live profile) or calculated price (local profile)
     */
    public Double getCurrentPrice(Long instrumentToken) {
        log.debug("Getting current price for instrument token: {}", instrumentToken);
        
        try {
            if (ProfileUtil.isProfileActive(environment, "live") && instrumentToken != null && instrumentToken > 0) {
                // Get real option LTP for live profile using Kite Connect API
                String token = String.valueOf(instrumentToken);
                double realLTP = kiteConnect.getLTP(new String[]{token}).get(token).lastPrice;
                
                log.debug("Real option LTP from Kite API - Token: {}, LTP: %.2f", instrumentToken, realLTP);
                return realLTP;
            } else {
                // For local profile, return null to indicate calculated price should be used
                log.debug("Local profile - Using calculated price for token: {}", instrumentToken);
                return null;
            }
        } catch (KiteException e) {
            log.error("KiteException getting current price for token: {} - Error: {}", instrumentToken, e.getMessage(), e);
            return null; // Fallback to calculated price
        } catch (Exception e) {
            log.error("Unexpected error getting current price for token: {} - Error: {}", instrumentToken, e.getMessage(), e);
            return null; // Fallback to calculated price
        }
    }



    public Double calculateProfitLoss(Double entryPrice, Double exitPrice) {
        log.debug("Calculating profit/loss - Entry: {}, Exit: {}", entryPrice, exitPrice);
        
        if (entryPrice == null || exitPrice == null) {
            log.warn("Invalid parameters for profit/loss calculation - Entry: {}, Exit: {}",
                    entryPrice, exitPrice);
            return 0.0;
        }
        
        double profitLoss = exitPrice - entryPrice;
        log.debug("Profit/Loss calculated: %.2f", profitLoss);
        return profitLoss;
    }

    /**
     * Data class to hold live option pricing information
     */
    @lombok.Data
    @lombok.Builder
    public static class LiveOptionPricingInfo {
        private Instrument optionInstrument;
        private double optionLTP;
        private double niftyIndexPrice;
        private int strikePrice;
        private String optionType;
        private String orderType;
    }

    /**
     * Enhanced data class with Greeks analysis for optimal order entry
     */
    @lombok.Data
    @lombok.Builder
    public static class GreeksEnhancedPricingInfo {
        private String tradingSymbol;
        private Long instrumentToken;
        private int strikePrice;
        private String expiry;
        private String optionType;
        private String orderType;
        private double liveLTP;
        private double calculatedPrice;
        private double niftyIndexPrice;
        private OptionGreeksCalculator.OptionGreeks greeks;
        private double impliedVolatility;
        private double timeToExpiry;
        private Object scalpingAnalysis; // Can be null if not available
        private boolean isCached;
    }
}
