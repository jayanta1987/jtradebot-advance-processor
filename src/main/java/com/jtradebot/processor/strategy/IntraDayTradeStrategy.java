package com.jtradebot.processor.strategy;

import com.jtradebot.processor.manager.EmaCrossTrackingManager;
import com.jtradebot.processor.model.CpDetails;
import com.jtradebot.processor.model.EmaCrossOverConfirmation;
import com.jtradebot.processor.model.enums.EntryReason;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.repository.document.EntryRule;
import com.jtradebot.processor.repository.document.OrderInfo;
import com.jtradebot.processor.rule.EntryRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.FIVE_MIN;
import static com.jtradebot.processor.model.enums.TradeMode.INTRA_DAY;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntraDayTradeStrategy implements TradeStrategy {
    private final EmaCrossTrackingManager emaCrossTrackingManager;
    private final EntryRuleService entryRuleService;

    private static final String __IS_CURRENT_CANDLE_CROSSING_EMA5 = "__IS_CURRENT_CANDLE_CROSSING_EMA5";
    private static final String __IS_EMA5_CROSSED_EMA34 = "__IS_EMA5_CROSSED_EMA34";
    private static final String __IS_EMA5_CROSSED_EMA14 = "__IS_EMA5_CROSSED_EMA14";

    @Override
    public Optional<OrderInfo> findEntry(CpDetails cpDetails) {
        log.info("INTRA_DAY -> Finding ENTRY.....");

        OrderTypeEnum orderType = cpDetails.getOrderType();

        cpDetails.setOrderType(orderType);

        // five min ema cross over confirmation
        EmaCrossOverConfirmation emaCrossOverConfirmation = emaCrossTrackingManager.getEmaCrossOverConfirmation(orderType, FIVE_MIN);
        //logEmaCrossOverConfirmation();

        // Reset entry data map for any reversal
        emaCrossTrackingManager.checkEmaCrossReversal(cpDetails.getMidAvgCp());

        // Check if any special entry condition is met
        Optional<EntryReason> entryReasonEnum = entryRuleService.getAllActiveRules(INTRA_DAY).stream()
                .map(EntryRule::getEntryReason)
                .filter(reason -> {
                    Map<String, Object> computedValues = computeValuesForEntryReason(emaCrossOverConfirmation, reason);
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

    private void logEmaCrossOverConfirmation() {
        // log if any of these true
        emaCrossTrackingManager.getEntryDataMap().forEach((orderType, data) -> {
            log.info("orderType: {}, isCurrentCandleCrossingEma5: {}, isEma5CrossedEma14: {}, isEma5CrossedEma34: {}",
                    orderType, data.isCurrentCandleCrossingEma(),
                    data.isEma5CrossedEma14(), data.isEma5CrossedEma34());
        });
    }

    private Map<String, Object> computeValuesForEntryReason(EmaCrossOverConfirmation emaCrossOverConfirmation, EntryReason entryReason) {
        Map<String, Object> computedValues = new HashMap<>();
        switch (entryReason) {
            case EMA_5_14_CROSSOVER -> {
                computedValues.put(__IS_CURRENT_CANDLE_CROSSING_EMA5, emaCrossOverConfirmation.isCurrentCandleCrossingEma());
                computedValues.put(__IS_EMA5_CROSSED_EMA14, emaCrossOverConfirmation.isEma5CrossedEma14());
            }
            case EMA_5_34_CROSSOVER -> {
                computedValues.put(__IS_CURRENT_CANDLE_CROSSING_EMA5, emaCrossOverConfirmation.isCurrentCandleCrossingEma());
                computedValues.put(__IS_EMA5_CROSSED_EMA34, emaCrossOverConfirmation.isEma5CrossedEma34());
            }
            default -> {
            }
        }
        return computedValues;
    }

    private OrderInfo getTradeEntryInfo(CpDetails cpDetails) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setCpDetails(cpDetails);
        return orderInfo;
    }
}
