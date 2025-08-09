package com.jtradebot.processor.indicator;

import com.jtradebot.processor.candleStick.CandlestickPattern;
import com.jtradebot.processor.candleStick.TriFunction;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

@Service
public class CandlestickPatternIndicator {

    public static double calculatePatternScore(List<Bar> bars, Function<Bar, Boolean> patternFunction) {
        return bars.stream().mapToDouble(bar -> patternFunction.apply(bar) ? 1 : 0).average().orElse(0);
    }

    public static double calculatePatternScoreForTwoBars(List<Bar> bars, BiFunction<Bar, Bar, Boolean> patternFunction) {
        if (bars.size() < 2) return 0;
        int matches = 0;
        for (int i = 1; i < bars.size(); i++) {
            if (patternFunction.apply(bars.get(i - 1), bars.get(i))) {
                matches++;
            }
        }
        return (double) matches / (bars.size() - 1);
    }

    public static double calculatePatternScoreForThreeBars(List<Bar> bars, TriFunction<Bar, Bar, Bar, Boolean> patternFunction) {
        if (bars.size() < 3) return 0;
        int matches = 0;
        for (int i = 2; i < bars.size(); i++) {
            if (patternFunction.apply(bars.get(i - 2), bars.get(i - 1), bars.get(i))) {
                matches++;
            }
        }
        return (double) matches / (bars.size() - 2);
    }

    public static boolean hasBullishCandleStickPattern(Bar current1MinBar, Bar previous1MinBar, Bar previous2nd1MinBar) {
        return CandlestickPattern.isBullishEngulfing(current1MinBar, previous1MinBar) ||
                CandlestickPattern.isBullishHarami(current1MinBar, previous1MinBar) ||
                CandlestickPattern.isBullishDojiStar(current1MinBar, previous1MinBar) ||
                CandlestickPattern.isBullishMorningStar(current1MinBar, previous1MinBar, previous2nd1MinBar) ||
                CandlestickPattern.isBullishMarubozu(current1MinBar) ||
                CandlestickPattern.isLongLowerShadow(current1MinBar);
    }

    public static boolean hasBearishCandleStickPattern(Bar current1MinBar, Bar previous1MinBar, Bar previous2nd1MinBar) {
        return CandlestickPattern.isBearishEngulfing(current1MinBar, previous1MinBar) ||
                CandlestickPattern.isBearishHarami(current1MinBar, previous1MinBar) ||
                CandlestickPattern.isBearishDojiStar(current1MinBar, previous1MinBar) ||
                CandlestickPattern.isBearishEveningStar(current1MinBar, previous1MinBar, previous2nd1MinBar) ||
                CandlestickPattern.isBearishMarubozu(current1MinBar) ||
                CandlestickPattern.isLongUpperShadow(current1MinBar);
    }
}