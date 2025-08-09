package com.jtradebot.processor.manager;

import com.jtradebot.processor.model.enums.EntryReason;
import com.jtradebot.processor.model.enums.TradeMode;
import com.jtradebot.processor.repository.EntryRuleRepository;
import com.jtradebot.processor.repository.document.EntryRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApplicationCacheManager {

    private final EntryRuleRepository entryRuleRepository;

    @Cacheable(value = "entryRules", key = "#entryReason")
    public EntryRule getRule(EntryReason entryReason) {
        return entryRuleRepository.findByEntryReason(entryReason)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + entryReason.name()));
    }
    @Cacheable(value = "entryRules")
    public List<EntryRule> getRules(TradeMode tradeMode, boolean isActive) {
        return entryRuleRepository.findByTradeModeAndActive(tradeMode, isActive);
    }
    @Cacheable(value = "entryRules")
    public List<EntryRule> getAllRules() {
        return entryRuleRepository.findAll();
    }

}
