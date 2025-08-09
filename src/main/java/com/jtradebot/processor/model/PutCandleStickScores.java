package com.jtradebot.processor.model;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class PutCandleStickScores {

    private double totalScore;
    private double doji;
    private double shootingStar;
    private double hangingMan;
    private double bearishEngulfing;
    private double bearishHarami;
    private double bearishDojiStar;
    private double bearishEveningStar;
    private double bearishMarubozu;
    private double longUpperShadow;
    private double redCandle;

    public void calculateTotalScore() {
        totalScore = doji + shootingStar + hangingMan + bearishEngulfing + bearishHarami +
                bearishDojiStar + bearishEveningStar + bearishMarubozu + longUpperShadow + redCandle;
    }

    public double getTotalScore() {
        calculateTotalScore();
        return totalScore;
    }

}
