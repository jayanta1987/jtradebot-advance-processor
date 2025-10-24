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
     * Uses baseMilestonePoints and decreases down to minMilestonePoints, then continues with 1-point increments.
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
        
        // Calculate how many milestones we can have with decremental approach
        int decrementalMilestones = (int) (baseMilestonePoints - minMilestonePoints + 1);
        
        if (milestoneNumber <= decrementalMilestones) {
            // Decremental phase: baseMilestonePoints, baseMilestonePoints-1, ..., minMilestonePoints
            return baseMilestonePoints - (milestoneNumber - 1);
        } else {
            // After decremental phase: all remaining milestones are 1 point
            return minMilestonePoints;
        }
    }


}
