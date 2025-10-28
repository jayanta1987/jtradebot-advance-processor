package com.jtradebot.processor.indicator;

import com.jtradebot.processor.model.indicator.EmaIndicatorInfo;
import com.jtradebot.processor.model.indicator.EmaInfo;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.jtradebot.processor.model.enums.CrossTypeEnum;
import com.jtradebot.processor.model.enums.TrendEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.util.Arrays;
import java.util.List;

import static com.jtradebot.processor.model.enums.CrossTypeEnum.CROSS_UP;
import static com.jtradebot.processor.model.enums.TrendEnum.*;


@Component
@Slf4j
public class MultiEmaIndicator {

    public EmaInfo calculateEmaValues(BarSeries series, CandleTimeFrameEnum timeframe) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        EmaInfo emaInfo = new EmaInfo();
        emaInfo.setTimeFrame(timeframe);
        emaInfo.setEma5(new EMAIndicator(closePrice, 5).getValue(series.getEndIndex()).doubleValue());
        emaInfo.setEma9(new EMAIndicator(closePrice, 9).getValue(series.getEndIndex()).doubleValue());
        emaInfo.setEma14(new EMAIndicator(closePrice, 14).getValue(series.getEndIndex()).doubleValue());
        emaInfo.setEma20(new EMAIndicator(closePrice, 20).getValue(series.getEndIndex()).doubleValue());
        emaInfo.setEma34(new EMAIndicator(closePrice, 34).getValue(series.getEndIndex()).doubleValue());
        emaInfo.setEma200(new EMAIndicator(closePrice, 200).getValue(series.getEndIndex()).doubleValue());
        return emaInfo;
    }

    public double getEmaValue(BarSeries series, int barCount, int endIndex) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        return new EMAIndicator(closePrice, barCount).getValue(endIndex).doubleValue();
    }


    public EmaIndicatorInfo createEmaInfoForTimeframes(BarSeries series, CandleTimeFrameEnum timeframe) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        EmaIndicatorInfo emaInfo = new EmaIndicatorInfo();
        emaInfo.setTimeFrame(timeframe);
        emaInfo.setEma5(new EMAIndicator(closePrice, 5));
        emaInfo.setEma9(new EMAIndicator(closePrice, 9));
        emaInfo.setEma14(new EMAIndicator(closePrice, 14));
        emaInfo.setEma20(new EMAIndicator(closePrice, 20));
        emaInfo.setEma34(new EMAIndicator(closePrice, 34));
        emaInfo.setEma200(new EMAIndicator(closePrice, 200));
        return emaInfo;
    }

    public double getLastEmaValue(BarSeries series, EMAIndicator emaIndicator) {
        int endIndex = series.getEndIndex();
        if (endIndex < 1) {
            return 0.0; // Not enough data
        }
        return emaIndicator.getValue(endIndex).doubleValue();
    }

    // Method to check LTP crossover EMA
    public boolean isLtpCrossingEma(double ltp, BarSeries series, EMAIndicator emaIndicator, CrossTypeEnum crossType, double buffer) {
        int endIndex = series.getEndIndex();
        if (endIndex < 1) {
            return false; // Not enough data
        }

        double currentEmaValue = emaIndicator.getValue(endIndex).doubleValue();
        double previousEmaValue = emaIndicator.getValue(endIndex - 1).doubleValue();
        Bar currentBar = series.getBar(endIndex);
        double currentHigh = currentBar.getHighPrice().doubleValue();
        double currentLow = currentBar.getLowPrice().doubleValue();

        boolean isCrossedEmaUp = currentLow < currentEmaValue
                && ltp > currentEmaValue
                && Math.abs(ltp - currentEmaValue) > buffer;
        //&& previousEmaValue < currentEmaValue; not required. check later if needed

        boolean isCrossedEmaDown = currentHigh > currentEmaValue
                && ltp < currentEmaValue
                && Math.abs(ltp - currentEmaValue) > buffer;
        //&& previousEmaValue > currentEmaValue; not required. check later if needed

        if (crossType == CrossTypeEnum.CROSS_UP) {
            return isCrossedEmaUp;
        } else if (crossType == CrossTypeEnum.CROSS_DOWN) {
            return isCrossedEmaDown;
        } else { // ANY
            return isCrossedEmaUp || isCrossedEmaDown;
        }
    }


    // Method to check if LTP is between multiple EMAs
    public boolean isLtpBetweenEmas(double ltp, int endIndex, EmaIndicatorInfo emaInfo) {
        if (endIndex < 1) {
            return false; // Not enough data
        }
        EMAIndicator[] emaIndicators = new EMAIndicator[]{emaInfo.getEma9(), emaInfo.getEma20(), emaInfo.getEma34()};

        double minEmaValue = Double.MAX_VALUE;
        double maxEmaValue = Double.MIN_VALUE;
        for (EMAIndicator emaIndicator : emaIndicators) {
            double emaValue = emaIndicator.getValue(endIndex).doubleValue();
            minEmaValue = Math.min(minEmaValue, emaValue);
            maxEmaValue = Math.max(maxEmaValue, emaValue);
        }

        return (ltp > minEmaValue && ltp < maxEmaValue);
    }

    // Method to check if multiple EMAs are very close to each other
    public boolean areEmasVeryClose(int endIndex, double threshold, EmaIndicatorInfo emaInfo) {
        if (endIndex < 1) {
            return false; // Not enough data
        }
        EMAIndicator[] emaIndicators = new EMAIndicator[]{emaInfo.getEma9(), emaInfo.getEma20(), emaInfo.getEma34()};

        for (int i = 0; i < emaIndicators.length - 1; i++) {
            for (int j = i + 1; j < emaIndicators.length; j++) {
                double emaValue1 = emaIndicators[i].getValue(endIndex).doubleValue();
                double emaValue2 = emaIndicators[j].getValue(endIndex).doubleValue();
                if (Math.abs(emaValue1 - emaValue2) <= threshold) {
                    return true;
                }
            }
        }

        return false;
    }

    //Method to check if LTP is above Highest EMA
    public boolean isLtpAboveHighestEma(double ltp, int endIndex, EmaIndicatorInfo emaInfo) {
        if (endIndex < 1) {
            return false; // Not enough data
        }
        EMAIndicator[] emaIndicators = new EMAIndicator[]{emaInfo.getEma9(), emaInfo.getEma20(), emaInfo.getEma34(), emaInfo.getEma200()};

        double maxEmaValue = Double.MIN_VALUE;
        for (EMAIndicator emaIndicator : emaIndicators) {
            double emaValue = emaIndicator.getValue(endIndex).doubleValue();
            maxEmaValue = Math.max(maxEmaValue, emaValue);
        }

        return ltp > maxEmaValue;
    }

    //Method to check if LTP is below Lowest EMA
    public boolean isLtpBelowLowestEma(double ltp, int endIndex, EmaIndicatorInfo emaInfo) {
        if (endIndex < 1) {
            return false; // Not enough data
        }
        EMAIndicator[] emaIndicators = new EMAIndicator[]{emaInfo.getEma9(), emaInfo.getEma20(), emaInfo.getEma34(), emaInfo.getEma200()};

        double minEmaValue = Double.MAX_VALUE;
        for (EMAIndicator emaIndicator : emaIndicators) {
            double emaValue = emaIndicator.getValue(endIndex).doubleValue();
            minEmaValue = Math.min(minEmaValue, emaValue);
        }

        return ltp < minEmaValue;
    }

    public boolean isLtpNearEma200(double ltp, int endIndex, EmaIndicatorInfo emaInfo, double threshold) {
        if (endIndex < 1) {
            return false; // Not enough data
        }
        double ema200Value = emaInfo.getEma200().getValue(endIndex).doubleValue();
        return Math.abs(ltp - ema200Value) < threshold;
    }

    public boolean isLtpGreaterThanEma(double ltp, int endIndex, EMAIndicator ema) {
        if (endIndex < 1) {
            return false; // Not enough data
        }
        double emaValue = ema.getValue(endIndex).doubleValue();
        return ltp > emaValue;
    }

    public boolean isLtpLessThanEma(double ltp, int endIndex, EMAIndicator ema) {
        if (endIndex < 1) {
            return false; // Not enough data
        }
        double emaValue = ema.getValue(endIndex).doubleValue();
        return ltp < emaValue;
    }


    public boolean isLtpFarBeyondEmaRange(double ltp, int endIndex, EmaIndicatorInfo emaIndicatorInfo, int threshold) {
        if (endIndex < 1) {
            return false; // Not enough data
        }
        // Get the highest and lowest EMA values and if ltp is beyond the range of these values by threshold
        EMAIndicator[] emaIndicators = new EMAIndicator[]{emaIndicatorInfo.getEma5(), emaIndicatorInfo.getEma9(), emaIndicatorInfo.getEma20(), emaIndicatorInfo.getEma34(), emaIndicatorInfo.getEma200()};
        double maxEmaValue = Double.MIN_VALUE;
        double minEmaValue = Double.MAX_VALUE;
        for (EMAIndicator emaIndicator : emaIndicators) {
            double emaValue = emaIndicator.getValue(endIndex).doubleValue();
            maxEmaValue = Math.max(maxEmaValue, emaValue);
            minEmaValue = Math.min(minEmaValue, emaValue);
        }
        return ltp > maxEmaValue + threshold || ltp < minEmaValue - threshold;
    }

    public boolean hasEma5CrossedEma34(BarSeries series, EmaIndicatorInfo emaIndicatorInfo, CrossTypeEnum crossType) {
        int endIndex = series.getEndIndex();
        if (endIndex < 1) {
            return false; // Not enough data
        }
        EMAIndicator ema5 = emaIndicatorInfo.getEma5();
        EMAIndicator ema34 = emaIndicatorInfo.getEma34();
        double ema5Value = ema5.getValue(endIndex).doubleValue();
        double ema34Value = ema34.getValue(endIndex).doubleValue();
        double previousEma5Value = ema5.getValue(endIndex - 1).doubleValue();
        double previousEma34Value = ema34.getValue(endIndex - 1).doubleValue();
        return crossType == CROSS_UP ? previousEma5Value < previousEma34Value && ema5Value > ema34Value
                : previousEma5Value > previousEma34Value && ema5Value < ema34Value;
    }

    public boolean hasEma5CrossedEma14(BarSeries series, EmaIndicatorInfo emaIndicatorInfo, CrossTypeEnum crossType) {
        int endIndex = series.getEndIndex();
        if (endIndex < 1) {
            return false; // Not enough data
        }
        EMAIndicator ema5 = emaIndicatorInfo.getEma5();
        EMAIndicator ema14 = emaIndicatorInfo.getEma14();
        double ema5Value = ema5.getValue(endIndex).doubleValue();
        double ema14Value = ema14.getValue(endIndex).doubleValue();
        double previousEma5Value = ema5.getValue(endIndex - 1).doubleValue();
        double previousEma14Value = ema14.getValue(endIndex - 1).doubleValue();
        return crossType == CROSS_UP ? previousEma5Value < previousEma14Value && ema5Value > ema14Value
                : previousEma5Value > previousEma14Value && ema5Value < ema14Value;
    }

    public boolean hasEma5CrossedEma200(BarSeries series, EmaIndicatorInfo emaIndicatorInfo, CrossTypeEnum crossType) {
        int endIndex = series.getEndIndex();
        if (endIndex < 1) {
            return false; // Not enough data
        }
        EMAIndicator ema5 = emaIndicatorInfo.getEma5();
        EMAIndicator ema200 = emaIndicatorInfo.getEma200();
        double ema5Value = ema5.getValue(endIndex).doubleValue();
        double ema200Value = ema200.getValue(endIndex).doubleValue();
        double previousEma5Value = ema5.getValue(endIndex - 1).doubleValue();
        double previousEma200Value = ema200.getValue(endIndex - 1).doubleValue();
        return crossType == CROSS_UP ? previousEma5Value < previousEma200Value && ema5Value > ema200Value
                : previousEma5Value > previousEma200Value && ema5Value < ema200Value;
    }

    public List<Double> getEmaValues(int endIndex, EmaIndicatorInfo emaInfo) {
        return Arrays.asList(
                emaInfo.getEma5().getValue(endIndex).doubleValue(),
                emaInfo.getEma9().getValue(endIndex).doubleValue(),
                emaInfo.getEma14().getValue(endIndex).doubleValue(),
                emaInfo.getEma20().getValue(endIndex).doubleValue(),
                emaInfo.getEma34().getValue(endIndex).doubleValue(),
                emaInfo.getEma200().getValue(endIndex).doubleValue()
        );
    }


    public TrendEnum determineOverallTrendByEma(BarSeries series) {
        // Simple trend determination using moving averages
        EMAIndicator shortEma = new EMAIndicator(new ClosePriceIndicator(series), 9);
        EMAIndicator longEma = new EMAIndicator(new ClosePriceIndicator(series), 34);

        int endIndex = series.getEndIndex();
        double shortEmaValue = shortEma.getValue(endIndex).doubleValue();
        double longEmaValue = longEma.getValue(endIndex).doubleValue();
        double threshold = 10; // Define a threshold for sideways trend

        if (Math.abs(shortEmaValue - longEmaValue) <= threshold) {
            return SIDEWAYS;
        } else if (shortEmaValue > longEmaValue) {
            return UP;
        } else {
            return DOWN;
        }
    }

    /*public boolean isEma5CrossUp(String trend, Double ltp, BarSeries series5Min, EmaIndicatorInfo emaIndicatorInfo) {
        int endIndex = series5Min.getEndIndex();
        if (endIndex < 1) {
            return false; // Not enough data
        }
        double ema5Value = emaIndicatorInfo.getEma5().getValue(endIndex).doubleValue();
        double previousEma5Value = emaIndicatorInfo.getEma5().getValue(endIndex - 1).doubleValue();
        double previousClosePrice = series5Min.getBar(endIndex - 1).getClosePrice().doubleValue();
        return previousClosePrice < previousEma5Value && ltp > ema5Value && "UPTREND".equals(trend);
    }*/

    public boolean isEma5CrossUp(TrendEnum trend, Double ltp, BarSeries series5Min, EmaIndicatorInfo emaIndicatorInfo) {
        int endIndex = series5Min.getEndIndex();
        if (endIndex < 1) {
            return false; // Not enough data
        }
        double ema5Value = emaIndicatorInfo.getEma5().getValue(endIndex).doubleValue();
        double previousEma5Value = emaIndicatorInfo.getEma5().getValue(endIndex - 1).doubleValue();
        double currentLowPrice = series5Min.getBar(endIndex).getLowPrice().doubleValue();
        return currentLowPrice < previousEma5Value && ltp > ema5Value && UP.equals(trend);
    }

    /*public boolean isEma5CrossDown(String trend, Double ltp, BarSeries series5Min, EmaIndicatorInfo emaIndicatorInfo) {
        int endIndex = series5Min.getEndIndex();
        if (endIndex < 1) {
            return false; // Not enough data
        }
        double ema5Value = emaIndicatorInfo.getEma5().getValue(endIndex).doubleValue();
        double previousEma5Value = emaIndicatorInfo.getEma5().getValue(endIndex - 1).doubleValue();
        double previousClosePrice = series5Min.getBar(endIndex - 1).getClosePrice().doubleValue();
        return previousClosePrice > previousEma5Value && ltp < ema5Value && "DOWNTREND".equals(trend);
    }*/

    public boolean isEma5CrossDown(TrendEnum trend, Double ltp, BarSeries series5Min, EmaIndicatorInfo emaIndicatorInfo) {
        int endIndex = series5Min.getEndIndex();
        if (endIndex < 1) {
            return false; // Not enough data
        }
        double ema5Value = emaIndicatorInfo.getEma5().getValue(endIndex).doubleValue();
        double previousEma5Value = emaIndicatorInfo.getEma5().getValue(endIndex - 1).doubleValue();
        double currentHighPrice = series5Min.getBar(endIndex).getHighPrice().doubleValue();
        return currentHighPrice > previousEma5Value && ltp < ema5Value && DOWN.equals(trend);
    }

    // // check if last 4 min candle closes at least 5 points above of ema 5.
    public boolean isLastCandleBeyondEma5(BarSeries series5Min, EmaIndicatorInfo emaIndicatorInfo, double minPoints) {
        int endIndex = series5Min.getEndIndex();
        if (endIndex < 1) {
            return false; // Not enough data
        }
        double ema5Value = emaIndicatorInfo.getEma5().getValue(endIndex).doubleValue();
        double previousClosePrice = series5Min.getBar(endIndex - 1).getClosePrice().doubleValue();
        return previousClosePrice > ema5Value + minPoints || previousClosePrice < ema5Value - minPoints;
    }


    public double getEmaDistance(double ltp, int endIndex, int i, EmaIndicatorInfo emaInfo) {
        if (endIndex < 1) {
            return 0.0; // Not enough data
        }
        EMAIndicator ema = switch (i) {
            case 5 -> emaInfo.getEma5();
            case 9 -> emaInfo.getEma9();
            case 14 -> emaInfo.getEma14();
            case 20 -> emaInfo.getEma20();
            case 34 -> emaInfo.getEma34();
            case 200 -> emaInfo.getEma200();
            default -> null;
        };
        if (ema == null) {
            return 0.0;
        }
        double emaValue = ema.getValue(endIndex).doubleValue();
        return Math.abs(ltp - emaValue);
    }

    public boolean isLtpCrossedMultipleEmas(Double ltp, BarSeries series, EmaIndicatorInfo emaIndicatorInfo, CrossTypeEnum crossType) {
        if (series == null || series.getBarCount() < 2 || emaIndicatorInfo == null) {
            return false; // Not enough data or invalid inputs
        }
        int endIndex = series.getEndIndex();
        double previousLtp = series.getBar(endIndex - 1).getClosePrice().doubleValue();

        EMAIndicator[] emaIndicators = new EMAIndicator[]{
                emaIndicatorInfo.getEma9(),
                emaIndicatorInfo.getEma20(),
                emaIndicatorInfo.getEma34()
        };
        boolean crossedUp = true;
        boolean crossedDown = true;
        for (EMAIndicator emaIndicator : emaIndicators) {
            if (emaIndicator == null) {
                continue; // Skip null indicators
            }
            double emaValue = emaIndicator.getValue(endIndex).doubleValue();
            double previousEmaValue = emaIndicator.getValue(endIndex - 1).doubleValue();
            // Check for Cross-Up
            if (!(ltp > emaValue && previousLtp <= previousEmaValue)) {
                crossedUp = false;
            }
            // Check for Cross-Down
            if (!(ltp < emaValue && previousLtp >= previousEmaValue)) {
                crossedDown = false;
            }
        }
        return crossType == CrossTypeEnum.CROSS_UP ? crossedUp : crossedDown;
    }


}
