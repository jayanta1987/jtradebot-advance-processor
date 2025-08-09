package com.jtradebot.tickstore.repository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "calculated_ticks")
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class CalculatedTick {
    private TickModel tick;
    // CP details removed - no longer used in new strategy
}
