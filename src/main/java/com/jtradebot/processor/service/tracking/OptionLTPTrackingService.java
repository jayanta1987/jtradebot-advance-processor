package com.jtradebot.processor.service.tracking;

import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.repository.document.JtradeOrder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to track option LTP movements after order entry and analyze price patterns
 * to determine likelihood of reversal, continuation, or neutral state
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OptionLTPTrackingService {

    // Store LTP history for each active order (orderId -> LTPHistory)
    private final Map<String, LTPHistory> orderLTPHistoryMap = new ConcurrentHashMap<>();

    // Configuration constants
    private static final int LTP_HISTORY_SIZE = 50;
    private static final double MOVING_AVERAGE_THRESHOLD = 2.0; // 0.25% threshold for movement classification
    private static final int MOVING_AVERAGE_PERIOD = 10; // Last 5 prices for moving average
    private static final int NEUTRAL_EXIT_COUNT = 50; // Exit after 50 consecutive neutral signals
    
    // Minimum entries needed for analysis (at least 5 ticks for moving average)
    private static final int MIN_ENTRIES_FOR_ANALYSIS = 5;

    /**
     * Track LTP for a specific order
     */
    public void trackOptionLTP(JtradeOrder order, double currentOptionPrice, double currentIndexPrice) {
        try {
            // Get or create LTP history for this order
            LTPHistory ltpHistory = orderLTPHistoryMap.computeIfAbsent(order.getId(), 
                k -> new LTPHistory(order.getId(), order.getOrderType()));

            // Add current LTP to history
            LTPEntry currentEntry = new LTPEntry(currentOptionPrice, new Date(), currentIndexPrice);
            ltpHistory.addLTPEntry(currentEntry);

            // Log the tracking
            log.info("📊 LTP TRACKING - Order: {} | Option LTP: {} | Index: {} | History Size: {}", 
                order.getId(), String.format("%.2f", currentOptionPrice), 
                String.format("%.2f", currentIndexPrice), ltpHistory.getLtpEntries().size());

            // Analyze price movements when we have enough history
            if (ltpHistory.getLtpEntries().size() >= MIN_ENTRIES_FOR_ANALYSIS) {
                analyzePriceMovement(order, ltpHistory);
            }

        } catch (Exception e) {
            log.error("Error tracking LTP for order: {}", order.getId(), e);
        }
    }

    /**
     * Analyze price movements using moving average-based logic
     */
    private void analyzePriceMovement(JtradeOrder order, LTPHistory ltpHistory) {
        try {
            List<LTPEntry> entries = ltpHistory.getLtpEntries();
            if (entries.size() < MIN_ENTRIES_FOR_ANALYSIS) {
                return;
            }

            // Get last 5 prices for moving average calculation
            List<LTPEntry> recentEntries = entries.size() >= MOVING_AVERAGE_PERIOD ?
                entries.subList(entries.size() - MOVING_AVERAGE_PERIOD, entries.size()) : entries;
            
            // Calculate moving average of last 5 prices
            double movingAverage = calculateMovingAverage(recentEntries);
            double currentPrice = recentEntries.get(recentEntries.size() - 1).getOptionPrice();
            
            // Determine movement type based on current price vs moving average
            MovementType movementType = determineMovementTypeFromMovingAverage(currentPrice, movingAverage);
            
            // Update consecutive neutral counter
            updateNeutralCounter(ltpHistory, movementType);
            
            // Log analysis
            logMovingAverageAnalysis(order, currentPrice, movingAverage, movementType, ltpHistory.getConsecutiveNeutralCount());
            
            // Store analysis in history
            ltpHistory.setLatestMovementType(movementType);

        } catch (Exception e) {
            log.error("Error analyzing price movement for order: {}", order.getId(), e);
        }
    }

    /**
     * Calculate moving average of recent entries
     */
    private double calculateMovingAverage(List<LTPEntry> entries) {
        if (entries.isEmpty()) {
            return 0.0;
        }
        
        double sum = entries.stream()
            .mapToDouble(LTPEntry::getOptionPrice)
            .sum();
        
        return sum / entries.size();
    }
    
    /**
     * Determine movement type based on current price vs moving average
     */
    private MovementType determineMovementTypeFromMovingAverage(double currentPrice, double movingAverage) {
        double percentageDifference = ((currentPrice - movingAverage) / movingAverage) * 100;
        
        if (percentageDifference > MOVING_AVERAGE_THRESHOLD) {
            return MovementType.CONTINUATION; // Price above MA by >0.25%
        } else if (percentageDifference < -MOVING_AVERAGE_THRESHOLD) {
            return MovementType.REVERSAL; // Price below MA by >0.25%
        } else {
            return MovementType.NEUTRAL; // Price within ±0.25% of MA
        }
    }
    
    /**
     * Update consecutive neutral counter
     */
    private void updateNeutralCounter(LTPHistory ltpHistory, MovementType movementType) {
        if (movementType == MovementType.NEUTRAL) {
            ltpHistory.setConsecutiveNeutralCount(ltpHistory.getConsecutiveNeutralCount() + 1);
        } else {
            ltpHistory.setConsecutiveNeutralCount(0); // Reset counter on non-neutral signal
        }
    }
    
    /**
     * Log moving average analysis
     */
    private void logMovingAverageAnalysis(JtradeOrder order, double currentPrice, double movingAverage, 
                                       MovementType movementType, int consecutiveNeutralCount) {
        double percentageDifference = ((currentPrice - movingAverage) / movingAverage) * 100;
        
        log.info("📊 MOVING AVERAGE ANALYSIS - Order: {}", order.getId());
        log.info("   💰 Current Price: {} | Moving Average: {} | Difference: {}%", 
            String.format("%.2f", currentPrice), 
            String.format("%.2f", movingAverage),
            String.format("%+.2f", percentageDifference));
        log.info("   🎯 Movement Type: {} | Consecutive Neutral: {}/{}", 
            movementType.getDescription(), consecutiveNeutralCount, NEUTRAL_EXIT_COUNT);
        log.info("   ⏰ Analysis Time: {}", 
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }






    /**
     * Get LTP history for a specific order (for debugging or external access)
     */
    public LTPHistory getLTPHistory(String orderId) {
        return orderLTPHistoryMap.get(orderId);
    }

    /**
     * Clear LTP history for a specific order (called when order is closed)
     */
    public void clearLTPHistory(String orderId) {
        LTPHistory removed = orderLTPHistoryMap.remove(orderId);
        if (removed != null) {
            log.info("🗑️ Cleared LTP history for order: {} ({} entries)", orderId, removed.getLtpEntries().size());
        }
    }

    /**
     * Get all active LTP histories (for monitoring)
     */
    public Map<String, LTPHistory> getAllLTPHistories() {
        return new HashMap<>(orderLTPHistoryMap);
    }

    /**
     * Check if order should exit based on moving average price movement analysis
     * Returns detailed exit reason information
     */
    public PriceMovementExitInfo shouldExitBasedOnPriceMovement(String orderId) {
        LTPHistory ltpHistory = orderLTPHistoryMap.get(orderId);
        if (ltpHistory == null || ltpHistory.getLtpEntries().size() < MIN_ENTRIES_FOR_ANALYSIS) {
            return new PriceMovementExitInfo(false, null, null, 0, 0.0, 0.0);
        }

        MovementType latestMovementType = ltpHistory.getLatestMovementType();
        int consecutiveNeutralCount = ltpHistory.getConsecutiveNeutralCount();
        
        // Get current price and moving average for detailed description
        List<LTPEntry> entries = ltpHistory.getLtpEntries();
        List<LTPEntry> recentEntries = entries.size() >= MOVING_AVERAGE_PERIOD ? 
            entries.subList(entries.size() - MOVING_AVERAGE_PERIOD, entries.size()) : entries;
        double movingAverage = calculateMovingAverage(recentEntries);
        double currentPrice = recentEntries.get(recentEntries.size() - 1).getOptionPrice();
        double percentageDifference = ((currentPrice - movingAverage) / movingAverage) * 100;
        
        // Immediate exit on REVERSAL signal
        if (latestMovementType == MovementType.REVERSAL) {
            String reason = String.format("REVERSAL signal detected - Price: %.2f, MA: %.2f, Diff: %.2f%%", 
                currentPrice, movingAverage, percentageDifference);
            log.info("📈 IMMEDIATE REVERSAL EXIT - Order: {} | Reason: {}", orderId, reason);
            return new PriceMovementExitInfo(true, "REVERSAL", reason, consecutiveNeutralCount, currentPrice, movingAverage);
        }
        
        // Exit after 10 consecutive NEUTRAL signals
        if (consecutiveNeutralCount >= NEUTRAL_EXIT_COUNT) {
            String reason = String.format("NEUTRAL signal for %d consecutive times - Price: %.2f, MA: %.2f, Diff: %.2f%%", 
                consecutiveNeutralCount, currentPrice, movingAverage, percentageDifference);
            log.info("📈 NEUTRAL EXIT - Order: {} | Reason: {}", orderId, reason);
            return new PriceMovementExitInfo(true, "NEUTRAL", reason, consecutiveNeutralCount, currentPrice, movingAverage);
        }
        
        return new PriceMovementExitInfo(false, null, null, consecutiveNeutralCount, currentPrice, movingAverage);
    }



    // Data classes for LTP tracking and analysis

    @Data
    public static class LTPHistory {
        private String orderId;
        private OrderTypeEnum orderType;
        private List<LTPEntry> ltpEntries;
        private PriceMovementAnalysis latestAnalysis;
        private MovementType latestMovementType;
        private int consecutiveNeutralCount; // Counter for consecutive neutral signals

        public LTPHistory(String orderId, OrderTypeEnum orderType) {
            this.orderId = orderId;
            this.orderType = orderType;
            this.ltpEntries = new ArrayList<>();
            this.consecutiveNeutralCount = 0;
        }

        public void addLTPEntry(LTPEntry entry) {
            ltpEntries.add(entry);
            // Keep only the last LTP_HISTORY_SIZE entries
            if (ltpEntries.size() > LTP_HISTORY_SIZE) {
                ltpEntries.remove(0);
            }
        }
    }

    @Data
    public static class LTPEntry {
        private double optionPrice;
        private Date timestamp;
        private double indexPrice;

        public LTPEntry(double optionPrice, Date timestamp, double indexPrice) {
            this.optionPrice = optionPrice;
            this.timestamp = timestamp;
            this.indexPrice = indexPrice;
        }
    }

    @Data
    public static class PriceMovementAnalysis {
        private double totalChangeFromEntry;
        private double recentChange;
        private double volatility;
        private String trendDirection;
        private double trendStrength;
        private double priceStability;
        private double minPrice;
        private double maxPrice;
        private double priceRange;
        private double priceRangePercentage;
        private double avgTimeBetweenUpdates;
        private double entryPrice;
        private double firstPrice;
        private double lastPrice;
        private Date analysisTimestamp;
    }


    @Getter
    public enum MovementType {
        REVERSAL("Price Reversal", 85.0),
        CONTINUATION("Price Continuation", 75.0),
        NEUTRAL("Neutral Movement", 50.0);

        private final String description;
        private final double confidence;

        MovementType(String description, double confidence) {
            this.description = description;
            this.confidence = confidence;
        }

    }

    @Data
    public static class PriceMovementExitInfo {
        private boolean shouldExit;
        private String exitType; // "REVERSAL" or "NEUTRAL"
        private String detailedReason;
        private int consecutiveNeutralCount;
        private double currentPrice;
        private double movingAverage;

        public PriceMovementExitInfo(boolean shouldExit, String exitType, String detailedReason, 
                                   int consecutiveNeutralCount, double currentPrice, double movingAverage) {
            this.shouldExit = shouldExit;
            this.exitType = exitType;
            this.detailedReason = detailedReason;
            this.consecutiveNeutralCount = consecutiveNeutralCount;
            this.currentPrice = currentPrice;
            this.movingAverage = movingAverage;
        }
    }
}
