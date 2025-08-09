package com.jtradebot.processor.strategy;

import com.jtradebot.processor.model.CpDetails;
import com.jtradebot.processor.model.enums.EntryReason;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.repository.document.EntryRule;
import com.jtradebot.processor.repository.document.Ntp;
import com.jtradebot.processor.repository.document.OrderInfo;
import com.jtradebot.processor.rule.EntryRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.jtradebot.processor.model.enums.TradeMode.SCALPING;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScalpingTradeStrategy implements TradeStrategy {
    private final EntryRuleService entryRuleService;

    private static final String __IS_ALL_CP_AVG_JUMP = "__IS_ALL_CP_AVG_JUMP";
    private static final String __NTP_5_MIN = "__NTP_5_MIN";
    private static final String __NTP_15_MIN = "__NTP_15_MIN";
    private static final String __IS_NO_TRADE_ZONE = "__IS_NO_TRADE_ZONE";
    private static final String __IS_NO_DYNAMIC_TRADE_ZONE = "__IS_NO_DYNAMIC_TRADE_ZONE";
    private static final String __IS_ANY_CP_AVG_JUMP = "__IS_ANY_CP_AVG_JUMP";
    private static final String __IS_ANY_CP_HIGH = "__IS_ANY_CP_HIGH";
    private static final String __IS_FUTURE_SIGNAL_STRONG = "__IS_FUTURE_SIGNAL_STRONG";
    private static final String __IS_NTP_OPTIMAL = "__IS_NTP_OPTIMAL";
    private static final String __FUTURE_SIGNAL_SCORE = "__FUTURE_SIGNAL_SCORE";
    @Override
    public Optional<OrderInfo> findEntry(CpDetails cpDetails) {
        //log.info("SCALPING -> Finding ENTRY.....");

        OrderTypeEnum orderType = cpDetails.getOrderType();

        cpDetails.setOrderType(orderType);

        // Check if any special entry condition is met
        Optional<EntryReason> entryReasonEnum = entryRuleService.getAllActiveRules(SCALPING).stream()
                .map(EntryRule::getEntryReason)
                .filter(reason -> {
                    Map<String, Object> computedValues = computeValuesForEntryReason(cpDetails, reason);
                    return entryRuleService.evaluateRule(reason, cpDetails, computedValues);
                }).findFirst();
        OrderInfo orderInfo = getTradeEntryInfo(cpDetails);
        entryReasonEnum.ifPresent(orderInfo::setEntryReason);

        if (orderInfo.getEntryReason() != null) {
            return Optional.of(orderInfo);
        } else {
            return Optional.empty();
        }
    }
    public OrderInfo getTradeEntryInfo(CpDetails cpDetails) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setCpDetails(cpDetails);
        return orderInfo;
    }

    private Map<String, Object> computeValuesForEntryReason(CpDetails cpDetails, EntryReason entryReason) {
        Ntp ntp = cpDetails.getNtp();
        return switch (entryReason) {
            case SCALPING_LONG_CP_MOVEMENT, SCALPING_FUTURE_VOLUME_SURGE -> {
                Map<String, Object> computedValues = new HashMap<>();
                computedValues.put(__IS_ALL_CP_AVG_JUMP,
                        DynamicValueCalculator.isAllCpAvgJump(8, 0.5, cpDetails) // good results
                                || DynamicValueCalculator.isAllCpAvgJump(8, 0.5, cpDetails));
                computedValues.put(__NTP_15_MIN, ntp.getNtp15Min());
                computedValues.put(__IS_NO_TRADE_ZONE, DynamicValueCalculator.isNoTradeZone(cpDetails));
                computedValues.put(__IS_NO_DYNAMIC_TRADE_ZONE, DynamicValueCalculator.isNoDynamicTradeZone(cpDetails));
                computedValues.put(__IS_ANY_CP_AVG_JUMP,
                        DynamicValueCalculator.isAnyCpAvgJump(8.5, 0.5, cpDetails));
                computedValues.put(__IS_ANY_CP_HIGH,
                        DynamicValueCalculator.isEnhancedScalpingOpportunity(7.0, cpDetails));
                
                // Enhanced future signal logic
                computedValues.put(__IS_FUTURE_SIGNAL_STRONG, 
                        DynamicValueCalculator.isFutureSignalStrong(cpDetails));
                computedValues.put(__IS_NTP_OPTIMAL, 
                        DynamicValueCalculator.isNtpOptimalForTrading(cpDetails));
                computedValues.put(__FUTURE_SIGNAL_SCORE, 
                        DynamicValueCalculator.getFutureSignalScore(cpDetails));
                
                yield computedValues;
            }
            case SCALPING_CP_MOVEMENT -> {
                Map<String, Object> computedValues = new HashMap<>();
                computedValues.put(__NTP_5_MIN, ntp.getNtp5Min());
                
                // Enhanced future signal logic for CP movement
                computedValues.put(__IS_FUTURE_SIGNAL_STRONG, 
                        DynamicValueCalculator.isFutureSignalStrong(cpDetails));
                computedValues.put(__FUTURE_SIGNAL_SCORE, 
                        DynamicValueCalculator.getFutureSignalScore(cpDetails));
                
                yield computedValues;
            }
            default -> new HashMap<>();
        };
    }

}
