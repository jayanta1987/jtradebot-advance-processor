package com.jtradebot.processor.order;

import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.kafka.KafkaOrderProducer;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.CpDetails;
import com.jtradebot.processor.model.enums.EntryReason;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.model.enums.TradeMode;
import com.jtradebot.processor.model.enums.TrendEnum;
import com.jtradebot.processor.model.event.OrderEntryEvent;
import com.jtradebot.processor.repository.document.EntryRule;
import com.jtradebot.processor.repository.document.OrderInfo;
import com.jtradebot.processor.repository.document.TradeOrder;
import com.jtradebot.processor.rule.EntryRuleService;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public abstract class BaseEntryService {

    private static final double MIN_TARGET_DISTANCE = 5.0; // Minimum distance between entry price and target
    private static final double MIN_TARGET_DISTANCE_MANUAL = 10.0; // Higher threshold for manual entries
    private static final double MAX_TARGET_DISTANCE = 50.0; // Maximum reasonable target distance
    private static final double CP_BASED_TARGET_MULTIPLIER = 2.0; // CP-based target multiplier
    protected final TickDataManager tickDataManager;
    protected final EntryRuleService entryRuleService;
    private final KafkaOrderProducer kafkaOrderProducer;
    private final com.jtradebot.processor.handler.KiteInstrumentHandler kiteInstrumentHandler;

    public void createOrder(OrderInfo orderInfo, TradeMode tradeMode) {

        OrderTypeEnum orderType = orderInfo.getCpDetails().getOrderType();
        log.info("<<<<<<<<<<<<<<<<<{} Entry initiated>>>>>>>>>>>>>>>>>>>", orderType);

        CpDetails cpDetails = orderInfo.getCpDetails();
        boolean isOppositeScoreRising = isOppositeScoreRising(cpDetails);

        if (isOppositeScoreRising) {
            log.error("Opposite score is rising for {}. Skipping order creation.", orderType);
            if (orderInfo.getEntryReason() == com.jtradebot.processor.model.enums.EntryReason.MANUAL_ENTRY) {
                throw new ManualEntryRejectedException("Opposite score is rising for manual entry.");
            }
            return;
        }

        if (!isValidData(orderInfo) && !cpDetails.isHighCP()) {
            log.error("Invalid data for {}. Skipping order creation.", orderType);
            if (orderInfo.getEntryReason() == com.jtradebot.processor.model.enums.EntryReason.MANUAL_ENTRY) {
                throw new ManualEntryRejectedException("Invalid data for manual entry.");
            }
            return;
        }

        EntryRule entryRule = entryRuleService.getRule(orderInfo.getEntryReason());

        TradeOrder tradeOrder = new TradeOrder();
        tradeOrder.setOrderType(orderType);
        tradeOrder.setStatus("OPEN");
        Tick lastTick = tickDataManager.getLastTick(kiteInstrumentHandler.getNifty50Token().toString());
        tradeOrder.setNtp(cpDetails.getNtp());
        tradeOrder.setEntryIndexPrice(lastTick.getLastTradedPrice());
        tradeOrder.setStartTime(lastTick.getTickTimestamp());
        tradeOrder.setMinIndexPrice(lastTick.getLastTradedPrice());
        tradeOrder.setMaxIndexPrice(lastTick.getLastTradedPrice());
        tradeOrder.setEntryRule(entryRule);
        tradeOrder.setTradeMode(tradeMode);
        tradeOrder.setMaxAllowedQuantity(entryRule.getMaxQty());
        setStopLossTarget(cpDetails, tradeOrder, entryRule.getEntryReason());
        tradeOrder.setOrderInfo(orderInfo);

        if (tradeOrder.getIndexTarget() == Integer.MAX_VALUE || tradeOrder.getIndexStopLoss() == Integer.MIN_VALUE) {
            log.error("Invalid target or stop-loss for {}. Skipping order creation.", orderType);
            if (orderInfo.getEntryReason() == com.jtradebot.processor.model.enums.EntryReason.MANUAL_ENTRY) {
                throw new ManualEntryRejectedException("Invalid target or stop-loss for manual entry.");
            }
            return;
        }
        // Enhanced target distance validation with dynamic adjustment
        double targetDistance = Math.abs(tradeOrder.getIndexTarget() - tradeOrder.getEntryIndexPrice());
        double minRequiredDistance = getMinRequiredDistance(orderInfo, cpDetails);
        
        // Check for breakout scenario (no clear support/resistance)
        boolean isBreakoutScenario = isBreakoutScenario(tradeOrder, cpDetails);
        
        if (targetDistance < minRequiredDistance) {
            String comment = String.format(
                    "Target %s is too close to entry price %s (distance: %.2f, required: %.2f) for %s. %s",
                    tradeOrder.getIndexTarget(), tradeOrder.getEntryIndexPrice(), targetDistance, minRequiredDistance, orderType,
                    isBreakoutScenario ? "BREAKOUT DETECTED - Using breakout strategy." : "Adjusting target."
            );
            log.warn(comment);

            if (isBreakoutScenario) {
                // Use breakout strategy instead of simple adjustment
                handleBreakoutScenario(tradeOrder, cpDetails, orderType, orderInfo);
            } else {
                // Dynamic target adjustment based on market conditions
                double adjustedTarget = calculateDynamicTarget(tradeOrder, cpDetails, orderType);
                
                if (adjustedTarget != tradeOrder.getIndexTarget()) {
                    tradeOrder.setIndexTarget((int) adjustedTarget);
                    tradeOrder.setIndexTarget2((int) (adjustedTarget + getTargetIncrement(orderType, cpDetails)));
                    tradeOrder.setIndexTarget3((int) (adjustedTarget + (2 * getTargetIncrement(orderType, cpDetails))));
                    tradeOrder.addComment(comment + " Adjusted to: " + adjustedTarget);
                }
            }
            
            // For manual entries, use stricter validation
            if (orderInfo.getEntryReason() == com.jtradebot.processor.model.enums.EntryReason.MANUAL_ENTRY) {
                double newDistance = Math.abs(tradeOrder.getIndexTarget() - tradeOrder.getEntryIndexPrice());
                if (newDistance < MIN_TARGET_DISTANCE_MANUAL) {
                    throw new ManualEntryRejectedException("Target is too close to entry price for manual entry even after adjustment.");
                }
            }
        }

        OrderEntryEvent orderEntryRequest = mapToOrderEntryRequest(tradeOrder, lastTick);
        kafkaOrderProducer.triggerOrder(orderEntryRequest);

    }

    private OrderEntryEvent mapToOrderEntryRequest(TradeOrder tradeOrder, Tick tick) {

        OrderEntryEvent orderEntryRequest = new OrderEntryEvent();
        orderEntryRequest.setRefOrderId(UUID.randomUUID().toString());
        orderEntryRequest.setEventTimeStamp(new Date());
        orderEntryRequest.setOrderType(tradeOrder.getOrderType());
        orderEntryRequest.setTradeMode(tradeOrder.getTradeMode());
        orderEntryRequest.setEntryRule(tradeOrder.getEntryRule());
        orderEntryRequest.setTickTimeStamp(tick.getTickTimestamp());
        orderEntryRequest.setIndexStopLoss(tradeOrder.getIndexStopLoss());
        orderEntryRequest.setIndexTarget(tradeOrder.getIndexTarget());
        orderEntryRequest.setIndexTarget2(tradeOrder.getIndexTarget2());
        orderEntryRequest.setIndexTarget3(tradeOrder.getIndexTarget3());
        orderEntryRequest.setEntryIndexPrice(tradeOrder.getEntryIndexPrice());
        orderEntryRequest.setNtp(tradeOrder.getNtp());
        orderEntryRequest.setMaxAllowedQuantity(tradeOrder.getMaxAllowedQuantity());
        orderEntryRequest.setOneMinATR(5); // NEED TO CHANGE
        orderEntryRequest.setMaxProfitReached(false); // NEED TO CHANGE

        return orderEntryRequest;
    }

    protected abstract boolean isValidData(OrderInfo orderInfo);

    protected abstract boolean isOppositeScoreRising(CpDetails cpDetails);

    protected abstract void setStopLossTarget(CpDetails cpDetails, TradeOrder tradeOrder, EntryReason entryReason);

    /**
     * Calculates minimum required distance based on entry reason and CP conditions
     */
    private double getMinRequiredDistance(OrderInfo orderInfo, CpDetails cpDetails) {
        // Higher threshold for manual entries
        if (orderInfo.getEntryReason() == EntryReason.MANUAL_ENTRY) {
            return MIN_TARGET_DISTANCE_MANUAL;
        }
        
        // Base distance
        double baseDistance = MIN_TARGET_DISTANCE;
        
        // Adjust based on CP strength
        double cpAbs = cpDetails.getCpAbs();
        if (cpAbs > 8.0) {
            baseDistance += 5.0; // Strong CP requires more distance
        } else if (cpAbs > 6.0) {
            baseDistance += 2.0; // Moderate CP
        }
        
        // Adjust based on CP delta (momentum)
        double cpDelta = cpDetails.getCpDelta();
        if (Math.abs(cpDelta) > 2.0) {
            baseDistance += 3.0; // High momentum requires more distance
        }
        
        return baseDistance;
    }
    
    /**
     * Calculates dynamic target based on market conditions and CP values
     */
    private double calculateDynamicTarget(TradeOrder tradeOrder, CpDetails cpDetails, OrderTypeEnum orderType) {
        double entryPrice = tradeOrder.getEntryIndexPrice();
        double currentTarget = tradeOrder.getIndexTarget();
        double cpAbs = cpDetails.getCpAbs();
        double cpDelta = cpDetails.getCpDelta();
        
        // Calculate target increment based on CP strength and momentum
        double targetIncrement = getTargetIncrement(orderType, cpDetails);
        
        // Determine direction
        double direction = (orderType == OrderTypeEnum.CALL_BUY) ? 1.0 : -1.0;
        
        // Calculate new target
        double newTarget = entryPrice + (direction * targetIncrement);
        
        // Ensure target is reasonable (not too far)
        double maxDistance = Math.min(MAX_TARGET_DISTANCE, cpAbs * CP_BASED_TARGET_MULTIPLIER);
        double minDistance = getMinRequiredDistance(tradeOrder.getOrderInfo(), cpDetails);
        
        // Clamp target to reasonable range
        if (orderType == OrderTypeEnum.CALL_BUY) {
            newTarget = Math.min(newTarget, entryPrice + maxDistance);
            newTarget = Math.max(newTarget, entryPrice + minDistance);
        } else {
            newTarget = Math.max(newTarget, entryPrice - maxDistance);
            newTarget = Math.min(newTarget, entryPrice - minDistance);
        }
        
        return newTarget;
    }
    
    /**
     * Calculates target increment based on order type and CP conditions
     */
    private double getTargetIncrement(OrderTypeEnum orderType, CpDetails cpDetails) {
        double baseIncrement = 15.0; // Base increment
        
        // Adjust based on CP strength
        double cpAbs = cpDetails.getCpAbs();
        if (cpAbs > 9.0) {
            baseIncrement += 10.0; // Very strong CP
        } else if (cpAbs > 7.0) {
            baseIncrement += 5.0; // Strong CP
        } else if (cpAbs < 5.0) {
            baseIncrement -= 5.0; // Weak CP
        }
        
        // Adjust based on CP momentum
        double cpDelta = cpDetails.getCpDelta();
        if (Math.abs(cpDelta) > 3.0) {
            baseIncrement += 5.0; // High momentum
        } else if (Math.abs(cpDelta) < 0.5) {
            baseIncrement -= 3.0; // Low momentum
        }
        
        // Ensure minimum increment
        return Math.max(baseIncrement, 8.0);
    }
    
    /**
     * Detects if current scenario is a breakout (no clear support/resistance levels)
     */
    private boolean isBreakoutScenario(TradeOrder tradeOrder, CpDetails cpDetails) {
        double entryPrice = tradeOrder.getEntryIndexPrice();
        double targetPrice = tradeOrder.getIndexTarget();
        double stopLossPrice = tradeOrder.getIndexStopLoss();
        
        // Check if target and stop loss are very close to entry price (indicating no clear levels)
        double targetDistance = Math.abs(targetPrice - entryPrice);
        double stopLossDistance = Math.abs(stopLossPrice - entryPrice);
        
        // Breakout indicators:
        // 1. Target and stop loss are very close to entry price
        boolean noClearLevels = targetDistance < 3.0 && stopLossDistance < 3.0;
        
        // 2. Strong CP momentum (breakout confirmation)
        double cpDelta = cpDetails.getCpDelta();
        boolean strongMomentum = Math.abs(cpDelta) > 2.0;
        
        // 3. High CP values (strong directional bias)
        double cpAbs = cpDetails.getCpAbs();
        boolean strongCP = cpAbs > 7.0;
        
        // 4. Future signal alignment
        boolean futureSignalAligned = cpDetails.isFutureSignal() && 
                                    ((tradeOrder.getOrderType() == OrderTypeEnum.CALL_BUY && cpDetails.getFutureTrend() == TrendEnum.UP) ||
                                     (tradeOrder.getOrderType() == OrderTypeEnum.PUT_BUY && cpDetails.getFutureTrend() == TrendEnum.DOWN));
        
        return noClearLevels && (strongMomentum || strongCP || futureSignalAligned);
    }
    
    /**
     * Handles breakout scenario with aggressive target setting
     */
    private void handleBreakoutScenario(TradeOrder tradeOrder, CpDetails cpDetails, OrderTypeEnum orderType, OrderInfo orderInfo) {
        double entryPrice = tradeOrder.getEntryIndexPrice();
        double cpAbs = cpDetails.getCpAbs();
        double cpDelta = cpDetails.getCpDelta();
        
        // Calculate breakout targets based on CP strength and momentum
        double breakoutTargetDistance = calculateBreakoutTargetDistance(cpAbs, cpDelta, orderType);
        
        // Set aggressive targets for breakout
        if (orderType == OrderTypeEnum.CALL_BUY) {
            int breakoutTarget = (int) (entryPrice + breakoutTargetDistance);
            int breakoutTarget2 = (int) (entryPrice + (breakoutTargetDistance * 1.5));
            int breakoutTarget3 = (int) (entryPrice + (breakoutTargetDistance * 2.0));
            
            tradeOrder.setIndexTarget(breakoutTarget);
            tradeOrder.setIndexTarget2(breakoutTarget2);
            tradeOrder.setIndexTarget3(breakoutTarget3);
            
            // Set tight stop loss for breakout
            int breakoutStopLoss = (int) (entryPrice - Math.min(breakoutTargetDistance * 0.3, 8.0));
            tradeOrder.setIndexStopLoss(breakoutStopLoss);
            
        } else if (orderType == OrderTypeEnum.PUT_BUY) {
            int breakoutTarget = (int) (entryPrice - breakoutTargetDistance);
            int breakoutTarget2 = (int) (entryPrice - (breakoutTargetDistance * 1.5));
            int breakoutTarget3 = (int) (entryPrice - (breakoutTargetDistance * 2.0));
            
            tradeOrder.setIndexTarget(breakoutTarget);
            tradeOrder.setIndexTarget2(breakoutTarget2);
            tradeOrder.setIndexTarget3(breakoutTarget3);
            
            // Set tight stop loss for breakout
            int breakoutStopLoss = (int) (entryPrice + Math.min(breakoutTargetDistance * 0.3, 8.0));
            tradeOrder.setIndexStopLoss(breakoutStopLoss);
        }
        
        String breakoutComment = String.format(
                "BREAKOUT STRATEGY: CP=%.2f, Delta=%.2f, Target Distance=%.1f points",
                cpAbs, cpDelta, breakoutTargetDistance
        );
        tradeOrder.addComment(breakoutComment);
        
        log.info("Breakout strategy applied for {}: Entry={}, Target={}, StopLoss={}", 
                orderType, entryPrice, tradeOrder.getIndexTarget(), tradeOrder.getIndexStopLoss());
    }
    
    /**
     * Calculates target distance for breakout scenarios
     */
    private double calculateBreakoutTargetDistance(double cpAbs, double cpDelta, OrderTypeEnum orderType) {
        double baseDistance = 20.0; // Base breakout distance
        
        // Adjust based on CP strength
        if (cpAbs > 9.0) {
            baseDistance += 15.0; // Very strong CP - aggressive breakout
        } else if (cpAbs > 7.0) {
            baseDistance += 10.0; // Strong CP
        } else if (cpAbs < 5.0) {
            baseDistance -= 5.0; // Weak CP - conservative breakout
        }
        
        // Adjust based on CP momentum
        if (Math.abs(cpDelta) > 3.0) {
            baseDistance += 10.0; // High momentum - aggressive breakout
        } else if (Math.abs(cpDelta) > 2.0) {
            baseDistance += 5.0; // Moderate momentum
        } else if (Math.abs(cpDelta) < 0.5) {
            baseDistance -= 5.0; // Low momentum - conservative breakout
        }
        
        // Ensure reasonable range
        return Math.max(baseDistance, 12.0); // Minimum 12 points for breakout
    }
}
