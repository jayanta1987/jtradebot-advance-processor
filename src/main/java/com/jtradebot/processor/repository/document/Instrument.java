package com.jtradebot.processor.repository.document;

import lombok.Data;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document(collection = "instruments")
@CompoundIndex(name = "name_instrumentType_segment_idx", def = "{'name': 1, 'instrumentType': 1, 'segment': 1}")
@CompoundIndex(name = "tradingSymbol_idx", def = "{'tradingSymbol': 1}")
@CompoundIndex(name = "name_instrumentType_idx", def = "{'name': 1, 'instrumentType': 1}")
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
    @Indexed // Index for performance optimization on createdAt queries
    private String createdAt; // IST date format like IST-2025-08-19
}