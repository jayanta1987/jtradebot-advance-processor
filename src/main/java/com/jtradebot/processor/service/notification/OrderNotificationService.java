package com.jtradebot.processor.service.notification;

import com.jtradebot.processor.model.enums.ExitReasonEnum;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.repository.document.JtradeOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderNotificationService {

    private final SnsEmailService snsEmailService;

    /**
     * Send notification when a new order is created
     */
    public void sendOrderCreationNotification(JtradeOrder order) {
        try {
            String subject = "🚀 NEW TRADE ORDER CREATED";
            String message = buildOrderCreationMessage(order);
            
            snsEmailService.sendEmail(subject, message);
            log.info("📧 Order creation notification sent for order: {}", order.getId());
        } catch (Exception e) {
            log.error("Failed to send order creation notification for order: {}", order.getId(), e);
        }
    }

    /**
     * Send notification when an order is exited
     */
    public void sendOrderExitNotification(JtradeOrder order, ExitReasonEnum exitReason, Double exitPrice, Double exitIndexPrice) {
        try {
            String subject = "🏁 TRADE ORDER EXITED";
            String message = buildOrderExitMessage(order, exitReason, exitPrice, exitIndexPrice);
            
            snsEmailService.sendEmail(subject, message);
            log.info("📧 Order exit notification sent for order: {}", order.getId());
        } catch (Exception e) {
            log.error("Failed to send order exit notification for order: {}", order.getId(), e);
        }
    }



    /**
     * Build the message for order creation notification
     */
    private String buildOrderCreationMessage(JtradeOrder order) {
        StringBuilder message = new StringBuilder();
        
        message.append("🎯 NEW TRADE ORDER CREATED\n");
        message.append("========================\n\n");
        
        // Basic order details
        message.append("📊 ORDER DETAILS:\n");
        message.append("• Order ID: ").append(order.getId()).append("\n");
        message.append("• Type: ").append(order.getOrderType()).append("\n");
        message.append("• Symbol: ").append(order.getTradingSymbol()).append("\n");
        message.append("• Quantity: ").append(order.getQuantity()).append("\n");
        message.append("• Entry Price: ₹").append(String.format("%.2f", order.getEntryPrice())).append("\n");
        message.append("• Entry Index Price: ").append(String.format("%.2f", order.getEntryIndexPrice())).append("\n");
        message.append("• Stop Loss: ₹").append(String.format("%.2f", order.getStopLossPrice())).append("\n");
        message.append("• Target: ₹").append(String.format("%.2f", order.getTargetPrice())).append("\n");
        message.append("• Entry Time: ").append(order.getEntryTime()).append("\n");
        message.append("• Status: ").append(order.getStatus()).append("\n\n");
        
        // Scenario information
        if (order.getEntryScenarioName() != null) {
            message.append("🎭 SCENARIO INFORMATION:\n");
            message.append("• Name: ").append(order.getEntryScenarioName()).append("\n");
            message.append("• Description: ").append(order.getEntryScenarioDescription() != null ? order.getEntryScenarioDescription() : "N/A").append("\n");
            message.append("• Confidence: ").append(order.getEntryScenarioConfidence() != null ? order.getEntryScenarioConfidence() + "/10" : "N/A").append("\n\n");
        }
        
        // Category scores
        if (order.getEntryCategoryScores() != null && !order.getEntryCategoryScores().isEmpty()) {
            message.append("📈 CATEGORY SCORES:\n");
            order.getEntryCategoryScores().forEach((category, score) -> 
                message.append("• ").append(category).append(": ").append(score).append("\n"));
            message.append("\n");
        }
        
        // Matched conditions
        if (order.getEntryMatchedConditions() != null && !order.getEntryMatchedConditions().isEmpty()) {
            message.append("✅ MATCHED CONDITIONS:\n");
            order.getEntryMatchedConditions().forEach((category, conditions) -> {
                message.append("• ").append(category).append(":\n");
                conditions.forEach(condition -> message.append("  - ").append(condition).append("\n"));
            });
            message.append("\n");
        }
        
        // Market condition
        if (order.getEntryMarketConditionSuitable() != null) {
            message.append("🌍 MARKET CONDITION:\n");
            message.append("• Suitable for Entry: ").append(order.getEntryMarketConditionSuitable() ? "✅ YES" : "❌ NO").append("\n\n");
        }
        
        message.append("⏰ Notification sent at: ").append(getCurrentISTTime()).append("\n");
        
        return message.toString();
    }

    /**
     * Build the message for order exit notification
     */
    private String buildOrderExitMessage(JtradeOrder order, ExitReasonEnum exitReason, Double exitPrice, Double exitIndexPrice) {
        StringBuilder message = new StringBuilder();
        
        message.append("🏁 TRADE ORDER EXITED\n");
        message.append("===================\n\n");
        
        // Basic order details
        message.append("📊 ORDER DETAILS:\n");
        message.append("• Order ID: ").append(order.getId()).append("\n");
        message.append("• Type: ").append(order.getOrderType()).append("\n");
        message.append("• Symbol: ").append(order.getTradingSymbol()).append("\n");
        message.append("• Quantity: ").append(order.getQuantity()).append("\n");
        message.append("• Entry Price: ₹").append(String.format("%.2f", order.getEntryPrice())).append("\n");
        message.append("• Exit Price: ₹").append(String.format("%.2f", exitPrice)).append("\n");
        message.append("• Entry Index Price: ").append(String.format("%.2f", order.getEntryIndexPrice())).append("\n");
        message.append("• Exit Index Price: ").append(String.format("%.2f", exitIndexPrice)).append("\n");
        message.append("• Entry Time: ").append(order.getEntryTime()).append("\n");
        message.append("• Exit Time: ").append(order.getExitTime()).append("\n");
        message.append("• Status: ").append(order.getStatus()).append("\n\n");
        
        // Exit reason and P&L
        message.append("💸 EXIT INFORMATION:\n");
        message.append("• Exit Reason: ").append(exitReason).append("\n");
        message.append("• Points: ").append(String.format("%.2f", order.getTotalPoints() != null ? order.getTotalPoints() : 0.0)).append("\n");
        message.append("• Total P&L: ₹").append(String.format("%.2f", order.getTotalProfit() != null ? order.getTotalProfit() : 0.0)).append("\n");
        message.append("• P&L Type: ").append(getPnLType(order.getTotalProfit())).append("\n\n");
        
        // Duration
        if (order.getEntryTime() != null && order.getExitTime() != null) {
            long durationMinutes = calculateOrderDurationMinutes(order);
            message.append("⏱️ TRADE DURATION:\n");
            message.append("• Duration: ").append(durationMinutes).append(" minutes\n\n");
        }
        
        // Scenario information (if available)
        if (order.getEntryScenarioName() != null) {
            message.append("🎭 SCENARIO INFORMATION:\n");
            message.append("• Name: ").append(order.getEntryScenarioName()).append("\n");
            message.append("• Description: ").append(order.getEntryScenarioDescription() != null ? order.getEntryScenarioDescription() : "N/A").append("\n");
            message.append("• Confidence: ").append(order.getEntryScenarioConfidence() != null ? order.getEntryScenarioConfidence() + "/10" : "N/A").append("\n\n");
        }
        
        // Index price tracking
        if (order.getMinIndexPrice() != null && order.getMaxIndexPrice() != null) {
            message.append("📈 INDEX PRICE TRACKING:\n");
            message.append("• Min Index Price: ").append(String.format("%.2f", order.getMinIndexPrice())).append("\n");
            message.append("• Max Index Price: ").append(String.format("%.2f", order.getMaxIndexPrice())).append("\n");
            message.append("• Index Range: ").append(String.format("%.2f", order.getMaxIndexPrice() - order.getMinIndexPrice())).append(" points\n\n");
        }
        
        message.append("⏰ Notification sent at: ").append(getCurrentISTTime()).append("\n");
        
        return message.toString();
    }



    /**
     * Get the current IST time as a formatted string
     */
    private String getCurrentISTTime() {
        ZonedDateTime istTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        return istTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss z"));
    }

    /**
     * Calculate order duration in minutes
     */
    private long calculateOrderDurationMinutes(JtradeOrder order) {
        try {
            if (order.getEntryTime() == null || order.getExitTime() == null) {
                return 0;
            }
            
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss 'IST'");
            ZonedDateTime entryTime = LocalDateTime.parse(order.getEntryTime(), formatter).atZone(ZoneId.of("Asia/Kolkata"));
            ZonedDateTime exitTime = LocalDateTime.parse(order.getExitTime(), formatter).atZone(ZoneId.of("Asia/Kolkata"));
            
            return java.time.Duration.between(entryTime, exitTime).toMinutes();
        } catch (Exception e) {
            log.warn("Could not calculate order duration for order: {}", order.getId(), e);
            return 0;
        }
    }

    /**
     * Get P&L type based on profit/loss value
     */
    private String getPnLType(Double totalProfit) {
        if (totalProfit == null) return "UNKNOWN";
        if (totalProfit > 0) return "✅ PROFIT";
        if (totalProfit < 0) return "❌ LOSS";
        return "➖ BREAKEVEN";
    }
}
