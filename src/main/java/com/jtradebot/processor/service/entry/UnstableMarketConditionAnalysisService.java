package com.jtradebot.processor.service.entry;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.config.ScoringConfigurationService;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.strategy.FlatMarketFilteringConfig;
import com.jtradebot.processor.model.strategy.ScalpingEntryConfig;
import com.zerodhatech.models.Tick;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnstableMarketConditionAnalysisService {

    private final TickDataManager tickDataManager;
    private final DynamicStrategyConfigService configService;
    private final ScoringConfigurationService scoringConfigService;

    public boolean isMarketConditionSuitable(Tick tick, FlattenedIndicators indicators) {
        try {
            // Check if no-trade-zones filtering is enabled
            if (configService.isNoTradeZonesEnabled()) {
                FlexibleFilteringResult filteringResult = checkFlexibleFilteringConditions(tick, indicators);
                return filteringResult.isConditionsMet();
            }
            
            // Fallback to old flat market filtering if no-trade-zones is not enabled
            if (!configService.isFlatMarketFilteringEnabled()) {
                return true; // If filtering is disabled, always return suitable
            }
            EntryFilteringResult filteringResult = checkEntryFilteringConditions(tick, indicators);
            return filteringResult.isConditionsMet();
        } catch (Exception e) {
            log.error("Error checking market condition suitability: {}", e.getMessage());
            return false; // Conservative approach - return false on error
        }
    }

    /**
     * New flexible filtering method using no-trade-zones configuration
     */
    public FlexibleFilteringResult checkFlexibleFilteringConditions(Tick tick, FlattenedIndicators indicators) {
        try {
            String instrumentToken = String.valueOf(tick.getInstrumentToken());
            List<FilterResult> filterResults = new ArrayList<>();
            
            // Get configuration
            ScalpingEntryConfig.NoTradeZonesConfig noTradeZonesConfig = configService.getNoTradeZonesConfig();
            Map<String, ScalpingEntryConfig.NoTradeFilter> filters = configService.getNoTradeFilters();
            int maxOptionalFiltersToIgnore = configService.getMaxOptionalFiltersToIgnore();
            
            // Configuration info removed to reduce log noise
            
            // Reuse existing candle analysis
            CandleAnalysisResult candleAnalysis = analyzeCandleCharacteristics(tick, indicators);
            
            // Check each filter
            for (Map.Entry<String, ScalpingEntryConfig.NoTradeFilter> entry : filters.entrySet()) {
                String filterKey = entry.getKey();
                ScalpingEntryConfig.NoTradeFilter filter = entry.getValue();
                
                if (!filter.getEnabled()) {
                    continue; // Skip disabled filters
                }
                
                FilterResult result = checkIndividualFilter(filterKey, filter, tick, indicators, candleAnalysis);
                filterResults.add(result);
                
                log.debug("🔍 FILTER CHECK - {}: {} - {}", filterKey, result.isPassed() ? "PASS" : "FAIL", result.getDetails());
            }
            
            // Sort by priority (lower number = higher priority)
            filterResults.sort((a, b) -> Integer.compare(a.getPriority(), b.getPriority()));
            
            // Separate mandatory and optional failed filters
            List<FilterResult> mandatoryFailedFilters = filterResults.stream()
                    .filter(result -> !result.isPassed() && result.isMandatory())
                    .collect(Collectors.toList());
            
            List<FilterResult> optionalFailedFilters = filterResults.stream()
                    .filter(result -> !result.isPassed() && !result.isMandatory())
                    .collect(Collectors.toList());
            
            // Check conditions: mandatory filters must all pass, optional filters can fail up to maxOptionalFiltersToIgnore
            boolean mandatoryConditionsMet = mandatoryFailedFilters.isEmpty();
            boolean optionalConditionsMet = optionalFailedFilters.size() <= maxOptionalFiltersToIgnore;
            boolean conditionsMet = mandatoryConditionsMet && optionalConditionsMet;
            
            // Combine the two logs into one comprehensive log line
            if (!mandatoryFailedFilters.isEmpty() || !optionalFailedFilters.isEmpty()) {
                String mandatoryFailedNames = mandatoryFailedFilters.stream()
                        .map(FilterResult::getName)
                        .collect(Collectors.joining(", "));
                String optionalFailedNames = optionalFailedFilters.stream()
                        .map(FilterResult::getName)
                        .collect(Collectors.joining(", "));
                
                log.info("🔍 FLEXIBLE FILTERING RESULT - Mandatory failed: {}, Optional failed: {}, Allowed to ignore optional: {}, Conditions met: {} | Mandatory failed: {} | Optional failed: {}", 
                        mandatoryFailedFilters.size(), optionalFailedFilters.size(), maxOptionalFiltersToIgnore, conditionsMet, 
                        mandatoryFailedNames.isEmpty() ? "none" : mandatoryFailedNames,
                        optionalFailedNames.isEmpty() ? "none" : optionalFailedNames);
            } else {
                log.info("🔍 FLEXIBLE FILTERING RESULT - All filters passed, Conditions met: {}", conditionsMet);
            }
            
            // Build reason message
            StringBuilder reason = new StringBuilder();
            if (conditionsMet) {
                if (mandatoryFailedFilters.isEmpty() && optionalFailedFilters.isEmpty()) {
                    reason.append("All no-trade-zone filters passed");
                } else if (!mandatoryFailedFilters.isEmpty()) {
                    reason.append(String.format("Mandatory filters failed: %s",
                            mandatoryFailedFilters.stream()
                                    .map(FilterResult::getName)
                                    .collect(Collectors.joining(", "))));
                } else {
                    reason.append(String.format("Flexible filtering: %d optional filters failed but %d allowed to ignore. Failed: %s",
                            optionalFailedFilters.size(), maxOptionalFiltersToIgnore,
                            optionalFailedFilters.stream()
                                    .map(FilterResult::getName)
                                    .collect(Collectors.joining(", "))));
                }
            } else {
                if (!mandatoryFailedFilters.isEmpty()) {
                    reason.append(String.format("Mandatory filters failed: %s",
                            mandatoryFailedFilters.stream()
                                    .map(FilterResult::getName)
                                    .collect(Collectors.joining(", "))));
                } else {
                    reason.append(String.format("Optional filtering failed: %d optional filters failed, only %d allowed to ignore. Failed: %s",
                            optionalFailedFilters.size(), maxOptionalFiltersToIgnore,
                            optionalFailedFilters.stream()
                                    .map(FilterResult::getName)
                                    .collect(Collectors.joining(", "))));
                }
            }
            
            return new FlexibleFilteringResult(conditionsMet, filterResults, reason.toString());
            
        } catch (Exception e) {
            log.error("Error checking flexible filtering conditions for tick: {}", tick.getInstrumentToken(), e);
            return new FlexibleFilteringResult(false, new ArrayList<>(), "Error during flexible filtering check: " + e.getMessage());
        }
    }
    
    private FilterResult checkIndividualFilter(String filterKey, ScalpingEntryConfig.NoTradeFilter filter, 
                                             Tick tick, FlattenedIndicators indicators, CandleAnalysisResult candleAnalysis) {
        boolean passed = false;
        String details = "";
        
        switch (filterKey) {
            case "candleHeight":
                passed = candleAnalysis.getCandleHeight() >= filter.getThreshold();
                details = String.format("Candle height: %.2f (threshold: %.2f)", 
                        candleAnalysis.getCandleHeight(), filter.getThreshold());
                break;
                
            case "volumeSurge":
                Double volumeMultiplier = indicators.getVolume_surge_multiplier();
                passed = volumeMultiplier != null && volumeMultiplier > filter.getThreshold();
                details = String.format("Volume surge: %.2fx (threshold: %.2fx)", 
                        volumeMultiplier != null ? volumeMultiplier : 0.0, filter.getThreshold());
                break;
                
            case "bodyRatio":
                passed = candleAnalysis.getBodyRatio() >= filter.getThreshold();
                details = String.format("Body ratio: %.2f (threshold: %.2f)", 
                        candleAnalysis.getBodyRatio(), filter.getThreshold());
                break;
                
            case "ema200Distance":
                Double ema200Distance5min = indicators.getEma200_distance_5min();
                Double ema200_5min = indicators.getEma200_5min();
                double maxAllowedDistance = ema200_5min != null ? ema200_5min * filter.getThreshold() : 0.0;
                passed = ema200Distance5min != null && Math.abs(ema200Distance5min) <= maxAllowedDistance;
                details = String.format("EMA 200 distance: %.2f (max allowed: %.2f)", 
                        ema200Distance5min != null ? Math.abs(ema200Distance5min) : 0.0, maxAllowedDistance);
                break;
                
            case "priceBetweenEma34AndEma200":
                Double ema34_5min_price = indicators.getEma34_5min();
                Double ema200_5min_price = indicators.getEma200_5min();
                double currentIndexPrice = tick.getLastTradedPrice();
                
                if (ema34_5min_price != null && ema200_5min_price != null) {
                    double minEma = Math.min(ema34_5min_price, ema200_5min_price);
                    double maxEma = Math.max(ema34_5min_price, ema200_5min_price);
                    boolean priceBetween = currentIndexPrice >= minEma && currentIndexPrice <= maxEma;
                    passed = !priceBetween; // Filter passes when price is NOT between EMAs
                    details = String.format("Price: %.2f, EMA34: %.2f, EMA200: %.2f, Between: %s", 
                            currentIndexPrice, ema34_5min_price, ema200_5min_price, priceBetween);
                } else {
                    passed = true; // Pass if EMAs not available
                    details = "EMA34 or EMA200 not available";
                }
                break;
                
            case "overboughtOversold":
                boolean overbought = Boolean.TRUE.equals(indicators.getRsi_1min_gt_80()) ||
                        Boolean.TRUE.equals(indicators.getRsi_5min_gt_80()) ||
                        Boolean.TRUE.equals(indicators.getRsi_15min_gt_80());
                        
                boolean oversold = Boolean.TRUE.equals(indicators.getRsi_1min_lt_20()) ||
                        Boolean.TRUE.equals(indicators.getRsi_5min_lt_20()) ||
                        Boolean.TRUE.equals(indicators.getRsi_15min_lt_20());
                        
                passed = !overbought && !oversold;
                details = String.format("Overbought: %s, Oversold: %s", overbought, oversold);
                break;
                
            case "directionalStrength":
                double directionalStrength = calculateDirectionalStrength(tick, indicators);
                passed = directionalStrength >= filter.getThreshold();
                details = String.format("Directional strength: %.2f (threshold: %.2f)", 
                        directionalStrength, filter.getThreshold());
                break;
                
            case "consecutiveSameColorCandles":
                String instrumentToken = String.valueOf(tick.getInstrumentToken());
                
                // Get timeframe from filter configuration, default to FIVE_MIN
                String timeframeStr = filter.getTimeframe() != null ? filter.getTimeframe() : "FIVE_MIN";
                com.jtradebot.processor.model.enums.CandleTimeFrameEnum timeframe = com.jtradebot.processor.model.enums.CandleTimeFrameEnum.valueOf(timeframeStr);
                
                // Get analysis window from filter configuration, default to 10
                int analysisWindow = filter.getAnalysisWindow() != null ? filter.getAnalysisWindow() : 10;
                
                BarSeries barSeries = tickDataManager.getBarSeriesForTimeFrame(instrumentToken, timeframe);
                int consecutiveSameColorCount = calculateConsecutiveSameColorCandles(barSeries, analysisWindow);
                passed = consecutiveSameColorCount < filter.getMaxConsecutiveCount();
                details = String.format("Consecutive same color candles: %d (max allowed: %d, timeframe: %s, analysis window: %d)", 
                        consecutiveSameColorCount, filter.getMaxConsecutiveCount(), timeframeStr, analysisWindow);
                break;
                
            default:
                passed = true; // Unknown filter, pass by default
                details = "Unknown filter type";
                break;
        }
        
        return new FilterResult(filter.getName(), filter.getDescription(), filter.getPriority(), 
                               passed, details, filterKey, filter.getMandatory() != null ? filter.getMandatory() : false);
    }

    public double calculateDirectionalStrength(Tick tick, FlattenedIndicators indicators) {
        try {
            int bullishSignals = 0;
            int bearishSignals = 0;
            int totalSignals = 0;

            // EMA signals
            if (Boolean.TRUE.equals(indicators.getEma5_5min_gt_ema34_5min())) bullishSignals++;
            if (Boolean.TRUE.equals(indicators.getEma5_1min_gt_ema34_1min())) bullishSignals++;
            if (Boolean.TRUE.equals(indicators.getEma5_15min_gt_ema34_15min())) bullishSignals++;

            if (Boolean.TRUE.equals(indicators.getEma5_5min_lt_ema34_5min())) bearishSignals++;
            if (Boolean.TRUE.equals(indicators.getEma5_1min_lt_ema34_1min())) bearishSignals++;
            if (Boolean.TRUE.equals(indicators.getEma5_15min_lt_ema34_15min())) bearishSignals++;
            totalSignals += 6;

            // RSI signals
            if (Boolean.TRUE.equals(indicators.getRsi_5min_gt_60())) bullishSignals++;
            if (Boolean.TRUE.equals(indicators.getRsi_1min_gt_60())) bullishSignals++;
            if (Boolean.TRUE.equals(indicators.getRsi_15min_gt_60())) bullishSignals++;

            if (Boolean.TRUE.equals(indicators.getRsi_5min_lt_40())) bearishSignals++;
            if (Boolean.TRUE.equals(indicators.getRsi_1min_lt_40())) bearishSignals++;
            if (Boolean.TRUE.equals(indicators.getRsi_15min_lt_40())) bearishSignals++;
            totalSignals += 6;

            // Price action signals
            if (Boolean.TRUE.equals(indicators.getPrice_gt_vwap_5min())) bullishSignals++;
            if (Boolean.TRUE.equals(indicators.getPrice_gt_vwap_1min())) bullishSignals++;
            if (Boolean.TRUE.equals(indicators.getPrice_gt_vwap_15min())) bullishSignals++;

            if (Boolean.TRUE.equals(indicators.getPrice_lt_vwap_5min())) bearishSignals++;
            if (Boolean.TRUE.equals(indicators.getPrice_lt_vwap_1min())) bearishSignals++;
            if (Boolean.TRUE.equals(indicators.getPrice_lt_vwap_15min())) bearishSignals++;
            totalSignals += 6;

            // Calculate directional strength as the ratio of dominant signals

            int dominantSignals = Math.max(bullishSignals, bearishSignals);
            double directionalStrength = (double) dominantSignals / totalSignals;

            log.info("directionalStrength calculation - Bullish: {}, Bearish: {}, Total: {}, Strength: {}",
                    bullishSignals, bearishSignals, totalSignals, String.format("%.2f", directionalStrength));
            return Math.min(directionalStrength, 1.0);

        } catch (Exception e) {
            log.error("Error calculating directional strength: {}", e.getMessage());
            return 0.0;
        }
    }


    public CandleAnalysisResult analyzeCandleCharacteristics(Tick tick, FlattenedIndicators indicators) {
        try {
            String instrumentToken = String.valueOf(tick.getInstrumentToken());
            CandleAnalysisResult result = new CandleAnalysisResult();

            // Get recent bars for analysis
            BarSeries oneMinSeries = tickDataManager.getBarSeriesForTimeFrame(instrumentToken, ONE_MIN);

            if (oneMinSeries == null || oneMinSeries.getBarCount() < 10) {
                log.warn("Insufficient data for candle analysis: {}", instrumentToken);
                return result;
            }

            // Analyze current candle
            Bar currentBar = oneMinSeries.getLastBar();
            double candleHeight = currentBar.getHighPrice().minus(currentBar.getLowPrice()).doubleValue();
            double bodySize = Math.abs(currentBar.getClosePrice().minus(currentBar.getOpenPrice()).doubleValue());
            double bodyRatio = candleHeight > 0 ? bodySize / candleHeight : 0.0;

            result.setCandleHeight(candleHeight);
            result.setBodyRatio(bodyRatio);
            result.setDoji(bodyRatio <= 0.1);
            result.setSpinningTop(bodyRatio <= 0.3);
            result.setSmallBody(bodyRatio <= 0.2);
            result.setLongBody(bodyRatio >= 0.60);

            // Analyze recent candles for patterns
            analyzeRecentCandles(oneMinSeries, result);

            return result;

        } catch (Exception e) {
            log.error("Error analyzing candle characteristics: {}", e.getMessage());
            return new CandleAnalysisResult();
        }
    }

    private void analyzeRecentCandles(BarSeries barSeries, CandleAnalysisResult result) {
        try {
            // Get configuration
            FlatMarketFilteringConfig config = configService.getFlatMarketFilteringConfig();
            FlatMarketFilteringConfig.LookbackSettings lookbackSettings = config.getLookbackSettings();

            int consecutiveSmallCandles = 0;
            int consecutiveDoji = 0;
            int consecutiveSpinningTop = 0;
            double totalHeight = 0.0;
            double totalBodyRatio = 0.0;
            int analyzedBars = 0;

            int lookbackBars = Math.min(lookbackSettings.getCandleAnalysis(), barSeries.getBarCount());

            for (int i = barSeries.getBarCount() - lookbackBars; i < barSeries.getBarCount(); i++) {
                Bar bar = barSeries.getBar(i);
                double height = bar.getHighPrice().minus(bar.getLowPrice()).doubleValue();
                double bodySize = Math.abs(bar.getClosePrice().minus(bar.getOpenPrice()).doubleValue());
                double bodyRatio = height > 0 ? bodySize / height : 0.0;

                totalHeight += height;
                totalBodyRatio += bodyRatio;
                analyzedBars++;

                // Check for consecutive patterns using configuration thresholds
                if (bodyRatio <= 0.2) {
                    consecutiveSmallCandles++;
                } else {
                    consecutiveSmallCandles = 0;
                }

                if (bodyRatio <= 0.1) {
                    consecutiveDoji++;
                } else {
                    consecutiveDoji = 0;
                }

                if (bodyRatio <= 0.3) {
                    consecutiveSpinningTop++;
                } else {
                    consecutiveSpinningTop = 0;
                }
            }

            result.setConsecutiveSmallCandles(consecutiveSmallCandles);
            result.setConsecutiveDoji(consecutiveDoji);
            result.setConsecutiveSpinningTop(consecutiveSpinningTop);
            result.setAverageCandleHeight(analyzedBars > 0 ? totalHeight / analyzedBars : 0.0);
            result.setAverageBodyRatio(analyzedBars > 0 ? totalBodyRatio / analyzedBars : 0.0);

        } catch (Exception e) {
            log.error("Error analyzing recent candles: {}", e.getMessage());
        }
    }

    /**
     * Calculate the number of consecutive same color candles
     * @param barSeries The bar series to analyze
     * @param lookbackBars Number of bars to look back (default 10)
     * @return Number of consecutive same color candles
     */
    private int calculateConsecutiveSameColorCandles(BarSeries barSeries, int lookbackBars) {
        try {
            if (barSeries.getBarCount() < lookbackBars) {
                return 0;
            }

            int consecutiveCount = 0;
            String currentColor = null;

            // Start from the most recent bar and go backwards
            for (int i = barSeries.getBarCount() - 1; i >= barSeries.getBarCount() - lookbackBars; i--) {
                Bar bar = barSeries.getBar(i);
                String candleColor = determineCandleColor(bar);

                if (currentColor == null) {
                    // First candle
                    currentColor = candleColor;
                    consecutiveCount = 1;
                } else if (candleColor.equals(currentColor)) {
                    // Same color as previous
                    consecutiveCount++;
                } else {
                    // Different color, break the streak
                    break;
                }
            }

            return consecutiveCount;

        } catch (Exception e) {
            log.error("Error calculating consecutive same color candles: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Determine the color of a candle (GREEN, RED, or NEUTRAL)
     * @param bar The bar to analyze
     * @return Color of the candle
     */
    private String determineCandleColor(Bar bar) {
        double openPrice = bar.getOpenPrice().doubleValue();
        double closePrice = bar.getClosePrice().doubleValue();
        
        if (closePrice > openPrice) {
            return "GREEN";
        } else if (closePrice < openPrice) {
            return "RED";
        } else {
            return "NEUTRAL"; // Doji or neutral candle
        }
    }

    /**
     * Check entry filtering conditions: minimum candle height ≥ 8, volume surge > 10x, good candle body ratio (>0.6),
     * EMA 200 distance check (0.5% of index price), and index price between EMA34 and EMA200 check
     * This method reuses existing calculations from the service
     */
    public EntryFilteringResult checkEntryFilteringConditions(Tick tick, FlattenedIndicators indicators) {
        try {
            String instrumentToken = String.valueOf(tick.getInstrumentToken());

            // Reuse existing candle analysis
            CandleAnalysisResult candleAnalysis = analyzeCandleCharacteristics(tick, indicators);

            // Check minimum candle height ≥ 8
            boolean candleHeightOk = candleAnalysis.getCandleHeight() >= 8.0;

            // Check volume surge > configured threshold
            Double volumeMultiplier = indicators.getVolume_surge_multiplier();
            boolean volumeSurgeOk = volumeMultiplier != null && volumeMultiplier > scoringConfigService.getScoringConfig().getQualityScoring().getVolumeQuality().getVolumeMultiplierThreshold();

            // Check candle body ratio > 0.6
            boolean minBodyRatioOk = candleAnalysis.getBodyRatio() >= 0.50;

            // NEW FILTER: Check if body ratio > 0.95 and candle height > ATR 5min (DO NOT ENTRY)
            boolean candleHeightExceedsATR = false;

            // Get 5-minute ATR value
            // Map<String, Double> atrValues = atrIndicator.calculateATRValues(tick);

            BarSeries barSeries = tickDataManager.getBarSeriesForTimeFrame(instrumentToken, FIVE_MIN);
            ATRIndicator atrIndicator = new ATRIndicator(barSeries, 14);
            double atr5min = atrIndicator.getValue(barSeries.getBarCount() - 1).doubleValue();

            if (atr5min > 0) {
                candleHeightExceedsATR = candleAnalysis.getCandleHeight() > atr5min;
            }


            // Body ratio filter: must be >= 0.50 AND NOT (body ratio > 0.95 AND candle height > ATR 5min)
            boolean bodyRatioFilterOk = minBodyRatioOk && !(candleHeightExceedsATR && candleAnalysis.getBodyRatio() > 0.95);

            // Check EMA 200 distance in 5min timeframe - should not be more than 0.6% of EMA 200
            Double ema200Distance5min = indicators.getEma200_distance_5min();
            double currentIndexPrice = tick.getLastTradedPrice();
            Double ema200_5min = indicators.getEma200_5min();
            double maxAllowedDistance = ema200_5min != null ? ema200_5min * 0.006 : 0.0; // 0.6% of EMA 200
            boolean ema200DistanceOk = ema200Distance5min != null && Math.abs(ema200Distance5min) <= maxAllowedDistance;

            // Check if current index price is between EMA34 and EMA200 (NEW FILTER)
            Double ema34_5min = indicators.getEma34_5min();
            boolean priceBetweenEma34AndEma200 = false;

            if (ema34_5min != null && ema200_5min != null) {
                double minEma = Math.min(ema34_5min, ema200_5min);
                double maxEma = Math.max(ema34_5min, ema200_5min);
                priceBetweenEma34AndEma200 = currentIndexPrice >= minEma && currentIndexPrice <= maxEma;

            } else {
                log.warn("⚠️ EMA34 or EMA200 values not available for price between check");
            }

            // Check for overbought/oversold conditions to block risky entries
            boolean overboughtOversoldOk = true;
            String overboughtOversoldReason = null;

            // Check for overbought conditions (RSI > 80) - avoid buying at extreme highs
            if (Boolean.TRUE.equals(indicators.getRsi_1min_gt_80()) ||
                    Boolean.TRUE.equals(indicators.getRsi_5min_gt_80()) ||
                    Boolean.TRUE.equals(indicators.getRsi_15min_gt_80())) {
                overboughtOversoldOk = false;
                overboughtOversoldReason = "RSI overbought detected (>80) - avoiding entry at extreme highs";
            }

            // Check for oversold conditions (RSI < 20) - avoid selling at extreme lows
            if (Boolean.TRUE.equals(indicators.getRsi_1min_lt_20()) ||
                    Boolean.TRUE.equals(indicators.getRsi_5min_lt_20()) ||
                    Boolean.TRUE.equals(indicators.getRsi_15min_lt_20())) {
                overboughtOversoldOk = false;
                overboughtOversoldReason = "RSI oversold detected (<20) - avoiding entry at extreme lows";
            }

            // All conditions must be met (including the new filter)
            boolean allConditionsMet = candleHeightOk && volumeSurgeOk && bodyRatioFilterOk && ema200DistanceOk && !priceBetweenEma34AndEma200 && overboughtOversoldOk;

            // Build detailed reason message
            StringBuilder reason = new StringBuilder();
            if (allConditionsMet) {
                reason.append("All entry filtering conditions met");
            } else {
                List<String> failedConditions = new ArrayList<>();
                if (!candleHeightOk) {
                    failedConditions.add(String.format("Candle height %.2f < 8.0", candleAnalysis.getCandleHeight()));
                }
                if (!volumeSurgeOk) {
                    failedConditions.add(String.format("Volume surge %.2fx <= 10x",
                            volumeMultiplier != null ? volumeMultiplier : 0.0));
                }
                if (!bodyRatioFilterOk) {
                    if (!minBodyRatioOk) {
                        failedConditions.add(String.format("Body ratio %.2f < 0.60", candleAnalysis.getBodyRatio()));
                    } else {
                        failedConditions.add(String.format("candle height %.2f > ATR 5min %.2f (DO NOT ENTRY)", candleAnalysis.getCandleHeight(), atr5min));
                    }
                }
                if (!ema200DistanceOk) {
                    failedConditions.add(String.format("EMA 200 distance %.2f > %.2f (0.5%% of price)",
                            ema200Distance5min != null ? Math.abs(ema200Distance5min) : 0.0, maxAllowedDistance));
                }
                if (priceBetweenEma34AndEma200) {
                    failedConditions.add(String.format("Price %.2f is between EMA34 %.2f and EMA200 %.2f",
                            currentIndexPrice, ema34_5min, ema200_5min));
                }
                if (!overboughtOversoldOk && overboughtOversoldReason != null) {
                    failedConditions.add(overboughtOversoldReason);
                }
                reason.append("Entry filtering failed: ").append(String.join(", ", failedConditions));
            }

            return new EntryFilteringResult(allConditionsMet, candleAnalysis.getCandleHeight(),
                    volumeMultiplier, candleAnalysis.getBodyRatio(), ema200Distance5min, atr5min, reason.toString());

        } catch (Exception e) {
            log.error("Error checking entry filtering conditions for tick: {}", tick.getInstrumentToken(), e);
            return new EntryFilteringResult(false, 0.0, 0.0, 0.0, null,
                    "Error during filtering check: " + e.getMessage());
        }
    }


    // SIMPLIFIED: Market condition details for database storage
    public Map<String, Object> getStructuredMarketConditionDetails(Tick tick, FlattenedIndicators indicators) {
        try {
            Map<String, Object> marketDetails = new HashMap<>();

            // Get entry filtering result for basic validation
            EntryFilteringResult filteringResult = checkEntryFilteringConditions(tick, indicators);

            // Basic entry filtering details
            marketDetails.put("entryFilteringPassed", filteringResult.isConditionsMet());
            marketDetails.put("candleHeight", Math.round(filteringResult.getCandleHeight() * 100.0) / 100.0);
            marketDetails.put("volumeSurgeMultiplier", filteringResult.getVolumeMultiplier() != null ?
                    Math.round(filteringResult.getVolumeMultiplier() * 100.0) / 100.0 : 0.0);
            marketDetails.put("bodyRatio", Math.round(filteringResult.getBodyRatio() * 100.0) / 100.0);
            marketDetails.put("ema200Distance5min", filteringResult.getEma200Distance5min() != null ?
                    Math.round(filteringResult.getEma200Distance5min() * 100.0) / 100.0 : 0.0);
            marketDetails.put("atr5min", filteringResult.getAtr5min() != null ?
                    Math.round(filteringResult.getAtr5min() * 100.0) / 100.0 : 0.0);

            // New EMA values for additional filtering
            marketDetails.put("ema34_5min", indicators.getEma34_5min() != null ?
                    Math.round(indicators.getEma34_5min() * 100.0) / 100.0 : 0.0);
            marketDetails.put("ema200_5min", indicators.getEma200_5min() != null ?
                    Math.round(indicators.getEma200_5min() * 100.0) / 100.0 : 0.0);

            // ATR values for different timeframes
            BarSeries barSeries5Min = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tick.getInstrumentToken()), FIVE_MIN);
            ATRIndicator atrIndicator5Min = new ATRIndicator(barSeries5Min, 14);
            double atr5min = atrIndicator5Min.getValue(barSeries5Min.getBarCount() - 1).doubleValue();

            BarSeries barSeries15Min = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tick.getInstrumentToken()), FIFTEEN_MIN);
            ATRIndicator atrIndicator15Min = new ATRIndicator(barSeries15Min, 14);
            double atr15min = atrIndicator15Min.getValue(barSeries15Min.getBarCount() - 1).doubleValue();

            BarSeries barSeries1Min = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tick.getInstrumentToken()), ONE_MIN);
            ATRIndicator atrIndicator1Min = new ATRIndicator(barSeries1Min, 14);
            double atr1min = atrIndicator1Min.getValue(barSeries1Min.getBarCount() - 1).doubleValue();

            // Add ATR values for different timeframes to the map
            marketDetails.put("atr5min", atr5min);
            marketDetails.put("atr15min", atr15min);
            marketDetails.put("atr1min", atr1min);

            // Directional strength (kept for reference)
            double directionalStrength = calculateDirectionalStrength(tick, indicators);
            marketDetails.put("directionalStrength", Math.round(directionalStrength * 100.0) / 100.0);

            // Candle analysis details (simplified)
            CandleAnalysisResult candleAnalysis = analyzeCandleCharacteristics(tick, indicators);
            if (candleAnalysis != null) {
                marketDetails.put("isLongBody", candleAnalysis.isLongBody());
                marketDetails.put("isDoji", candleAnalysis.isDoji());
                marketDetails.put("consecutiveDoji", candleAnalysis.getConsecutiveDoji());
                marketDetails.put("consecutiveSpinningTop", candleAnalysis.getConsecutiveSpinningTop());
                marketDetails.put("consecutiveSmallCandles", candleAnalysis.getConsecutiveSmallCandles());
            }

            // Market suitability status
            boolean isMarketSuitable = isMarketConditionSuitable(tick, indicators);
            marketDetails.put("marketSuitable", isMarketSuitable);

            return marketDetails;

        } catch (Exception e) {
            log.error("Error getting structured market condition details: {}", e.getMessage());
            return new HashMap<>();
        }
    }


    /**
     * Result class for candle analysis
     */
    @Setter
    @Getter
    public static class CandleAnalysisResult {
        // Getters and setters
        private double candleHeight;
        private double bodyRatio;
        private boolean isDoji;
        private boolean isSpinningTop;
        private boolean isSmallBody;
        private boolean isLongBody;
        private int consecutiveSmallCandles;
        private int consecutiveDoji;
        private int consecutiveSpinningTop;
        private double averageCandleHeight;
        private double averageBodyRatio;

        // Constructors, getters, setters
        public CandleAnalysisResult() {
        }
    }

    /**
     * Result class for entry filtering conditions
     */
    @Setter
    @Getter
    public static class EntryFilteringResult {
        private boolean conditionsMet;
        private double candleHeight;
        private Double volumeMultiplier;
        private double bodyRatio;
        private Double ema200Distance5min; // Added EMA 200 distance field
        private Double atr5min; // Added ATR 5min field for body ratio filter
        private String reason;

        public EntryFilteringResult(boolean conditionsMet, double candleHeight,
                                    Double volumeMultiplier, double bodyRatio, String reason) {
            this.conditionsMet = conditionsMet;
            this.candleHeight = candleHeight;
            this.volumeMultiplier = volumeMultiplier;
            this.bodyRatio = bodyRatio;
            this.reason = reason;
        }

        public EntryFilteringResult(boolean conditionsMet, double candleHeight,
                                    Double volumeMultiplier, double bodyRatio, Double ema200Distance5min, String reason) {
            this.conditionsMet = conditionsMet;
            this.candleHeight = candleHeight;
            this.volumeMultiplier = volumeMultiplier;
            this.bodyRatio = bodyRatio;
            this.ema200Distance5min = ema200Distance5min;
            this.reason = reason;
        }

        public EntryFilteringResult(boolean conditionsMet, double candleHeight,
                                    Double volumeMultiplier, double bodyRatio, Double ema200Distance5min, Double atr5min, String reason) {
            this.conditionsMet = conditionsMet;
            this.candleHeight = candleHeight;
            this.volumeMultiplier = volumeMultiplier;
            this.bodyRatio = bodyRatio;
            this.ema200Distance5min = ema200Distance5min;
            this.atr5min = atr5min;
            this.reason = reason;
        }
    }

    /**
     * Result class for flexible filtering using no-trade-zones configuration
     */
    @Setter
    @Getter
    public static class FlexibleFilteringResult {
        private boolean conditionsMet;
        private List<FilterResult> filterResults;
        private String reason;

        public FlexibleFilteringResult(boolean conditionsMet, List<FilterResult> filterResults, String reason) {
            this.conditionsMet = conditionsMet;
            this.filterResults = filterResults;
            this.reason = reason;
        }
    }

    /**
     * Result class for individual filter checks
     */
    @Setter
    @Getter
    public static class FilterResult {
        private String name;
        private String description;
        private int priority;
        private boolean passed;
        private String details;
        private String filterKey;
        private boolean mandatory;

        public FilterResult(String name, String description, int priority, boolean passed, String details, String filterKey, boolean mandatory) {
            this.name = name;
            this.description = description;
            this.priority = priority;
            this.passed = passed;
            this.details = details;
            this.filterKey = filterKey;
            this.mandatory = mandatory;
        }
    }

}
