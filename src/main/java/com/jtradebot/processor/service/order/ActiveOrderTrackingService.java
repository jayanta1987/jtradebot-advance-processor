package com.jtradebot.processor.service.order;

import com.jtradebot.processor.common.ProfileUtil;
import com.jtradebot.processor.config.TradingConfigurationService;
import com.jtradebot.processor.manager.BarSeriesManager;
import com.jtradebot.processor.model.MilestoneSystem;
import com.jtradebot.processor.model.MilestoneSystem.Milestone;
import com.jtradebot.processor.model.enums.ExitReasonEnum;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.repository.JtradeOrderRepository;
import com.jtradebot.processor.repository.document.JtradeOrder;
import com.jtradebot.processor.service.exit.ExitSignalTrackingService;
import com.jtradebot.processor.service.price.LiveOptionPricingService;
import com.jtradebot.processor.service.price.MockOptionPricingService;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Tick;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.jtradebot.processor.handler.DateTimeHandler.formatDateToIST;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActiveOrderTrackingService {

    private final JtradeOrderRepository jtradeOrderRepository;
    private final MockOptionPricingService mockOptionPricingService;

    private final TradingConfigurationService tradingConfigurationService;
    private final ExitSignalTrackingService exitSignalTrackingService;
    private final Environment environment;
    private final LiveOptionPricingService liveOptionPricingService;
    private final BarSeriesManager barSeriesManager;


    // In-memory storage for active orders
    private final Map<String, JtradeOrder> activeOrdersMap = new ConcurrentHashMap<>();

    // Flag to track if orders need database update
    private final AtomicBoolean needsUpdate = new AtomicBoolean(false);


    @Getter
    private ExitReasonEnum lastExitReason;
    @Getter
    private Date lastExitTime;

    @PostConstruct
    public void initializeService() {
        log.info("Initializing ExitStrategyService...");
        loadActiveOrdersFromDatabase();
        log.info("ExitStrategyService initialized with {} active orders", activeOrdersMap.size());
    }


    public boolean shouldBlockEntryAfterStopLoss(Long instrumentToken) {
        // If no previous exit or not a STOPLOSS_HIT, allow entry
        if (lastExitReason != ExitReasonEnum.STOPLOSS_HIT || lastExitTime == null) {
            return false;
        }

        // Check if the 5-minute candle has closed since the last exit
        boolean isCandleOpen = barSeriesManager.is5MinCandleOpen(String.valueOf(instrumentToken), lastExitTime);

        if (isCandleOpen) {
            log.info("🚫 ENTRY BLOCKED - Recent STOPLOSS_HIT exit in same 5-min candle. Last exit: {} at {}",
                    formatDateToIST(lastExitTime), formatDateToIST(lastExitTime));
            return true;
        }
        lastExitTime = null; // Reset to avoid repeated blocks
        lastExitReason = null; // Reset to avoid repeated blocks
        log.info("ENTRY ALLOWED - 5-min candle has closed now");
        return false;
    }


    public void updateExitTracking(ExitReasonEnum exitReason, Date exitTime) {
        this.lastExitReason = exitReason;
        this.lastExitTime = exitTime;

        log.info("📊 EXIT TRACKING UPDATED - Reason: {}, Time: {}",
                exitReason, formatDateToIST(exitTime));
    }


    public List<JtradeOrder> getActiveOrders() {
        return new ArrayList<>(activeOrdersMap.values());
    }

    private void loadActiveOrdersFromDatabase() {
        try {
            List<JtradeOrder> activeOrders = jtradeOrderRepository.findAllActiveOrders();
            activeOrdersMap.clear();

            for (JtradeOrder order : activeOrders) {
                setActiveOrderMap(order);
            }

            log.info("Loaded {} active orders from database", activeOrders.size());

        } catch (Exception e) {
            log.error("Error loading active orders from database: {}", e.getMessage(), e);
        }
    }


    private ExitReasonEnum checkPrimaryExitReason(JtradeOrder order, Double currentLTP) {
        // Check if we have valid stop loss and target prices
        if (order.getStopLossPrice() == null || order.getTargetPrice() == null) {
            log.warn("⚠️ Missing stop loss or target price - Order: {} | Stop Loss: {} | Target: {}",
                    order.getId(), order.getStopLossPrice(), order.getTargetPrice());
            return ExitReasonEnum.FORCE_EXIT; // Force exit if data is missing
        }

        // For both CALL and PUT orders, stop loss is hit when current price <= stop loss price
        // When option price goes down, it's a loss for both CALL and PUT
        if (currentLTP <= order.getStopLossPrice()) {
            return ExitReasonEnum.STOPLOSS_HIT;
        }

        // For both CALL and PUT orders, target is hit when current price >= target price
        // When option price goes up, it's profitable for both CALL and PUT
        if (currentLTP >= order.getTargetPrice()) {
            return ExitReasonEnum.TARGET_HIT;
        }

        // If neither stop loss nor target is hit, return null to indicate no exit needed
        return null;
    }

    /**
     * Enhanced exit reason determination including time-based and strategy-based exits
     * Priority order: Stop Loss/Target > Strategy-based > Time-based
     */
    public ExitReasonEnum determineEnhancedExitReason(JtradeOrder order, Double currentLTP, Tick tick, double qualityScore, String dominantTrend) {

        // Check traditional stop loss and target FIRST (highest priority)
        ExitReasonEnum stopLossOrTargetReason = checkPrimaryExitReason(order, currentLTP);
        if (stopLossOrTargetReason == ExitReasonEnum.STOPLOSS_HIT ||
                stopLossOrTargetReason == ExitReasonEnum.TARGET_HIT) {
            log.info("🎯 PRIORITY EXIT - Order: {} | Reason: {} | Current LTP: {}",
                    order.getId(), stopLossOrTargetReason, currentLTP);
            return stopLossOrTargetReason;
        }

        // If FORCE_EXIT was returned due to missing data, return it immediately
        if (stopLossOrTargetReason == ExitReasonEnum.FORCE_EXIT) {
            log.warn("🔄 FORCE EXIT - Order: {} | Reason: Missing stop loss or target data", order.getId());
            return ExitReasonEnum.FORCE_EXIT;
        }

        // Check strategy-based exit
        if (exitSignalTrackingService.shouldExitBasedOnStrategy(order, tick, qualityScore, dominantTrend)) {
            log.info("📊 STRATEGY EXIT - Order: {} | Reason: EXIT_SIGNAL", order.getId());
            return ExitReasonEnum.EXIT_SIGNAL;
        }

        // Check time-based exit last (lowest priority)
        if (shouldExitBasedOnTime(order, tick.getTickTimestamp())) {
            log.info("⏰ TIME EXIT - Order: {} | Reason: TIME_BASED_EXIT", order.getId());
            return ExitReasonEnum.TIME_BASED_EXIT;
        }

        return null;
    }

    /**
     * Check if order should be exited based on time limit
     * Only exit if no milestone has been reached AND time limit exceeded
     */
    private boolean shouldExitBasedOnTime(JtradeOrder order, Date currentTime) {
        if (order.getEntryTime() == null) {
            log.warn("Entry time is null for order: {}", order.getId());
            return false;
        }

        // Check if any milestone has been reached
        boolean anyMilestoneReached = hasAnyMilestoneBeenReached(order);
        if (anyMilestoneReached) {
            log.debug("⏰ Time-based exit skipped for order: {} - Milestone already reached", order.getId());
            return false;
        }

        // Get max holding time from tradeSettings configuration (in seconds)
        long maxHoldingTimeSeconds = tradingConfigurationService.getMaxTradeHoldingTimeInSec();

        try {
            // Parse entry time with literal IST and assign Asia/Kolkata zone explicitly
            DateTimeFormatter entryFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss 'IST'");
            LocalDateTime entryLocal = LocalDateTime.parse(order.getEntryTime(), entryFormatter);
            ZonedDateTime entryTime = entryLocal.atZone(ZoneId.of("Asia/Kolkata"));

            // Convert current time to IST
            ZonedDateTime currentISTTime = currentTime.toInstant().atZone(ZoneId.of("Asia/Kolkata"));

            // Calculate duration in seconds
            long durationSeconds = java.time.Duration.between(entryTime, currentISTTime).getSeconds();

            if (durationSeconds >= maxHoldingTimeSeconds) {
                log.info("⏰ Time-based exit triggered for order: {} - Duration: {} seconds (Max: {} seconds) - No milestones reached",
                        order.getId(), durationSeconds, maxHoldingTimeSeconds);
                return true;
            }

            return false;
        } catch (Exception e) {
            log.error("❌ Error parsing entry time for order: {} - Entry time: {}", order.getId(), order.getEntryTime(), e);
            return false; // Don't exit if we can't parse the time
        }
    }

    /**
     * Update index price tracking (min/max) for the order
     */
    private void updateIndexPriceTracking(JtradeOrder order, Double currentIndexPrice) {
        if (currentIndexPrice == null) {
            return;
        }

        // Update min index price
        if (order.getMinIndexPrice() == null || currentIndexPrice < order.getMinIndexPrice()) {
            order.setMinIndexPrice(currentIndexPrice);
        }

        // Update max index price
        if (order.getMaxIndexPrice() == null || currentIndexPrice > order.getMaxIndexPrice()) {
            order.setMaxIndexPrice(currentIndexPrice);
        }
    }

    /**
     * Check if any milestone has been reached for the order
     */
    private boolean hasAnyMilestoneBeenReached(JtradeOrder order) {
        List<Milestone> targetMilestones = order.getTargetMilestones();
        if (targetMilestones == null || targetMilestones.isEmpty()) {
            return false;
        }

        // Check if any milestone has been hit
        for (Milestone milestone : targetMilestones) {
            if (milestone.isTargetHit()) {
                return true;
            }
        }

        return false;
    }


    public List<JtradeOrder> getOrdersForExit(Tick tick, double qualityScore, String dominantTrend) {

        // Get current index price
        Double currentIndexPrice = tick.getLastTradedPrice(); // As tick is for index itself

        List<JtradeOrder> ordersToExit = new ArrayList<>();

        for (JtradeOrder order : getActiveOrders()) {
            // Get current price for this specific order (profile-aware)
            Double currentLTP = getCurrentPrice(order, currentIndexPrice);

            // Update index price tracking for all active orders
            updateIndexPriceTracking(order, currentIndexPrice);

            // Handle milestone system for additional target-based exits (lowest priority)
            if (order.getTargetMilestones() != null && !order.getTargetMilestones().isEmpty()) {
                handleTrailingStopLossAndMilestones(order, currentLTP, currentIndexPrice);
            }

            // Check all exit conditions including time-based, strategy-based, and market-based
            ExitReasonEnum exitReason = determineEnhancedExitReason(order, currentLTP, tick, qualityScore, dominantTrend);
            if (exitReason != null) {
                order.setExitReason(exitReason);
                ordersToExit.add(order);
            }
        }
        return ordersToExit;

    }

    private void handleTrailingStopLossAndMilestones(JtradeOrder order, Double currentLTP, Double currentIndexPrice) {
        // Milestone system only handles milestone-specific target exits
        // Stop loss and main target are handled in the main shouldExitOrder method
        List<MilestoneSystem.Milestone> targetMilestones = order.getTargetMilestones();
        if (targetMilestones == null || targetMilestones.isEmpty()) {
            return;
        }

        // Check each milestone for additional target-based exits
        for (MilestoneSystem.Milestone milestone : targetMilestones) {
            if (!milestone.isTargetHit() && currentLTP >= milestone.getTargetPrice()) {
                // Mark milestone as hit
                milestone.setTargetHit(true);
                milestone.setProfitAtMilestone(milestone.getPoints());

                // Add to milestone history
                if (order.getMilestoneHistory() == null) {
                    order.setMilestoneHistory(new ArrayList<>());
                }
                order.getMilestoneHistory().add(
                        String.format("Target milestone %d hit at price: %.2f, profit: %.2f",
                                milestone.getMilestoneNumber(), currentLTP, milestone.getPoints())
                );

                // CRITICAL: Update trailing stop loss after milestone hit
                updateTrailingStopLoss(order, milestone);

                // Update index price tracking
                updateIndexPriceTracking(order, currentIndexPrice);

                // Mark for database update
                setNeedUpdateFlag(true);

                // Log milestone exit with stop loss update
                log.info("🎯 MILESTONE TARGET HIT - {} {} @ {} | Milestone: {}, Profit: {} | New Stop Loss: {}",
                        order.getOrderType(), order.getTradingSymbol(), currentLTP,
                        milestone.getMilestoneNumber(), milestone.getPoints(), order.getStopLossPrice());

                return;
            }
        }
    }


    /**
     * Update trailing stop loss after milestone is hit
     * New logic: Stop loss moves to previous milestone price
     * - 1st milestone hit → stop loss becomes entry price
     * - 2nd milestone hit → stop loss becomes 1st milestone price
     * - 3rd milestone hit → stop loss becomes 2nd milestone price
     * - And so on...
     */
    private void updateTrailingStopLoss(JtradeOrder order, MilestoneSystem.Milestone milestone) {
        try {
            List<MilestoneSystem.Milestone> targetMilestones = order.getTargetMilestones();
            double oldStopLoss = order.getStopLossPrice();
            double newStopLossPrice;

            // Determine the new stop loss based on which milestone was hit
            if (milestone.getMilestoneNumber() == 1) {
                // 1st milestone hit → stop loss becomes entry price
                newStopLossPrice = order.getEntryPrice();
                log.info("🔒 TRAILING STOP LOSS UPDATED - Order: {} | 1st Milestone Hit | Old Stop Loss: {} | New Stop Loss: {} (Entry Price)",
                        order.getId(), oldStopLoss, newStopLossPrice);
            } else {
                // 2nd milestone and beyond → stop loss becomes previous milestone price
                MilestoneSystem.Milestone previousMilestone = null;
                for (MilestoneSystem.Milestone m : targetMilestones) {
                    if (m.getMilestoneNumber() == milestone.getMilestoneNumber() - 1) {
                        previousMilestone = m;
                        break;
                    }
                }

                if (previousMilestone != null) {
                    newStopLossPrice = previousMilestone.getTargetPrice();
                    log.info("🔒 TRAILING STOP LOSS UPDATED - Order: {} | Milestone {} Hit | Old Stop Loss: {} | New Stop Loss: {} (Milestone {} Price)",
                            order.getId(), milestone.getMilestoneNumber(), oldStopLoss, newStopLossPrice, previousMilestone.getMilestoneNumber());
                } else {
                    // Fallback to entry price if previous milestone not found
                    newStopLossPrice = order.getEntryPrice();
                    log.warn("⚠️ Previous milestone not found for milestone {}, using entry price as stop loss - Order: {}",
                            milestone.getMilestoneNumber(), order.getId());
                }
            }

            order.setStopLossPrice(newStopLossPrice);

            // Add to milestone history
            if (order.getMilestoneHistory() == null) {
                order.setMilestoneHistory(new ArrayList<>());
            }

            if (milestone.getMilestoneNumber() == 1) {
                order.getMilestoneHistory().add(
                        String.format("Stop loss updated to %.2f (entry price) after milestone %d hit",
                                newStopLossPrice, milestone.getMilestoneNumber())
                );
            } else {
                order.getMilestoneHistory().add(
                        String.format("Stop loss updated to %.2f (milestone %d price) after milestone %d hit",
                                newStopLossPrice, milestone.getMilestoneNumber() - 1, milestone.getMilestoneNumber())
                );
            }

        } catch (Exception e) {
            log.error("❌ Error updating trailing stop loss for order: {} | Milestone: {}",
                    order.getId(), milestone.getMilestoneNumber(), e);
        }
    }

    public void updateLivePnL(Tick indexTick) throws KiteException {
        try {
            // Get active order from ExitStrategyService (global check)
            List<JtradeOrder> activeOrders = getActiveOrders();
            if (activeOrders.isEmpty()) {
                return;
            }

            JtradeOrder activeOrder = activeOrders.get(0); // Get the first active order

            double currentIndexPrice = indexTick.getLastTradedPrice();
            double currentOptionPrice = getCurrentPrice(activeOrder, currentIndexPrice);
            double entryOptionPrice = activeOrder.getEntryPrice();
            double points = currentOptionPrice - entryOptionPrice;
            double pnl = points * activeOrder.getQuantity();

            // Simplified live P&L log (only points and P&L)
            String orderTypeDisplay = OrderTypeEnum.CALL_BUY.equals(activeOrder.getOrderType()) ? "CALL" : "PUT";
            log.info("_________________________ 💰 LIVE P&L - {} | Points: {}, P&L: ₹{}, LTP: {} _________________________",
                    orderTypeDisplay, String.format("%+.2f", points), String.format("%.2f", pnl), String.format("%.2f", currentOptionPrice));

        } catch (Exception e) {
            log.error("Error updating live P&L for tick: {}", indexTick.getInstrumentToken(), e);
        }
    }

    public Double getCurrentPrice(JtradeOrder order, Double currentIndexPrice) {
        // Use centralized price service
        Double currentPrice = liveOptionPricingService.getCurrentPrice(order.getInstrumentToken());

        if (currentPrice != null) {
            // Real price from KiteConnect (live profile)
            log.info("🎯 USING REAL PRICE FROM KITE API - Token: {}, Symbol: {}, Price: {}",
                    order.getInstrumentToken(), order.getTradingSymbol(), currentPrice);
            return currentPrice;
        } else {
            return mockOptionPricingService.calculateCurrentLTP(
                    order.getEntryPrice(),
                    order.getEntryIndexPrice(),
                    currentIndexPrice,
                    order.getOrderType()
            );
        }
    }

    public boolean hasActiveOrder() {
        return !activeOrdersMap.isEmpty();
    }

    public void setNeedUpdateFlag(Boolean flag) {
        needsUpdate.set(flag);
    }

    public boolean getNeedUpdateFlag() {
        return needsUpdate.get();
    }

    public void removeActiveOrderMap(String orderId) {
        activeOrdersMap.remove(orderId);
    }

    public void setActiveOrderMap(JtradeOrder order) {
        activeOrdersMap.put(order.getId(), order);
    }

    public JtradeOrder getOrderById(String orderId) {
        return activeOrdersMap.get(orderId);
    }


}
