package com.jtradebot.processor.model.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.jtradebot.processor.repository.document.TickDocument;
import lombok.Data;

import java.util.Date;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class TickEvent {
    private Date eventTimeStamp;
    private TickDocument tickDocument;
}
