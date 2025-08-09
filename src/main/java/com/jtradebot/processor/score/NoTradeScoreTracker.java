package com.jtradebot.processor.score;

import com.jtradebot.processor.indicator.MultiEmaIndicator;
import com.jtradebot.processor.indicator.RsiIndicator;
import com.jtradebot.processor.indicator.SupportResistanceIndicator;
import com.jtradebot.processor.manager.BarSeriesManager;
import com.jtradebot.processor.model.EmaIndicatorInfo;
import com.jtradebot.processor.model.NoTradeScores;
import com.jtradebot.processor.model.Resistance;
import com.jtradebot.processor.model.Support;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.util.Set;

import static com.jtradebot.processor.constants.SignalConstants.MIN_CANDLE_FULL_HEIGHT;
import static com.jtradebot.processor.constants.SignalConstants.PREFERRED_AVG_CANDLE_HEIGHT_FOR_TRADE;
import static com.jtradebot.processor.indicator.SupportResistanceIndicator.VERY_MIN_SUPPORT_RESISTANCE_DISTANCE_BUFFER;
import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoTradeScoreTracker {

    private final MultiEmaIndicator multiEmaIndicator;
    private final SupportResistanceIndicator supportResistanceIndicator;
    private final RsiIndicator rsiIndicator;
    private final BarSeriesManager barSeriesManager;

    public NoTradeScores getPreConfirmationScore(BarSeries barSeries, EmaIndicatorInfo emaInfo, Set<Support> supports, Set<Resistance> resistances, Tick tick, String instrumentToken) {
        NoTradeScores noTradeScores = new NoTradeScores();
        double ltp = tick.getLastTradedPrice();

        boolean areEmaClosedToEachOther = multiEmaIndicator.areEmasVeryClose(barSeries.getEndIndex(), MIN_CANDLE_FULL_HEIGHT, emaInfo);
        noTradeScores.setEmaClosedToEachOther(areEmaClosedToEachOther ? 1 : 0);

        boolean withinMultipleEmas = multiEmaIndicator.isLtpBetweenEmas(ltp, barSeries.getEndIndex(), emaInfo);
        noTradeScores.setWithinMultipleEmas(withinMultipleEmas ? 1.5 : 0);

        boolean isRsiBetween44And56 = rsiIndicator.getRsiValue(barSeries, 14) >= 44 && rsiIndicator.getRsiValue(barSeries, 14) <= 56;
        boolean isRsiExtreme = rsiIndicator.getRsiValue(barSeries, 14) >= 75 || rsiIndicator.getRsiValue(barSeries, 14) <= 25;
        noTradeScores.setRsiBadZone((isRsiBetween44And56 || isRsiExtreme) ? 1.5 : 0);

        boolean isNearStrongSupportResistance = supportResistanceIndicator.isNearStrongResistance(ltp, resistances)
                || supportResistanceIndicator.isNearStrongSupport(ltp, supports);
        noTradeScores.setNearStrongSupportResistance(isNearStrongSupportResistance ? 1 : 0);

        if (FIVE_MIN.name().equals(barSeries.getName()) || THREE_MIN.name().equals(barSeries.getName())) {
            Double avgCandleHeight = barSeriesManager.getAvgCandleHeightForTimeFrame(instrumentToken, valueOf(barSeries.getName()));
            boolean isAvgCandleHeightShort = avgCandleHeight < PREFERRED_AVG_CANDLE_HEIGHT_FOR_TRADE;
            noTradeScores.setAvgCandleHeightShort(isAvgCandleHeightShort ? 1 : 0);

            double ema5Distance = multiEmaIndicator.getEmaDistance(ltp, barSeries.getEndIndex(), 5, emaInfo);
            noTradeScores.setFarFromEma5(getFarFromEma5Score(ema5Distance));

            int candleCount = 3;
            double totalLength = barSeriesManager.getLastNCandleTotalLength(barSeries, candleCount);
            noTradeScores.setRecentlyTravelledHugeMargin(calculateHugeMarginPoints(totalLength, tick.getLastTradedPrice()));
        }

       // boolean isCurrentCandleCrossingEma200 = multiEmaIndicator.isLtpCrossedEma(ltp, barSeries, emaInfo.getEma200(), CrossTypeEnum.ANY, 5);
        boolean isEma200TooClose = multiEmaIndicator.isLtpNearEma200(ltp, barSeries.getEndIndex(), emaInfo, 40);
        noTradeScores.setTooNearEma200(isEma200TooClose ? 0.75 : 0);

       /* boolean isRecentlyBookedLoss = isVeryRecentlyBookedLoss(tick);
        noTradeScores.setBookedLossInCurrent5MinCandle(isRecentlyBookedLoss ? 1 : 0);*/

        boolean isLastClosedCandleNotTouchedEma5 = isLastClosedCandleNotTouchedEma5(barSeries, emaInfo, VERY_MIN_SUPPORT_RESISTANCE_DISTANCE_BUFFER);
        noTradeScores.setLastClosedCandleNotTouchedEma5(isLastClosedCandleNotTouchedEma5 ? 1 : 0);

        boolean isIndexPriceRoundNumber = isIndexPriceRoundNumber(tick.getLastTradedPrice(), VERY_MIN_SUPPORT_RESISTANCE_DISTANCE_BUFFER);
        noTradeScores.setCloseToRoundIndexPrice(isIndexPriceRoundNumber ? 0.25 : 0);

        noTradeScores.calculateTotalPoints();

        boolean isBackToBackSameCandleColor = barSeriesManager.isBackToBackSameCandleColor(barSeries, 5);
        noTradeScores.setBackToBackSameCandleColor(isBackToBackSameCandleColor ? 1 : 0);

        return noTradeScores;
    }

    private boolean isIndexPriceRoundNumber(double lastTradedPrice, double buffer) {
        // check if the last traded price is very close to round number e.g. 24400, 24300, 24500 etc.
        double extraNumber = lastTradedPrice % 100;
        return extraNumber <= buffer || extraNumber >= 100 - buffer;

    }

    private boolean isLastClosedCandleNotTouchedEma5(BarSeries barSeries, EmaIndicatorInfo emaInfo, double buffer) {
        // we have to check last candle high or low is not touched EMA5
        double lastCandleHigh = barSeries.getBar(barSeries.getEndIndex() - 1).getHighPrice().doubleValue();
        double lastCandleLow = barSeries.getBar(barSeries.getEndIndex() - 1).getLowPrice().doubleValue();
        double ema5 = emaInfo.getEma5().getValue(barSeries.getEndIndex() - 1).doubleValue();
        return lastCandleLow > ema5 + buffer || lastCandleHigh < ema5 - buffer;
    }

    private double calculateHugeMarginPoints(double totalLength, double currentIndexPrice) {
        double allowedDistance = 0.004 * currentIndexPrice; // 0.4% of the current price
        if (totalLength <= allowedDistance) {
            return 0;
        }
        return ((totalLength - allowedDistance) / allowedDistance) * 0.5;
    }

    private static double getFarFromEma5Score(double ema5Distance) {
        if (ema5Distance <= MIN_CANDLE_FULL_HEIGHT) {
            return 0;
        }
        return ((ema5Distance - MIN_CANDLE_FULL_HEIGHT) / MIN_CANDLE_FULL_HEIGHT) * 0.25;
    }
}
