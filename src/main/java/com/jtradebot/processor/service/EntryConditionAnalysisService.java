package com.jtradebot.processor.service;

import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.strategy.ScalpingEntryDecision;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EntryConditionAnalysisService {

    /**
     * Capture all conditions that led to the order entry - Scenario-based approach aligned with JSON configuration
     */
    public List<String> captureEntryConditions(ScalpingEntryDecision entryDecision, FlattenedIndicators indicators) {
        List<String> conditions = new ArrayList<>();
        
        try {
            // Use the passed entryDecision (no need to recalculate)
            
            if (entryDecision == null) {
                conditions.add("ERROR: No entry decision available");
                return conditions;
            }
            
            // Add scenario information
            conditions.add(String.format("Scenario: %s", entryDecision.getScenarioName()));
            conditions.add(String.format("Confidence: %.1f/10", entryDecision.getConfidence()));
            conditions.add(String.format("Reason: %s", entryDecision.getReason()));
            
            // Add quality score if available
            if (entryDecision.getQualityScore() > 0.0) {
                conditions.add(String.format("Quality Score: %.1f/10", entryDecision.getQualityScore()));
            }
            
            // Add category scores if available
            if (entryDecision.getCategoryScores() != null && !entryDecision.getCategoryScores().isEmpty()) {
                conditions.add("--- CATEGORY SCORES ---");
                for (Map.Entry<String, Integer> entry : entryDecision.getCategoryScores().entrySet()) {
                    conditions.add(String.format("%s: %d", entry.getKey(), entry.getValue()));
                }
            }
            
            // Add matched conditions if available
            if (entryDecision.getMatchedConditions() != null && !entryDecision.getMatchedConditions().isEmpty()) {
                conditions.add("--- MATCHED CONDITIONS ---");
                for (Map.Entry<String, List<String>> entry : entryDecision.getMatchedConditions().entrySet()) {
                    conditions.add(String.format("%s: %s", entry.getKey(), String.join(", ", entry.getValue())));
                }
            }
            
            // Add additional indicator information if needed
            // Use the passed indicators instead of recalculating
            // FlattenedIndicators indicators = scalpingVolumeSurgeService.getFlattenedIndicators(tick);
            if (indicators != null) {
                // Add futuresignal information
                if (indicators.getFuturesignals() != null) {
                    conditions.add("--- FUTURESIGNALS ---");
                    if (indicators.getFuturesignals().getAllTimeframesBullish()) {
                        conditions.add("✓ All timeframes bullish");
                    } else if (indicators.getFuturesignals().getAllTimeframesBearish()) {
                        conditions.add("✓ All timeframes bearish");
                    } else {
                        conditions.add("Mixed futuresignals");
                    }
                }
                
                // Add volume surge multiplier if available
                if (indicators.getVolume_surge_multiplier() != null) {
                    conditions.add(String.format("Volume Surge Multiplier: %.2fx", indicators.getVolume_surge_multiplier()));
                }
            }
            
        } catch (Exception e) {
            conditions.add("ERROR: Failed to capture conditions - " + e.getMessage());
            log.error("Error capturing entry conditions: {}", e.getMessage());
        }
        
        return conditions;
    }

}
