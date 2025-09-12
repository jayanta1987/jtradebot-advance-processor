package com.jtradebot.processor.controller;

import com.jtradebot.processor.repository.document.NoTradeZoneFilter;
import com.jtradebot.processor.service.config.ConfigNoTradeZoneFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/config/no-trade-zone-filters")
@CrossOrigin(origins = {"http://localhost:5173", "https://jtradebot.com", "https://www.jtradebot.com"})
@RequiredArgsConstructor
@Slf4j
public class ConfigNoTradeZoneFilterController {
    
    private final ConfigNoTradeZoneFilterService configNoTradeZoneFilterService;
    
    @GetMapping
    public ResponseEntity<List<NoTradeZoneFilter>> getAllFilters() {
        try {
            List<NoTradeZoneFilter> filters = configNoTradeZoneFilterService.getAllFilters();
            return ResponseEntity.ok(filters);
        } catch (Exception e) {
            log.error("Error retrieving no-trade zone filters", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/enabled")
    public ResponseEntity<List<NoTradeZoneFilter>> getEnabledFilters() {
        try {
            List<NoTradeZoneFilter> filters = configNoTradeZoneFilterService.getEnabledFilters();
            return ResponseEntity.ok(filters);
        } catch (Exception e) {
            log.error("Error retrieving enabled no-trade zone filters", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{filterName}")
    public ResponseEntity<NoTradeZoneFilter> getFilterByName(@PathVariable String filterName) {
        try {
            Optional<NoTradeZoneFilter> filter = configNoTradeZoneFilterService.getFilterByName(filterName);
            return filter.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error retrieving filter: {}", filterName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping
    public ResponseEntity<NoTradeZoneFilter> createFilter(@RequestBody NoTradeZoneFilter filter) {
        try {
            NoTradeZoneFilter created = configNoTradeZoneFilterService.createFilter(filter);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid filter creation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error creating filter", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PatchMapping("/{filterName}")
    public ResponseEntity<NoTradeZoneFilter> updateMultipleFilterValues(@PathVariable String filterName,
                                                                       @RequestBody Map<String, Object> fieldUpdates) {
        try {
            if (fieldUpdates == null || fieldUpdates.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            NoTradeZoneFilter updated = configNoTradeZoneFilterService.updateMultipleFilterValues(filterName, fieldUpdates);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid multiple filter field update request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error updating multiple filter fields: {}", filterName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @DeleteMapping("/{filterName}")
    public ResponseEntity<Void> deleteFilter(@PathVariable String filterName) {
        try {
            configNoTradeZoneFilterService.deleteFilter(filterName);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid filter deletion request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error deleting filter: {}", filterName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PatchMapping("/{filterName}/deactivate")
    public ResponseEntity<NoTradeZoneFilter> deactivateFilter(@PathVariable String filterName) {
        try {
            NoTradeZoneFilter deactivated = configNoTradeZoneFilterService.deactivateFilter(filterName);
            return ResponseEntity.ok(deactivated);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid filter deactivation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error deactivating filter: {}", filterName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
