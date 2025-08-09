package com.jtradebot.processor.model;

import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import lombok.Data;
import lombok.ToString;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@ToString
public class PutData {
    private double maxScore;
    private double minScore;
    private double avgScore;
    private double finalScore;

    private boolean validPut;

    private Map<CandleTimeFrameEnum, Set<Support>> supportMap;
    private Map<CandleTimeFrameEnum, Set<Resistance>> resistanceMap;

    private List<PutScoresTF> putScoresTFList;
    private List<String> avgCandleHeights;
}