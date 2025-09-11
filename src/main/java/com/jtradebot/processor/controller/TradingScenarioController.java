package com.jtradebot.processor.controller;

import com.jtradebot.processor.repository.document.TradingScenario;
import com.jtradebot.processor.service.config.TradingScenarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/config/scenarios")
@RequiredArgsConstructor
@Slf4j
public class TradingScenarioController {
    
    private final TradingScenarioService tradingScenarioService;
    
    @GetMapping
    public ResponseEntity<List<TradingScenario>> getAllScenarios() {
        try {
            List<TradingScenario> scenarios = tradingScenarioService.getAllScenarios();
            return ResponseEntity.ok(scenarios);
        } catch (Exception e) {
            log.error("Error retrieving scenarios", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{name}")
    public ResponseEntity<TradingScenario> getScenarioByName(@PathVariable String name) {
        try {
            Optional<TradingScenario> scenario = tradingScenarioService.getScenarioByName(name);
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
            TradingScenario created = tradingScenarioService.createScenario(scenario);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid scenario creation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error creating scenario", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PutMapping("/{name}")
    public ResponseEntity<TradingScenario> updateScenario(@PathVariable String name, @RequestBody TradingScenario scenario) {
        try {
            TradingScenario updated = tradingScenarioService.updateScenario(name, scenario);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid scenario update request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error updating scenario: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @DeleteMapping("/{name}")
    public ResponseEntity<Void> deleteScenario(@PathVariable String name) {
        try {
            tradingScenarioService.deleteScenario(name);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid scenario deletion request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error deleting scenario: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PatchMapping("/{name}/deactivate")
    public ResponseEntity<TradingScenario> deactivateScenario(@PathVariable String name) {
        try {
            TradingScenario deactivated = tradingScenarioService.deactivateScenario(name);
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
