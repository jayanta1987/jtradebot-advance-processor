package com.jtradebot.processor.model;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class PutEmaRsiScores {
    private double totalScore;
    private double ema5CrossDownEma34;
    private double ema5CrossDownEma14;
    private double ema5CrossDownEma200;
    private double crossedDownMultipleEmas;
    //private double ltpBelowLowestEma;
    private double ltpCrossedDownEma5;
    private double ltpCrossedDownEma9;
    private double ltpCrossedDownEma14;
    private double ltpLessThanEma200;
    private double rsiBelow45;
    private double rsiDivergenceBearish;

    public void calculateTotalPoints() {
        double sum = ema5CrossDownEma34 + ema5CrossDownEma200 + ema5CrossDownEma14 + crossedDownMultipleEmas +
                ltpCrossedDownEma5 + ltpCrossedDownEma9 + ltpCrossedDownEma14 + ltpLessThanEma200 + rsiBelow45 + rsiDivergenceBearish;
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
