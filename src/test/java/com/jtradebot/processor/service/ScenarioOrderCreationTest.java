package com.jtradebot.processor.service;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.repository.document.JtradeOrder;
import com.jtradebot.processor.repository.JtradeOrderRepository;
import com.jtradebot.processor.service.OptionPricingService;
import com.jtradebot.processor.service.ScalpingVolumeSurgeService;
import com.jtradebot.processor.service.impl.ExitStrategyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScenarioOrderCreationTest {
    
    @Mock
    private JtradeOrderRepository jtradeOrderRepository;
    
    @Mock
    private OptionPricingService optionPricingService;
    
    @Mock
    private DynamicStrategyConfigService configService;
    
    @Mock
    private ScalpingVolumeSurgeService scalpingVolumeSurgeService;
    
    private ExitStrategyServiceImpl exitStrategyService;
    
    @BeforeEach
    void setUp() {
        exitStrategyService = new ExitStrategyServiceImpl(
            jtradeOrderRepository, 
            optionPricingService, 
            configService, 
            scalpingVolumeSurgeService
        );
    }
    
    @Test
    void testCreateOrderWithScenarioInformation() {
        // Given
        String scenarioName = "HIGH_QUALITY_SCORE_SIGNAL";
        String scenarioDescription = "Only depends on QualityScore";
        Double scenarioConfidence = 8.5;
        
        Map<String, Integer> categoryScores = new HashMap<>();
        categoryScores.put("ema", 2);
        categoryScores.put("futureAndVolume", 3);
        categoryScores.put("candlestick", 1);
        categoryScores.put("momentum", 2);
        
        Map<String, List<String>> matchedConditions = new HashMap<>();
        matchedConditions.put("ema", List.of("ema5_5min_gt_ema34_5min", "ema5_1min_gt_ema34_1min"));
        matchedConditions.put("futureAndVolume", List.of("volume_5min_surge", "price_gt_vwap_5min", "price_above_resistance"));
        
        // When
        JtradeOrder order = exitStrategyService.createOrderEntryWithScenario(
            OrderTypeEnum.CALL_BUY,
            "TEST_OPTION_CE",
            12345L,
            100.0, // entryPrice
            24750.0, // entryIndexPrice
            90.0, // stopLossPrice
            120.0, // targetPrice
            75, // quantity
            scenarioName,
            scenarioDescription,
            scenarioConfidence,
            categoryScores,
            matchedConditions,
            new Date() // entryTime
        );
        
        // Then
        assertNotNull(order);
        assertEquals("ACTIVE", order.getStatus());
        assertEquals(OrderTypeEnum.CALL_BUY, order.getOrderType());
        assertEquals("TEST_OPTION_CE", order.getTradingSymbol());
        assertEquals(12345L, order.getInstrumentToken());
        assertEquals(100.0, order.getEntryPrice());
        assertEquals(24750.0, order.getEntryIndexPrice());
        assertEquals(90.0, order.getStopLossPrice());
        assertEquals(120.0, order.getTargetPrice());
        assertEquals(75, order.getQuantity());
        
        // Verify scenario information is stored
        assertEquals(scenarioName, order.getEntryScenarioName());
        assertEquals(scenarioDescription, order.getEntryScenarioDescription());
        assertEquals(scenarioConfidence, order.getEntryScenarioConfidence());
        assertEquals(categoryScores, order.getEntryCategoryScores());
        assertEquals(matchedConditions, order.getEntryMatchedConditions());
        
        // Verify order is in active orders
        assertTrue(exitStrategyService.hasActiveOrder());
        List<JtradeOrder> activeOrders = exitStrategyService.getActiveOrders();
        assertEquals(1, activeOrders.size());
        assertEquals(order.getId(), activeOrders.get(0).getId());
    }
    
    @Test
    void testCreateOrderWithoutScenarioInformation() {
        // Given - no scenario information
        
        // When
        JtradeOrder order = exitStrategyService.createOrderEntry(
            OrderTypeEnum.PUT_BUY,
            "TEST_OPTION_PE",
            12345L,
            80.0, // entryPrice
            24750.0, // entryIndexPrice
            70.0, // stopLossPrice
            100.0, // targetPrice
            75, // quantity
            new Date() // entryTime
        );
        
        // Then
        assertNotNull(order);
        assertEquals("ACTIVE", order.getStatus());
        assertEquals(OrderTypeEnum.PUT_BUY, order.getOrderType());
        assertEquals("TEST_OPTION_PE", order.getTradingSymbol());
        assertEquals(12345L, order.getInstrumentToken());
        assertEquals(80.0, order.getEntryPrice());
        assertEquals(24750.0, order.getEntryIndexPrice());
        assertEquals(70.0, order.getStopLossPrice());
        assertEquals(100.0, order.getTargetPrice());
        assertEquals(75, order.getQuantity());
        
        // Verify scenario information is null (not set)
        assertNull(order.getEntryScenarioName());
        assertNull(order.getEntryScenarioDescription());
        assertNull(order.getEntryScenarioConfidence());
        assertNull(order.getEntryCategoryScores());
        assertNull(order.getEntryMatchedConditions());
    }
}
