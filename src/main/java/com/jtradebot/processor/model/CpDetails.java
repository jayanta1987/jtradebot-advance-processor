package com.jtradebot.processor.model;

import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.model.enums.TrendEnum;
import com.jtradebot.processor.repository.document.Ntp;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Getter
@Slf4j
@ToString
public class CpDetails {

    private static final double HIGH_RISK_CP_ABS_THRESHOLD = 10;

    @Setter
    private OrderTypeEnum orderType;

    private Double cp, shortAvgCp, midAvgCp;
    private Double cpAbs, shortAvgCpAbs, midAvgCpAbs;

    private Double oneMinCp, oneMinAvgCp;
    private Double oneMinCpAbs, oneMinAvgCpAbs;

    private Double longTrendCp, longTrendAvgCp;
    private Double longTrendCpAbs, longTrendAvgCpAbs;

    private double cpDelta;
    private double oneMinCpDelta;
    private double longCpDelta;
    private double totalCpDelta;

    private boolean allCpInSameDirection;

    private boolean futureSignal;
    private TrendEnum futureTrend;
    private boolean futureBullishSurge;
    private boolean futureBearishSurge;

    @Setter
    private Ntp ntp;
    @Setter
    private List<NoTradeScores> noTradeScores;

    @Setter
    private CallData callData;
    @Setter
    private PutData putData;

    public void setCp(Double cp) {
        this.cp = cp;
        this.cpAbs = Math.abs(cp);
    }

    public void setShortAvgCp(Double shortAvgCp) {
        this.shortAvgCp = shortAvgCp;
        this.shortAvgCpAbs = Math.abs(shortAvgCp);
    }

    public void setMidAvgCp(Double midAvgCp) {
        this.midAvgCp = midAvgCp;
        this.midAvgCpAbs = Math.abs(midAvgCp);
    }

    public void setOneMinCp(Double oneMinCp) {
        this.oneMinCp = oneMinCp;
        this.oneMinCpAbs = Math.abs(oneMinCp);
    }

    public void setOneMinAvgCp(Double oneMinAvgCp) {
        this.oneMinAvgCp = oneMinAvgCp;
        this.oneMinAvgCpAbs = Math.abs(oneMinAvgCp);
    }

    public void setLongTrendCp(Double longTrendCp) {
        this.longTrendCp = longTrendCp;
        this.longTrendCpAbs = Math.abs(longTrendCp);
    }

    public void setLongTrendAvgCp(Double longTrendAvgCp) {
        this.longTrendAvgCp = longTrendAvgCp;
        this.longTrendAvgCpAbs = Math.abs(longTrendAvgCp);
    }

    public void calculateAllCpInSameDirection(OrderTypeEnum orderType) {
        this.allCpInSameDirection = (orderType == OrderTypeEnum.CALL_BUY && oneMinCp > 0 && cp > 0 && shortAvgCp > 0 && midAvgCp > 0 && longTrendAvgCp > 0)
                || (orderType == OrderTypeEnum.PUT_BUY && oneMinCp < 0 && cp < 0 && shortAvgCp < 0 && midAvgCp < 0 && longTrendAvgCp < 0);
    }

    public void calculateAllCpDeltas() {
        this.cpDelta = cpAbs - shortAvgCpAbs;
        this.oneMinCpDelta = oneMinCpAbs - oneMinAvgCpAbs;
        this.longCpDelta = longTrendCpAbs - longTrendAvgCpAbs;
        this.totalCpDelta = cpDelta + oneMinCpDelta + longCpDelta;
    }

    public void calculateFutureSignal(CallFutureScores callFutureScores, PutFutureScores putFutureScores) {
        if (callFutureScores == null || putFutureScores == null) {
            log.warn("CallFutureScores or PutFutureScores is null, cannot determine future signal.");
            this.futureSignal = false;
            this.futureTrend = TrendEnum.SIDEWAYS;
            this.futureBullishSurge = false;
            this.futureBearishSurge = false;
            return;
        }

        double callScore = callFutureScores.getTotalScore();
        double putScore = putFutureScores.getTotalScore();
        
        // Enhanced logic with stronger thresholds
        boolean hasStrongCallSignal = callScore >= 1.5; // Require stronger signal
        boolean hasStrongPutSignal = putScore >= 1.5;
        
        // Set surge indicators
        this.futureBullishSurge = callFutureScores.getPvsiScore() > 0;
        this.futureBearishSurge = putFutureScores.getPvsiScore() > 0;
        
        // Enhanced future signal logic with directional filtering
        if (hasStrongPutSignal && !hasStrongCallSignal) {
            this.futureSignal = true;
            this.futureTrend = TrendEnum.DOWN;
        } else if (hasStrongCallSignal && !hasStrongPutSignal) {
            this.futureSignal = true;
            this.futureTrend = TrendEnum.UP;
        } else if (hasStrongCallSignal && hasStrongPutSignal) {
            // Both signals are strong - use surge indicators to break tie
            if (this.futureBullishSurge && !this.futureBearishSurge) {
                this.futureSignal = true;
                this.futureTrend = TrendEnum.UP;
            } else if (this.futureBearishSurge && !this.futureBullishSurge) {
                this.futureSignal = true;
                this.futureTrend = TrendEnum.DOWN;
            } else {
                // Conflicting signals - no clear direction
                this.futureSignal = false;
                this.futureTrend = TrendEnum.SIDEWAYS;
            }
        } else {
            // Weak or no signals
            this.futureSignal = false;
            this.futureTrend = TrendEnum.SIDEWAYS;
        }
        
        // Additional validation: require at least one surge indicator for strong signals
        if (this.futureSignal && !this.futureBullishSurge && !this.futureBearishSurge) {
            log.debug("Future signal detected but no surge indicators present - weakening signal");
            // Optionally weaken the signal if no surge is present
            // this.futureSignal = false;
        }
    }

    public boolean isHighCP() {
        return cpAbs > HIGH_RISK_CP_ABS_THRESHOLD ||
               oneMinCpAbs > HIGH_RISK_CP_ABS_THRESHOLD ||
               longTrendCpAbs > HIGH_RISK_CP_ABS_THRESHOLD;
    }
}