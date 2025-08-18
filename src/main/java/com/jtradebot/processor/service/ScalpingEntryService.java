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
}
