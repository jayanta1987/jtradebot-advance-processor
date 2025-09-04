package com.jtradebot.processor.service.notification;

import com.jtradebot.processor.model.enums.ExitReasonEnum;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.repository.document.JtradeOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Integration test to verify that notifications are actually sent via SNS
 * This test will send real emails - use with caution!
 * 
 * To run this test:
 * 1. Make sure your AWS credentials are configured
 * 2. Ensure the SNS topic ARN is correct in application.yml
 * 3. Run: mvn test -Dtest=OrderNotificationIntegrationTest
 */
@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "aws.sns.topic-arn=arn:aws:sns:ap-south-1:692859939395:jtradebot-notifications"
})
class OrderNotificationIntegrationTest {

    @Autowired
    private OrderNotificationService orderNotificationService;

    @Test
    void testSendRealOrderCreationNotification() {
        // Create a test order
        JtradeOrder order = createTestOrder();
        
        System.out.println("🚀 Sending REAL order creation notification...");
        System.out.println("📧 Check your email for notification!");
        
        // Send the notification
        orderNotificationService.sendOrderCreationNotification(order);
        
        System.out.println("✅ Order creation notification sent successfully!");
        System.out.println("📋 Order Details:");
        System.out.println("   - ID: " + order.getId());
        System.out.println("   - Symbol: " + order.getTradingSymbol());
        System.out.println("   - Type: " + order.getOrderType());
        System.out.println("   - Entry Price: ₹" + order.getEntryPrice());
        System.out.println("   - Stop Loss: ₹" + order.getStopLossPrice());
        System.out.println("   - Target: ₹" + order.getTargetPrice());
        
        // Wait a bit for the email to be processed
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void testSendRealOrderExitNotification() {
        // Create a test order with exit information
        JtradeOrder order = createTestOrder();
        order.setExitTime("15 Dec 2024 15:30:00 IST");
        order.setStatus("EXITED");
        order.setTotalPoints(15.0);
        order.setTotalProfit(1500.0);
        order.setMinIndexPrice(19350.0);
        order.setMaxIndexPrice(19550.0);
        
        System.out.println("🏁 Sending REAL order exit notification...");
        System.out.println("📧 Check your email for notification!");
        
        // Send the notification
        orderNotificationService.sendOrderExitNotification(
            order, 
            ExitReasonEnum.TARGET_HIT, 
            115.0, // Exit price
            19550.0 // Exit index price
        );
        
        System.out.println("✅ Order exit notification sent successfully!");
        System.out.println("📋 Exit Details:");
        System.out.println("   - Exit Reason: TARGET_HIT");
        System.out.println("   - Exit Price: ₹115.00");
        System.out.println("   - Points: " + order.getTotalPoints());
        System.out.println("   - P&L: ₹" + order.getTotalProfit());
        
        // Wait a bit for the email to be processed
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void testSendRealOrderExitNotificationWithLoss() {
        // Create a test order with loss exit information
        JtradeOrder order = createTestOrder();
        order.setExitTime("15 Dec 2024 15:15:00 IST");
        order.setStatus("EXITED");
        order.setTotalPoints(-8.0);
        order.setTotalProfit(-800.0);
        order.setMinIndexPrice(19200.0);
        order.setMaxIndexPrice(19400.0);
        
        System.out.println("💸 Sending REAL order exit notification (LOSS)...");
        System.out.println("📧 Check your email for notification!");
        
        // Send the notification
        orderNotificationService.sendOrderExitNotification(
            order, 
            ExitReasonEnum.STOPLOSS_HIT, 
            92.0, // Exit price (stop loss hit)
            19200.0 // Exit index price
        );
        
        System.out.println("✅ Order exit notification (LOSS) sent successfully!");
        System.out.println("📋 Exit Details:");
        System.out.println("   - Exit Reason: STOPLOSS_HIT");
        System.out.println("   - Exit Price: ₹92.00");
        System.out.println("   - Points: " + order.getTotalPoints());
        System.out.println("   - P&L: ₹" + order.getTotalProfit());
        
        // Wait a bit for the email to be processed
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Create a test order for integration testing
     */
    private JtradeOrder createTestOrder() {
        JtradeOrder order = new JtradeOrder();
        order.setId("integration-test-" + System.currentTimeMillis());
        order.setOrderType(OrderTypeEnum.CALL_BUY);
        order.setTradingSymbol("NIFTY24DEC19500CE");
        order.setQuantity(100);
        order.setEntryPrice(100.0);
        order.setEntryIndexPrice(19400.0);
        order.setStopLossPrice(92.0);
        order.setTargetPrice(115.0);
        order.setEntryTime("15 Dec 2024 14:00:00 IST");
        order.setStatus("ACTIVE");
        order.setCreatedAt("15 Dec 2024 14:00:00 IST");
        order.setLastUpdated("15 Dec 2024 14:00:00 IST");
        
        // Scenario information
        order.setEntryScenarioName("Integration Test Strategy");
        order.setEntryScenarioDescription("Test notification system integration");
        order.setEntryScenarioConfidence(9.0);
        
        // Category scores
        Map<String, Double> categoryScores = new HashMap<>();
        categoryScores.put("Volume Analysis", 95.0);
        categoryScores.put("Price Action", 88.0);
        categoryScores.put("Market Sentiment", 92.0);
        order.setEntryCategoryScores(categoryScores);
        
        // Matched conditions
        Map<String, List<String>> matchedConditions = new HashMap<>();
        matchedConditions.put("Volume Analysis", List.of("Volume surge detected", "Above average volume"));
        matchedConditions.put("Price Action", List.of("Bullish candle pattern", "Price above moving average"));
        matchedConditions.put("Market Sentiment", List.of("Positive momentum", "Support level respected"));
        order.setEntryMatchedConditions(matchedConditions);
        
        // Market condition
        order.setEntryMarketConditionSuitable(true);
        
        return order;
    }
}
