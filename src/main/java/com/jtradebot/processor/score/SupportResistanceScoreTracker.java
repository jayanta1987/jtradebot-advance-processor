package com.jtradebot.processor.score;

import com.jtradebot.processor.indicator.SupportResistanceIndicator;
import com.jtradebot.processor.model.PutSRScores;
import com.jtradebot.processor.model.CallSRScores;
import com.jtradebot.processor.model.Resistance;
import com.jtradebot.processor.model.Support;
import com.jtradebot.processor.model.enums.TrendEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;

import java.util.Set;

import static com.jtradebot.processor.indicator.SupportResistanceIndicator.MIN_SUPPORT_RESISTANCE_DISTANCE_BUFFER;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportResistanceScoreTracker {

    private final SupportResistanceIndicator supportResistanceIndicator;
    public CallSRScores getSRPointsForCallEntry(double ltp, BarSeries series5Min, TrendEnum trend5Min, Set<Support> supportLevels, Set<Resistance> resistanceLevels, boolean isEma5AsSupport, boolean isLtpHigherThanLastCandleHigh) {
        CallSRScores callSRScores = new CallSRScores();
        boolean crossedUpSupportInUpTrend = supportResistanceIndicator.monitorForCallEntry(series5Min, supportLevels, resistanceLevels, trend5Min);
        callSRScores.setLtpCrossedUpSRInUpTrend(crossedUpSupportInUpTrend ? 2 : 0);
        callSRScores.setEma5AsSupport(isEma5AsSupport ? 0.5 : 0);
        callSRScores.setLtpHigherThanLastCandleHigh(isLtpHigherThanLastCandleHigh ? 0.5 : 0);
        // bullishSRConfirmation.setLtpNearSupport(isNearSupport(ltp, levels5Min) ? 1 : 0);
        callSRScores.setLtpNearStrongSupport(supportResistanceIndicator.isNearStrongSupport(ltp, supportLevels) || supportResistanceIndicator.isNearMajorSupports(supportLevels, resistanceLevels) ? 1 : 0);
        callSRScores.setLtpNotNearResistance(!supportResistanceIndicator.isNearResistance(ltp, resistanceLevels, MIN_SUPPORT_RESISTANCE_DISTANCE_BUFFER) ? 1 : 0);
        callSRScores.calculateTotalPoints();
        return callSRScores;
    }


    public PutSRScores getSRPointsForPutEntry(Double ltp, BarSeries series5Min, TrendEnum trend5Min, Set<Support> supportLevels, Set<Resistance> resistanceLevels, boolean isEma5AsResistance, boolean isLtpLowerThanLastCandleLow) {
        PutSRScores putSRScores = new PutSRScores();
        boolean crossedDownResistanceInDownTrend = supportResistanceIndicator.monitorForPutEntry(series5Min, supportLevels, resistanceLevels, trend5Min);
        putSRScores.setLtpCrossedDownSRInDownTrend(crossedDownResistanceInDownTrend ? 2 : 0);
        // bearishSRConfirmation.setLtpNearResistance(isNearResistance(ltp, levels5Min) ? 1 : 0);
        putSRScores.setLtpNearStrongResistance(supportResistanceIndicator.isNearStrongResistance(ltp, resistanceLevels) || supportResistanceIndicator.isNearMajorResistances(supportLevels, resistanceLevels) ? 1 : 0);
        putSRScores.setLtpNotNearSupport(!supportResistanceIndicator.isNearSupport(ltp, supportLevels, MIN_SUPPORT_RESISTANCE_DISTANCE_BUFFER) ? 1 : 0);
        putSRScores.setEma5AsResistance(isEma5AsResistance ? 0.5 : 0);
        putSRScores.setLtpLowerThanLastCandleLow(isLtpLowerThanLastCandleLow ? 0.5 : 0);
        putSRScores.calculateTotalPoints();
        return putSRScores;
    }
}
