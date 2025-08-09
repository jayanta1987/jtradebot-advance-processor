package com.jtradebot.processor.model;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class CallEmaRsiScores {
    private double totalScore;
    private double ema5CrossOverEma34;
    private double ema5CrossOverEma14;
    private double ema5CrossOverEma200;
    private double crossedUpMultipleEmas;
    //private double ltpAboveHighestEma;
    private double ltpCrossedUpEma5;
    private double ltpCrossedUpEma9;
    private double ltpCrossedUpEma14;
    private double ltpGreaterThanEma200;
    private double rsiAbove55;
    private double rsiDivergenceBullish;

    public void calculateTotalPoints() {
        double sum = ema5CrossOverEma34 + ema5CrossOverEma200 + ema5CrossOverEma14 + crossedUpMultipleEmas +
                ltpCrossedUpEma5 + ltpCrossedUpEma9 + ltpCrossedUpEma14 + ltpGreaterThanEma200 + rsiAbove55 + rsiDivergenceBullish;
        if (sum < 0) {
            totalScore = 0;
        } else {
            totalScore = sum;
        }
    }

    public double getTotalScore() {
        calculateTotalPoints();
        return totalScore;
    }
}
