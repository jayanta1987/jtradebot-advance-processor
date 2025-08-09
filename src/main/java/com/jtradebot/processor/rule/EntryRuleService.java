package com.jtradebot.processor.rule;

import com.jtradebot.processor.manager.ApplicationCacheManager;
import com.jtradebot.processor.model.CpDetails;
import com.jtradebot.processor.model.enums.EntryReason;
import com.jtradebot.processor.model.enums.TradeMode;
import com.jtradebot.processor.repository.EntryRuleRepository;
import com.jtradebot.processor.repository.document.EntryRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EntryRuleService {

    private final EntryRuleRepository entryRuleRepository;
    private final ApplicationCacheManager cacheManager;

    public boolean evaluateRule(EntryReason reason, CpDetails cpDetails, Map<String, Object> computedValues) {
        try {
            EntryRule rule = cacheManager.getRule(reason); // Cached method call
            return RuleEvaluator.evaluate(cpDetails, rule, computedValues);
        } catch (Exception e) {
            log.error("Error evaluating rule: {}", reason.name(), e);
            return false;
        }
    }

    public EntryRule getRule(EntryReason reason) {
        return cacheManager.getRule(reason);
    }

    public void saveRule(EntryRule rule) {
        entryRuleRepository.save(rule);
    }

    public void deleteRule(String ruleId) {
        entryRuleRepository.deleteById(ruleId);
    }

    public List<EntryRule> getAllActiveRules(TradeMode tradeMode) {
        return cacheManager.getRules(tradeMode, true);
    }

}
