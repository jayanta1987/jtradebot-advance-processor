package com.jtradebot.processor.service;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.model.DynamicFlattenedIndicators;
import com.jtradebot.processor.model.DynamicIndicatorConfig;
import com.jtradebot.processor.model.ScalpingEntryDecision;
import com.jtradebot.processor.model.StrategyEvaluationResult;
import com.jtradebot.processor.repository.document.TickDocument;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicTickLoggingService {
    
    private final DynamicStrategyConfigService configService;
    private final DynamicIndicatorFlattenerService dynamicIndicatorFlattenerService;
    private final DynamicStrategyEvaluatorService dynamicStrategyEvaluatorService;
    private final ScalpingEntryService scalpingEntryService;
    
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    /**
     * Enhanced tick logging with dynamic indicators
     */
    public void logTickWithDynamicIndicators(Tick tick, TickDocument tickDocument) {
        try {
            // Get dynamic configuration
            DynamicIndicatorConfig indicatorConfig = configService.getDynamicIndicatorConfig();
            
            // Flatten indicators dynamically
            DynamicFlattenedIndicators flattenedIndicators = 
                dynamicIndicatorFlattenerService.flattenIndicators(tickDocument, indicatorConfig);
            
            // Evaluate strategy dynamically
            StrategyEvaluationResult strategyResult = 
                dynamicStrategyEvaluatorService.evaluateStrategy(flattenedIndicators, indicatorConfig);
            
            // Log basic tick information
            logBasicTickInfo(tick);
            
            // Log dynamic indicators summary
            logDynamicIndicatorsSummary(flattenedIndicators);
            
            // Log strategy evaluation
            logStrategyEvaluation(strategyResult);
            
            // Log entry decisions if applicable
            if (strategyResult.isTradeRecommended()) {
                logEntryDecisions(tick, flattenedIndicators, indicatorConfig);
            }
            
            // Log detailed indicators for strong signals
            if (Math.abs(strategyResult.getConfidenceScore()) >= 0.7) {
                logDetailedIndicators(flattenedIndicators);
            }
            
        } catch (Exception e) {
            log.error("Error in dynamic tick logging for instrument: {}", tick.getInstrumentToken(), e);
        }
    }
    
    /**
     * Log basic tick information
     */
    private void logBasicTickInfo(Tick tick) {
        String timeStr = LocalDateTime.now().format(TIME_FORMATTER);
        log.info("🕐 {} | 📊 {} | 💰 {:.2f} | 📈 {:.2f} | 📉 {:.2f} | 📊 Vol: {}", 
                timeStr,
                tick.getInstrumentToken(),
                tick.getLastTradedPrice(),
                tick.getHighPrice(),
                tick.getLowPrice(),
                tick.getVolumeTradedToday());
    }
    
    /**
     * Log dynamic indicators summary
     */
    private void logDynamicIndicatorsSummary(DynamicFlattenedIndicators indicators) {
        StringBuilder summary = new StringBuilder();
        summary.append("🎯 INDICATORS: ");
        
        // Count positive and negative indicators
        int positiveCount = 0;
        int negativeCount = 0;
        
        if (indicators.getBooleanIndicators() != null) {
            for (Map.Entry<String, Boolean> entry : indicators.getBooleanIndicators().entrySet()) {
                if (entry.getValue()) {
                    if (entry.getKey().contains("crossover") || entry.getKey().contains("bullish")) {
                        positiveCount++;
                    } else if (entry.getKey().contains("crossdown") || entry.getKey().contains("bearish")) {
                        negativeCount++;
                    }
                }
            }
        }
        
        summary.append(String.format("✅ %d | ❌ %d", positiveCount, negativeCount));
        
        // Add key indicator status
        if (indicators.getBooleanIndicators() != null) {
            if (indicators.getBooleanIndicator("ema_1min_crossover")) summary.append(" | EMA1↑");
            if (indicators.getBooleanIndicator("ema_5min_crossover")) summary.append(" | EMA5↑");
            if (indicators.getBooleanIndicator("rsi_1min_bullish")) summary.append(" | RSI1↑");
            if (indicators.getBooleanIndicator("volume_1min_surge")) summary.append(" | VOL↑");
        }
        
        log.info(summary.toString());
    }
    
    /**
     * Log strategy evaluation results
     */
    private void logStrategyEvaluation(StrategyEvaluationResult result) {
        String confidenceEmoji = getConfidenceEmoji(result.getConfidenceScore());
        String strategyEmoji = getStrategyEmoji(result.getRecommendedStrategy());
        
        log.info("{} {} | Confidence: {:.1%} | {} | {}", 
                confidenceEmoji,
                strategyEmoji,
                result.getConfidenceScore(),
                result.getRecommendedStrategy(),
                result.getReasoning());
        
        // Log satisfied conditions
        if (result.getSatisfiedConditions() != null && !result.getSatisfiedConditions().isEmpty()) {
            log.info("✅ SATISFIED: {}", String.join(", ", result.getSatisfiedConditions()));
        }
        
        // Log unsatisfied conditions for debugging
        if (result.getUnsatisfiedConditions() != null && !result.getUnsatisfiedConditions().isEmpty()) {
            log.debug("❌ UNSATISFIED: {}", String.join(", ", result.getUnsatisfiedConditions()));
        }
    }
    
    /**
     * Log entry decisions
     */
    private void logEntryDecisions(Tick tick, DynamicFlattenedIndicators indicators, DynamicIndicatorConfig config) {
        try {
            double currentPrice = tick.getLastTradedPrice();
            LocalDateTime currentTime = LocalDateTime.now();
            
            // Evaluate CALL entry
            ScalpingEntryDecision callDecision = scalpingEntryService.evaluateCallEntry(
                indicators, config, currentPrice, currentTime);
            
            if (callDecision.isShouldEnter()) {
                log.info("🚀 CALL ENTRY | Price: {:.2f} | Size: {} | SL: {:.2f} | Target: {:.2f} | Quality: {} | Confidence: {:.1%}", 
                        callDecision.getEntryPrice(),
                        callDecision.getPositionSize(),
                        callDecision.getStopLossPrice(),
                        callDecision.getTargetPrice(),
                        callDecision.getEntryQuality(),
                        callDecision.getConfidenceScore());
            }
            
            // Evaluate PUT entry
            ScalpingEntryDecision putDecision = scalpingEntryService.evaluatePutEntry(
                indicators, config, currentPrice, currentTime);
            
            if (putDecision.isShouldEnter()) {
                log.info("📉 PUT ENTRY | Price: {:.2f} | Size: {} | SL: {:.2f} | Target: {:.2f} | Quality: {} | Confidence: {:.1%}", 
                        putDecision.getEntryPrice(),
                        putDecision.getPositionSize(),
                        putDecision.getStopLossPrice(),
                        putDecision.getTargetPrice(),
                        putDecision.getEntryQuality(),
                        putDecision.getConfidenceScore());
            }
            
        } catch (Exception e) {
            log.error("Error evaluating entry decisions", e);
        }
    }
    
    /**
     * Log detailed indicators for strong signals
     */
    private void logDetailedIndicators(DynamicFlattenedIndicators indicators) {
        StringBuilder details = new StringBuilder();
        details.append("📊 DETAILED INDICATORS:\n");
        
        // EMA indicators
        details.append("  📈 EMA: ");
        if (indicators.getBooleanIndicators() != null) {
            if (indicators.getBooleanIndicator("ema_1min_crossover")) details.append("1min↑ ");
            if (indicators.getBooleanIndicator("ema_5min_crossover")) details.append("5min↑ ");
            if (indicators.getBooleanIndicator("ema_15min_crossover")) details.append("15min↑ ");
            if (indicators.getBooleanIndicator("ema_1min_crossdown")) details.append("1min↓ ");
            if (indicators.getBooleanIndicator("ema_5min_crossdown")) details.append("5min↓ ");
            if (indicators.getBooleanIndicator("ema_15min_crossdown")) details.append("15min↓ ");
        }
        details.append("\n");
        
        // RSI indicators
        details.append("  📊 RSI: ");
        if (indicators.getBooleanIndicators() != null) {
            if (indicators.getBooleanIndicator("rsi_1min_bullish")) details.append("1min↑ ");
            if (indicators.getBooleanIndicator("rsi_5min_bullish")) details.append("5min↑ ");
            if (indicators.getBooleanIndicator("rsi_15min_bullish")) details.append("15min↑ ");
            if (indicators.getBooleanIndicator("rsi_1min_bearish")) details.append("1min↓ ");
            if (indicators.getBooleanIndicator("rsi_5min_bearish")) details.append("5min↓ ");
            if (indicators.getBooleanIndicator("rsi_15min_bearish")) details.append("15min↓ ");
        }
        details.append("\n");
        
        // Volume indicators
        details.append("  📈 Volume: ");
        if (indicators.getBooleanIndicators() != null) {
            if (indicators.getBooleanIndicator("volume_1min_surge")) details.append("1min↑ ");
            if (indicators.getBooleanIndicator("volume_5min_surge")) details.append("5min↑ ");
            if (indicators.getBooleanIndicator("volume_15min_surge")) details.append("15min↑ ");
            if (indicators.getBooleanIndicator("volume_1min_low")) details.append("1min↓ ");
            if (indicators.getBooleanIndicator("volume_5min_low")) details.append("5min↓ ");
            if (indicators.getBooleanIndicator("volume_15min_low")) details.append("15min↓ ");
        }
        details.append("\n");
        
        // Price action indicators
        details.append("  💰 Price Action: ");
        if (indicators.getBooleanIndicators() != null) {
            if (indicators.getBooleanIndicator("price_1min_gt_vwap")) details.append("1min>VWAP ");
            if (indicators.getBooleanIndicator("price_5min_gt_vwap")) details.append("5min>VWAP ");
            if (indicators.getBooleanIndicator("price_15min_gt_vwap")) details.append("15min>VWAP ");
            if (indicators.getBooleanIndicator("price_1min_lt_vwap")) details.append("1min<VWAP ");
            if (indicators.getBooleanIndicator("price_5min_lt_vwap")) details.append("5min<VWAP ");
            if (indicators.getBooleanIndicator("price_15min_lt_vwap")) details.append("15min<VWAP ");
        }
        
        log.info(details.toString());
    }
    
    /**
     * Get confidence emoji based on confidence score
     */
    private String getConfidenceEmoji(double confidence) {
        if (confidence >= 0.9) return "🟢";
        else if (confidence >= 0.7) return "🟡";
        else if (confidence >= 0.5) return "🟠";
        else return "🔴";
    }
    
    /**
     * Get strategy emoji
     */
    private String getStrategyEmoji(String strategy) {
        switch (strategy) {
            case "CALL": return "🚀";
            case "PUT": return "📉";
            default: return "⚪";
        }
    }
    
    /**
     * Legacy compatibility method for existing tick processing
     */
    public void logLegacyTickInfo(Tick tick, String niftyInfo, double score, String scoreDescription) {
        String scoreSymbol = getLegacyScoreColorSymbol(score);
        log.info("{} {} | {}", scoreSymbol, scoreDescription, niftyInfo);
    }
    
    /**
     * Legacy score color symbol method
     */
    private String getLegacyScoreColorSymbol(Double score) {
        if (score == null) return "⚪";
        
        double absScore = Math.abs(score);
        
        if (score > 0) {
            if (absScore >= 8.0) return "🟢";
            else if (absScore >= 6.0) return "🟢";
            else if (absScore >= 3.0) return "🟡";
            else if (absScore >= 1.5) return "🟡";
            else return "⚪";
        } else if (score < 0) {
            if (absScore >= 8.0) return "🔴";
            else if (absScore >= 6.0) return "🔴";
            else if (absScore >= 3.0) return "🟠";
            else if (absScore >= 1.5) return "🟠";
            else return "⚪";
        } else {
            return "⚪";
        }
    }
}
