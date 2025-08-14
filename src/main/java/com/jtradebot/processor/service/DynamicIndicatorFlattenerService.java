package com.jtradebot.processor.service;

import com.jtradebot.processor.model.DynamicFlattenedIndicators;
import com.jtradebot.processor.model.DynamicIndicatorConfig;
import com.jtradebot.processor.repository.document.TickDocument;

public interface DynamicIndicatorFlattenerService {
    
    /**
     * Flattens all technical indicators for a given tick based on dynamic configuration
     * @param tickDocument The tick data to process
     * @param config The dynamic indicator configuration
     * @return DynamicFlattenedIndicators object with all indicators flattened
     */
    DynamicFlattenedIndicators flattenIndicators(TickDocument tickDocument, DynamicIndicatorConfig config);
    
    /**
     * Flattens indicators for a specific type (EMA, RSI, VOLUME, etc.)
     * @param tickDocument The tick data
     * @param flattenedIndicators The object to populate
     * @param indicatorConfig The indicator configuration
     */
    void flattenIndicatorType(TickDocument tickDocument, DynamicFlattenedIndicators flattenedIndicators, 
                             DynamicIndicatorConfig.IndicatorDefinition indicatorConfig);
    
    /**
     * Loads indicator configuration from JSON file
     * @param configPath Path to the JSON configuration file
     * @return DynamicIndicatorConfig object
     */
    DynamicIndicatorConfig loadConfiguration(String configPath);
    
    /**
     * Validates indicator configuration
     * @param config The configuration to validate
     * @return true if valid, false otherwise
     */
    boolean validateConfiguration(DynamicIndicatorConfig config);
}
