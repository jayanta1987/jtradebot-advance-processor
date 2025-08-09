package com.jtradebot.processor.repository;

import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;


@Data
public class Transaction {
    private Integer quantity;
    private String time;

    @Indexed
    private String entryKiteOrderId;
    private String exitKiteOrderId;

    private String entryTime;
    private String exitTime;

    private Double entryPrice;
    private Double exitPrice;

    private double minPrice;
    private double maxPrice;

    private Double charges = 0.0;
}