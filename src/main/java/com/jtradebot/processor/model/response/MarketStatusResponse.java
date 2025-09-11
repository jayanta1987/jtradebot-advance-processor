package com.jtradebot.processor.model.response;

import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.strategy.DetailedCategoryScore;
import com.jtradebot.processor.service.entry.UnstableMarketConditionAnalysisService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Response model for market status API endpoint
 * Contains all current market analysis data without recalculating
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketStatusResponse {
    
    // Basic market info
    private String instrumentToken;
    private LocalDateTime timestamp;
    private Double currentPrice;
    private String marketSession;
    
    // Trading zone status
    private Boolean inTradingZone;
    private UnstableMarketConditionAnalysisService.FlexibleFilteringResult filteringResult;
    
    // Category scores for both directions
    private Map<String, DetailedCategoryScore> callCategoryScores;
    private Map<String, DetailedCategoryScore> putCategoryScores;
    
    // Overall scores
    private Double callTotalScore;
    private Double putTotalScore;
    private Double qualityScore;
    private String dominantTrend;
    
    // Current indicators
    private FlattenedIndicators currentIndicators;
    
    // Market condition details
    private Map<String, Object> marketConditionDetails;
    
    // Entry eligibility
    private Boolean eligibleForEntry;
    private String entryEligibilityReason;
    
    // Additional market metrics
    private Map<String, Object> additionalMetrics;
}
