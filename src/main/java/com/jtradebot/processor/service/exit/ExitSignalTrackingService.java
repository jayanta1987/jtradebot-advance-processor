package com.jtradebot.processor.service.exit;

import com.jtradebot.processor.model.MilestoneSystem;
import com.jtradebot.processor.model.enums.ExitReasonEnum;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.repository.document.JtradeOrder;
import com.jtradebot.processor.service.entry.DynamicRuleEvaluatorService;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExitSignalTrackingService {

    private final DynamicRuleEvaluatorService dynamicRuleEvaluatorService;

    /**
     * Check if order should be exited based on strategy reversal
     */
    public boolean shouldExitBasedOnStrategy(JtradeOrder order, Tick tick) {
        try {
            // For CALL orders, exit if PUT entry conditions are met (strategy reversal)
            if (order.getOrderType() == OrderTypeEnum.CALL_BUY) {
                if (dynamicRuleEvaluatorService.shouldMakeCallExit(tick)) {
                    log.info("Strategy-based exit triggered for CALL order: {} - PUT entry conditions met", order.getId());
                    return true;
                }
            }
            // For PUT orders, exit if CALL entry conditions are met (strategy reversal)
            else if (order.getOrderType() == OrderTypeEnum.PUT_BUY) {
                if (dynamicRuleEvaluatorService.shouldMakePutExit(tick)) {
                    log.info("Strategy-based exit triggered for PUT order: {} - CALL entry conditions met", order.getId());
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("Error checking strategy-based exit for order: {}", order.getId(), e);
        }

        return false;
    }



    private Double getCurrentOptionPriceForManualCheck(Double currentPrice) {
        // For manual checks, use the provided price directly
        // This is used when we don't have a tick object
        return currentPrice;
    }


}
