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
    private double totalTargetPoints; // Total target points for the trade (drives number of milestones)
    
    // Current state
    private double entryPrice;
    private double entryIndexPrice;
    private OrderTypeEnum orderType;
    
    // Milestone tracking
    private List<Milestone> targetMilestones;
    private int currentTargetMilestone; // Index into targetMilestones (0-based before first target)
    
    // Profit tracking
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

}
