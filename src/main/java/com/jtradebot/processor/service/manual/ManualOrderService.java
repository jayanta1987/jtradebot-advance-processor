package com.jtradebot.processor.service.manual;

import com.jtradebot.processor.common.CommonUtils;
import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.config.TradingConfigurationService;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.MilestoneSystem;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.repository.JtradeOrderRepository;
import com.jtradebot.processor.repository.document.JtradeOrder;
import com.jtradebot.processor.service.order.ActiveOrderTrackingService;
import com.jtradebot.processor.service.order.KiteOrderService;
import com.jtradebot.processor.service.notification.OrderNotificationService;
import com.jtradebot.processor.service.price.LiveOptionPricingService;
import com.jtradebot.processor.service.price.MockOptionPricingService;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.jtradebot.processor.handler.DateTimeHandler.formatDateToIST;
import static com.jtradebot.processor.handler.DateTimeHandler.getCurrentISTTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManualOrderService {

    private final LiveOptionPricingService liveOptionPricingService;
    private final MockOptionPricingService mockOptionPricingService;
    private final TradingConfigurationService tradingConfigService;
    private final KiteInstrumentHandler kiteInstrumentHandler;
    private final TickDataManager tickDataManager;
    private final ActiveOrderTrackingService activeOrderTrackingService;
    private final DynamicStrategyConfigService configService;
    private final OrderNotificationService orderNotificationService;
    private final JtradeOrderRepository jtradeOrderRepository;
    private final KiteOrderService kiteOrderService;

    /**
     * Place a manual order immediately, skipping all filters, NTP checks, and category scores
     */
    public JtradeOrder placeManualOrder(OrderTypeEnum orderType) throws KiteException {
        log.info("🎯 PLACING MANUAL ORDER - Type: {}", orderType);
        
        // Check if there's already an active order
        if (activeOrderTrackingService.hasActiveOrder()) {
            log.warn("⚠️ CANNOT PLACE MANUAL ORDER - Active order already exists");
            return null;
        }

        try {
            // Get current Nifty tick for pricing
            String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();
            Tick niftyTick = tickDataManager.getLastTick(niftyToken);

            if (niftyTick == null) {
                log.error("❌ No Nifty index data available for manual order creation");
                return null;
            }

            double currentIndexPrice = niftyTick.getLastTradedPrice();
            log.info("📊 Current Nifty Index Price: {}", currentIndexPrice);

            // Create order with manual entry decision
            JtradeOrder order = createManualTradeOrder(niftyTick, orderType, currentIndexPrice);
            
            if (order != null && order.getId() != null) {
                // Place the order through Kite
                kiteOrderService.placeOrder(order, Constants.TRANSACTION_TYPE_BUY);
                
                // Store in memory
                activeOrderTrackingService.setActiveOrderMap(order);
                activeOrderTrackingService.setNeedUpdateFlag(true);
                
                // Save to database
                updateOrdersToDatabase();
                
                log.info("✅ MANUAL ORDER PLACED SUCCESSFULLY - ID: {}, Type: {}, Symbol: {}, Price: {}", 
                        order.getId(), order.getOrderType(), order.getTradingSymbol(), order.getEntryPrice());
                
                // Send notification
                try {
                    orderNotificationService.sendOrderCreationNotification(order);
                } catch (Exception e) {
                    log.error("Failed to send order creation notification for manual order: {}", order.getId(), e);
                }
            }

            return order;

        } catch (Exception e) {
            log.error("❌ ERROR PLACING MANUAL ORDER: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Create a manual trade order with simplified logic (no filters, no category scores)
     */
    private JtradeOrder createManualTradeOrder(Tick tick, OrderTypeEnum orderType, double currentIndexPrice) throws KiteException {
        JtradeOrder order = new JtradeOrder();
        
        try {
            // Get option pricing
            Optional<LiveOptionPricingService.LiveOptionPricingInfo> liveOptionPricing = 
                    liveOptionPricingService.getLiveOptionPricing(orderType.name());

            double optionEntryPrice;
            String optionSymbol;
            long optionInstrumentToken;

            if (liveOptionPricing.isPresent()) {
                LiveOptionPricingService.LiveOptionPricingInfo pricingInfo = liveOptionPricing.get();
                optionEntryPrice = pricingInfo.getOptionLTP();
                optionSymbol = pricingInfo.getOptionInstrument().getTradingSymbol();
                optionInstrumentToken = pricingInfo.getOptionInstrument().getInstrumentToken();
                log.info("🎯 USING LIVE OPTION PRICING - Symbol: {}, LTP: {}, Strike: {}", 
                        optionSymbol, optionEntryPrice, pricingInfo.getStrikePrice());
            } else {
                optionEntryPrice = mockOptionPricingService.calculateEntryLTP(currentIndexPrice);
                optionSymbol = orderType == OrderTypeEnum.CALL_BUY ? "MANUAL_CALL_CE" : "MANUAL_PUT_PE";
                optionInstrumentToken = 0L;
                log.info("📊 USING PLACEHOLDER PRICING - Index: {}, Premium: {}", 
                        currentIndexPrice, optionEntryPrice);
            }

            // Calculate stop loss and target
            double stopLossPercentage = tradingConfigService.getStopLossPercentage();
            double stopLossPoints = optionEntryPrice * (stopLossPercentage / 100.0);
            
            // Use percentage-based target for manual orders (simpler)
            double targetPercentage = tradingConfigService.getTargetPercentage();
            double targetPoints = optionEntryPrice * (targetPercentage / 100.0);

            double stopLossPrice = Math.max(0.0, optionEntryPrice - stopLossPoints);
            double targetPrice = optionEntryPrice + targetPoints;

            // Set order details
            order.setId(UUID.randomUUID().toString());
            order.setOrderType(orderType);
            order.setTradingSymbol(optionSymbol);
            order.setInstrumentToken(optionInstrumentToken);
            order.setEntryPrice(optionEntryPrice);
            order.setEntryIndexPrice(currentIndexPrice);
            order.setStopLossPrice(stopLossPrice);
            order.setTargetPrice(targetPrice);
            order.setQuantity(tradingConfigService.getMinLotSize());
            order.setStatus("ACTIVE");
            order.setEntryTime(formatDateToIST(tick.getTickTimestamp()));
            order.setCreatedAt(getCurrentISTTime());
            order.setLastUpdated(getCurrentISTTime());

            // Set manual order specific details
            order.setEntryScenarioName("MANUAL_ORDER");
            order.setEntryScenarioDescription("Manual order placed by user, skipping all filters");
            order.setEntryScenarioConfidence(1.0); // Manual orders have 100% confidence
            order.setEntryMarketConditionSuitable(true); // Skip market condition checks
            order.setEntryQualityScore(10.0); // Manual orders bypass quality checks
            order.setEntryDominantTrend(orderType.name().replace("_BUY", ""));

            // Initialize milestone system
            initializeMilestoneSystem(order, tick);

            log.info("📝 MANUAL ORDER CREATED - {} {} @ {} (SL: {}, Target: {})",
                    orderType, optionSymbol, optionEntryPrice, stopLossPrice, targetPrice);

        } catch (Exception e) {
            log.error("Error creating manual trade order: {}", e.getMessage(), e);
            return null;
        }

        return order;
    }

    /**
     * Initialize milestone system for manual orders
     */
    private void initializeMilestoneSystem(JtradeOrder order, Tick tick) {
        try {
            // Get milestone configuration from strategy config
            double jsonMilestonePoints = order.getOrderType() == OrderTypeEnum.CALL_BUY ?
                    configService.getCallMilestonePoints() : configService.getPutMilestonePoints();
            
            double totalTargetPoints = order.getTargetPrice() - order.getEntryPrice();

            // Calculate dynamic milestone points using ATR values
            double dynamicMilestonePoints = CommonUtils.calculateDynamicMilestonePoints(tick, tickDataManager, jsonMilestonePoints);
            
            // Create target milestones
            List<MilestoneSystem.Milestone> targetMilestones = new ArrayList<>();
            List<String> milestoneHistory = new ArrayList<>();

            if (dynamicMilestonePoints > 0) {
                int milestoneCount = (int) Math.ceil(totalTargetPoints / dynamicMilestonePoints);
                for (int i = 1; i <= milestoneCount; i++) {
                    double points = Math.min(i * dynamicMilestonePoints, totalTargetPoints);
                    double targetPrice = order.getEntryPrice() + points;

                    MilestoneSystem.Milestone milestone = MilestoneSystem.Milestone.builder()
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

            log.info("🎯 Manual order milestone system initialized - Milestones: {}, Dynamic Step: {}", 
                    targetMilestones.size(), dynamicMilestonePoints);

        } catch (Exception e) {
            log.error("Error initializing milestone system for manual order: {}", order.getId(), e);
            order.setTargetMilestones(null);
            order.setMilestoneHistory(null);
        }
    }

    /**
     * Update orders to database
     */
    private void updateOrdersToDatabase() {
        if (!activeOrderTrackingService.getNeedUpdateFlag()) {
            return;
        }

        try {
            for (JtradeOrder order : activeOrderTrackingService.getActiveOrders()) {
                order.updateLastUpdated();
                jtradeOrderRepository.save(order);
            }
            activeOrderTrackingService.setNeedUpdateFlag(false);
            log.info("💾 Manual orders updated to database");
        } catch (Exception e) {
            log.error("Error updating manual orders to database: {}", e.getMessage(), e);
        }
    }

    /**
     * Get status of manual orders
     */
    public Map<String, Object> getManualOrdersStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            boolean hasActiveOrder = activeOrderTrackingService.hasActiveOrder();
            status.put("hasActiveOrder", hasActiveOrder);
            
            if (hasActiveOrder) {
                JtradeOrder activeOrder = activeOrderTrackingService.getActiveOrders().stream()
                        .filter(order -> "MANUAL_ORDER".equals(order.getEntryScenarioName()))
                        .findFirst()
                        .orElse(null);
                
                if (activeOrder != null) {
                    status.put("activeOrder", Map.of(
                        "id", activeOrder.getId(),
                        "orderType", activeOrder.getOrderType(),
                        "tradingSymbol", activeOrder.getTradingSymbol(),
                        "entryPrice", activeOrder.getEntryPrice(),
                        "stopLossPrice", activeOrder.getStopLossPrice(),
                        "targetPrice", activeOrder.getTargetPrice(),
                        "quantity", activeOrder.getQuantity(),
                        "status", activeOrder.getStatus(),
                        "entryTime", activeOrder.getEntryTime()
                    ));
                }
            }
            
            status.put("success", true);
            status.put("message", "Manual orders status retrieved successfully");
            
        } catch (Exception e) {
            log.error("Error getting manual orders status: {}", e.getMessage(), e);
            status.put("success", false);
            status.put("message", "Error getting manual orders status: " + e.getMessage());
        }
        
        return status;
    }
}
