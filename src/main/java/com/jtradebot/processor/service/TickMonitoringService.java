package com.jtradebot.processor.service;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.model.DynamicFlattenedIndicators;
import com.jtradebot.processor.model.DynamicIndicatorConfig;
import com.jtradebot.processor.model.StrategyScore;
import com.jtradebot.processor.repository.document.TickDocument;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TickMonitoringService {
    
    private final DynamicStrategyConfigService configService;
    private final DynamicIndicatorFlattenerService dynamicIndicatorFlattenerService;
    
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    /**
     * Enhanced tick monitoring with score and indicator satisfaction
     */
    public void monitorTickWithScoreAndIndicators(Tick tick, TickDocument tickDocument) {
        try {
            // Get dynamic configuration
            DynamicIndicatorConfig indicatorConfig = configService.getDynamicIndicatorConfig();
            
            // Flatten indicators dynamically for detailed analysis
            DynamicFlattenedIndicators flattenedIndicators = 
                dynamicIndicatorFlattenerService.flattenIndicators(tickDocument, indicatorConfig);
            
            // Verify indicator count
            long totalIndicators = flattenedIndicators.getAllIndicatorKeys().size();
            if (totalIndicators != 42) {
                log.warn("⚠️ WARNING: Expected 42 indicators but got {} indicators", totalIndicators);
                log.warn("⚠️ This suggests legacy indicators are still being included");
            }
            
            // Calculate dynamic strategy score for monitoring (not for decisions)
            double dynamicScore = calculateDynamicScore(flattenedIndicators, indicatorConfig);
            
            // Log basic tick information
            logBasicTickInfo(tick);
            
            // Log dynamic strategy score and direction
            logDynamicStrategyScoreAndDirection(dynamicScore, flattenedIndicators);
            
            // Log indicator satisfaction analysis
            logIndicatorSatisfactionAnalysis(flattenedIndicators, indicatorConfig);
            
            // Log score breakdown for strong signals
            if (Math.abs(dynamicScore) >= 3.0) {
                logDetailedDynamicScoreBreakdown(flattenedIndicators, indicatorConfig);
            }
            
            // Log market sentiment summary
            logMarketSentimentSummary(dynamicScore, flattenedIndicators);
            
        } catch (Exception e) {
            log.error("Error in tick monitoring for instrument: {}", tick.getInstrumentToken(), e);
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
     * Calculate dynamic score based on indicator satisfaction
     */
    private double calculateDynamicScore(DynamicFlattenedIndicators indicators, DynamicIndicatorConfig config) {
        long totalSatisfied = countTotalSatisfied(indicators);
        long totalIndicators = indicators.getAllIndicatorKeys().size();
        
        if (totalIndicators == 0) return 0.0;
        
        // Calculate score based on satisfaction ratio
        double satisfactionRatio = (double) totalSatisfied / totalIndicators;
        
        // Convert to a -10 to +10 scale
        double score = (satisfactionRatio - 0.5) * 20.0;
        
        return Math.max(-10.0, Math.min(10.0, score));
    }
    
    /**
     * Log dynamic strategy score and direction
     */
    private void logDynamicStrategyScoreAndDirection(double score, DynamicFlattenedIndicators indicators) {
        String scoreSymbol = getScoreColorSymbol(score);
        String direction = getDirectionDescription(score);
        String scoreDescription = getScoreDescription(score);
        
        log.info("{} {} | Score: {:.2f} | Direction: {} | Indicators: {}/{}", 
                scoreSymbol,
                scoreDescription,
                score,
                direction,
                countTotalSatisfied(indicators),
                indicators.getAllIndicatorKeys().size());
    }
    
    /**
     * Log detailed dynamic score breakdown
     */
    private void logDetailedDynamicScoreBreakdown(DynamicFlattenedIndicators indicators, DynamicIndicatorConfig config) {
        Map<String, Long> satisfiedByType = countSatisfiedIndicatorsByType(indicators);
        
        log.info("📈 DYNAMIC SCORE BREAKDOWN - EMA: {}, RSI: {}, Volume: {}, Price: {}, Crossover: {}", 
                satisfiedByType.getOrDefault("EMA", 0L),
                satisfiedByType.getOrDefault("RSI", 0L),
                satisfiedByType.getOrDefault("Volume", 0L),
                satisfiedByType.getOrDefault("Price", 0L),
                satisfiedByType.getOrDefault("Crossover", 0L));
    }
    
    /**
     * Get score description
     */
    private String getScoreDescription(double score) {
        double absScore = Math.abs(score);
        
        if (score > 0) {
            if (absScore >= 6.0) return "STRONG_BULLISH";
            else if (absScore >= 3.0) return "MODERATE_BULLISH";
            else if (absScore >= 1.5) return "WEAK_BULLISH";
            else return "NEUTRAL";
        } else if (score < 0) {
            if (absScore >= 6.0) return "STRONG_BEARISH";
            else if (absScore >= 3.0) return "MODERATE_BEARISH";
            else if (absScore >= 1.5) return "WEAK_BEARISH";
            else return "NEUTRAL";
        } else {
            return "NEUTRAL";
        }
    }
    
    /**
     * Log strategy score and direction (legacy method - kept for compatibility)
     */
    private void logStrategyScoreAndDirection(StrategyScore strategyScore) {
        String scoreSymbol = getScoreColorSymbol(strategyScore.getScore());
        String direction = getDirectionDescription(strategyScore.getScore());
        
        log.info("{} {} | Score: {:.2f} | Direction: {} | Confidence: {:.1%}", 
                scoreSymbol,
                strategyScore.getScoreDescription(),
                strategyScore.getScore(),
                direction,
                strategyScore.getConfidence());
    }
    
    /**
     * Log indicator satisfaction analysis
     */
    private void logIndicatorSatisfactionAnalysis(DynamicFlattenedIndicators indicators, DynamicIndicatorConfig config) {
        // Count satisfied indicators by type
        Map<String, Long> satisfiedByType = countSatisfiedIndicatorsByType(indicators);
        
        // Get total counts
        long totalSatisfied = satisfiedByType.values().stream().mapToLong(Long::longValue).sum();
        long totalIndicators = indicators.getAllIndicatorKeys().size();
        
        // Debug: Log all indicator keys to understand the count
        if (totalIndicators > 50) {
            log.info("🔍 DEBUG: Total indicators count: {}", totalIndicators);
            log.info("🔍 DEBUG: First 10 indicator keys: {}", 
                indicators.getAllIndicatorKeys().stream().limit(10).collect(Collectors.toList()));
            log.info("🔍 DEBUG: All indicator keys count by type:");
            Map<String, Long> allByType = indicators.getAllIndicatorKeys().stream()
                .collect(Collectors.groupingBy(
                    key -> {
                        if (key.contains("ema")) return "EMA";
                        else if (key.contains("rsi")) return "RSI";
                        else if (key.contains("volume")) return "Volume";
                        else if (key.contains("price")) return "Price";
                        else if (key.contains("crossover") || key.contains("crossdown")) return "Crossover";
                        else if (key.contains("vwap")) return "VWAP";
                        else if (key.contains("support") || key.contains("resistance")) return "Support/Resistance";
                        else return "Other";
                    },
                    Collectors.counting()
                ));
            allByType.forEach((type, count) -> log.info("🔍 DEBUG: {}: {}", type, count));
            
            // Log all indicator keys for debugging
            log.info("🔍 DEBUG: ALL INDICATOR KEYS:");
            indicators.getAllIndicatorKeys().stream()
                .sorted()
                .forEach(key -> log.info("🔍 DEBUG: - {}", key));
        }
        
        // Log summary
        log.info("🎯 INDICATOR SATISFACTION | Total: {}/{} | EMA: {} | RSI: {} | Volume: {} | Price: {} | Crossover: {}", 
                totalSatisfied,
                totalIndicators,
                satisfiedByType.getOrDefault("EMA", 0L),
                satisfiedByType.getOrDefault("RSI", 0L),
                satisfiedByType.getOrDefault("Volume", 0L),
                satisfiedByType.getOrDefault("Price", 0L),
                satisfiedByType.getOrDefault("Crossover", 0L));
        
        // Log detailed satisfaction for strong signals
        if (totalSatisfied >= 8) {
            logDetailedIndicatorSatisfaction(indicators, config);
        }
    }
    
    /**
     * Count satisfied indicators by type
     */
    private Map<String, Long> countSatisfiedIndicatorsByType(DynamicFlattenedIndicators indicators) {
        return indicators.getAllIndicatorKeys().stream()
                .filter(key -> {
                    Boolean value = indicators.getBooleanIndicator(key);
                    return value != null && value;
                })
                .collect(Collectors.groupingBy(
                    key -> {
                        if (key.contains("ema")) return "EMA";
                        else if (key.contains("rsi")) return "RSI";
                        else if (key.contains("volume")) return "Volume";
                        else if (key.contains("price")) return "Price";
                        else if (key.contains("crossover") || key.contains("crossdown")) return "Crossover";
                        else return "Other";
                    },
                    Collectors.counting()
                ));
    }
    
    /**
     * Log detailed indicator satisfaction
     */
    private void logDetailedIndicatorSatisfaction(DynamicFlattenedIndicators indicators, DynamicIndicatorConfig config) {
        log.info("📊 DETAILED INDICATOR ANALYSIS:");
        
        // EMA indicators
        List<String> satisfiedEma = getSatisfiedIndicators(indicators, "ema");
        if (!satisfiedEma.isEmpty()) {
            log.info("  📈 EMA Satisfied: {}", String.join(", ", satisfiedEma));
        }
        
        // RSI indicators
        List<String> satisfiedRsi = getSatisfiedIndicators(indicators, "rsi");
        if (!satisfiedRsi.isEmpty()) {
            log.info("  📊 RSI Satisfied: {}", String.join(", ", satisfiedRsi));
        }
        
        // Volume indicators
        List<String> satisfiedVolume = getSatisfiedIndicators(indicators, "volume");
        if (!satisfiedVolume.isEmpty()) {
            log.info("  📈 Volume Satisfied: {}", String.join(", ", satisfiedVolume));
        }
        
        // Price action indicators
        List<String> satisfiedPrice = getSatisfiedIndicators(indicators, "price");
        if (!satisfiedPrice.isEmpty()) {
            log.info("  💰 Price Action Satisfied: {}", String.join(", ", satisfiedPrice));
        }
    }
    
    /**
     * Get satisfied indicators by prefix
     */
    private List<String> getSatisfiedIndicators(DynamicFlattenedIndicators indicators, String prefix) {
        return indicators.getAllIndicatorKeys().stream()
                .filter(key -> key.contains(prefix))
                .filter(key -> {
                    Boolean value = indicators.getBooleanIndicator(key);
                    return value != null && value;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Log detailed score breakdown
     */
    private void logDetailedScoreBreakdown(StrategyScore strategyScore) {
        log.info("📈 SCORE BREAKDOWN - EMA: {}, RSI: {}, Volume: {}, Price: {}, Futures: {}", 
                String.format("%+.1f", strategyScore.getEmaScore()),
                String.format("%+.1f", strategyScore.getRsiScore()),
                String.format("%+.1f", strategyScore.getVolumeScore()),
                String.format("%+.1f", strategyScore.getPriceActionScore()),
                String.format("%+.1f", strategyScore.getFuturesignalScore()));
    }
    
    /**
     * Log market sentiment summary
     */
    private void logMarketSentimentSummary(double score, DynamicFlattenedIndicators indicators) {
        String sentiment = getMarketSentiment(score);
        String strength = getSignalStrength(score);
        
        log.info("🎭 MARKET SENTIMENT | {} | {} | Indicators Satisfied: {}/{}", 
                sentiment,
                strength,
                countTotalSatisfied(indicators),
                indicators.getAllIndicatorKeys().size());
    }
    
    /**
     * Count total satisfied indicators
     */
    private long countTotalSatisfied(DynamicFlattenedIndicators indicators) {
        return indicators.getAllIndicatorKeys().stream()
                .filter(key -> {
                    Boolean value = indicators.getBooleanIndicator(key);
                    return value != null && value;
                })
                .count();
    }
    
    /**
     * Get market sentiment based on score
     */
    private String getMarketSentiment(double score) {
        if (score >= 6.0) return "🟢 STRONG BULLISH";
        else if (score >= 3.0) return "🟡 MODERATE BULLISH";
        else if (score >= 1.5) return "🟡 WEAK BULLISH";
        else if (score <= -6.0) return "🔴 STRONG BEARISH";
        else if (score <= -3.0) return "🟠 MODERATE BEARISH";
        else if (score <= -1.5) return "🟠 WEAK BEARISH";
        else return "⚪ NEUTRAL";
    }
    
    /**
     * Get signal strength description
     */
    private String getSignalStrength(double score) {
        double absScore = Math.abs(score);
        if (absScore >= 8.0) return "VERY STRONG";
        else if (absScore >= 6.0) return "STRONG";
        else if (absScore >= 3.0) return "MODERATE";
        else if (absScore >= 1.5) return "WEAK";
        else return "VERY WEAK";
    }
    
    /**
     * Get direction description
     */
    private String getDirectionDescription(double score) {
        if (score > 0) return "BULLISH";
        else if (score < 0) return "BEARISH";
        else return "NEUTRAL";
    }
    
    /**
     * Get color-coded symbol based on score strength
     */
    private String getScoreColorSymbol(Double score) {
        if (score == null) return "⚪";
        
        double absScore = Math.abs(score);
        
        if (score > 0) {
            // Bullish signals
            if (absScore >= 8.0) return "🟢";      // Strong Green (STRONG_BULLISH)
            else if (absScore >= 6.0) return "🟢"; // Green (STRONG_BULLISH)
            else if (absScore >= 3.0) return "🟡"; // Light Green (MODERATE_BULLISH)
            else if (absScore >= 1.5) return "🟡"; // Light Green (WEAK_BULLISH)
            else return "⚪";                       // Grey (NEUTRAL)
        } else if (score < 0) {
            // Bearish signals
            if (absScore >= 8.0) return "🔴";      // Strong Red (STRONG_BEARISH)
            else if (absScore >= 6.0) return "🔴"; // Red (STRONG_BEARISH)
            else if (absScore >= 3.0) return "🟠"; // Light Red (MODERATE_BEARISH)
            else if (absScore >= 1.5) return "🟠"; // Light Red (WEAK_BEARISH)
            else return "⚪";                       // Grey (NEUTRAL)
        } else {
            return "⚪"; // Grey (NEUTRAL)
        }
    }
}
