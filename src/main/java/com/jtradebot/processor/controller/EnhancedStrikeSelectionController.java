package com.jtradebot.processor.controller;

import com.jtradebot.processor.common.ProfileUtil;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.service.price.EnhancedStrikeSelectionService;
import com.jtradebot.processor.service.price.DynamicStrikeSelectionService;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/enhanced-strike")
@RequiredArgsConstructor
@Slf4j
public class EnhancedStrikeSelectionController {

    private final EnhancedStrikeSelectionService enhancedStrikeSelectionService;
    private final DynamicStrikeSelectionService dynamicStrikeSelectionService;
    private final TickDataManager tickDataManager;
    private final KiteInstrumentHandler kiteInstrumentHandler;
    private final Environment environment;
    private final KiteConnect kiteConnect;

    /**
     * Get best strike for both CALL and PUT options within ±100 range
     * Only works in live profile
     */
    @GetMapping("/best-strikes")
    public ResponseEntity<Map<String, Object>> getBestStrikes() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Check if we're in live profile
            if (!ProfileUtil.isProfileActive(environment, "live")) {
                response.put("error", "This API only works in live profile");
                response.put("message", "Please switch to live profile to use enhanced strike selection");
                return ResponseEntity.badRequest().body(response);
            }

            // Get current Nifty index price
            Tick niftyTick = tickDataManager.getLastTick(String.valueOf(kiteInstrumentHandler.getNifty50Token()));
            if (niftyTick == null) {
                response.put("error", "No Nifty index data available");
                response.put("message", "Unable to get current Nifty index price");
                return ResponseEntity.badRequest().body(response);
            }

            double niftyIndexPrice = niftyTick.getLastTradedPrice();
            response.put("niftyIndexPrice", niftyIndexPrice);
            response.put("timestamp", System.currentTimeMillis());

            // Get best CALL option
            log.info("🔍 FINDING BEST CALL STRIKE - Index Price: {}", niftyIndexPrice);
            var callOption = enhancedStrikeSelectionService.findBestStrikeInRange(niftyIndexPrice, "CE");
            
            Map<String, Object> callResult = new HashMap<>();
            if (callOption.isPresent()) {
                var instrument = callOption.get();
                callResult.put("tradingSymbol", instrument.getTradingSymbol());
                callResult.put("strikePrice", instrument.getStrike());
                callResult.put("expiry", instrument.getExpiry());
                callResult.put("instrumentToken", instrument.getInstrumentToken());
                
                // Get LTP for the CALL option
                try {
                    String callToken = String.valueOf(instrument.getInstrumentToken());
                    double callLTP = kiteConnect.getLTP(new String[]{callToken}).get(callToken).lastPrice;
                    callResult.put("ltp", callLTP);
                    log.info("✅ BEST CALL STRIKE FOUND - Symbol: {}, Strike: {}, LTP: {}", 
                            instrument.getTradingSymbol(), instrument.getStrike(), callLTP);
                } catch (KiteException e) {
                    log.warn("Could not get LTP for CALL option: {}", instrument.getTradingSymbol(), e);
                    callResult.put("ltp", null);
                    callResult.put("ltpError", "Could not fetch LTP: " + e.getMessage());
                    log.info("✅ BEST CALL STRIKE FOUND - Symbol: {}, Strike: {} (LTP unavailable)", 
                            instrument.getTradingSymbol(), instrument.getStrike());
                } catch (Exception e) {
                    log.warn("Unexpected error getting LTP for CALL option: {}", instrument.getTradingSymbol(), e);
                    callResult.put("ltp", null);
                    callResult.put("ltpError", "Unexpected error: " + e.getMessage());
                    log.info("✅ BEST CALL STRIKE FOUND - Symbol: {}, Strike: {} (LTP unavailable)", 
                            instrument.getTradingSymbol(), instrument.getStrike());
                }
                
                callResult.put("found", true);
            } else {
                callResult.put("found", false);
                callResult.put("message", "No suitable CALL option found in ±100 range");
                log.warn("❌ NO CALL OPTION FOUND");
            }
            response.put("callOption", callResult);

            // Get best PUT option
            log.info("🔍 FINDING BEST PUT STRIKE - Index Price: {}", niftyIndexPrice);
            var putOption = enhancedStrikeSelectionService.findBestStrikeInRange(niftyIndexPrice, "PE");
            
            Map<String, Object> putResult = new HashMap<>();
            if (putOption.isPresent()) {
                var instrument = putOption.get();
                putResult.put("tradingSymbol", instrument.getTradingSymbol());
                putResult.put("strikePrice", instrument.getStrike());
                putResult.put("expiry", instrument.getExpiry());
                putResult.put("instrumentToken", instrument.getInstrumentToken());
                
                // Get LTP for the PUT option
                try {
                    String putToken = String.valueOf(instrument.getInstrumentToken());
                    double putLTP = kiteConnect.getLTP(new String[]{putToken}).get(putToken).lastPrice;
                    putResult.put("ltp", putLTP);
                    log.info("✅ BEST PUT STRIKE FOUND - Symbol: {}, Strike: {}, LTP: {}", 
                            instrument.getTradingSymbol(), instrument.getStrike(), putLTP);
                } catch (KiteException e) {
                    log.warn("Could not get LTP for PUT option: {}", instrument.getTradingSymbol(), e);
                    putResult.put("ltp", null);
                    putResult.put("ltpError", "Could not fetch LTP: " + e.getMessage());
                    log.info("✅ BEST PUT STRIKE FOUND - Symbol: {}, Strike: {} (LTP unavailable)", 
                            instrument.getTradingSymbol(), instrument.getStrike());
                } catch (Exception e) {
                    log.warn("Unexpected error getting LTP for PUT option: {}", instrument.getTradingSymbol(), e);
                    putResult.put("ltp", null);
                    putResult.put("ltpError", "Unexpected error: " + e.getMessage());
                    log.info("✅ BEST PUT STRIKE FOUND - Symbol: {}, Strike: {} (LTP unavailable)", 
                            instrument.getTradingSymbol(), instrument.getStrike());
                }
                
                putResult.put("found", true);
            } else {
                putResult.put("found", false);
                putResult.put("message", "No suitable PUT option found in ±100 range");
                log.warn("❌ NO PUT OPTION FOUND");
            }
            response.put("putOption", putResult);

            response.put("message", "Best strikes analysis completed");
            response.put("success", true);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error in getBestStrikes API", e);
            response.put("error", e.getMessage());
            response.put("message", "Error occurred while finding best strikes");
            response.put("success", false);
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get optimal strike using advanced dynamic analysis with Greeks
     * Only works in live profile
     */
    @GetMapping("/optimal-strikes")
    public ResponseEntity<Map<String, Object>> getOptimalStrikes(@RequestParam(defaultValue = "SCALPING") String strategy) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Check if we're in live profile
            if (!ProfileUtil.isProfileActive(environment, "live")) {
                response.put("error", "This API only works in live profile");
                response.put("message", "Please switch to live profile to use dynamic strike selection");
                return ResponseEntity.badRequest().body(response);
            }

            // Get current Nifty index price
            Tick niftyTick = tickDataManager.getLastTick(String.valueOf(kiteInstrumentHandler.getNifty50Token()));
            if (niftyTick == null) {
                response.put("error", "No Nifty index data available");
                response.put("message", "Unable to get current Nifty index price");
                return ResponseEntity.badRequest().body(response);
            }

            double niftyIndexPrice = niftyTick.getLastTradedPrice();
            response.put("niftyIndexPrice", niftyIndexPrice);
            response.put("strategy", strategy);
            response.put("timestamp", System.currentTimeMillis());

            // Get optimal CALL option with advanced analysis
            log.info("🔍 FINDING OPTIMAL CALL STRIKE - Index Price: {}, Strategy: {}", niftyIndexPrice, strategy);
            var callAnalysis = dynamicStrikeSelectionService.findOptimalStrike(niftyIndexPrice, "CE", strategy);
            
            Map<String, Object> callResult = new HashMap<>();
            if (callAnalysis.isPresent()) {
                var analysis = callAnalysis.get();
                var instrument = analysis.getInstrument();
                
                callResult.put("tradingSymbol", instrument.getTradingSymbol());
                callResult.put("strikePrice", instrument.getStrike());
                callResult.put("expiry", instrument.getExpiry());
                callResult.put("instrumentToken", instrument.getInstrumentToken());
                callResult.put("totalScore", analysis.getTotalScore());
                callResult.put("isFallback", analysis.isFallback());
                
                // Add Greeks information
                if (analysis.getGreeks() != null) {
                    Map<String, Object> greeks = new HashMap<>();
                    greeks.put("delta", analysis.getGreeks().getDelta());
                    greeks.put("gamma", analysis.getGreeks().getGamma());
                    greeks.put("theta", analysis.getGreeks().getTheta());
                    greeks.put("vega", analysis.getGreeks().getVega());
                    greeks.put("intrinsicValue", analysis.getGreeks().getIntrinsicValue());
                    greeks.put("timeValue", analysis.getGreeks().getTimeValue());
                    greeks.put("impliedVolatility", analysis.getGreeks().getImpliedVolatility());
                    greeks.put("timeToExpiry", analysis.getGreeks().getTimeToExpiry());
                    callResult.put("greeks", greeks);
                }
                
                // Add market factors
                if (analysis.getMarketFactors() != null) {
                    Map<String, Object> marketFactors = new HashMap<>();
                    marketFactors.put("volumeScore", analysis.getMarketFactors().getVolumeScore());
                    marketFactors.put("oiScore", analysis.getMarketFactors().getOiScore());
                    marketFactors.put("liquidityScore", analysis.getMarketFactors().getLiquidityScore());
                    marketFactors.put("totalScore", analysis.getMarketFactors().getTotalScore());
                    callResult.put("marketFactors", marketFactors);
                }
                
                // Add strategy factors
                if (analysis.getStrategyFactors() != null) {
                    Map<String, Object> strategyFactors = new HashMap<>();
                    strategyFactors.put("deltaScore", analysis.getStrategyFactors().getDeltaScore());
                    strategyFactors.put("thetaScore", analysis.getStrategyFactors().getThetaScore());
                    strategyFactors.put("gammaScore", analysis.getStrategyFactors().getGammaScore());
                    strategyFactors.put("moneynessScore", analysis.getStrategyFactors().getMoneynessScore());
                    strategyFactors.put("totalScore", analysis.getStrategyFactors().getTotalScore());
                    callResult.put("strategyFactors", strategyFactors);
                }
                
                // Add risk metrics
                if (analysis.getRiskMetrics() != null) {
                    Map<String, Object> riskMetrics = new HashMap<>();
                    riskMetrics.put("timeDecayRisk", analysis.getRiskMetrics().getTimeDecayRisk());
                    riskMetrics.put("volatilityRisk", analysis.getRiskMetrics().getVolatilityRisk());
                    riskMetrics.put("priceRisk", analysis.getRiskMetrics().getPriceRisk());
                    riskMetrics.put("totalRisk", analysis.getRiskMetrics().getTotalRisk());
                    callResult.put("riskMetrics", riskMetrics);
                }
                
                // Get LTP for the CALL option
                try {
                    String callToken = String.valueOf(instrument.getInstrumentToken());
                    double callLTP = kiteConnect.getLTP(new String[]{callToken}).get(callToken).lastPrice;
                    callResult.put("ltp", callLTP);
                    log.info("✅ OPTIMAL CALL STRIKE FOUND - Symbol: {}, Strike: {}, LTP: {}, Score: {:.2f}, Delta: {:.3f}", 
                            instrument.getTradingSymbol(), instrument.getStrike(), callLTP, 
                            analysis.getTotalScore(), analysis.getGreeks().getDelta());
                } catch (KiteException e) {
                    log.warn("Could not get LTP for CALL option: {}", instrument.getTradingSymbol(), e);
                    callResult.put("ltp", null);
                    callResult.put("ltpError", "Could not fetch LTP: " + e.getMessage());
                    log.info("✅ OPTIMAL CALL STRIKE FOUND - Symbol: {}, Strike: {}, Score: {:.2f} (LTP unavailable)", 
                            instrument.getTradingSymbol(), instrument.getStrike(), analysis.getTotalScore());
                } catch (Exception e) {
                    log.warn("Unexpected error getting LTP for CALL option: {}", instrument.getTradingSymbol(), e);
                    callResult.put("ltp", null);
                    callResult.put("ltpError", "Unexpected error: " + e.getMessage());
                    log.info("✅ OPTIMAL CALL STRIKE FOUND - Symbol: {}, Strike: {}, Score: {:.2f} (LTP unavailable)", 
                            instrument.getTradingSymbol(), instrument.getStrike(), analysis.getTotalScore());
                }
                
                callResult.put("found", true);
            } else {
                callResult.put("found", false);
                callResult.put("message", "No suitable CALL option found for strategy: " + strategy);
                log.warn("❌ NO CALL OPTION FOUND");
            }
            response.put("callOption", callResult);

            // Get optimal PUT option with advanced analysis
            log.info("🔍 FINDING OPTIMAL PUT STRIKE - Index Price: {}, Strategy: {}", niftyIndexPrice, strategy);
            var putAnalysis = dynamicStrikeSelectionService.findOptimalStrike(niftyIndexPrice, "PE", strategy);
            
            Map<String, Object> putResult = new HashMap<>();
            if (putAnalysis.isPresent()) {
                var analysis = putAnalysis.get();
                var instrument = analysis.getInstrument();
                
                putResult.put("tradingSymbol", instrument.getTradingSymbol());
                putResult.put("strikePrice", instrument.getStrike());
                putResult.put("expiry", instrument.getExpiry());
                putResult.put("instrumentToken", instrument.getInstrumentToken());
                putResult.put("totalScore", analysis.getTotalScore());
                putResult.put("isFallback", analysis.isFallback());
                
                // Add Greeks information
                if (analysis.getGreeks() != null) {
                    Map<String, Object> greeks = new HashMap<>();
                    greeks.put("delta", analysis.getGreeks().getDelta());
                    greeks.put("gamma", analysis.getGreeks().getGamma());
                    greeks.put("theta", analysis.getGreeks().getTheta());
                    greeks.put("vega", analysis.getGreeks().getVega());
                    greeks.put("intrinsicValue", analysis.getGreeks().getIntrinsicValue());
                    greeks.put("timeValue", analysis.getGreeks().getTimeValue());
                    greeks.put("impliedVolatility", analysis.getGreeks().getImpliedVolatility());
                    greeks.put("timeToExpiry", analysis.getGreeks().getTimeToExpiry());
                    putResult.put("greeks", greeks);
                }
                
                // Add market factors
                if (analysis.getMarketFactors() != null) {
                    Map<String, Object> marketFactors = new HashMap<>();
                    marketFactors.put("volumeScore", analysis.getMarketFactors().getVolumeScore());
                    marketFactors.put("oiScore", analysis.getMarketFactors().getOiScore());
                    marketFactors.put("liquidityScore", analysis.getMarketFactors().getLiquidityScore());
                    marketFactors.put("totalScore", analysis.getMarketFactors().getTotalScore());
                    putResult.put("marketFactors", marketFactors);
                }
                
                // Add strategy factors
                if (analysis.getStrategyFactors() != null) {
                    Map<String, Object> strategyFactors = new HashMap<>();
                    strategyFactors.put("deltaScore", analysis.getStrategyFactors().getDeltaScore());
                    strategyFactors.put("thetaScore", analysis.getStrategyFactors().getThetaScore());
                    strategyFactors.put("gammaScore", analysis.getStrategyFactors().getGammaScore());
                    strategyFactors.put("moneynessScore", analysis.getStrategyFactors().getMoneynessScore());
                    strategyFactors.put("totalScore", analysis.getStrategyFactors().getTotalScore());
                    putResult.put("strategyFactors", strategyFactors);
                }
                
                // Add risk metrics
                if (analysis.getRiskMetrics() != null) {
                    Map<String, Object> riskMetrics = new HashMap<>();
                    riskMetrics.put("timeDecayRisk", analysis.getRiskMetrics().getTimeDecayRisk());
                    riskMetrics.put("volatilityRisk", analysis.getRiskMetrics().getVolatilityRisk());
                    riskMetrics.put("priceRisk", analysis.getRiskMetrics().getPriceRisk());
                    riskMetrics.put("totalRisk", analysis.getRiskMetrics().getTotalRisk());
                    putResult.put("riskMetrics", riskMetrics);
                }
                
                // Get LTP for the PUT option
                try {
                    String putToken = String.valueOf(instrument.getInstrumentToken());
                    double putLTP = kiteConnect.getLTP(new String[]{putToken}).get(putToken).lastPrice;
                    putResult.put("ltp", putLTP);
                    log.info("✅ OPTIMAL PUT STRIKE FOUND - Symbol: {}, Strike: {}, LTP: {}, Score: {:.2f}, Delta: {:.3f}", 
                            instrument.getTradingSymbol(), instrument.getStrike(), putLTP, 
                            analysis.getTotalScore(), analysis.getGreeks().getDelta());
                } catch (KiteException e) {
                    log.warn("Could not get LTP for PUT option: {}", instrument.getTradingSymbol(), e);
                    putResult.put("ltp", null);
                    putResult.put("ltpError", "Could not fetch LTP: " + e.getMessage());
                    log.info("✅ OPTIMAL PUT STRIKE FOUND - Symbol: {}, Strike: {}, Score: {:.2f} (LTP unavailable)", 
                            instrument.getTradingSymbol(), instrument.getStrike(), analysis.getTotalScore());
                } catch (Exception e) {
                    log.warn("Unexpected error getting LTP for PUT option: {}", instrument.getTradingSymbol(), e);
                    putResult.put("ltp", null);
                    putResult.put("ltpError", "Unexpected error: " + e.getMessage());
                    log.info("✅ OPTIMAL PUT STRIKE FOUND - Symbol: {}, Strike: {}, Score: {:.2f} (LTP unavailable)", 
                            instrument.getTradingSymbol(), instrument.getStrike(), analysis.getTotalScore());
                }
                
                putResult.put("found", true);
            } else {
                putResult.put("found", false);
                putResult.put("message", "No suitable PUT option found for strategy: " + strategy);
                log.warn("❌ NO PUT OPTION FOUND");
            }
            response.put("putOption", putResult);

            response.put("message", "Optimal strikes analysis completed using advanced Greeks-based selection");
            response.put("success", true);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error in getOptimalStrikes API", e);
            response.put("error", e.getMessage());
            response.put("message", "Error occurred while finding optimal strikes");
            response.put("success", false);
            return ResponseEntity.internalServerError().body(response);
        }
    }

}


