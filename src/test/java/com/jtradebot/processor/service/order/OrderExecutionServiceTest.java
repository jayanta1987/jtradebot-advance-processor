package com.jtradebot.processor.service.order;

import com.jtradebot.processor.repository.document.JtradeOrder;
import com.jtradebot.processor.service.entry.UnstableMarketConditionAnalysisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class OrderExecutionServiceTest {

    @Mock
    private UnstableMarketConditionAnalysisService unstableMarketConditionAnalysisService;

    @Test
    void testFilterFailureReasonListStructure() {
        // Given: Create a JtradeOrder instance
        JtradeOrder order = new JtradeOrder();
        
        // When: Set filter failure reason as a list
        List<String> failedFilters = List.of(
            "Trading Hours Filter (NTP: 3)",
            "Volume Surge Multiplier (NTP: 1)",
            "Candle Body Ratio (NTP: 1)"
        );
        order.setFilterFailureReason(failedFilters);
        
        // Then: Verify the list structure
        assertNotNull(order.getFilterFailureReason());
        assertEquals(3, order.getFilterFailureReason().size());
        assertTrue(order.getFilterFailureReason().contains("Trading Hours Filter (NTP: 3)"));
        assertTrue(order.getFilterFailureReason().contains("Volume Surge Multiplier (NTP: 1)"));
        assertTrue(order.getFilterFailureReason().contains("Candle Body Ratio (NTP: 1)"));
        
        // Verify NTP values are included in the strings
        order.getFilterFailureReason().forEach(reason -> {
            assertTrue(reason.contains("(NTP: "));
            assertTrue(reason.contains(")"));
        });
    }

    @Test
    void testFilterFailureReasonEmptyList() {
        // Given: Create a JtradeOrder instance
        JtradeOrder order = new JtradeOrder();
        
        // When: Set empty filter failure reason list
        List<String> emptyList = List.of();
        order.setFilterFailureReason(emptyList);
        
        // Then: Verify empty list is handled correctly
        assertNotNull(order.getFilterFailureReason());
        assertEquals(0, order.getFilterFailureReason().size());
    }

    @Test
    void testFilterFailureReasonNullHandling() {
        // Given: Create a JtradeOrder instance
        JtradeOrder order = new JtradeOrder();
        
        // When: Set null filter failure reason
        order.setFilterFailureReason(null);
        
        // Then: Verify null is handled correctly
        assertNull(order.getFilterFailureReason());
    }
}
