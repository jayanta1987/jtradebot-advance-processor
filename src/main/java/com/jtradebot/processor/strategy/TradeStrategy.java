package com.jtradebot.processor.strategy;

import com.jtradebot.processor.model.CpDetails;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.repository.document.OrderInfo;
import com.jtradebot.processor.repository.document.TradeOrder;

import java.util.Optional;

public interface TradeStrategy {
    Optional<OrderInfo> findEntry(CpDetails cpDetails);
}
