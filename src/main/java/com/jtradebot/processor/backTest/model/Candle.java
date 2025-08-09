package com.jtradebot.processor.backTest.model;

import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Getter
public class Candle {
    @Setter
    private double open;

    @Setter
    private double high;

    @Setter
    private double low;

    @Setter
    private double close;

    @Setter
    private double lastTradedPrice;

    private double height;

    @Setter
    private long volume;

    @Setter
    private double oi;

    @Setter
    private ZonedDateTime lastUpdatedTime;
    @Setter
    private Candle previousCandle;

    private boolean greenCandle;
    private boolean redCandle;
    private boolean doji;
    private boolean hammer;
    private boolean invertedHammer;
    private boolean shootingStar;
    private boolean hangingMan;
    private boolean spinningTop;
    private boolean marubozu;
    private boolean longBody;
    private boolean shortBody;
    private boolean longUpperShadow;
    private boolean longLowerShadow;
    private boolean bullishEngulfing; // requires previous candle
    private boolean bearishEngulfing; // requires previous candle
    private boolean bullishHarami;    // requires previous candle
    private boolean bearishHarami;    // requires previous candle
    private boolean bullishDojiStar;  // requires previous candle
    private boolean bearishDojiStar;  // requires previous candle
    private boolean bullishMorningStar; // requires previous two candles
    private boolean bearishEveningStar; // requires previous two candles

    public Candle() {
        setCandleStickPatterns();
    }

    public int getBullishScore() {
        int score = 0;

        if (greenCandle) score += 1;
        if (longLowerShadow) score += 2.5;
        if (bullishEngulfing) score += 2;
        if (bullishHarami) score += 1;
        if (bullishDojiStar) score += 1;
        if (bullishMorningStar) score += 2;
        if (marubozu && greenCandle) score += 2.5;
        if (hammer) score += 1;
        if (invertedHammer) score += 1;

        // if previous candle's close is higher than previous to previous candle's close
        if(previousCandle != null && previousCandle.isGreenCandle() && previousCandle.previousCandle != null
                && previousCandle.getClose() > previousCandle.previousCandle.getClose()) {
            score += 1;
        }

        if(this.isGreenCandle() && this.longBody && this.height >=30) {
            score += 2;
        }
        if((this.high-this.low) <= 12) {
            score -= 3;
        }


        // Limiting the score to a maximum of 10
        return Math.min(score, 10);
    }

    public int getBearishScore() {
        int score = 0;

        if (redCandle) score += 1;
        if (longUpperShadow) score += 2.5;
        if (bearishEngulfing) score += 2;
        if (bearishHarami) score += 1;
        if (bearishDojiStar) score += 1;
        if (bearishEveningStar) score += 2;
        if (marubozu && redCandle) score += 2.5;
        if (shootingStar) score += 1;
        if (hangingMan) score += 1;

        if(previousCandle != null && previousCandle.isRedCandle() && previousCandle.previousCandle != null
                && previousCandle.getClose() < previousCandle.previousCandle.getClose()) {
            score += 1;
        }

        if(this.isRedCandle() && this.longBody && this.height >=30) {
            score += 2;
        }

        if((this.high-this.low) <= 12) {
            score -= 3;
        }


        // if current candle is green and body is more than 50


        // Limiting the score to a maximum of 10
        return Math.min(score, 10);
    }

    public void setCandleStickPatterns() {
        this.greenCandle = this.close > this.open;
        this.redCandle = this.close < this.open;
        this.doji = Math.abs(this.close - this.open) <= (this.high - this.low) * 0.1;
        this.hammer = checkHammer();
        this.invertedHammer = checkInvertedHammer();
        this.shootingStar = checkShootingStar();
        this.hangingMan = checkHangingMan();
        this.spinningTop = checkSpinningTop();
        this.marubozu = checkMarubozu();
        this.longBody = (this.close - this.open) / (this.high - this.low) > 0.6;
        this.shortBody = (this.close - this.open) / (this.high - this.low) < 0.2;
        this.longUpperShadow = (this.high - Math.max(this.open, this.close)) / (this.high - this.low) > 0.3;
        this.longLowerShadow = (Math.min(this.open, this.close) - this.low) / (this.high - this.low) > 0.3;
        this.height = Math.abs(this.open - this.close);
        // Multi-candle patterns
        if (this.previousCandle != null) {
            this.bullishEngulfing = checkBullishEngulfing();
            this.bearishEngulfing = checkBearishEngulfing();
            this.bullishHarami = checkBullishHarami();
            this.bearishHarami = checkBearishHarami();
            this.bullishDojiStar = checkBullishDojiStar();
            this.bearishDojiStar = checkBearishDojiStar();

            if (this.previousCandle.previousCandle != null) {
                this.bullishMorningStar = checkBullishMorningStar();
                this.bearishEveningStar = checkBearishEveningStar();
            }
        }
    }

    private boolean checkHammer() {
        double body = Math.abs(this.open - this.close);
        double lowerShadow = Math.min(this.open, this.close) - this.low;
        return body < lowerShadow && lowerShadow > (this.high - this.low) * 0.5;
    }

    private boolean checkInvertedHammer() {
        double body = Math.abs(this.open - this.close);
        double upperShadow = this.high - Math.max(this.open, this.close);
        return body < upperShadow && upperShadow > (this.high - this.low) * 0.5;
    }

    private boolean checkShootingStar() {
        double body = Math.abs(this.open - this.close);
        double upperShadow = this.high - Math.max(this.open, this.close);
        return body < upperShadow && upperShadow > (this.high - this.low) * 0.5;
    }

    private boolean checkHangingMan() {
        double body = Math.abs(this.open - this.close);
        double lowerShadow = Math.min(this.open, this.close) - this.low;
        return body < lowerShadow && lowerShadow > (this.high - this.low) * 0.5;
    }

    private boolean checkSpinningTop() {
        double body = Math.abs(this.open - this.close);
        return body / (this.high - this.low) < 0.3;
    }

    private boolean checkMarubozu() {
        return (this.high - this.close) < 0.05 * (this.high - this.low) &&
                (this.open - this.low) < 0.05 * (this.high - this.low);
    }

    private boolean checkBullishEngulfing() {
        return this.greenCandle && this.previousCandle.redCandle &&
                this.open < this.previousCandle.close && this.close > this.previousCandle.open;
    }

    private boolean checkBearishEngulfing() {
        return this.redCandle && this.previousCandle.greenCandle &&
                this.open > this.previousCandle.close && this.close < this.previousCandle.open;
    }

    private boolean checkBullishHarami() {
        return this.greenCandle && this.previousCandle.redCandle &&
                this.open > this.previousCandle.close && this.close < this.previousCandle.open;
    }

    private boolean checkBearishHarami() {
        return this.redCandle && this.previousCandle.greenCandle &&
                this.open < this.previousCandle.close && this.close > this.previousCandle.open;
    }

    private boolean checkBullishDojiStar() {
        return this.doji && this.previousCandle.redCandle && this.close > this.previousCandle.open;
    }

    private boolean checkBearishDojiStar() {
        return this.doji && this.previousCandle.greenCandle && this.close < this.previousCandle.open;
    }

    private boolean checkBullishMorningStar() {
        if (this.previousCandle.previousCandle == null) return false;
        Candle pc = this.previousCandle;
        Candle ppc = pc.previousCandle;
        return ppc.redCandle && pc.doji && this.greenCandle &&
                pc.close < ppc.close && this.close > pc.close && this.close > pc.open;
    }

    private boolean checkBearishEveningStar() {
        if (this.previousCandle.previousCandle == null) return false;
        Candle pc = this.previousCandle;
        Candle ppc = pc.previousCandle;
        return ppc.greenCandle && pc.doji && this.redCandle &&
                pc.close > ppc.close && this.close < pc.close && this.close < pc.open;
    }


}