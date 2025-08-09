package com.jtradebot.processor.repository.document;

import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document(collection = "instruments")
@Data
public class Instrument {
    @MongoId
    private String id;
    private long instrumentToken;
    private String exchangeToken;
    private String tradingSymbol;
    private String name;
    private String instrumentType;
    private String segment;
    private String exchange;
    private String strike;
    private String expiry;
}