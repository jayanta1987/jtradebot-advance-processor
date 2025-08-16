package com.jtradebot.processor.model.indicator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DynamicFlattenedIndicators {
    
    // Basic identification
    private String instrumentToken;
    private LocalDateTime timestamp;
    private String timeframe;
    
    // Dynamic indicator storage - key format: "indicatorType_timeframe_condition"
    // Examples: "ema_1min_crossover", "rsi_5min_gt_70", "volume_15min_surge"
    private Map<String, Boolean> booleanIndicators;
    private Map<String, Double> numericIndicators;
    private Map<String, String> stringIndicators;
    
    // Strategy decision
    private String recommendedStrategy; // CALL, PUT, or NO_TRADE
    private Double strategyConfidence; // 0.0 to 1.0
    
    // Raw indicator values for debugging
    private Map<String, Object> rawValues;
    
    /**
     * Set a boolean indicator value
     */
    public void setBooleanIndicator(String key, Boolean value) {
        if (booleanIndicators == null) {
            booleanIndicators = new HashMap<>();
        }
        booleanIndicators.put(key, value);
    }
    
    /**
     * Set a numeric indicator value
     */
    public void setNumericIndicator(String key, Double value) {
        if (numericIndicators == null) {
            numericIndicators = new HashMap<>();
        }
        numericIndicators.put(key, value);
    }
    
    /**
     * Set a string indicator value
     */
    public void setStringIndicator(String key, String value) {
        if (stringIndicators == null) {
            stringIndicators = new HashMap<>();
        }
        stringIndicators.put(key, value);
    }
    
    /**
     * Set a raw indicator value
     */
    public void setRawValue(String key, Object value) {
        if (rawValues == null) {
            rawValues = new HashMap<>();
        }
        rawValues.put(key, value);
    }
    
    /**
     * Get a boolean indicator value
     */
    public Boolean getBooleanIndicator(String key) {
        return booleanIndicators != null ? booleanIndicators.get(key) : null;
    }
    
    /**
     * Get a numeric indicator value
     */
    public Double getNumericIndicator(String key) {
        return numericIndicators != null ? numericIndicators.get(key) : null;
    }
    
    /**
     * Get a string indicator value
     */
    public String getStringIndicator(String key) {
        return stringIndicators != null ? stringIndicators.get(key) : null;
    }
    
    /**
     * Get a raw indicator value
     */
    public Object getRawValue(String key) {
        return rawValues != null ? rawValues.get(key) : null;
    }
    
    /**
     * Check if an indicator exists
     */
    public boolean hasIndicator(String key) {
        return (booleanIndicators != null && booleanIndicators.containsKey(key)) ||
               (numericIndicators != null && numericIndicators.containsKey(key)) ||
               (stringIndicators != null && stringIndicators.containsKey(key));
    }
    
    /**
     * Get all indicator keys
     */
    public java.util.Set<String> getAllIndicatorKeys() {
        java.util.Set<String> keys = new java.util.HashSet<>();
        if (booleanIndicators != null) keys.addAll(booleanIndicators.keySet());
        if (numericIndicators != null) keys.addAll(numericIndicators.keySet());
        if (stringIndicators != null) keys.addAll(stringIndicators.keySet());
        return keys;
    }
}
