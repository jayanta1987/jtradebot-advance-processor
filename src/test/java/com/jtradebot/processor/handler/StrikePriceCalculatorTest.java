package com.jtradebot.processor.handler;

import com.jtradebot.processor.repository.InstrumentRepository;
import com.jtradebot.processor.repository.document.Instrument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrikePriceCalculatorTest {

    @Mock
    private InstrumentRepository instrumentRepository;

    private StrikePriceCalculator strikePriceCalculator;

    @BeforeEach
    void setUp() {
        strikePriceCalculator = new StrikePriceCalculator(instrumentRepository);
    }

    @Test
    void testStrikePriceCalculationForCallWithIndexPrice24548() {
        // Given: Current Nifty index price is 24548
        double niftyIndexPrice = 24548.0;
        String optionType = "CE"; // Call option

        // When: Calculate ATM strike price for Call option
        int calculatedStrikePrice = strikePriceCalculator.getATMStrikePrice(niftyIndexPrice, optionType);

        // Then: Verify the calculation
        // For Call options: ITM means strike < current price
        // Base strike calculation: Math.floor(24548 / 100) * 100 = 24500
        // ITM strike: 24500 - 100 = 24400
        int expectedStrikePrice = 24400;
        
        assertEquals(expectedStrikePrice, calculatedStrikePrice, 
                "Strike price should be 24400 for Call option with index price 24548");
        
        System.out.println("🎯 STRIKE PRICE CALCULATION TEST");
        System.out.println("Index Price: " + niftyIndexPrice);
        System.out.println("Option Type: " + optionType);
        System.out.println("Calculated Strike Price: " + calculatedStrikePrice);
        System.out.println("Expected Strike Price: " + expectedStrikePrice);
        System.out.println("✅ Test Passed: Strike price calculation is correct");
    }

    @Test
    void testStrikePriceCalculationLogic() {
        // Given: Test the calculation logic step by step
        double niftyIndexPrice = 24548.0;
        String optionType = "CE";
        int strikeDifference = 100;

        // When: Calculate step by step
        double baseStrike = Math.floor(niftyIndexPrice / strikeDifference) * strikeDifference;
        int itmStrike = (int) baseStrike - strikeDifference;

        // Then: Verify each step
        assertEquals(24500.0, baseStrike, "Base strike should be 24500");
        assertEquals(24400, itmStrike, "ITM strike should be 24400");

        System.out.println("🔍 STEP-BY-STEP CALCULATION:");
        System.out.println("Index Price: " + niftyIndexPrice);
        System.out.println("Strike Difference: " + strikeDifference);
        System.out.println("Base Strike: Math.floor(" + niftyIndexPrice + " / " + strikeDifference + ") * " + strikeDifference + " = " + baseStrike);
        System.out.println("ITM Strike: " + baseStrike + " - " + strikeDifference + " = " + itmStrike);
    }

    @Test
    void testFindOptionInstrumentWithMockData() {
        // Given: Mock instrument data for testing
        double niftyIndexPrice = 24548.0;
        String optionType = "CE";
        int expectedStrikePrice = 24400;

        // Create mock instruments with different expiries
        Instrument instrument1 = createMockInstrument("NIFTY24JAN24400CE", 24400, "CE", "25-Jan-2024");
        Instrument instrument2 = createMockInstrument("NIFTY24FEB24400CE", 24400, "CE", "29-Feb-2024");
        Instrument instrument3 = createMockInstrument("NIFTY24MAR24400CE", 24400, "CE", "28-Mar-2024");

        List<Instrument> mockInstruments = Arrays.asList(instrument1, instrument2, instrument3);

        when(instrumentRepository.findAll()).thenReturn(mockInstruments);

        // When: Find option instrument
        Optional<Instrument> foundInstrument = strikePriceCalculator.findOptionInstrument(niftyIndexPrice, optionType);

        // Then: Verify the result
        assertTrue(foundInstrument.isPresent(), "Should find an option instrument");
        Instrument instrument = foundInstrument.get();
        assertEquals(expectedStrikePrice, Integer.parseInt(instrument.getStrike()), 
                "Should find instrument with strike price " + expectedStrikePrice);
        assertEquals("25-Jan-2024", instrument.getExpiry(), 
                "Should select the earliest expiry (25-Jan-2024)");

        System.out.println("🎯 OPTION INSTRUMENT FINDING TEST");
        System.out.println("Index Price: " + niftyIndexPrice);
        System.out.println("Option Type: " + optionType);
        System.out.println("Expected Strike: " + expectedStrikePrice);
        System.out.println("Found Instrument: " + instrument.getTradingSymbol());
        System.out.println("Found Strike: " + instrument.getStrike());
        System.out.println("Found Expiry: " + instrument.getExpiry());
        System.out.println("✅ Test Passed: Correct instrument found");
    }

    @Test
    void testStrikePriceCalculationForDifferentIndexPrices() {
        // Given: Test different index prices
        double[] indexPrices = {24500, 24548, 24600, 25000, 24000};
        String optionType = "CE";

        System.out.println("🔍 STRIKE PRICE CALCULATION FOR DIFFERENT INDEX PRICES:");
        System.out.println("Index Price\t|\tStrike Price\t|\tMoneyness");
        System.out.println("------------\t|\t------------\t|\t---------");

        for (double indexPrice : indexPrices) {
            int strikePrice = strikePriceCalculator.getATMStrikePrice(indexPrice, optionType);
            String moneyness = strikePrice < indexPrice ? "ITM" : "OTM";
            
            System.out.println(indexPrice + "\t\t|\t" + strikePrice + "\t\t|\t" + moneyness);
            
            // Verify ITM condition for Call options
            assertTrue(strikePrice < indexPrice, 
                    "For Call options, ITM strike should be less than index price");
        }
    }

    @Test
    void testStrikePriceCalculationForPutOptions() {
        // Given: Test Put option calculation
        double niftyIndexPrice = 24548.0;
        String optionType = "PE"; // Put option

        // When: Calculate ATM strike price for Put option
        int calculatedStrikePrice = strikePriceCalculator.getATMStrikePrice(niftyIndexPrice, optionType);

        // Then: Verify the calculation
        // For Put options: ITM means strike > current price
        // Base strike calculation: Math.ceil(24548 / 100) * 100 = 24600
        // ITM strike: 24600 + 100 = 24700
        int expectedStrikePrice = 24700;
        
        assertEquals(expectedStrikePrice, calculatedStrikePrice, 
                "Strike price should be 24700 for Put option with index price 24548");
        
        System.out.println("🎯 PUT OPTION STRIKE PRICE TEST");
        System.out.println("Index Price: " + niftyIndexPrice);
        System.out.println("Option Type: " + optionType);
        System.out.println("Calculated Strike Price: " + calculatedStrikePrice);
        System.out.println("Expected Strike Price: " + expectedStrikePrice);
        System.out.println("✅ Test Passed: Put option strike price calculation is correct");
    }

    private Instrument createMockInstrument(String tradingSymbol, int strike, String instrumentType, String expiry) {
        Instrument instrument = new Instrument();
        instrument.setTradingSymbol(tradingSymbol);
        instrument.setStrike(String.valueOf(strike));
        instrument.setInstrumentType(instrumentType);
        instrument.setExpiry(expiry);
        instrument.setName("NIFTY");
        instrument.setInstrumentToken(123456L);
        return instrument;
    }
}
