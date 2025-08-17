package com.jtradebot.processor.indicator;


import com.jtradebot.processor.model.indicator.EmaIndicatorInfo;
import com.jtradebot.processor.model.indicator.Resistance;
import com.jtradebot.processor.model.indicator.Support;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.model.enums.TrendEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.EMAIndicator;

import java.util.*;

import static com.jtradebot.processor.model.enums.OrderTypeEnum.CALL_BUY;
import static com.jtradebot.processor.model.enums.OrderTypeEnum.PUT_BUY;


@Component
@Slf4j
@RequiredArgsConstructor
public class SupportResistanceIndicator {
    public static Integer MIN_SUPPORT_RESISTANCE_DISTANCE_BUFFER = 10;
    public static Integer VERY_MIN_SUPPORT_RESISTANCE_DISTANCE_BUFFER = 5;

    public Set<Support> calculateSupports(CandleTimeFrameEnum timeFrame, BarSeries barSeriesForTimeFrame, double ltp, EmaIndicatorInfo emaIndicatorInfo, int[] periods) {
        Set<Support> supports = new TreeSet<>(Comparator.naturalOrder());

        Arrays.stream(periods).forEach(period -> {
            Support support = new Support();
            int[] highLow = calculateHighLowLevels(barSeriesForTimeFrame, period);
            int high = highLow[0];
            int low = highLow[1];

            support.setCandleTimeFrame(timeFrame);
            support.setEma(false);
            if (high < ltp) {
                support.setSupportValue(high);
                supports.add(support);
            }
            if (low < ltp) {
                support.setSupportValue(low);
                supports.add(support);
            }
        });

        EMAIndicator[] emaIndicators = new EMAIndicator[]{emaIndicatorInfo.getEma9(), emaIndicatorInfo.getEma14(),
                emaIndicatorInfo.getEma20(), emaIndicatorInfo.getEma34(), emaIndicatorInfo.getEma200()};

        Arrays.stream(emaIndicators).forEach(emaIndicator -> {
            Support support = new Support();
            support.setCandleTimeFrame(timeFrame);
            support.setEma(true);
            if (emaIndicator.getValue(barSeriesForTimeFrame.getEndIndex()).intValue() < ltp) {
                support.setSupportValue(emaIndicator.getValue(barSeriesForTimeFrame.getEndIndex()).intValue());
                supports.add(support);
            }
        });
        return supports;
    }

    public Set<Resistance> calculateResistances(CandleTimeFrameEnum timeFrame, BarSeries barSeriesForTimeFrame, double ltp, EmaIndicatorInfo emaIndicatorInfo, int[] periods) {
        Set<Resistance> resistances = new TreeSet<>(Comparator.naturalOrder());

        Arrays.stream(periods).forEach(period -> {
            Resistance resistance = new Resistance();
            int[] highLow = calculateHighLowLevels(barSeriesForTimeFrame, period);
            int high = highLow[0];
            int low = highLow[1];

            resistance.setCandleTimeFrame(timeFrame);
            resistance.setEma(false);
            if (high > ltp) {
                resistance.setResistanceValue(high);
                resistances.add(resistance);
            }
            if (low > ltp) {
                resistance.setResistanceValue(low);
                resistances.add(resistance);
            }
        });

        EMAIndicator[] emaIndicators = new EMAIndicator[]{emaIndicatorInfo.getEma9(), emaIndicatorInfo.getEma14(),
                emaIndicatorInfo.getEma20(), emaIndicatorInfo.getEma34(), emaIndicatorInfo.getEma200()};

        Arrays.stream(emaIndicators).forEach(emaIndicator -> {
            Resistance resistance = new Resistance();
            resistance.setCandleTimeFrame(timeFrame);
            resistance.setEma(true);
            if (emaIndicator.getValue(barSeriesForTimeFrame.getEndIndex()).intValue() > ltp) {
                resistance.setResistanceValue(emaIndicator.getValue(barSeriesForTimeFrame.getEndIndex()).intValue());
                resistances.add(resistance);
            }
        });

        return resistances;
    }

    private int[] calculateHighLowLevels(BarSeries series, int period) {
        int endIndex = series.getEndIndex();
        int startIndex = Math.max(0, endIndex - period + 1);

        int highestHigh = Integer.MIN_VALUE;
        int lowestLow = Integer.MAX_VALUE;

        for (int i = startIndex; i <= endIndex; i++) {
            int high = series.getBar(i).getHighPrice().intValue();
            int low = series.getBar(i).getLowPrice().intValue();

            if (high > highestHigh) {
                highestHigh = high;
            }
            if (low < lowestLow) {
                lowestLow = low;
            }
        }

        return new int[]{highestHigh, lowestLow};
    }


    public boolean monitorForCallEntry(BarSeries lowerSeries, Set<Support> supportLevels, Set<Resistance> resistanceLevels, TrendEnum trend) {
        int endIndex = lowerSeries.getEndIndex();
        double currentPrice = lowerSeries.getBar(endIndex).getClosePrice().doubleValue();
        if (trend == TrendEnum.UP) {
            for (Support support : supportLevels) {
                if (currentPrice <= support.getSupportValue()) {
                    log.debug("Potential long entry near support level: {}", support);
                    return true;
                }
            }
            for (Resistance resistance : resistanceLevels) {
                if (currentPrice >= resistance.getResistanceValue()) {
                    log.debug("Potential long entry near resistance level: {}", resistance);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean monitorForPutEntry(BarSeries lowerSeries, Set<Support> supportLevels, Set<Resistance> resistanceLevels, TrendEnum trend) {
        int endIndex = lowerSeries.getEndIndex();
        double currentPrice = lowerSeries.getBar(endIndex).getClosePrice().doubleValue();
        if (trend == TrendEnum.DOWN) {
            for (Resistance resistance : resistanceLevels) {
                if (currentPrice >= resistance.getResistanceValue()) {
                    log.debug("Potential short entry near resistance level: {}", resistance);
                    return true;
                }
            }
            for (Support support : supportLevels) {
                if (currentPrice <= support.getSupportValue()) {
                    log.debug("Potential short entry near support level: {}", support);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isNearSupport(double ltp, Set<Support> supportLevels, int minDistance) {
        return findDistanceToNearestSupport(ltp, supportLevels) <= minDistance;
    }

    public boolean isNearStrongSupport(double ltp, Set<Support> supportLevels) {
        List<Support> supportLevelsInRange = supportLevels.stream()
                .filter(level -> level.getSupportValue() <= ltp && level.getSupportValue() >= ltp - MIN_SUPPORT_RESISTANCE_DISTANCE_BUFFER)
                .toList();
        return isNearSupport(ltp, supportLevels, VERY_MIN_SUPPORT_RESISTANCE_DISTANCE_BUFFER) ||
                (isNearSupport(ltp, supportLevels, MIN_SUPPORT_RESISTANCE_DISTANCE_BUFFER) && supportLevelsInRange.size() >= 2);
    }

    public boolean isNearResistance(double ltp, Set<Resistance> resistanceLevels, int minDistance) {
        return findDistanceToNearestResistance(ltp, resistanceLevels) <= minDistance;
    }

    public boolean isNearStrongResistance(double ltp, Set<Resistance> resistanceLevels) {
        List<Resistance> resistanceLevelsInRange = resistanceLevels.stream()
                .filter(level -> level.getResistanceValue() >= ltp && level.getResistanceValue() <= ltp + MIN_SUPPORT_RESISTANCE_DISTANCE_BUFFER)
                .toList();
        return isNearResistance(ltp, resistanceLevels, VERY_MIN_SUPPORT_RESISTANCE_DISTANCE_BUFFER) ||
                (isNearResistance(ltp, resistanceLevels, MIN_SUPPORT_RESISTANCE_DISTANCE_BUFFER) && resistanceLevelsInRange.size() >= 2);
    }

    public boolean isNearMajorResistances(Set<Support> supportLevels, Set<Resistance> resistanceLevels) {
        return resistanceLevels.size() >= 2 * supportLevels.size();
    }

    public boolean isNearMajorSupports(Set<Support> supportLevels, Set<Resistance> resistanceLevels) {
        return supportLevels.size() >= 2 * resistanceLevels.size();
    }

    public double findDistanceToNearestSupport(double ltp, Set<Support> supportLevels) {
        Support defaultSupport = new Support();
        defaultSupport.setSupportValue((int)ltp);

        double nearestSupport = supportLevels.stream()
                .min(Comparator.comparingDouble(support -> Math.abs(support.getSupportValue() - ltp)))
                .orElse(defaultSupport)
                .getSupportValue();
        return Math.abs(nearestSupport - ltp);
    }

    public double findDistanceToNearestResistance(double ltp, Set<Resistance> resistanceLevels) {
        Resistance defaultResistance = new Resistance();
        defaultResistance.setResistanceValue((int)ltp);

        double nearestResistance = resistanceLevels.stream()
                .min(Comparator.comparingDouble(resistance -> Math.abs(resistance.getResistanceValue() - ltp)))
                .orElse(defaultResistance)
                .getResistanceValue();
        return Math.abs(nearestResistance - ltp);
    }

    public static List<Double> generateFibonacciPointsDowntrends(double high, double low) {
        List<Double> fibonacciPoints = new ArrayList<>();

        // Fibonacci retracement levels
        double level1 = high - 0.236 * (high - low);
        double level2 = high - 0.382 * (high - low);
        double level3 = high - 0.5 * (high - low);
        double level4 = high - 0.618 * (high - low);
        double level5 = high - 0.786 * (high - low);
        double level6 = high - 1 * (high - low);
        double level7 = high - 1.236 * (high - low);

        fibonacciPoints.add(level1);
        fibonacciPoints.add(level2);
        fibonacciPoints.add(level3);
        fibonacciPoints.add(level4);
        fibonacciPoints.add(level5);
        fibonacciPoints.add(level6);
        fibonacciPoints.add(level7);

        return fibonacciPoints;
    }

    public static List<Double> generateFibonacciPointsUptrend(double low, double high) {
        List<Double> fibonacciPoints = new ArrayList<>();

        // Fibonacci retracement levels for an uptrend
        double level1 = low + 0.236 * (high - low);
        double level2 = low + 0.382 * (high - low);
        double level3 = low + 0.5 * (high - low);
        double level4 = low + 0.618 * (high - low);
        double level5 = low + 0.786 * (high - low);
        double level6 = low + 1 * (high - low);
        double level7 = low + 1.236 * (high - low);

        fibonacciPoints.add(level1);
        fibonacciPoints.add(level2);
        fibonacciPoints.add(level3);
        fibonacciPoints.add(level4);
        fibonacciPoints.add(level5);
        fibonacciPoints.add(level6);
        fibonacciPoints.add(level7);

        return fibonacciPoints;
    }

    public Integer findValidNextResistance(Double entryPrice, Double entryIndexPrice, Set<Integer> supportLevels, Set<Integer> resistanceLevels, double buffer) {
        Integer nextResistance = resistanceLevels.stream()
                .filter(resistance -> resistance >= (entryIndexPrice + buffer))
                .min(Comparator.naturalOrder())
                .orElse(-1);
        if (nextResistance > 0) {
            return (int) (entryPrice + (nextResistance - entryIndexPrice));
        }
        return -1;
    }

    public Integer findValidNextSupport(Double entryPrice, Double entryIndexPrice, Set<Integer> supportLevels, Set<Integer> resistanceLevels, double buffer) {
        Integer nextSupport = supportLevels.stream()
                .filter(support -> support <= (entryIndexPrice - buffer))
                .max(Comparator.naturalOrder())
                .orElse(-1);
        if (nextSupport > 0) {
            return (int) (entryPrice - (entryIndexPrice - nextSupport));
        }
        return -1;
    }

    public Set<Integer> getFibLevels(Set<Support> supports, Set<Resistance> resistances, Double ltp, OrderTypeEnum orderType, double buffer) {
        Set<Integer> fibonacciLevels = new HashSet<>();

        Optional<Support> nearestSupportOpt = Optional.ofNullable(findNearestSupport(supports, ltp, false, buffer));
        Optional<Resistance> nearestResistanceOpt = Optional.ofNullable(findNearestResistance(resistances, ltp, false, buffer));

        Integer nearestSupport = nearestSupportOpt.map(Support::getSupportValue).orElse(null);
        Integer nearestResistance = nearestResistanceOpt.map(Resistance::getResistanceValue).orElse(null);
        // If Distance between nearest support and resistance is less than 50, or (orderType is call & the support is isEMA true)
        // or (orderType is put & resistance is isEMa true) then we will not consider it for fibonacci levels
        if (nearestSupport != null && nearestResistance != null && (nearestResistance - nearestSupport) >= 30) {
            if (orderType == CALL_BUY) {
                // Bottom to top
                fibonacciLevels.add((int) (nearestSupport + (nearestResistance - nearestSupport) * 0.236));
                fibonacciLevels.add((int) (nearestSupport + (nearestResistance - nearestSupport) * 0.382));
                fibonacciLevels.add((int) (nearestSupport + (nearestResistance - nearestSupport) * 0.5));
                fibonacciLevels.add((int) (nearestSupport + (nearestResistance - nearestSupport) * 0.618));
                fibonacciLevels.add((int) (nearestSupport + (nearestResistance - nearestSupport) * 0.786));
                fibonacciLevels.add((int) (nearestSupport + (nearestResistance - nearestSupport) * 1.0));
                fibonacciLevels.add((int) (nearestSupport + (nearestResistance - nearestSupport) * 1.236));
            } else if (orderType == PUT_BUY) {
                // Top to bottom
                fibonacciLevels.add((int) (nearestResistance - (nearestResistance - nearestSupport) * 0.236));
                fibonacciLevels.add((int) (nearestResistance - (nearestResistance - nearestSupport) * 0.382));
                fibonacciLevels.add((int) (nearestResistance - (nearestResistance - nearestSupport) * 0.5));
                fibonacciLevels.add((int) (nearestResistance - (nearestResistance - nearestSupport) * 0.618));
                fibonacciLevels.add((int) (nearestResistance - (nearestResistance - nearestSupport) * 0.786));
                fibonacciLevels.add((int) (nearestResistance - (nearestResistance - nearestSupport) * 1.0));
                fibonacciLevels.add((int) (nearestResistance - (nearestResistance - nearestSupport) * 1.236));
            }

        }

        return fibonacciLevels;
    }

    public Support findNearestSupport(Set<Support> supports, Double ltp, boolean isEma, double buffer) {
        // found nearest support from the list
        Support nearestSupport = null;
        if (!supports.isEmpty()) {
            nearestSupport = supports.stream()
                    .filter(support -> support.getSupportValue() < (ltp - buffer) && support.isEma() == isEma)
                    .max(Comparator.comparing(Support::getSupportValue))
                    .orElse(null);
        }
        return nearestSupport;
    }

    public Resistance findNearestResistance(Set<Resistance> resistances, Double ltp, boolean isEma, double buffer) {
        // found nearest resistance from the list
        Resistance nearestResistance = null;
        if (!resistances.isEmpty()) {
            nearestResistance = resistances.stream()
                    .filter(resistance -> resistance.getResistanceValue() > (ltp + buffer) && resistance.isEma() == isEma)
                    .min(Comparator.comparing(Resistance::getResistanceValue))
                    .orElse(null);
        }
        return nearestResistance;
    }

    public Integer getNearestSupportFromFibonacciLevels(Set<Integer> fibonacciLevels, Double ltp, double buffer) {
        return fibonacciLevels.stream()
                .filter(fibonacciLevel -> fibonacciLevel <= (ltp - buffer))
                .max(Comparator.naturalOrder())
                .orElse(Integer.MIN_VALUE);
    }


    public Integer getNearestResistanceFromFibonacciLevels(Set<Integer> fibonacciLevels, Double ltp, double buffer) {
        return fibonacciLevels.stream()
                .filter(fibonacciLevel -> fibonacciLevel >= (ltp + buffer))
                .min(Comparator.naturalOrder())
                .orElse(Integer.MAX_VALUE);
    }


}
