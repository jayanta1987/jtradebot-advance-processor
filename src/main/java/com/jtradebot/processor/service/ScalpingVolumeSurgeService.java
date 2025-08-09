package com.jtradebot.processor.service;

import com.jtradebot.processor.model.FlattenedIndicators;
import com.jtradebot.processor.model.ScalpingVolumeSurgeCallRule;
import com.jtradebot.processor.model.ScalpingVolumeSurgePutRule;
import com.zerodhatech.models.Tick;

public interface ScalpingVolumeSurgeService {
    
    /**
     * Evaluates if a CALL entry should be made based on flattened indicators
     * @param tick The current tick data
     * @return true if CALL entry should be made, false otherwise
     */
    boolean shouldMakeCallEntry(Tick tick);
    
    /**
     * Evaluates if a PUT entry should be made based on flattened indicators
     * @param tick The current tick data
     * @return true if PUT entry should be made, false otherwise
     */
    boolean shouldMakePutEntry(Tick tick);
    
    /**
     * Gets the flattened indicators for the current tick
     * @param tick The current tick data
     * @return FlattenedIndicators object with all indicators flattened
     */
    FlattenedIndicators getFlattenedIndicators(Tick tick);
    
    /**
     * Gets the recommended strategy (CALL, PUT, or NO_TRADE)
     * @param tick The current tick data
     * @return String representing the recommended strategy
     */
    String getRecommendedStrategy(Tick tick);
    
    /**
     * Gets the strategy confidence score (0.0 to 1.0)
     * @param tick The current tick data
     * @return Double representing confidence in the strategy
     */
    Double getStrategyConfidence(Tick tick);
}
