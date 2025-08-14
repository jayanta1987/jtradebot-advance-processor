package com.jtradebot.processor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtradebot.processor.model.DynamicIndicatorConfig;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.*;

public class DynamicIndicatorConfigTest {

    @Test
    public void testDynamicIndicatorConfigDeserialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ClassPathResource resource = new ClassPathResource("rules/dynamic-indicators-config.json");
        
        DynamicIndicatorConfig config = mapper.readValue(resource.getInputStream(), DynamicIndicatorConfig.class);
        
        // Verify basic fields
        assertNotNull(config);
        assertEquals("DYNAMIC_SCALPING_VOLUME_SURGE", config.getStrategy());
        assertEquals("2.0", config.getVersion());
        assertNotNull(config.getDescription());
        
        // Verify indicators
        assertNotNull(config.getIndicators());
        assertFalse(config.getIndicators().isEmpty());
        
        // Verify timeframes
        assertNotNull(config.getTimeframes());
        assertEquals(3, config.getTimeframes().size());
        assertTrue(config.getTimeframes().contains("1min"));
        assertTrue(config.getTimeframes().contains("5min"));
        assertTrue(config.getTimeframes().contains("15min"));
        
        // Verify thresholds
        assertNotNull(config.getThresholds());
        
        // Verify call conditions
        assertNotNull(config.getCallConditions());
        assertNotNull(config.getCallConditions().getRequiredIndicators());
        assertFalse(config.getCallConditions().getRequiredIndicators().isEmpty());
        assertTrue(config.getCallConditions().getMinRequiredCount() > 0);
        assertNotNull(config.getCallConditions().getDescription());
        
        // Verify put conditions
        assertNotNull(config.getPutConditions());
        assertNotNull(config.getPutConditions().getRequiredIndicators());
        assertFalse(config.getPutConditions().getRequiredIndicators().isEmpty());
        assertTrue(config.getPutConditions().getMinRequiredCount() > 0);
        assertNotNull(config.getPutConditions().getDescription());
        
        System.out.println("✅ DynamicIndicatorConfig deserialization test passed!");
        System.out.println("Strategy: " + config.getStrategy());
        System.out.println("Version: " + config.getVersion());
        System.out.println("Call Conditions Count: " + config.getCallConditions().getRequiredIndicators().size());
        System.out.println("Put Conditions Count: " + config.getPutConditions().getRequiredIndicators().size());
    }
}
