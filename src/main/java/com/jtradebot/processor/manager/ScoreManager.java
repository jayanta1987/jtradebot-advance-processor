package com.jtradebot.processor.manager;

import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.indicator.MultiEmaIndicator;
import com.jtradebot.processor.model.*;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.jtradebot.processor.model.enums.TrendEnum;
import com.jtradebot.processor.score.CandleStickScoreTracker;
import com.jtradebot.processor.score.EmaRsiScoreTracker;
import com.jtradebot.processor.score.NiftyFutureScoreTracker;
import com.jtradebot.processor.score.SupportResistanceScoreTracker;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;

import java.util.Arrays;
import java.util.Set;


@Service
@RequiredArgsConstructor
@Slf4j
public class ScoreManager {

    private final EmaRsiScoreTracker emaRsiScoreTracker;
    private final SupportResistanceScoreTracker supportResistanceScoreTracker;
    private final CandleStickScoreTracker candleStickScoreTracker;
    private final BarSeriesManager barSeriesManager;
    private final MultiEmaIndicator multiEmaIndicator;
    private final NiftyFutureScoreTracker niftyFutureScoreTracker;

    public @NotNull PutScoresTF getPutScores(Tick tick, CandleTimeFrameEnum timeFrame, TrendEnum trend, BarSeries barSeriesForTimeFrame,
                                             EmaIndicatorInfo emaIndicatorInfo, Set<Support> supports, Set<Resistance> resistances, Double rsiValue, String instrumentToken) {
        // PUT
        PutEmaRsiScores putEmaRsiScores = emaRsiScoreTracker.getEmaPointsForPutEntry(tick.getLastTradedPrice(), barSeriesForTimeFrame, emaIndicatorInfo, rsiValue);
        boolean isEma5AsResistance = multiEmaIndicator.isEma5CrossDown(trend, tick.getLastTradedPrice(), barSeriesForTimeFrame, emaIndicatorInfo);
        boolean isLtpLowerThanLastCandleLow = tick.getLastTradedPrice() < barSeriesManager.getPreviousBarForTimeFrame(instrumentToken, timeFrame).getLowPrice().doubleValue();
        PutSRScores putSRScores = supportResistanceScoreTracker.getSRPointsForPutEntry(tick.getLastTradedPrice(), barSeriesForTimeFrame, trend, supports, resistances, isEma5AsResistance, isLtpLowerThanLastCandleLow);
        PutCandleStickScores putCandleStickScores = candleStickScoreTracker.getBearishScore(
                Arrays.asList(barSeriesManager.getCurrentBarForTimeFrame(instrumentToken, timeFrame),
                        barSeriesManager.getPreviousBarForTimeFrame(instrumentToken, timeFrame),
                        barSeriesManager.getPrecious2ndBarForTimeFrame(instrumentToken, timeFrame)));
        PutCandleStickScores previousPutCandleStickScores = candleStickScoreTracker.getBearishScore(
                Arrays.asList(barSeriesManager.getPreviousBarForTimeFrame(instrumentToken, timeFrame),
                        barSeriesManager.getPrecious2ndBarForTimeFrame(instrumentToken, timeFrame),
                        barSeriesManager.getPrecious3rdBarForTimeFrame(instrumentToken, timeFrame)));

        PutScoresTF putScoresTF = PutScoresTF.builder()
                .putSRScores(putSRScores)
                .putEmaRsiScores(putEmaRsiScores)
                .putCandleStickScores(putCandleStickScores)
                .previousPutCandleStickScores(previousPutCandleStickScores)
                .timeFrame(timeFrame)
                .build();
        putScoresTF.calculateTotalScore();
        return putScoresTF;
    }

    public @NotNull CallScoresTF getCallScores(Tick tick, CandleTimeFrameEnum timeFrame, TrendEnum trend, BarSeries barSeriesForTimeFrame,
                                               EmaIndicatorInfo emaIndicatorInfo, Set<Support> supports, Set<Resistance> resistances, Double rsiValue, String instrumentToken) {
        // Call
        boolean isEma5AsSupport = multiEmaIndicator.isEma5CrossUp(trend, tick.getLastTradedPrice(), barSeriesForTimeFrame, emaIndicatorInfo);
        boolean isLtpHigherThanLastCandleHigh = tick.getLastTradedPrice() > barSeriesManager.getPreviousBarForTimeFrame(instrumentToken, timeFrame).getHighPrice().doubleValue();
        CallSRScores callSRScores = supportResistanceScoreTracker.getSRPointsForCallEntry(tick.getLastTradedPrice(), barSeriesForTimeFrame, trend, supports, resistances, isEma5AsSupport, isLtpHigherThanLastCandleHigh);
        CallEmaRsiScores callEmaRsiScores = emaRsiScoreTracker.getEmaPointsForCallEntry(tick.getLastTradedPrice(), barSeriesForTimeFrame, emaIndicatorInfo, rsiValue);

        CallCandleStickScores callCandleStickScores = candleStickScoreTracker.getBullishScore(
                Arrays.asList(barSeriesManager.getCurrentBarForTimeFrame(instrumentToken, timeFrame),
                        barSeriesManager.getPreviousBarForTimeFrame(instrumentToken, timeFrame),
                        barSeriesManager.getPrecious2ndBarForTimeFrame(instrumentToken, timeFrame)));
        CallCandleStickScores previousCallCandleStickScores = candleStickScoreTracker.getBullishScore(
                Arrays.asList(barSeriesManager.getPreviousBarForTimeFrame(instrumentToken, timeFrame),
                        barSeriesManager.getPrecious2ndBarForTimeFrame(instrumentToken, timeFrame),
                        barSeriesManager.getPrecious3rdBarForTimeFrame(instrumentToken, timeFrame)));

        CallScoresTF callScoresTF = CallScoresTF.builder()
                .callSRScores(callSRScores)
                .callEmaRsiScores(callEmaRsiScores)
                .callCandleStickScores(callCandleStickScores)
                .previousCallCandleStickScores(previousCallCandleStickScores)
                .timeFrame(timeFrame)
                .build();

        callScoresTF.calculateTotalScore();
        return callScoresTF;
    }


}
