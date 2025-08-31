package com.jtradebot.processor.indicator;

import com.jtradebot.processor.model.indicator.Resistance;
import com.jtradebot.processor.model.indicator.Support;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SupportResistanceIndicatorTest {

    @InjectMocks
    private SupportResistanceIndicator supportResistanceIndicator;

    private Set<Support> supportLevels;
    private Set<Resistance> resistanceLevels;

    @BeforeEach
    void setUp() {
        supportLevels = new HashSet<>();
        resistanceLevels = new HashSet<>();
    }

    @Test
    void testIsVeryNearSupportResistance_WhenNearSupport_ShouldReturnTrue() {
        // Given
        Support support = new Support();
        support.setSupportValue(24500);
        supportLevels.add(support);
        
        double currentPrice = 24503; // Within 5 points of support
        
        // When
        boolean result = supportResistanceIndicator.isVeryNearSupportResistance(currentPrice, supportLevels, resistanceLevels);
        
        // Then
        assertTrue(result, "Should return true when price is very near support level");
    }

    @Test
    void testIsVeryNearSupportResistance_WhenNearResistance_ShouldReturnTrue() {
        // Given
        Resistance resistance = new Resistance();
        resistance.setResistanceValue(25000);
        resistanceLevels.add(resistance);
        
        double currentPrice = 24997; // Within 5 points of resistance
        
        // When
        boolean result = supportResistanceIndicator.isVeryNearSupportResistance(currentPrice, supportLevels, resistanceLevels);
        
        // Then
        assertTrue(result, "Should return true when price is very near resistance level");
    }

    @Test
    void testIsVeryNearSupportResistance_WhenNotNear_ShouldReturnFalse() {
        // Given
        Support support = new Support();
        support.setSupportValue(24500);
        supportLevels.add(support);
        
        double currentPrice = 24510; // More than 5 points away from support
        
        // When
        boolean result = supportResistanceIndicator.isVeryNearSupportResistance(currentPrice, supportLevels, resistanceLevels);
        
        // Then
        assertFalse(result, "Should return false when price is not near support/resistance levels");
    }

    @Test
    void testIsNearRoundFigureLevel_WhenNearRoundFigure_ShouldReturnTrue() {
        // Given
        double currentPrice = 24505; // Near 24500 round figure (within 10 points)
        
        // When
        boolean result = supportResistanceIndicator.isNearRoundFigureLevel(currentPrice);
        
        // Then
        assertTrue(result, "Should return true when price is near round figure level");
    }

    @Test
    void testIsNearRoundFigureLevel_WhenNotNearRoundFigure_ShouldReturnFalse() {
        // Given
        double currentPrice = 24520; // Not near any round figure (more than 10 points from 24500)
        
        // When
        boolean result = supportResistanceIndicator.isNearRoundFigureLevel(currentPrice);
        
        // Then
        assertFalse(result, "Should return false when price is not near round figure level");
    }

    @Test
    void testIsNearRoundFigureLevel_WithDifferentRoundFigures_ShouldWorkCorrectly() {
        // Given & When & Then
        // Test various round figure levels
        assertTrue(supportResistanceIndicator.isNearRoundFigureLevel(25005), "Should detect near 25000");
        assertTrue(supportResistanceIndicator.isNearRoundFigureLevel(24995), "Should detect near 25000");
        assertTrue(supportResistanceIndicator.isNearRoundFigureLevel(24005), "Should detect near 24000");
        assertTrue(supportResistanceIndicator.isNearRoundFigureLevel(23995), "Should detect near 24000");
        assertFalse(supportResistanceIndicator.isNearRoundFigureLevel(25015), "Should not detect when too far from 25000");
        assertFalse(supportResistanceIndicator.isNearRoundFigureLevel(24985), "Should not detect when too far from 25000");
    }

    @Test
    void testIsNearSupportResistanceOrRoundFigure_WhenNearSupport_ShouldReturnTrue() {
        // Given
        Support support = new Support();
        support.setSupportValue(24500);
        supportLevels.add(support);
        
        double currentPrice = 24503; // Near support
        
        // When
        boolean result = supportResistanceIndicator.isNearSupportResistanceOrRoundFigure(currentPrice, supportLevels, resistanceLevels);
        
        // Then
        assertTrue(result, "Should return true when price is near support level");
    }

    @Test
    void testIsNearSupportResistanceOrRoundFigure_WhenNearRoundFigure_ShouldReturnTrue() {
        // Given
        double currentPrice = 24505; // Near round figure
        
        // When
        boolean result = supportResistanceIndicator.isNearSupportResistanceOrRoundFigure(currentPrice, supportLevels, resistanceLevels);
        
        // Then
        assertTrue(result, "Should return true when price is near round figure level");
    }

    @Test
    void testIsNearSupportResistanceOrRoundFigure_WhenNotInAnyZone_ShouldReturnFalse() {
        // Given
        double currentPrice = 24520; // Not near support/resistance or round figures
        
        // When
        boolean result = supportResistanceIndicator.isNearSupportResistanceOrRoundFigure(currentPrice, supportLevels, resistanceLevels);
        
        // Then
        assertFalse(result, "Should return false when price is not near support/resistance or round figures");
    }
}
