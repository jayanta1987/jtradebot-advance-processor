package com.jtradebot.processor.controller;

import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.service.price.GreeksAnalysisService;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for Greeks analysis API
 * Works in both local and live profiles
 */
@RestController
@RequestMapping("/api/greeks")
@RequiredArgsConstructor
@Slf4j
public class GreeksAnalysisController {

    private final GreeksAnalysisService greeksAnalysisService;
    private final TickDataManager tickDataManager;
    private final KiteInstrumentHandler kiteInstrumentHandler;

    /**
     * Get Greeks analysis for ±200 strike prices around current Nifty index
     * Works in both local and live profiles
     */
    @GetMapping("/analysis")
    public ResponseEntity<Map<String, Object>> getGreeksAnalysis(
            @RequestParam(defaultValue = "CE") String optionType) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get current Nifty index price
            Tick niftyTick = tickDataManager.getLastTick(String.valueOf(kiteInstrumentHandler.getNifty50Token()));
            if (niftyTick == null) {
                response.put("error", "No Nifty index data available");
                response.put("message", "Unable to get current Nifty index price");
                return ResponseEntity.badRequest().body(response);
            }

            double niftyIndexPrice = niftyTick.getLastTradedPrice();
            log.info("🔍 GREEKS ANALYSIS REQUEST - Index: {}, Type: {}", niftyIndexPrice, optionType);

            // Get Greeks analysis
            GreeksAnalysisService.GreeksAnalysisResult analysis = 
                    greeksAnalysisService.getGreeksAnalysis(niftyIndexPrice, optionType);

            // Build response
            response.put("niftyIndexPrice", niftyIndexPrice);
            response.put("optionType", optionType);
            response.put("strikeRange", analysis.getStrikeRange());
            response.put("totalStrikes", analysis.getTotalStrikes());
            response.put("timestamp", analysis.getTimestamp());
            response.put("strikes", analysis.getStrikes());
            
            if (analysis.getError() != null) {
                response.put("error", analysis.getError());
            }

            response.put("message", "Greeks analysis completed successfully");
            response.put("success", true);

            log.info("✅ GREEKS ANALYSIS COMPLETED - Found {} strikes for {} options", 
                    analysis.getTotalStrikes(), optionType);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error in Greeks analysis API", e);
            response.put("error", e.getMessage());
            response.put("message", "Error occurred while performing Greeks analysis");
            response.put("success", false);
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get Greeks analysis for both CALL and PUT options
     */
    @GetMapping("/analysis/both")
    public ResponseEntity<Map<String, Object>> getGreeksAnalysisBoth() {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get current Nifty index price
            Tick niftyTick = tickDataManager.getLastTick(String.valueOf(kiteInstrumentHandler.getNifty50Token()));
            if (niftyTick == null) {
                response.put("error", "No Nifty index data available");
                response.put("message", "Unable to get current Nifty index price");
                return ResponseEntity.badRequest().body(response);
            }

            double niftyIndexPrice = niftyTick.getLastTradedPrice();
            log.info("🔍 GREEKS ANALYSIS REQUEST - Index: {}, Both CE and PE", niftyIndexPrice);

            // Get Greeks analysis for both option types
            GreeksAnalysisService.GreeksAnalysisResult callAnalysis = 
                    greeksAnalysisService.getGreeksAnalysis(niftyIndexPrice, "CE");
            
            GreeksAnalysisService.GreeksAnalysisResult putAnalysis = 
                    greeksAnalysisService.getGreeksAnalysis(niftyIndexPrice, "PE");

            // Build response
            response.put("niftyIndexPrice", niftyIndexPrice);
            response.put("timestamp", System.currentTimeMillis());
            
            // CALL options data
            Map<String, Object> callData = new HashMap<>();
            callData.put("optionType", "CE");
            callData.put("strikeRange", callAnalysis.getStrikeRange());
            callData.put("totalStrikes", callAnalysis.getTotalStrikes());
            callData.put("strikes", callAnalysis.getStrikes());
            if (callAnalysis.getError() != null) {
                callData.put("error", callAnalysis.getError());
            }
            response.put("callOptions", callData);

            // PUT options data
            Map<String, Object> putData = new HashMap<>();
            putData.put("optionType", "PE");
            putData.put("strikeRange", putAnalysis.getStrikeRange());
            putData.put("totalStrikes", putAnalysis.getTotalStrikes());
            putData.put("strikes", putAnalysis.getStrikes());
            if (putAnalysis.getError() != null) {
                putData.put("error", putAnalysis.getError());
            }
            response.put("putOptions", putData);

            response.put("message", "Greeks analysis completed for both CALL and PUT options");
            response.put("success", true);

            log.info("✅ GREEKS ANALYSIS COMPLETED - CE: {} strikes, PE: {} strikes", 
                    callAnalysis.getTotalStrikes(), putAnalysis.getTotalStrikes());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error in Greeks analysis API for both options", e);
            response.put("error", e.getMessage());
            response.put("message", "Error occurred while performing Greeks analysis for both options");
            response.put("success", false);
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get best strike price for scalping (FAST - uses cached results)
     * Only recalculates if index price changed by ≥10 points
     */
    @GetMapping("/best-strike")
    public ResponseEntity<Map<String, Object>> getBestStrikeForScalping(
            @RequestParam(defaultValue = "CE") String optionType) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            log.info("🚀 FAST BEST STRIKE REQUEST - Type: {}", optionType);

            // Get cached best strike (fast!)
            GreeksAnalysisService.BestStrikeResult result = 
                    greeksAnalysisService.getBestStrikeForScalping(optionType);

            if (!result.isSuccess()) {
                response.put("error", result.getError());
                response.put("message", "Unable to get best strike for scalping");
                response.put("success", false);
                return ResponseEntity.badRequest().body(response);
            }

            GreeksAnalysisService.StrikeGreeksData bestStrike = result.getBestStrike();
            double niftyIndexPrice = result.getNiftyIndexPrice();

            // Build response
            response.put("niftyIndexPrice", niftyIndexPrice);
            response.put("optionType", optionType);
            response.put("timestamp", result.getTimestamp());
            response.put("cached", true); // Indicates this is from cache
            
            if (bestStrike != null) {
                // Build best strike recommendation
                Map<String, Object> bestStrikeData = new HashMap<>();
                bestStrikeData.put("tradingSymbol", bestStrike.getTradingSymbol());
                bestStrikeData.put("strikePrice", bestStrike.getStrikePrice());
                bestStrikeData.put("expiry", bestStrike.getExpiry());
                bestStrikeData.put("instrumentToken", bestStrike.getInstrumentToken());
                bestStrikeData.put("optionPrice", bestStrike.getOptionPrice());
                bestStrikeData.put("lastTradedPrice", bestStrike.getLastTradedPrice());
                bestStrikeData.put("greeks", bestStrike.getGreeks());
                bestStrikeData.put("timeToExpiry", bestStrike.getTimeToExpiry());
                bestStrikeData.put("impliedVolatility", bestStrike.getImpliedVolatility());
                
                // Add scalping analysis
                Map<String, Object> scalpingAnalysis = analyzeStrikeForScalping(bestStrike, niftyIndexPrice, optionType);
                bestStrikeData.put("scalpingAnalysis", scalpingAnalysis);
                
                response.put("bestStrike", bestStrikeData);
                response.put("found", true);

            } else {
                response.put("bestStrike", null);
                response.put("found", false);
                response.put("message", "No suitable strike found for scalping");
            }

            response.put("message", "Best strike retrieved successfully (cached)");
            response.put("success", true);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error in fast best strike API", e);
            response.put("error", e.getMessage());
            response.put("message", "Error occurred while getting best strike for scalping");
            response.put("success", false);
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Find the best strike for scalping based on multiple criteria
     */
    private GreeksAnalysisService.StrikeGreeksData findBestStrikeForScalping(
            List<GreeksAnalysisService.StrikeGreeksData> strikes, double niftyIndexPrice, String optionType) {
        
        if (strikes.isEmpty()) {
            return null;
        }

        GreeksAnalysisService.StrikeGreeksData bestStrike = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (GreeksAnalysisService.StrikeGreeksData strike : strikes) {
            double score = calculateScalpingScore(strike, niftyIndexPrice, optionType);
            
            if (score > bestScore) {
                bestScore = score;
                bestStrike = strike;
            }
        }

        return bestStrike;
    }

    /**
     * Calculate scalping score for a strike based on multiple factors
     */
    private double calculateScalpingScore(GreeksAnalysisService.StrikeGreeksData strike, double niftyIndexPrice, String optionType) {
        try {
            double delta = strike.getGreeks().getDelta();
            double optionPrice = strike.getOptionPrice();
            double impliedVolatility = strike.getImpliedVolatility();
            double theta = strike.getGreeks().getTheta();
            int strikePrice = strike.getStrikePrice();
            
            // Calculate moneyness (how close to ATM)
            double moneyness = Math.abs(niftyIndexPrice - strikePrice) / niftyIndexPrice;
            
            // Score components (0-100 each)
            double deltaScore = calculateDeltaScore(Math.abs(delta), optionType); // Use absolute delta for scoring
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
            
            log.debug("📊 SCALPING SCORE - Strike: {}, Delta: {:.3f}, Price: {:.2f}, Score: {:.2f} (D:{:.1f}, P:{:.1f}, M:{:.1f}, IV:{:.1f}, T:{:.1f})",
                    strike.getStrikePrice(), delta, optionPrice, totalScore,
                    deltaScore, priceScore, moneynessScore, ivScore, thetaScore);
            
            return totalScore;
            
        } catch (Exception e) {
            log.warn("Error calculating scalping score for strike: {}", strike.getStrikePrice(), e);
            return 0.0;
        }
    }

    /**
     * Calculate delta score (0-100) - prefer 0.45-0.65 range
     */
    private double calculateDeltaScore(double absDelta, String optionType) {
        if (absDelta < 0.3 || absDelta > 0.7) return 0; // Too low or too high
        if (absDelta >= 0.45 && absDelta <= 0.65) return 100; // Perfect range
        if (absDelta >= 0.4 && absDelta <= 0.7) return 80; // Good range
        return 50; // Acceptable range
    }

    /**
     * Calculate price score (0-100) - prefer reasonable prices
     */
    private double calculatePriceScore(double optionPrice) {
        if (optionPrice < 50) return 0; // Too cheap, low liquidity
        if (optionPrice > 500) return 0; // Too expensive
        if (optionPrice >= 150 && optionPrice <= 300) return 100; // Perfect range
        if (optionPrice >= 100 && optionPrice <= 400) return 80; // Good range
        return 60; // Acceptable range
    }

    /**
     * Calculate moneyness score (0-100) - prefer ATM options
     */
    private double calculateMoneynessScore(double moneyness) {
        if (moneyness <= 0.005) return 100; // Very close to ATM (0.5%)
        if (moneyness <= 0.01) return 90;   // Close to ATM (1%)
        if (moneyness <= 0.02) return 70;   // Near ATM (2%)
        if (moneyness <= 0.03) return 50;   // Somewhat away (3%)
        return 20; // Far from ATM
    }

    /**
     * Calculate IV score (0-100) - prefer moderate IV
     */
    private double calculateIVScore(double impliedVolatility) {
        double ivPercent = impliedVolatility * 100;
        if (ivPercent < 10) return 60; // Low IV
        if (ivPercent >= 12 && ivPercent <= 18) return 100; // Perfect range
        if (ivPercent >= 10 && ivPercent <= 20) return 80; // Good range
        if (ivPercent > 25) return 40; // High IV (overpriced)
        return 60; // Acceptable range
    }

    /**
     * Calculate theta score (0-100) - prefer manageable time decay
     */
    private double calculateThetaScore(double theta) {
        double absTheta = Math.abs(theta);
        if (absTheta < 3000) return 100; // Low time decay
        if (absTheta < 5000) return 80;  // Moderate time decay
        if (absTheta < 7000) return 60;  // High but manageable
        return 40; // Very high time decay
    }

    /**
     * Calculate option position (ITM/OTM/ATM) based on option type
     */
    private String calculateOptionPosition(int strikePrice, double indexPrice, String optionType) {
        if (Math.abs(strikePrice - indexPrice) / indexPrice < 0.01) { // Within 1%
            return "ATM";
        }
        
        if ("CE".equals(optionType)) {
            // For CALL options: ITM when Strike < Index, OTM when Strike > Index
            return strikePrice < indexPrice ? "ITM" : "OTM";
        } else {
            // For PUT options: ITM when Strike > Index, OTM when Strike < Index
            return strikePrice > indexPrice ? "ITM" : "OTM";
        }
    }

    /**
     * Analyze strike for scalping characteristics
     */
    private Map<String, Object> analyzeStrikeForScalping(GreeksAnalysisService.StrikeGreeksData strike, double niftyIndexPrice, String optionType) {
        Map<String, Object> analysis = new HashMap<>();
        
        double delta = strike.getGreeks().getDelta();
        double optionPrice = strike.getOptionPrice();
        double theta = strike.getGreeks().getTheta();
        int strikePrice = strike.getStrikePrice();
        
        // Calculate characteristics
        double moneyness = Math.abs(niftyIndexPrice - strikePrice) / niftyIndexPrice;
        String position = calculateOptionPosition(strikePrice, niftyIndexPrice, optionType);
        
        // Scalping recommendations
        double absDelta = Math.abs(delta);
        String sensitivity = absDelta > 0.6 ? "High" : (absDelta > 0.4 ? "Moderate" : "Low");
        String timeDecay = Math.abs(theta) > 6000 ? "High" : (Math.abs(theta) > 4000 ? "Moderate" : "Low");
        String riskLevel = optionPrice > 300 ? "High" : (optionPrice > 150 ? "Moderate" : "Low");
        
        analysis.put("position", position);
        analysis.put("moneyness", String.format("%.2f%%", moneyness * 100));
        analysis.put("sensitivity", sensitivity);
        analysis.put("timeDecay", timeDecay);
        analysis.put("riskLevel", riskLevel);
        analysis.put("recommendedHoldTime", getRecommendedHoldTime(absDelta, theta));
        analysis.put("expectedMove", String.format("%.1f%% of index movement", absDelta * 100));
        
        return analysis;
    }

    /**
     * Get recommended hold time based on delta and theta
     */
    private String getRecommendedHoldTime(double absDelta, double theta) {
        if (absDelta > 0.6 && Math.abs(theta) > 6000) {
            return "5-15 minutes (High sensitivity, high time decay)";
        } else if (absDelta > 0.4 && Math.abs(theta) > 4000) {
            return "10-30 minutes (Moderate sensitivity, moderate time decay)";
        } else {
            return "15-45 minutes (Lower sensitivity, manageable time decay)";
        }
    }
}
