package com.jtradebot.processor.common;

import com.jtradebot.processor.manager.TickDataManager;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import com.zerodhatech.models.Tick;

import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.FIVE_MIN;
import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.ONE_MIN;

public class CommonUtils {

    private static final double MAX_ATR = 50;
    private static final double MIN_STOP_LOSS_MILESTONE_DISTANCE = 4;
    private static final double MAX_STOP_LOSS_MILESTONE_DISTANCE = 6;

    private static final double MIN_TARGET_MILESTONE_DISTANCE = 3;
    private static final double MAX_TARGET_MILESTONE_DISTANCE = 5;


    public static double getMilStoneDistance(double distance, double oneMinATR, double minMilestoneDistance, double maxMilestoneDistance) {
        if (distance <= minMilestoneDistance) {
            return distance;
        }
        double atrFactor = getATRValue(oneMinATR);
        double result = distance * atrFactor;
        return Math.max(minMilestoneDistance, Math.min(maxMilestoneDistance, result));
    }

    private static double getATRValue(double oneMinATR) {
        // Ensure maxATR is greater than zero to avoid division by zero
        // Normalize ATR value to range 0 to 1
        return Math.max(0, Math.min(1, oneMinATR / CommonUtils.MAX_ATR)); // Higher oneMinATR gives higher atrFactor
    }

    public static double parseExitNtp(String entryReason, double entryNtp, String exitNtp) {
        if (exitNtp.startsWith("+")) {
            return entryNtp + Double.parseDouble(exitNtp.substring(1));
        } else {
            return Double.parseDouble(exitNtp);
        }
    }

    /**
     * Calculate dynamic milestone points based on ATR values and JSON configuration.
     * Returns the minimum of:
     * 1. 1/4 of ATR5Min
     * 2. 1/3 of ATR1Min  
     * 3. maxMilestonePoints
     * 
     * The result is constrained between minMilestonePoints and maxMilestonePoints.
     * 
     * @param tick The tick data for ATR calculation
     * @param tickDataManager Manager to get bar series data
     * @param minMilestonePoints The minimum milestone points from JSON configuration
     * @param maxMilestonePoints The maximum milestone points from JSON configuration
     * @return The calculated milestone points constrained between min and max
     */
    public static double calculateDynamicMilestonePoints(Tick tick, TickDataManager tickDataManager, double minMilestonePoints, double maxMilestonePoints) {
        try {
            // Null checks
            if (tick == null || tickDataManager == null) {
                System.out.println("❌ Null parameters provided, using maxMilestone fallback: " + maxMilestonePoints);
                return maxMilestonePoints;
            }
            
            String instrumentToken = String.valueOf(tick.getInstrumentToken());
            
            // Calculate ATR5Min
            double atr5Min = 0.0;
            BarSeries barSeries5Min = tickDataManager.getBarSeriesForTimeFrame(instrumentToken, FIVE_MIN);
            if (barSeries5Min != null && barSeries5Min.getBarCount() >= 14) {
                ATRIndicator atrIndicator5Min = new ATRIndicator(barSeries5Min, 14);
                atr5Min = atrIndicator5Min.getValue(barSeries5Min.getBarCount() - 1).doubleValue();
            }
            
            // Calculate ATR1Min
            double atr1Min = 0.0;
            BarSeries barSeries1Min = tickDataManager.getBarSeriesForTimeFrame(instrumentToken, ONE_MIN);
            if (barSeries1Min != null && barSeries1Min.getBarCount() >= 14) {
                ATRIndicator atrIndicator1Min = new ATRIndicator(barSeries1Min, 14);
                atr1Min = atrIndicator1Min.getValue(barSeries1Min.getBarCount() - 1).doubleValue();
            }
            
            // Calculate the ATR-based options
            double option1 = atr5Min / 4.0;  // 1/4 of ATR5Min
            double option2 = atr1Min / 3.0;  // 1/3 of ATR1Min
            
            // Use the maximum of the ATR-based calculations, but cap it at maxMilestonePoints
            double calculatedMilestone = Math.min(Math.max(option1, option2), maxMilestonePoints);
            
            // Ensure the result is within the min/max bounds
            calculatedMilestone = Math.max(minMilestonePoints, Math.min(maxMilestonePoints, calculatedMilestone));
            
            // Ensure minimum milestone points (fallback to maxMilestone if calculation fails)
            if (calculatedMilestone <= 0 || Double.isNaN(calculatedMilestone) || Double.isInfinite(calculatedMilestone)) {
                return maxMilestonePoints;
            }
            
            return calculatedMilestone;
            
        } catch (Exception e) {
            // Fallback to maxMilestone if any error occurs
            return maxMilestonePoints;
        }
    }

}
