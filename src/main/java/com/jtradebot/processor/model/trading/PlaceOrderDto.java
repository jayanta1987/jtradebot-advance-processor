package com.jtradebot.processor.model.trading;

import com.jtradebot.processor.model.enums.OrderTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class PlaceOrderDto {

    private String tradingSymbol;
    private Double latestIndexPrice;
    private OrderTypeEnum orderType;
    private LocalDateTime entryTime;
    private String referenceOrderId;
    private Set<Double> supportLevels;
    private Set<Double> resistanceLevels;
}
