package com.jtradebot.processor.service.impl;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.config.TradingConfigurationService;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.model.enums.ExitReasonEnum;
import com.jtradebot.processor.model.MilestoneSystem;
import com.jtradebot.processor.repository.JtradeOrderRepository;
import com.jtradebot.processor.repository.document.JtradeOrder;
import com.jtradebot.processor.service.ExitStrategyService;
import com.jtradebot.processor.service.OptionPricingService;
import com.jtradebot.processor.service.ScalpingVolumeSurgeService;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class ExitStrategyServiceImpl implements ExitStrategyService {
    
    private final JtradeOrderRepository jtradeOrderRepository;
    private final OptionPricingService optionPricingService;
    private final DynamicStrategyConfigService configService;
    private final TradingConfigurationService tradingConfigurationService;
    private final ScalpingVolumeSurgeService scalpingVolumeSurgeService;
    
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
    
    @Override
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
    
    @Override
    public JtradeOrder createOrderEntryWithScenario(OrderTypeEnum orderType, String tradingSymbol, Long instrumentToken,
                                                   Double entryPrice, Double entryIndexPrice, Double stopLossPrice,
                                                   Double targetPrice, Integer quantity, String scenarioName,
                                                   String scenarioDescription, Double scenarioConfidence,
                                                   Map<String, Integer> categoryScores,
                                                   Map<String, List<String>> matchedConditions, Date entryTime) {
        
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
        
        // Initialize milestone system
        initializeMilestoneSystem(order);
        
        // Store in memory
        activeOrdersMap.put(order.getId(), order);
        needsUpdate.set(true);
        
        log.info("Created new order entry with scenario: {} - {} @ {} (SL: {}, Target: {}) - Scenario: {} (Confidence: {})", 
                orderType, tradingSymbol, entryPrice, stopLossPrice, targetPrice, scenarioName, scenarioConfidence);
        
        return order;
    }
    
    @Override
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
    
    @Override
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
    
    @Override
    public List<JtradeOrder> getActiveOrders() {
        return new ArrayList<>(activeOrdersMap.values());
    }
    
    @Override
    public List<JtradeOrder> getActiveOrdersByType(OrderTypeEnum orderType) {
        return activeOrdersMap.values().stream()
                .filter(order -> order.getOrderType() == orderType)
                .toList();
    }
    
    @Override
    public void checkAndProcessExits(Double currentPrice, Double currentIndexPrice) {
        if (activeOrdersMap.isEmpty()) {
            return;
        }
        
        // Log current profit/loss for all active orders
        logCurrentProfitLoss(currentIndexPrice);
        
        List<JtradeOrder> ordersToExit = new ArrayList<>();
        
        for (JtradeOrder order : activeOrdersMap.values()) {
            // Calculate current LTP based on index movement
            Double currentLTP = calculateCurrentLTP(order, currentIndexPrice);
            
            // Check all exit conditions
            if (shouldExitOrder(order, currentLTP, currentIndexPrice)) {
                ordersToExit.add(order);
            }
        }
        
        // Process exits
        for (JtradeOrder order : ordersToExit) {
            Double currentLTP = calculateCurrentLTP(order, currentIndexPrice);
            ExitReasonEnum exitReason = determineExitReason(order, currentLTP, currentIndexPrice);
            exitOrder(order.getId(), exitReason, currentLTP, currentIndexPrice, new Date()); // Use current time for non-tick exits
        }
    }
    
    @Override
    public void checkAndProcessExitsWithStrategy(Tick tick) {
        if (activeOrdersMap.isEmpty()) {
            return;
        }
        
        Double currentPrice = tick.getLastTradedPrice();
        Double currentIndexPrice = tick.getLastTradedPrice(); // For now using same price
        
        // Log current profit/loss for all active orders
        logCurrentProfitLoss(currentIndexPrice);
        
        List<JtradeOrder> ordersToExit = new ArrayList<>();
        
        for (JtradeOrder order : activeOrdersMap.values()) {
            // Calculate current LTP based on index movement
            Double currentLTP = calculateCurrentLTP(order, currentIndexPrice);
            
            // Log that we're checking exits for this order
            log.info("🔍 Checking exits for order: {} - Entry time: {}", order.getId(), order.getEntryTime());
            
            // Check all exit conditions including time-based and strategy-based
            if (shouldExitOrder(order, currentLTP, currentIndexPrice) || 
                shouldExitBasedOnTime(order, tick.getTickTimestamp()) || 
                shouldExitBasedOnStrategy(order, tick)) {
                ordersToExit.add(order);
            }
        }
        
        // Process exits
        for (JtradeOrder order : ordersToExit) {
            Double currentLTP = calculateCurrentLTP(order, currentIndexPrice);
            ExitReasonEnum exitReason = determineEnhancedExitReason(order, currentLTP, currentIndexPrice, tick);
            exitOrder(order.getId(), exitReason, currentLTP, currentIndexPrice, tick.getTickTimestamp()); // Use tick timestamp for accurate backtesting
        }
    }
    
    /**
     * Calculate current LTP based on index movement
     * LTP = Entry Price + (Current Index Price - Entry Index Price)
     */
    private Double calculateCurrentLTP(JtradeOrder order, Double currentIndexPrice) {
        return optionPricingService.calculateCurrentLTP(
            order.getEntryPrice(), 
            order.getEntryIndexPrice(), 
            currentIndexPrice,
            order.getOrderType()
        );
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
    
    @Override
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
    
    @Override
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
        // Check if milestone system is enabled
        if (order.getMilestoneSystem() != null && order.getMilestoneSystem().isEnabled()) {
            return shouldExitOrderWithMilestones(order, currentLTP, currentIndexPrice);
        } else {
            return shouldExitOrderTraditional(order, currentLTP, currentIndexPrice);
        }
    }
    
    private boolean shouldExitOrderWithMilestones(JtradeOrder order, Double currentLTP, Double currentIndexPrice) {
        MilestoneSystem milestoneSystem = order.getMilestoneSystem();
        MilestoneSystem.MilestoneResult result = milestoneSystem.processPrice(currentLTP, currentIndexPrice);
        
        if (result.isExitRequired()) {
            // Update order with milestone information (only in milestoneSystem object)
            // Don't duplicate data at root level - keep it only in milestoneSystem
            order.setMilestoneSystem(milestoneSystem);
            
            // Log milestone exit
            log.info("🎯 Milestone Exit - {} {} @ {} | Reason: {}, Profit: {}, Milestone: {}", 
                    order.getOrderType(), order.getTradingSymbol(), currentLTP,
                    result.getExitReason(), result.getProfit(), result.getMilestoneNumber());
            
            return true;
        }
        
        // Update order with current milestone status (only in milestoneSystem object)
        order.setMilestoneSystem(milestoneSystem);
        
        return false;
    }
    
    private boolean shouldExitOrderTraditional(JtradeOrder order, Double currentLTP, Double currentIndexPrice) {
        if (order.getStopLossPrice() != null && order.getTargetPrice() != null) {
            // Debug logging to understand exit conditions
            log.debug("Exit Check - Order: {}, Current LTP: {}, Stop Loss: {}, Target: {}, Entry: {}", 
                    order.getId(), currentLTP, order.getStopLossPrice(), order.getTargetPrice(), order.getEntryPrice());
            
            // For both CALL and PUT orders, stop loss is hit when current price <= stop loss price
            // When option price goes down, it's a loss for both CALL and PUT
            if (currentLTP <= order.getStopLossPrice()) {
                log.info("🛑 Stop Loss Hit - Current: {}, Stop Loss: {}", currentLTP, order.getStopLossPrice());
                return true;
            }
            
            // For both CALL and PUT orders, target is hit when current price >= target price
            // When option price goes up, it's profitable for both CALL and PUT
            if (currentLTP >= order.getTargetPrice()) {
                log.info("🎯 Target Hit - Current: {}, Target: {}", currentLTP, order.getTargetPrice());
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Initialize milestone system for a new order
     */
    private void initializeMilestoneSystem(JtradeOrder order) {
        try {
            // Get milestone configuration from strategy config
            double milestonePoints = order.getOrderType() == OrderTypeEnum.CALL_BUY ? 
                configService.getCallMilestonePoints() : configService.getPutMilestonePoints();
            double maxStopLossPoints = order.getOrderType() == OrderTypeEnum.CALL_BUY ? 
                configService.getCallMaxStopLossPoints() : configService.getPutMaxStopLossPoints();
            double totalTargetPoints = order.getOrderType() == OrderTypeEnum.CALL_BUY ?
                configService.getCallTargetPoints() : configService.getPutTargetPoints();
            
            // Create milestone system
            MilestoneSystem milestoneSystem = MilestoneSystem.builder()
                    .enabled(true)
                    .milestonePoints(milestonePoints)
                    .maxStopLossPoints(maxStopLossPoints)
                    .totalTargetPoints(totalTargetPoints)
                    .build();
            
            // Initialize the milestone system
            milestoneSystem.initialize(order.getEntryPrice(), order.getEntryIndexPrice(), order.getOrderType());
            
            // Set milestone system in order (only in milestoneSystem object)
            order.setMilestoneSystem(milestoneSystem);
            // Don't duplicate data at root level - keep it only in milestoneSystem
            
            log.info("🎯 Milestone system initialized for {} order - Milestone step: {}, Max SL: {}, Total Target: {}", 
                    order.getOrderType(), milestonePoints, maxStopLossPoints, totalTargetPoints);
            
        } catch (Exception e) {
            log.error("Error initializing milestone system for order: {}", order.getId(), e);
            // Fallback to traditional system
            order.setMilestoneSystem(null);
        }
    }
    
    private ExitReasonEnum determineExitReason(JtradeOrder order, Double currentLTP, Double currentIndexPrice) {
        if (order.getStopLossPrice() != null && order.getTargetPrice() != null) {
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
        }
        
        return ExitReasonEnum.FORCE_EXIT;
    }
    
    /**
     * Enhanced exit reason determination including time-based and strategy-based exits
     */
    private ExitReasonEnum determineEnhancedExitReason(JtradeOrder order, Double currentLTP, Double currentIndexPrice, Tick tick) {
        // Check time-based exit first
        if (shouldExitBasedOnTime(order, tick.getTickTimestamp())) {
            return ExitReasonEnum.TIME_BASED_EXIT;
        }
        
        // Check strategy-based exit
        if (shouldExitBasedOnStrategy(order, tick)) {
            return ExitReasonEnum.EXIT_SIGNAL;
        }
        
        // Check traditional stop loss and target
        return determineExitReason(order, currentLTP, currentIndexPrice);
    }
    
    /**
     * Check if order should be exited based on time limit
     */
    private boolean shouldExitBasedOnTime(JtradeOrder order, Date currentTime) {
        if (order.getEntryTime() == null) {
            log.warn("Entry time is null for order: {}", order.getId());
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
                log.info("⏰ Time-based exit triggered for order: {} - Duration: {} seconds (Max: {} seconds)", 
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
    
    @Override
    public boolean hasActiveOrder() {
        return !activeOrdersMap.isEmpty();
    }
    
    @Override
    public void logCurrentProfitLoss(Double currentIndexPrice) {
        if (activeOrdersMap.isEmpty()) {
            return;
        }
        
        for (JtradeOrder order : activeOrdersMap.values()) {
            Double currentLTP = calculateCurrentLTP(order, currentIndexPrice);
            Double profitLoss = optionPricingService.calculateProfitLoss(
                order.getEntryPrice(), 
                currentLTP, 
                order.getOrderType()
            );
            
            Double totalProfitLoss = profitLoss * order.getQuantity();
            
            log.info("LIVE P&L - {} {}: Entry={}, Current LTP={}, P&L={}, Total P&L={}, Index={}", 
                order.getOrderType(), 
                order.getTradingSymbol(),
                order.getEntryPrice(),
                currentLTP,
                profitLoss,
                totalProfitLoss,
                currentIndexPrice);
        }
    }
}
