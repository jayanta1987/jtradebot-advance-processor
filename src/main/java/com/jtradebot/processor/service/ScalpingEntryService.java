package com.jtradebot.processor.service;

import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.strategy.ScalpingEntryDecision;
import com.zerodhatech.models.Tick;

public interface ScalpingEntryService {
    
    /**
     * Evaluates entry conditions based on scenarios and returns a decision
     * @param tick The current tick data
     * @param indicators The flattened indicators for the tick
     * @return ScalpingEntryDecision with entry recommendation and details
     */
    ScalpingEntryDecision evaluateEntry(Tick tick, FlattenedIndicators indicators);
    
    /**
     * Evaluates entry conditions with optional pre-calculated quality score
     * @param tick The current tick data
     * @param indicators The flattened indicators for the tick
     * @param preCalculatedQualityScore Optional pre-calculated quality score to avoid duplicate calculations
     * @return ScalpingEntryDecision with entry recommendation and details
     */
    ScalpingEntryDecision evaluateEntry(Tick tick, FlattenedIndicators indicators, Double preCalculatedQualityScore);
    
    /**
     * Evaluates entry conditions with optional pre-calculated quality score and market condition
     * 🔥 OPTIMIZATION: Accepts pre-calculated market condition to avoid redundant calls
     * @param tick The current tick data
     * @param indicators The flattened indicators for the tick
     * @param preCalculatedQualityScore Optional pre-calculated quality score to avoid duplicate calculations
     * @param preCalculatedMarketCondition Optional pre-calculated market condition to avoid redundant calls
     * @return ScalpingEntryDecision with entry recommendation and details
     */
    ScalpingEntryDecision evaluateEntry(Tick tick, FlattenedIndicators indicators, Double preCalculatedQualityScore, Boolean preCalculatedMarketCondition);
    
    /**
     * Evaluates entry conditions for standalone usage (when market condition is not pre-calculated)
     * This method will calculate market condition internally when needed
     * @param tick The current tick data
     * @param indicators The flattened indicators for the tick
     * @return ScalpingEntryDecision with entry recommendation and details
     */
    ScalpingEntryDecision evaluateEntryStandalone(Tick tick, FlattenedIndicators indicators);
}
