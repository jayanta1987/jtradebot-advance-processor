package com.jtradebot.processor.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.jtradebot.processor.repository.document.TradeOrder;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true) // unknown annotation
public class TradeOrderResponse {
    private Double totalReleasedProfit;
    private Double totalIndexPoints;
    private Integer noOfOrders;

    private List<Order> tradeOrders;

    private Integer maxOrderQuantity;
    private Integer validOrderQuantity;

    private Double currentIndexPrice;
    private Double currentPrice;
    private Double unUtilizedFundBalance;
    private Double totalProfitFromClosedOrders;
    private Double currentInvestedAmount;

}
