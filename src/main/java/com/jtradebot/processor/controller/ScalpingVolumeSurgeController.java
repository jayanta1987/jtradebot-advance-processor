package com.jtradebot.processor.controller;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.strategy.StrategyScore;
import com.jtradebot.processor.service.ExitStrategyService;
import com.jtradebot.processor.service.ScalpingVolumeSurgeService;
import com.jtradebot.processor.repository.document.JtradeOrder;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scalping-volume-surge")
@RequiredArgsConstructor
@Slf4j
public class ScalpingVolumeSurgeController {
    
    private final ScalpingVolumeSurgeService scalpingVolumeSurgeService;
    private final DynamicStrategyConfigService configService;
    private final ExitStrategyService exitStrategyService;
    
    @PostMapping("/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateStrategy(@RequestBody Tick tick) {
        try {
            log.info("Evaluating SCALPING_FUTURE_VOLUME_SURGE strategy for instrument: {}", tick.getInstrumentToken());
            
            // Get comprehensive strategy score
            StrategyScore strategyScore = scalpingVolumeSurgeService.calculateStrategyScore(tick);
            
            // Get flattened indicators
            FlattenedIndicators indicators = scalpingVolumeSurgeService.getFlattenedIndicators(tick);
            
            Map<String, Object> response = new HashMap<>();
            response.put("instrumentToken", tick.getInstrumentToken());
            response.put("timestamp", tick.getTickTimestamp());
            response.put("lastTradedPrice", tick.getLastTradedPrice());
            response.put("strategyScore", strategyScore);
            response.put("recommendedStrategy", strategyScore.getRecommendation());
            response.put("strategyConfidence", strategyScore.getConfidence());
            response.put("shouldMakeCallEntry", strategyScore.getShouldMakeCallEntry());
            response.put("shouldMakePutEntry", strategyScore.getShouldMakePutEntry());
            response.put("flattenedIndicators", indicators);
            
            log.info("Strategy evaluation completed. Score: {}, Recommendation: {}, Confidence: {}", 
                    strategyScore.getScoreWithSign(), strategyScore.getRecommendation(), strategyScore.getConfidence());
            
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
        response.put("strategy", configService.getStrategyName());
        response.put("version", configService.getStrategyVersion());
        response.put("description", configService.getStrategyDescription());
        response.put("timeframes", configService.getFuturesignalTimeframes());
        response.put("indicators", new String[]{"EMA", "RSI", "Volume", "VWAP", "Support/Resistance"});
        response.put("entryConditions", new String[]{"Volume Surge", "EMA Crossover", "RSI Bullish/Bearish", "Price vs VWAP", "Support/Resistance"});
        
        // Add scenarios information
        response.put("scenarios", configService.getScenarios());
        response.put("categories", configService.getCategories());
        
        // Add current thresholds
        Map<String, Object> thresholds = new HashMap<>();
        thresholds.put("callRsiThreshold", configService.getCallRsiThreshold());
        thresholds.put("putRsiThreshold", configService.getPutRsiThreshold());
        thresholds.put("volumeSurgeMultiplier", configService.getCallVolumeSurgeMultiplier());
        response.put("currentThresholds", thresholds);
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/score")
    public ResponseEntity<StrategyScore> getStrategyScore(@RequestBody Tick tick) {
        try {
            log.info("Calculating strategy score for instrument: {}", tick.getInstrumentToken());
            
            StrategyScore strategyScore = scalpingVolumeSurgeService.calculateStrategyScore(tick);
            
            log.info("Strategy score calculated: {} ({})", 
                    strategyScore.getScoreWithSign(), strategyScore.getScoreDescription());
            
            return ResponseEntity.ok(strategyScore);
            
        } catch (Exception e) {
            log.error("Error calculating strategy score for instrument: {}", tick.getInstrumentToken(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/configuration")
    public ResponseEntity<Map<String, Object>> getFullConfiguration() {
        return ResponseEntity.ok(configService.getFullConfiguration());
    }
    
    @GetMapping("/active-orders")
    public ResponseEntity<Map<String, Object>> getActiveOrders() {
        Map<String, Object> response = new HashMap<>();
        
        List<JtradeOrder> activeOrders = exitStrategyService.getActiveOrders();
        response.put("activeOrdersCount", activeOrders.size());
        response.put("activeOrders", activeOrders);
        
        // Add exit strategy configuration
        Map<String, Object> exitConfig = new HashMap<>();
        exitConfig.put("callStopLossPercentage", configService.getCallStopLossPercentage());
        exitConfig.put("putStopLossPercentage", configService.getPutStopLossPercentage());
        exitConfig.put("callTargetPercentage", configService.getCallTargetPercentage());
        exitConfig.put("putTargetPercentage", configService.getPutTargetPercentage());
        response.put("exitStrategyConfig", exitConfig);
        
        return ResponseEntity.ok(response);
    }
}
