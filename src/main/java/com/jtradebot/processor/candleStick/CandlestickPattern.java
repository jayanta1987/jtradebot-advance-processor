package com.jtradebot.processor.candleStick;

import org.ta4j.core.Bar;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

import static com.jtradebot.processor.constants.SignalConstants.MIN_CANDLE_FULL_HEIGHT;


public class CandlestickPattern {

    public static boolean isDoji(Bar bar) {
        Num open = bar.getOpenPrice();
        Num close = bar.getClosePrice();
        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();

        // Doji: Close is very close to Open
        return close.minus(open).abs().isLessThan(high.minus(low).multipliedBy(DecimalNum.valueOf(0.1)));
    }

    public static boolean isHammer(Bar bar) {
        Num open = bar.getOpenPrice();
        Num close = bar.getClosePrice();
        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();

        // Hammer: Small body, long lower shadow
        Num body = close.minus(open).abs();
        Num lowerShadow = open.min(close).minus(low);
        Num upperShadow = high.minus(open.max(close));

        return body.isLessThan(lowerShadow.multipliedBy(DecimalNum.valueOf(0.5))) && upperShadow.isLessThan(body);
    }

    public static boolean isInvertedHammer(Bar bar) {
        Num open = bar.getOpenPrice();
        Num close = bar.getClosePrice();
        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();

        // Inverted Hammer: Small body, long upper shadow
        Num body = close.minus(open).abs();
        Num lowerShadow = open.min(close).minus(low);
        Num upperShadow = high.minus(open.max(close));

        return body.isLessThan(upperShadow.multipliedBy(DecimalNum.valueOf(0.5))) && lowerShadow.isLessThan(body);
    }

    public static boolean isShootingStar(Bar bar) {
        Num open = bar.getOpenPrice();
        Num close = bar.getClosePrice();
        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();

        // Shooting Star: Small body, long upper shadow
        Num body = close.minus(open).abs();
        Num lowerShadow = open.min(close).minus(low);
        Num upperShadow = high.minus(open.max(close));

        return body.isLessThan(upperShadow.multipliedBy(DecimalNum.valueOf(0.5))) && lowerShadow.isLessThan(body);
    }

    public static boolean isHangingMan(Bar bar) {
        Num open = bar.getOpenPrice();
        Num close = bar.getClosePrice();
        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();

        // Hanging Man: Small body, long lower shadow
        Num body = close.minus(open).abs();
        Num lowerShadow = open.min(close).minus(low);
        Num upperShadow = high.minus(open.max(close));

        return body.isLessThan(lowerShadow.multipliedBy(DecimalNum.valueOf(0.5))) && upperShadow.isLessThan(body);
    }

    public static boolean isSpinningTop(Bar bar) {
        Num open = bar.getOpenPrice();
        Num close = bar.getClosePrice();
        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();

        // Spinning Top: Small body relative to shadows
        Num body = close.minus(open).abs();
        return body.dividedBy(high.minus(low)).isLessThan(DecimalNum.valueOf(0.3));
    }

    public static boolean isMarubozu(Bar bar) {
        Num open = bar.getOpenPrice();
        Num close = bar.getClosePrice();
        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();

        // Marubozu: No shadows
        return high.minus(close).isLessThan(high.minus(low).multipliedBy(DecimalNum.valueOf(0.05))) &&
                open.minus(low).isLessThan(high.minus(low).multipliedBy(DecimalNum.valueOf(0.05)));
    }

    public static boolean isBullishEngulfing(Bar previousBar, Bar currentBar) {
        Num prevOpen = previousBar.getOpenPrice();
        Num prevClose = previousBar.getClosePrice();
        Num currOpen = currentBar.getOpenPrice();
        Num currClose = currentBar.getClosePrice();

        // Bullish Engulfing: Previous bar is bearish, current bar is bullish, and current body engulfs previous body
        return prevClose.isLessThan(prevOpen) && currClose.isGreaterThan(currOpen) &&
                currOpen.isLessThan(prevClose) && currClose.isGreaterThan(prevOpen);
    }

    public static boolean isBearishEngulfing(Bar previousBar, Bar currentBar) {
        Num prevOpen = previousBar.getOpenPrice();
        Num prevClose = previousBar.getClosePrice();
        Num currOpen = currentBar.getOpenPrice();
        Num currClose = currentBar.getClosePrice();

        // Bearish Engulfing: Previous bar is bullish, current bar is bearish, and current body engulfs previous body
        return prevClose.isGreaterThan(prevOpen) && currClose.isLessThan(currOpen) &&
                currOpen.isGreaterThan(prevClose) && currClose.isLessThan(prevOpen);
    }

    public static boolean isBullishHarami(Bar previousBar, Bar currentBar) {
        Num prevOpen = previousBar.getOpenPrice();
        Num prevClose = previousBar.getClosePrice();
        Num currOpen = currentBar.getOpenPrice();
        Num currClose = currentBar.getClosePrice();

        // Bullish Harami: Previous bar is bearish, current bar is bullish, and current body is within previous body
        return prevClose.isLessThan(prevOpen) && currClose.isGreaterThan(currOpen) &&
                currOpen.isGreaterThan(prevClose) && currClose.isLessThan(prevOpen);
    }

    public static boolean isBearishHarami(Bar previousBar, Bar currentBar) {
        Num prevOpen = previousBar.getOpenPrice();
        Num prevClose = previousBar.getClosePrice();
        Num currOpen = currentBar.getOpenPrice();
        Num currClose = currentBar.getClosePrice();

        // Bearish Harami: Previous bar is bullish, current bar is bearish, and current body is within previous body
        return prevClose.isGreaterThan(prevOpen) && currClose.isLessThan(currOpen) &&
                currOpen.isLessThan(prevClose) && currClose.isGreaterThan(prevOpen);
    }

    public static boolean isBullishDojiStar(Bar previousBar, Bar currentBar) {
        Num prevOpen = previousBar.getOpenPrice();
        Num prevClose = previousBar.getClosePrice();
        Num currOpen = currentBar.getOpenPrice();
        Num currClose = currentBar.getClosePrice();

        // Bullish Doji Star: Previous bar is bearish, current bar is doji, and current close is above previous open
        return prevClose.isLessThan(prevOpen) && isDoji(currentBar) && currClose.isGreaterThan(prevOpen);
    }

    public static boolean isBearishDojiStar(Bar previousBar, Bar currentBar) {
        Num prevOpen = previousBar.getOpenPrice();
        Num prevClose = previousBar.getClosePrice();
        Num currOpen = currentBar.getOpenPrice();
        Num currClose = currentBar.getClosePrice();

        // Bearish Doji Star: Previous bar is bullish, current bar is doji, and current close is below previous open
        return prevClose.isGreaterThan(prevOpen) && isDoji(currentBar) && currClose.isLessThan(prevOpen);
    }

    public static boolean isBullishMorningStar(Bar firstBar, Bar secondBar, Bar thirdBar) {
        if(getFullHeight(firstBar).isLessThan(DecimalNum.valueOf(MIN_CANDLE_FULL_HEIGHT))) {
            return false;
        }
        // Bullish Morning Star: First bar is bearish, second bar is doji/small body, third bar is bullish
        return firstBar.getClosePrice().isLessThan(firstBar.getOpenPrice()) &&
                isDoji(secondBar) &&
                thirdBar.getClosePrice().isGreaterThan(thirdBar.getOpenPrice()) &&
                secondBar.getClosePrice().isLessThan(firstBar.getClosePrice()) &&
                thirdBar.getClosePrice().isGreaterThan(secondBar.getClosePrice());
    }

    public static boolean isBearishEveningStar(Bar firstBar, Bar secondBar, Bar thirdBar) {
        if(getFullHeight(firstBar).isLessThan(DecimalNum.valueOf(MIN_CANDLE_FULL_HEIGHT))) {
            return false;
        }
        // Bearish Evening Star: First bar is bullish, second bar is doji/small body, third bar is bearish
        return firstBar.getClosePrice().isGreaterThan(firstBar.getOpenPrice()) &&
                isDoji(secondBar) &&
                thirdBar.getClosePrice().isLessThan(thirdBar.getOpenPrice()) &&
                secondBar.getClosePrice().isGreaterThan(firstBar.getClosePrice()) &&
                thirdBar.getClosePrice().isLessThan(secondBar.getClosePrice());
    }

    public static boolean isBullishMarubozu(Bar bar) {
        if(getFullHeight(bar).isLessThan(DecimalNum.valueOf(MIN_CANDLE_FULL_HEIGHT))) {
            return false;
        }
        Num open = bar.getOpenPrice();
        Num close = bar.getClosePrice();
        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();

        // Bullish Marubozu: No lower shadow
        return open.minus(low).isLessThan(high.minus(low).multipliedBy(DecimalNum.valueOf(0.05)));
    }

    public static boolean isLongLowerShadow(Bar bar) {
        if(getFullHeight(bar).isLessThan(DecimalNum.valueOf(MIN_CANDLE_FULL_HEIGHT))) {
            return false;
        }
        Num open = bar.getOpenPrice();
        Num close = bar.getClosePrice();
        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();

        // Long Lower Shadow: Lower shadow is at least twice the size of the body
        return open.minus(low).isGreaterThan(close.minus(open).multipliedBy(DecimalNum.valueOf(2)));
    }

    public static boolean isLongBody(Bar bar) {
        if(getFullHeight(bar).isLessThan(DecimalNum.valueOf(MIN_CANDLE_FULL_HEIGHT))) {
            return false;
        }
        Num open = bar.getOpenPrice();
        Num close = bar.getClosePrice();
        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();

        // Long Body: Body is at least 75% of the total range
        return close.minus(open).isGreaterThan(high.minus(low).multipliedBy(DecimalNum.valueOf(0.75)));
    }

    public static boolean isBullishLongBody(Bar bar) {
        if(getFullHeight(bar).isLessThan(DecimalNum.valueOf(MIN_CANDLE_FULL_HEIGHT))) {
            return false;
        }
        Num open = bar.getOpenPrice();
        Num close = bar.getClosePrice();
        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();

        // Bullish Long Body: Long body with close > open (green candle)
        return close.isGreaterThan(open) && 
               close.minus(open).isGreaterThan(high.minus(low).multipliedBy(DecimalNum.valueOf(0.75)));
    }

    public static boolean isBearishLongBody(Bar bar) {
        if(getFullHeight(bar).isLessThan(DecimalNum.valueOf(MIN_CANDLE_FULL_HEIGHT))) {
            return false;
        }
        Num open = bar.getOpenPrice();
        Num close = bar.getClosePrice();
        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();

        // Bearish Long Body: Long body with close < open (red candle)
        return close.isLessThan(open) && 
               open.minus(close).isGreaterThan(high.minus(low).multipliedBy(DecimalNum.valueOf(0.75)));
    }

    public static boolean isBearishMarubozu(Bar bar) {
        if(getFullHeight(bar).isLessThan(DecimalNum.valueOf(MIN_CANDLE_FULL_HEIGHT))) {
            return false;
        }
        Num open = bar.getOpenPrice();
        Num close = bar.getClosePrice();
        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();

        // Bearish Marubozu: No upper shadow
        return high.minus(close).isLessThan(high.minus(low).multipliedBy(DecimalNum.valueOf(0.05)));
    }

    public static boolean isLongUpperShadow(Bar bar) {
        if(getFullHeight(bar).isLessThan(DecimalNum.valueOf(MIN_CANDLE_FULL_HEIGHT))) {
            return false;
        }
        Num open = bar.getOpenPrice();
        Num close = bar.getClosePrice();
        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();

        // Long Upper Shadow: Upper shadow is at least twice the size of the body
        return high.minus(open).isGreaterThan(close.minus(open).multipliedBy(DecimalNum.valueOf(2)));
    }

    public static boolean isShortBody(Bar bar) {
        Num open = bar.getOpenPrice();
        Num close = bar.getClosePrice();
        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();

        // Short Body: Body is less than 25% of the total range
        return close.minus(open).isLessThan(high.minus(low).multipliedBy(DecimalNum.valueOf(0.25)));
    }

    public static boolean isGreenCandle(Bar bar) {
        Num open = bar.getOpenPrice();
        Num close = bar.getClosePrice();
        return close.isGreaterThan(open);
    }

    public static boolean isRedCandle(Bar bar) {
        Num open = bar.getOpenPrice();
        Num close = bar.getClosePrice();
        return close.isLessThan(open);
    }

    public static Num getFullHeight(Bar bar) {
        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();

        // Calculate the absolute distance between high and low
        return high.minus(low).abs();
    }

    /**
     * Check if current bar breaks out of an inside bar pattern
     * Inside bar: current bar's high and low are within previous bar's range
     * Breakout: current bar breaks above previous bar's high or below previous bar's low
     */
    public static boolean isInsideBarBreakout(Bar previousBar, Bar currentBar) {
        if (previousBar == null) return false;
        
        Num prevHigh = previousBar.getHighPrice();
        Num prevLow = previousBar.getLowPrice();
        Num currHigh = currentBar.getHighPrice();
        Num currLow = currentBar.getLowPrice();
        
        // Check if previous bar was an inside bar (current bar's range within previous bar's range)
        boolean wasInsideBar = currHigh.isLessThanOrEqual(prevHigh) && currLow.isGreaterThanOrEqual(prevLow);
        
        if (!wasInsideBar) return false;
        
        // Check for breakout: current bar breaks above previous high or below previous low
        boolean breakoutUp = currHigh.isGreaterThan(prevHigh);
        boolean breakoutDown = currLow.isLessThan(prevLow);
        
        return breakoutUp || breakoutDown;
    }

    /**
     * Check if current bar breaks down from an inside bar pattern (bearish signal)
     * Inside bar: current bar's high and low are within previous bar's range
     * Breakdown: current bar breaks below previous bar's low (downward breakout)
     */
    public static boolean isInsideBarBreakdown(Bar previousBar, Bar currentBar) {
        if (previousBar == null) return false;
        
        Num prevHigh = previousBar.getHighPrice();
        Num prevLow = previousBar.getLowPrice();
        Num currHigh = currentBar.getHighPrice();
        Num currLow = currentBar.getLowPrice();
        
        // Check if previous bar was an inside bar (current bar's range within previous bar's range)
        boolean wasInsideBar = currHigh.isLessThanOrEqual(prevHigh) && currLow.isGreaterThanOrEqual(prevLow);
        
        if (!wasInsideBar) return false;
        
        // Check for breakdown: current bar breaks below previous low (downward breakout only)
        boolean breakdownDown = currLow.isLessThan(prevLow);
        
        return breakdownDown;
    }

    /**
     * Check for wick rejection pattern (long upper shadow indicating rejection of higher prices)
     * This is typically a bearish signal when it occurs at resistance levels
     */
    public static boolean isWickRejectionFilter(Bar bar) {
        if(getFullHeight(bar).isLessThan(DecimalNum.valueOf(MIN_CANDLE_FULL_HEIGHT))) {
            return false;
        }
        
        Num open = bar.getOpenPrice();
        Num close = bar.getClosePrice();
        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();
        
        // Calculate body and upper shadow
        Num body = close.minus(open).abs();
        Num upperShadow = high.minus(open.max(close));
        Num totalRange = high.minus(low);
        
        // Wick rejection: upper shadow is at least 40% of total range and body is relatively small
        boolean longUpperShadow = upperShadow.isGreaterThan(totalRange.multipliedBy(DecimalNum.valueOf(0.4)));
        boolean smallBody = body.isLessThan(totalRange.multipliedBy(DecimalNum.valueOf(0.3)));
        
        return longUpperShadow && smallBody;
    }

    /**
     * Check for bullish wick rejection filter (lack of long upper shadow, indicating strong buying pressure)
     * This is the opposite of bearish wick rejection - bullish when upper shadow is small
     */
    public static boolean isBullishWickRejectionFilter(Bar bar) {
        if(getFullHeight(bar).isLessThan(DecimalNum.valueOf(MIN_CANDLE_FULL_HEIGHT))) {
            return false;
        }
        
        Num open = bar.getOpenPrice();
        Num close = bar.getClosePrice();
        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();
        
        // Calculate body and upper shadow
        Num body = close.minus(open).abs();
        Num upperShadow = high.minus(open.max(close));
        Num totalRange = high.minus(low);
        
        // Bullish wick filter: upper shadow is small (less than 25% of total range) and body is relatively large
        boolean smallUpperShadow = upperShadow.isLessThan(totalRange.multipliedBy(DecimalNum.valueOf(0.25)));
        boolean largeBody = body.isGreaterThan(totalRange.multipliedBy(DecimalNum.valueOf(0.4)));
        
        return smallUpperShadow && largeBody;
    }

    /**
     * Check if current candle has crossed above the previous candle's high
     * This indicates bullish momentum and potential breakout
     */
    public static boolean isCurrentCandleCrossedAbovePrevHigh(Bar previousBar, Bar currentBar) {
        if (previousBar == null) return false;
        
        Num prevHigh = previousBar.getHighPrice();
        Num currHigh = currentBar.getHighPrice();
        Num currLow = currentBar.getLowPrice();
        
        // Current candle crosses above previous high: current high > previous high
        // and current low was below previous high (indicating a cross)
        boolean crossedAbove = currHigh.isGreaterThan(prevHigh) && currLow.isLessThanOrEqual(prevHigh);
        
        return crossedAbove;
    }

    /**
     * Check if current candle has crossed below the previous candle's low
     * This indicates bearish momentum and potential breakdown
     */
    public static boolean isCurrentCandleCrossedBelowPrevLow(Bar previousBar, Bar currentBar) {
        if (previousBar == null) return false;
        
        Num prevLow = previousBar.getLowPrice();
        Num currHigh = currentBar.getHighPrice();
        Num currLow = currentBar.getLowPrice();
        
        // Current candle crosses below previous low: current low < previous low
        // and current high was above previous low (indicating a cross)
        boolean crossedBelow = currLow.isLessThan(prevLow) && currHigh.isGreaterThanOrEqual(prevLow);
        
        return crossedBelow;
    }

}
