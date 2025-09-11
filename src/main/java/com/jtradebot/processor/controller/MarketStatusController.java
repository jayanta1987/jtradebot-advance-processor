package com.jtradebot.processor.controller;

import com.jtradebot.processor.model.response.MarketStatusResponse;
import com.jtradebot.processor.service.MarketStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for market status API endpoints
 * Provides current market analysis without recalculating data
 */
@CrossOrigin(origins = {"http://localhost:5173", "https://jtradebot.com", "https://www.jtradebot.com"})
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
@Slf4j
public class MarketStatusController {
    
    private final MarketStatusService marketStatusService;
    
    /**
     * Get current market status with all scores, filters, and indicators
     * Returns data already calculated by TickOrchestrationService
     * 
     * @return Current market status with category scores, filters, and overall scores
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCurrentMarketStatus() {
        try {
            log.info("📊 MARKET STATUS REQUEST RECEIVED");
            
            MarketStatusResponse marketStatus = marketStatusService.getCurrentMarketStatus();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", marketStatus);
            response.put("message", "Market status retrieved successfully");
            response.put("timestamp", System.currentTimeMillis());
            
            log.info("✅ MARKET STATUS RETRIEVED - Quality Score: {}, Dominant Trend: {}, In Trading Zone: {}", 
                    marketStatus.getQualityScore(), marketStatus.getDominantTrend(), marketStatus.getInTradingZone());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ ERROR GETTING MARKET STATUS: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to get market status");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * Get simplified market status with key metrics only
     * 
     * @return Simplified market status with essential information
     */
    @GetMapping("/status/summary")
    public ResponseEntity<Map<String, Object>> getMarketStatusSummary() {
        try {
            log.info("📊 MARKET STATUS SUMMARY REQUEST RECEIVED");
            
            MarketStatusResponse marketStatus = marketStatusService.getCurrentMarketStatus();
            
            Map<String, Object> summary = new HashMap<>();
            summary.put("instrumentToken", marketStatus.getInstrumentToken());
            summary.put("currentPrice", marketStatus.getCurrentPrice());
            summary.put("timestamp", marketStatus.getTimestamp());
            summary.put("inTradingZone", marketStatus.getInTradingZone());
            summary.put("dominantTrend", marketStatus.getDominantTrend());
            summary.put("qualityScore", marketStatus.getQualityScore());
            summary.put("callTotalScore", marketStatus.getCallTotalScore());
            summary.put("putTotalScore", marketStatus.getPutTotalScore());
            summary.put("eligibleForEntry", marketStatus.getEligibleForEntry());
            summary.put("entryEligibilityReason", marketStatus.getEntryEligibilityReason());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", summary);
            response.put("message", "Market status summary retrieved successfully");
            response.put("timestamp", System.currentTimeMillis());
            
            log.info("✅ MARKET STATUS SUMMARY RETRIEVED - Trend: {}, Quality: {}, Eligible: {}", 
                    marketStatus.getDominantTrend(), marketStatus.getQualityScore(), marketStatus.getEligibleForEntry());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ ERROR GETTING MARKET STATUS SUMMARY: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to get market status summary");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
