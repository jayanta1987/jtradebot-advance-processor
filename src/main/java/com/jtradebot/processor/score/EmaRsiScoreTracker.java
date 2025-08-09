package com.jtradebot.processor.score;

import com.jtradebot.processor.indicator.MultiEmaIndicator;
import com.jtradebot.processor.model.PutEmaRsiScores;
import com.jtradebot.processor.model.CallEmaRsiScores;
import com.jtradebot.processor.model.EmaIndicatorInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;

import static com.jtradebot.processor.model.enums.CrossTypeEnum.CROSS_DOWN;
import static com.jtradebot.processor.model.enums.CrossTypeEnum.CROSS_UP;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmaRsiScoreTracker {

    private final MultiEmaIndicator multiEmaIndicator;

    public CallEmaRsiScores getEmaPointsForCallEntry(Double ltp, BarSeries series, EmaIndicatorInfo emaIndicatorInfo, Double rsiValue) {

        CallEmaRsiScores callEmaConfirmations = new CallEmaRsiScores();

        // LTP Crossed Up EMA5
        double ltpCrossedUpEma5 = multiEmaIndicator.isLtpCrossingEma(ltp, series, emaIndicatorInfo.getEma5(), CROSS_UP, 5) ? 1 : 0;
        callEmaConfirmations.setLtpCrossedUpEma5(ltpCrossedUpEma5);
        // LTP Crossed Up EMA9
        double ltpCrossedUpEma9 = multiEmaIndicator.isLtpCrossingEma(ltp, series, emaIndicatorInfo.getEma9(), CROSS_UP, 5) ? 1 : 0;
        callEmaConfirmations.setLtpCrossedUpEma9(ltpCrossedUpEma9);
        // LTP Crossed Up EMA14
        double ltpCrossedUpEma14 = multiEmaIndicator.isLtpCrossingEma(ltp, series, emaIndicatorInfo.getEma14(), CROSS_UP, 5) ? 1 : 0;
        callEmaConfirmations.setLtpCrossedUpEma14(ltpCrossedUpEma14);
        // LTP Greater Than EMA200
        double ltpGreaterThanEma200 = multiEmaIndicator.isLtpGreaterThanEma(ltp, series.getEndIndex(), emaIndicatorInfo.getEma200()) ? 1 : 0;
        callEmaConfirmations.setLtpGreaterThanEma200(ltpGreaterThanEma200);

        // EMA5 Cross Over EMA34
        double ema5CrossOverEma34 = multiEmaIndicator.hasEma5CrossedEma34(series, emaIndicatorInfo, CROSS_UP) ? 2 : 0;
        callEmaConfirmations.setEma5CrossOverEma34(ema5CrossOverEma34);

        // EM5 Cross Over EMA14
        double ema5CrossOverEma14 = multiEmaIndicator.hasEma5CrossedEma14(series, emaIndicatorInfo, CROSS_UP) ? 1 : 0;
        callEmaConfirmations.setEma5CrossOverEma14(ema5CrossOverEma14);

        // EMA5 Cross Over EMA200
        double ema5CrossOverEma200 = multiEmaIndicator.hasEma5CrossedEma200(series, emaIndicatorInfo, CROSS_UP) ? 2 : 0;
        callEmaConfirmations.setEma5CrossOverEma200(ema5CrossOverEma200);

        // LTP crossed up multiple EMAs
        double crossedUpMultipleEmas = multiEmaIndicator.isLtpCrossedMultipleEmas(ltp, series, emaIndicatorInfo, CROSS_UP) ? 2 : 0;
        callEmaConfirmations.setCrossedUpMultipleEmas(crossedUpMultipleEmas);

        callEmaConfirmations.setRsiAbove55(rsiValue >= 55 ? 1 : 0);
        callEmaConfirmations.calculateTotalPoints();
        return callEmaConfirmations;
    }

    public PutEmaRsiScores getEmaPointsForPutEntry(double ltp, BarSeries series, EmaIndicatorInfo emaIndicatorInfo, Double rsiValue) {
        PutEmaRsiScores putEmaConfirmations = new PutEmaRsiScores();
        // LTP Crossed Down EMA5
        double ltpCrossedDownEma5 = multiEmaIndicator.isLtpCrossingEma(ltp, series, emaIndicatorInfo.getEma5(), CROSS_DOWN, 5) ? 1 : 0;
        putEmaConfirmations.setLtpCrossedDownEma5(ltpCrossedDownEma5);
        // LTP Crossed Down EMA9
        double ltpCrossedDownEma9 = multiEmaIndicator.isLtpCrossingEma(ltp, series, emaIndicatorInfo.getEma9(), CROSS_DOWN, 5) ? 1 : 0;
        putEmaConfirmations.setLtpCrossedDownEma9(ltpCrossedDownEma9);
        // LTP Crossed Down EMA14
        double ltpCrossedDownEma14 = multiEmaIndicator.isLtpCrossingEma(ltp, series, emaIndicatorInfo.getEma14(), CROSS_DOWN, 5) ? 1 : 0;
        putEmaConfirmations.setLtpCrossedDownEma14(ltpCrossedDownEma14);
        // LTP Less Than EMA200
        double ltpLessThanEma200 = multiEmaIndicator.isLtpLessThanEma(ltp, series.getEndIndex(), emaIndicatorInfo.getEma200()) ? 1 : 0;
        putEmaConfirmations.setLtpLessThanEma200(ltpLessThanEma200);
        // EMA5 Cross Down EMA34
        double ema5CrossDownEma34 = multiEmaIndicator.hasEma5CrossedEma34(series, emaIndicatorInfo, CROSS_DOWN) ? 2 : 0;
        putEmaConfirmations.setEma5CrossDownEma34(ema5CrossDownEma34);

        // EM5 Cross Down EMA14
        double ema5CrossDownEma14 = multiEmaIndicator.hasEma5CrossedEma14(series, emaIndicatorInfo, CROSS_DOWN) ? 1 : 0;
        putEmaConfirmations.setEma5CrossDownEma14(ema5CrossDownEma14);

        // EMA5 Cross Down EMA200
        double ema5CrossDownEma200 = multiEmaIndicator.hasEma5CrossedEma200(series, emaIndicatorInfo, CROSS_DOWN) ? 2 : 0;
        putEmaConfirmations.setEma5CrossDownEma200(ema5CrossDownEma200);

        // LTP crossed up multiple EMAs
        double crossedUpMultipleEmas = multiEmaIndicator.isLtpCrossedMultipleEmas(ltp, series, emaIndicatorInfo, CROSS_DOWN) ? 2 : 0;
        putEmaConfirmations.setCrossedDownMultipleEmas(crossedUpMultipleEmas);

        putEmaConfirmations.setRsiBelow45(rsiValue <= 45 ? 1 : 0);
        putEmaConfirmations.calculateTotalPoints();
        return putEmaConfirmations;
    }
}
