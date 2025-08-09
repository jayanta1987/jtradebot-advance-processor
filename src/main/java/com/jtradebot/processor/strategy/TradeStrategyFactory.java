package com.jtradebot.processor.strategy;

import com.jtradebot.processor.model.enums.TradeMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TradeStrategyFactory {
    private final ScalpingTradeStrategy scalpingTradeStrategy;
    private final IntraDayTradeStrategy intraDayTradeStrategy;

    public TradeStrategy getStrategy(TradeMode tradeMode) {
        return switch (tradeMode) {
            case SCALPING -> scalpingTradeStrategy;
            case INTRA_DAY -> intraDayTradeStrategy;
        };
    }

}