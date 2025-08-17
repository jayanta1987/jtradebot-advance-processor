package com.jtradebot.processor.service;

import com.jtradebot.processor.model.indicator.EntryQuality;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.strategy.ProfitableTradeFilterResult;
import com.zerodhatech.models.Tick;

public interface ProfitableTradeFilterService {
    
    /**
     * Evaluate if a CALL entry meets the profitable trade criteria
     * @param indicators Flattened indicators
     * @param entryQuality Entry quality assessment
     * @param tick Current tick data
     * @return ProfitableTradeFilterResult with evaluation details
     */
    ProfitableTradeFilterResult evaluateCallEntry(FlattenedIndicators indicators, EntryQuality entryQuality, Tick tick);
    
    /**
     * Evaluate if a PUT entry meets the profitable trade criteria
     * @param indicators Flattened indicators
     * @param entryQuality Entry quality assessment
     * @param tick Current tick data
     * @return ProfitableTradeFilterResult with evaluation details
     */
    ProfitableTradeFilterResult evaluatePutEntry(FlattenedIndicators indicators, EntryQuality entryQuality, Tick tick);
    
    /**
     * Check if the profitable trade filter is enabled
     * @return true if enabled, false otherwise
     */
    boolean isFilterEnabled();
    
    /**
     * Get the current filter configuration
     * @return ProfitableTradeFilterConfig object
     */
    com.jtradebot.processor.model.strategy.ProfitableTradeFilterConfig getFilterConfig();
}
