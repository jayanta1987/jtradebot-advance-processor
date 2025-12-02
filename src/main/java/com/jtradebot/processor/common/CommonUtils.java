package com.jtradebot.processor.common;

import com.zerodhatech.models.Tick;

public class CommonUtils {




    public static double parseExitNtp(String entryReason, double entryNtp, String exitNtp) {
        if (exitNtp.startsWith("+")) {
            return entryNtp + Double.parseDouble(exitNtp.substring(1));
        } else {
            return Double.parseDouble(exitNtp);
        }
    }

    /**
     * Calculate decremental milestone points for a given milestone number.
     * Uses baseMilestonePoints and smoothly decreases down to minMilestonePoints over 5 milestones,
     * then continues with minMilestonePoints for all subsequent milestones.
     * 
     * @param milestoneNumber The milestone number (1-based)
     * @param baseMilestonePoints The base milestone points to start with
     * @param minMilestonePoints The minimum milestone points to decrease to
     * @return The calculated milestone points for the given milestone number
     */
    public static double calculateDecrementalMilestonePoints(int milestoneNumber, double baseMilestonePoints, double minMilestonePoints) {
        if (milestoneNumber <= 0) {
            return 0;
        }
        
        // Fixed number of decremental milestones for smooth transition
        int fixedDecrementalMilestones = 5;
        
        if (milestoneNumber <= fixedDecrementalMilestones) {
            // Smooth decremental phase: evenly distribute from baseMilestonePoints to minMilestonePoints
            double range = baseMilestonePoints - minMilestonePoints;
            // Divide range by (fixedDecrementalMilestones - 1) to get step size
            // -1 because first milestone is at base, last milestone is at min
            double stepSize = range / (fixedDecrementalMilestones - 1);
            double points = baseMilestonePoints - (stepSize * (milestoneNumber - 1));
            // Ensure we don't go below min (safety check for floating point precision)
            return Math.max(points, minMilestonePoints);
        } else {
            // After decremental phase: all remaining milestones use minMilestonePoints
            return minMilestonePoints;
        }
    }


}
