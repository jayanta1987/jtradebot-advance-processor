package com.jtradebot.processor.manager;

import com.jtradebot.processor.model.enums.EntryReason;
import com.jtradebot.processor.model.enums.TradeMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApplicationCacheManager {

    // TODO: Old CP-based rule system removed - these methods need refactoring

    public Object getRule(EntryReason entryReason) {
        log.warn("getRule called but old CP-based rule system has been removed - returning null");
        return null;
    }

    public List<Object> getRules(TradeMode tradeMode, boolean isActive) {
        log.warn("getRules called but old CP-based rule system has been removed - returning empty list");
        return List.of();
    }

    public List<Object> getAllRules() {
        log.warn("getAllRules called but old CP-based rule system has been removed - returning empty list");
        return List.of();
    }
}
