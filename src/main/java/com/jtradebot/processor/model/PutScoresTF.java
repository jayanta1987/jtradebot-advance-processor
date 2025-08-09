package com.jtradebot.processor.model;

import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PutScoresTF {

    @Getter
    private PutEmaRsiScores putEmaRsiScores;
    @Getter
    private PutSRScores putSRScores;
    @Getter
    private PutCandleStickScores putCandleStickScores;
    @Getter
    private PutCandleStickScores previousPutCandleStickScores;

    private double totalScore;
    @Getter
    private CandleTimeFrameEnum timeFrame;

    public void calculateTotalScore() {
        if (putEmaRsiScores != null && putSRScores != null && putCandleStickScores != null && previousPutCandleStickScores != null) {
            totalScore = putEmaRsiScores.getTotalScore() + putSRScores.getTotalScore() +
                    putCandleStickScores.getTotalScore() + previousPutCandleStickScores.getTotalScore();
        }
    }

    public double getTotalScore() {
        calculateTotalScore();
        return totalScore;
    }

    public boolean checkValidScore() {
        return putEmaRsiScores.getTotalScore() > 1
                && putSRScores.getTotalScore() > 1
                && putCandleStickScores.getTotalScore() > 1
                && previousPutCandleStickScores.getTotalScore() > 1;
    }

    public boolean checkValidCandleStick() {
        return putCandleStickScores.getTotalScore() >= 2
                || putCandleStickScores.getTotalScore() >= previousPutCandleStickScores.getTotalScore();
    }
}