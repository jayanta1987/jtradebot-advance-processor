package com.jtradebot.processor.score;

import com.jtradebot.processor.candleStick.CandlestickPattern;
import com.jtradebot.processor.model.CallCandleStickScores;
import com.jtradebot.processor.model.PutCandleStickScores;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;

import java.util.List;

import static com.jtradebot.processor.indicator.CandlestickPatternIndicator.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CandleStickScoreTracker {

    public CallCandleStickScores getBullishScore(List<Bar> bars) {
        CallCandleStickScores callCandleStickScores = new CallCandleStickScores();
        // Calculate and set scores for each pattern
        callCandleStickScores.setGreenCandle(calculatePatternScore(bars, CandlestickPattern::isGreenCandle));
        callCandleStickScores.setDoji(calculatePatternScore(bars, CandlestickPattern::isDoji));
        callCandleStickScores.setHammer(calculatePatternScore(bars, CandlestickPattern::isHammer));
        callCandleStickScores.setInvertedHammer(calculatePatternScore(bars, CandlestickPattern::isInvertedHammer));
        callCandleStickScores.setBullishEngulfing(calculatePatternScoreForTwoBars(bars, CandlestickPattern::isBullishEngulfing));
        callCandleStickScores.setBullishHarami(calculatePatternScoreForTwoBars(bars, CandlestickPattern::isBullishHarami));
        callCandleStickScores.setBullishDojiStar(calculatePatternScoreForTwoBars(bars, CandlestickPattern::isBullishDojiStar));
        callCandleStickScores.setBullishMorningStar(calculatePatternScoreForThreeBars(bars, CandlestickPattern::isBullishMorningStar));
        callCandleStickScores.setLongLowerShadow(calculatePatternScore(bars, CandlestickPattern::isLongLowerShadow));
        callCandleStickScores.setBullishMarubozu(calculatePatternScore(bars, CandlestickPattern::isBullishMarubozu));
        callCandleStickScores.calculateTotalScore();
        return callCandleStickScores;
    }

    public PutCandleStickScores getBearishScore(List<Bar> bars) {
        PutCandleStickScores putCandleStickScores = new PutCandleStickScores();
        // Calculate and set scores for each pattern
        putCandleStickScores.setRedCandle(calculatePatternScore(bars, CandlestickPattern::isRedCandle));
        putCandleStickScores.setDoji(calculatePatternScore(bars, CandlestickPattern::isDoji));
        putCandleStickScores.setShootingStar(calculatePatternScore(bars, CandlestickPattern::isShootingStar));
        putCandleStickScores.setHangingMan(calculatePatternScore(bars, CandlestickPattern::isHangingMan));
        putCandleStickScores.setBearishEngulfing(calculatePatternScoreForTwoBars(bars, CandlestickPattern::isBearishEngulfing));
        putCandleStickScores.setBearishHarami(calculatePatternScoreForTwoBars(bars, CandlestickPattern::isBearishHarami));
        putCandleStickScores.setBearishDojiStar(calculatePatternScoreForTwoBars(bars, CandlestickPattern::isBearishDojiStar));
        putCandleStickScores.setBearishEveningStar(calculatePatternScoreForThreeBars(bars, CandlestickPattern::isBearishEveningStar));
        putCandleStickScores.setLongUpperShadow(calculatePatternScore(bars, CandlestickPattern::isLongUpperShadow));
        putCandleStickScores.setBearishMarubozu(calculatePatternScore(bars, CandlestickPattern::isBearishMarubozu));
        putCandleStickScores.calculateTotalScore();
        return putCandleStickScores;
    }
}
