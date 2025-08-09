package com.jtradebot.processor.model;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class CallSRScores {
    private double totalScore;
    private double ema5AsSupport;
    private double ltpNotNearResistance;
    private double ltpNearStrongSupport;
    private double ltpNearStrongResistance;
    private double ltpCrossedUpSRInUpTrend;
    private double ltpHigherThanLastCandleHigh;

    public void calculateTotalPoints() {
        double sum = ltpNotNearResistance + ltpNearStrongSupport
                + ltpNearStrongResistance + ltpCrossedUpSRInUpTrend + ema5AsSupport + ltpHigherThanLastCandleHigh;
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
