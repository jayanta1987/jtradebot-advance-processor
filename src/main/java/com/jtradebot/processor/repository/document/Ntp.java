package com.jtradebot.processor.repository.document;

import com.jtradebot.processor.model.CpDetails;
import lombok.Data;

@Data
public class Ntp {
    public static final double HIGH_RISK_NTP_THRESHOLD = 1.0;
    public static final double EXTREME_HIGH_RISK_NTP_THRESHOLD = 3.0;
    public static final double MIN_CP_DELTA_THRESHOLD = 4.0;
    public static final double MIN_TREND_CP_ABS = 8.0;

    private double ntp3Min;
    private double ntp5Min;
    private double ntp15Min;

    public boolean isNoTradeZone(CpDetails cpDetails) {
        return cpDetails.getTotalCpDelta() < MIN_CP_DELTA_THRESHOLD
                && (ntp3Min > EXTREME_HIGH_RISK_NTP_THRESHOLD || ntp5Min > EXTREME_HIGH_RISK_NTP_THRESHOLD || ntp15Min > EXTREME_HIGH_RISK_NTP_THRESHOLD);
    }

    public boolean isDynamicNoTradeZone(CpDetails cpDetails) {
        double longTrendThresholdDelta = Math.max(0, cpDetails.getLongTrendCpAbs() - MIN_CP_DELTA_THRESHOLD);
        double cpTrendThresholdDelta = Math.max(0, cpDetails.getCpAbs() - MIN_CP_DELTA_THRESHOLD);
        double shortTrendThresholdDelta = Math.max(0, cpDetails.getShortAvgCpAbs() - MIN_CP_DELTA_THRESHOLD);
        double avgOfTrendThresholdDelta = (longTrendThresholdDelta + cpTrendThresholdDelta + shortTrendThresholdDelta) / 3;

        double ntp3MinRisk = Math.max(0, ntp3Min - HIGH_RISK_NTP_THRESHOLD);
        double ntp5MinRisk = Math.max(0, ntp5Min - HIGH_RISK_NTP_THRESHOLD);
        double ntp15MinRisk = Math.max(0, ntp15Min - HIGH_RISK_NTP_THRESHOLD);
        double avgNtpRisk = (ntp3MinRisk + ntp5MinRisk + ntp15MinRisk) / 3;

        return isNoTradeZone(cpDetails) || avgOfTrendThresholdDelta < avgNtpRisk;

    }
}
