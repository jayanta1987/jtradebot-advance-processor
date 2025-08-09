package com.jtradebot.processor.model;

import com.jtradebot.processor.model.enums.CrossTypeEnum;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class IntraDayConfirmationScores {

    private CrossTypeEnum crossType;
    private double ema5CrossedEma34;
    private double ema5CrossedEma14;
    private double ema5CrossedEma200;
    private double crossedMultipleEmas;
    private double ltpCrossedEma5;
    private double ltpCrossedEma9;
    private double ltpCrossedEma14;

    private double totalScore;

    public void calculateTotalScore() {
        double sum = ema5CrossedEma34 + ema5CrossedEma200 + ema5CrossedEma14 + crossedMultipleEmas +
                ltpCrossedEma5 + ltpCrossedEma9 + ltpCrossedEma14;
        if (sum < 0) {
            totalScore = 0;
        } else {
            totalScore = sum;
        }
    }

    public double getTotalScore() {
        calculateTotalScore();
        return totalScore;
    }


}
