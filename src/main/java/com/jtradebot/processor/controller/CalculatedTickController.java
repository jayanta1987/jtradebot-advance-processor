package com.jtradebot.processor.controller;

import com.jtradebot.tickstore.service.CalculatedTickService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = {"http://localhost:5173", "https://jtradebot.com", "https://www.jtradebot.com"})
@RestController
@RequestMapping("/api/calculated-ticks")
@RequiredArgsConstructor
@Slf4j
public class CalculatedTickController {
    
    private final CalculatedTickService calculatedTickService;
    
    /**
     * Get list of unique dates (use with caution on large datasets)
     */
    @GetMapping("/unique-dates")
    public ResponseEntity<Map<String, Object>> getUniqueDates() {
        try {
            List<String> uniqueDates = calculatedTickService.getUniqueDates();
            
            Map<String, Object> response = new HashMap<>();
            response.put("uniqueDates", uniqueDates);
            response.put("count", uniqueDates.size());
            response.put("message", "Successfully retrieved unique dates");
            response.put("timestamp", System.currentTimeMillis());
            
            log.info("Retrieved {} unique dates", uniqueDates.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting unique dates", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to get unique dates");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
} 