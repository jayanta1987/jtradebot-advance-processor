package com.jtradebot.processor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * NTP (Near The Price) Details for tracking filter results and market conditions
 * at the time of order entry. This class stores comprehensive information about
 * NTP filter analysis results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NtpDetails {
    
    /**
     * Number of filters that passed the NTP analysis
     */
    private Integer filtersPassed;
    
    /**
     * Number of filters that failed the NTP analysis
     */
    private Integer filtersFailed;
    
    /**
     * Total number of filters checked during NTP analysis
     */
    private Integer totalFiltersChecked;
    
    /**
     * Total NTP value calculated from all filters
     */
    private Double totalNtpValue;
    
    /**
     * Maximum allowed NTP value threshold
     */
    private Double maxAllowedNtpValue;
    
    /**
     * List of failed filter names with their NTP values
     * Format: "FilterName (NTP: value)"
     */
    private List<String> failedFilterNames;
    
    /**
     * Reason for NTP analysis result
     */
    private String ntpReason;
    
    /**
     * Whether all conditions were met for entry
     */
    private Boolean conditionsMet;
    
    /**
     * Additional metadata about the NTP analysis
     */
    private String analysisTimestamp;
    
    /**
     * Market condition score at the time of analysis
     */
    private Double marketConditionScore;
    
    /**
     * Whether the market was considered stable for entry
     */
    private Boolean marketStable;
}
