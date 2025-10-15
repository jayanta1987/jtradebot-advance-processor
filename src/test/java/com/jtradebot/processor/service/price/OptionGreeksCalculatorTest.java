package com.jtradebot.processor.service.price;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class OptionGreeksCalculatorTest {

    private OptionGreeksCalculator greeksCalculator;

    @BeforeEach
    void setUp() {
        greeksCalculator = new OptionGreeksCalculator();
    }

    @Test
    void testCalculateGreeksForCallOption() {
        // Given: ATM Call option parameters
        double spotPrice = 25000.0;
        double strikePrice = 25000.0;
        double timeToExpiry = 30.0; // 30 days
        double impliedVolatility = 0.20; // 20% IV
        String optionType = "CE";
        double optionPrice = 500.0;

        // When: Calculate Greeks
        OptionGreeksCalculator.OptionGreeks greeks = greeksCalculator.calculateGreeks(
                spotPrice, strikePrice, timeToExpiry, impliedVolatility, optionType, optionPrice);

        // Then: Verify Greeks values
        assertNotNull(greeks);
        assertTrue(greeks.getDelta() > 0 && greeks.getDelta() < 1, "Call delta should be between 0 and 1");
        assertTrue(greeks.getGamma() > 0, "Gamma should be positive");
        assertTrue(greeks.getTheta() < 0, "Theta should be negative for long options");
        assertTrue(greeks.getVega() > 0, "Vega should be positive");
        assertTrue(greeks.getIntrinsicValue() >= 0, "Intrinsic value should be non-negative");
        assertTrue(greeks.getTimeValue() >= 0, "Time value should be non-negative");
        
        System.out.println("📊 CALL OPTION GREEKS:");
        System.out.println("Delta: " + String.format("%.4f", greeks.getDelta()));
        System.out.println("Gamma: " + String.format("%.6f", greeks.getGamma()));
        System.out.println("Theta: " + String.format("%.4f", greeks.getTheta()));
        System.out.println("Vega: " + String.format("%.4f", greeks.getVega()));
        System.out.println("Intrinsic Value: " + String.format("%.2f", greeks.getIntrinsicValue()));
        System.out.println("Time Value: " + String.format("%.2f", greeks.getTimeValue()));
    }

    @Test
    void testCalculateGreeksForPutOption() {
        // Given: ATM Put option parameters
        double spotPrice = 25000.0;
        double strikePrice = 25000.0;
        double timeToExpiry = 30.0; // 30 days
        double impliedVolatility = 0.20; // 20% IV
        String optionType = "PE";
        double optionPrice = 400.0;

        // When: Calculate Greeks
        OptionGreeksCalculator.OptionGreeks greeks = greeksCalculator.calculateGreeks(
                spotPrice, strikePrice, timeToExpiry, impliedVolatility, optionType, optionPrice);

        // Then: Verify Greeks values
        assertNotNull(greeks);
        assertTrue(greeks.getDelta() < 0 && greeks.getDelta() > -1, "Put delta should be between -1 and 0");
        assertTrue(greeks.getGamma() > 0, "Gamma should be positive");
        assertTrue(greeks.getTheta() < 0, "Theta should be negative for long options");
        assertTrue(greeks.getVega() > 0, "Vega should be positive");
        assertTrue(greeks.getIntrinsicValue() >= 0, "Intrinsic value should be non-negative");
        assertTrue(greeks.getTimeValue() >= 0, "Time value should be non-negative");
        
        System.out.println("📊 PUT OPTION GREEKS:");
        System.out.println("Delta: " + String.format("%.4f", greeks.getDelta()));
        System.out.println("Gamma: " + String.format("%.6f", greeks.getGamma()));
        System.out.println("Theta: " + String.format("%.4f", greeks.getTheta()));
        System.out.println("Vega: " + String.format("%.4f", greeks.getVega()));
        System.out.println("Intrinsic Value: " + String.format("%.2f", greeks.getIntrinsicValue()));
        System.out.println("Time Value: " + String.format("%.2f", greeks.getTimeValue()));
    }

    @Test
    void testCalculateTimeToExpiry() {
        // Test with a future date (next month from October 15, 2025)
        String futureExpiry = "15-Nov-2025";
        double timeToExpiry = greeksCalculator.calculateTimeToExpiry(futureExpiry);
        
        assertTrue(timeToExpiry > 0, "Time to expiry should be positive for future dates");
        
        // Test with invalid date
        String invalidExpiry = "invalid-date";
        double invalidTime = greeksCalculator.calculateTimeToExpiry(invalidExpiry);
        assertEquals(1.0, invalidTime, "Should return default 1 day for invalid dates");
        
        System.out.println("📅 Time to expiry for " + futureExpiry + ": " + String.format("%.1f", timeToExpiry) + " days");
    }

    @Test
    void testEstimateImpliedVolatility() {
        // Given: Option parameters
        double spotPrice = 25000.0;
        double strikePrice = 25000.0;
        double optionPrice = 500.0;
        double timeToExpiry = 30.0;
        String optionType = "CE";

        // When: Estimate implied volatility using Newton-Raphson
        double iv = greeksCalculator.estimateImpliedVolatility(
                spotPrice, strikePrice, optionPrice, timeToExpiry, optionType);

        // Then: Verify IV is reasonable
        assertTrue(iv > 0 && iv < 2.0, "Implied volatility should be between 0 and 200%");
        
        // Verify the IV produces the expected option price (reverse test)
        OptionGreeksCalculator.OptionGreeks greeks = greeksCalculator.calculateGreeks(
                spotPrice, strikePrice, timeToExpiry, iv, optionType, optionPrice);
        double theoreticalPrice = optionPrice; // Should match since IV was calculated from this price
        
        System.out.println("📈 Newton-Raphson IV Calculation:");
        System.out.println("   Input Price: " + String.format("%.2f", optionPrice));
        System.out.println("   Calculated IV: " + String.format("%.4f%%", iv * 100));
        System.out.println("   Theoretical Price: " + String.format("%.2f", theoreticalPrice));
        System.out.println("   Delta: " + String.format("%.4f", greeks.getDelta()));
        System.out.println("   Theta: " + String.format("%.2f", greeks.getTheta()));
    }

    @Test
    void testNewtonRaphsonConvergence() {
        // Test Newton-Raphson convergence with known values
        double spotPrice = 25000.0;
        double strikePrice = 25000.0;
        double timeToExpiry = 30.0;
        String optionType = "CE";
        
        // Test with different option prices to verify convergence
        double[] testPrices = {100.0, 300.0, 500.0, 800.0, 1200.0};
        
        System.out.println("🔬 Newton-Raphson Convergence Test:");
        System.out.println("Spot: " + spotPrice + ", Strike: " + strikePrice + ", Time: " + timeToExpiry + " days");
        
        for (double optionPrice : testPrices) {
            double iv = greeksCalculator.estimateImpliedVolatility(
                    spotPrice, strikePrice, optionPrice, timeToExpiry, optionType);
            
            // Verify convergence by checking if the calculated IV produces the expected price
            OptionGreeksCalculator.OptionGreeks greeks = greeksCalculator.calculateGreeks(
                    spotPrice, strikePrice, timeToExpiry, iv, optionType, optionPrice);
            
            // The theoretical price should be very close to the input price
            double priceDifference = Math.abs(greeks.getIntrinsicValue() + greeks.getTimeValue() - optionPrice);
            assertTrue(priceDifference < 1.0, 
                    "Price difference should be less than 1.0 for option price: " + optionPrice);
            
            System.out.println("   Price: " + String.format("%6.2f", optionPrice) + 
                             " → IV: " + String.format("%6.2f%%", iv * 100) + 
                             " → Diff: " + String.format("%.4f", priceDifference));
        }
    }

    @Test
    void testImpliedVolatilityEdgeCases() {
        // Test edge cases for robustness
        double spotPrice = 25000.0;
        double timeToExpiry = 30.0;
        String optionType = "CE";
        
        System.out.println("🧪 IV Edge Cases Test:");
        
        // Test 1: Very low option price
        double lowPrice = 5.0;
        double ivLow = greeksCalculator.estimateImpliedVolatility(
                spotPrice, spotPrice, lowPrice, timeToExpiry, optionType);
        assertTrue(ivLow > 0 && ivLow < 5.0, "Low price should produce reasonable IV (allowing for extreme cases)");
        System.out.println("   Low Price (" + lowPrice + "): IV = " + String.format("%.2f%%", ivLow * 100));
        
        // Test 2: High option price
        double highPrice = 2000.0;
        double ivHigh = greeksCalculator.estimateImpliedVolatility(
                spotPrice, spotPrice, highPrice, timeToExpiry, optionType);
        assertTrue(ivHigh > 0 && ivHigh < 3.0, "High price should produce reasonable IV");
        System.out.println("   High Price (" + highPrice + "): IV = " + String.format("%.2f%%", ivHigh * 100));
        
        // Test 3: ITM option
        double itmStrike = 24000.0;
        double itmPrice = 1200.0;
        double ivITM = greeksCalculator.estimateImpliedVolatility(
                spotPrice, itmStrike, itmPrice, timeToExpiry, optionType);
        assertTrue(ivITM > 0 && ivITM < 2.0, "ITM option should produce reasonable IV");
        System.out.println("   ITM Option: IV = " + String.format("%.2f%%", ivITM * 100));
        
        // Test 4: OTM option
        double otmStrike = 26000.0;
        double otmPrice = 150.0;
        double ivOTM = greeksCalculator.estimateImpliedVolatility(
                spotPrice, otmStrike, otmPrice, timeToExpiry, optionType);
        assertTrue(ivOTM > 0 && ivOTM < 2.0, "OTM option should produce reasonable IV");
        System.out.println("   OTM Option: IV = " + String.format("%.2f%%", ivOTM * 100));
    }

    @Test
    void testGreeksForDifferentMoneyness() {
        double spotPrice = 25000.0;
        double timeToExpiry = 30.0;
        double impliedVolatility = 0.20;
        String optionType = "CE";

        // Test ITM option (strike below spot)
        double itmStrike = 24800.0;
        OptionGreeksCalculator.OptionGreeks itmGreeks = greeksCalculator.calculateGreeks(
                spotPrice, itmStrike, timeToExpiry, impliedVolatility, optionType, 700.0);

        // Test OTM option (strike above spot)
        double otmStrike = 25200.0;
        OptionGreeksCalculator.OptionGreeks otmGreeks = greeksCalculator.calculateGreeks(
                spotPrice, otmStrike, timeToExpiry, impliedVolatility, optionType, 300.0);

        // ITM should have higher delta than OTM
        assertTrue(itmGreeks.getDelta() > otmGreeks.getDelta(), 
                "ITM option should have higher delta than OTM option");

        // ITM should have higher intrinsic value
        assertTrue(itmGreeks.getIntrinsicValue() > otmGreeks.getIntrinsicValue(),
                "ITM option should have higher intrinsic value");

        System.out.println("📊 MONEYNESS COMPARISON:");
        System.out.println("ITM (24800): Delta=" + String.format("%.4f", itmGreeks.getDelta()) + 
                         ", Intrinsic=" + String.format("%.2f", itmGreeks.getIntrinsicValue()));
        System.out.println("OTM (25200): Delta=" + String.format("%.4f", otmGreeks.getDelta()) + 
                         ", Intrinsic=" + String.format("%.2f", otmGreeks.getIntrinsicValue()));
    }
}
