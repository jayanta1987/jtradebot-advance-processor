package com.jtradebot.processor.controller;

import com.jtradebot.processor.manager.ApplicationCacheManager;
import com.jtradebot.processor.model.enums.EntryReason;
import com.jtradebot.processor.repository.document.EntryRule;
import com.jtradebot.processor.rule.EntryRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rules")
@RequiredArgsConstructor
public class RuleController {

    private final EntryRuleService entryRuleService;
    private final ApplicationCacheManager cacheManager;

    @PostMapping
    public ResponseEntity<EntryRule> createRule(@RequestBody EntryRule rule) {
        entryRuleService.saveRule(rule);
        return ResponseEntity.ok(rule);
    }

    @PutMapping
    public ResponseEntity<EntryRule> updateRule(@RequestBody EntryRule updatedRule) {
        EntryRule existingRule = cacheManager.getRule(updatedRule.getEntryReason());
        existingRule.setConditions(updatedRule.getConditions());
        existingRule.setCombineWith(updatedRule.getCombineWith());
        existingRule.setActive(updatedRule.isActive());
        existingRule.setExitNtp(updatedRule.getExitNtp());
        existingRule.setMaxQty(updatedRule.getMaxQty());
        existingRule.setTradeMode(updatedRule.getTradeMode());

        entryRuleService.saveRule(existingRule);
        return ResponseEntity.ok(existingRule);
    }

    @GetMapping("/{name}")
    public ResponseEntity<EntryRule> getRule(@PathVariable EntryReason entryReason) {
        EntryRule rule = cacheManager.getRule(entryReason);
        return ResponseEntity.ok(rule);
    }

    @GetMapping
    public ResponseEntity<List<EntryRule>> getAllRules() {
        List<EntryRule> rules = cacheManager.getAllRules();
        return ResponseEntity.ok(rules);
    }
}
