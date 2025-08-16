package com.jtradebot.processor.controller;

import com.jtradebot.processor.model.indicator.DynamicFlattenedIndicators;
import com.jtradebot.processor.model.indicator.DynamicIndicatorConfig;
import com.jtradebot.processor.model.strategy.StrategyEvaluationResult;
import com.jtradebot.processor.repository.document.TickDocument;
import com.jtradebot.processor.service.DynamicIndicatorFlattenerService;
import com.jtradebot.processor.service.DynamicStrategyEvaluatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dynamic-indicators")
@RequiredArgsConstructor
@Slf4j
public class DynamicIndicatorController {

    private final DynamicIndicatorFlattenerService dynamicIndicatorFlattenerService;
    private final DynamicStrategyEvaluatorService dynamicStrategyEvaluatorService;

    /**
     * Test endpoint to demonstrate dynamic indicator flattening
     */
    @PostMapping("/flatten")
    public ResponseEntity<Map<String, Object>> flattenIndicators(@RequestBody TickDocument tickDocument) {
        try {
            log.info("Flattening indicators for instrument: {}", tickDocument.getInstrumentToken());
            
            // Load configuration from JSON
            DynamicIndicatorConfig config = dynamicIndicatorFlattenerService.loadConfiguration("rules/dynamic-indicators-config.json");
            
            if (config == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Failed to load indicator configuration"));
            }
            
            // Validate configuration
            if (!dynamicIndicatorFlattenerService.validateConfiguration(config)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid indicator configuration"));
            }
            
            // Flatten indicators
            DynamicFlattenedIndicators flattenedIndicators = dynamicIndicatorFlattenerService.flattenIndicators(tickDocument, config);
            
            // Evaluate strategy
            StrategyEvaluationResult evaluationResult = dynamicStrategyEvaluatorService.evaluateStrategy(flattenedIndicators, config);
            
            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("instrumentToken", tickDocument.getInstrumentToken());
            response.put("timestamp", tickDocument.getTickTimestamp());
            response.put("flattenedIndicators", flattenedIndicators);
            response.put("strategyEvaluation", evaluationResult);
            response.put("configuration", config);
            
            log.info("Dynamic indicator processing completed for instrument: {}", tickDocument.getInstrumentToken());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error processing dynamic indicators for instrument: {}", tickDocument.getInstrumentToken(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get indicator configuration
     */
    @GetMapping("/config")
    public ResponseEntity<DynamicIndicatorConfig> getConfiguration() {
        try {
            DynamicIndicatorConfig config = dynamicIndicatorFlattenerService.loadConfiguration("rules/dynamic-indicators-config.json");
            
            if (config == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(config);
            
        } catch (Exception e) {
            log.error("Error loading indicator configuration", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Test endpoint to evaluate strategy with custom indicators
     */
    @PostMapping("/evaluate")
    public ResponseEntity<StrategyEvaluationResult> evaluateStrategy(@RequestBody DynamicFlattenedIndicators flattenedIndicators) {
        try {
            log.info("Evaluating strategy for instrument: {}", flattenedIndicators.getInstrumentToken());
            
            // Load configuration
            DynamicIndicatorConfig config = dynamicIndicatorFlattenerService.loadConfiguration("rules/dynamic-indicators-config.json");
            
            if (config == null) {
                return ResponseEntity.badRequest().build();
            }
            
            // Evaluate strategy
            StrategyEvaluationResult evaluationResult = dynamicStrategyEvaluatorService.evaluateStrategy(flattenedIndicators, config);
            
            return ResponseEntity.ok(evaluationResult);
            
        } catch (Exception e) {
            log.error("Error evaluating strategy", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get available indicator types
     */
    @GetMapping("/types")
    public ResponseEntity<Map<String, Object>> getIndicatorTypes() {
        Map<String, Object> types = new HashMap<>();
        types.put("supportedTypes", new String[]{
            "EMA", "RSI", "VOLUME", "VWAP", "SUPPORT_RESISTANCE", "CROSSOVER", "CROSSDOWN"
        });
        types.put("supportedComparisons", new String[]{
            "GT", "LT", "EQ", "CROSSOVER", "CROSSDOWN"
        });
        types.put("supportedTimeframes", new String[]{
            "1min", "5min", "15min"
        });
        
        return ResponseEntity.ok(types);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "DynamicIndicatorService");
        health.put("version", "2.0");
        
        try {
            DynamicIndicatorConfig config = dynamicIndicatorFlattenerService.loadConfiguration("rules/dynamic-indicators-config.json");
            health.put("configLoaded", config != null);
            health.put("configValid", config != null && dynamicIndicatorFlattenerService.validateConfiguration(config));
        } catch (Exception e) {
            health.put("configLoaded", false);
            health.put("configValid", false);
            health.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(health);
    }
}
