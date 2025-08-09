package com.jtradebot.processor.model;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class PutSRScores {
    private double totalScore;
    private double ltpNotNearSupport;
    private double ema5AsResistance;
    private double ltpNearStrongSupport;
    private double ltpNearStrongResistance;
    private double ltpCrossedDownSRInDownTrend;
    private double ltpLowerThanLastCandleLow;

    public void calculateTotalPoints() {
        double sum = ltpNotNearSupport + ltpNearStrongSupport
                + ltpNearStrongResistance + ltpCrossedDownSRInDownTrend + ema5AsResistance + ltpLowerThanLastCandleLow;

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
