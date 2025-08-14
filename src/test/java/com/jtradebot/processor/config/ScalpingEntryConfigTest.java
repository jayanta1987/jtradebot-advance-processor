package com.jtradebot.processor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtradebot.processor.model.ScalpingEntryConfig;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.*;

public class ScalpingEntryConfigTest {

    @Test
    public void testScalpingEntryConfigDeserialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ClassPathResource resource = new ClassPathResource("rules/scalping-entry-config.json");
        
        ScalpingEntryConfig config = mapper.readValue(resource.getInputStream(), ScalpingEntryConfig.class);
        
        // Verify basic fields
        assertNotNull(config);
        assertEquals("SCALPING_ENTRY_LOGIC", config.getStrategy());
        assertEquals("1.0", config.getVersion());
        assertNotNull(config.getDescription());
        
        // Verify call strategy
        assertNotNull(config.getCallStrategy());
        assertEquals("CALL", config.getCallStrategy().getStrategyType());
        assertNotNull(config.getCallStrategy().getEntryConditions());
        assertNotNull(config.getCallStrategy().getRiskManagement());
        assertNotNull(config.getCallStrategy().getEntryQuality());
        assertNotNull(config.getCallStrategy().getMarketConditions());
        
        // Verify put strategy
        assertNotNull(config.getPutStrategy());
        assertEquals("PUT", config.getPutStrategy().getStrategyType());
        assertNotNull(config.getPutStrategy().getEntryConditions());
        assertNotNull(config.getPutStrategy().getRiskManagement());
        assertNotNull(config.getPutStrategy().getEntryQuality());
        assertNotNull(config.getPutStrategy().getMarketConditions());
        
        // Verify common settings
        assertNotNull(config.getCommonSettings());
        assertTrue(config.getCommonSettings().getAccountBalance() > 0);
        assertTrue(config.getCommonSettings().getLotSize() > 0);
        
        // Verify quality grades
        assertNotNull(config.getQualityGrades());
        assertFalse(config.getQualityGrades().isEmpty());
        assertTrue(config.getQualityGrades().containsKey("A"));
        assertTrue(config.getQualityGrades().containsKey("B"));
        assertTrue(config.getQualityGrades().containsKey("C"));
        assertTrue(config.getQualityGrades().containsKey("D"));
        assertTrue(config.getQualityGrades().containsKey("F"));
        
        // Verify market sessions
        assertNotNull(config.getMarketSessions());
        assertFalse(config.getMarketSessions().isEmpty());
        assertTrue(config.getMarketSessions().containsKey("OPENING"));
        assertTrue(config.getMarketSessions().containsKey("MID"));
        assertTrue(config.getMarketSessions().containsKey("CLOSING"));
        
        System.out.println("✅ ScalpingEntryConfig deserialization test passed!");
        System.out.println("Strategy: " + config.getStrategy());
        System.out.println("Version: " + config.getVersion());
        System.out.println("Call Strategy Type: " + config.getCallStrategy().getStrategyType());
        System.out.println("Put Strategy Type: " + config.getPutStrategy().getStrategyType());
        System.out.println("Call Mandatory Conditions: " + config.getCallStrategy().getEntryConditions().getMandatoryConditions().size());
        System.out.println("Put Mandatory Conditions: " + config.getPutStrategy().getEntryConditions().getMandatoryConditions().size());
        System.out.println("Quality Grades: " + config.getQualityGrades().size());
        System.out.println("Market Sessions: " + config.getMarketSessions().size());
    }
}
