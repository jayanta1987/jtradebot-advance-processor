package com.jtradebot.processor.controller;

import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.strategy.ScalpingEntryDecision;
import com.jtradebot.processor.service.ScalpingEntryService;
import com.jtradebot.processor.service.ScalpingVolumeSurgeService;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/scalping-entry")
@RequiredArgsConstructor
@Slf4j
public class ScalpingEntryController {

    private final ScalpingEntryService scalpingEntryService;
    private final ScalpingVolumeSurgeService scalpingVolumeSurgeService;

    /**
     * Evaluate scalping entry using the new scenario-based approach
     */
    @PostMapping("/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateScalpingEntry(@RequestBody Tick tick) {
        try {
            log.info("Evaluating scalping entry for instrument: {}", tick.getInstrumentToken());
            
            // Get flattened indicators from the volume surge service
            FlattenedIndicators indicators = scalpingVolumeSurgeService.getFlattenedIndicators(tick);
            
            // Evaluate entry using new scenario-based approach
            ScalpingEntryDecision decision = scalpingEntryService.evaluateEntry(tick, indicators);
            
            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("instrumentToken", tick.getInstrumentToken());
            response.put("currentPrice", tick.getLastTradedPrice());
            response.put("currentTime", tick.getTickTimestamp());
            response.put("decision", decision);
            response.put("flattenedIndicators", indicators);
            
            log.info("Scalping entry evaluation completed for instrument: {}", tick.getInstrumentToken());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error evaluating scalping entry for instrument: {}", tick.getInstrumentToken(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Evaluate CALL entry using legacy approach (for backward compatibility)
     */
    @PostMapping("/evaluate-call")
    public ResponseEntity<Map<String, Object>> evaluateCallEntry(@RequestBody Tick tick) {
        try {
            log.info("Evaluating CALL entry for instrument: {}", tick.getInstrumentToken());
            
            // Use the volume surge service for CALL evaluation
            boolean shouldMakeCallEntry = scalpingVolumeSurgeService.shouldMakeCallEntry(tick);
            
            Map<String, Object> response = new HashMap<>();
            response.put("instrumentToken", tick.getInstrumentToken());
            response.put("currentPrice", tick.getLastTradedPrice());
            response.put("shouldMakeCallEntry", shouldMakeCallEntry);
            response.put("strategyType", "CALL");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error evaluating CALL entry", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Evaluate PUT entry using legacy approach (for backward compatibility)
     */
    @PostMapping("/evaluate-put")
    public ResponseEntity<Map<String, Object>> evaluatePutEntry(@RequestBody Tick tick) {
        try {
            log.info("Evaluating PUT entry for instrument: {}", tick.getInstrumentToken());
            
            // Use the volume surge service for PUT evaluation
            boolean shouldMakePutEntry = scalpingVolumeSurgeService.shouldMakePutEntry(tick);
            
            Map<String, Object> response = new HashMap<>();
            response.put("instrumentToken", tick.getInstrumentToken());
            response.put("currentPrice", tick.getLastTradedPrice());
            response.put("shouldMakePutEntry", shouldMakePutEntry);
            response.put("strategyType", "PUT");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error evaluating PUT entry", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get strategy score and recommendation
     */
    @PostMapping("/strategy-score")
    public ResponseEntity<Map<String, Object>> getStrategyScore(@RequestBody Tick tick) {
        try {
            log.info("Getting strategy score for instrument: {}", tick.getInstrumentToken());
            
            // Get strategy score from volume surge service
            String recommendedStrategy = scalpingVolumeSurgeService.getRecommendedStrategy(tick);
            Double strategyConfidence = scalpingVolumeSurgeService.getStrategyConfidence(tick);
            
            Map<String, Object> response = new HashMap<>();
            response.put("instrumentToken", tick.getInstrumentToken());
            response.put("currentPrice", tick.getLastTradedPrice());
            response.put("recommendedStrategy", recommendedStrategy);
            response.put("strategyConfidence", strategyConfidence);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting strategy score", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
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
        health.put("version", "2.0");
        health.put("description", "Scenario-based scalping entry evaluation");
        
        return ResponseEntity.ok(health);
    }

    /**
     * Get available scenarios and categories
     */
    @GetMapping("/scenarios")
    public ResponseEntity<Map<String, Object>> getScenarios() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Scenarios and categories are configured in scalping-entry-config.json");
            response.put("note", "Use /api/scalping-volume-surge/rules to see current configuration");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting scenarios", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}

