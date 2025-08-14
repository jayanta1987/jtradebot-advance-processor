package com.jtradebot.processor.controller;

import com.jtradebot.processor.model.*;
import com.jtradebot.processor.repository.document.TickDocument;
import com.jtradebot.processor.service.DynamicIndicatorFlattenerService;
import com.jtradebot.processor.service.ScalpingEntryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/scalping-entry")
@RequiredArgsConstructor
@Slf4j
public class ScalpingEntryController {

    private final DynamicIndicatorFlattenerService dynamicIndicatorFlattenerService;
    private final ScalpingEntryService scalpingEntryService;

    /**
     * Evaluate scalping entry for both CALL and PUT strategies
     */
    @PostMapping("/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateScalpingEntry(@RequestBody TickDocument tickDocument) {
        try {
            log.info("Evaluating scalping entry for instrument: {}", tickDocument.getInstrumentToken());
            
            // Load dynamic indicator configuration
            DynamicIndicatorConfig config = dynamicIndicatorFlattenerService.loadConfiguration("rules/dynamic-indicators-config.json");
            if (config == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Failed to load indicator configuration"));
            }
            
            // Flatten indicators
            DynamicFlattenedIndicators flattenedIndicators = dynamicIndicatorFlattenerService.flattenIndicators(tickDocument, config);
            
            // Get current price and time
            double currentPrice = tickDocument.getLastTradedPrice();
            LocalDateTime currentTime = LocalDateTime.now();
            
            // Validate market conditions
            MarketConditionValidation marketConditions = scalpingEntryService.validateMarketConditions(
                currentTime, currentPrice, currentPrice * 0.9999, currentPrice * 1.0001, 1000000L);
            
            // Evaluate CALL entry
            ScalpingEntryDecision callDecision = scalpingEntryService.evaluateCallEntry(
                flattenedIndicators, config, currentPrice, currentTime);
            
            // Evaluate PUT entry
            ScalpingEntryDecision putDecision = scalpingEntryService.evaluatePutEntry(
                flattenedIndicators, config, currentPrice, currentTime);
            
            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("instrumentToken", tickDocument.getInstrumentToken());
            response.put("currentPrice", currentPrice);
            response.put("currentTime", currentTime);
            response.put("marketConditions", marketConditions);
            response.put("callDecision", callDecision);
            response.put("putDecision", putDecision);
            response.put("flattenedIndicators", flattenedIndicators);
            
            // Determine best entry
            ScalpingEntryDecision bestEntry = determineBestEntry(callDecision, putDecision);
            response.put("bestEntry", bestEntry);
            
            log.info("Scalping entry evaluation completed for instrument: {}", tickDocument.getInstrumentToken());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error evaluating scalping entry for instrument: {}", tickDocument.getInstrumentToken(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Evaluate CALL entry only
     */
    @PostMapping("/evaluate-call")
    public ResponseEntity<ScalpingEntryDecision> evaluateCallEntry(@RequestBody TickDocument tickDocument) {
        try {
            log.info("Evaluating CALL entry for instrument: {}", tickDocument.getInstrumentToken());
            
            DynamicIndicatorConfig config = dynamicIndicatorFlattenerService.loadConfiguration("rules/dynamic-indicators-config.json");
            if (config == null) {
                return ResponseEntity.badRequest().build();
            }
            
            DynamicFlattenedIndicators flattenedIndicators = dynamicIndicatorFlattenerService.flattenIndicators(tickDocument, config);
            
            ScalpingEntryDecision callDecision = scalpingEntryService.evaluateCallEntry(
                flattenedIndicators, config, tickDocument.getLastTradedPrice(), LocalDateTime.now());
            
            return ResponseEntity.ok(callDecision);
            
        } catch (Exception e) {
            log.error("Error evaluating CALL entry", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Evaluate PUT entry only
     */
    @PostMapping("/evaluate-put")
    public ResponseEntity<ScalpingEntryDecision> evaluatePutEntry(@RequestBody TickDocument tickDocument) {
        try {
            log.info("Evaluating PUT entry for instrument: {}", tickDocument.getInstrumentToken());
            
            DynamicIndicatorConfig config = dynamicIndicatorFlattenerService.loadConfiguration("rules/dynamic-indicators-config.json");
            if (config == null) {
                return ResponseEntity.badRequest().build();
            }
            
            DynamicFlattenedIndicators flattenedIndicators = dynamicIndicatorFlattenerService.flattenIndicators(tickDocument, config);
            
            ScalpingEntryDecision putDecision = scalpingEntryService.evaluatePutEntry(
                flattenedIndicators, config, tickDocument.getLastTradedPrice(), LocalDateTime.now());
            
            return ResponseEntity.ok(putDecision);
            
        } catch (Exception e) {
            log.error("Error evaluating PUT entry", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Calculate position size and risk levels
     */
    @PostMapping("/calculate-position")
    public ResponseEntity<Map<String, Object>> calculatePosition(@RequestBody Map<String, Object> request) {
        try {
            double accountBalance = Double.parseDouble(request.get("accountBalance").toString());
            double riskPerTrade = Double.parseDouble(request.get("riskPerTrade").toString());
            double stopLossPoints = Double.parseDouble(request.get("stopLossPoints").toString());
            double currentPrice = Double.parseDouble(request.get("currentPrice").toString());
            String strategyType = request.get("strategyType").toString();
            
            int positionSize = scalpingEntryService.calculatePositionSize(accountBalance, riskPerTrade, stopLossPoints, currentPrice);
            
            StopLossTargetLevels levels = scalpingEntryService.calculateStopLossTarget(currentPrice, strategyType, stopLossPoints, 8.0);
            
            Map<String, Object> response = new HashMap<>();
            response.put("positionSize", positionSize);
            response.put("stopLossTargetLevels", levels);
            response.put("riskAmount", accountBalance * (riskPerTrade / 100.0));
            response.put("riskPerShare", stopLossPoints);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error calculating position", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Validate market conditions
     */
    @PostMapping("/validate-market")
    public ResponseEntity<MarketConditionValidation> validateMarketConditions(@RequestBody Map<String, Object> request) {
        try {
            LocalDateTime currentTime = LocalDateTime.now();
            double currentPrice = Double.parseDouble(request.get("currentPrice").toString());
            double bidPrice = Double.parseDouble(request.get("bidPrice").toString());
            double askPrice = Double.parseDouble(request.get("askPrice").toString());
            long volume = Long.parseLong(request.get("volume").toString());
            
            MarketConditionValidation validation = scalpingEntryService.validateMarketConditions(
                currentTime, currentPrice, bidPrice, askPrice, volume);
            
            return ResponseEntity.ok(validation);
            
        } catch (Exception e) {
            log.error("Error validating market conditions", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get scalping entry configuration
     */
    @GetMapping("/config")
    public ResponseEntity<ScalpingEntryLogic> getEntryConfig() {
        try {
            ScalpingEntryLogic config = scalpingEntryService.loadEntryLogic("rules/scalping-entry-config.json");
            if (config == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            log.error("Error loading entry configuration", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Health check for scalping entry service
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "ScalpingEntryService");
        health.put("version", "1.0");
        
        try {
            ScalpingEntryLogic config = scalpingEntryService.loadEntryLogic("rules/scalping-entry-config.json");
            health.put("configLoaded", config != null);
        } catch (Exception e) {
            health.put("configLoaded", false);
            health.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(health);
    }

    // Helper method to determine best entry
    private ScalpingEntryDecision determineBestEntry(ScalpingEntryDecision callDecision, ScalpingEntryDecision putDecision) {
        if (!callDecision.isShouldEnter() && !putDecision.isShouldEnter()) {
            return ScalpingEntryDecision.builder()
                    .strategyType("NO_TRADE")
                    .shouldEnter(false)
                    .rejectionReason("Neither CALL nor PUT conditions met")
                    .build();
        }
        
        if (callDecision.isShouldEnter() && !putDecision.isShouldEnter()) {
            return callDecision;
        }
        
        if (!callDecision.isShouldEnter() && putDecision.isShouldEnter()) {
            return putDecision;
        }
        
        // Both are valid, choose the one with higher confidence
        if (callDecision.getConfidenceScore() > putDecision.getConfidenceScore()) {
            return callDecision;
        } else {
            return putDecision;
        }
    }
}
