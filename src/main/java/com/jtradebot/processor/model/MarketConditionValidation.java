package com.jtradebot.processor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketConditionValidation {
    private boolean isValid; // Whether market conditions are valid for entry
    private String marketSession; // Current market session
    private double spreadPercentage; // Current spread percentage
    private double volatility; // Current volatility
    private long volume; // Current volume
    private boolean isLiquid; // Whether market is liquid enough
    private boolean isNewsTime; // Whether it's news time
    private boolean isAvoidTimeSlot; // Whether it's in avoid time slot
    private LocalDateTime validationTime; // Time of validation
    private String rejectionReason; // Reason for rejection if not valid
    private List<String> warnings; // List of warnings
    private Map<String, Object> marketMetrics; // Additional market metrics
}
