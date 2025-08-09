package com.jtradebot.processor.model;

import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class NoTradeScores {
    private double totalScore;
    private double withinMultipleEmas;
    private double rsiBadZone;
    private double tooNearEma200;
    private double recentlyTravelledHugeMargin;
    private double weakTrend;
    private double emaClosedToEachOther;
    private double nearStrongSupportResistance;
    private double farFromEma5;
    private double avgCandleHeightShort;
    private double lastCandleBeyondEma5;
    private double bookedLossInCurrent5MinCandle;
    private double rsiDivergence;
    private double bonusPoints;
    private double backToBackSameCandleColor;

    private double lastClosedCandleNotTouchedEma5;
    private double closeToRoundIndexPrice;
    private CandleTimeFrameEnum timeFrame;

    public void calculateTotalPoints() {
        totalScore = withinMultipleEmas + rsiBadZone + tooNearEma200 + recentlyTravelledHugeMargin + backToBackSameCandleColor +
                weakTrend + emaClosedToEachOther + nearStrongSupportResistance
                + farFromEma5 + avgCandleHeightShort + lastCandleBeyondEma5 + bonusPoints + bookedLossInCurrent5MinCandle + rsiDivergence
                + lastClosedCandleNotTouchedEma5 + closeToRoundIndexPrice;
    }

    public double getTotalScore() {
        calculateTotalPoints();
        return totalScore;
    }
}
