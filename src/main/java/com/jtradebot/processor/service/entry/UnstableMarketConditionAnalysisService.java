package com.jtradebot.processor.service.entry;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.model.strategy.FlatMarketFilteringConfig;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;

import com.zerodhatech.models.Tick;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnstableMarketConditionAnalysisService {

    private final TickDataManager tickDataManager;
    private final DynamicStrategyConfigService configService;


    private MarketConditionResult analyzeMarketCondition(Tick tick, FlattenedIndicators indicators) {
        try {
            String instrumentToken = String.valueOf(tick.getInstrumentToken());

            // Analyze candle characteristics
            CandleAnalysisResult candleAnalysis = analyzeCandleCharacteristics(tick, indicators);

            // Calculate directional strength
            double directionalStrength = calculateDirectionalStrength(tick, indicators);

            // Calculate volatility score
            double volatilityScore = calculateVolatilityScore(tick, indicators);

            // Calculate candle size score
            double candleSizeScore = calculateCandleSizeScore(candleAnalysis);

            // Determine if market is flat
            boolean isFlatMarket = isFlatMarket(candleAnalysis, directionalStrength, volatilityScore);

            // Calculate overall score
            double overallScore = calculateOverallScore(directionalStrength, volatilityScore, candleSizeScore);

            // Determine if suitable for trading (avoiding recursion)
            boolean isSuitableForTrading = determineSuitability(isFlatMarket, directionalStrength,
                    candleAnalysis, indicators);

            // Build reason message
            String reason = buildReasonMessage(isFlatMarket, isSuitableForTrading, candleAnalysis,
                    directionalStrength, volatilityScore, overallScore);

            return new MarketConditionResult(isFlatMarket, isSuitableForTrading, directionalStrength,
                    volatilityScore, candleSizeScore, overallScore, reason, candleAnalysis);

        } catch (Exception e) {
            log.error("Error analyzing market condition for tick: {}", tick.getInstrumentToken(), e);
            return new MarketConditionResult(true, false, 0.0, 0.0, 0.0, 0.0,
                    "Error during analysis: " + e.getMessage(), null);
        }
    }


    public boolean isMarketConditionSuitable(Tick tick, FlattenedIndicators indicators) {
        try {
            // Check if flat market filtering is enabled
            if (!configService.isFlatMarketFilteringEnabled()) {
                return true; // If filtering is disabled, always return suitable
            }

            MarketConditionResult result = analyzeMarketCondition(tick, indicators);

            // Get thresholds from configuration
            double minDirectionalStrength = configService.getFlatMarketFilteringConfig()
                    .getRequirements().getMinDirectionalStrength();
            double minVolumeMultiplier = configService.getFlatMarketFilteringConfig()
                    .getRequirements().getMinVolumeMultiplier();

            // Check if market conditions meet minimum requirements
            boolean directionalStrengthOk = result.getDirectionalStrength() >= minDirectionalStrength;
            boolean volumeOk = indicators.getVolume_surge_multiplier() != null &&
                    indicators.getVolume_surge_multiplier() >= minVolumeMultiplier;

            // Additional comprehensive flat market checks
            boolean comprehensiveFlatCheck = performComprehensiveFlatMarketCheck(tick, indicators, result);

            boolean isSuitable = !result.isFlatMarket() && directionalStrengthOk && volumeOk && comprehensiveFlatCheck;

            if (!isSuitable) {
                if (!result.isFlatMarket()) {
                    log.debug("Market condition unsuitable - Flat: {}, Directional: {}, Volume: {}, Comprehensive: {}",
                            result.isFlatMarket(), directionalStrengthOk, volumeOk, comprehensiveFlatCheck);
                }
            }

            return isSuitable;

        } catch (Exception e) {
            log.error("Error checking market condition suitability: {}", e.getMessage());
            return false; // Conservative approach - return false on error
        }
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
            if (Boolean.TRUE.equals(indicators.getRsi_5min_gt_56())) bullishSignals++;
            if (Boolean.TRUE.equals(indicators.getRsi_1min_gt_56())) bullishSignals++;
            if (Boolean.TRUE.equals(indicators.getRsi_15min_gt_56())) bullishSignals++;

            if (Boolean.TRUE.equals(indicators.getRsi_5min_lt_44())) bearishSignals++;
            if (Boolean.TRUE.equals(indicators.getRsi_1min_lt_44())) bearishSignals++;
            if (Boolean.TRUE.equals(indicators.getRsi_15min_lt_44())) bearishSignals++;
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
            BarSeries fiveMinSeries = tickDataManager.getBarSeriesForTimeFrame(instrumentToken, FIVE_MIN);

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
            result.setLongBody(bodyRatio >= 0.6);

            // Analyze recent candles for patterns
            analyzeRecentCandles(oneMinSeries, result);

            return result;

        } catch (Exception e) {
            log.error("Error analyzing candle characteristics: {}", e.getMessage());
            return new CandleAnalysisResult();
        }
    }


    public double calculateVolatilityScore(Tick tick, FlattenedIndicators indicators) {
        try {
            String instrumentToken = String.valueOf(tick.getInstrumentToken());
            BarSeries fiveMinSeries = tickDataManager.getBarSeriesForTimeFrame(instrumentToken, FIVE_MIN);

            // Get configuration
            FlatMarketFilteringConfig config = configService.getFlatMarketFilteringConfig();
            FlatMarketFilteringConfig.AtrSettings atrSettings = config.getAtrSettings();
            FlatMarketFilteringConfig.LookbackSettings lookbackSettings = config.getLookbackSettings();

            if (fiveMinSeries == null || fiveMinSeries.getBarCount() < atrSettings.getPeriod()) {
                return 0.0;
            }

            // Calculate ATR using configuration
            ClosePriceIndicator closePrice = new ClosePriceIndicator(fiveMinSeries);
            ATRIndicator atrIndicator = new ATRIndicator(fiveMinSeries, atrSettings.getPeriod());
            double atr = atrIndicator.getValue(fiveMinSeries.getBarCount() - 1).doubleValue();

            // Calculate price range over recent bars using configuration
            double maxHigh = Double.MIN_VALUE;
            double minLow = Double.MAX_VALUE;
            int lookbackBars = Math.min(lookbackSettings.getVolatilityAnalysis(), fiveMinSeries.getBarCount());

            for (int i = fiveMinSeries.getBarCount() - lookbackBars; i < fiveMinSeries.getBarCount(); i++) {
                Bar bar = fiveMinSeries.getBar(i);
                maxHigh = Math.max(maxHigh, bar.getHighPrice().doubleValue());
                minLow = Math.min(minLow, bar.getLowPrice().doubleValue());
            }

            double priceRange = maxHigh - minLow;

            // Get thresholds from configuration
            double minATR = atrSettings.getMinATR();
            double minPriceRange = config.getRequirements().getMinPriceRange();

            // Validate inputs to prevent NaN
            if (Double.isNaN(atr) || Double.isInfinite(atr) || atr < 0) {
                atr = 0.0;
            }
            if (Double.isNaN(minATR) || Double.isInfinite(minATR) || minATR <= 0) {
                minATR = 0.1; // Default minimum value
            }
            if (Double.isNaN(priceRange) || Double.isInfinite(priceRange) || priceRange < 0) {
                priceRange = 0.0;
            }
            if (Double.isNaN(minPriceRange) || Double.isInfinite(minPriceRange) || minPriceRange <= 0) {
                minPriceRange = 0.1; // Default minimum value
            }

            // Calculate volatility score with safety checks
            double atrScore = minATR > 0 ? Math.min(atr / minATR, 1.0) : 0.0;
            double rangeScore = minPriceRange > 0 ? Math.min(priceRange / minPriceRange, 1.0) : 0.0;

            // Validate scores
            if (Double.isNaN(atrScore) || Double.isInfinite(atrScore)) {
                atrScore = 0.0;
            }
            if (Double.isNaN(rangeScore) || Double.isInfinite(rangeScore)) {
                rangeScore = 0.0;
            }

            double volatilityScore = (atrScore + rangeScore) / 2.0;

            // Final validation
            if (Double.isNaN(volatilityScore) || Double.isInfinite(volatilityScore)) {
                volatilityScore = 0.0;
            }

            return Math.min(volatilityScore, 1.0);

        } catch (Exception e) {
            log.error("Error calculating volatility score: {}", e.getMessage());
            return 0.0;
        }
    }

    private double calculateCandleSizeScore(CandleAnalysisResult candleAnalysis) {
        try {
            // Get thresholds from configuration
            double minCandleBodyRatio = configService.getFlatMarketFilteringConfig()
                    .getRequirements().getMinCandleBodyRatio();

            // Validate inputs to prevent NaN
            double bodyRatio = candleAnalysis.getBodyRatio();
            if (Double.isNaN(bodyRatio) || Double.isInfinite(bodyRatio) || bodyRatio < 0) {
                bodyRatio = 0.0;
            }
            if (Double.isNaN(minCandleBodyRatio) || Double.isInfinite(minCandleBodyRatio) || minCandleBodyRatio <= 0) {
                minCandleBodyRatio = 0.1; // Default minimum value
            }

            // Calculate body ratio score with safety check
            double bodyRatioScore = minCandleBodyRatio > 0 ? Math.min(bodyRatio / minCandleBodyRatio, 1.0) : 0.0;

            // Validate bodyRatioScore
            if (Double.isNaN(bodyRatioScore) || Double.isInfinite(bodyRatioScore)) {
                bodyRatioScore = 0.0;
            }

            // Penalize for small candles
            double smallCandlePenalty = 0.0;
            if (candleAnalysis.isSmallBody()) smallCandlePenalty += 0.3;
            if (candleAnalysis.isDoji()) smallCandlePenalty += 0.2;
            if (candleAnalysis.isSpinningTop()) smallCandlePenalty += 0.1;

            // Calculate final score
            double score = bodyRatioScore - smallCandlePenalty;

            // Final validation
            if (Double.isNaN(score) || Double.isInfinite(score)) {
                score = 0.0;
            }

            return Math.max(score, 0.0);

        } catch (Exception e) {
            log.error("Error calculating candle size score: {}", e.getMessage());
            return 0.0;
        }
    }

    private boolean isFlatMarket(CandleAnalysisResult candleAnalysis, double directionalStrength, double volatilityScore) {
        try {
            // Get configuration
            FlatMarketFilteringConfig config = configService.getFlatMarketFilteringConfig();
            FlatMarketFilteringConfig.Requirements requirements = config.getRequirements();
            FlatMarketFilteringConfig.Thresholds thresholds = config.getThresholds();

            // Check various flat market conditions using configuration
            boolean lowDirectionalStrength = directionalStrength < requirements.getMinDirectionalStrength();
            boolean lowVolatility = volatilityScore < thresholds.getVolatilityScore().getLowThreshold();
            boolean tooManyDoji = candleAnalysis.getConsecutiveDoji() > requirements.getMaxConsecutiveDoji();
            boolean tooManySpinningTop = candleAnalysis.getConsecutiveSpinningTop() > requirements.getMaxConsecutiveSpinningTop();

            // Additional flat market conditions using configuration
            boolean veryLowDirectionalStrength = directionalStrength < thresholds.getDirectionalStrength().getVeryLowThreshold();
            boolean veryLowVolatility = volatilityScore < thresholds.getVolatilityScore().getVeryLowThreshold();
            boolean tooManySmallCandles = candleAnalysis.getConsecutiveSmallCandles() > requirements.getMaxConsecutiveSmallCandles();

            // Market is flat if any of these conditions are met
            boolean isFlat = lowDirectionalStrength || lowVolatility || tooManyDoji || tooManySpinningTop ||
                    veryLowDirectionalStrength || veryLowVolatility || tooManySmallCandles;

            if (isFlat) {
                // Build detailed reason message
                List<String> reasons = new ArrayList<>();
                if (lowDirectionalStrength) {
                    reasons.add(String.format("Low directional strength: %.2f < %.2f",
                            directionalStrength, requirements.getMinDirectionalStrength()));
                }
                if (lowVolatility) {
                    reasons.add(String.format("Low volatility: %.2f < %.2f",
                            volatilityScore, thresholds.getVolatilityScore().getLowThreshold()));
                }
                if (tooManyDoji) {
                    reasons.add(String.format("Too many doji: %d > %d",
                            candleAnalysis.getConsecutiveDoji(), requirements.getMaxConsecutiveDoji()));
                }
                if (tooManySpinningTop) {
                    reasons.add(String.format("Too many spinning tops: %d > %d",
                            candleAnalysis.getConsecutiveSpinningTop(), requirements.getMaxConsecutiveSpinningTop()));
                }
                if (veryLowDirectionalStrength) {
                    reasons.add(String.format("Very low directional strength: %.2f < %.2f",
                            directionalStrength, thresholds.getDirectionalStrength().getVeryLowThreshold()));
                }
                if (veryLowVolatility) {
                    reasons.add(String.format("Very low volatility: %.2f < %.2f",
                            volatilityScore, thresholds.getVolatilityScore().getVeryLowThreshold()));
                }
                if (tooManySmallCandles) {
                    reasons.add(String.format("Too many small candles: %d > %d",
                            candleAnalysis.getConsecutiveSmallCandles(), requirements.getMaxConsecutiveSmallCandles()));
                }

                String reasonMessage = String.join("; ", reasons);
                log.debug("🔍 FLAT MARKET DETECTED - Reasons: {}", reasonMessage);
            }

            return isFlat;

        } catch (Exception e) {
            log.error("Error determining flat market: {}", e.getMessage());
            return true; // Assume flat market on error
        }
    }

    private double calculateOverallScore(double directionalStrength, double volatilityScore, double candleSizeScore) {
        try {
            // Validate inputs to prevent NaN
            if (Double.isNaN(directionalStrength) || Double.isInfinite(directionalStrength)) {
                directionalStrength = 0.0;
            }
            if (Double.isNaN(volatilityScore) || Double.isInfinite(volatilityScore)) {
                volatilityScore = 0.0;
            }
            if (Double.isNaN(candleSizeScore) || Double.isInfinite(candleSizeScore)) {
                candleSizeScore = 0.0;
            }

            // Weight the components
            double directionalWeight = 0.4;
            double volatilityWeight = 0.3;
            double candleSizeWeight = 0.3;

            double overallScore = (directionalStrength * directionalWeight) +
                    (volatilityScore * volatilityWeight) +
                    (candleSizeScore * candleSizeWeight);

            // Validate final result
            if (Double.isNaN(overallScore) || Double.isInfinite(overallScore)) {
                overallScore = 0.0;
            }

            return Math.min(overallScore, 1.0);

        } catch (Exception e) {
            log.error("Error calculating overall score: {}", e.getMessage());
            return 0.0;
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

    private boolean determineSuitability(boolean isFlatMarket, double directionalStrength,
                                         CandleAnalysisResult candleAnalysis, FlattenedIndicators indicators) {
        try {
            // Get thresholds from configuration
            double minDirectionalStrength = configService.getFlatMarketFilteringConfig()
                    .getRequirements().getMinDirectionalStrength();
            double minVolumeMultiplier = configService.getFlatMarketFilteringConfig()
                    .getRequirements().getMinVolumeMultiplier();

            // Check if market conditions meet minimum requirements
            boolean directionalStrengthOk = directionalStrength >= minDirectionalStrength;
            boolean volumeOk = indicators.getVolume_surge_multiplier() != null &&
                    indicators.getVolume_surge_multiplier() >= minVolumeMultiplier;

            boolean isSuitable = !isFlatMarket && directionalStrengthOk && volumeOk;

            if (!isSuitable) {
                log.debug("Market condition unsuitable - Flat: {}, Directional: {}, Volume: {}",
                        isFlatMarket, directionalStrengthOk, volumeOk);
            }

            return isSuitable;

        } catch (Exception e) {
            log.error("Error determining suitability: {}", e.getMessage());
            return false;
        }
    }

    private String buildReasonMessage(boolean isFlatMarket, boolean isSuitableForTrading,
                                      CandleAnalysisResult candleAnalysis, double directionalStrength,
                                      double volatilityScore, double overallScore) {
        List<String> reasons = new ArrayList<>();

        if (isFlatMarket) {
            reasons.add("Flat market detected");
        }

        if (!isSuitableForTrading) {
            reasons.add("Market conditions unsuitable");
        }

        if (directionalStrength < 0.6) {
            reasons.add("Weak directional strength: " + String.format("%.2f", directionalStrength));
        }

        if (volatilityScore < 0.3) {
            reasons.add("Low volatility: " + String.format("%.2f", volatilityScore));
        }


        if (candleAnalysis.getConsecutiveDoji() > 2) {
            reasons.add("Too many consecutive doji: " + candleAnalysis.getConsecutiveDoji());
        }

        if (candleAnalysis.getConsecutiveSpinningTop() > 3) {
            reasons.add("Too many consecutive spinning tops: " + candleAnalysis.getConsecutiveSpinningTop());
        }

        if (reasons.isEmpty()) {
            return "Market conditions suitable for trading";
        }

        return String.join("; ", reasons);
    }

    /**
     * Perform comprehensive flat market check considering multiple factors
     */
    private boolean performComprehensiveFlatMarketCheck(Tick tick, FlattenedIndicators indicators, MarketConditionResult result) {
        try {
            // Get configuration
            FlatMarketFilteringConfig config = configService.getFlatMarketFilteringConfig();
            FlatMarketFilteringConfig.Thresholds thresholds = config.getThresholds();
            FlatMarketFilteringConfig.ComprehensiveChecks comprehensiveChecks = thresholds.getComprehensiveChecks();

            // Calculate additional metrics
            double emaAlignmentScore = calculateEmaAlignmentScore(indicators);
            double volumeConsistencyScore = calculateVolumeConsistencyScore(indicators);
            double priceActionScore = calculatePriceActionScore(indicators);

            // Combined score - all factors must be above threshold using configuration
            boolean emaAlignmentOk = emaAlignmentScore >= comprehensiveChecks.getEmaAlignmentScore();
            boolean volumeConsistencyOk = volumeConsistencyScore >= comprehensiveChecks.getVolumeConsistencyScore();
            boolean priceActionOk = priceActionScore >= comprehensiveChecks.getPriceActionScore();
            boolean overallScoreOk = result.getOverallScore() >= comprehensiveChecks.getOverallScore();

            boolean isComprehensiveSuitable = emaAlignmentOk && volumeConsistencyOk && priceActionOk && overallScoreOk;

            if (!isComprehensiveSuitable) {
                // Build detailed reason message
                List<String> failedChecks = new ArrayList<>();
                if (!emaAlignmentOk) {
                    failedChecks.add(String.format("EMA alignment: %.2f < %.2f",
                            emaAlignmentScore, comprehensiveChecks.getEmaAlignmentScore()));
                }
                if (!volumeConsistencyOk) {
                    failedChecks.add(String.format("Volume consistency: %.2f < %.2f",
                            volumeConsistencyScore, comprehensiveChecks.getVolumeConsistencyScore()));
                }
                if (!priceActionOk) {
                    failedChecks.add(String.format("Price action: %.2f < %.2f",
                            priceActionScore, comprehensiveChecks.getPriceActionScore()));
                }
                if (!overallScoreOk) {
                    failedChecks.add(String.format("Overall score: %.2f < %.2f",
                            result.getOverallScore(), comprehensiveChecks.getOverallScore()));
                }

                String reasonMessage = String.join("; ", failedChecks);
                log.debug("🔍 COMPREHENSIVE FLAT MARKET CHECK FAILED - Reasons: {}", reasonMessage);
            }

            return isComprehensiveSuitable;

        } catch (Exception e) {
            log.error("Error in comprehensive flat market check: {}", e.getMessage());
            return false; // Conservative approach
        }
    }

    /**
     * Calculate EMA alignment score
     */
    private double calculateEmaAlignmentScore(FlattenedIndicators indicators) {
        int bullishEmaSignals = 0;
        int totalEmaSignals = 0;

        if (Boolean.TRUE.equals(indicators.getEma5_5min_gt_ema34_5min())) bullishEmaSignals++;
        if (Boolean.TRUE.equals(indicators.getEma5_1min_gt_ema34_1min())) bullishEmaSignals++;
        if (Boolean.TRUE.equals(indicators.getEma5_15min_gt_ema34_15min())) bullishEmaSignals++;
        totalEmaSignals += 3;

        return totalEmaSignals > 0 ? (double) bullishEmaSignals / totalEmaSignals : 0.0;
    }

    /**
     * Calculate volume consistency score
     */
    private double calculateVolumeConsistencyScore(FlattenedIndicators indicators) {
        try {
            // Get configuration
            FlatMarketFilteringConfig config = configService.getFlatMarketFilteringConfig();
            FlatMarketFilteringConfig.VolumeConsistency volumeConsistency = config.getThresholds().getVolumeConsistency();

            int volumeSignals = 0;
            int totalVolumeChecks = 0;

            if (Boolean.TRUE.equals(indicators.getVolume_5min_surge())) volumeSignals++;
            if (Boolean.TRUE.equals(indicators.getVolume_1min_surge())) volumeSignals++;
            if (Boolean.TRUE.equals(indicators.getVolume_15min_surge())) volumeSignals++;
            totalVolumeChecks += 3;

            // Also check volume multiplier using configuration
            if (indicators.getVolume_surge_multiplier() != null &&
                    indicators.getVolume_surge_multiplier() >= volumeConsistency.getMinVolumeMultiplier()) {
                volumeSignals++;
            }
            totalVolumeChecks++;

            return totalVolumeChecks > 0 ? (double) volumeSignals / totalVolumeChecks : 0.0;

        } catch (Exception e) {
            log.error("Error calculating volume consistency score: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Calculate price action score
     */
    private double calculatePriceActionScore(FlattenedIndicators indicators) {
        int priceActionSignals = 0;
        int totalPriceActionChecks = 0;

        if (Boolean.TRUE.equals(indicators.getPrice_gt_vwap_5min())) priceActionSignals++;
        if (Boolean.TRUE.equals(indicators.getPrice_gt_vwap_1min())) priceActionSignals++;
        if (Boolean.TRUE.equals(indicators.getPrice_gt_vwap_15min())) priceActionSignals++;
        totalPriceActionChecks += 3;

        // Check for strong candlestick patterns
        if (Boolean.TRUE.equals(indicators.getLong_body_5min())) priceActionSignals++;
        if (Boolean.TRUE.equals(indicators.getLong_body_1min())) priceActionSignals++;
        if (Boolean.TRUE.equals(indicators.getBullish_engulfing_5min())) priceActionSignals++;
        if (Boolean.TRUE.equals(indicators.getBullish_engulfing_1min())) priceActionSignals++;
        totalPriceActionChecks += 4;

        return totalPriceActionChecks > 0 ? (double) priceActionSignals / totalPriceActionChecks : 0.0;
    }

    /**
     * Check entry filtering conditions: minimum candle height ≥ 4 and volume surge > 10x + good candle body ratio (>0.7)
     * This method reuses existing calculations from the service
     */
    public EntryFilteringResult checkEntryFilteringConditions(Tick tick, FlattenedIndicators indicators) {
        try {
            String instrumentToken = String.valueOf(tick.getInstrumentToken());

            // Reuse existing candle analysis
            CandleAnalysisResult candleAnalysis = analyzeCandleCharacteristics(tick, indicators);

            // Check minimum candle height ≥ 4
            boolean candleHeightOk = candleAnalysis.getCandleHeight() >= 4.0;

            // Check volume surge > 10x
            Double volumeMultiplier = indicators.getVolume_surge_multiplier();
            boolean volumeSurgeOk = volumeMultiplier != null && volumeMultiplier > 10.0;

            // Check candle body ratio > 0.7
            boolean bodyRatioOk = candleAnalysis.getBodyRatio() >= 0.60;

            // All conditions must be met
            boolean allConditionsMet = candleHeightOk && volumeSurgeOk && bodyRatioOk;

            // Build detailed reason message
            StringBuilder reason = new StringBuilder();
            if (allConditionsMet) {
                reason.append("All entry filtering conditions met");
            } else {
                List<String> failedConditions = new ArrayList<>();
                if (!candleHeightOk) {
                    failedConditions.add(String.format("Candle height %.2f < 4.0", candleAnalysis.getCandleHeight()));
                }
                if (!volumeSurgeOk) {
                    failedConditions.add(String.format("Volume surge %.2fx <= 10x",
                            volumeMultiplier != null ? volumeMultiplier : 0.0));
                }
                if (!bodyRatioOk) {
                    failedConditions.add(String.format("Body ratio %.2f < 0.60", candleAnalysis.getBodyRatio()));
                }
                reason.append("Entry filtering failed: ").append(String.join(", ", failedConditions));
            }

            return new EntryFilteringResult(allConditionsMet, candleAnalysis.getCandleHeight(),
                    volumeMultiplier, candleAnalysis.getBodyRatio(), reason.toString());

        } catch (Exception e) {
            log.error("Error checking entry filtering conditions for tick: {}", tick.getInstrumentToken(), e);
            return new EntryFilteringResult(false, 0.0, 0.0, 0.0,
                    "Error during filtering check: " + e.getMessage());
        }
    }

    public Map<String, Object> getStructuredMarketConditionDetails(Tick tick, FlattenedIndicators indicators) {
        try {
            MarketConditionResult result = analyzeMarketCondition(tick, indicators);

            FlatMarketFilteringConfig config = configService.getFlatMarketFilteringConfig();
            FlatMarketFilteringConfig.Requirements requirements = config.getRequirements();
            FlatMarketFilteringConfig.Thresholds thresholds = config.getThresholds();

            Map<String, Object> marketDetails = new HashMap<>();

            // Basic scores
            marketDetails.put("directionalStrength", Math.round(result.getDirectionalStrength() * 100.0) / 100.0);
            marketDetails.put("volatilityScore", Math.round(result.getVolatilityScore() * 100.0) / 100.0);
            marketDetails.put("overallScore", Math.round(result.getOverallScore() * 100.0) / 100.0);

            // Candle analysis details
            CandleAnalysisResult candleAnalysis = result.getCandleAnalysis();
            if (candleAnalysis != null) {
                marketDetails.put("candleHeight", Math.round(candleAnalysis.getCandleHeight() * 100.0) / 100.0);
                marketDetails.put("bodyRatio", Math.round(candleAnalysis.getBodyRatio() * 100.0) / 100.0);
                marketDetails.put("isLongBody", candleAnalysis.isLongBody());
                marketDetails.put("isDoji", candleAnalysis.isDoji());
                marketDetails.put("consecutiveDoji", candleAnalysis.getConsecutiveDoji());
                marketDetails.put("consecutiveSpinningTop", candleAnalysis.getConsecutiveSpinningTop());
                marketDetails.put("consecutiveSmallCandles", candleAnalysis.getConsecutiveSmallCandles());
            }

            // Volume surge multiplier
            if (indicators.getVolume_surge_multiplier() != null) {
                marketDetails.put("volumeSurgeMultiplier", Math.round(indicators.getVolume_surge_multiplier() * 100.0) / 100.0);
            }

            // Comprehensive scores
            double emaAlignmentScore = calculateEmaAlignmentScore(indicators);
            double volumeConsistencyScore = calculateVolumeConsistencyScore(indicators);
            double priceActionScore = calculatePriceActionScore(indicators);

            marketDetails.put("emaAlignmentScore", Math.round(emaAlignmentScore * 100.0) / 100.0);
            marketDetails.put("volumeConsistencyScore", Math.round(volumeConsistencyScore * 100.0) / 100.0);
            marketDetails.put("priceActionScore", Math.round(priceActionScore * 100.0) / 100.0);

            // If it's a flat market, add the reasons why
            if (result.isFlatMarket()) {
                List<String> flatMarketReasons = new ArrayList<>();
                flatMarketReasons.add("FLAT_MARKET:");

                // Check directional strength
                if (result.getDirectionalStrength() < requirements.getMinDirectionalStrength()) {
                    flatMarketReasons.add(String.format("Dir:%.2f<%.2f",
                            result.getDirectionalStrength(), requirements.getMinDirectionalStrength()));
                }

                // Check volatility
                if (result.getVolatilityScore() < thresholds.getVolatilityScore().getLowThreshold()) {
                    flatMarketReasons.add(String.format("Vol:%.2f<%.2f",
                            result.getVolatilityScore(), thresholds.getVolatilityScore().getLowThreshold()));
                }

                // Check candle analysis
                if (candleAnalysis != null) {
                    if (candleAnalysis.getConsecutiveDoji() > requirements.getMaxConsecutiveDoji()) {
                        flatMarketReasons.add(String.format("Doji:%d>%d",
                                candleAnalysis.getConsecutiveDoji(), requirements.getMaxConsecutiveDoji()));
                    }
                    if (candleAnalysis.getConsecutiveSpinningTop() > requirements.getMaxConsecutiveSpinningTop()) {
                        flatMarketReasons.add(String.format("Spin:%d>%d",
                                candleAnalysis.getConsecutiveSpinningTop(), requirements.getMaxConsecutiveSpinningTop()));
                    }
                    if (candleAnalysis.getConsecutiveSmallCandles() > requirements.getMaxConsecutiveSmallCandles()) {
                        flatMarketReasons.add(String.format("Small:%d>%d",
                                candleAnalysis.getConsecutiveSmallCandles(), requirements.getMaxConsecutiveSmallCandles()));
                    }
                }

                FlatMarketFilteringConfig.ComprehensiveChecks comprehensiveChecks = thresholds.getComprehensiveChecks();

                if (emaAlignmentScore < comprehensiveChecks.getEmaAlignmentScore()) {
                    flatMarketReasons.add(String.format("EMA:%.2f<%.2f",
                            emaAlignmentScore, comprehensiveChecks.getEmaAlignmentScore()));
                }
                if (volumeConsistencyScore < comprehensiveChecks.getVolumeConsistencyScore()) {
                    flatMarketReasons.add(String.format("VolCons:%.2f<%.2f",
                            volumeConsistencyScore, comprehensiveChecks.getVolumeConsistencyScore()));
                }
                if (priceActionScore < comprehensiveChecks.getPriceActionScore()) {
                    flatMarketReasons.add(String.format("Price:%.2f<%.2f",
                            priceActionScore, comprehensiveChecks.getPriceActionScore()));
                }
                if (result.getOverallScore() < comprehensiveChecks.getOverallScore()) {
                    flatMarketReasons.add(String.format("Overall:%.2f<%.2f",
                            result.getOverallScore(), comprehensiveChecks.getOverallScore()));
                }

                marketDetails.put("flatMarketReasons", flatMarketReasons);
            }

            return marketDetails;

        } catch (Exception e) {
            log.error("Error getting structured market condition details: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Result class for market condition analysis
     */
    @Setter
    @Getter
    public static class MarketConditionResult {
        // Getters and setters
        private boolean isFlatMarket;
        private boolean isSuitableForTrading;
        private double directionalStrength;
        private double volatilityScore;
        private double candleSizeScore;
        private double overallScore;
        private String reason;
        private CandleAnalysisResult candleAnalysis;

        // Constructors, getters, setters
        public MarketConditionResult() {
        }

        public MarketConditionResult(boolean isFlatMarket, boolean isSuitableForTrading,
                                     double directionalStrength, double volatilityScore,
                                     double candleSizeScore, double overallScore,
                                     String reason, CandleAnalysisResult candleAnalysis) {
            this.isFlatMarket = isFlatMarket;
            this.isSuitableForTrading = isSuitableForTrading;
            this.directionalStrength = directionalStrength;
            this.volatilityScore = volatilityScore;
            this.candleSizeScore = candleSizeScore;
            this.overallScore = overallScore;
            this.reason = reason;
            this.candleAnalysis = candleAnalysis;
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
        private String reason;

        public EntryFilteringResult() {
        }

        public EntryFilteringResult(boolean conditionsMet, double candleHeight,
                                    Double volumeMultiplier, double bodyRatio, String reason) {
            this.conditionsMet = conditionsMet;
            this.candleHeight = candleHeight;
            this.volumeMultiplier = volumeMultiplier;
            this.bodyRatio = bodyRatio;
            this.reason = reason;
        }
    }

}
