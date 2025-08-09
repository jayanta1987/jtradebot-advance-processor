package com.jtradebot.processor.order;

import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.kafka.KafkaOrderProducer;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.*;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.jtradebot.processor.model.enums.EntryReason;
import com.jtradebot.processor.model.enums.TrendEnum;
import com.jtradebot.processor.repository.document.OrderInfo;
import com.jtradebot.processor.repository.document.TradeOrder;
import com.jtradebot.processor.rule.EntryRuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Set;

@Slf4j
@Service
public class PutEntryService extends BaseEntryService {

    public PutEntryService(TickDataManager tickDataManager,
                           EntryRuleService entryRuleService, KafkaOrderProducer kafkaOrderProducer,
                           com.jtradebot.processor.handler.KiteInstrumentHandler kiteInstrumentHandler) {
        super(tickDataManager, entryRuleService, kafkaOrderProducer, kiteInstrumentHandler);
    }

    @Override
    protected boolean isValidData(OrderInfo orderInfo) {
        PutData putData = orderInfo.getCpDetails().getPutData();
        return putData.isValidPut();
    }

    @Override
    protected boolean isOppositeScoreRising(CpDetails cpDetails) {
        CallData callData = cpDetails.getCallData();
        return callData.getAvgScore() > 6;
    }

    @Override
    protected void setStopLossTarget(CpDetails cpDetails, TradeOrder tradeOrder, EntryReason entryReason) {
        CandleTimeFrameEnum candleTimeFrame;
        if (entryReason == EntryReason.SCALPING_LONG_CP_MOVEMENT) {
            candleTimeFrame = CandleTimeFrameEnum.FIFTEEN_MIN;
        } else {
            candleTimeFrame = CandleTimeFrameEnum.FIVE_MIN;
        }
        PutData putData = cpDetails.getPutData();
        Set<Support> supportSet = putData.getSupportMap().get(candleTimeFrame);
        Set<Resistance> resistanceSet = putData.getResistanceMap().get(candleTimeFrame);
        
        // Enhanced target calculation with CP-based adjustments
        int maxSupportValue = supportSet.stream()
                .max(Comparator.comparingInt(Support::getSupportValue))
                .map(Support::getSupportValue)
                .orElse((int) tradeOrder.getEntryIndexPrice());

        int minResistanceValue = resistanceSet.stream()
                .min(Comparator.comparingInt(Resistance::getResistanceValue))
                .map(Resistance::getResistanceValue)
                .orElse((int) tradeOrder.getEntryIndexPrice());

        // Check for breakout scenario (no clear support levels)
        boolean isBreakoutScenario = isPutBreakoutScenario(cpDetails, maxSupportValue, tradeOrder.getEntryIndexPrice());
        
        if (isBreakoutScenario) {
            // Use breakout strategy for PUT
            handlePutBreakoutScenario(tradeOrder, cpDetails);
        } else {
            // CP-based target adjustment
            double cpAbs = cpDetails.getCpAbs();
            double cpDelta = cpDetails.getCpDelta();
            
            // Adjust target based on CP strength and momentum
            int adjustedTarget = calculateCpAdjustedTarget(maxSupportValue, tradeOrder.getEntryIndexPrice(), cpAbs, cpDelta, false);
            
            tradeOrder.setIndexStopLoss(minResistanceValue);
            tradeOrder.setIndexTarget(adjustedTarget);
        }
    }
    
    /**
     * Detects breakout scenario for PUT orders
     */
    private boolean isPutBreakoutScenario(CpDetails cpDetails, int supportValue, double entryPrice) {
        // Check if support is very close to entry (indicating no clear support)
        double supportDistance = Math.abs(supportValue - entryPrice);
        boolean noClearSupport = supportDistance < 3.0;
        
        // Check for strong downward momentum
        double cpDelta = cpDetails.getCpDelta();
        boolean strongDownwardMomentum = cpDelta < -2.0;
        
        // Check for strong CP values
        double cpAbs = cpDetails.getCpAbs();
        boolean strongCP = cpAbs > 7.0;
        
        // Check future signal alignment
        boolean futureSignalAligned = cpDetails.isFutureSignal() && cpDetails.getFutureTrend() == TrendEnum.DOWN;
        
        return noClearSupport && (strongDownwardMomentum || strongCP || futureSignalAligned);
    }
    
    /**
     * Handles breakout scenario for PUT orders
     */
    private void handlePutBreakoutScenario(TradeOrder tradeOrder, CpDetails cpDetails) {
        double entryPrice = tradeOrder.getEntryIndexPrice();
        double cpAbs = cpDetails.getCpAbs();
        double cpDelta = cpDetails.getCpDelta();
        
        // Calculate breakout targets
        double breakoutDistance = calculatePutBreakoutDistance(cpAbs, cpDelta);
        
        // Set aggressive downward targets
        int breakoutTarget = (int) (entryPrice - breakoutDistance);
        int breakoutTarget2 = (int) (entryPrice - (breakoutDistance * 1.5));
        int breakoutTarget3 = (int) (entryPrice - (breakoutDistance * 2.0));
        
        tradeOrder.setIndexTarget(breakoutTarget);
        tradeOrder.setIndexTarget2(breakoutTarget2);
        tradeOrder.setIndexTarget3(breakoutTarget3);
        
        // Set tight stop loss above entry
        int breakoutStopLoss = (int) (entryPrice + Math.min(breakoutDistance * 0.3, 8.0));
        tradeOrder.setIndexStopLoss(breakoutStopLoss);
        
        String breakoutComment = String.format(
                "PUT BREAKOUT: CP=%.2f, Delta=%.2f, Target Distance=%.1f points",
                cpAbs, cpDelta, breakoutDistance
        );
        tradeOrder.addComment(breakoutComment);
        
        log.info("PUT Breakout strategy applied: Entry={}, Target={}, StopLoss={}", 
                entryPrice, breakoutTarget, breakoutStopLoss);
    }
    
    /**
     * Calculates breakout distance for PUT orders
     */
    private double calculatePutBreakoutDistance(double cpAbs, double cpDelta) {
        double baseDistance = 20.0;
        
        // Adjust based on CP strength
        if (cpAbs > 9.0) {
            baseDistance += 15.0; // Very strong CP
        } else if (cpAbs > 7.0) {
            baseDistance += 10.0; // Strong CP
        } else if (cpAbs < 5.0) {
            baseDistance -= 5.0; // Weak CP
        }
        
        // Adjust based on downward momentum
        if (cpDelta < -3.0) {
            baseDistance += 10.0; // High downward momentum
        } else if (cpDelta < -2.0) {
            baseDistance += 5.0; // Moderate downward momentum
        } else if (cpDelta > -0.5) {
            baseDistance -= 5.0; // Low momentum
        }
        
        return Math.max(baseDistance, 12.0);
    }
    
    /**
     * Calculates CP-adjusted target for PUT orders
     */
    private int calculateCpAdjustedTarget(int baseTarget, double entryPrice, double cpAbs, double cpDelta, boolean isCall) {
        double targetDistance = Math.abs(baseTarget - entryPrice);
        
        // Adjust based on CP strength
        if (cpAbs > 8.0) {
            targetDistance *= 1.2; // Strong CP - increase target distance
        } else if (cpAbs > 6.0) {
            targetDistance *= 1.1; // Moderate CP
        } else if (cpAbs < 5.0) {
            targetDistance *= 0.9; // Weak CP - reduce target distance
        }
        
        // Adjust based on CP momentum
        if (Math.abs(cpDelta) > 2.0) {
            targetDistance *= 1.15; // High momentum - increase target
        } else if (Math.abs(cpDelta) < 0.5) {
            targetDistance *= 0.95; // Low momentum - reduce target
        }
        
        // Ensure minimum and maximum distances
        targetDistance = Math.max(targetDistance, 8.0); // Minimum 8 points
        targetDistance = Math.min(targetDistance, 40.0); // Maximum 40 points
        
        // Calculate final target
        if (isCall) {
            return (int) (entryPrice + targetDistance);
        } else {
            return (int) (entryPrice - targetDistance);
        }
    }

}
