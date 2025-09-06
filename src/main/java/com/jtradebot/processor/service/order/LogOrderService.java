package com.jtradebot.processor.service.order;

import com.jtradebot.processor.repository.document.JtradeOrder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LogOrderService {

    public static void logScores(JtradeOrder order) {
        // Log scenario information if available
        if (order.getEntryScenarioName() != null) {
            log.info("🎯 SCENARIO INFO - Name: {}, Description: {}, Confidence: {}/10",
                    order.getEntryScenarioName(), order.getEntryScenarioDescription(), order.getEntryScenarioConfidence());
            if (order.getEntryCategoryScores() != null) {
                log.info("📈 CATEGORY SCORES - {}", order.getEntryCategoryScores());
            }
            if (order.getEntryMatchedConditions() != null) {
                log.info("✅ MATCHED CONDITIONS - {}", order.getEntryMatchedConditions());
            }
            // 🔥 NEW: Log quality score and direction scores
            if (order.getEntryQualityScore() != null) {
                log.info("🎯 QUALITY SCORE - {}/10", String.format("%.1f", order.getEntryQualityScore()));
            }
            if (order.getEntryCallScores() != null) {
                log.info("📈 CALL SCORES - {}", order.getEntryCallScores());
            }
            if (order.getEntryPutScores() != null) {
                log.info("📉 PUT SCORES - {}", order.getEntryPutScores());
            }
            if (order.getEntryDominantTrend() != null) {
                log.info("🎯 DOMINANT TREND - {}", order.getEntryDominantTrend());
            }
        }
    }
}
