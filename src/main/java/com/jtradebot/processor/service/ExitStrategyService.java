package com.jtradebot.processor.service;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.config.TradingConfigurationService;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.model.enums.ExitReasonEnum;
import com.jtradebot.processor.model.MilestoneSystem.Milestone;
import com.jtradebot.processor.repository.JtradeOrderRepository;
import com.jtradebot.processor.repository.document.JtradeOrder;

import com.jtradebot.processor.common.ProfileUtil;
import com.jtradebot.processor.service.price.LiveOptionPricingService;
import com.jtradebot.processor.service.price.OptionPricingService;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExitStrategyService {
    
    private final JtradeOrderRepository jtradeOrderRepository;
    private final OptionPricingService optionPricingService;
    private final DynamicStrategyConfigService configService;
    private final TradingConfigurationService tradingConfigurationService;
    private final ScalpingVolumeSurgeService scalpingVolumeSurgeService;
    private final Environment environment;
    private final LiveOptionPricingService liveOptionPricingService;
    
    // In-memory storage for active orders
    private final Map<String, JtradeOrder> activeOrdersMap = new ConcurrentHashMap<>();
    
    // Flag to track if orders need database update
    private final AtomicBoolean needsUpdate = new AtomicBoolean(false);
    
    @PostConstruct
    public void initializeService() {
        log.info("Initializing ExitStrategyService...");
        loadActiveOrdersFromDatabase();
        log.info("ExitStrategyService initialized with {} active orders", activeOrdersMap.size());
    }
    
    // Utility methods for IST time formatting
    private String formatDateToIST(Date date) {
        if (date == null) return null;
        ZonedDateTime istTime = date.toInstant().atZone(ZoneId.of("Asia/Kolkata"));
        return istTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss z"));
    }
    
    private String getCurrentISTTime() {
        ZonedDateTime istTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        return istTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss z"));
    }
    

    public JtradeOrder createOrderEntry(OrderTypeEnum orderType, String tradingSymbol, Long instrumentToken,
                                       Double entryPrice, Double entryIndexPrice, Double stopLossPrice,
                                       Double targetPrice, Integer quantity, Date entryTime) {
        
        // Check if there's already an active order
        if (hasActiveOrder()) {
            log.warn("Cannot create new order - there's already an active order. Please exit existing order first.");
            return null;
        }
        
        JtradeOrder order = new JtradeOrder();
        order.setId(UUID.randomUUID().toString());
        order.setOrderType(orderType);
        order.setTradingSymbol(tradingSymbol);
        order.setInstrumentToken(instrumentToken);
        order.setEntryPrice(entryPrice);
        order.setEntryIndexPrice(entryIndexPrice);
        order.setStopLossPrice(stopLossPrice);
        order.setTargetPrice(targetPrice);
        order.setQuantity(quantity);
        order.setStatus("ACTIVE");
        order.setEntryTime(formatDateToIST(entryTime));
        order.setCreatedAt(getCurrentISTTime());
        order.setLastUpdated(getCurrentISTTime());
        
        // Initialize milestone system
        initializeMilestoneSystem(order);
        
        // Store in memory
        activeOrdersMap.put(order.getId(), order);
        needsUpdate.set(true);
        
        log.info("Created new order entry: {} - {} @ {} (SL: {}, Target: {})", 
                orderType, tradingSymbol, entryPrice, stopLossPrice, targetPrice);
        
        return order;
    }
    

    public JtradeOrder createOrderEntryWithScenario(OrderTypeEnum orderType, String tradingSymbol, Long instrumentToken,
                                                   Double entryPrice, Double entryIndexPrice, Double stopLossPrice,
                                                   Double targetPrice, Integer quantity, String scenarioName,
                                                   String scenarioDescription, Double scenarioConfidence,
                                                   Map<String, Integer> categoryScores,
                                                   Map<String, List<String>> matchedConditions,
                                                   Boolean entryMarketConditionSuitable,
                                                   Date entryTime) {
        
        // Check if there's already an active order
        if (hasActiveOrder()) {
            log.warn("Cannot create new order - there's already an active order. Please exit existing order first.");
            return null;
        }
        
        JtradeOrder order = new JtradeOrder();
        order.setId(UUID.randomUUID().toString());
        order.setOrderType(orderType);
        order.setTradingSymbol(tradingSymbol);
        order.setInstrumentToken(instrumentToken);
        order.setEntryPrice(entryPrice);
        order.setEntryIndexPrice(entryIndexPrice);
        order.setStopLossPrice(stopLossPrice);
        order.setTargetPrice(targetPrice);
        order.setQuantity(quantity);
        order.setStatus("ACTIVE");
        order.setEntryTime(formatDateToIST(entryTime));
        order.setCreatedAt(getCurrentISTTime());
        order.setLastUpdated(getCurrentISTTime());
        
        // Store scenario information
        order.setEntryScenarioName(scenarioName);
        order.setEntryScenarioDescription(scenarioDescription);
        order.setEntryScenarioConfidence(scenarioConfidence);
        order.setEntryCategoryScores(categoryScores);
        order.setEntryMatchedConditions(matchedConditions);
        
        // Store market condition details at entry time
        order.setEntryMarketConditionSuitable(entryMarketConditionSuitable);
        
        // Initialize milestone system
        initializeMilestoneSystem(order);
        
        // Store in memory
        activeOrdersMap.put(order.getId(), order);
        needsUpdate.set(true);
        
        log.info("Created new order entry with scenario: {} - {} @ {} (SL: {}, Target: {}) - Scenario: {} (Confidence: {})", 
                orderType, tradingSymbol, entryPrice, stopLossPrice, targetPrice, scenarioName, scenarioConfidence);
        
        return order;
    }
    

    public JtradeOrder createOrderEntryWithRealisticPricing(OrderTypeEnum orderType, String tradingSymbol,
                                                          Long instrumentToken, Double currentIndexPrice,
                                                          Double stopLossPercentage, Double targetPercentage,
                                                          Integer quantity) {
        
        // Check if there's already an active order
        if (hasActiveOrder()) {
            log.warn("Cannot create new order - there's already an active order. Please exit existing order first.");
            return null;
        }
        
        // Calculate realistic entry price (1% of index price)
        Double entryPrice = optionPricingService.calculateEntryPrice(currentIndexPrice);
        if (entryPrice == null) {
            log.error("Failed to calculate entry price for index price: {}", currentIndexPrice);
            return null;
        }
        
        // Calculate stop loss and target prices
        Double stopLossPrice = optionPricingService.calculateStopLossPrice(entryPrice, stopLossPercentage, orderType);
        Double targetPrice = optionPricingService.calculateTargetPrice(entryPrice, targetPercentage, orderType);
        
        if (stopLossPrice == null || targetPrice == null) {
            log.error("Failed to calculate stop loss or target price for order type: {}", orderType);
            return null;
        }
        
        log.info("Creating realistic order: {} - {} @ {} (Index: {}, SL: {}, Target: {})", 
                orderType, tradingSymbol, entryPrice, currentIndexPrice, stopLossPrice, targetPrice);
        
        // Create order with calculated prices
                return createOrderEntry(orderType, tradingSymbol, instrumentToken, entryPrice, currentIndexPrice,
                               stopLossPrice, targetPrice, quantity, new Date());
    }
    

    public void exitOrder(String orderId, ExitReasonEnum exitReason, Double exitPrice, Double exitIndexPrice, Date exitTime) {
        JtradeOrder order = activeOrdersMap.get(orderId);
        if (order == null) {
            log.warn("Order not found for exit: {}", orderId);
            return;
        }
        
        order.markExited(exitReason, exitPrice, exitIndexPrice, exitTime);
        
        // Calculate profit/loss using option pricing service
        Double points = optionPricingService.calculateProfitLoss(
            order.getEntryPrice(), 
            exitPrice, 
            order.getOrderType()
        );
        
        order.setTotalPoints(points);
        order.setTotalProfit(points * order.getQuantity());
        
        // Save exited order to database immediately
        try {
            order.updateLastUpdated();
            jtradeOrderRepository.save(order);
            log.info("💾 EXITED ORDER SAVED TO DATABASE - ID: {}, Status: {}", order.getId(), order.getStatus());
        } catch (Exception e) {
            log.error("Error saving exited order to database: {}", e.getMessage(), e);
        }
        
        // Remove from active orders
        activeOrdersMap.remove(orderId);
        
        // Enhanced exit logging with clear visual indicator
        log.info("<<<<<<<<<EXIT>>>>>>>>> - Order: {} - {} @ {} (Reason: {}, Points: {}, Profit: {})", 
                order.getOrderType(), order.getTradingSymbol(), exitPrice, 
                exitReason, points, order.getTotalProfit());
        
        // Log exit details for analysis
        log.info("EXIT Details - Entry: {}, Exit: {}, Index Entry: {}, Index Exit: {}, Duration: {} minutes", 
                order.getEntryPrice(), exitPrice, order.getEntryIndexPrice(), exitIndexPrice,
                calculateOrderDurationMinutes(order));
    }
    

    public List<JtradeOrder> getActiveOrders() {
        return new ArrayList<>(activeOrdersMap.values());
    }
    

    public List<JtradeOrder> getActiveOrdersByType(OrderTypeEnum orderType) {
        return activeOrdersMap.values().stream()
                .filter(order -> order.getOrderType() == orderType)
                .toList();
    }
    

    public void checkAndProcessExits(Double currentPrice, Double currentIndexPrice) {
        if (activeOrdersMap.isEmpty()) {
            return;
        }
        
        // Log current profit/loss for all active orders
        logCurrentProfitLoss(currentIndexPrice);
        
        List<JtradeOrder> ordersToExit = new ArrayList<>();
        
        for (JtradeOrder order : activeOrdersMap.values()) {
            // Use the appropriate price source based on profile
            Double currentLTP = getCurrentOptionPriceForManualCheck(currentPrice);
            
            // Check all exit conditions
            if (shouldExitOrder(order, currentLTP, currentIndexPrice)) {
                ordersToExit.add(order);
            }
        }
        
        // Process exits
        for (JtradeOrder order : ordersToExit) {
            Double currentLTP = getCurrentOptionPriceForManualCheck(currentPrice);
            ExitReasonEnum exitReason = determineExitReason(order, currentLTP, currentIndexPrice);
            exitOrder(order.getId(), exitReason, currentLTP, currentIndexPrice, new Date()); // Use current time for non-tick exits
        }
    }
    

    public void checkAndProcessExitsWithStrategy(Tick tick) {
        if (activeOrdersMap.isEmpty()) {
            return;
        }
        
        // Get current index price
        Double currentIndexPrice = getCurrentIndexPrice(tick);
        
        // Log current profit/loss for all active orders
        logCurrentProfitLoss(currentIndexPrice);
        
        List<JtradeOrder> ordersToExit = new ArrayList<>();
        
        for (JtradeOrder order : activeOrdersMap.values()) {
            // Get current price for this specific order (profile-aware)
            Double currentLTP = getCurrentPrice(order, currentIndexPrice);
            
            // Update index price tracking for all active orders
            updateIndexPriceTracking(order, currentIndexPrice);
            
            // Log that we're checking exits for this order
            log.debug("🔍 Checking exits for order: {} - Entry time: {}, Current LTP: {}, Stop Loss: {}, Target: {}, Profile: {}", 
                    order.getId(), order.getEntryTime(), currentLTP, order.getStopLossPrice(), order.getTargetPrice(), 
                    getActiveProfile());
            
            // Check all exit conditions including time-based and strategy-based
            if (shouldExitOrder(order, currentLTP, currentIndexPrice) || 
                shouldExitBasedOnTime(order, tick.getTickTimestamp()) || 
                shouldExitBasedOnStrategy(order, tick)) {
                ordersToExit.add(order);
            }
        }
        
        // Process exits
        for (JtradeOrder order : ordersToExit) {
            Double currentLTP = getCurrentPrice(order, currentIndexPrice);
            ExitReasonEnum exitReason = determineEnhancedExitReason(order, currentLTP, currentIndexPrice, tick);
            
            // Only exit if we have a valid exit reason
            if (exitReason != null) {
                exitOrder(order.getId(), exitReason, currentLTP, currentIndexPrice, tick.getTickTimestamp()); // Use tick timestamp for accurate backtesting
            } else {
                log.debug("⏸️ SKIPPING EXIT - Order: {} | No exit conditions met", order.getId());
            }
        }
    }
    
    /**
     * Calculate current LTP based on index movement
     * LTP = Entry Price + (Current Index Price - Entry Index Price)
     */
    private Double calculateCurrentLTP(JtradeOrder order, Double currentIndexPrice) {
        Double calculatedLTP = optionPricingService.calculateCurrentLTP(
            order.getEntryPrice(), 
            order.getEntryIndexPrice(), 
            currentIndexPrice,
            order.getOrderType()
        );
        
        log.info("🧮 LTP CALCULATION - Order: {} | Entry Price: {} | Entry Index: {} | Current Index: {} | Calculated LTP: {} | Order Type: {}", 
                order.getId(), order.getEntryPrice(), order.getEntryIndexPrice(), currentIndexPrice, calculatedLTP, order.getOrderType());
        
        return calculatedLTP;
    }
    
    /**
     * Calculate order duration in minutes
     */
    private long calculateOrderDurationMinutes(JtradeOrder order) {
        if (order.getEntryTime() == null || order.getExitTime() == null) {
            return 0;
        }
        
        try {
            // Parse the IST time strings
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss z");
            ZonedDateTime entryTime = ZonedDateTime.parse(order.getEntryTime(), formatter);
            ZonedDateTime exitTime = ZonedDateTime.parse(order.getExitTime(), formatter);
            
            // Calculate duration in minutes
            return java.time.Duration.between(entryTime, exitTime).toMinutes();
        } catch (Exception e) {
            log.error("Error calculating order duration for order: {} - Entry: {}, Exit: {}", 
                    order.getId(), order.getEntryTime(), order.getExitTime(), e);
            return 0;
        }
    }
    

    public void updateOrdersToDatabase() {
        if (!needsUpdate.get()) {
            return;
        }
        
        try {
            // Update all active orders in database
            for (JtradeOrder order : activeOrdersMap.values()) {
                order.updateLastUpdated();
                jtradeOrderRepository.save(order);
            }
            
            needsUpdate.set(false);
            log.debug("Updated {} active orders to database", activeOrdersMap.size());
            
        } catch (Exception e) {
            log.error("Error updating orders to database: {}", e.getMessage(), e);
        }
    }
    

    public void initialize() {
        loadActiveOrdersFromDatabase();
    }
    
    private void loadActiveOrdersFromDatabase() {
        try {
            List<JtradeOrder> activeOrders = jtradeOrderRepository.findAllActiveOrders();
            activeOrdersMap.clear();
            
            for (JtradeOrder order : activeOrders) {
                activeOrdersMap.put(order.getId(), order);
            }
            
            log.info("Loaded {} active orders from database", activeOrders.size());
            
        } catch (Exception e) {
            log.error("Error loading active orders from database: {}", e.getMessage(), e);
        }
    }
    
    private boolean shouldExitOrder(JtradeOrder order, Double currentLTP, Double currentIndexPrice) {
        // MANDATORY: Check stop loss FIRST for ALL orders (highest priority)
        if (order.getStopLossPrice() != null && currentLTP <= order.getStopLossPrice()) {
            log.info("🛑 MANDATORY STOP LOSS HIT - Order: {} | Current: {} | Stop Loss: {} | Difference: {}", 
                    order.getId(), currentLTP, order.getStopLossPrice(), order.getStopLossPrice() - currentLTP);
            return true;
        }
        
        // Check target price for ALL orders (second priority)
        if (order.getTargetPrice() != null && currentLTP >= order.getTargetPrice()) {
            log.info("🎯 MANDATORY TARGET HIT - Order: {} | Current: {} | Target: {} | Difference: {}", 
                    order.getId(), currentLTP, order.getTargetPrice(), currentLTP - order.getTargetPrice());
            return true;
        }
        
        // Check milestone system for additional target-based exits (lowest priority)
        if (order.getTargetMilestones() != null && !order.getTargetMilestones().isEmpty()) {
            log.debug("🔍 CHECKING MILESTONE SYSTEM - Order: {} | Milestones: {} | Current LTP: {}", 
                    order.getId(), order.getTargetMilestones().size(), currentLTP);
            return shouldExitOrderWithMilestones(order, currentLTP, currentIndexPrice);
        }
        
        log.debug("✅ No exit conditions met - Order: {} | Current: {} | Stop Loss: {} | Target: {}", 
                order.getId(), currentLTP, order.getStopLossPrice(), order.getTargetPrice());
        return false;
    }
    
    private boolean shouldExitOrderWithMilestones(JtradeOrder order, Double currentLTP, Double currentIndexPrice) {
        // Milestone system only handles milestone-specific target exits
        // Stop loss and main target are handled in the main shouldExitOrder method
        List<Milestone> targetMilestones = order.getTargetMilestones();
        if (targetMilestones == null || targetMilestones.isEmpty()) {
            return false;
        }
        
        // Check each milestone for additional target-based exits
        for (Milestone milestone : targetMilestones) {
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
                needsUpdate.set(true);
                
                // Log milestone exit with stop loss update
                log.info("🎯 MILESTONE TARGET HIT - {} {} @ {} | Milestone: {}, Profit: {} | New Stop Loss: {}", 
                        order.getOrderType(), order.getTradingSymbol(), currentLTP,
                        milestone.getMilestoneNumber(), milestone.getPoints(), order.getStopLossPrice());
                
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Update trailing stop loss after milestone is hit
     * Simple logic: Use the latest hit milestone price as new stop loss
     */
    private void updateTrailingStopLoss(JtradeOrder order, Milestone milestone) {
        try {
            // Get the latest milestone that was hit (targetHit: true)
            List<Milestone> targetMilestones = order.getTargetMilestones();
            Milestone latestHitMilestone = null;
            
            for (Milestone m : targetMilestones) {
                if (m.isTargetHit()) {
                    latestHitMilestone = m;
                }
            }
            
            if (latestHitMilestone != null) {
                // Use the latest hit milestone price as new stop loss
                double oldStopLoss = order.getStopLossPrice();
                double newStopLossPrice = latestHitMilestone.getTargetPrice();
                
                order.setStopLossPrice(newStopLossPrice);
                
                log.info("🔒 TRAILING STOP LOSS UPDATED - Order: {} | Latest Hit Milestone: {} | Old Stop Loss: {} | New Stop Loss: {}", 
                        order.getId(), latestHitMilestone.getMilestoneNumber(), oldStopLoss, newStopLossPrice);
                
                // Add to milestone history
                if (order.getMilestoneHistory() == null) {
                    order.setMilestoneHistory(new ArrayList<>());
                }
                order.getMilestoneHistory().add(
                    String.format("Stop loss updated to %.2f (milestone %d price) after milestone %d hit", 
                        newStopLossPrice, latestHitMilestone.getMilestoneNumber(), milestone.getMilestoneNumber())
                );
            }
            
        } catch (Exception e) {
            log.error("❌ Error updating trailing stop loss for order: {} | Milestone: {}", 
                    order.getId(), milestone.getMilestoneNumber(), e);
        }
    }
    

    
    /**
     * Initialize milestone system for a new order (simplified)
     */
    private void initializeMilestoneSystem(JtradeOrder order) {
        try {
            // Get milestone configuration from strategy config
            double milestonePoints = order.getOrderType() == OrderTypeEnum.CALL_BUY ? 
                configService.getCallMilestonePoints() : configService.getPutMilestonePoints();
            double totalTargetPoints = order.getOrderType() == OrderTypeEnum.CALL_BUY ?
                configService.getCallTargetPoints() : configService.getPutTargetPoints();
            
            // Create target milestones
            List<Milestone> targetMilestones = new ArrayList<>();
            List<String> milestoneHistory = new ArrayList<>();
            
            if (milestonePoints > 0) {
                int milestoneCount = (int) Math.ceil(totalTargetPoints / milestonePoints);
                for (int i = 1; i <= milestoneCount; i++) {
                    double points = Math.min(i * milestonePoints, totalTargetPoints);
                    double targetPrice = order.getEntryPrice() + points; // Target is always entry + points
                    
                    Milestone milestone = Milestone.builder()
                            .milestoneNumber(i)
                            .points(points)
                            .targetPrice(targetPrice)
                            .targetHit(false)
                            .profitAtMilestone(0.0)
                            .build();
                    
                    targetMilestones.add(milestone);
                }
            }
            
            // Set milestone data in order
            order.setTargetMilestones(targetMilestones);
            order.setMilestoneHistory(milestoneHistory);
            
            // Initialize index price tracking
            order.setMinIndexPrice(order.getEntryIndexPrice());
            order.setMaxIndexPrice(order.getEntryIndexPrice());
            
            log.info("🎯 Simplified milestone system initialized for {} order - Milestones: {}, Step: {}, Total Target: {}", 
                    order.getOrderType(), targetMilestones.size(), milestonePoints, totalTargetPoints);
            
        } catch (Exception e) {
            log.error("Error initializing milestone system for order: {}", order.getId(), e);
            // Fallback to traditional system
            order.setTargetMilestones(null);
            order.setMilestoneHistory(null);
        }
    }
    
    private ExitReasonEnum determineExitReason(JtradeOrder order, Double currentLTP, Double currentIndexPrice) {
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
    private ExitReasonEnum determineEnhancedExitReason(JtradeOrder order, Double currentLTP, Double currentIndexPrice, Tick tick) {
        log.debug("🔍 DETERMINING EXIT REASON - Order: {} | Current LTP: {} | Priority: Stop Loss/Target > Strategy > Time", 
                order.getId(), currentLTP);
        
        // Check traditional stop loss and target FIRST (highest priority)
        ExitReasonEnum stopLossOrTargetReason = determineExitReason(order, currentLTP, currentIndexPrice);
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
        if (shouldExitBasedOnStrategy(order, tick)) {
            log.info("📊 STRATEGY EXIT - Order: {} | Reason: EXIT_SIGNAL", order.getId());
            return ExitReasonEnum.EXIT_SIGNAL;
        }
        
        // Check time-based exit last (lowest priority)
        if (shouldExitBasedOnTime(order, tick.getTickTimestamp())) {
            log.info("⏰ TIME EXIT - Order: {} | Reason: TIME_BASED_EXIT", order.getId());
            return ExitReasonEnum.TIME_BASED_EXIT;
        }
        
        // If no exit conditions are met, return null (no exit needed)
        log.debug("✅ NO EXIT NEEDED - Order: {} | Current LTP: {} | No conditions met", 
                order.getId(), currentLTP);
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
    
    /**
     * Check if order should be exited based on strategy reversal
     */
    private boolean shouldExitBasedOnStrategy(JtradeOrder order, Tick tick) {
        try {
            // For CALL orders, exit if PUT entry conditions are met (strategy reversal)
            if (order.getOrderType() == OrderTypeEnum.CALL_BUY) {
                if (scalpingVolumeSurgeService.shouldMakePutEntry(tick)) {
                    log.info("Strategy-based exit triggered for CALL order: {} - PUT entry conditions met", order.getId());
                    return true;
                }
            }
            // For PUT orders, exit if CALL entry conditions are met (strategy reversal)
            else if (order.getOrderType() == OrderTypeEnum.PUT_BUY) {
                if (scalpingVolumeSurgeService.shouldMakeCallEntry(tick)) {
                    log.info("Strategy-based exit triggered for PUT order: {} - CALL entry conditions met", order.getId());
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("Error checking strategy-based exit for order: {}", order.getId(), e);
        }
        
        return false;
    }
    

    public boolean hasActiveOrder() {
        return !activeOrdersMap.isEmpty();
    }
    

    public void logCurrentProfitLoss(Double currentIndexPrice) {
        if (activeOrdersMap.isEmpty()) {
            return;
        }
        
        for (JtradeOrder order : activeOrdersMap.values()) {
            // Note: This method is called from checkAndProcessExitsWithStrategy where we have the actual option price
            // For now, we'll use a placeholder since we don't have the actual option price in this context
            // In practice, this should be called with the actual option price from the tick data
            Double currentLTP = calculateCurrentLTP(order, currentIndexPrice); // Keep this for now as fallback
            Double profitLoss = optionPricingService.calculateProfitLoss(
                order.getEntryPrice(), 
                currentLTP, 
                order.getOrderType()
            );
            
            Double totalProfitLoss = profitLoss * order.getQuantity();
            
            log.debug("LIVE P&L - {} {}: Entry={}, Current LTP={}, P&L={}, Total P&L={}, Index={}", 
                order.getOrderType(), 
                order.getTradingSymbol(),
                order.getEntryPrice(),
                currentLTP,
                profitLoss,
                totalProfitLoss,
                currentIndexPrice);
        }
    }
    
    /**
     * Get the current index price based on profile and tick type
     */
    private Double getCurrentIndexPrice(Tick tick) {
        // For live profile, we need to get the actual Nifty index price
        // For local profile, use the tick price as index price
        if (isLiveProfile()) {
            // In live profile, we need to get the actual Nifty index price
            // This should come from a separate tick or be passed as parameter
            return tick.getLastTradedPrice(); // For now, use tick price as fallback
        } else {
            return tick.getLastTradedPrice(); // For local profile, use tick price as index
        }
    }
    
    /**
     * Check if we're running in live profile
     */
    private boolean isLiveProfile() {
        return ProfileUtil.isProfileActive(environment, "live");
    }
    
    /**
     * Get the active profile name for logging
     */
    private String getActiveProfile() {
        return isLiveProfile() ? "LIVE" : "LOCAL";
    }
    
    /**
     * Get the current option price for manual exit checks (non-tick based)
     */
    private Double getCurrentOptionPriceForManualCheck(Double currentPrice) {
        // For manual checks, use the provided price directly
        // This is used when we don't have a tick object
        return currentPrice;
    }

    /**
     * Get current price for a specific order (profile-aware) with current index price
     */
    private Double getCurrentPrice(JtradeOrder order, Double currentIndexPrice) {
        // Use centralized price service
        Double currentPrice = liveOptionPricingService.getCurrentPrice(order.getInstrumentToken());
        
        if (currentPrice != null) {
            // Real price from KiteConnect (live profile)
            log.info("🎯 USING REAL PRICE FROM KITE API - Token: {}, Symbol: {}, Price: {}", 
                    order.getInstrumentToken(), order.getTradingSymbol(), currentPrice);
            return currentPrice;
        } else {
            // Calculated price (local profile or fallback)
            // Use provided current index price if available, otherwise fallback to entry index price
            Double indexPriceForCalculation = currentIndexPrice != null ? currentIndexPrice : order.getEntryIndexPrice();
            Double calculatedPrice = calculateCurrentLTP(order, indexPriceForCalculation);
            log.info("📊 USING CALCULATED PRICE - Token: {}, Symbol: {}, Price: {}, Index: {}, Entry Index: {}", 
                    order.getInstrumentToken(), order.getTradingSymbol(), calculatedPrice, indexPriceForCalculation, order.getEntryIndexPrice());
            return calculatedPrice;
        }
    }
}
