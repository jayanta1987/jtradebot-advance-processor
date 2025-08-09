package com.jtradebot.processor.controller;

import com.jtradebot.processor.manager.ApplicationCacheManager;
import com.jtradebot.processor.model.enums.EntryReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rules")
@RequiredArgsConstructor
@Slf4j
public class RuleController {

    // TODO: Old CP-based rule system removed - these services need refactoring
    private final ApplicationCacheManager cacheManager;

    @PostMapping
    public ResponseEntity<String> createRule(@RequestBody String rule) {
        log.warn("createRule called but old CP-based rule system has been removed - returning error");
        return ResponseEntity.badRequest().body("Old CP-based rule system has been removed - needs refactoring");
    }

    @PutMapping
    public ResponseEntity<String> updateRule(@RequestBody String updatedRule) {
        log.warn("updateRule called but old CP-based rule system has been removed - returning error");
        return ResponseEntity.badRequest().body("Old CP-based rule system has been removed - needs refactoring");
    }

    @GetMapping("/{name}")
    public ResponseEntity<String> getRule(@PathVariable EntryReason entryReason) {
        log.warn("getRule called but old CP-based rule system has been removed - returning error");
        return ResponseEntity.badRequest().body("Old CP-based rule system has been removed - needs refactoring");
    }

    @GetMapping
    public ResponseEntity<String> getAllRules() {
        log.warn("getAllRules called but old CP-based rule system has been removed - returning error");
        return ResponseEntity.badRequest().body("Old CP-based rule system has been removed - needs refactoring");
    }
}
