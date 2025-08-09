package com.jtradebot.processor.repository.document;

import lombok.Data;

@Data
public class Ntp {
    public static final double HIGH_RISK_NTP_THRESHOLD = 1.0;
    public static final double EXTREME_HIGH_RISK_NTP_THRESHOLD = 3.0;

    private double ntp3Min;
    private double ntp5Min;
    private double ntp15Min;

    // CP-related methods removed - no longer needed in new strategy
    // The new strategy will handle risk management differently
}
