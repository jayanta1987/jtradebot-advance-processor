package com.jtradebot.processor.service.quantity;

import com.jtradebot.processor.repository.document.TradeConfig;
import com.jtradebot.processor.service.TickSetupService;
import com.jtradebot.processor.service.notification.OrderNotificationService;
import com.jtradebot.processor.service.price.LiveOptionPricingService;
import com.jtradebot.processor.common.ProfileUtil;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.jtradebot.processor.service.entry.DynamicRuleEvaluatorService;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Margin;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Service
@Slf4j
@RequiredArgsConstructor
public class DynamicQuantityService {

    private final KiteConnect kiteConnect;
    private final LiveOptionPricingService liveOptionPricingService;
    private final TickSetupService tickSetupService;
    private final OrderNotificationService orderNotificationService;
    private final Environment environment;
    private final TickDataManager tickDataManager;
    private final KiteInstrumentHandler kiteInstrumentHandler;
    private final DynamicRuleEvaluatorService dynamicRuleEvaluatorService;
    
    // Constants
    public static final int ZERO_QUANTITY = 0;
    
    // Throttle state for insufficient quantity notification (global, 1-minute window)
    private long lastInsufficientQtyEmailAtMillis = 0L;
    

    private TradeConfig.TradePreference getTradingPreferences() {
        try {
            TradeConfig tradeConfig = tickSetupService.getTradeConfig();
            if (tradeConfig.getTradePreference() != null) {
                return tradeConfig.getTradePreference();
            }
            throw new RuntimeException("Trading preferences not configured in database and no fallback available");
        } catch (Exception e) {
            log.error("Failed to get trading preferences from database: {}", e.getMessage());
            throw new RuntimeException("Failed to get trading preferences from configuration", e);
        }
    }

    public int getMinQuantity() {
        return getTradingPreferences().getMinQuantity();
    }

    public int calculateDynamicQuantity(String orderType) {
        try {
            // Get configuration from database
            TradeConfig.TradePreference preferences = getTradingPreferences();
            int minQuantity = preferences.getMinQuantity();
            int maxQuantity = preferences.getMaxQuantity();
            
            // For local profile, use max quantity but still check EMA200 direction
            if (ProfileUtil.isProfileActive(environment, "local")) {
                int adjustedQuantity = adjustQuantityBasedOnEma200Direction(maxQuantity, maxQuantity, minQuantity);
                log.info("🏠 LOCAL PROFILE - Using max quantity: {} (Order Type: {}), Adjusted based on EMA200: {}", 
                        maxQuantity, orderType, adjustedQuantity);
                return adjustedQuantity;
            }
            
            // For live profile, perform full balance-based calculation
            double maxInvestment = preferences.getMaxInvestment();
            
            // Step 1: Get current balance from KiteConnect
            double availableBalance = getCurrentBalance();
            if (availableBalance <= 0) {
                log.warn("⚠️ DYNAMIC QTY - No available balance, using zero quantity: {}", ZERO_QUANTITY);
                return ZERO_QUANTITY;
            }
            
            // Step 2: Get option price for 1 quantity
            Optional<LiveOptionPricingService.LiveOptionPricingInfo> pricingInfo = 
                liveOptionPricingService.getLiveOptionPricing(orderType);
            
            if (pricingInfo.isEmpty()) {
                log.warn("⚠️ DYNAMIC QTY - No pricing information available for order type: {}, using zero quantity: {}",
                        orderType, ZERO_QUANTITY);
                return ZERO_QUANTITY;
            }
            
            double optionPrice = pricingInfo.get().getOptionLTP();
            if (optionPrice <= 0) {
                log.warn("⚠️ DYNAMIC QTY - Invalid option price: {}, using zero quantity: {}", optionPrice, ZERO_QUANTITY);
                return ZERO_QUANTITY;
            }
            
            // Step 3: Calculate cost for 1 lot
            double costPerLot = optionPrice * minQuantity;

            if(availableBalance < costPerLot) {
                log.warn("⚠️ DYNAMIC QTY - Not sufficient even for one lot: {}, using zero quantity: {}", costPerLot, ZERO_QUANTITY);
                return ZERO_QUANTITY;
            }
            
            // Step 4: Calculate how many lots we can afford using Math.min(maxInvestment, currentBalance)
            double maxInvestmentAmount = Math.min(maxInvestment, availableBalance);
            int maxAffordableLots = (int) Math.floor(maxInvestmentAmount / costPerLot);
            
            // Step 5: Apply safety limits from database
            int calculatedQuantity = Math.max(minQuantity, Math.min(maxAffordableLots * minQuantity, maxQuantity));
            
            // Step 6: Check EMA200 direction and adjust quantity if needed
            calculatedQuantity = adjustQuantityBasedOnEma200Direction(calculatedQuantity, maxQuantity, minQuantity);
            
            log.info("🔴 LIVE PROFILE - DYNAMIC QTY CALCULATION - Order Type: {}, Balance: ₹{}, Max Investment: ₹{}, Option Price: ₹{}, " +
                    "Cost/Lot: ₹{}, Max Lots: {}, Min Qty: {}, Max Qty: {}, Calculated Qty: {}",
                    orderType,
                    String.format("%.2f", availableBalance),
                    String.format("%.2f", maxInvestment),
                    String.format("%.2f", optionPrice),
                    String.format("%.2f", costPerLot),
                    maxAffordableLots,
                    minQuantity,
                    maxQuantity,
                    calculatedQuantity);

            return calculatedQuantity;
            
        } catch (KiteException e) {
            log.error("❌ DYNAMIC QTY - KiteException calculating quantity for order type {}: {}", orderType, e.getMessage(), e);
            return getMinQuantity();
        } catch (Exception e) {
            log.error("❌ DYNAMIC QTY - Error calculating quantity for order type {}: {}", orderType, e.getMessage(), e);
            return getMinQuantity();
        }
    }
    
    /**
     * Get current available balance from KiteConnect
     * 
     * @return Available balance or 0 if error
     */
    private double getCurrentBalance() throws KiteException {
        try {
            Map<String, Margin> margins = kiteConnect.getMargins();
            if (margins != null && margins.containsKey("equity")) {
                String equityMargin = margins.get("equity").net;
                return Double.parseDouble(equityMargin);
            }
            log.warn("⚠️ BALANCE INFO - Margin information not available");
            return 0.0;
        } catch (KiteException e) {
            log.error("❌ BALANCE INFO - Error fetching balance from KiteConnect: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("❌ BALANCE INFO - Error parsing balance: {}", e.getMessage(), e);
            return 0.0;
        }
    }

    public boolean validateQuantity(int dynamicQuantity, String orderType, String context) {
        int minQuantity = getMinQuantity();

        if (dynamicQuantity < minQuantity) {
            String targetedSymbol = null;
            try {
                // Try to resolve intended trading symbol (best-effort; optional)
                Optional<LiveOptionPricingService.LiveOptionPricingInfo> pricingInfo =
                        liveOptionPricingService.getLiveOptionPricing(orderType);
                if (pricingInfo.isPresent() && pricingInfo.get().getOptionInstrument() != null) {
                    targetedSymbol = pricingInfo.get().getOptionInstrument().getTradingSymbol();
                }
            } catch (KiteException e) {
                log.debug("Unable to resolve targeted trading symbol due to KiteException: {}", e.getMessage());
            } catch (Exception e) {
                log.debug("Unable to resolve targeted trading symbol for notification: {}", e.getMessage());
            }

            String message = String.format(
                    "⚠️ QUANTITY VALIDATION FAILED - %s\n\n" +
                            "📊 Details:\n" +
                            "• Order Type: %s\n" +
                            (targetedSymbol != null ? "• Target Trading Symbol: %s\n" : "") +
                            "• Target Order Quantity: %d\n" +
                            "• Minimum Required Quantity: %d\n" +
                            "• Context: %s\n\n" +
                            "🚫 Order creation blocked due to insufficient quantity.\n" +
                            "💡 Consider increasing account balance or adjusting quantity calculation parameters.",
                    context,
                    orderType,
                    targetedSymbol != null ? targetedSymbol : "",
                    dynamicQuantity,
                    minQuantity,
                    context
            );

            // Log the validation failure
            log.warn("___________UNABLE TO CREATE ORDER. Calculated dynamic quantity {} is less than minimum required {}. Context: {}",
                    dynamicQuantity, minQuantity, context);

            // Send email notification (throttled: at most once per minute)
            if (shouldSendInsufficientQtyEmail()) {
                try {
                    String subject = "🚫 ORDER BLOCKED - Insufficient Quantity" +
                            (targetedSymbol != null ? (" - " + targetedSymbol) : "");
                    orderNotificationService.sendCustomNotification(subject, message);
                    log.info("📧 Quantity validation failure notification sent for {} order", context);
                } catch (Exception e) {
                    log.error("Failed to send quantity validation failure notification: {}", e.getMessage(), e);
                }
            } else {
                log.info("⏱️ Skipping insufficient quantity email - last notification sent less than 1 minute ago");
            }

            return false;
        }

        log.info("✅ Quantity validation passed - {}: {} (Min: {})", context, dynamicQuantity, minQuantity);
        return true;
    }

    private synchronized boolean shouldSendInsufficientQtyEmail() {
        long now = System.currentTimeMillis();
        if (now - lastInsufficientQtyEmailAtMillis >= 60_000L) {
            lastInsufficientQtyEmailAtMillis = now;
            return true;
        }
        return false;
    }

    /**
     * Adjust quantity based on EMA200 direction
     * If all EMA200 are in same direction → use calculated quantity (current logic)
     * If mixed directions → use 50% of maxQuantity, rounded to nearest multiple of lot size (75)
     * 
     * @param calculatedQuantity The quantity calculated from balance and investment limits
     * @param maxQuantity Maximum allowed quantity from configuration
     * @param lotSize Lot size (typically 75)
     * @return Adjusted quantity based on EMA200 direction
     */
    private int adjustQuantityBasedOnEma200Direction(int calculatedQuantity, int maxQuantity, int lotSize) {
        try {
            // Get Nifty index tick for EMA200 check
            String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();
            Tick niftyTick = tickDataManager.getLastTick(niftyToken);
            
            if (niftyTick == null) {
                log.warn("⚠️ DYNAMIC QTY - No Nifty tick available for EMA200 check, using calculated quantity: {}", calculatedQuantity);
                return calculatedQuantity;
            }
            
            // Check if all EMA200 are in same direction (using same timeframes as milestone check: 1min, 5min, 15min, 1hour)
            List<CandleTimeFrameEnum> timeframes = Arrays.asList(CandleTimeFrameEnum.ONE_MIN, CandleTimeFrameEnum.FIVE_MIN, CandleTimeFrameEnum.FIFTEEN_MIN, CandleTimeFrameEnum.ONE_HOUR);
            boolean allEma200InSameDirection = dynamicRuleEvaluatorService.areAllEma200InSameDirection(niftyTick, timeframes);
            
            if (allEma200InSameDirection) {
                // All EMA200 in same direction → use calculated quantity (current logic)
                log.info("📊 DYNAMIC QTY - All EMA200 in same direction, using calculated quantity: {}", calculatedQuantity);
                return calculatedQuantity;
            } else {
                // Mixed directions → use 50% of maxQuantity, rounded to nearest multiple of lot size
                int fiftyPercentOfMax = (int) Math.round(maxQuantity * 0.5);
                int adjustedQuantity = roundToNearestMultiple(fiftyPercentOfMax, lotSize);
                
                // Ensure adjusted quantity is at least lot size and not more than maxQuantity
                adjustedQuantity = Math.max(lotSize, Math.min(adjustedQuantity, maxQuantity));
                
                log.info("📊 DYNAMIC QTY - Mixed EMA200 directions detected, using 50% of max: {} (rounded to {}), Original calculated: {}",
                        fiftyPercentOfMax, adjustedQuantity, calculatedQuantity);
                return adjustedQuantity;
            }
            
        } catch (Exception e) {
            log.error("❌ DYNAMIC QTY - Error adjusting quantity based on EMA200 direction: {}", e.getMessage(), e);
            // On error, use calculated quantity (safer approach)
            return calculatedQuantity;
        }
    }

    /**
     * Round a number to the nearest multiple of a given value
     * 
     * @param value The value to round
     * @param multiple The multiple to round to (e.g., 75 for lot size)
     * @return The value rounded to the nearest multiple
     */
    private int roundToNearestMultiple(int value, int multiple) {
        if (multiple <= 0) {
            return value;
        }
        return Math.round((float) value / multiple) * multiple;
    }

}
