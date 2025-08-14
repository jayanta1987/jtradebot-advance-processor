package com.jtradebot.processor.controller;

import com.jtradebot.processor.service.BacktestDataAnalysisService;
import com.jtradebot.processor.service.BacktestDataCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/backtest-data")
@RequiredArgsConstructor
@Slf4j
public class BacktestDataController {
    
    private final BacktestDataAnalysisService backtestDataAnalysisService;
    private final BacktestDataCollectorService backtestDataCollectorService;
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Backtest data collection service is running");
            response.put("endpoints", new String[]{
                "GET /api/backtest-data/status - Get service status",
                "POST /api/backtest-data/analyze - Analyze collected data",
                "POST /api/backtest-data/cleanup - Clean up old data files"
            });
            response.put("note", "Data collection is automatic and controlled by application.yml configuration");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting status: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupData() {
        try {
            log.info("🧹 Starting manual cleanup of backtest data directory...");
            
            Map<String, Object> cleanupResult = backtestDataCollectorService.cleanBacktestDirectory();
            
            if ((Boolean) cleanupResult.get("success")) {
                log.info("✅ Cleanup completed successfully");
                return ResponseEntity.ok(cleanupResult);
            } else {
                log.error("❌ Cleanup failed: {}", cleanupResult.get("error"));
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cleanupResult);
            }
            
        } catch (Exception e) {
            log.error("Error during cleanup: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Cleanup failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeData() {
        try {
            log.info("🔍 Starting automatic analysis of all backtest data...");
            
            // Analyze all files in directory
            String outputDir = "./backtest-data";
            Map<String, Object> analysis = backtestDataAnalysisService.analyzeBacktestDataFromDirectory(outputDir);
            
            if (analysis.containsKey("error")) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", analysis.get("error"));
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Analysis completed successfully");
            response.put("analysis", analysis);
            
            log.info("✅ Analysis completed successfully");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error analyzing data: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
