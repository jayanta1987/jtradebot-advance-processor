package com.jtradebot.processor.model;

import com.jtradebot.processor.model.enums.OrderTypeEnum;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneSystem {
    
    // Configuration
    private boolean enabled;
    private double milestonePoints; // 5 points per milestone
    private double maxStopLossPoints; // Maximum 5 points stop loss
    private boolean trailingStopLoss;
    
    // Current state
    private double entryPrice;
    private double entryIndexPrice;
    private OrderTypeEnum orderType;
    
    // Milestone tracking
    private List<Milestone> targetMilestones;
    private int currentTargetMilestone; // 0, 1, 2, 3 (for 0, 5, 10, 15 points)
    
    // Trailing stop loss
    private double trailingStopLossPrice;
    private double highestProfitReached;
    private double lowestProfitReached;
    
    // Results
    private double totalProfit;
    private double totalReleasedProfit;
    private List<String> milestoneHistory;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Milestone {
        private int milestoneNumber; // 1, 2, 3
        private double points; // 5, 10, 15
        private double targetPrice;
        private boolean targetHit;
        private double profitAtMilestone;
    }
    
    /**
     * Initialize milestone system for a new trade
     */
    public void initialize(double entryPrice, double entryIndexPrice, OrderTypeEnum orderType) {
        this.entryPrice = entryPrice;
        this.entryIndexPrice = entryIndexPrice;
        this.orderType = orderType;
        this.currentTargetMilestone = 0;
        this.highestProfitReached = 0.0;
        this.lowestProfitReached = 0.0;
        this.totalProfit = 0.0;
        this.totalReleasedProfit = 0.0;
        this.milestoneHistory = new ArrayList<>();
        
        // Create target milestones (5, 10, 15 points)
        this.targetMilestones = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            double points = i * milestonePoints;
            double targetPrice = calculateTargetPrice(points);
            
            Milestone milestone = Milestone.builder()
                    .milestoneNumber(i)
                    .points(points)
                    .targetPrice(targetPrice)
                    .targetHit(false)
                    .profitAtMilestone(0.0)
                    .build();
            
            targetMilestones.add(milestone);
        }
        
        // Set initial trailing stop loss
        updateTrailingStopLoss(entryPrice);
        
        logMilestone("Milestone system initialized - Entry: {}, Target milestones: 5, 10, 15 points", entryPrice);
    }
    
    /**
     * Calculate target price based on points and order type
     */
    private double calculateTargetPrice(double points) {
        // For both CALL and PUT orders, target is always entryPrice + points
        // When option price goes up, it's profitable for both CALL and PUT
        return entryPrice + points;
    }
    
    /**
     * Calculate stop loss price based on points and order type
     */
    private double calculateStopLossPrice(double points) {
        // For both CALL and PUT orders, stop loss is always entryPrice - points
        // When option price goes down, it's a loss for both CALL and PUT
        return entryPrice - points;
    }
    
    /**
     * Process current price and check for milestone hits
     */
    public MilestoneResult processPrice(double currentPrice, double currentIndexPrice) {
        double currentProfit = calculateCurrentProfit(currentPrice);
        
        // Update highest/lowest profit reached
        if (currentProfit > highestProfitReached) {
            highestProfitReached = currentProfit;
            updateTrailingStopLoss(currentPrice);
        }
        if (currentProfit < lowestProfitReached) {
            lowestProfitReached = currentProfit;
        }
        
        // Check target milestones
        MilestoneResult targetResult = checkTargetMilestones(currentPrice, currentProfit);
        if (targetResult.isExitRequired()) {
            return targetResult;
        }
        
        // Check trailing stop loss (only if we've reached at least one target milestone)
        if (trailingStopLoss && currentTargetMilestone > 0 && isTrailingStopLossHit(currentPrice)) {
            MilestoneResult trailingResult = MilestoneResult.builder()
                    .exitRequired(true)
                    .exitReason("TRAILING_STOPLOSS_HIT")
                    .exitPrice(trailingStopLossPrice)
                    .exitIndexPrice(currentIndexPrice)
                    .profit(currentProfit)
                    .milestoneNumber(currentTargetMilestone)
                    .points(currentProfit)
                    .build();
            
            logMilestone("Trailing stop loss hit at price: {}, profit: {}", trailingStopLossPrice, currentProfit);
            return trailingResult;
        }
        
        // Check initial stop loss (only if no target milestone reached yet)
        if (currentTargetMilestone == 0 && isInitialStopLossHit(currentPrice)) {
            MilestoneResult stopLossResult = MilestoneResult.builder()
                    .exitRequired(true)
                    .exitReason("STOPLOSS_HIT")
                    .exitPrice(currentPrice)
                    .exitIndexPrice(currentIndexPrice)
                    .profit(currentProfit)
                    .milestoneNumber(0)
                    .points(-maxStopLossPoints)
                    .build();
            
            logMilestone("Initial stop loss hit at price: {}, profit: {}", currentPrice, currentProfit);
            return stopLossResult;
        }
        
        return MilestoneResult.builder()
                .exitRequired(false)
                .currentProfit(currentProfit)
                .currentTargetMilestone(currentTargetMilestone)
                .build();
    }
    
    /**
     * Check if any target milestones are hit
     */
    private MilestoneResult checkTargetMilestones(double currentPrice, double currentProfit) {
        for (int i = currentTargetMilestone; i < targetMilestones.size(); i++) {
            Milestone milestone = targetMilestones.get(i);
            
            boolean targetHit = false;
            // For both CALL and PUT orders, target is hit when current price >= target price
            // When option price goes up, it's profitable for both CALL and PUT
            targetHit = currentPrice >= milestone.getTargetPrice();
            
            if (targetHit && !milestone.isTargetHit()) {
                milestone.setTargetHit(true);
                milestone.setProfitAtMilestone(currentProfit);
                currentTargetMilestone = i + 1;
                
                // Release profit at this milestone
                double profitToRelease = milestone.getPoints();
                totalReleasedProfit += profitToRelease;
                
                logMilestone("Target milestone {} hit at price: {}, profit: {}, released: {}", 
                        milestone.getMilestoneNumber(), currentPrice, currentProfit, profitToRelease);
                
                // If this is the final milestone (15 points), exit
                if (milestone.getMilestoneNumber() == 3) {
                    return MilestoneResult.builder()
                            .exitRequired(true)
                            .exitReason("FINAL_TARGET_HIT")
                            .exitPrice(currentPrice)
                            .profit(currentProfit)
                            .milestoneNumber(milestone.getMilestoneNumber())
                            .points(milestone.getPoints())
                            .build();
                }
            }
        }
        
        return MilestoneResult.builder().exitRequired(false).build();
    }
    
    /**
     * Check if initial stop loss is hit (only before reaching first target milestone)
     */
    private boolean isInitialStopLossHit(double currentPrice) {
        double initialStopLossPrice = calculateStopLossPrice(maxStopLossPoints);
        
        // For both CALL and PUT orders, stop loss is hit when current price <= stop loss price
        // When option price goes down, it's a loss for both CALL and PUT
        return currentPrice <= initialStopLossPrice;
    }
    
    /**
     * Update trailing stop loss based on highest profit reached
     */
    private void updateTrailingStopLoss(double currentPrice) {
        if (!trailingStopLoss) return;
        
        double maxStopLossPoints = this.maxStopLossPoints;
        
        // For both CALL and PUT orders, trailing stop loss is current price - max stop loss points
        // This protects profits when option price goes down
        trailingStopLossPrice = currentPrice - maxStopLossPoints;
    }
    
    /**
     * Check if trailing stop loss is hit
     */
    private boolean isTrailingStopLossHit(double currentPrice) {
        if (!trailingStopLoss) return false;
        
        // For both CALL and PUT orders, trailing stop loss is hit when current price <= trailing stop loss price
        // This protects profits when option price goes down
        return currentPrice <= trailingStopLossPrice;
    }
    
    /**
     * Calculate current profit
     */
    private double calculateCurrentProfit(double currentPrice) {
        // For both CALL and PUT orders, profit is always current price - entry price
        // The option price (premium) determines profit/loss, not the underlying index
        return currentPrice - entryPrice;
    }
    
    /**
     * Log milestone event
     */
    private void logMilestone(String message, Object... args) {
        String logMessage = String.format(message, args);
        milestoneHistory.add(logMessage);
    }
    
    /**
     * Get current status summary
     */
    public String getStatusSummary() {
        return String.format("Target Milestone: %d/3, Released Profit: %.2f, Current Profit: %.2f", 
                currentTargetMilestone, totalReleasedProfit, 
                calculateCurrentProfit(entryPrice)); // This will be updated by caller
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MilestoneResult {
        private boolean exitRequired;
        private String exitReason;
        private double exitPrice;
        private double exitIndexPrice;
        private double profit;
        private int milestoneNumber;
        private double points;
        private double currentProfit;
        private int currentTargetMilestone;
    }
}
