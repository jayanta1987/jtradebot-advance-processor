package com.jtradebot.processor.indicator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class OIIndicator {

    // Cache to store previous OI per instrument token
    private final ConcurrentMap<String, Double> previousOiMap = new ConcurrentHashMap<>();

    public enum OISignal {
        LONG_BUILDUP,        // Price ↑ + OI ↑ (Strong bullish)
        SHORT_BUILDUP,       // Price ↓ + OI ↑ (Strong bearish)
        SHORT_COVERING,      // Price ↑ + OI ↓ (Weak bullish)
        LONG_UNWINDING,      // Price ↓ + OI ↓ (Weak bearish)
        NONE                 // No valid signal
    }

    /**
     * Calculate OI signal for the given instrument.
     *
     * @param instrumentToken unique ID of the future contract
     * @param currentOI       latest OI from tick
     * @param isBullishCandle true if candle close > open
     * @param isBearishCandle true if candle close < open
     * @return OISignal classification
     */
    public OISignal calculateOiSignal(String instrumentToken, double currentOI,
                                      boolean isBullishCandle, boolean isBearishCandle) {
        double prevOI = previousOiMap.getOrDefault(instrumentToken, currentOI);
        boolean oiIncreasing = currentOI > prevOI;

        OISignal signal = OISignal.NONE;

        if (oiIncreasing && isBullishCandle) {
            signal = OISignal.LONG_BUILDUP;
        } else if (oiIncreasing && isBearishCandle) {
            signal = OISignal.SHORT_BUILDUP;
        } else if (!oiIncreasing && isBullishCandle) {
            signal = OISignal.SHORT_COVERING;
        } else if (!oiIncreasing && isBearishCandle) {
            signal = OISignal.LONG_UNWINDING;
        }

        // Update OI cache
        previousOiMap.put(instrumentToken, currentOI);

        return signal;
    }

    /**
     * Get only bullish OI signal (true if LONG_BUILDUP or SHORT_COVERING).
     */
    public boolean isBullishSignal(String instrumentToken, double currentOI,
                                   boolean isBullishCandle, boolean isBearishCandle) {
        OISignal signal = calculateOiSignal(instrumentToken, currentOI, isBullishCandle, isBearishCandle);
        return signal == OISignal.LONG_BUILDUP || signal == OISignal.SHORT_COVERING;
    }

    /**
     * Get only bearish OI signal (true if SHORT_BUILDUP or LONG_UNWINDING).
     */
    public boolean isBearishSignal(String instrumentToken, double currentOI,
                                   boolean isBullishCandle, boolean isBearishCandle) {
        OISignal signal = calculateOiSignal(instrumentToken, currentOI, isBullishCandle, isBearishCandle);
        return signal == OISignal.SHORT_BUILDUP || signal == OISignal.LONG_UNWINDING;
    }
}
