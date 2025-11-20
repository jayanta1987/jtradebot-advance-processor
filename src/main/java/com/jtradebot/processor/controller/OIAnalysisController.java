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
import java.util.Map;

/**
 * Controller for Open Interest (OI) Analysis API
 * Provides detailed OI state and signals
 */
@RestController
@RequestMapping("/api/oi")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:5173", "https://jtradebot.com", "https://www.jtradebot.com"})
public class OIAnalysisController {

    private final OIAnalysisService oiAnalysisService;
    private final TickDataManager tickDataManager;
    private final KiteInstrumentHandler kiteInstrumentHandler;

    /**
     * Get current OI state with detailed information
     * Returns OI signals, scores, and data history
     */
    @GetMapping("/state")
    public ResponseEntity<Map<String, Object>> getCurrentOIState() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get current Nifty index price
            Tick niftyTick = tickDataManager.getLastTick(String.valueOf(kiteInstrumentHandler.getNifty50Token()));
            if (niftyTick == null) {
                response.put("error", "No Nifty index data available");
                response.put("message", "Unable to get current Nifty index price");
                response.put("success", false);
                return ResponseEntity.badRequest().body(response);
            }

            double niftyIndexPrice = niftyTick.getLastTradedPrice();
            log.info("🔍 OI STATE REQUEST - Index: {}", niftyIndexPrice);

            // Get OI signals from cache
            OIAnalysisService.OISignalsCache oiSignals = oiAnalysisService.getOISignals();
            
            // Build detailed response
            response.put("success", true);
            response.put("message", "OI state retrieved successfully");
            response.put("niftyIndexPrice", niftyIndexPrice);
            response.put("timestamp", System.currentTimeMillis());
            
            // OI Signals for different timeframes
            Map<String, Object> signals = new HashMap<>();
            
            Map<String, Object> signal1min = new HashMap<>();
            signal1min.put("bullish", oiSignals.getOiBullishSignal1min());
            signal1min.put("bearish", oiSignals.getOiBearishSignal1min());
            signals.put("1min", signal1min);
            
            Map<String, Object> signal5min = new HashMap<>();
            signal5min.put("bullish", oiSignals.getOiBullishSignal5min());
            signal5min.put("bearish", oiSignals.getOiBearishSignal5min());
            signals.put("5min", signal5min);
            
            Map<String, Object> signal15min = new HashMap<>();
            signal15min.put("bullish", oiSignals.getOiBullishSignal15min());
            signal15min.put("bearish", oiSignals.getOiBearishSignal15min());
            signals.put("15min", signal15min);
            
            response.put("signals", signals);
            
            // Buy scores
            Map<String, Object> scores = new HashMap<>();
            scores.put("callBuyScore", oiSignals.getCallBuyScore());
            scores.put("putBuyScore", oiSignals.getPutBuyScore());
            response.put("scores", scores);
            
            // Cache metadata
            Map<String, Object> cacheInfo = new HashMap<>();
            cacheInfo.put("lastUpdated", oiSignals.getLastUpdated());
            cacheInfo.put("cacheAgeSeconds", (System.currentTimeMillis() - oiSignals.getLastUpdated()) / 1000);
            response.put("cacheInfo", cacheInfo);
            
            // Signal interpretation
            Map<String, String> interpretation = new HashMap<>();
            interpretation.put("callRecommendation", getCallRecommendation(oiSignals));
            interpretation.put("putRecommendation", getPutRecommendation(oiSignals));
            interpretation.put("marketSentiment", getMarketSentiment(oiSignals));
            response.put("interpretation", interpretation);
            
            log.info("✅ OI STATE RETRIEVED - Call Score: {}, Put Score: {}", 
                    oiSignals.getCallBuyScore(), oiSignals.getPutBuyScore());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error in OI state API", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("message", "Error occurred while retrieving OI state");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get detailed OI data for all tracked strikes
     */
    @GetMapping("/data")
    public ResponseEntity<Map<String, Object>> getOIData() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get current Nifty index price
            Tick niftyTick = tickDataManager.getLastTick(String.valueOf(kiteInstrumentHandler.getNifty50Token()));
            if (niftyTick == null) {
                response.put("error", "No Nifty index data available");
                response.put("message", "Unable to get current Nifty index price");
                response.put("success", false);
                return ResponseEntity.badRequest().body(response);
            }

            double niftyIndexPrice = niftyTick.getLastTradedPrice();
            log.info("🔍 OI DATA REQUEST - Index: {}", niftyIndexPrice);

            // Get OI data history (this would need a method to expose it)
            // For now, return summary
            response.put("success", true);
            response.put("message", "OI data summary");
            response.put("niftyIndexPrice", niftyIndexPrice);
            response.put("timestamp", System.currentTimeMillis());
            response.put("note", "Detailed OI data history is available internally. Use /api/oi/state for signals and scores.");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error in OI data API", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("message", "Error occurred while retrieving OI data");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get CALL recommendation based on OI signals
     */
    private String getCallRecommendation(OIAnalysisService.OISignalsCache signals) {
        double callScore = signals.getCallBuyScore();
        
        if (callScore >= 2.0) {
            return "STRONG_BUY - Multiple bullish OI signals across timeframes";
        } else if (callScore >= 1.0) {
            return "BUY - Bullish OI signals present";
        } else if (callScore >= 0.0) {
            return "NEUTRAL - Mixed or weak OI signals";
        } else {
            return "AVOID - Bearish OI signals dominant";
        }
    }

    /**
     * Get PUT recommendation based on OI signals
     */
    private String getPutRecommendation(OIAnalysisService.OISignalsCache signals) {
        double putScore = signals.getPutBuyScore();
        
        if (putScore >= 2.0) {
            return "STRONG_BUY - Multiple bearish OI signals across timeframes";
        } else if (putScore >= 1.0) {
            return "BUY - Bearish OI signals present";
        } else if (putScore >= 0.0) {
            return "NEUTRAL - Mixed or weak OI signals";
        } else {
            return "AVOID - Bullish OI signals dominant";
        }
    }

    /**
     * Get overall market sentiment
     */
    private String getMarketSentiment(OIAnalysisService.OISignalsCache signals) {
        double callScore = signals.getCallBuyScore();
        double putScore = signals.getPutBuyScore();
        
        if (callScore > putScore && callScore > 1.0) {
            return "BULLISH - Strong bullish OI buildup";
        } else if (putScore > callScore && putScore > 1.0) {
            return "BEARISH - Strong bearish OI buildup";
        } else if (callScore > 0 && putScore > 0) {
            return "MIXED - Conflicting OI signals";
        } else {
            return "NEUTRAL - Weak or no clear OI signals";
        }
    }
}

