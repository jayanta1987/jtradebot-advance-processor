package com.jtradebot.processor.service.order;


import com.jtradebot.processor.config.TradingConfigurationService;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.strategy.ScalpingEntryDecision;
import com.jtradebot.processor.repository.document.JtradeOrder;
import com.jtradebot.processor.service.EntryConditionAnalysisService;

import com.jtradebot.processor.service.entry.UnstableMarketConditionAnalysisService;
import com.jtradebot.processor.service.price.LiveOptionPricingService;
import com.jtradebot.processor.service.price.OptionPricingService;
import com.jtradebot.processor.service.risk.DynamicRiskManagementService;
import com.jtradebot.processor.service.analysis.CategoryAnalysisService;
import com.jtradebot.processor.service.entry.DynamicRuleEvaluatorService;
import com.jtradebot.processor.service.notification.OrderNotificationService;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderExecutionService {

    private final ExitStrategyService exitStrategyService;
    private final OptionPricingService optionPricingService;
    private final KiteInstrumentHandler kiteInstrumentHandler;
    private final TickDataManager tickDataManager;
    private final KiteConnect kiteConnect;
    private final Environment environment;
    private final LiveOptionPricingService liveOptionPricingService;
    private final TradingConfigurationService tradingConfigService;
    private final DynamicRiskManagementService dynamicRiskManagementService;
    private final DynamicRuleEvaluatorService dynamicRuleEvaluatorService;

    public void handleOrderManagement(Tick indexTick) {
        exitStrategyService.checkAndProcessExitsWithStrategy(indexTick);
        try {
            updateLivePnL(indexTick);
        } catch (KiteException e) {
            log.error("Error updating live P&L for tick: {}", indexTick.getInstrumentToken(), e);
        }
    }

    public void validateAndExecuteOrder(Tick tick, ScalpingEntryDecision entryDecision, boolean inTradingZone, String dominantTrend) {

        try {
            // Check if we can execute the order (no active orders)
            boolean hasActiveOrder = exitStrategyService.hasActiveOrder();

            log.debug("🔍 ORDER VALIDATION - HasActiveOrder: {}", hasActiveOrder);

            if (!hasActiveOrder) {
                // Determine order type based on entry decision
                String orderType = determineOrderType(dominantTrend);
                createTradeOrder(tick, orderType, entryDecision, inTradingZone);
            } else {
                log.warn("⚠️ ACTIVE ORDER EXISTS - Cannot create new order.");
            }
        } catch (KiteException e) {
            log.error("KiteException while validating and executing order: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error validating and executing order: {}", e.getMessage());
        }
    }


    public void executeOrdersIfSignalsGenerated(Tick indexTick, FlattenedIndicators indicators, UnstableMarketConditionAnalysisService.FlexibleFilteringResult result, double qualityScore, String dominantTrend, Map<String, Integer> callScores, Map<String, Integer> putScores) {
        // Get entry decision directly from DynamicRuleEvaluatorService
        ScalpingEntryDecision scenarioDecision = null;
        try {
            scenarioDecision = dynamicRuleEvaluatorService.getEntryDecision(indexTick, indicators, result, qualityScore, dominantTrend, callScores, putScores);
        } catch (Exception e) {
            log.error("Error getting entry decision for order execution: {}", e.getMessage());
            return;
        }

        if (scenarioDecision != null && scenarioDecision.isShouldEntry()) {
            validateAndExecuteOrder(indexTick, scenarioDecision, result.isConditionsMet(), dominantTrend);
        }
    }

    private String determineOrderType(String dominantTrend) {
        return dominantTrend.concat("_BUY");
    }

    private void createTradeOrder(Tick tick, String orderType, ScalpingEntryDecision entryDecision, Boolean entryMarketConditionSuitable) throws KiteException {
        try {
            String instrumentToken = String.valueOf(tick.getInstrumentToken());

            // Get dynamic stop loss and target points based on 5-minute candle range
            double stopLossPoints, targetPoints;
            String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();
            Tick niftyTick = tickDataManager.getLastTick(niftyToken);

            if (niftyTick == null) {
                log.error("No Nifty index data available for order creation");
                return;
            }

            double currentIndexPrice = niftyTick.getLastTradedPrice();

            // Calculate dynamic stop loss and target based on 5-minute candle range
            stopLossPoints = dynamicRiskManagementService.calculateDynamicStopLoss(niftyToken, currentIndexPrice);
            targetPoints = dynamicRiskManagementService.calculateDynamicTarget(stopLossPoints);

            // Log dynamic risk management info
            String dynamicRiskInfo = optionPricingService.getDynamicRiskInfo(niftyToken, currentIndexPrice);
            log.info("🎯 DYNAMIC RISK MANAGEMENT - Stop Loss: {} pts, Target: {} pts | {}",
                    String.format("%.2f", stopLossPoints),
                    String.format("%.2f", targetPoints),
                    dynamicRiskInfo);

            // Try to get live option pricing first (for live profile)
            Optional<LiveOptionPricingService.LiveOptionPricingInfo> livePricing = liveOptionPricingService.getLiveOptionPricing(orderType);

            double optionEntryPrice, stopLossPrice, targetPrice;
            String optionSymbol;
            long optionInstrumentToken;

            if (livePricing.isPresent()) {
                // Use live option pricing
                LiveOptionPricingService.LiveOptionPricingInfo pricingInfo = livePricing.get();
                optionEntryPrice = pricingInfo.getOptionLTP();
                stopLossPrice = Math.max(0.0, optionEntryPrice - stopLossPoints);
                targetPrice = optionEntryPrice + targetPoints;
                optionSymbol = pricingInfo.getOptionInstrument().getTradingSymbol();
                optionInstrumentToken = pricingInfo.getOptionInstrument().getInstrumentToken();
                currentIndexPrice = pricingInfo.getNiftyIndexPrice();

                log.info("🎯 USING LIVE OPTION PRICING - Symbol: {}, LTP: {}, Strike: {}, Index: {}",
                        optionSymbol, optionEntryPrice, pricingInfo.getStrikePrice(), currentIndexPrice);
            } else {
                // Fallback to placeholder pricing (for local profile or when live pricing fails)
                // Calculate option entry price (1% of index price as premium)
                optionEntryPrice = currentIndexPrice * 0.01; // Simplified calculation

                // Calculate stop loss and target prices using dynamic points
                stopLossPrice = Math.max(0.0, optionEntryPrice - stopLossPoints);
                targetPrice = optionEntryPrice + targetPoints;

                // Use placeholder option symbols since we're analyzing with index/future tokens
                optionSymbol = "CALL_BUY".equals(orderType) ? "TEST_OPTION_CE" : "TEST_OPTION_PE";
                optionInstrumentToken = 0L; // No instrument token (using placeholder symbols)

                log.info("📊 USING PLACEHOLDER PRICING - Index: {}, Premium: {} (1% of index)",
                        currentIndexPrice, optionEntryPrice);
            }

            JtradeOrder order;
            if (entryDecision.isShouldEntry() && entryDecision.getScenarioName() != null) {
                // Create order with scenario information
                order = exitStrategyService.createOrderEntryWithScenario(
                        OrderTypeEnum.valueOf(orderType),
                        optionSymbol,
                        optionInstrumentToken,
                        optionEntryPrice, // Option entry price (premium)
                        currentIndexPrice, // Entry index price (Nifty level)
                        stopLossPrice,
                        targetPrice,
                        tradingConfigService.getMinLotSize(), // Get configured minimum lot size from JSON
                        entryDecision.getScenarioName(),
                        entryDecision.getReason(), // Use reason as description
                        entryDecision.getConfidence(),
                        entryDecision.getCategoryScores(),
                        entryDecision.getMatchedConditions(),
                        entryMarketConditionSuitable, // Market condition suitable flag
                        tick.getTickTimestamp() // Use tick timestamp instead of current time
                );
            } else {
                log.warn("❌ ENTRY DECISION INVALID - Cannot create order without valid scenario");
                return;
            }

            if (order != null) {
                // Force database update to ensure order is saved
                exitStrategyService.updateOrdersToDatabase();

                log.info("📝 TRADE CREATED - {} {} @ {}",
                        orderType, order.getTradingSymbol(), optionEntryPrice);
                log.info("📊 ORDER DETAILS - ID: {}, Status: {}, StopLoss: {}, Target: {}",
                        order.getId(), order.getStatus(), order.getStopLossPrice(), order.getTargetPrice());

                // Log scenario information if available
                if (order.getEntryScenarioName() != null) {
                    log.info("🎯 SCENARIO INFO - Name: {}, Description: {}, Confidence: {}/10",
                            order.getEntryScenarioName(), order.getEntryScenarioDescription(), order.getEntryScenarioConfidence());
                    if (order.getEntryCategoryScores() != null) {
                        log.info("📈 CATEGORY SCORES - {}", order.getEntryCategoryScores());
                    }
                    if (order.getEntryMatchedConditions() != null) {
                        log.info("✅ MATCHED CONDITIONS - {}", order.getEntryMatchedConditions());
                    }
                }

                log.info("💾 ORDER SAVED TO DATABASE - ID: {}", order.getId());
            } else {
                log.warn("❌ Failed to create trade order - may already have active trade or ExitStrategyService returned null");
                log.debug("Debug: hasActiveOrder() = {}, instrumentToken = {}",
                        exitStrategyService.hasActiveOrder(), instrumentToken);
            }

        } catch (Exception e) {
            log.error("Error creating trade order for tick: {}", tick.getInstrumentToken(), e);
        }
    }


    public void updateLivePnL(Tick tick) throws KiteException {
        try {
            // Get active order from ExitStrategyService (global check)
            List<JtradeOrder> activeOrders = exitStrategyService.getActiveOrders();
            if (activeOrders.isEmpty()) {
                return;
            }

            JtradeOrder activeOrder = activeOrders.get(0); // Get the first active order

            double currentOptionPrice;
            double currentIndexPrice;

            // Check if we're in live profile and have real option instrument token
            if (isLiveProfile() && activeOrder.getInstrumentToken() != null && activeOrder.getInstrumentToken() > 0) {
                // Get real option LTP for live profile using Kite Connect API
                String instrumentToken = String.valueOf(activeOrder.getInstrumentToken());
                currentOptionPrice = kiteConnect.getLTP(new String[]{instrumentToken}).get(instrumentToken).lastPrice;

                // Get current Nifty index price for reference
                String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();
                Tick niftyTick = tickDataManager.getLastTick(niftyToken);
                currentIndexPrice = niftyTick != null ? niftyTick.getLastTradedPrice() : activeOrder.getEntryIndexPrice();

                log.debug("🎯 USING REAL OPTION LTP FROM KITE API - Token: {}, Symbol: {}, LTP: {}, Index: {}",
                        activeOrder.getInstrumentToken(), activeOrder.getTradingSymbol(), currentOptionPrice, currentIndexPrice);
            } else {
                // Use calculated option price for local profile or when no real instrument token
                String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();
                Tick niftyTick = tickDataManager.getLastTick(niftyToken);
                if (niftyTick == null) {
                    log.warn("No Nifty index data available for P&L calculation");
                    return;
                }

                currentIndexPrice = niftyTick.getLastTradedPrice();
                double entryIndexPrice = activeOrder.getEntryIndexPrice();
                double entryOptionPrice = activeOrder.getEntryPrice();

                // Calculate current option price based on index movement
                currentOptionPrice = optionPricingService.calculateCurrentLTP(
                        entryOptionPrice, entryIndexPrice, currentIndexPrice, activeOrder.getOrderType());

                log.debug("📊 USING CALCULATED OPTION PRICE - Entry: {}, Current: {}, Index: {}",
                        entryOptionPrice, currentOptionPrice, currentIndexPrice);
            }

            double entryOptionPrice = activeOrder.getEntryPrice();
            double points = currentOptionPrice - entryOptionPrice;
            double pnl = points * activeOrder.getQuantity();

            // Simplified live P&L log (only points and P&L)
            String orderTypeDisplay = OrderTypeEnum.CALL_BUY.equals(activeOrder.getOrderType()) ? "CALL" : "PUT";
            log.info("_________________________ 💰 LIVE P&L - {} | Points: {}, P&L: ₹{}, LTP: {} _________________________",
                    orderTypeDisplay, String.format("%+.2f", points), String.format("%.2f", pnl), String.format("%.2f", currentOptionPrice));

        } catch (Exception e) {
            log.error("Error updating live P&L for tick: {}", tick.getInstrumentToken(), e);
        }
    }

    /**
     * Check if we're in live profile
     */
    private boolean isLiveProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if ("live".equals(profile)) {
                return true;
            }
        }
        return false;
    }


}
