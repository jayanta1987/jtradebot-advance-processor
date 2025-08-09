package com.jtradebot.processor.manager;

import com.jtradebot.processor.model.CpDetails;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.model.enums.TradeMode;
import com.jtradebot.processor.order.CallEntryService;
import com.jtradebot.processor.order.PutEntryService;
import com.jtradebot.processor.strategy.TradeStrategyFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StrategyManager {
    private final TradeStrategyFactory tradeStrategyFactory;
    private final CallEntryService callEntryService;
    private final PutEntryService putEntryService;

    public void findStrategyBasedEntry(CpDetails cpDetails, TradeMode tradeMode) {
        tradeStrategyFactory.getStrategy(tradeMode)
                .findEntry(cpDetails).ifPresent(orderInfo -> {
                    OrderTypeEnum orderType = orderInfo.getCpDetails().getOrderType();
                    switch (orderType) {
                        case CALL_BUY -> callEntryService.createOrder(orderInfo, tradeMode);
                        case PUT_BUY -> putEntryService.createOrder(orderInfo, tradeMode);
                    }
                });
    }

}
