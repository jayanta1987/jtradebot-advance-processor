package com.jtradebot.processor.service.order;

import com.jtradebot.processor.config.TradingConfigurationService;
import com.jtradebot.processor.manager.BarSeriesManager;
import com.jtradebot.processor.model.MilestoneSystem;
import com.jtradebot.processor.model.MilestoneSystem.Milestone;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.jtradebot.processor.model.enums.ExitReasonEnum;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.repository.JtradeOrderRepository;
import com.jtradebot.processor.repository.document.JtradeOrder;
import com.jtradebot.processor.service.exit.ExitSignalTrackingService;
import com.jtradebot.processor.service.price.LiveOptionPricingService;
import com.jtradebot.processor.service.price.MockOptionPricingService;
import com.jtradebot.processor.service.tracking.OptionLTPTrackingService;
import com.jtradebot.processor.config.DayTradingSettingService;
import com.jtradebot.processor.service.TickSetupService;
import com.jtradebot.processor.repository.document.TradeConfig;
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
    private final OptionLTPTrackingService optionLTPTrackingService;
    private final DayTradingSettingService dayTradingSettingService;
    private final TickSetupService tickSetupService;


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


    /**
     * Check if entry should be blocked based on recent exit reasons
     * Blocks entry based on configuration:
     * - If enableTradeAfterStopLossHit is true: allows entry immediately (no blocking)
     * - If enableTradeAfterStopLossHit is false: blocks entry until the configured candle timeframe closes
     *   The stopLossBlockTimeframe specifies which candle timeframe to use (e.g., "ONE_MIN", "THREE_MIN", "FIVE_MIN")
     * 
     * @param instrumentToken The instrument token
     * @param timeframe The candle timeframe to check (e.g., ONE_MIN, FIVE_MIN) - used as fallback if config is not available
     * @param exitReasons Exit reasons to check for blocking (e.g., STOPLOSS_HIT, FORCE_EXIT)
     * @return true if entry should be blocked, false otherwise
     */
    public boolean shouldBlockEntryAfterStopLoss(Long instrumentToken, CandleTimeFrameEnum timeframe, ExitReasonEnum... exitReasons) {
        // If no previous exit or lastExitReason not in provided reasons, allow entry
        if (lastExitTime == null || !java.util.Arrays.asList(exitReasons).contains(lastExitReason)) {
            return false;
        }

        // Get trade config to check the enableTradeAfterStopLossHit flag
        try {
            TradeConfig tradeConfig = tickSetupService.getTradeConfig();
            TradeConfig.TradePreference tradePreference = tradeConfig.getTradePreference();
            
            if (tradePreference != null) {
                // Check if trading after stop loss is enabled
                if (tradePreference.isEnableTradeAfterStopLossHit()) {
                    log.info("ENTRY ALLOWED - enableTradeAfterStopLossHit is true, allowing entry after stop loss");
                    lastExitTime = null; // Reset to avoid repeated blocks
                    lastExitReason = null; // Reset to avoid repeated blocks
                    return false;
                }
                
                // If flag is false, use candle-based blocking with configured timeframe
                String blockTimeframeStr = tradePreference.getStopLossBlockTimeframe();
                CandleTimeFrameEnum blockingTimeframe = parseCandleTimeframe(blockTimeframeStr, timeframe);
                
                // Check if the candle is still open since the last exit
                boolean isCandleOpen = barSeriesManager.isCandleOpen(String.valueOf(instrumentToken), lastExitTime, blockingTimeframe);
                
                if (isCandleOpen) {
                    log.warn("🚫 ENTRY BLOCKED - Stop loss hit, blocking until {} candle closes (stopLossBlockTimeframe: {})", 
                            blockingTimeframe, blockTimeframeStr);
                    return true;
                } else {
                    log.info("ENTRY ALLOWED - {} candle has closed since stop loss hit (stopLossBlockTimeframe: {})", 
                            blockingTimeframe, blockTimeframeStr);
                    lastExitTime = null; // Reset to avoid repeated blocks
                    lastExitReason = null; // Reset to avoid repeated blocks
                    return false;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get trade config, falling back to candle-based blocking: {}", e.getMessage());
        }

        // Fallback to original candle-based blocking if config is not available
        boolean isCandleOpen = barSeriesManager.isCandleOpen(String.valueOf(instrumentToken), lastExitTime, timeframe);

        if (isCandleOpen) {
            return true;
        }
        lastExitTime = null; // Reset to avoid repeated blocks
        lastExitReason = null; // Reset to avoid repeated blocks
        log.info("ENTRY ALLOWED - {} candle has closed now", timeframe);
        return false;
    }

    /**
     * Parse the candle timeframe string to CandleTimeFrameEnum
     * @param timeframeStr The timeframe string (e.g., "ONE_MIN", "THREE_MIN", "FIVE_MIN")
     * @param defaultTimeframe The default timeframe to use if parsing fails
     * @return The parsed CandleTimeFrameEnum or default if parsing fails
     */
    private CandleTimeFrameEnum parseCandleTimeframe(String timeframeStr, CandleTimeFrameEnum defaultTimeframe) {
        if (timeframeStr == null || timeframeStr.trim().isEmpty()) {
            log.warn("stopLossBlockTimeframe is null or empty, using default: {}", defaultTimeframe);
            return defaultTimeframe;
        }
        
        try {
            return CandleTimeFrameEnum.valueOf(timeframeStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid stopLossBlockTimeframe value: {}, using default: {}", timeframeStr, defaultTimeframe);
            return defaultTimeframe;
        }
    }

    /**
     * Check if entry should be blocked based on 1-minute candle cooldown after any order exit
     * This prevents new orders from being created within the same 1-minute candle as the last order exit
     * 
     * @param instrumentToken The instrument token to check candle status
     * @return true if entry should be blocked due to 1-minute candle cooldown, false otherwise
     */
    public boolean shouldBlockEntryAfterOneMinuteCandleCooldown(Long instrumentToken) {
        if (lastExitTime == null) {
            return false; // No previous exit, allow entry
        }

        // Check if the 1-minute candle is still open since the last exit
        boolean isCandleOpen = barSeriesManager.isCandleOpen(String.valueOf(instrumentToken), lastExitTime, CandleTimeFrameEnum.ONE_MIN);

        if (isCandleOpen) {
            log.warn("🚫 ORDER CREATION BLOCKED - 1-minute candle cooldown active. Last exit: {} (same candle still open)",
                    formatDateToIST(lastExitTime));
            return true;
        }

        // Reset tracking if candle has closed
        lastExitTime = null;
        lastExitReason = null;
        log.info("✅ 1-MINUTE CANDLE COOLDOWN EXPIRED - Entry allowed after candle closed");
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
            // Check if this is a trailing stoploss hit by comparing stopLossPrice with targetMilestones or entryPrice
            if (isTrailingStopLossHit(order)) {
                log.info("🔒 TRAILING STOPLOSS HIT - Order: {} | Stop Loss Price: {} matches target milestone or entry price",
                        order.getId(), order.getStopLossPrice());
                return ExitReasonEnum.TRAILING_STOPLOSS_HIT;
            } else {
                log.info("🛑 REGULAR STOPLOSS HIT - Order: {} | Stop Loss Price: {}",
                        order.getId(), order.getStopLossPrice());
                return ExitReasonEnum.STOPLOSS_HIT;
            }
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
     * Check if the current stoploss hit is a trailing stoploss hit
     * A trailing stoploss hit occurs when the stopLossPrice matches:
     * 1. Any target milestone price, OR
     * 2. The entry price
     */
    private boolean isTrailingStopLossHit(JtradeOrder order) {
        Double stopLossPrice = order.getStopLossPrice();
        Double entryPrice = order.getEntryPrice();
        
        if (stopLossPrice == null || entryPrice == null) {
            return false;
        }
        
        // Check if stopLossPrice matches entryPrice
        if (Math.abs(stopLossPrice - entryPrice) < 0.01) { // Using small tolerance for double comparison
            log.debug("🔒 Trailing stoploss detected - StopLossPrice {} matches EntryPrice {}", 
                    stopLossPrice, entryPrice);
            return true;
        }
        
        // Check if stopLossPrice matches any target milestone price
        List<MilestoneSystem.Milestone> targetMilestones = order.getTargetMilestones();
        if (targetMilestones != null && !targetMilestones.isEmpty()) {
            for (MilestoneSystem.Milestone milestone : targetMilestones) {
                Double milestoneTargetPrice = milestone.getTargetPrice();
                if (milestoneTargetPrice != null && 
                    Math.abs(stopLossPrice - milestoneTargetPrice) < 0.01) { // Using small tolerance for double comparison
                    log.debug("🔒 Trailing stoploss detected - StopLossPrice {} matches Milestone {} TargetPrice {}", 
                            stopLossPrice, milestone.getMilestoneNumber(), milestoneTargetPrice);
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * Enhanced exit reason determination including time-based and strategy-based exits
     * Priority order: Stop Loss/Target > Strategy-based > Time-based
     */
    public ExitReasonEnum determineEnhancedExitReason(JtradeOrder order, Double currentLTP, Tick tick, double qualityScore, String dominantTrend) {

        // Check traditional stop loss and target FIRST (highest priority, if enabled)
        ExitReasonEnum stopLossOrTargetReason = null;
        if (dayTradingSettingService.isStopLossTargetExitEnabled()) {
            stopLossOrTargetReason = checkPrimaryExitReason(order, currentLTP);
            if (stopLossOrTargetReason == ExitReasonEnum.STOPLOSS_HIT ||
                    stopLossOrTargetReason == ExitReasonEnum.TRAILING_STOPLOSS_HIT ||
                    stopLossOrTargetReason == ExitReasonEnum.TARGET_HIT) {
                log.info("🎯 PRIORITY EXIT - Order: {} | Reason: {} | Current LTP: {}",
                        order.getId(), stopLossOrTargetReason, currentLTP);
                return stopLossOrTargetReason;
            }
        }

        // If FORCE_EXIT was returned due to missing data, return it immediately
        if (stopLossOrTargetReason == ExitReasonEnum.FORCE_EXIT) {
            log.warn("🔄 FORCE EXIT - Order: {} | Reason: Missing stop loss or target data", order.getId());
            return ExitReasonEnum.FORCE_EXIT;
        }

        // Check strategy-based exit (if enabled)
        if (dayTradingSettingService.isStrategyBasedExitEnabled() &&
            exitSignalTrackingService.shouldExitBasedOnStrategy(order, tick, qualityScore, dominantTrend)) {
            log.info("📊 STRATEGY EXIT - Order: {} | Reason: EXIT_SIGNAL", order.getId());
            return ExitReasonEnum.EXIT_SIGNAL;
        }

        // Check price movement-based exit (if enabled)
        if (dayTradingSettingService.isPriceMovementExitEnabled()) {
            OptionLTPTrackingService.PriceMovementExitInfo priceMovementExitInfo = 
                optionLTPTrackingService.shouldExitBasedOnPriceMovement(order.getId());
            
            if (priceMovementExitInfo.isShouldExit()) {
                // Store detailed exit reason in order comments for later reference
                String detailedExitReason = String.format("PRICE_MOVEMENT_EXIT - %s: %s", 
                    priceMovementExitInfo.getExitType(), priceMovementExitInfo.getDetailedReason());
                order.setComments(detailedExitReason);
                
                log.info("📈 PRICE MOVEMENT EXIT - Order: {} | Type: {} | Reason: {}", 
                    order.getId(), priceMovementExitInfo.getExitType(), priceMovementExitInfo.getDetailedReason());
                return ExitReasonEnum.PRICE_MOVEMENT_EXIT;
            }
        }

        // Check time-based exit last (lowest priority, if enabled)
        if (dayTradingSettingService.isTimeBasedExitEnabled() &&
            shouldExitBasedOnTime(order, tick.getTickTimestamp())) {
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

            // Handle milestone system for additional target-based exits (lowest priority, if enabled)
            if (dayTradingSettingService.isMilestoneBasedExitEnabled() &&
                order.getTargetMilestones() != null && !order.getTargetMilestones().isEmpty()) {
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

            if (dayTradingSettingService.isPriceMovementExitEnabled()) {
                // Track option LTP movements for analysis
                optionLTPTrackingService.trackOptionLTP(activeOrder, currentOptionPrice, currentIndexPrice);
            }

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
