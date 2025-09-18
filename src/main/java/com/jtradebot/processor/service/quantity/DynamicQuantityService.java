package com.jtradebot.processor.service.quantity;

import com.jtradebot.processor.repository.document.TradeConfig;
import com.jtradebot.processor.service.TickSetupService;
import com.jtradebot.processor.service.notification.OrderNotificationService;
import com.jtradebot.processor.service.price.LiveOptionPricingService;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Margin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    
    // Constants
    public static final int ZERO_QUANTITY = 0;
    

    private TradeConfig.TradePreference getTradingPreferences() {
        try {
            TradeConfig tradeConfig = tickSetupService.getTradeConfig();
            if (tradeConfig.getTradePreference() != null) {
                return tradeConfig.getTradePreference();
            }
        } catch (Exception e) {
            log.warn("Failed to get trading preferences from database, using defaults: {}", e.getMessage());
        }
        
        // Return default values if database access fails
        TradeConfig.TradePreference defaultPrefs = new TradeConfig.TradePreference();
        defaultPrefs.setMaxInvestmentPercentage(95.0);
        defaultPrefs.setMinQuantity(75);
        defaultPrefs.setMaxQuantity(150);
        return defaultPrefs;
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
            double maxInvestmentPercentage = preferences.getMaxInvestmentPercentage() / 100.0;
            
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
            
            // Step 4: Calculate how many lots we can afford
            double maxInvestmentAmount = availableBalance * maxInvestmentPercentage;
            int maxAffordableLots = (int) Math.floor(maxInvestmentAmount / costPerLot);
            
            // Step 5: Apply safety limits from database
            int calculatedQuantity = Math.max(minQuantity, Math.min(maxAffordableLots * minQuantity, maxQuantity));
            
            log.info("DYNAMIC QTY CALCULATION - Order Type: {}, Balance: ₹{}, Option Price: ₹{}, " +
                    "Cost/Lot: ₹{}, Max Lots: {}, Min Qty: {}, Max Qty: {}, Calculated Qty: {}",
                    orderType,
                    String.format("%.2f", availableBalance),
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
            String message = String.format(
                    "⚠️ QUANTITY VALIDATION FAILED - %s\n\n" +
                            "📊 Details:\n" +
                            "• Order Type: %s\n" +
                            "• Calculated Quantity: %d\n" +
                            "• Minimum Required: %d\n" +
                            "• Context: %s\n\n" +
                            "🚫 Order creation blocked due to insufficient quantity.\n" +
                            "💡 Consider increasing account balance or adjusting quantity calculation parameters.",
                    context, orderType, dynamicQuantity, minQuantity, context
            );

            // Log the validation failure
            log.warn("___________UNABLE TO CREATE ORDER. Calculated dynamic quantity {} is less than minimum required {}. Context: {}",
                    dynamicQuantity, minQuantity, context);

            // Send email notification
            try {
                String subject = "🚫 ORDER BLOCKED - Insufficient Quantity";
                orderNotificationService.sendCustomNotification(subject, message);
                log.info("📧 Quantity validation failure notification sent for {} order", context);
            } catch (Exception e) {
                log.error("Failed to send quantity validation failure notification: {}", e.getMessage(), e);
            }

            return false;
        }

        log.info("✅ Quantity validation passed - {}: {} (Min: {})", context, dynamicQuantity, minQuantity);
        return true;
    }

}
