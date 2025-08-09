/*
package com.jtradebot.processor.rule;

import com.jtradebot.processor.model.CpDetails;
import com.jtradebot.processor.model.EmaCrossOverConfirmation;
import com.jtradebot.processor.repository.EntryRuleRepository;
import com.jtradebot.processor.rule.util.EmaCrossOverTestUtil;
import com.jtradebot.processor.rule.util.ScalpingLongCpMovementTestUtil;
import com.jtradebot.processor.strategy.enums.EntryReason;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static com.jtradebot.processor.strategy.enums.EntryReason.SCALPING_LONG_CP_MOVEMENT;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class ScalpingLongCpMovementTest {
    private final RuleProcessor ruleProcessor;
    private final EntryRuleRepository entryRuleRepository;

    private static final EntryReason RULE_NAME = SCALPING_LONG_CP_MOVEMENT;


    @Test
    void call_valid() {
        entryRuleRepository.findByName(RULE_NAME.name())
                .orElseThrow(() -> new IllegalArgumentException("Rule not found in DB: " + RULE_NAME));
        CpDetails cpDetails = ScalpingLongCpMovementTestUtil.getValidCpDetailsForCallBuy();
        EmaCrossOverConfirmation emaCrossOverConfirmation = EmaCrossOverTestUtil.getInValidEmaCrossOverConfirmation();
        boolean isEligible = ruleProcessor.isEligibleForEntry(cpDetails, emaCrossOverConfirmation, RULE_NAME);
        assertTrue(isEligible, "Trade is eligible for Call Buy.");
    }

    @Test
    void call_invalid() {
        entryRuleRepository.findByName(RULE_NAME.name())
                .orElseThrow(() -> new IllegalArgumentException("Rule not found in DB: " + RULE_NAME));
        CpDetails cpDetails = ScalpingLongCpMovementTestUtil.getInValidCpDetailsForCallBuy();
        EmaCrossOverConfirmation emaCrossOverConfirmation = EmaCrossOverTestUtil.getInValidEmaCrossOverConfirmation();
        boolean isEligible = ruleProcessor.isEligibleForEntry(cpDetails, emaCrossOverConfirmation, RULE_NAME);
        assertFalse(isEligible, "Trade is not eligible for Call Buy.");
    }

    @Test
    void put_valid() {
        entryRuleRepository.findByName(RULE_NAME.name())
                .orElseThrow(() -> new IllegalArgumentException("Rule not found in DB: " + RULE_NAME));
        CpDetails cpDetails = ScalpingLongCpMovementTestUtil.getValidCpDetailsForPutBuy();
        EmaCrossOverConfirmation emaCrossOverConfirmation = EmaCrossOverTestUtil.getInValidEmaCrossOverConfirmation();
        boolean isEligible = ruleProcessor.isEligibleForEntry(cpDetails, emaCrossOverConfirmation, RULE_NAME);
        assertTrue(isEligible, "Trade is eligible for Put Buy.");
    }

    @Test
    void put_invalid() {
        entryRuleRepository.findByName(RULE_NAME.name())
                .orElseThrow(() -> new IllegalArgumentException("Rule not found in DB: " + RULE_NAME));
        CpDetails cpDetails = ScalpingLongCpMovementTestUtil.getInValidCpDetailsForPutBuy();
        EmaCrossOverConfirmation emaCrossOverConfirmation = EmaCrossOverTestUtil.getInValidEmaCrossOverConfirmation();
        boolean isEligible = ruleProcessor.isEligibleForEntry(cpDetails, emaCrossOverConfirmation, RULE_NAME);
        assertFalse(isEligible, "Trade is not eligible for Put Buy.");
    }

}*/
