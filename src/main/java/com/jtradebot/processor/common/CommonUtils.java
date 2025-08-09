package com.jtradebot.processor.common;

import com.jtradebot.processor.model.enums.EntryReason;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.repository.Transaction;
import com.jtradebot.processor.repository.document.TradeOrder;

import java.util.TreeSet;

import static com.jtradebot.processor.model.enums.OrderTypeEnum.CALL_BUY;
import static com.jtradebot.processor.model.enums.OrderTypeEnum.PUT_BUY;

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

}
