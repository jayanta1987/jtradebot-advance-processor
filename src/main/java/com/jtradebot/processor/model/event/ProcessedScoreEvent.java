package com.jtradebot.processor.model.event;

import com.jtradebot.processor.model.Resistance;
import com.jtradebot.processor.model.Support;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.zerodhatech.models.Tick;
import lombok.Data;
import lombok.ToString;

import java.util.Date;
import java.util.Map;
import java.util.Set;

@Data
@ToString
public class ProcessedScoreEvent {
    private Date eventTimeStamp;
    private Tick tick;
    private CpDetailsEvent cpDetailsEvent;
    private Map<CandleTimeFrameEnum, Set<Support>> supportMap;
    private Map<CandleTimeFrameEnum, Set<Resistance>> resistanceMap;
    private Long instrumentToken;
}
