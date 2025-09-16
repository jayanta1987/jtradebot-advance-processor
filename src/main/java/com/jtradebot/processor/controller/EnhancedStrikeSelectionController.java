package com.jtradebot.processor.controller;

import com.jtradebot.processor.common.ProfileUtil;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.service.price.EnhancedStrikeSelectionService;
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

}


