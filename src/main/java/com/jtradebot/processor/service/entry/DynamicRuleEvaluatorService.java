package com.jtradebot.processor.service.entry;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.config.ScoringConfigurationService;
import com.jtradebot.processor.config.TradingConfigurationService;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.strategy.ScalpingVolumeSurgeCallRule;
import com.jtradebot.processor.model.strategy.ScalpingVolumeSurgePutRule;
import com.jtradebot.processor.model.strategy.ScalpingEntryDecision;
import com.jtradebot.processor.indicator.VWAPIndicator;

import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.*;


@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicRuleEvaluatorService {
    
    private final TickDataManager tickDataManager;
    private final DynamicStrategyConfigService configService;
    private final KiteInstrumentHandler kiteInstrumentHandler;
    private final ScalpingEntryService scalpingEntryService;
    private final ScoringConfigurationService scoringConfigService;
    private final RuleHelper ruleHelper;
    private final TradingConfigurationService tradingConfigurationService;
    private final VWAPIndicator vwapIndicator;

    // Rules will be built dynamically from JSON configuration
    private ScalpingVolumeSurgeCallRule callRule;
    private ScalpingVolumeSurgePutRule putRule;
    
    // Cache for flattened indicators to prevent multiple calculations
    private final Map<String, FlattenedIndicators> indicatorsCache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION_MS = 1000; // 1 second cache

    
    // Initialize rules from configuration
    private void initializeRules() {
        callRule = ScalpingVolumeSurgeCallRule.builder()
                .ruleName(configService.getCallRuleName())
                .strategyType(configService.getCallStrategyType())
                .isActive(configService.isCallRuleActive())
                .requireAllTimeframesBullish(configService.isCallRequireAllTimeframesBullish())
                .requireVolumeSurge(configService.isCallRequireVolumeSurge())
                .requireEmaCrossover(configService.isCallRequireEmaCrossover())
                .requireRsiOverbought(configService.isCallRequireRsiOverbought())
                .requirePriceAboveVwap(configService.isCallRequirePriceAboveVwap())
                .requirePriceAboveResistance(configService.isCallRequirePriceAboveResistance())
                .minVolumeSurgeMultiplier(configService.getCallVolumeSurgeMultiplier())
                .minRsiThreshold(configService.getCallRsiThreshold())
                .minSignalStrength(configService.getCallSignalStrength())
                .check1Min(configService.isCallCheck1Min())
                .check5Min(configService.isCallCheck5Min())
                .check15Min(configService.isCallCheck15Min())
                .stopLossPercentage(configService.getCallStopLossPercentage())
                .targetPercentage(configService.getCallTargetPercentage())
                .build();

        putRule = ScalpingVolumeSurgePutRule.builder()
                .ruleName(configService.getPutRuleName())
                .strategyType(configService.getPutStrategyType())
                .isActive(configService.isPutRuleActive())
                .requireAllTimeframesBearish(configService.isPutRequireAllTimeframesBearish())
                .requireVolumeSurge(configService.isPutRequireVolumeSurge())
                .requireEmaCrossover(configService.isPutRequireEmaCrossover())
                .requireRsiOversold(configService.isPutRequireRsiOversold())
                .requirePriceBelowVwap(configService.isPutRequirePriceBelowVwap())
                .requirePriceBelowSupport(configService.isPutRequirePriceBelowSupport())
                .minVolumeSurgeMultiplier(configService.getPutVolumeSurgeMultiplier())
                .maxRsiThreshold(configService.getPutRsiThreshold())
                .minSignalStrength(configService.getPutSignalStrength())
                .check1Min(configService.isPutCheck1Min())
                .check5Min(configService.isPutCheck5Min())
                .check15Min(configService.isPutCheck15Min())
                .stopLossPercentage(configService.getPutStopLossPercentage())
                .targetPercentage(configService.getPutTargetPercentage())
                .build();

        log.info("Strategy rules initialized from JSON configuration");
        log.info("Call Rule - RSI Threshold: {}, Volume Multiplier: {}",
                callRule.getMinRsiThreshold(), callRule.getMinVolumeSurgeMultiplier());
        log.info("Put Rule - RSI Threshold: {}, Volume Multiplier: {}",
                putRule.getMaxRsiThreshold(), putRule.getMinVolumeSurgeMultiplier());
    }

    public boolean shouldMakePutExit(Tick tick){
        FlattenedIndicators indicators = getFlattenedIndicators(tick);
        
        // 🔥 CONFIGURABLE: Use exit signal configuration from JSON
        double putExitThreshold = tradingConfigurationService.getTradingConfig().getExitSignalConfiguration().getExitThresholds().getPutExitThreshold();
        boolean rsiDivergenceEnabled = tradingConfigurationService.getTradingConfig().getExitSignalConfiguration().getRsiDivergenceExit().isEnabled();
        boolean marketConditionEnabled = tradingConfigurationService.getTradingConfig().getExitSignalConfiguration().getMarketConditionExit().isEnabled();
        
        // Check for RSI bullish divergence as exit signal for PUT orders
        if (rsiDivergenceEnabled) {
            boolean rsiBullishDivergence = isRsiBullishDivergencePresent(indicators);
            if (rsiBullishDivergence) {
                log.info("🔄 PUT EXIT SIGNAL - RSI Bullish Divergence detected");
                return true;
            }
        }
        
        // Check market condition reversal
        if (marketConditionEnabled) {
            log.debug("🔍 PUT EXIT CHECK - Threshold: {}, Market Condition: {}", putExitThreshold, "Bullish");
            return ruleHelper.isMarketConditionBullish(indicators, putExitThreshold);
        }
        
        return false;
    }

    public boolean shouldMakeCallExit(Tick tick){
        FlattenedIndicators indicators = getFlattenedIndicators(tick);
        
        // 🔥 CONFIGURABLE: Use exit signal configuration from JSON
        double callExitThreshold = tradingConfigurationService.getTradingConfig().getExitSignalConfiguration().getExitThresholds().getCallExitThreshold();
        boolean rsiDivergenceEnabled = tradingConfigurationService.getTradingConfig().getExitSignalConfiguration().getRsiDivergenceExit().isEnabled();
        boolean marketConditionEnabled = tradingConfigurationService.getTradingConfig().getExitSignalConfiguration().getMarketConditionExit().isEnabled();
        
        // Check for RSI bearish divergence as exit signal for CALL orders
        if (rsiDivergenceEnabled) {
            boolean rsiBearishDivergence = isRsiBearishDivergencePresent(indicators);
            if (rsiBearishDivergence) {
                log.info("🔄 CALL EXIT SIGNAL - RSI Bearish Divergence detected");
                return true;
            }
        }
        
        // Check market condition reversal
        if (marketConditionEnabled) {
            log.debug("🔍 CALL EXIT CHECK - Threshold: {}, Market Condition: {}", callExitThreshold, "Bearish");
            return ruleHelper.isMarketConditionBearish(indicators, callExitThreshold);
        }
        
        return false;
    }
    
    /**
     * Check if RSI bullish divergence is present in any timeframe
     */
    private boolean isRsiBullishDivergencePresent(FlattenedIndicators indicators) {
        return Boolean.TRUE.equals(indicators.getRsi_bullish_divergence_1min()) ||
               Boolean.TRUE.equals(indicators.getRsi_bullish_divergence_5min()) ||
               Boolean.TRUE.equals(indicators.getRsi_bullish_divergence_15min());
    }
    
    /**
     * Check if RSI bearish divergence is present in any timeframe
     */
    private boolean isRsiBearishDivergencePresent(FlattenedIndicators indicators) {
        return Boolean.TRUE.equals(indicators.getRsi_bearish_divergence_1min()) ||
               Boolean.TRUE.equals(indicators.getRsi_bearish_divergence_5min()) ||
               Boolean.TRUE.equals(indicators.getRsi_bearish_divergence_15min());
    }


    public ScalpingEntryDecision getEntryDecision(Tick tick, FlattenedIndicators indicators, UnstableMarketConditionAnalysisService.FlexibleFilteringResult result,
                                                  double qualityScore, String dominantTrend, Map<String, Double> callScores, Map<String, Double> putScores) {
        try {
            ScalpingEntryDecision decision = scalpingEntryService.evaluateEntry(tick, callScores, putScores, qualityScore, result, dominantTrend);
            
            if (decision.isShouldEntry()) {
                log.debug("🎯 ENTRY DECISION - Instrument: {}, Price: {}, Scenario: {}, Confidence: {}/10, Time: {}", 
                    tick.getInstrumentToken(), tick.getLastTradedPrice(), decision.getScenarioName(), 
                    decision.getConfidence(), tick.getTickTimestamp());
            } else {
                log.debug("🔍 ENTRY BLOCKED - Instrument: {}, Reason: {}", tick.getInstrumentToken(), decision.getReason());
            }
            
            return decision;
            
        } catch (Exception e) {
            log.error("Error getting entry decision for tick: {}", tick.getInstrumentToken(), e);
            return ScalpingEntryDecision.builder()
                    .shouldEntry(false)
                    .reason("Error during evaluation: " + e.getMessage())
                    .build();
        }
    }
    

    public FlattenedIndicators getFlattenedIndicators(Tick indexTick) {
        try {
            String instrumentToken = String.valueOf(indexTick.getInstrumentToken());
            long currentTime = System.currentTimeMillis();
            
            // Check cache first
            FlattenedIndicators cachedIndicators = indicatorsCache.get(instrumentToken);
            Long cacheTimestamp = cacheTimestamps.get(instrumentToken);
            
            if (cachedIndicators != null && cacheTimestamp != null && 
                (currentTime - cacheTimestamp) < CACHE_DURATION_MS) {
                log.debug("📋 Using cached indicators for instrument: {}", instrumentToken);
                return cachedIndicators;
            }
            
            FlattenedIndicators indicators = new FlattenedIndicators();
            indicators.setInstrumentToken(instrumentToken);
            
            // Step 1: Calculate index-based indicators (EMA, RSI, MACD, candlestick patterns, price action)
            calculateIndexBasedIndicators(indicators, indexTick);
            
            // Step 2: Calculate future-based indicators (Volume, OI, price-volume surge)
            calculateFutureBasedIndicators(indicators, indexTick);
            
            // Step 3: Calculate futuresignals based on combined indicators
            indicators.setFuturesignals(ruleHelper.calculateFuturesignals(indicators));
            
            // Cache the result
            indicatorsCache.put(instrumentToken, indicators);
            cacheTimestamps.put(instrumentToken, currentTime);
            
            return indicators;
            
        } catch (Exception e) {
            log.error("Error getting flattened indicators for tick: {}", indexTick.getInstrumentToken(), e);
            return new FlattenedIndicators();
        }
    }

    /**
     * Calculate index-based indicators using index BarSeries data
     * Includes: EMA, RSI, MACD, candlestick patterns, price action, support/resistance
     */
    private void calculateIndexBasedIndicators(FlattenedIndicators indicators, Tick indexTick) {
        try {
            String instrumentToken = String.valueOf(indexTick.getInstrumentToken());
            
            // Check if we have sufficient BarSeries data for indicator calculations
            ensureSufficientBarSeriesData(instrumentToken);
            
            // Get BarSeries for different timeframes (INDEX DATA)
            BarSeries oneMinSeries = tickDataManager.getBarSeriesForTimeFrame(instrumentToken, ONE_MIN);
            BarSeries fiveMinSeries = tickDataManager.getBarSeriesForTimeFrame(instrumentToken, FIVE_MIN);
            BarSeries fifteenMinSeries = tickDataManager.getBarSeriesForTimeFrame(instrumentToken, FIFTEEN_MIN);
            
            // Initialize rules if not already done
            if (callRule == null || putRule == null) {
                initializeRules();
            }
            
            // Use RuleHelper to flatten indicators using INDEX data
            ruleHelper.flattenEmaIndicators(indicators, oneMinSeries, fiveMinSeries, fifteenMinSeries);
            ruleHelper.flattenRsiIndicators(indicators, oneMinSeries, fiveMinSeries, fifteenMinSeries, 
                callRule.getMinRsiThreshold(), putRule.getMaxRsiThreshold(),
                configService.getRsiMaPeriod(), configService.isEnableRsiMaComparison());
            ruleHelper.flattenPriceActionIndicators(indicators, oneMinSeries, fiveMinSeries, fifteenMinSeries, indexTick);
            ruleHelper.flattenCandlestickPatternIndicators(indicators, oneMinSeries, fiveMinSeries, fifteenMinSeries);
            
            log.debug("✅ Index-based indicators calculated successfully for instrument: {}", instrumentToken);
            
        } catch (Exception e) {
            log.error("Error calculating index-based indicators for instrument: {}", indexTick.getInstrumentToken(), e);
        }
    }

    /**
     * Calculate future-based indicators using future tick data
     * Includes: Volume surge, OI signals, price-volume directional indicators, VWAP
     */
    private void calculateFutureBasedIndicators(FlattenedIndicators indicators, Tick indexTick) {
        try {
            // Get future tick from map for volume and OI calculations
            String niftyFutureToken = kiteInstrumentHandler.getNifty50FutureToken().toString();
            Tick futureTick = tickDataManager.getLastTick(niftyFutureToken);
            
            if (futureTick != null) {
                // Enhance volume indicators with future data
                ruleHelper.enhanceVolumeIndicatorsWithFutureData(indicators, futureTick);
                
                // Enhance OI indicators with future data (real OI data)
                ruleHelper.enhanceOIIndicatorsWithFutureData(indicators, futureTick, tickDataManager);
                
                // Calculate VWAP indicators using future BarSeries data
                calculateVWAPIndicators(indicators, futureTick, niftyFutureToken);
                
                log.info("✅ Future-based indicators calculated successfully - Index Token: {}, Future Token: {}, Index Price: {}, Future Volume: {}, Future OI: {}",
                    indexTick.getInstrumentToken(), futureTick.getInstrumentToken(),
                    indexTick.getLastTradedPrice(), futureTick.getVolumeTradedToday(), futureTick.getOi());
            } else {
                log.warn("⚠️ No future tick available for volume and OI enhancement - Index Token: {}, Expected Future Token: {}", 
                    indexTick.getInstrumentToken(), niftyFutureToken);
                
                // Set default values for future-based indicators when data is not available
                setDefaultFutureBasedIndicators(indicators);
            }
            
        } catch (Exception e) {
            log.error("Error calculating future-based indicators for instrument: {}", indexTick.getInstrumentToken(), e);
            // Set default values on error
            setDefaultFutureBasedIndicators(indicators);
        }
    }

    /**
     * Calculate VWAP indicators using future BarSeries data
     * VWAP should be calculated using future data since that's where actual trading volume happens
     */
    private void calculateVWAPIndicators(FlattenedIndicators indicators, Tick futureTick, String futureToken) {
        try {
            // Get future BarSeries for different timeframes
            BarSeries oneMinSeries = tickDataManager.getBarSeriesForTimeFrame(futureToken, ONE_MIN);
            BarSeries fiveMinSeries = tickDataManager.getBarSeriesForTimeFrame(futureToken, FIVE_MIN);
            BarSeries fifteenMinSeries = tickDataManager.getBarSeriesForTimeFrame(futureToken, FIFTEEN_MIN);
            
            double currentPrice = futureTick.getLastTradedPrice();
            
            // Calculate VWAP for 5min timeframe
            if (fiveMinSeries != null && fiveMinSeries.getBarCount() >= 20) {
                Double vwap5min = vwapIndicator.calculateVWAP(fiveMinSeries);
                if (vwap5min != null) {
                    indicators.setPrice_gt_vwap_5min(currentPrice > vwap5min);
                    indicators.setPrice_lt_vwap_5min(currentPrice < vwap5min);
                }
            }

            // Calculate VWAP for 1min timeframe
            if (oneMinSeries != null && oneMinSeries.getBarCount() >= 20) {
                Double vwap1min = vwapIndicator.calculateVWAP(oneMinSeries);
                if (vwap1min != null) {
                    indicators.setPrice_gt_vwap_1min(currentPrice > vwap1min);
                    indicators.setPrice_lt_vwap_1min(currentPrice < vwap1min);
                }
            }

            // Calculate VWAP for 15min timeframe
            if (fifteenMinSeries != null && fifteenMinSeries.getBarCount() >= 20) {
                Double vwap15min = vwapIndicator.calculateVWAP(fifteenMinSeries);
                if (vwap15min != null) {
                    indicators.setPrice_gt_vwap_15min(currentPrice > vwap15min);
                    indicators.setPrice_lt_vwap_15min(currentPrice < vwap15min);
                }
            }
            
            log.debug("✅ VWAP indicators calculated using future data - Future Token: {}, Current Price: {}", 
                futureToken, currentPrice);
                
        } catch (Exception e) {
            log.error("Error calculating VWAP indicators using future data", e);
            // Set default VWAP values on error
            setDefaultVWAPIndicators(indicators);
        }
    }

    /**
     * Set default VWAP indicators on error
     */
    private void setDefaultVWAPIndicators(FlattenedIndicators indicators) {
        try {
            indicators.setPrice_gt_vwap_1min(false);
            indicators.setPrice_lt_vwap_1min(false);
            indicators.setPrice_gt_vwap_5min(false);
            indicators.setPrice_lt_vwap_5min(false);
            indicators.setPrice_gt_vwap_15min(false);
            indicators.setPrice_lt_vwap_15min(false);
            log.debug("✅ Default VWAP indicators set on error");
        } catch (Exception e) {
            log.error("Error setting default VWAP indicators", e);
        }
    }

    /**
     * Set default values for future-based indicators when future data is not available
     */
    private void setDefaultFutureBasedIndicators(FlattenedIndicators indicators) {
        try {
            // Set volume indicators to false (no surge)
            indicators.setVolume_1min_surge(false);
            indicators.setVolume_5min_surge(false);
            indicators.setVolume_15min_surge(false);
            indicators.setVolume_surge_multiplier(1.0);
            
            // Set price-volume indicators to false
            indicators.setPrice_volume_bullish_surge_1min(false);
            indicators.setPrice_volume_bullish_surge_5min(false);
            indicators.setPrice_volume_bullish_surge_15min(false);
            indicators.setPrice_volume_bearish_surge_1min(false);
            indicators.setPrice_volume_bearish_surge_5min(false);
            indicators.setPrice_volume_bearish_surge_15min(false);
            
            // Set OI indicators to false
            indicators.setOi_bullish_signal_1min(false);
            indicators.setOi_bullish_signal_5min(false);
            indicators.setOi_bullish_signal_15min(false);
            indicators.setOi_bearish_signal_1min(false);
            indicators.setOi_bearish_signal_5min(false);
            indicators.setOi_bearish_signal_15min(false);
            
            // Set VWAP indicators to false
            indicators.setPrice_gt_vwap_1min(false);
            indicators.setPrice_lt_vwap_1min(false);
            indicators.setPrice_gt_vwap_5min(false);
            indicators.setPrice_lt_vwap_5min(false);
            indicators.setPrice_gt_vwap_15min(false);
            indicators.setPrice_lt_vwap_15min(false);
            
            log.debug("✅ Default values set for future-based indicators");
            
        } catch (Exception e) {
            log.error("Error setting default future-based indicators", e);
        }
    }


    /**
     * Pre-populate BarSeries with historical data to ensure sufficient data for indicator calculations
     * This is crucial for backtesting to have enough historical data for RSI (14+ bars) and Volume (20+ bars)
     */
    private void ensureSufficientBarSeriesData(String instrumentToken) {
        try {
            // Check if we have sufficient data for all timeframes
            BarSeries oneMinSeries = tickDataManager.getBarSeriesForTimeFrame(instrumentToken, ONE_MIN);
            BarSeries fiveMinSeries = tickDataManager.getBarSeriesForTimeFrame(instrumentToken, FIVE_MIN);
            BarSeries fifteenMinSeries = tickDataManager.getBarSeriesForTimeFrame(instrumentToken, FIFTEEN_MIN);
            
            boolean needsHistoricalData = false;
            
            // Check if we have enough data for RSI calculations (14+ bars)
            if (oneMinSeries == null || oneMinSeries.getBarCount() < scoringConfigService.getMinDataBars()) {
                log.debug("Insufficient 1min data: {} bars (need {}+)", oneMinSeries != null ? oneMinSeries.getBarCount() : 0, scoringConfigService.getMinDataBars());
                needsHistoricalData = true;
            }
            
            if (fiveMinSeries == null || fiveMinSeries.getBarCount() < scoringConfigService.getMinDataBars()) {
                log.debug("Insufficient 5min data: {} bars (need {}+)", fiveMinSeries != null ? fiveMinSeries.getBarCount() : 0, scoringConfigService.getMinDataBars());
                needsHistoricalData = true;
            }
            
            if (fifteenMinSeries == null || fifteenMinSeries.getBarCount() < scoringConfigService.getMinDataBars()) {
                log.debug("Insufficient 15min data: {} bars (need {}+)", fifteenMinSeries != null ? fifteenMinSeries.getBarCount() : 0, scoringConfigService.getMinDataBars());
                needsHistoricalData = true;
            }
            
            if (needsHistoricalData) {
                log.info("Insufficient BarSeries data detected. This is normal during backtesting startup.");
                log.info("Indicators will start working once sufficient historical data is accumulated.");
                log.info("Expected: RSI after 14+ bars, Volume surge after 20+ bars per timeframe.");
            } else {
                log.debug("Sufficient BarSeries data available for indicator calculations");
            }
            
        } catch (Exception e) {
            log.error("Error checking BarSeries data sufficiency for instrument: {}", instrumentToken, e);
        }
    }

}
