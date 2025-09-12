package com.jtradebot.processor.controller;

import com.jtradebot.processor.config.TradingConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config/refresh")
@CrossOrigin(origins = {"http://localhost:5173", "https://jtradebot.com", "https://www.jtradebot.com"})
@RequiredArgsConstructor
@Slf4j
public class ConfigRefreshController {
    
    private final TradingConfigurationService tradingConfigurationService;
    
    @PostMapping("/mongodb")
    public ResponseEntity<String> refreshConfigurationFromMongoDB() {
        try {
            tradingConfigurationService.refreshConfigurationFromMongoDB();
            return ResponseEntity.ok("Configuration refreshed successfully from MongoDB");
        } catch (Exception e) {
            log.error("Error refreshing configuration from MongoDB", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Configuration refresh failed: " + e.getMessage());
        }
    }
}
