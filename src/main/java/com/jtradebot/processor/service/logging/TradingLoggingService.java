package com.jtradebot.processor.service.logging;

import com.jtradebot.processor.model.indicator.EntryQuality;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.strategy.ScalpingEntryDecision;
import com.jtradebot.processor.repository.document.JtradeOrder;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.zerodhatech.models.Tick;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class TradingLoggingService {

    /**
     * Log entry scenario passed
     */
    public void logEntryScenarioPassed(ScalpingEntryDecision entryDecision) {
        log.info("✅ ENTRY SCENARIO PASSED - Scenario: {}, Confidence: {}/10", 
            entryDecision.getScenarioName(), entryDecision.getConfidence());
    }

    /**
     * Log entry signal generated
     */
    public void logEntrySignal(Tick tick, String orderType, ScalpingEntryDecision entryDecision) {
        log.info("🚀 {} ENTRY SIGNAL - Instrument: {}, Price: {}, Scenario: {}, Confidence: {}/10, Time: {}", 
            orderType, tick.getInstrumentToken(), tick.getLastTradedPrice(), 
            entryDecision.getScenarioName(), entryDecision.getConfidence(), tick.getTickTimestamp());
    }

    /**
     * Log order execution
     */
    public void logOrderExecution(String orderType, ScalpingEntryDecision entryDecision) {
        log.info("🎯 EXECUTING {} ORDER - Scenario: {}, Confidence: {}/10", 
            orderType, entryDecision.getScenarioName(), entryDecision.getConfidence());
    }

    /**
     * Log active order warning
     */
    public void logActiveOrderWarning(boolean hasActiveOrder) {
        log.warn("⚠️ ACTIVE ORDER EXISTS - Cannot create new order. Active orders: {}", hasActiveOrder);
    }

    /**
     * Log entry skipped
     */
    public void logEntrySkipped(boolean shouldCall, boolean shouldPut, ScalpingEntryDecision scenarioDecision) {
        log.debug("⏸️ ENTRY SKIPPED - Scenario decision no longer valid at execution time (shouldCall: {}, shouldPut: {}, decisionPresent: {}, decisionAllows: {})", 
            shouldCall, shouldPut, scenarioDecision != null, scenarioDecision != null && scenarioDecision.isShouldEntry());
    }

    /**
     * Log trade created
     */
    public void logTradeCreated(String orderType, String tradingSymbol, double entryPrice) {
        log.info("📝 TRADE CREATED - {} {} @ {}", orderType, tradingSymbol, entryPrice);
    }

    /**
     * Log order details
     */
    public void logOrderDetails(JtradeOrder order) {
        log.info("📊 ORDER DETAILS - ID: {}, Status: {}, StopLoss: {}, Target: {}", 
            order.getId(), order.getStatus(), order.getStopLossPrice(), order.getTargetPrice());
    }

    /**
     * Log scenario information
     */
    public void logScenarioInfo(JtradeOrder order) {
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
    }

    /**
     * Log live option pricing
     */
    public void logLiveOptionPricing(String symbol, double ltp, double strikePrice, double indexPrice) {
        log.info("🎯 USING LIVE OPTION PRICING - Symbol: {}, LTP: {}, Strike: {}, Index: {}", 
            symbol, ltp, strikePrice, indexPrice);
    }

    /**
     * Log placeholder pricing
     */
    public void logPlaceholderPricing(double indexPrice, double premium) {
        log.info("📊 USING PLACEHOLDER PRICING - Index: {}, Premium: {} (1% of index)", indexPrice, premium);
    }

    /**
     * Log order saved
     */
    public void logOrderSaved(String orderId) {
        log.info("💾 ORDER SAVED TO DATABASE - ID: {}", orderId);
    }

    /**
     * Log order creation failed
     */
    public void logOrderCreationFailed(boolean hasActiveOrder, String instrumentToken) {
        log.warn("❌ Failed to create trade order - may already have active trade or ExitStrategyService returned null");
        log.debug("Debug: hasActiveOrder() = {}, instrumentToken = {}", hasActiveOrder, instrumentToken);
    }

    /**
     * Log live P&L update
     */
    public void logLivePnL(String orderType, double points, double pnl, double ltp) {
        log.info("_________________________ 💰 LIVE P&L - {} | Points: {}, P&L: ₹{}, LTP: {} _________________________", 
            orderType, String.format("%+.2f", points), String.format("%.2f", pnl), String.format("%.2f", ltp));
    }

    /**
     * Log real option LTP from Kite API
     */
    public void logRealOptionLTP(long instrumentToken, String tradingSymbol, double ltp, double indexPrice) {
        log.debug("🎯 USING REAL OPTION LTP FROM KITE API - Token: {}, Symbol: {}, LTP: {}, Index: {}", 
            instrumentToken, tradingSymbol, ltp, indexPrice);
    }

    /**
     * Log calculated option price
     */
    public void logCalculatedOptionPrice(double entryPrice, double currentPrice, double indexPrice) {
        log.debug("📊 USING CALCULATED OPTION PRICE - Entry: {}, Current: {}, Index: {}", 
            entryPrice, currentPrice, indexPrice);
    }

    /**
     * Log no Nifty data warning
     */
    public void logNoNiftyDataWarning() {
        log.warn("No Nifty index data available for P&L calculation");
    }

    /**
     * Log market not started
     */
    public void logMarketNotStarted() {
        log.info("Market not started yet. Skipping tick processing. Current time: {}", new java.util.Date());
    }

    /**
     * Log tick processing start
     */
    public void logTickProcessingStart(int tickCount) {
        log.debug("🔄 Processing {} ticks", tickCount);
    }

    /**
     * Log tick processing error
     */
    public void logTickProcessingError(long instrumentToken, Exception e) {
        log.error("Error processing tick with SCALPING_FUTURE_VOLUME_SURGE strategy for index instrument: {}", instrumentToken, e);
    }

    /**
     * Log entry logic error
     */
    public void logEntryLogicError(Exception e) {
        log.error("Error logging real entry logic: {}", e.getMessage());
    }

    /**
     * Log order validation error
     */
    public void logOrderValidationError(Exception e) {
        log.error("Error validating and executing order: {}", e.getMessage());
    }

    /**
     * Log KiteException in order validation
     */
    public void logKiteExceptionInOrderValidation(Exception e) {
        log.error("KiteException while validating and executing order: {}", e.getMessage());
    }

    /**
     * Log KiteException in order validation (overloaded for KiteException)
     */
    public void logKiteExceptionInOrderValidation(com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException e) {
        log.error("KiteException while validating and executing order: {}", e.getMessage());
    }

    /**
     * Log trade order creation error
     */
    public void logTradeOrderCreationError(long instrumentToken, Exception e) {
        log.error("Error creating trade order for tick: {}", instrumentToken, e);
    }

    /**
     * Log live P&L error
     */
    public void logLivePnLError(long instrumentToken, Exception e) {
        log.error("Error updating live P&L for tick: {}", instrumentToken, e);
    }

    /**
     * Log live P&L error with KiteException
     */
    public void logLivePnLError(long instrumentToken, com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException e) {
        log.error("Error updating live P&L for tick: {}", instrumentToken, e);
    }

    /**
     * Log exit processing error
     */
    public void logExitProcessingError(long instrumentToken, Exception e) {
        log.error("Error checking and processing exits for tick: {}", instrumentToken, e);
    }

    /**
     * Log profitable trade filter error
     */
    public void logProfitableTradeFilterError(String orderId, Exception e) {
        log.error("Error storing profitable trade filter info for order: {}", orderId, e);
    }
}
