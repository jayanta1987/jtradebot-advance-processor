package com.jtradebot.processor.model;

import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import lombok.Data;
import lombok.ToString;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@ToString
public class CallData {
    private double maxScore;
    private double minScore;
    private double avgScore;
    private double finalScore;

    private boolean validCall;

    private Map<CandleTimeFrameEnum, Set<Support>> supportMap;
    private Map<CandleTimeFrameEnum, Set<Resistance>> resistanceMap;

    private List<CallScoresTF> callScoresTFList;
    private List<String> avgCandleHeights;
}