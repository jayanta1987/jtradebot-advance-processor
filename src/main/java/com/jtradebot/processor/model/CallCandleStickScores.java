package com.jtradebot.processor.model;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class CallCandleStickScores {

    private double totalScore;
    private double doji;
    private double hammer;
    private double invertedHammer;
    private double bullishEngulfing;
    private double bullishHarami;
    private double bullishDojiStar;
    private double bullishMorningStar;
    private double bullishMarubozu;
    private double longLowerShadow;
    private double greenCandle;

    public void calculateTotalScore() {
        totalScore = doji + hammer + invertedHammer + bullishEngulfing + bullishHarami +
                bullishDojiStar + bullishMorningStar + bullishMarubozu + longLowerShadow + greenCandle;
    }

    public double getTotalScore() {
        calculateTotalScore();
        return totalScore;
    }
}
