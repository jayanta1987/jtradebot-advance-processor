package com.jtradebot.processor.model;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class PutFutureScores {
    private double totalScore;
    private double vwapSignal;
    private double oiTrendSignal;
    private double pvsiScore;

    public void calculateTotalScore() {
        totalScore = vwapSignal + oiTrendSignal + pvsiScore;
        if (totalScore < 0) {
            totalScore = 0;
        }
    }

    public double getTotalScore() {
        calculateTotalScore();
        return totalScore;
    }
}
