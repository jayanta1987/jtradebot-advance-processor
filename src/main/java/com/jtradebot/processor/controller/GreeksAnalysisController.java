package com.jtradebot.processor.controller;

import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.service.price.OIAnalysisService;
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

    private final OIAnalysisService oiAnalysisService;
    private final TickDataManager tickDataManager;
    private final KiteInstrumentHandler kiteInstrumentHandler;

    /**
     * DEPRECATED: Greeks analysis endpoint - Use OI-based selection instead
     * This endpoint is kept for backward compatibility but returns a deprecation message
     */
    @GetMapping("/analysis")
    public ResponseEntity<Map<String, Object>> getGreeksAnalysis(
            @RequestParam(defaultValue = "CE") String optionType) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("deprecated", true);
        response.put("message", "Greeks analysis endpoint is deprecated. Use /api/greeks/best-strike for OI-based strike selection.");
        response.put("success", false);
        return ResponseEntity.ok(response);
    }

    /**
     * DEPRECATED: Greeks analysis endpoint for both options - Use OI-based selection instead
     * This endpoint is kept for backward compatibility but returns a deprecation message
     */
    @GetMapping("/analysis/both")
    public ResponseEntity<Map<String, Object>> getGreeksAnalysisBoth() {
        
        Map<String, Object> response = new HashMap<>();
        response.put("deprecated", true);
        response.put("message", "Greeks analysis endpoint is deprecated. Use /api/greeks/best-strike for OI-based strike selection.");
        response.put("success", false);
        return ResponseEntity.ok(response);
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
            OIAnalysisService.BestStrikeResult result = 
                    oiAnalysisService.getBestStrikeForScalping(optionType);

            if (!result.isSuccess()) {
                response.put("error", result.getError());
                response.put("message", "Unable to get best strike for scalping");
                response.put("success", false);
                return ResponseEntity.badRequest().body(response);
            }

            OIAnalysisService.StrikeOIData bestStrike = result.getBestStrike();
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
                bestStrikeData.put("oi", bestStrike.getOi());
                bestStrikeData.put("volume", bestStrike.getVolume());
                bestStrikeData.put("bid", bestStrike.getBid());
                bestStrikeData.put("ask", bestStrike.getAsk());
                // Note: Greeks data (Delta, Gamma, Theta, Vega, IV) not available with OI-based selection
                
                // Add scalping analysis (simplified - no Greeks)
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

    // Note: Helper methods for Greeks-based scoring removed - OI-based selection handles scoring internally

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
     * Analyze strike for scalping characteristics (OI-based, no Greeks)
     */
    private Map<String, Object> analyzeStrikeForScalping(OIAnalysisService.StrikeOIData strike, double niftyIndexPrice, String optionType) {
        Map<String, Object> analysis = new HashMap<>();
        
        double optionPrice = strike.getOptionPrice();
        double oi = strike.getOi();
        long volume = strike.getVolume() != null ? strike.getVolume() : 0L;
        int strikePrice = strike.getStrikePrice();
        
        // Calculate characteristics
        double moneyness = Math.abs(niftyIndexPrice - strikePrice) / niftyIndexPrice;
        String position = calculateOptionPosition(strikePrice, niftyIndexPrice, optionType);
        
        // Scalping recommendations based on OI data
        String liquidity = oi > 500000 ? "High" : (oi > 200000 ? "Moderate" : "Low");
        String activity = volume > 50000 ? "High" : (volume > 20000 ? "Moderate" : "Low");
        String riskLevel = optionPrice > 300 ? "High" : (optionPrice > 150 ? "Moderate" : "Low");
        
        // Calculate spread if available
        double spread = 0.0;
        if (strike.getBid() != null && strike.getAsk() != null) {
            spread = (strike.getAsk() - strike.getBid()) / optionPrice * 100;
        }
        String spreadQuality = spread <= 1.0 ? "Excellent" : (spread <= 2.0 ? "Good" : (spread <= 3.0 ? "Fair" : "Wide"));
        
        analysis.put("position", position);
        analysis.put("moneyness", String.format("%.2f%%", moneyness * 100));
        analysis.put("liquidity", liquidity);
        analysis.put("activity", activity);
        analysis.put("riskLevel", riskLevel);
        analysis.put("spread", String.format("%.2f%%", spread));
        analysis.put("spreadQuality", spreadQuality);
        analysis.put("oi", oi);
        analysis.put("volume", volume);
        analysis.put("recommendedHoldTime", getRecommendedHoldTime(oi, volume, moneyness));
        
        return analysis;
    }

    /**
     * Get recommended hold time based on OI, volume, and moneyness
     */
    private String getRecommendedHoldTime(double oi, long volume, double moneyness) {
        if (oi > 500000 && volume > 50000 && moneyness <= 0.01) {
            return "5-15 minutes (High liquidity, active trading, ATM)";
        } else if (oi > 200000 && volume > 20000 && moneyness <= 0.02) {
            return "10-30 minutes (Moderate liquidity, moderate activity, near ATM)";
        } else {
            return "15-45 minutes (Lower liquidity or further from ATM)";
        }
    }
}
