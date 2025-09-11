package com.jtradebot.processor.controller;

import com.jtradebot.processor.repository.document.NoTradeZoneFilter;
import com.jtradebot.processor.service.config.NoTradeZoneFilterService;
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
@RequiredArgsConstructor
@Slf4j
public class NoTradeZoneFilterController {
    
    private final NoTradeZoneFilterService noTradeZoneFilterService;
    
    @GetMapping
    public ResponseEntity<List<NoTradeZoneFilter>> getAllFilters() {
        try {
            List<NoTradeZoneFilter> filters = noTradeZoneFilterService.getAllFilters();
            return ResponseEntity.ok(filters);
        } catch (Exception e) {
            log.error("Error retrieving no-trade zone filters", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/enabled")
    public ResponseEntity<List<NoTradeZoneFilter>> getEnabledFilters() {
        try {
            List<NoTradeZoneFilter> filters = noTradeZoneFilterService.getEnabledFilters();
            return ResponseEntity.ok(filters);
        } catch (Exception e) {
            log.error("Error retrieving enabled no-trade zone filters", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{filterName}")
    public ResponseEntity<NoTradeZoneFilter> getFilterByName(@PathVariable String filterName) {
        try {
            Optional<NoTradeZoneFilter> filter = noTradeZoneFilterService.getFilterByName(filterName);
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
            NoTradeZoneFilter created = noTradeZoneFilterService.createFilter(filter);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid filter creation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error creating filter", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PutMapping("/{filterName}")
    public ResponseEntity<NoTradeZoneFilter> updateFilter(@PathVariable String filterName, @RequestBody NoTradeZoneFilter filter) {
        try {
            NoTradeZoneFilter updated = noTradeZoneFilterService.updateFilter(filterName, filter);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid filter update request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error updating filter: {}", filterName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PatchMapping("/{filterName}/field/{fieldName}")
    public ResponseEntity<NoTradeZoneFilter> updateFilterValue(@PathVariable String filterName,
                                                             @PathVariable String fieldName,
                                                             @RequestBody Map<String, Object> request) {
        try {
            Object newValue = request.get("value");
            if (newValue == null) {
                return ResponseEntity.badRequest().build();
            }
            
            NoTradeZoneFilter updated = noTradeZoneFilterService.updateFilterValue(filterName, fieldName, newValue);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid filter field update request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error updating filter field: {} - {}", filterName, fieldName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @DeleteMapping("/{filterName}")
    public ResponseEntity<Void> deleteFilter(@PathVariable String filterName) {
        try {
            noTradeZoneFilterService.deleteFilter(filterName);
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
            NoTradeZoneFilter deactivated = noTradeZoneFilterService.deactivateFilter(filterName);
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
