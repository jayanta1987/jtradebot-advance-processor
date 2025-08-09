package com.jtradebot.processor.strategy;

import com.jtradebot.processor.model.CpDetails;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.model.enums.TrendEnum;

public class DynamicValueCalculator {

    public static boolean isAllCpAvgJump(double minCp, double minMove, CpDetails cpDetails) {
        double longTrendCpAbs = cpDetails.getLongTrendCpAbs();
        double longTrendAvgAbs = cpDetails.getLongTrendAvgCpAbs();
        boolean isLongTrendAvgJump = longTrendCpAbs > minCp && (longTrendCpAbs > longTrendAvgAbs + minMove);
        boolean isShortTrendAvgJump = cpDetails.getCpAbs() > cpDetails.getShortAvgCpAbs() + (minMove / 2);
        boolean isMidTrendAvgJump = cpDetails.getShortAvgCpAbs() > cpDetails.getMidAvgCpAbs() + (minMove / 4);
        return isLongTrendAvgJump && isShortTrendAvgJump && isMidTrendAvgJump;
    }

    public static boolean isNoTradeZone(CpDetails cpDetails) {
        // Enhanced NTP-based filtering based on trade analysis
        if (cpDetails.getNtp() == null) {
            return true; // Default to no trade if NTP data is missing
        }
        
        // Get NTP values
        double ntp3Min = cpDetails.getNtp().getNtp3Min();
        double ntp5Min = cpDetails.getNtp().getNtp5Min();
        double ntp15Min = cpDetails.getNtp().getNtp15Min();
        
        // Critical NTP thresholds based on analysis
        boolean isNtp5MinCritical = ntp5Min > 1.5;  // High risk threshold
        boolean isNtp3MinCritical = ntp3Min > 2.5;  // High risk threshold  
        boolean isNtp15MinCritical = ntp15Min > 2.8; // High risk threshold
        
        // Very high risk - avoid all trades
        boolean isVeryHighRisk = ntp5Min > 3.0 || ntp3Min > 3.0 || ntp15Min > 3.5;
        
        // High risk - reduce position size or avoid
        boolean isHighRisk = isNtp5MinCritical || isNtp3MinCritical || isNtp15MinCritical;
        
        // Original NTP check
        boolean originalNtpCheck = cpDetails.getNtp().isNoTradeZone(cpDetails);
        
        // Enhanced NTP check: combine original logic with new thresholds
        return originalNtpCheck || isVeryHighRisk || isHighRisk;
    }

    public static boolean isNoDynamicTradeZone(CpDetails cpDetails) {
        return cpDetails.getNtp().isDynamicNoTradeZone(cpDetails);
    }

    public static boolean isAnyCpAvgJump(double minCp, double minMove, CpDetails cpDetails) {
        boolean isLongTrendAvgJump = cpDetails.getLongTrendCpAbs() >= minCp &&
                (cpDetails.getLongTrendCpAbs() >= cpDetails.getLongTrendAvgCpAbs() + minMove);
        boolean isShortTrendAvgJump = cpDetails.getCpAbs() >= minCp &&
                (cpDetails.getCpAbs() >= cpDetails.getShortAvgCpAbs() + minMove);
        boolean isMidTrendAvgJump = cpDetails.getShortAvgCpAbs() >= minCp &&
                (cpDetails.getShortAvgCpAbs() >= cpDetails.getMidAvgCpAbs() + minMove);
        return isLongTrendAvgJump || isShortTrendAvgJump || isMidTrendAvgJump;
    }

    public static boolean isNoTradeZoneOrAnyCpAvgJump(CpDetails cpDetails, double minCp, double minMove) {
        return !cpDetails.getNtp().isNoTradeZone(cpDetails) && isAnyCpAvgJump(minCp, minMove, cpDetails);
    }

    public static boolean isCPHighEnoughForScalping(double minCp, CpDetails cpDetails) {
        // Enhanced scalping strategy that detects sudden CP movements with NTP awareness
        double longTrendCpAbs = cpDetails.getLongTrendCpAbs();
        double cpAbs = cpDetails.getCpAbs();
        double oneMinCpAbs = cpDetails.getOneMinCpAbs();
        
        // Get actual CP values (not absolute) for direction analysis
        double cp = cpDetails.getCp();
        double oneMinCp = cpDetails.getOneMinCp();
        double longTrendCp = cpDetails.getLongTrendCp();
        
        // NTP-aware CP thresholds (adjust based on NTP conditions)
        double adjustedMinCp = getNtpAdjustedMinCp(minCp, cpDetails);
        
        // Base threshold check with NTP adjustment
        boolean baseThresholdMet = longTrendCpAbs >= adjustedMinCp - 1;
        
        // Scenario 1: Strong current CP with recent surge (sudden movement detection)
        boolean scenario1 = cpAbs >= (adjustedMinCp + 2) && oneMinCpAbs >= (adjustedMinCp - 1);
        
        // Scenario 2: Moderate current CP but very strong recent surge
        boolean scenario2 = cpAbs >= adjustedMinCp && oneMinCpAbs >= adjustedMinCp + 3;
        
        // NEW: Scenario 3: Sudden directional change detection
        boolean suddenDirectionalChange = detectSuddenDirectionalChange(cpDetails, adjustedMinCp);
        
        // NEW: Scenario 4: CP momentum acceleration
        boolean cpMomentumAcceleration = detectCpMomentumAcceleration(cpDetails, adjustedMinCp);
        
        // NEW: Scenario 5: Multi-timeframe CP alignment
        boolean multiTimeframeAlignment = detectMultiTimeframeAlignment(cpDetails, adjustedMinCp);
        
        // NEW: Scenario 6: Strong directional CP with momentum
        boolean strongDirectionalMomentum = detectStrongDirectionalMomentum(cp, oneMinCp, longTrendCp, adjustedMinCp);
        
        // NEW: Scenario 7: NTP-optimized CP conditions
        boolean ntpOptimizedCp = detectNtpOptimizedCp(cpDetails, adjustedMinCp);
        
        return baseThresholdMet && (
            scenario1 || 
            scenario2 || 
            suddenDirectionalChange || 
            cpMomentumAcceleration || 
            multiTimeframeAlignment ||
            strongDirectionalMomentum ||
            ntpOptimizedCp
        );
    }
    
    /**
     * Adjusts minimum CP threshold based on NTP conditions
     * Higher NTP = Higher CP requirement for entry
     */
    private static double getNtpAdjustedMinCp(double baseMinCp, CpDetails cpDetails) {
        if (cpDetails.getNtp() == null) {
            return baseMinCp;
        }
        
        double ntp3Min = cpDetails.getNtp().getNtp3Min();
        double ntp5Min = cpDetails.getNtp().getNtp5Min();
        double ntp15Min = cpDetails.getNtp().getNtp15Min();
        
        // Calculate NTP risk factor
        double ntpRiskFactor = 0.0;
        
        // NTP 5Min is most critical based on analysis
        if (ntp5Min > 1.5) ntpRiskFactor += 1.0;
        if (ntp5Min > 2.0) ntpRiskFactor += 1.0;
        if (ntp5Min > 2.5) ntpRiskFactor += 1.0;
        
        // NTP 3Min secondary importance
        if (ntp3Min > 2.5) ntpRiskFactor += 0.5;
        if (ntp3Min > 3.0) ntpRiskFactor += 0.5;
        
        // NTP 15Min tertiary importance
        if (ntp15Min > 2.8) ntpRiskFactor += 0.5;
        if (ntp15Min > 3.0) ntpRiskFactor += 0.5;
        
        // Adjust CP threshold based on NTP risk
        return baseMinCp + (ntpRiskFactor * 1.5);
    }
    
    /**
     * Detects CP conditions that are optimized for current NTP levels
     */
    private static boolean detectNtpOptimizedCp(CpDetails cpDetails, double adjustedMinCp) {
        if (cpDetails.getNtp() == null) {
            return false;
        }
        
        double ntp5Min = cpDetails.getNtp().getNtp5Min();
        double cp = cpDetails.getCp();
        double cpAbs = cpDetails.getCpAbs();
        
        // For low NTP conditions (good for trading)
        if (ntp5Min <= 1.0) {
            // Lower CP threshold for low NTP
            return cpAbs >= (adjustedMinCp - 1.0) && Math.abs(cp) >= 6.0;
        }
        
        // For moderate NTP conditions
        if (ntp5Min <= 1.5) {
            // Standard CP threshold
            return cpAbs >= adjustedMinCp && Math.abs(cp) >= 7.0;
        }
        
        // For high NTP conditions (be more selective)
        if (ntp5Min <= 2.0) {
            // Higher CP threshold for high NTP
            return cpAbs >= (adjustedMinCp + 1.0) && Math.abs(cp) >= 8.0;
        }
        
        // For very high NTP conditions (avoid unless very strong CP)
        return cpAbs >= (adjustedMinCp + 2.0) && Math.abs(cp) >= 9.0;
    }
    
    /**
     * Detects sudden directional changes in CP values
     * Example: CP was -6 and suddenly becomes +8.5
     */
    private static boolean detectSuddenDirectionalChange(CpDetails cpDetails, double minCp) {
        double cp = cpDetails.getCp();
        double oneMinCp = cpDetails.getOneMinCp();
        double longTrendCp = cpDetails.getLongTrendCp();
        
        // Check for significant directional change in 1min CP
        boolean oneMinDirectionalChange = Math.abs(oneMinCp) >= minCp + 2 && 
                                       Math.signum(oneMinCp) != Math.signum(longTrendCp);
        
        // Check for current CP showing strong directional movement
        boolean currentCpDirectionalChange = Math.abs(cp) >= minCp + 1.5 && 
                                          Math.signum(cp) == Math.signum(oneMinCp);
        
        return oneMinDirectionalChange && currentCpDirectionalChange;
    }
    
    /**
     * Detects CP momentum acceleration across timeframes
     */
    private static boolean detectCpMomentumAcceleration(CpDetails cpDetails, double minCp) {
        double cpAbs = cpDetails.getCpAbs();
        double oneMinCpAbs = cpDetails.getOneMinCpAbs();
        double longTrendCpAbs = cpDetails.getLongTrendCpAbs();
        
        // Check if CP is accelerating (current > 1min > longTrend)
        boolean cpAcceleration = cpAbs > oneMinCpAbs && oneMinCpAbs > longTrendCpAbs;
        
        // Check if acceleration is significant enough
        boolean significantAcceleration = cpAbs >= minCp + 1 && 
                                       oneMinCpAbs >= minCp - 0.5 && 
                                       longTrendCpAbs >= minCp - 2;
        
        return cpAcceleration && significantAcceleration;
    }
    
    /**
     * Detects alignment across multiple timeframes with strong signals
     */
    private static boolean detectMultiTimeframeAlignment(CpDetails cpDetails, double minCp) {
        double cp = cpDetails.getCp();
        double oneMinCp = cpDetails.getOneMinCp();
        double longTrendCp = cpDetails.getLongTrendCp();
        
        // Check if all CPs are in the same direction
        boolean sameDirection = (cp > 0 && oneMinCp > 0 && longTrendCp > 0) ||
                              (cp < 0 && oneMinCp < 0 && longTrendCp < 0);
        
        // Check if at least two timeframes show strong signals
        int strongSignals = 0;
        if (Math.abs(cp) >= minCp) strongSignals++;
        if (Math.abs(oneMinCp) >= minCp + 1) strongSignals++;
        if (Math.abs(longTrendCp) >= minCp - 1) strongSignals++;
        
        boolean multipleStrongSignals = strongSignals >= 2;
        
        return sameDirection && multipleStrongSignals;
    }
    
    /**
     * Detects strong directional momentum in CP values
     * This checks if the current CP is significantly higher than the 1min and longTrend averages
     */
    private static boolean detectStrongDirectionalMomentum(double cp, double oneMinCp, double longTrendCp, double minCp) {
        double cpAbs = Math.abs(cp);
        double oneMinCpAbs = Math.abs(oneMinCp);
        double longTrendCpAbs = Math.abs(longTrendCp);

        // Check if current CP is significantly higher than 1min average
        boolean cpHigherThanOneMinAvg = cpAbs >= minCp + 1 && oneMinCpAbs >= minCp - 0.5;

        // Check if current CP is significantly higher than longTrend average
        boolean cpHigherThanLongTrendAvg = cpAbs >= minCp - 1 && longTrendCpAbs >= minCp - 2;

        return cpHigherThanOneMinAvg && cpHigherThanLongTrendAvg;
    }
    
    /**
     * Calculates CP velocity (rate of change) to detect sudden movements
     * Higher velocity indicates faster CP changes
     */
    public static double calculateCpVelocity(CpDetails cpDetails) {
        // Calculate velocity based on current CP vs averages
        double cpVelocity = 0.0;
        
        if (cpDetails.getCp() != null && cpDetails.getShortAvgCp() != null) {
            // Velocity = current CP - short average CP
            cpVelocity = cpDetails.getCp() - cpDetails.getShortAvgCp();
        }
        
        return cpVelocity;
    }
    
    /**
     * Detects high velocity CP movements for scalping
     * This catches sudden jumps like -6 to +8.5 or +3 to +10.0
     */
    public static boolean isHighVelocityCpMovement(CpDetails cpDetails, double minVelocity) {
        double cpVelocity = calculateCpVelocity(cpDetails);
        double oneMinCpVelocity = 0.0;
        
        if (cpDetails.getOneMinCp() != null && cpDetails.getOneMinAvgCp() != null) {
            oneMinCpVelocity = cpDetails.getOneMinCp() - cpDetails.getOneMinAvgCp();
        }
        
        // Check for high velocity in either current CP or 1min CP
        boolean highCurrentVelocity = Math.abs(cpVelocity) >= minVelocity;
        boolean highOneMinVelocity = Math.abs(oneMinCpVelocity) >= minVelocity * 1.5; // 1min should be more sensitive
        
        // Also check if velocity direction matches CP direction
        boolean velocityDirectionMatch = (cpDetails.getCp() > 0 && cpVelocity > 0) || 
                                      (cpDetails.getCp() < 0 && cpVelocity < 0);
        
        return (highCurrentVelocity || highOneMinVelocity) && velocityDirectionMatch;
    }
    
    /**
     * Enhanced scalping detection with velocity analysis
     * This method combines traditional CP thresholds with velocity analysis
     */
    public static boolean isEnhancedScalpingOpportunity(double minCp, CpDetails cpDetails) {
        // Traditional CP threshold check
        boolean traditionalCheck = isCPHighEnoughForScalping(minCp, cpDetails);
        
        // Velocity-based check for sudden movements
        boolean velocityCheck = isHighVelocityCpMovement(cpDetails, 3.0); // 3.0 is minimum velocity threshold
        
        // Combined approach: either traditional high CP OR high velocity movement
        return traditionalCheck || velocityCheck;
    }
    
    /**
     * Provides NTP-based position sizing recommendations
     * Returns position size multiplier based on NTP risk levels
     */
    public static double getNtpBasedPositionSize(CpDetails cpDetails) {
        if (cpDetails.getNtp() == null) {
            return 0.5; // Default to half position if NTP data missing
        }
        
        double ntp3Min = cpDetails.getNtp().getNtp3Min();
        double ntp5Min = cpDetails.getNtp().getNtp5Min();
        double ntp15Min = cpDetails.getNtp().getNtp15Min();
        
        // Calculate risk score
        int riskScore = 0;
        
        // NTP 5Min is most critical
        if (ntp5Min <= 1.0) riskScore += 0;      // Low risk
        else if (ntp5Min <= 1.5) riskScore += 1;  // Moderate risk
        else if (ntp5Min <= 2.0) riskScore += 2;  // High risk
        else if (ntp5Min <= 2.5) riskScore += 3;  // Very high risk
        else riskScore += 4;                       // Extreme risk
        
        // NTP 3Min secondary
        if (ntp3Min > 2.5) riskScore += 1;
        if (ntp3Min > 3.0) riskScore += 1;
        
        // NTP 15Min tertiary
        if (ntp15Min > 2.8) riskScore += 1;
        if (ntp15Min > 3.0) riskScore += 1;
        
        // Return position size multiplier based on risk
        switch (riskScore) {
            case 0: return 1.0;    // Full position - low risk
            case 1: return 0.75;   // 75% position - moderate risk
            case 2: return 0.5;    // 50% position - high risk
            case 3: return 0.25;   // 25% position - very high risk
            default: return 0.0;   // No position - extreme risk
        }
    }
    
    /**
     * Checks if NTP conditions are optimal for trading
     * Returns true if NTP conditions are favorable
     */
    public static boolean isNtpOptimalForTrading(CpDetails cpDetails) {
        if (cpDetails.getNtp() == null) {
            return false;
        }
        
        double ntp5Min = cpDetails.getNtp().getNtp5Min();
        double ntp3Min = cpDetails.getNtp().getNtp3Min();
        double ntp15Min = cpDetails.getNtp().getNtp15Min();
        
        // Optimal conditions based on analysis
        return ntp5Min <= 1.5 && ntp3Min <= 2.5 && ntp15Min <= 2.8;
    }
    
    /**
     * Enhanced future signal validation with stronger criteria
     * Returns true if future signal is strong and reliable
     */
    public static boolean isFutureSignalStrong(CpDetails cpDetails) {
        if (!cpDetails.isFutureSignal()) {
            return false;
        }
        
        // Check if trend direction is clear
        if (cpDetails.getFutureTrend() == TrendEnum.SIDEWAYS) {
            return false;
        }
        
        // Require at least one surge indicator for strong signals
        if (!cpDetails.isFutureBullishSurge() && !cpDetails.isFutureBearishSurge()) {
            return false;
        }
        
        // Additional validation: check if future signal aligns with current CP direction
        boolean cpDirectionMatch = false;
        if (cpDetails.getFutureTrend() == TrendEnum.UP && cpDetails.getCp() > 0) {
            cpDirectionMatch = true;
        } else if (cpDetails.getFutureTrend() == TrendEnum.DOWN && cpDetails.getCp() < 0) {
            cpDirectionMatch = true;
        }
        
        return cpDirectionMatch;
    }
    
    /**
     * Validates future signal for specific order type
     * Returns true if future signal supports the intended trade direction
     */
    public static boolean isFutureSignalValidForOrderType(CpDetails cpDetails, OrderTypeEnum orderType) {
        if (!cpDetails.isFutureSignal()) {
            return false;
        }
        
        // Check if future trend aligns with order type
        if (orderType == OrderTypeEnum.CALL_BUY) {
            return cpDetails.getFutureTrend() == TrendEnum.UP && cpDetails.isFutureBullishSurge();
        } else if (orderType == OrderTypeEnum.PUT_BUY) {
            return cpDetails.getFutureTrend() == TrendEnum.DOWN && cpDetails.isFutureBearishSurge();
        }
        
        return false;
    }
    
    /**
     * Comprehensive future signal analysis
     * Returns a score from 0-3 based on future signal strength
     */
    public static int getFutureSignalScore(CpDetails cpDetails) {
        int score = 0;
        
        // Base future signal (1 point)
        if (cpDetails.isFutureSignal()) {
            score += 1;
        }
        
        // Clear trend direction (1 point)
        if (cpDetails.getFutureTrend() != TrendEnum.SIDEWAYS) {
            score += 1;
        }
        
        // Surge indicators (1 point)
        if (cpDetails.isFutureBullishSurge() || cpDetails.isFutureBearishSurge()) {
            score += 1;
        }
        
        return score;
    }
    
    /**
     * Enhanced scalping opportunity with future signal integration
     * Combines CP analysis with future signal validation
     */
    public static boolean isEnhancedScalpingWithFutureSignal(double minCp, CpDetails cpDetails) {
        // Traditional CP check
        boolean cpCheck = isEnhancedScalpingOpportunity(minCp, cpDetails);
        
        // Future signal check
        boolean futureSignalCheck = isFutureSignalStrong(cpDetails);
        
        // NTP check
        boolean ntpCheck = isNtpOptimalForTrading(cpDetails);
        
        // Combined approach: CP + Future Signal + NTP
        return cpCheck && futureSignalCheck && ntpCheck;
    }
}
