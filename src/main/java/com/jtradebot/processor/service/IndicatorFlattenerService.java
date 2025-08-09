package com.jtradebot.processor.service;

import com.jtradebot.processor.model.FlattenedIndicators;
import com.jtradebot.processor.repository.document.TickDocument;

public interface IndicatorFlattenerService {
    
    /**
     * Flattens all technical indicators for a given tick into boolean/numeric values
     * @param tickDocument The tick data to process
     * @return FlattenedIndicators object with all indicators flattened
     */
    FlattenedIndicators flattenIndicators(TickDocument tickDocument);
    
    /**
     * Flattens EMA crossover indicators
     * @param tickDocument The tick data
     * @param flattenedIndicators The object to populate
     */
    void flattenEmaIndicators(TickDocument tickDocument, FlattenedIndicators flattenedIndicators);
    
    /**
     * Flattens RSI indicators
     * @param tickDocument The tick data
     * @param flattenedIndicators The object to populate
     */
    void flattenRsiIndicators(TickDocument tickDocument, FlattenedIndicators flattenedIndicators);
    
    /**
     * Flattens volume surge indicators
     * @param tickDocument The tick data
     * @param flattenedIndicators The object to populate
     */
    void flattenVolumeIndicators(TickDocument tickDocument, FlattenedIndicators flattenedIndicators);
    
    /**
     * Flattens price action indicators (VWAP, Support/Resistance)
     * @param tickDocument The tick data
     * @param flattenedIndicators The object to populate
     */
    void flattenPriceActionIndicators(TickDocument tickDocument, FlattenedIndicators flattenedIndicators);
}
