/*
package com.jtradebot.processor.rule;

import com.jtradebot.processor.model.CpDetails;
import com.jtradebot.processor.model.EmaCrossOverConfirmation;
import com.jtradebot.processor.model.enums.EntryReason;
import com.jtradebot.processor.repository.EntryRuleRepository;
import com.jtradebot.processor.rule.util.EmaCrossOverTestUtil;
import com.jtradebot.processor.rule.util.ScalpingCpMovementTestUtil;
import com.jtradebot.processor.strategy.ScalpingTradeStrategy;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static com.jtradebot.processor.model.enums.EntryReason.SCALPING_CP_MOVEMENT;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class ScalpingCpMovementTest {
    private final EntryRuleRepository entryRuleRepository;
    private final ScalpingTradeStrategy scalpingTradeStrategy;

    private static final EntryReason RULE_NAME = SCALPING_CP_MOVEMENT;


    @Test
    void call_valid() {
        CpDetails cpDetails = ScalpingCpMovementTestUtil.getValidCpDetailsForCallBuy();
        EmaCrossOverConfirmation emaCrossOverConfirmation = EmaCrossOverTestUtil.getInValidEmaCrossOverConfirmation();
        boolean isEligible = ruleProcessor.isEligibleForEntry(cpDetails, emaCrossOverConfirmation, RULE_NAME);
        assertTrue(isEligible, "Trade is eligible for Call Buy.");
    }

    @Test
    void call_invalid() {

        EmaCrossOverConfirmation emaCrossOverConfirmation = EmaCrossOverTestUtil.getInValidEmaCrossOverConfirmation();
        boolean isEligible = ruleProcessor.isEligibleForEntry(cpDetails, emaCrossOverConfirmation, RULE_NAME);
        assertFalse(isEligible, "Trade is not eligible for Call Buy.");
    }

    @Test
    void put_valid() {

        EmaCrossOverConfirmation emaCrossOverConfirmation = EmaCrossOverTestUtil.getInValidEmaCrossOverConfirmation();
        boolean isEligible = ruleProcessor.isEligibleForEntry(cpDetails, emaCrossOverConfirmation, RULE_NAME);
        assertTrue(isEligible, "Trade is eligible for Put Buy.");
    }

    @Test
    void put_invalid() {

        CpDetails cpDetails = ScalpingCpMovementTestUtil.getInValidCpDetailsForPutBuy();
        EmaCrossOverConfirmation emaCrossOverConfirmation = EmaCrossOverTestUtil.getInValidEmaCrossOverConfirmation();
        boolean isEligible = ruleProcessor.isEligibleForEntry(cpDetails, emaCrossOverConfirmation, RULE_NAME);
        assertFalse(isEligible, "Trade is not eligible for Put Buy.");
    }

}*/
