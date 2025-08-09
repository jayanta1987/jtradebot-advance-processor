package com.jtradebot.processor.controller;

import com.jtradebot.processor.model.FlattenedIndicators;
import com.jtradebot.processor.service.ScalpingVolumeSurgeService;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/scalping-volume-surge")
@RequiredArgsConstructor
@Slf4j
public class ScalpingVolumeSurgeController {
    
    private final ScalpingVolumeSurgeService scalpingVolumeSurgeService;
    
    @PostMapping("/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateStrategy(@RequestBody Tick tick) {
        try {
            log.info("Evaluating SCALPING_FUTURE_VOLUME_SURGE strategy for instrument: {}", tick.getInstrumentToken());
            
            // Get flattened indicators
            FlattenedIndicators indicators = scalpingVolumeSurgeService.getFlattenedIndicators(tick);
            
            // Get strategy recommendation
            String recommendedStrategy = scalpingVolumeSurgeService.getRecommendedStrategy(tick);
            
            // Get strategy confidence
            Double strategyConfidence = scalpingVolumeSurgeService.getStrategyConfidence(tick);
            
            // Check entry conditions
            boolean shouldMakeCallEntry = scalpingVolumeSurgeService.shouldMakeCallEntry(tick);
            boolean shouldMakePutEntry = scalpingVolumeSurgeService.shouldMakePutEntry(tick);
            
            Map<String, Object> response = new HashMap<>();
            response.put("instrumentToken", tick.getInstrumentToken());
            response.put("timestamp", tick.getTickTimestamp());
            response.put("lastTradedPrice", tick.getLastTradedPrice());
            response.put("recommendedStrategy", recommendedStrategy);
            response.put("strategyConfidence", strategyConfidence);
            response.put("shouldMakeCallEntry", shouldMakeCallEntry);
            response.put("shouldMakePutEntry", shouldMakePutEntry);
            response.put("flattenedIndicators", indicators);
            
            log.info("Strategy evaluation completed. Recommended: {}, Confidence: {}", recommendedStrategy, strategyConfidence);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error evaluating strategy for instrument: {}", tick.getInstrumentToken(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to evaluate strategy");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "SCALPING_FUTURE_VOLUME_SURGE");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/rules")
    public ResponseEntity<Map<String, Object>> getRules() {
        Map<String, Object> response = new HashMap<>();
        response.put("strategy", "SCALPING_FUTURE_VOLUME_SURGE");
        response.put("description", "Scalping strategy based on volume surge and technical indicators");
        response.put("timeframes", new String[]{"1min", "5min", "15min"});
        response.put("indicators", new String[]{"EMA", "RSI", "Volume", "VWAP", "Support/Resistance"});
        response.put("entryConditions", new String[]{"Volume Surge", "EMA Crossover", "RSI Overbought/Oversold", "Price vs VWAP", "Support/Resistance"});
        return ResponseEntity.ok(response);
    }
}
