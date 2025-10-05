package com.jtradebot.processor.controller;

import com.jtradebot.processor.repository.document.TradingScenario;
import com.jtradebot.processor.service.config.ConfigTradingScenarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/config/scenarios")
@CrossOrigin(origins = {"http://localhost:5173", "https://jtradebot.com", "https://www.jtradebot.com"})
@RequiredArgsConstructor
@Slf4j
public class ConfigTradingScenarioController {
    
    private final ConfigTradingScenarioService configTradingScenarioService;
    
    @GetMapping
    public ResponseEntity<List<TradingScenario>> getAllScenarios() {
        try {
            List<TradingScenario> scenarios = configTradingScenarioService.getAllScenarios();
            return ResponseEntity.ok(scenarios);
        } catch (Exception e) {
            log.error("Error retrieving scenarios", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{name}")
    public ResponseEntity<TradingScenario> getScenarioByName(@PathVariable String name) {
        try {
            Optional<TradingScenario> scenario = configTradingScenarioService.getScenarioByName(name);
            return scenario.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error retrieving scenario: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping
    public ResponseEntity<TradingScenario> createScenario(@RequestBody TradingScenario scenario) {
        try {
            TradingScenario created = configTradingScenarioService.createScenario(scenario);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid scenario creation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error creating scenario", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PatchMapping("/{name}")
    public ResponseEntity<TradingScenario> updateMultipleScenarioFields(@PathVariable String name, 
                                                                       @RequestBody Map<String, Object> fieldUpdates) {
        try {
            if (fieldUpdates == null || fieldUpdates.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            TradingScenario updated = configTradingScenarioService.updateMultipleScenarioFields(name, fieldUpdates);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid multiple scenario field update request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error updating multiple scenario fields: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @DeleteMapping("/{name}")
    public ResponseEntity<Void> deleteScenario(@PathVariable String name) {
        try {
            configTradingScenarioService.deleteScenario(name);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid scenario deletion request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error deleting scenario: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PatchMapping("/{name}/activate")
    public ResponseEntity<TradingScenario> activateScenario(@PathVariable String name) {
        try {
            TradingScenario activated = configTradingScenarioService.activateScenario(name);
            return ResponseEntity.ok(activated);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid scenario activation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error activating scenario: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PatchMapping("/{name}/deactivate")
    public ResponseEntity<TradingScenario> deactivateScenario(@PathVariable String name) {
        try {
            TradingScenario deactivated = configTradingScenarioService.deactivateScenario(name);
            return ResponseEntity.ok(deactivated);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid scenario deactivation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error deactivating scenario: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
