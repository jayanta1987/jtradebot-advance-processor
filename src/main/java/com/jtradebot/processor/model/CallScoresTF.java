package com.jtradebot.processor.model;

import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CallScoresTF {

    @Getter
    private CallEmaRsiScores callEmaRsiScores;
    @Getter
    private CallSRScores callSRScores;
    @Getter
    private CallCandleStickScores callCandleStickScores;
    @Getter
    private CallCandleStickScores previousCallCandleStickScores;

    private double totalScore;
    @Getter
    private CandleTimeFrameEnum timeFrame;

    public void calculateTotalScore() {
        if (callEmaRsiScores != null && callSRScores != null && callCandleStickScores != null && previousCallCandleStickScores != null) {
            totalScore = callEmaRsiScores.getTotalScore() + callSRScores.getTotalScore() +
                    callCandleStickScores.getTotalScore() + previousCallCandleStickScores.getTotalScore();
        }
    }

    public double getTotalScore() {
        calculateTotalScore();
        return totalScore;
    }

    public boolean checkValidScore() {
        return callEmaRsiScores.getTotalScore() > 1
                && callSRScores.getTotalScore() > 1
                && callCandleStickScores.getTotalScore() > 1
                && previousCallCandleStickScores.getTotalScore() > 1;
    }

    public boolean checkValidCandleStick() {
        return previousCallCandleStickScores.getTotalScore() >= 2
                || callCandleStickScores.getTotalScore() >= previousCallCandleStickScores.getTotalScore();
    }

}