package com.jtradebot.processor.service.scheduler;

import com.jtradebot.processor.service.notification.SnsEmailService;
import com.jtradebot.processor.service.order.ActiveOrderTrackingService;
import com.jtradebot.processor.service.price.LiveOptionPricingService;
import com.jtradebot.processor.service.quantity.DynamicQuantityService;
import com.jtradebot.processor.common.ProfileUtil;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Margin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Service
@Slf4j
@RequiredArgsConstructor
public class BalanceTrackerSchedulerService {
    
    private final LiveOptionPricingService liveOptionPricingService;
    private final KiteConnect kiteConnect;
    private final Environment environment;
    private final SnsEmailService snsEmailService;
    private final ActiveOrderTrackingService activeOrderTrackingService;
    private final DynamicQuantityService dynamicQuantityService;
    
    @Value("${balance-check.scheduler.enabled}")
    private boolean balanceCheckSchedulerEnabled;
    
    /**
     * Balance tracker scheduler that runs every minute at the start of each minute
     * Checks option pricing for CALL and PUT, calculates required balance, and validates available balance
     * 
     * Filter: Only runs when there are no active orders to avoid unnecessary balance checks during trading
     */
    @Scheduled(cron = "0 */5 * * * *") // Run every 5 minutes at 0 seconds (e.g., 12:10:00, 12:15:00, 12:20:00)
    public void trackBalance() {
        try {
            // Check if balance check scheduler is enabled
            if (!balanceCheckSchedulerEnabled) {
                log.debug("Balance tracker skipped - scheduler disabled via configuration");
                return;
            }

            // Only run in live profile
            if (!ProfileUtil.isProfileActive(environment, "live")) {
                log.debug("Balance tracker skipped - not in live profile");
                return;
            }
            
            // Skip balance check if there are active orders
            if (activeOrderTrackingService.hasActiveOrder()) {
                log.info("🔄 BALANCE TRACKER - Skipping balance check due to active orders");
                return;
            }
            
            log.debug("🔄 BALANCE TRACKER - Starting balance check");
            
            // Check balance for both CALL and PUT options
            checkBalanceForOrderType("CALL_BUY");
            checkBalanceForOrderType("PUT_BUY");
            
            log.info("✅ BALANCE TRACKER - Completed balance check");
            
        } catch (Exception e) {
            log.error("❌ BALANCE TRACKER - Error in balance tracking: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Check balance for a specific order type (CALL or PUT)
     */
    private void checkBalanceForOrderType(String orderType) {
        try {
            log.debug("🔍 BALANCE CHECK - Checking balance for order type: {}", orderType);
            
            // Get live option pricing information
            Optional<LiveOptionPricingService.LiveOptionPricingInfo> pricingInfo = 
                liveOptionPricingService.getLiveOptionPricing(orderType);
            
            if (pricingInfo.isEmpty()) {
                log.warn("⚠️ BALANCE CHECK - No pricing information available for order type: {}", orderType);
                return;
            }
            
            LiveOptionPricingService.LiveOptionPricingInfo info = pricingInfo.get();
            double optionLTP = info.getOptionLTP();
            
            // Calculate required amount using dynamic quantity calculation
            int dynamicQuantity = dynamicQuantityService.calculateDynamicQuantity(orderType);
            int minQuantity = dynamicQuantityService.getMinQuantity();

            double minRequiredAmount = optionLTP * minQuantity;
            log.info("💰 PRICING INFO - Order Type: {}, Option LTP: {}, Dynamic Qty: {}, Required Amount: {}",
                    orderType, optionLTP, dynamicQuantity, minRequiredAmount);

            // Get current balance from KiteConnect
            double currentBalance = getCurrentBalance();
            double shortfall = minRequiredAmount - currentBalance;

            if (dynamicQuantity == 0) {
                log.warn("❌ INSUFFICIENT BALANCE - Order Type: {}, Required: {}, Available: {}, Shortfall: {}", 
                        orderType, minRequiredAmount, currentBalance, shortfall);
                
                // Send email notification for insufficient balance
                sendInsufficientBalanceNotification(orderType, minRequiredAmount, currentBalance, shortfall, info, dynamicQuantity);
            } else {
                log.info("✅ SUFFICIENT BALANCE - Order Type: {}, Required: {}, Available: {}, Excess: {}", 
                        orderType, minRequiredAmount, currentBalance, (currentBalance - minRequiredAmount));
            }
            
        } catch (KiteException e) {
            log.error("❌ BALANCE CHECK - KiteException checking balance for order type {}: {}", orderType, e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ BALANCE CHECK - Error checking balance for order type {}: {}", orderType, e.getMessage(), e);
        }
    }
    
    /**
     * Get current available balance from KiteConnect
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
    
    /**
     * Manually check balance and return detailed results
     * This method is called by the API endpoint to return results instead of just logging
     * 
     * Filter: Only runs when there are no active orders to avoid unnecessary balance checks during trading
     */
    public Map<String, Object> checkBalanceWithResults() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> balanceChecks = new ArrayList<>();
        boolean hasInsufficientBalance = false;
        
        try {
            // Note: Balance check now works in all profiles (live, local, etc.)
            
            // Skip balance check if there are active orders
            if (activeOrderTrackingService.hasActiveOrder()) {
                result.put("success", true);
                result.put("message", "Balance check skipped - active orders present");
                result.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss")));
                result.put("hasInsufficientBalance", false);
                result.put("balanceChecks", balanceChecks);
                result.put("skippedDueToActiveOrders", true);
                return result;
            }
            
            // Check balance for both CALL and PUT options
            Map<String, Object> callResult = checkBalanceForOrderTypeWithResults("CALL_BUY");
            Map<String, Object> putResult = checkBalanceForOrderTypeWithResults("PUT_BUY");
            
            balanceChecks.add(callResult);
            balanceChecks.add(putResult);
            
            // Check if any order type has insufficient balance
            if ((Boolean) callResult.get("insufficientBalance") || (Boolean) putResult.get("insufficientBalance")) {
                hasInsufficientBalance = true;
            }
            
            result.put("success", true);
            result.put("message", "Balance check completed successfully");
            result.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss")));
            result.put("hasInsufficientBalance", hasInsufficientBalance);
            result.put("balanceChecks", balanceChecks);
            result.put("skippedDueToActiveOrders", false);
            
            // Get overall current balance
            try {
                double currentBalance = getCurrentBalance();
                result.put("currentBalance", currentBalance);
            } catch (KiteException e) {
                log.error("❌ BALANCE INFO - Error fetching current balance: {}", e.getMessage(), e);
                result.put("currentBalance", 0.0);
            }
            
        } catch (Exception e) {
            log.error("❌ BALANCE CHECK - Error in balance tracking: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "Error during balance check: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Check balance for a specific order type and return results
     */
    private Map<String, Object> checkBalanceForOrderTypeWithResults(String orderType) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            result.put("orderType", orderType);
            
            // Get live option pricing information
            Optional<LiveOptionPricingService.LiveOptionPricingInfo> pricingInfo = 
                liveOptionPricingService.getLiveOptionPricing(orderType);
            
            if (pricingInfo.isEmpty()) {
                result.put("success", false);
                result.put("message", "No Live pricing information available");
                result.put("insufficientBalance", false);
                return result;
            }
            
            LiveOptionPricingService.LiveOptionPricingInfo info = pricingInfo.get();
            double optionLTP = info.getOptionLTP();
            
            // Calculate required amount using dynamic quantity calculation
            int dynamicQuantity = dynamicQuantityService.calculateDynamicQuantity(orderType);
            int minQuantity = dynamicQuantityService.getMinQuantity();
            double minRequiredAmount = optionLTP * minQuantity;
            
            // Get current balance from KiteConnect
            double currentBalance = getCurrentBalance();
            
            boolean insufficientBalance = currentBalance < minRequiredAmount;
            double shortfall = insufficientBalance ? (minRequiredAmount - currentBalance) : 0;
            double excess = !insufficientBalance ? (currentBalance - minRequiredAmount) : 0;
            
            // Calculate eligible quantity (maximum quantity that can be purchased with available balance)
            int eligibleQuantity = (int) Math.floor(currentBalance / optionLTP);
            int eligibleValidQuantity = eligibleQuantity - (eligibleQuantity % minQuantity);

            result.put("success", true);
            result.put("insufficientBalance", insufficientBalance);
            result.put("optionDetails", Map.of(
                "tradingSymbol", info.getOptionInstrument().getTradingSymbol(),
                "optionLTP", optionLTP,
                "strikePrice", info.getStrikePrice(),
                "niftyIndexPrice", info.getNiftyIndexPrice(),
                "optionType", info.getOptionType()
            ));
            result.put("balanceDetails", Map.of(
                "requiredAmount", minRequiredAmount,
                "currentBalance", currentBalance,
                "shortfall", shortfall,
                "excess", excess,
                "dynamicQuantity", dynamicQuantity,
                "eligibleQuantity", eligibleQuantity,
                "eligibleValidQuantity", eligibleValidQuantity
            ));
            
            if (insufficientBalance) {
                result.put("message", String.format("Insufficient balance: Min Required ₹%.2f, Available ₹%.2f, Shortfall ₹%.2f, Eligible Qty: %d",
                    minRequiredAmount, currentBalance, shortfall, eligibleQuantity));
                
                // Send email notification for insufficient balance
                sendInsufficientBalanceNotification(orderType, minRequiredAmount, currentBalance, shortfall, info, dynamicQuantity);
            } else {
                result.put("message", String.format("Sufficient balance: Min Required ₹%.2f, Available ₹%.2f, Excess ₹%.2f, Eligible Qty: %d",
                    minRequiredAmount, currentBalance, excess, eligibleQuantity));
            }
            
        } catch (KiteException e) {
            log.error("❌ BALANCE CHECK - KiteException checking balance for order type {}: {}", orderType, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "Kite API error: " + e.getMessage());
            result.put("insufficientBalance", false);
        } catch (Exception e) {
            log.error("❌ BALANCE CHECK - Error checking balance for order type {}: {}", orderType, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "Error: " + e.getMessage());
            result.put("insufficientBalance", false);
        }
        
        return result;
    }
    
    /**
     * Send email notification when insufficient balance is detected
     */
    private void sendInsufficientBalanceNotification(String orderType, double requiredAmount, 
                                                    double currentBalance, double shortfall, 
                                                    LiveOptionPricingService.LiveOptionPricingInfo pricingInfo, int dynamicQuantity) {
        try {
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss"));
            
            String subject = "⚠️ INSUFFICIENT BALANCE ALERT - " + orderType;
            
            String message = String.format("""
                🚨 INSUFFICIENT BALANCE DETECTED
                
                ⏰ Time: %s IST
                📊 Order Type: %s
                
                💰 BALANCE DETAILS:
                • Required Amount: ₹%.2f
                • Available Balance: ₹%.2f
                • Shortfall: ₹%.2f
                
                📈 OPTION DETAILS:
                • Trading Symbol: %s
                • Option LTP: ₹%.2f
                • Strike Price: %d
                • Nifty Index Price: ₹%.2f
                • Option Type: %s
                • Dynamic Quantity: %d lots
                
                ⚠️ ACTION REQUIRED:
                Please add ₹%.2f to your trading account to continue trading %s options.
                
                🔗 Check your Kite Connect account for more details.
                """, 
                currentTime,
                orderType,
                requiredAmount,
                currentBalance,
                shortfall,
                pricingInfo.getOptionInstrument().getTradingSymbol(),
                pricingInfo.getOptionLTP(),
                pricingInfo.getStrikePrice(),
                pricingInfo.getNiftyIndexPrice(),
                pricingInfo.getOptionType(),
                dynamicQuantity,
                shortfall,
                orderType
            );
            
            snsEmailService.sendEmail(subject, message);
            log.info("📧 Insufficient balance notification sent for order type: {}", orderType);
            
        } catch (Exception e) {
            log.error("❌ Failed to send insufficient balance notification for order type {}: {}", orderType, e.getMessage(), e);
        }
    }
}
