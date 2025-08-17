package com.jtradebot.processor.service;

import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;

class MarketDirectionTest {

    @InjectMocks
    private TickProcessService tickProcessService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testBullishMarketConditions() {
        // Create indicators with bullish signals
        FlattenedIndicators indicators = new FlattenedIndicators();
        
        // Set bullish EMA conditions (EMA9 > EMA21)
        indicators.setEma5_5min_gt_ema34_5min(true);
        indicators.setEma5_1min_gt_ema34_1min(true);
        indicators.setEma5_15min_gt_ema34_15min(true);
        
        // Set bullish price conditions (Price > VWAP)
        indicators.setPrice_gt_vwap_5min(true);
        indicators.setPrice_gt_vwap_1min(true);
        indicators.setPrice_gt_vwap_15min(true);
        
        // Set bullish RSI conditions (RSI > 56)
        indicators.setRsi_5min_gt_56(true);
        indicators.setRsi_1min_gt_56(true);
        indicators.setRsi_15min_gt_56(true);
        
        // Set bullish candlestick conditions
        indicators.setGreen_candle_5min(true);
        indicators.setGreen_candle_1min(true);
        
        // Test bullish condition (should be true with 100% bullish signals)
        boolean isBullish = tickProcessService.isMarketConditionBullish(indicators);
        assertTrue(isBullish, "Should be bullish with 100% bullish signals");
        
        // Test bearish condition (should be false)
        boolean isBearish = tickProcessService.isMarketConditionBearish(indicators);
        assertFalse(isBearish, "Should not be bearish with bullish signals");
    }

    @Test
    void testBearishMarketConditions() {
        // Create indicators with bearish signals
        FlattenedIndicators indicators = new FlattenedIndicators();
        
        // Set bearish EMA conditions (EMA9 < EMA21)
        indicators.setEma5_5min_lt_ema34_5min(true);
        indicators.setEma5_1min_lt_ema34_1min(true);
        indicators.setEma5_15min_lt_ema34_15min(true);
        
        // Set bearish price conditions (Price < VWAP)
        indicators.setPrice_lt_vwap_5min(true);
        indicators.setPrice_lt_vwap_1min(true);
        indicators.setPrice_lt_vwap_15min(true);
        
        // Set bearish RSI conditions (RSI < 44)
        indicators.setRsi_5min_lt_44(true);
        indicators.setRsi_1min_lt_44(true);
        indicators.setRsi_15min_lt_44(true);
        
        // Set bearish candlestick conditions
        indicators.setBearish_engulfing_5min(true);
        indicators.setBearish_engulfing_1min(true);
        
        // Test bearish condition (should be true with 100% bearish signals)
        boolean isBearish = tickProcessService.isMarketConditionBearish(indicators);
        assertTrue(isBearish, "Should be bearish with 100% bearish signals");
        
        // Test bullish condition (should be false)
        boolean isBullish = tickProcessService.isMarketConditionBullish(indicators);
        assertFalse(isBullish, "Should not be bullish with bearish signals");
    }

    @Test
    void testNeutralMarketConditions() {
        // Create indicators with mixed signals
        FlattenedIndicators indicators = new FlattenedIndicators();
        
        // Set some bullish and some bearish signals
        indicators.setEma5_5min_gt_ema34_5min(true);
        indicators.setEma5_1min_lt_ema34_1min(true);
        indicators.setPrice_gt_vwap_5min(true);
        indicators.setPrice_lt_vwap_1min(true);
        
        // Test both conditions (should be false with mixed signals)
        boolean isBullish = tickProcessService.isMarketConditionBullish(indicators);
        boolean isBearish = tickProcessService.isMarketConditionBearish(indicators);
        
        assertFalse(isBullish, "Should not be bullish with mixed signals");
        assertFalse(isBearish, "Should not be bearish with mixed signals");
    }
}
