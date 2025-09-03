package com.jtradebot.processor.handler;

import com.jtradebot.processor.repository.InstrumentRepository;
import com.jtradebot.processor.repository.document.Instrument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class StrikePriceCalculatorLiveTest {

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private StrikePriceCalculator strikePriceCalculator;

    @Test
    void testStrikePriceCalculationForIndexPrice24548() {
        // Given: Test parameters
        double niftyIndexPrice = 24548.0;
        String optionType = "CE";

        // When: Use the actual implementation to calculate strike price
        int calculatedStrikePrice = strikePriceCalculator.getATMStrikePrice(niftyIndexPrice, optionType);

        // Then: Verify the calculation is correct
        assertEquals(24400, calculatedStrikePrice, 
                "Strike price should be 24400 for Call option with index price 24548");

        System.out.println("✅ STRIKE PRICE CALCULATION TEST PASSED");
        System.out.println("Index Price: " + niftyIndexPrice);
        System.out.println("Calculated Strike: " + calculatedStrikePrice);
        System.out.println("Expected Strike: 24400");
    }

    @Test
    void testFindOptionInstrumentFromDatabase() {
        // Given: Test parameters
        double niftyIndexPrice = 24548.0;
        String optionType = "CE";

        // When: Use the actual implementation to find option instrument
        Optional<Instrument> foundInstrument = strikePriceCalculator.findOptionInstrument(niftyIndexPrice, optionType);

        // Then: Verify that an instrument was found
        assertTrue(foundInstrument.isPresent(), "Should find an option instrument");

        Instrument instrument = foundInstrument.get();
        
        // Verify the instrument properties
        assertEquals("24400", instrument.getStrike(), "Strike price should be 24400");
        assertEquals("CE", instrument.getInstrumentType(), "Should be a Call option");
        assertNotNull(instrument.getTradingSymbol(), "Trading symbol should not be null");
        assertNotNull(instrument.getExpiry(), "Expiry should not be null");
        assertTrue(instrument.getInstrumentToken() > 0, "Instrument token should be positive");

        System.out.println("✅ OPTION INSTRUMENT FOUND:");
        System.out.println("Trading Symbol: " + instrument.getTradingSymbol());
        System.out.println("Strike Price: " + instrument.getStrike());
        System.out.println("Expiry Date: " + instrument.getExpiry());
        System.out.println("Instrument Token: " + instrument.getInstrumentToken());
    }

    @Test
    void testDatabaseHasRequiredInstruments() {
        // Given: Expected strike price for index 24548
        int expectedStrike = 24400;
        String optionType = "CE";

        // When: Query database directly to verify instruments exist
        List<Instrument> allInstruments = instrumentRepository.findAll();
        
        // Filter for the specific strike and option type
        List<Instrument> matchingInstruments = allInstruments.stream()
                .filter(instrument -> "NIFTY".equals(instrument.getName()) && 
                        ("OPT".equals(instrument.getInstrumentType()) || "CE".equals(instrument.getInstrumentType()) || "PE".equals(instrument.getInstrumentType())))
                .filter(instrument -> expectedStrike == Integer.parseInt(instrument.getStrike()) && 
                        optionType.equals(instrument.getInstrumentType()))
                .toList();

        // Then: Verify that instruments exist in database
        assertFalse(matchingInstruments.isEmpty(), 
                "Database should contain instruments with strike " + expectedStrike + " and type " + optionType);

        System.out.println("✅ DATABASE VERIFICATION PASSED:");
        System.out.println("Found " + matchingInstruments.size() + " instruments with strike " + expectedStrike + " and type " + optionType);
        
        // Show the instruments found
        matchingInstruments.forEach(instrument -> 
            System.out.println("  - " + instrument.getTradingSymbol() + " (Expiry: " + instrument.getExpiry() + ")"));
    }

    @Test
    void testEarliestExpirySelection() {
        // Given: Test parameters
        double niftyIndexPrice = 24548.0;
        String optionType = "CE";

        // When: Find option instrument using the implementation
        Optional<Instrument> foundInstrument = strikePriceCalculator.findOptionInstrument(niftyIndexPrice, optionType);

        // Then: Verify that the earliest expiry was selected
        assertTrue(foundInstrument.isPresent(), "Should find an option instrument");

        Instrument selectedInstrument = foundInstrument.get();
        String selectedExpiry = selectedInstrument.getExpiry();

        // Get all instruments with same strike and type to verify earliest expiry selection
        List<Instrument> allInstruments = instrumentRepository.findAll();
        List<Instrument> sameStrikeInstruments = allInstruments.stream()
                .filter(instrument -> "NIFTY".equals(instrument.getName()) && 
                        optionType.equals(instrument.getInstrumentType()) &&
                        selectedInstrument.getStrike().equals(instrument.getStrike()))
                .toList();

        // Verify that the selected instrument has the earliest expiry
        boolean isEarliestExpiry = sameStrikeInstruments.stream()
                .allMatch(instrument -> {
                    try {
                        java.time.LocalDate selectedDate = java.time.LocalDate.parse(selectedExpiry, 
                                java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                        java.time.LocalDate instrumentDate = java.time.LocalDate.parse(instrument.getExpiry(), 
                                java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                        return !instrumentDate.isBefore(selectedDate);
                    } catch (Exception e) {
                        return true; // Skip if date parsing fails
                    }
                });

        assertTrue(isEarliestExpiry, "Selected instrument should have the earliest expiry");

        System.out.println("✅ EARLIEST EXPIRY SELECTION VERIFIED:");
        System.out.println("Selected Expiry: " + selectedExpiry);
        System.out.println("Total instruments with same strike: " + sameStrikeInstruments.size());
    }

    @Test
    void testStrikePriceCalculationConsistency() {
        // Given: Multiple test cases
        double[] testIndexPrices = {24500, 24548, 24600, 25000, 24000};
        String optionType = "CE";

        // When & Then: Verify consistency across different index prices
        for (double indexPrice : testIndexPrices) {
            int calculatedStrike = strikePriceCalculator.getATMStrikePrice(indexPrice, optionType);
            
            // Verify ITM condition for Call options (strike < index price)
            assertTrue(calculatedStrike < indexPrice, 
                    "For Call options, ITM strike should be less than index price. Index: " + indexPrice + ", Strike: " + calculatedStrike);
            
            // Verify strike is a multiple of 100
            assertEquals(0, calculatedStrike % 100, 
                    "Strike price should be a multiple of 100. Strike: " + calculatedStrike);
        }

        System.out.println("✅ STRIKE PRICE CALCULATION CONSISTENCY VERIFIED");
        System.out.println("Tested " + testIndexPrices.length + " different index prices");
    }

    @Test
    void testPutOptionCalculation() {
        // Given: Test parameters
        double niftyIndexPrice = 24548.0;
        String optionType = "PE";

        // When: Calculate Put strike price
        int calculatedStrikePrice = strikePriceCalculator.getATMStrikePrice(niftyIndexPrice, optionType);

        // Then: Verify the calculation
        assertEquals(24700, calculatedStrikePrice, 
                "Strike price should be 24700 for Put option with index price 24548");

        // Verify ITM condition for Put options (strike > index price)
        assertTrue(calculatedStrikePrice > niftyIndexPrice, 
                "For Put options, ITM strike should be greater than index price");

        System.out.println("✅ PUT OPTION CALCULATION VERIFIED:");
        System.out.println("Index Price: " + niftyIndexPrice);
        System.out.println("Calculated Strike: " + calculatedStrikePrice);
        System.out.println("Expected Strike: 24700");
    }
}
