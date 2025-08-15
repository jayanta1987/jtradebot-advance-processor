package com.jtradebot.processor.service.impl;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.indicator.MultiEmaIndicator;
import com.jtradebot.processor.indicator.PriceVolumeSurgeIndicator;
import com.jtradebot.processor.indicator.RsiIndicator;
import com.jtradebot.processor.indicator.SupportResistanceIndicator;
import com.jtradebot.processor.indicator.VWAPIndicator;
import com.jtradebot.processor.candleStick.CandlestickPattern;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.model.EmaIndicatorInfo;
import com.jtradebot.processor.model.EntryQuality;
import com.jtradebot.processor.model.Resistance;
import com.jtradebot.processor.model.Support;
import java.util.Set;
import com.jtradebot.processor.model.FlattenedIndicators;
import com.jtradebot.processor.model.FuturesignalData;
import com.jtradebot.processor.model.ScalpingEntryLogic;
import com.jtradebot.processor.model.ScalpingVolumeSurgeCallRule;
import com.jtradebot.processor.model.ScalpingVolumeSurgePutRule;
import com.jtradebot.processor.model.StrategyScore;
import com.jtradebot.processor.service.ScalpingEntryService;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.jtradebot.processor.service.ScalpingVolumeSurgeService;
import com.jtradebot.processor.service.ProfitableTradeFilterService;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Bar;

import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScalpingVolumeSurgeServiceImpl implements ScalpingVolumeSurgeService {
    
    private final TickDataManager tickDataManager;
    private final DynamicStrategyConfigService configService;
    private final MultiEmaIndicator multiEmaIndicator;
    private final PriceVolumeSurgeIndicator priceVolumeSurgeIndicator;
    private final VWAPIndicator vwapIndicator;
    private final SupportResistanceIndicator supportResistanceIndicator;
    private final KiteInstrumentHandler kiteInstrumentHandler;
    private final ScalpingEntryService scalpingEntryService;
    private final ProfitableTradeFilterService profitableTradeFilterService;
    
    // Rules will be built dynamically from JSON configuration
    private ScalpingVolumeSurgeCallRule callRule;
    private ScalpingVolumeSurgePutRule putRule;
    
    // 🔥 NEW: Volume surge cooldown mechanism to prevent persistent signals
    private final Map<String, Long> lastVolumeSurgeTime = new ConcurrentHashMap<>();
    private final Map<String, Double> lastVolumeSurgeMultiplier = new ConcurrentHashMap<>();
    private final Map<String, Boolean> volumeSurgeCooldown = new ConcurrentHashMap<>();
    
    // 🔥 NEW: Natural momentum tracking (no forced cooldowns)
    private final Map<String, Double> lastPriceLevel = new ConcurrentHashMap<>();
    private final Map<String, Long> lastPriceCheckTime = new ConcurrentHashMap<>();
    private final Map<String, Double> priceMomentum = new ConcurrentHashMap<>();
    
    // 🔥 NEW: Entry signal cooldown to prevent multiple entries in short time
    private final Map<String, Long> lastEntrySignalTime = new ConcurrentHashMap<>();
    private static final long ENTRY_COOLDOWN_MS = 30000; // 30 seconds between entry signals
    
    // 🔥 NEW: Volume surge quality tracking (natural approach)
    private final Map<String, Integer> consecutiveVolumeSurges = new ConcurrentHashMap<>();
    private final Map<String, Double> averageVolumeMultiplier = new ConcurrentHashMap<>();
    
    // 🔥 NEW: Signal quality tracking (natural approach)
    private final Map<String, Integer> successfulSignals = new ConcurrentHashMap<>();
    private final Map<String, Integer> failedSignals = new ConcurrentHashMap<>();
    private final Map<String, Double> signalSuccessRate = new ConcurrentHashMap<>();
    

    
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
                .maxHoldingTimeMinutes(configService.getCallMaxHoldingTimeMinutes())
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
                .maxHoldingTimeMinutes(configService.getPutMaxHoldingTimeMinutes())
                .build();
        
        log.info("Strategy rules initialized from JSON configuration");
        log.info("Call Rule - RSI Threshold: {}, Volume Multiplier: {}", 
                callRule.getMinRsiThreshold(), callRule.getMinVolumeSurgeMultiplier());
        log.info("Put Rule - RSI Threshold: {}, Volume Multiplier: {}", 
                putRule.getMaxRsiThreshold(), putRule.getMinVolumeSurgeMultiplier());
    }
    
    @Override
    public boolean shouldMakeCallEntry(Tick tick) {
        try {
            // Initialize rules if not already done
            if (callRule == null) {
                initializeRules();
            }
            
            String instrumentToken = String.valueOf(tick.getInstrumentToken());
            
            // 🔥 NATURAL APPROACH: Intelligent signal validation without forced cooldowns
            FlattenedIndicators indicators = getFlattenedIndicators(tick);
            
            // Enhanced CALL entry logic with NATURAL momentum and futuresignal requirements
            EntryQuality entryQuality = evaluateCallEntryQuality(indicators, tick);
            
            // NATURAL ENTRY VALIDATION for scalping - require strong momentum and futuresignals
            boolean hasStrongMomentum = validateStrongMomentum(indicators);
            boolean hasStrongFuturesignals = validateStrongFuturesignals(indicators);
            boolean hasStrongVolumeSurge = validateStrongVolumeSurge(indicators);
            
            // Check if entry quality meets minimum threshold AND has strong momentum/futuresignals
            boolean shouldEntry = entryQuality.getQualityScore() >= callRule.getMinSignalStrength() &&
                                hasStrongMomentum && hasStrongFuturesignals && hasStrongVolumeSurge;
            
            // Apply profitable trade filter if enabled
            boolean originalFilterPassed = shouldEntry;
            String filterRejectionReason = null;
            
            if (shouldEntry && profitableTradeFilterService.isFilterEnabled()) {
                var filterResult = profitableTradeFilterService.evaluateCallEntry(indicators, entryQuality, tick);
                shouldEntry = filterResult.getIsProfitableEntry();
                filterRejectionReason = filterResult.getRejectionReason();
            }
            
            // Combined logging for entry decision
            if (entryQuality.getQualityScore() >= callRule.getMinSignalStrength() && !shouldEntry) {
                if (originalFilterPassed && filterRejectionReason != null) {
                    log.info("🔍 CALL ENTRY BLOCKED - Quality: {}/10 | Profitable Filter: ✗ | Reason: {}", 
                        entryQuality.getQualityScore(), filterRejectionReason);
                } else {
                    log.info("🔍 CALL ENTRY BLOCKED - Quality: {}/10 | Original Filter: ✗ | Momentum: {}, Futuresignals: {}, VolumeSurge: {}", 
                        entryQuality.getQualityScore(), hasStrongMomentum, hasStrongFuturesignals, hasStrongVolumeSurge);
                }
            }
            
            // 🔥 NATURAL: Update signal tracking for learning (no forced cooldowns)
            if (shouldEntry) {
                updateSignalTrackingNatural(instrumentToken, true); // Track successful signal
                log.debug("🚀 NATURAL CALL ENTRY SIGNAL - Instrument: {}, Price: {}, Quality: {}/10, Time: {}", 
                    tick.getInstrumentToken(), tick.getLastTradedPrice(), entryQuality.getQualityScore(), tick.getTickTimestamp());
            }
            
            return shouldEntry;
            
        } catch (Exception e) {
            log.error("Error evaluating CALL entry for tick: {}", tick.getInstrumentToken(), e);
            return false;
        }
    }
    
    @Override
    public boolean shouldMakePutEntry(Tick tick) {
        try {
            // Initialize rules if not already done
            if (putRule == null) {
                initializeRules();
            }
            
            String instrumentToken = String.valueOf(tick.getInstrumentToken());
            
            // 🔥 NATURAL APPROACH: Intelligent signal validation without forced cooldowns
            FlattenedIndicators indicators = getFlattenedIndicators(tick);
            
            // Enhanced PUT entry logic with NATURAL momentum and futuresignal requirements
            EntryQuality entryQuality = evaluatePutEntryQuality(indicators, tick);
            
            // NATURAL ENTRY VALIDATION for scalping - require strong momentum and futuresignals
            boolean hasStrongMomentum = validateStrongMomentumForPut(indicators);
            boolean hasStrongFuturesignals = validateStrongFuturesignalsForPut(indicators);
            boolean hasStrongVolumeSurge = validateStrongVolumeSurgeForPut(indicators);
            
            // Check if entry quality meets minimum threshold AND has strong momentum/futuresignals
            boolean shouldEntry = entryQuality.getQualityScore() >= putRule.getMinSignalStrength() &&
                                hasStrongMomentum && hasStrongFuturesignals && hasStrongVolumeSurge;
            
            // Apply profitable trade filter if enabled
            boolean originalFilterPassed = shouldEntry;
            String filterRejectionReason = null;
            
            if (shouldEntry && profitableTradeFilterService.isFilterEnabled()) {
                var filterResult = profitableTradeFilterService.evaluatePutEntry(indicators, entryQuality, tick);
                shouldEntry = filterResult.getIsProfitableEntry();
                filterRejectionReason = filterResult.getRejectionReason();
            }
            
            // Combined logging for entry decision
            if (entryQuality.getQualityScore() >= putRule.getMinSignalStrength() && !shouldEntry) {
                if (originalFilterPassed && filterRejectionReason != null) {
                    log.info("🔍 PUT ENTRY BLOCKED - Quality: {}/10 | Profitable Filter: ✗ | Reason: {}", 
                        entryQuality.getQualityScore(), filterRejectionReason);
                } else {
                    log.info("🔍 PUT ENTRY BLOCKED - Quality: {}/10 | Original Filter: ✗ | Momentum: {}, Futuresignals: {}, VolumeSurge: {}", 
                        entryQuality.getQualityScore(), hasStrongMomentum, hasStrongFuturesignals, hasStrongVolumeSurge);
                }
            }
            
            // 🔥 NATURAL: Update signal tracking for learning (no forced cooldowns)
            if (shouldEntry) {
                updateSignalTrackingNatural(instrumentToken, true); // Track successful signal
                log.debug("📉 NATURAL PUT ENTRY SIGNAL - Instrument: {}, Price: {}, Quality: {}/10, Time: {}", 
                    tick.getInstrumentToken(), tick.getLastTradedPrice(), entryQuality.getQualityScore(), tick.getTickTimestamp());
            }
            
            return shouldEntry;
            
        } catch (Exception e) {
            log.error("Error evaluating PUT entry for tick: {}", tick.getInstrumentToken(), e);
            return false;
        }
    }
    
    @Override
    public FlattenedIndicators getFlattenedIndicators(Tick indexTick) {
        try {
            FlattenedIndicators indicators = new FlattenedIndicators();
            indicators.setInstrumentToken(String.valueOf(indexTick.getInstrumentToken()));
            
            // Check if we have sufficient BarSeries data for indicator calculations
            ensureSufficientBarSeriesData(String.valueOf(indexTick.getInstrumentToken()));
            
            // Get BarSeries for different timeframes
            BarSeries oneMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(indexTick.getInstrumentToken()), ONE_MIN);
            BarSeries fiveMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(indexTick.getInstrumentToken()), FIVE_MIN);
            BarSeries fifteenMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(indexTick.getInstrumentToken()), FIFTEEN_MIN);
            
            // Flatten EMA indicators
            flattenEmaIndicators(indicators, oneMinSeries, fiveMinSeries, fifteenMinSeries);
            
            // Flatten RSI indicators
            flattenRsiIndicators(indicators, oneMinSeries, fiveMinSeries, fifteenMinSeries);
            
            // Flatten volume indicators using index tick (will be enhanced with future data)
            flattenVolumeIndicators(indicators, oneMinSeries, fiveMinSeries, fifteenMinSeries, indexTick);
            
            // Flatten price action indicators
            flattenPriceActionIndicators(indicators, oneMinSeries, fiveMinSeries, fifteenMinSeries, indexTick);
            
            // Flatten candlestick pattern indicators
            flattenCandlestickPatternIndicators(indicators, oneMinSeries, fiveMinSeries, fifteenMinSeries);
            
            // Calculate futuresignals
            indicators.setFuturesignals(calculateFuturesignals(indicators));
            
            // Get future tick from map for volume calculations and enhance volume indicators
            String niftyFutureToken = kiteInstrumentHandler.getNifty50FutureToken().toString();
            Tick futureTick = tickDataManager.getLastTick(niftyFutureToken);
            
            if (futureTick != null) {
                // Enhance volume indicators with future data
                enhanceVolumeIndicatorsWithFutureData(indicators, futureTick);
                log.debug("Enhanced indicators with future data - Index Token: {}, Future Token: {}, Index Price: {}, Future Volume: {}",
                    indexTick.getInstrumentToken(), futureTick.getInstrumentToken(),
                    indexTick.getLastTradedPrice(), futureTick.getVolumeTradedToday());
            } else {
                log.debug("No future tick available for volume enhancement");
            }
            
            return indicators;
            
        } catch (Exception e) {
            log.error("Error getting flattened indicators for tick: {}", indexTick.getInstrumentToken(), e);
            return new FlattenedIndicators();
        }
    }
    
    @Override
    public String getRecommendedStrategy(Tick tick) {
        if (shouldMakeCallEntry(tick)) {
            return "CALL";
        } else if (shouldMakePutEntry(tick)) {
            return "PUT";
        } else {
            return "NO_TRADE";
        }
    }
    
    @Override
    public Double getStrategyConfidence(Tick tick) {
        try {
            FlattenedIndicators indicators = getFlattenedIndicators(tick);
            double confidence = 0.0;
            
            // Calculate confidence based on how many conditions are met
            if (indicators.getEma9_5min_gt_ema21_5min()) confidence += 0.2;
            if (indicators.getRsi_5min_gt_70()) confidence += 0.2;
            if (indicators.getVolume_5min_surge()) confidence += 0.2;
            if (indicators.getPrice_gt_vwap_5min()) confidence += 0.2;
            if (indicators.getPrice_above_resistance()) confidence += 0.2;
            
            return Math.min(confidence, 1.0);
            
        } catch (Exception e) {
            log.error("Error calculating strategy confidence for tick: {}", tick.getInstrumentToken(), e);
            return 0.0;
        }
    }
    
    @Override
    public StrategyScore calculateStrategyScore(Tick tick) {
        try {
            // Initialize rules if not already done
            if (callRule == null || putRule == null) {
                initializeRules();
            }
            
            FlattenedIndicators indicators = getFlattenedIndicators(tick);
            
            // Calculate individual component scores
            double emaScore = calculateEmaScore(indicators);
            double rsiScore = calculateRsiScore(indicators);
            double volumeScore = calculateVolumeScore(indicators);
            double priceActionScore = calculatePriceActionScore(indicators);
            double futuresignalScore = calculateFuturesignalScore(indicators);
            
            // Calculate momentum and trend strength score
            double momentumScore = calculateMomentumScore(indicators);
            
            // Calculate weighted total score with enhanced components
            double totalScore = (emaScore * configService.getEmaCrossoverWeight()) +
                               (rsiScore * configService.getRsiConditionWeight()) +
                               (volumeScore * configService.getVolumeSurgeWeight()) +
                               (priceActionScore * configService.getPriceActionWeight()) +
                               (futuresignalScore * configService.getFuturesignalsWeight()) +
                               (momentumScore * 0.15); // Additional momentum weight
            
            // Check entry conditions
            boolean shouldMakeCallEntry = shouldMakeCallEntry(tick);
            boolean shouldMakePutEntry = shouldMakePutEntry(tick);
            
            // Calculate confidence
            double confidence = getStrategyConfidence(tick);
            
            // Build strategy score
            StrategyScore strategyScore = StrategyScore.builder()
                    .instrumentToken(String.valueOf(tick.getInstrumentToken()))
                    .timestamp(tick.getTickTimestamp().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime())
                    .lastTradedPrice(tick.getLastTradedPrice())
                    .score(totalScore)
                    .emaScore(emaScore)
                    .rsiScore(rsiScore)
                    .volumeScore(volumeScore)
                    .priceActionScore(priceActionScore)
                    .futuresignalScore(futuresignalScore)
                    .shouldMakeCallEntry(shouldMakeCallEntry)
                    .shouldMakePutEntry(shouldMakePutEntry)
                    .confidence(confidence)
                    .build();
            
            // Interpret the score
            strategyScore.interpretScore();
            
            return strategyScore;
            
        } catch (Exception e) {
            log.error("Error calculating strategy score for tick: {}", tick.getInstrumentToken(), e);
            return StrategyScore.builder()
                    .instrumentToken(String.valueOf(tick.getInstrumentToken()))
                    .score(0.0)
                    .sentiment("NEUTRAL")
                    .strength("VERY_WEAK")
                    .recommendation("NO_TRADE")
                    .confidence(0.0)
                    .build();
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
            if (oneMinSeries == null || oneMinSeries.getBarCount() < 20) {
                log.debug("Insufficient 1min data: {} bars (need 20+)", oneMinSeries != null ? oneMinSeries.getBarCount() : 0);
                needsHistoricalData = true;
            }
            
            if (fiveMinSeries == null || fiveMinSeries.getBarCount() < 20) {
                log.debug("Insufficient 5min data: {} bars (need 20+)", fiveMinSeries != null ? fiveMinSeries.getBarCount() : 0);
                needsHistoricalData = true;
            }
            
            if (fifteenMinSeries == null || fifteenMinSeries.getBarCount() < 20) {
                log.debug("Insufficient 15min data: {} bars (need 20+)", fifteenMinSeries != null ? fifteenMinSeries.getBarCount() : 0);
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
    
    /**
     * Calculate EMA crossover score (-3 to +3)
     */
    private double calculateEmaScore(FlattenedIndicators indicators) {
        double score = 0.0;
        
        // Positive points for bullish EMA crossovers
        if (indicators.getEma9_5min_gt_ema21_5min()) score += 1.5;
        if (indicators.getEma9_1min_gt_ema21_1min()) score += 1.5;
        
        // Negative points for bearish EMA crossovers (only if we have data)
        if (indicators.getEma9_5min_gt_ema21_5min() != null && !indicators.getEma9_5min_gt_ema21_5min()) score -= 1.5;
        if (indicators.getEma9_1min_gt_ema21_1min() != null && !indicators.getEma9_1min_gt_ema21_1min()) score -= 1.5;
        
        return Math.max(-3.0, Math.min(3.0, score));
    }
    
    /**
     * Calculate RSI score (-3 to +3)
     */
    private double calculateRsiScore(FlattenedIndicators indicators) {
        double score = 0.0;
        
        // Positive points for bullish RSI conditions (RSI > 56)
        if (indicators.getRsi_5min_gt_56()) score += 1.5;
        if (indicators.getRsi_1min_gt_56()) score += 1.5;
        
        // Negative points for bearish RSI conditions (RSI < 44)
        if (indicators.getRsi_5min_lt_44()) score -= 1.5;
        if (indicators.getRsi_1min_lt_44()) score -= 1.5;
        
        return Math.max(-3.0, Math.min(3.0, score));
    }
    
    /**
     * Calculate volume surge score (-5 to +5) - Enhanced with volume multiplier strength
     */
    private double calculateVolumeScore(FlattenedIndicators indicators) {
        double score = 0.0;
        
        // Check if we have volume surge data
        boolean hasVolumeSurge = (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) ||
                                (indicators.getVolume_1min_surge() != null && indicators.getVolume_1min_surge());
        
        if (hasVolumeSurge) {
            // Determine if this is a bullish or bearish volume surge based on other indicators
            boolean isBullishVolume = false;
            boolean isBearishVolume = false;
            
            // Check EMA direction for volume surge context
            if (indicators.getEma9_5min_gt_ema21_5min() != null && indicators.getEma9_1min_gt_ema21_1min() != null) {
                isBullishVolume = indicators.getEma9_5min_gt_ema21_5min() && indicators.getEma9_1min_gt_ema21_1min();
                isBearishVolume = !indicators.getEma9_5min_gt_ema21_5min() && !indicators.getEma9_1min_gt_ema21_1min();
            }
            
            // Check RSI direction for volume surge context
            if (indicators.getRsi_5min_gt_56() != null && indicators.getRsi_1min_gt_56() != null) {
                if (indicators.getRsi_5min_gt_56() && indicators.getRsi_1min_gt_56()) {
                    isBullishVolume = true;
                } else if (indicators.getRsi_5min_lt_44() != null && indicators.getRsi_1min_lt_44() != null &&
                          indicators.getRsi_5min_lt_44() && indicators.getRsi_1min_lt_44()) {
                    isBearishVolume = true;
                }
            }
            
            // Calculate volume points based on direction
            double volumePoints = 0.0;
            if (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) volumePoints += 2.0;
            if (indicators.getVolume_1min_surge() != null && indicators.getVolume_1min_surge()) volumePoints += 1.0;
            
            // Enhanced volume multiplier bonus (relaxed thresholds)
            if (indicators.getVolume_surge_multiplier() != null) {
                if (indicators.getVolume_surge_multiplier() >= 3.0) {
                    volumePoints += 2.0; // Very high volume (relaxed from 5.0)
                } else if (indicators.getVolume_surge_multiplier() >= 2.0) {
                    volumePoints += 1.0; // High volume (relaxed from 3.0)
                } else if (indicators.getVolume_surge_multiplier() >= 1.5) {
                    volumePoints += 0.5; // Standard volume surge (relaxed from 2.0)
                }
            }
            
            // Apply direction
            if (isBullishVolume) {
                score += volumePoints; // Positive for bullish
            } else if (isBearishVolume) {
                score -= volumePoints; // Negative for bearish
            } else {
                // If direction is unclear, make it neutral (0)
                score += 0.0;
            }
        }
        
        return Math.max(-5.0, Math.min(5.0, score));
    }
    
    /**
     * Calculate price action score (-5 to +5) - Enhanced with breakout strength
     */
    private double calculatePriceActionScore(FlattenedIndicators indicators) {
        double score = 0.0;
        
        // Base VWAP points
        if (indicators.getPrice_gt_vwap_5min()) score += 1.5;
        if (indicators.getPrice_lt_vwap_5min()) score -= 1.5;
        
        // Support/Resistance points
        if (indicators.getPrice_above_resistance()) score += 1.5;
        if (indicators.getPrice_below_support()) score -= 1.5;
        
        // Enhanced breakout strength points
        if (indicators.getBreakoutStrength() != null && indicators.getBreakoutStrength() > 0) {
            // Strong breakout: >2% above resistance
            if (indicators.getBreakoutStrength() > 2.0) {
                score += 2.0; // Additional points for strong breakout
            } else {
                score += 1.0; // Standard breakout points
            }
        }
        
        if (indicators.getBreakdownStrength() != null && indicators.getBreakdownStrength() > 0) {
            // Strong breakdown: >2% below support
            if (indicators.getBreakdownStrength() > 2.0) {
                score -= 2.0; // Additional points for strong breakdown
            } else {
                score -= 1.0; // Standard breakdown points
            }
        }
        
        return Math.max(-5.0, Math.min(5.0, score));
    }
    
    /**
     * Calculate futuresignal score (-2 to +2)
     */
    private double calculateFuturesignalScore(FlattenedIndicators indicators) {
        double score = 0.0;
        
        if (indicators.getFuturesignals() != null) {
            // Positive points for bullish futuresignals
            if (indicators.getFuturesignals().getAllTimeframesBullish()) {
                score += 2.0;
            }
            // Negative points for bearish futuresignals
            else if (indicators.getFuturesignals().getAllTimeframesBearish()) {
                score -= 2.0;
            }
        }
        
        return Math.max(-2.0, Math.min(2.0, score));
    }
    
    private void flattenEmaIndicators(FlattenedIndicators indicators, BarSeries oneMinSeries, BarSeries fiveMinSeries, BarSeries fifteenMinSeries) {
        try {
            // Calculate actual EMA values using MultiEmaIndicator
            MultiEmaIndicator emaIndicator = new MultiEmaIndicator();
            
            // 1-minute EMA calculation
            if (oneMinSeries != null && oneMinSeries.getBarCount() >= 21) {
                try {
                    double ema9_1min = emaIndicator.getLastEmaValue(oneMinSeries, emaIndicator.createEmaInfoForTimeframes(oneMinSeries, ONE_MIN).getEma9());
                    double ema21_1min = emaIndicator.getLastEmaValue(oneMinSeries, emaIndicator.createEmaInfoForTimeframes(oneMinSeries, ONE_MIN).getEma20());
                    
                    indicators.setEma9_1min_gt_ema21_1min(ema9_1min > ema21_1min);
                } catch (Exception e) {
                    indicators.setEma9_1min_gt_ema21_1min(null);
                }
            } else {
                indicators.setEma9_1min_gt_ema21_1min(null);
            }
            
            // 5-minute EMA calculation
            if (fiveMinSeries != null && fiveMinSeries.getBarCount() >= 21) {
                try {
                    double ema9_5min = emaIndicator.getLastEmaValue(fiveMinSeries, emaIndicator.createEmaInfoForTimeframes(fiveMinSeries, FIVE_MIN).getEma9());
                    double ema21_5min = emaIndicator.getLastEmaValue(fiveMinSeries, emaIndicator.createEmaInfoForTimeframes(fiveMinSeries, FIVE_MIN).getEma20());
                    
                    indicators.setEma9_5min_gt_ema21_5min(ema9_5min > ema21_5min);
                } catch (Exception e) {
                    indicators.setEma9_5min_gt_ema21_5min(null);
                }
            } else {
                indicators.setEma9_5min_gt_ema21_5min(null);
            }
            
            // 15-minute EMA calculation
            if (fifteenMinSeries != null && fifteenMinSeries.getBarCount() >= 21) {
                try {
                    double ema9_15min = emaIndicator.getLastEmaValue(fifteenMinSeries, emaIndicator.createEmaInfoForTimeframes(fifteenMinSeries, FIFTEEN_MIN).getEma9());
                    double ema21_15min = emaIndicator.getLastEmaValue(fifteenMinSeries, emaIndicator.createEmaInfoForTimeframes(fifteenMinSeries, FIFTEEN_MIN).getEma20());
                    
                    indicators.setEma9_15min_gt_ema21_15min(ema9_15min > ema21_15min);
                } catch (Exception e) {
                    indicators.setEma9_15min_gt_ema21_15min(null);
                }
            } else {
                indicators.setEma9_15min_gt_ema21_15min(null);
            }
            
        } catch (Exception e) {
            log.error("Error flattening EMA indicators", e);
            // Set to null on error to indicate no data
            indicators.setEma9_1min_gt_ema21_1min(null);
            indicators.setEma9_5min_gt_ema21_5min(null);
            indicators.setEma9_15min_gt_ema21_15min(null);
        }
    }
    
    private void flattenRsiIndicators(FlattenedIndicators indicators, BarSeries oneMinSeries, BarSeries fiveMinSeries, BarSeries fifteenMinSeries) {
        try {
            // Initialize rules if not already done
            if (callRule == null || putRule == null) {
                initializeRules();
            }
            
            // Get RSI thresholds from configuration
            double callRsiThreshold = callRule.getMinRsiThreshold(); // 56.0
            double putRsiThreshold = putRule.getMaxRsiThreshold();   // 44.0
            
            log.debug("RSI Thresholds - Call: {}, Put: {}", callRsiThreshold, putRsiThreshold);
            
            // Calculate actual RSI values using RsiIndicator
            RsiIndicator rsiIndicator = new RsiIndicator();
            
            // 1-minute RSI calculation - Reduced minimum bars for backtesting
            if (oneMinSeries != null && oneMinSeries.getBarCount() >= 10) { // Reduced from 14 to 10
                Double rsi_1min = rsiIndicator.getRsiValue(oneMinSeries, 14);
                log.debug("1min RSI: {} (BarCount: {})", rsi_1min, oneMinSeries.getBarCount());
                
                if (rsi_1min != null) {
                    indicators.setRsi_1min_gt_70(rsi_1min > 70.0);
                    indicators.setRsi_1min_lt_30(rsi_1min < 30.0);
                    indicators.setRsi_1min_gt_56(rsi_1min > callRsiThreshold);
                    indicators.setRsi_1min_lt_44(rsi_1min < putRsiThreshold);
                } else {
                    indicators.setRsi_1min_gt_70(null);
                    indicators.setRsi_1min_lt_30(null);
                    indicators.setRsi_1min_gt_56(null);
                    indicators.setRsi_1min_lt_44(null);
                }
            } else {
                log.debug("1min BarSeries insufficient data - BarCount: {}", oneMinSeries != null ? oneMinSeries.getBarCount() : 0);
                indicators.setRsi_1min_gt_70(null);
                indicators.setRsi_1min_lt_30(null);
                indicators.setRsi_1min_gt_56(null);
                indicators.setRsi_1min_lt_44(null);
            }
            
            // 5-minute RSI calculation - Reduced minimum bars for backtesting
            if (fiveMinSeries != null && fiveMinSeries.getBarCount() >= 10) { // Reduced from 14 to 10
                Double rsi_5min = rsiIndicator.getRsiValue(fiveMinSeries, 14);
                log.debug("5min RSI: {} (BarCount: {})", rsi_5min, fiveMinSeries.getBarCount());
                
                if (rsi_5min != null) {
                    indicators.setRsi_5min_gt_70(rsi_5min > 70.0);
                    indicators.setRsi_5min_lt_30(rsi_5min < 30.0);
                    indicators.setRsi_5min_gt_56(rsi_5min > callRsiThreshold);
                    indicators.setRsi_5min_lt_44(rsi_5min < putRsiThreshold);
                } else {
                    indicators.setRsi_5min_gt_70(null);
                    indicators.setRsi_5min_lt_30(null);
                    indicators.setRsi_5min_gt_56(null);
                    indicators.setRsi_5min_lt_44(null);
                }
            } else {
                log.debug("5min BarSeries insufficient data - BarCount: {}", fiveMinSeries != null ? fiveMinSeries.getBarCount() : 0);
                indicators.setRsi_5min_gt_70(null);
                indicators.setRsi_5min_lt_30(null);
                indicators.setRsi_5min_gt_56(null);
                indicators.setRsi_5min_lt_44(null);
            }
            
            // 15-minute RSI calculation - Reduced minimum bars for backtesting
            if (fifteenMinSeries != null && fifteenMinSeries.getBarCount() >= 10) { // Reduced from 14 to 10
                Double rsi_15min = rsiIndicator.getRsiValue(fifteenMinSeries, 14);
                log.debug("15min RSI: {} (BarCount: {})", rsi_15min, fifteenMinSeries.getBarCount());
                
                if (rsi_15min != null) {
                    indicators.setRsi_15min_gt_70(rsi_15min > 70.0);
                    indicators.setRsi_15min_lt_30(rsi_15min < 30.0);
                    indicators.setRsi_15min_gt_56(rsi_15min > callRsiThreshold);
                    indicators.setRsi_15min_lt_44(rsi_15min < putRsiThreshold);
                } else {
                    indicators.setRsi_15min_gt_70(null);
                    indicators.setRsi_15min_lt_30(null);
                    indicators.setRsi_15min_gt_56(null);
                    indicators.setRsi_15min_lt_44(null);
                }
            } else {
                log.debug("15min BarSeries insufficient data - BarCount: {}", fifteenMinSeries != null ? fifteenMinSeries.getBarCount() : 0);
                indicators.setRsi_15min_gt_70(null);
                indicators.setRsi_15min_lt_30(null);
                indicators.setRsi_15min_gt_56(null);
                indicators.setRsi_15min_lt_44(null);
            }
            
        } catch (Exception e) {
            log.error("Error flattening RSI indicators", e);
            // Set all to null on error
            indicators.setRsi_1min_gt_70(null);
            indicators.setRsi_1min_lt_30(null);
            indicators.setRsi_1min_gt_56(null);
            indicators.setRsi_1min_lt_44(null);
            indicators.setRsi_5min_gt_70(null);
            indicators.setRsi_5min_lt_30(null);
            indicators.setRsi_5min_gt_56(null);
            indicators.setRsi_5min_lt_44(null);
            indicators.setRsi_15min_gt_70(null);
            indicators.setRsi_15min_lt_30(null);
            indicators.setRsi_15min_gt_56(null);
            indicators.setRsi_15min_lt_44(null);
        }
    }
    
    private void flattenVolumeIndicators(FlattenedIndicators indicators, BarSeries oneMinSeries, BarSeries fiveMinSeries, BarSeries fifteenMinSeries, Tick tick) {
        try {
            // Get current volume from the tick
            long currentVolume = tick.getVolumeTradedToday();
            log.debug("Current volume: {} for instrument: {}", currentVolume, tick.getInstrumentToken());
            
            // 1-minute volume surge using enhanced calculation - Reduced minimum bars for backtesting
            if (oneMinSeries != null && oneMinSeries.getBarCount() >= 15) { // Reduced from 20 to 15
                try {
                    PriceVolumeSurgeIndicator.VolumeSurgeResult surge1min = priceVolumeSurgeIndicator.calculateVolumeSurge(
                        indicators.getInstrumentToken(), ONE_MIN, currentVolume);
                    indicators.setVolume_1min_surge(surge1min.hasSurge());
                    log.debug("1min Volume Surge: {} (Multiplier: {})", surge1min.hasSurge(), surge1min.getVolumeMultiplier());
                    if (indicators.getVolume_surge_multiplier() == null || indicators.getVolume_surge_multiplier() < surge1min.getVolumeMultiplier()) {
                        indicators.setVolume_surge_multiplier(surge1min.getVolumeMultiplier());
                    }
                } catch (Exception e) {
                    log.debug("Error calculating 1min volume surge: {}", e.getMessage());
                    indicators.setVolume_1min_surge(null);
                }
            } else {
                log.debug("1min BarSeries insufficient data for volume - BarCount: {}", oneMinSeries != null ? oneMinSeries.getBarCount() : 0);
                indicators.setVolume_1min_surge(null);
            }
            
            // 5-minute volume surge using enhanced calculation - Reduced minimum bars for backtesting
            if (fiveMinSeries != null && fiveMinSeries.getBarCount() >= 15) { // Reduced from 20 to 15
                try {
                    PriceVolumeSurgeIndicator.VolumeSurgeResult surge5min = priceVolumeSurgeIndicator.calculateVolumeSurge(
                        indicators.getInstrumentToken(), FIVE_MIN, currentVolume);
                    indicators.setVolume_5min_surge(surge5min.hasSurge());
                    log.debug("5min Volume Surge: {} (Multiplier: {})", surge5min.hasSurge(), surge5min.getVolumeMultiplier());
                    if (indicators.getVolume_surge_multiplier() == null || indicators.getVolume_surge_multiplier() < surge5min.getVolumeMultiplier()) {
                        indicators.setVolume_surge_multiplier(surge5min.getVolumeMultiplier());
                    }
                } catch (Exception e) {
                    log.debug("Error calculating 5min volume surge: {}", e.getMessage());
                    indicators.setVolume_5min_surge(null);
                }
            } else {
                log.debug("5min BarSeries insufficient data for volume - BarCount: {}", fiveMinSeries != null ? fiveMinSeries.getBarCount() : 0);
                indicators.setVolume_5min_surge(null);
            }
            
            // 15-minute volume surge using enhanced calculation - Reduced minimum bars for backtesting
            if (fifteenMinSeries != null && fifteenMinSeries.getBarCount() >= 15) { // Reduced from 20 to 15
                try {
                    PriceVolumeSurgeIndicator.VolumeSurgeResult surge15min = priceVolumeSurgeIndicator.calculateVolumeSurge(
                        indicators.getInstrumentToken(), FIFTEEN_MIN, currentVolume);
                    indicators.setVolume_15min_surge(surge15min.hasSurge());
                    log.debug("15min Volume Surge: {} (Multiplier: {})", surge15min.hasSurge(), surge15min.getVolumeMultiplier());
                    if (indicators.getVolume_surge_multiplier() == null || indicators.getVolume_surge_multiplier() < surge15min.getVolumeMultiplier()) {
                        indicators.setVolume_surge_multiplier(surge15min.getVolumeMultiplier());
                    }
                } catch (Exception e) {
                    log.debug("Error calculating 15min volume surge: {}", e.getMessage());
                    indicators.setVolume_15min_surge(null);
                }
            } else {
                log.debug("15min BarSeries insufficient data for volume - BarCount: {}", fifteenMinSeries != null ? fifteenMinSeries.getBarCount() : 0);
                indicators.setVolume_15min_surge(null);
            }
            
            // Set default multiplier if none calculated
            if (indicators.getVolume_surge_multiplier() == null) {
                indicators.setVolume_surge_multiplier(1.0);
            }
            
        } catch (Exception e) {
            log.error("Error flattening volume indicators", e);
            indicators.setVolume_1min_surge(null);
            indicators.setVolume_5min_surge(null);
            indicators.setVolume_15min_surge(null);
            indicators.setVolume_surge_multiplier(1.0);
        }
    }
    
    private void flattenPriceActionIndicators(FlattenedIndicators indicators, BarSeries oneMinSeries, BarSeries fiveMinSeries, BarSeries fifteenMinSeries, Tick tick) {
        try {
            // VWAP indicators
            if (fiveMinSeries != null && fiveMinSeries.getBarCount() >= 20) {
                Double vwap5min = vwapIndicator.calculateVWAP(fiveMinSeries);
                if (vwap5min != null) {
                    indicators.setPrice_gt_vwap_5min(tick.getLastTradedPrice() > vwap5min);
                    indicators.setPrice_lt_vwap_5min(tick.getLastTradedPrice() < vwap5min);
                }
            }
            
            if (oneMinSeries != null && oneMinSeries.getBarCount() >= 20) {
                Double vwap1min = vwapIndicator.calculateVWAP(oneMinSeries);
                if (vwap1min != null) {
                    indicators.setPrice_gt_vwap_1min(tick.getLastTradedPrice() > vwap1min);
                    indicators.setPrice_lt_vwap_1min(tick.getLastTradedPrice() < vwap1min);
                }
            }
            
            if (fifteenMinSeries != null && fifteenMinSeries.getBarCount() >= 20) {
                Double vwap15min = vwapIndicator.calculateVWAP(fifteenMinSeries);
                if (vwap15min != null) {
                    indicators.setPrice_gt_vwap_15min(tick.getLastTradedPrice() > vwap15min);
                    indicators.setPrice_lt_vwap_15min(tick.getLastTradedPrice() < vwap15min);
                }
            }
            
            // Support/Resistance indicators
            if (fiveMinSeries != null && fiveMinSeries.getBarCount() >= 20) {
                // Calculate support and resistance using existing methods
                try {
                    // Create EMA indicator info for support/resistance calculation
                    EmaIndicatorInfo emaInfo = multiEmaIndicator.createEmaInfoForTimeframes(fiveMinSeries, FIVE_MIN);
                    
                    // Calculate support and resistance levels
                    Set<Resistance> resistances = supportResistanceIndicator.calculateResistances(FIVE_MIN, fiveMinSeries, tick.getLastTradedPrice(), emaInfo, new int[]{20, 50, 100});
                    Set<Support> supports = supportResistanceIndicator.calculateSupports(FIVE_MIN, fiveMinSeries, tick.getLastTradedPrice(), emaInfo, new int[]{20, 50, 100});
                    
                    // Get nearest resistance and support levels
                    double nearestResistance = resistances.isEmpty() ? tick.getLastTradedPrice() * 1.02 : resistances.iterator().next().getResistanceValue();
                    double nearestSupport = supports.isEmpty() ? tick.getLastTradedPrice() * 0.98 : supports.iterator().next().getSupportValue();
                    
                    // Check if price is above resistance (breakout)
                    indicators.setPrice_above_resistance(tick.getLastTradedPrice() > nearestResistance);
                    
                    // Check if price is below support (breakdown)
                    indicators.setPrice_below_support(tick.getLastTradedPrice() < nearestSupport);
                    
                    // Add breakout strength calculation
                    if (indicators.getPrice_above_resistance()) {
                        indicators.setBreakoutStrength((tick.getLastTradedPrice() - nearestResistance) / nearestResistance * 100);
                    } else if (indicators.getPrice_below_support()) {
                        indicators.setBreakdownStrength((nearestSupport - tick.getLastTradedPrice()) / nearestSupport * 100);
                    }
                    
                } catch (Exception e) {
                    log.warn("Error calculating support/resistance levels", e);
                    indicators.setPrice_above_resistance(false);
                    indicators.setPrice_below_support(false);
                }
            }
            
        } catch (Exception e) {
            log.error("Error flattening price action indicators", e);
        }
    }
    
    private void flattenCandlestickPatternIndicators(FlattenedIndicators indicators, BarSeries oneMinSeries, BarSeries fiveMinSeries, BarSeries fifteenMinSeries) {
        try {
            // Process 1-minute candlestick patterns
            if (oneMinSeries != null && oneMinSeries.getBarCount() >= 3) {
                processCandlestickPatterns(indicators, oneMinSeries, "1min");
            }
            
            // Process 3-minute candlestick patterns (using 5min as proxy since 3min not available)
            if (fiveMinSeries != null && fiveMinSeries.getBarCount() >= 3) {
                processCandlestickPatterns(indicators, fiveMinSeries, "3min");
            }
            
            // Process 5-minute candlestick patterns
            if (fiveMinSeries != null && fiveMinSeries.getBarCount() >= 3) {
                processCandlestickPatterns(indicators, fiveMinSeries, "5min");
            }
            
        } catch (Exception e) {
            log.error("Error flattening candlestick pattern indicators", e);
        }
    }
    
    private void processCandlestickPatterns(FlattenedIndicators indicators, BarSeries barSeries, String timeframe) {
        int currentIndex = barSeries.getBarCount() - 1;
        
        // Single candle patterns
        if (currentIndex >= 0) {
            Bar currentBar = barSeries.getBar(currentIndex);
            
            // Bullish patterns
            setPatternIndicator(indicators, "hammer", timeframe, CandlestickPattern.isHammer(currentBar));
            setPatternIndicator(indicators, "inverted_hammer", timeframe, CandlestickPattern.isInvertedHammer(currentBar));
            setPatternIndicator(indicators, "bullish_marubozu", timeframe, CandlestickPattern.isBullishMarubozu(currentBar));
            setPatternIndicator(indicators, "long_lower_shadow", timeframe, CandlestickPattern.isLongLowerShadow(currentBar));
            
            // Bearish patterns
            setPatternIndicator(indicators, "shooting_star", timeframe, CandlestickPattern.isShootingStar(currentBar));
            setPatternIndicator(indicators, "hanging_man", timeframe, CandlestickPattern.isHangingMan(currentBar));
            setPatternIndicator(indicators, "bearish_marubozu", timeframe, CandlestickPattern.isBearishMarubozu(currentBar));
            setPatternIndicator(indicators, "long_upper_shadow", timeframe, CandlestickPattern.isLongUpperShadow(currentBar));
            
            // Neutral patterns
            setPatternIndicator(indicators, "doji", timeframe, CandlestickPattern.isDoji(currentBar));
            setPatternIndicator(indicators, "spinning_top", timeframe, CandlestickPattern.isSpinningTop(currentBar));
            setPatternIndicator(indicators, "marubozu", timeframe, CandlestickPattern.isMarubozu(currentBar));
            setPatternIndicator(indicators, "long_body", timeframe, CandlestickPattern.isLongBody(currentBar));
            setPatternIndicator(indicators, "short_body", timeframe, CandlestickPattern.isShortBody(currentBar));
            
            // Candle color patterns (for directional confirmation)
            setPatternIndicator(indicators, "green_candle", timeframe, CandlestickPattern.isGreenCandle(currentBar));
            setPatternIndicator(indicators, "red_candle", timeframe, CandlestickPattern.isRedCandle(currentBar));
        }
        
        // Two candle patterns
        if (currentIndex >= 1) {
            Bar currentBar = barSeries.getBar(currentIndex);
            Bar previousBar = barSeries.getBar(currentIndex - 1);
            
            // Bullish patterns
            setPatternIndicator(indicators, "bullish_engulfing", timeframe, CandlestickPattern.isBullishEngulfing(previousBar, currentBar));
            setPatternIndicator(indicators, "bullish_harami", timeframe, CandlestickPattern.isBullishHarami(previousBar, currentBar));
            setPatternIndicator(indicators, "bullish_doji_star", timeframe, CandlestickPattern.isBullishDojiStar(previousBar, currentBar));
            
            // Bearish patterns
            setPatternIndicator(indicators, "bearish_engulfing", timeframe, CandlestickPattern.isBearishEngulfing(previousBar, currentBar));
            setPatternIndicator(indicators, "bearish_harami", timeframe, CandlestickPattern.isBearishHarami(previousBar, currentBar));
            setPatternIndicator(indicators, "bearish_doji_star", timeframe, CandlestickPattern.isBearishDojiStar(previousBar, currentBar));
        }
        
        // Three candle patterns
        if (currentIndex >= 2) {
            Bar firstBar = barSeries.getBar(currentIndex - 2);
            Bar secondBar = barSeries.getBar(currentIndex - 1);
            Bar thirdBar = barSeries.getBar(currentIndex);
            
            // Bullish patterns
            setPatternIndicator(indicators, "bullish_morning_star", timeframe, CandlestickPattern.isBullishMorningStar(firstBar, secondBar, thirdBar));
            
            // Bearish patterns
            setPatternIndicator(indicators, "bearish_evening_star", timeframe, CandlestickPattern.isBearishEveningStar(firstBar, secondBar, thirdBar));
        }
    }
    
    private void setPatternIndicator(FlattenedIndicators indicators, String patternName, String timeframe, boolean value) {
        String fieldName = patternName + "_" + timeframe;
        
        switch (fieldName) {
            // Bullish patterns
            case "bullish_engulfing_1min": indicators.setBullish_engulfing_1min(value); break;
            case "bullish_engulfing_3min": indicators.setBullish_engulfing_3min(value); break;
            case "bullish_engulfing_5min": indicators.setBullish_engulfing_5min(value); break;
            case "bullish_harami_1min": indicators.setBullish_harami_1min(value); break;
            case "bullish_harami_3min": indicators.setBullish_harami_3min(value); break;
            case "bullish_harami_5min": indicators.setBullish_harami_5min(value); break;
            case "bullish_morning_star_1min": indicators.setBullish_morning_star_1min(value); break;
            case "bullish_morning_star_3min": indicators.setBullish_morning_star_3min(value); break;
            case "bullish_morning_star_5min": indicators.setBullish_morning_star_5min(value); break;
            case "hammer_1min": indicators.setHammer_1min(value); break;
            case "hammer_3min": indicators.setHammer_3min(value); break;
            case "hammer_5min": indicators.setHammer_5min(value); break;
            case "inverted_hammer_1min": indicators.setInverted_hammer_1min(value); break;
            case "inverted_hammer_3min": indicators.setInverted_hammer_3min(value); break;
            case "inverted_hammer_5min": indicators.setInverted_hammer_5min(value); break;
            case "bullish_marubozu_1min": indicators.setBullish_marubozu_1min(value); break;
            case "bullish_marubozu_3min": indicators.setBullish_marubozu_3min(value); break;
            case "bullish_marubozu_5min": indicators.setBullish_marubozu_5min(value); break;
            case "long_lower_shadow_1min": indicators.setLong_lower_shadow_1min(value); break;
            case "long_lower_shadow_3min": indicators.setLong_lower_shadow_3min(value); break;
            case "long_lower_shadow_5min": indicators.setLong_lower_shadow_5min(value); break;
            
            // Bearish patterns
            case "bearish_engulfing_1min": indicators.setBearish_engulfing_1min(value); break;
            case "bearish_engulfing_3min": indicators.setBearish_engulfing_3min(value); break;
            case "bearish_engulfing_5min": indicators.setBearish_engulfing_5min(value); break;
            case "bearish_harami_1min": indicators.setBearish_harami_1min(value); break;
            case "bearish_harami_3min": indicators.setBearish_harami_3min(value); break;
            case "bearish_harami_5min": indicators.setBearish_harami_5min(value); break;
            case "bearish_evening_star_1min": indicators.setBearish_evening_star_1min(value); break;
            case "bearish_evening_star_3min": indicators.setBearish_evening_star_3min(value); break;
            case "bearish_evening_star_5min": indicators.setBearish_evening_star_5min(value); break;
            case "shooting_star_1min": indicators.setShooting_star_1min(value); break;
            case "shooting_star_3min": indicators.setShooting_star_3min(value); break;
            case "shooting_star_5min": indicators.setShooting_star_5min(value); break;
            case "hanging_man_1min": indicators.setHanging_man_1min(value); break;
            case "hanging_man_3min": indicators.setHanging_man_3min(value); break;
            case "hanging_man_5min": indicators.setHanging_man_5min(value); break;
            case "bearish_marubozu_1min": indicators.setBearish_marubozu_1min(value); break;
            case "bearish_marubozu_3min": indicators.setBearish_marubozu_3min(value); break;
            case "bearish_marubozu_5min": indicators.setBearish_marubozu_5min(value); break;
            case "long_upper_shadow_1min": indicators.setLong_upper_shadow_1min(value); break;
            case "long_upper_shadow_3min": indicators.setLong_upper_shadow_3min(value); break;
            case "long_upper_shadow_5min": indicators.setLong_upper_shadow_5min(value); break;
            
            // Neutral patterns
            case "doji_1min": indicators.setDoji_1min(value); break;
            case "doji_3min": indicators.setDoji_3min(value); break;
            case "doji_5min": indicators.setDoji_5min(value); break;
            case "spinning_top_1min": indicators.setSpinning_top_1min(value); break;
            case "spinning_top_3min": indicators.setSpinning_top_3min(value); break;
            case "spinning_top_5min": indicators.setSpinning_top_5min(value); break;
            case "marubozu_1min": indicators.setMarubozu_1min(value); break;
            case "marubozu_3min": indicators.setMarubozu_3min(value); break;
            case "marubozu_5min": indicators.setMarubozu_5min(value); break;
            case "long_body_1min": indicators.setLong_body_1min(value); break;
            case "long_body_3min": indicators.setLong_body_3min(value); break;
            case "long_body_5min": indicators.setLong_body_5min(value); break;
            case "short_body_1min": indicators.setShort_body_1min(value); break;
            case "short_body_3min": indicators.setShort_body_3min(value); break;
            case "short_body_5min": indicators.setShort_body_5min(value); break;
            
            // Candle color patterns
            case "green_candle_1min": indicators.setGreen_candle_1min(value); break;
            case "green_candle_3min": indicators.setGreen_candle_3min(value); break;
            case "green_candle_5min": indicators.setGreen_candle_5min(value); break;
            case "red_candle_1min": indicators.setRed_candle_1min(value); break;
            case "red_candle_3min": indicators.setRed_candle_3min(value); break;
            case "red_candle_5min": indicators.setRed_candle_5min(value); break;
        }
    }
    
    private FuturesignalData calculateFuturesignals(FlattenedIndicators indicators) {
        // Enhanced futuresignal calculation using correct RSI thresholds
        boolean oneMinBullish = indicators.getEma9_1min_gt_ema21_1min() != null && 
                               indicators.getEma9_1min_gt_ema21_1min() && 
                               indicators.getRsi_1min_gt_56() != null && 
                               indicators.getRsi_1min_gt_56();
        
        boolean fiveMinBullish = indicators.getEma9_5min_gt_ema21_5min() != null && 
                                indicators.getEma9_5min_gt_ema21_5min() && 
                                indicators.getRsi_5min_gt_56() != null && 
                                indicators.getRsi_5min_gt_56();
        
        boolean fifteenMinBullish = indicators.getEma9_15min_gt_ema21_15min() != null && 
                                   indicators.getEma9_15min_gt_ema21_15min() && 
                                   indicators.getRsi_15min_gt_56() != null && 
                                   indicators.getRsi_15min_gt_56();
        
        boolean oneMinBearish = indicators.getEma9_1min_gt_ema21_1min() != null && 
                               !indicators.getEma9_1min_gt_ema21_1min() && 
                               indicators.getRsi_1min_lt_44() != null && 
                               indicators.getRsi_1min_lt_44();
        
        boolean fiveMinBearish = indicators.getEma9_5min_gt_ema21_5min() != null && 
                                !indicators.getEma9_5min_gt_ema21_5min() && 
                                indicators.getRsi_5min_lt_44() != null && 
                                indicators.getRsi_5min_lt_44();
        
        boolean fifteenMinBearish = indicators.getEma9_15min_gt_ema21_15min() != null && 
                                   !indicators.getEma9_15min_gt_ema21_15min() && 
                                   indicators.getRsi_15min_lt_44() != null && 
                                   indicators.getRsi_15min_lt_44();
        
        // Calculate all timeframes alignment
        boolean allTimeframesBullish = oneMinBullish && fiveMinBullish && fifteenMinBullish;
        boolean allTimeframesBearish = oneMinBearish && fiveMinBearish && fifteenMinBearish;
        
        return FuturesignalData.builder()
                .oneMinBullishSurge(oneMinBullish)
                .fiveMinBullishSurge(fiveMinBullish)
                .fifteenMinBullishSurge(fifteenMinBullish)
                .oneMinBearishSurge(oneMinBearish)
                .fiveMinBearishSurge(fiveMinBearish)
                .fifteenMinBearishSurge(fifteenMinBearish)
                .allTimeframesBullish(allTimeframesBullish)
                .allTimeframesBearish(allTimeframesBearish)
                .build();
    }
    
    /**
     * Calculate momentum and trend strength score (-3 to +3)
     */
    private double calculateMomentumScore(FlattenedIndicators indicators) {
        double score = 0.0;
        
        // Multi-timeframe alignment bonus
        int bullishTimeframes = 0;
        int bearishTimeframes = 0;
        
        // Count bullish timeframes
        if (indicators.getEma9_1min_gt_ema21_1min() != null && indicators.getEma9_1min_gt_ema21_1min()) bullishTimeframes++;
        if (indicators.getEma9_5min_gt_ema21_5min() != null && indicators.getEma9_5min_gt_ema21_5min()) bullishTimeframes++;
        if (indicators.getEma9_15min_gt_ema21_15min() != null && indicators.getEma9_15min_gt_ema21_15min()) bullishTimeframes++;
        
        // Count bearish timeframes
        if (indicators.getEma9_1min_gt_ema21_1min() != null && !indicators.getEma9_1min_gt_ema21_1min()) bearishTimeframes++;
        if (indicators.getEma9_5min_gt_ema21_5min() != null && !indicators.getEma9_5min_gt_ema21_5min()) bearishTimeframes++;
        if (indicators.getEma9_15min_gt_ema21_15min() != null && !indicators.getEma9_15min_gt_ema21_15min()) bearishTimeframes++;
        
        // Perfect alignment bonus
        if (bullishTimeframes == 3) {
            score += 2.0; // All timeframes bullish
        } else if (bearishTimeframes == 3) {
            score -= 2.0; // All timeframes bearish
        } else if (bullishTimeframes == 2) {
            score += 1.0; // Majority bullish
        } else if (bearishTimeframes == 2) {
            score -= 1.0; // Majority bearish
        }
        
        // RSI momentum bonus
        int bullishRsi = 0;
        int bearishRsi = 0;
        
        if (indicators.getRsi_1min_gt_56() != null && indicators.getRsi_1min_gt_56()) bullishRsi++;
        if (indicators.getRsi_5min_gt_56() != null && indicators.getRsi_5min_gt_56()) bullishRsi++;
        if (indicators.getRsi_15min_gt_56() != null && indicators.getRsi_15min_gt_56()) bullishRsi++;
        
        if (indicators.getRsi_1min_lt_44() != null && indicators.getRsi_1min_lt_44()) bearishRsi++;
        if (indicators.getRsi_5min_lt_44() != null && indicators.getRsi_5min_lt_44()) bearishRsi++;
        if (indicators.getRsi_15min_lt_44() != null && indicators.getRsi_15min_lt_44()) bearishRsi++;
        
        if (bullishRsi == 3) {
            score += 1.0; // All RSI timeframes bullish
        } else if (bearishRsi == 3) {
            score -= 1.0; // All RSI timeframes bearish
        }
        
        return Math.max(-3.0, Math.min(3.0, score));
    }
    
    /**
     * Evaluate CALL entry quality with flexible scoring
     */
    public EntryQuality evaluateCallEntryQuality(FlattenedIndicators indicators, Tick tick) {
        EntryQuality quality = EntryQuality.builder()
                .instrumentToken(String.valueOf(tick.getInstrumentToken()))
                .timestamp(tick.getTickTimestamp().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime())
                .lastTradedPrice(tick.getLastTradedPrice())
                .entryType("CALL")
                .build();
        
        // EMA Quality Score (0-10)
        double emaScore = 0.0;
        if (indicators.getEma9_5min_gt_ema21_5min() != null && indicators.getEma9_5min_gt_ema21_5min()) emaScore += 5.0;
        if (indicators.getEma9_1min_gt_ema21_1min() != null && indicators.getEma9_1min_gt_ema21_1min()) emaScore += 5.0;
        quality.setEmaScore(emaScore);
        
        // RSI Quality Score (0-10)
        double rsiScore = 0.0;
        if (indicators.getRsi_5min_gt_56() != null && indicators.getRsi_5min_gt_56()) rsiScore += 5.0;
        if (indicators.getRsi_1min_gt_56() != null && indicators.getRsi_1min_gt_56()) rsiScore += 5.0;
        quality.setRsiScore(rsiScore);
        
        // Volume Quality Score (0-10)
        double volumeScore = 0.0;
        if (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) volumeScore += 5.0;
        if (indicators.getVolume_1min_surge() != null && indicators.getVolume_1min_surge()) volumeScore += 3.0;
        if (indicators.getVolume_surge_multiplier() != null && indicators.getVolume_surge_multiplier() >= 3.0) volumeScore += 2.0;
        quality.setVolumeScore(volumeScore);
        
        // Price Action Quality Score (0-10)
        double priceActionScore = 0.0;
        if (indicators.getPrice_gt_vwap_5min() != null && indicators.getPrice_gt_vwap_5min()) priceActionScore += 5.0;
        if (indicators.getPrice_above_resistance() != null && indicators.getPrice_above_resistance()) priceActionScore += 5.0;
        quality.setPriceActionScore(priceActionScore);
        
        // Futuresignal Quality Score (0-10)
        double futuresignalScore = 0.0;
        if (indicators.getFuturesignals() != null && indicators.getFuturesignals().getAllTimeframesBullish()) {
            futuresignalScore = 10.0;
        } else if (indicators.getFuturesignals() != null && 
                   (indicators.getFuturesignals().getFiveMinBullishSurge() || indicators.getFuturesignals().getOneMinBullishSurge())) {
            futuresignalScore = 5.0;
        }
        quality.setFuturesignalScore(futuresignalScore);
        
        // Momentum Quality Score (0-10)
        double momentumScore = 0.0;
        int bullishTimeframes = 0;
        if (indicators.getEma9_1min_gt_ema21_1min() != null && indicators.getEma9_1min_gt_ema21_1min()) bullishTimeframes++;
        if (indicators.getEma9_5min_gt_ema21_5min() != null && indicators.getEma9_5min_gt_ema21_5min()) bullishTimeframes++;
        if (indicators.getEma9_15min_gt_ema21_15min() != null && indicators.getEma9_15min_gt_ema21_15min()) bullishTimeframes++;
        
        if (bullishTimeframes == 3) momentumScore = 10.0;
        else if (bullishTimeframes == 2) momentumScore = 7.0;
        else if (bullishTimeframes == 1) momentumScore = 3.0;
        quality.setMomentumScore(momentumScore);
        
        // Candlestick Pattern Quality Score (0-10)
        double candlestickScore = 0.0;
        
        // High reliability bullish patterns (3 points each)
        if (indicators.getBullish_engulfing_5min() != null && indicators.getBullish_engulfing_5min()) candlestickScore += 3.0;
        if (indicators.getBullish_engulfing_1min() != null && indicators.getBullish_engulfing_1min()) candlestickScore += 3.0;
        if (indicators.getBullish_morning_star_5min() != null && indicators.getBullish_morning_star_5min()) candlestickScore += 3.0;
        if (indicators.getBullish_morning_star_1min() != null && indicators.getBullish_morning_star_1min()) candlestickScore += 3.0;
        
        // Medium reliability bullish patterns (2 points each)
        if (indicators.getHammer_5min() != null && indicators.getHammer_5min()) candlestickScore += 2.0;
        if (indicators.getHammer_1min() != null && indicators.getHammer_1min()) candlestickScore += 2.0;
        if (indicators.getInverted_hammer_5min() != null && indicators.getInverted_hammer_5min()) candlestickScore += 2.0;
        if (indicators.getInverted_hammer_1min() != null && indicators.getInverted_hammer_1min()) candlestickScore += 2.0;
        if (indicators.getBullish_harami_5min() != null && indicators.getBullish_harami_5min()) candlestickScore += 2.0;
        if (indicators.getBullish_harami_1min() != null && indicators.getBullish_harami_1min()) candlestickScore += 2.0;
        if (indicators.getBullish_marubozu_5min() != null && indicators.getBullish_marubozu_5min()) candlestickScore += 2.0;
        if (indicators.getBullish_marubozu_1min() != null && indicators.getBullish_marubozu_1min()) candlestickScore += 2.0;
        
        // Low reliability bullish patterns (1 point each)
        if (indicators.getLong_lower_shadow_5min() != null && indicators.getLong_lower_shadow_5min()) candlestickScore += 1.0;
        if (indicators.getLong_lower_shadow_1min() != null && indicators.getLong_lower_shadow_1min()) candlestickScore += 1.0;
        if (indicators.getLong_body_5min() != null && indicators.getLong_body_5min()) candlestickScore += 1.0;
        if (indicators.getLong_body_1min() != null && indicators.getLong_body_1min()) candlestickScore += 1.0;
        
        // Cap the score at 10
        candlestickScore = Math.min(candlestickScore, 10.0);
        quality.setCandlestickScore(candlestickScore);
        
        // Calculate overall quality score
        quality.calculateQualityScore();
        
        return quality;
    }
    
    /**
     * Evaluate PUT entry quality with flexible scoring
     */
    public EntryQuality evaluatePutEntryQuality(FlattenedIndicators indicators, Tick tick) {
        EntryQuality quality = EntryQuality.builder()
                .instrumentToken(String.valueOf(tick.getInstrumentToken()))
                .timestamp(tick.getTickTimestamp().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime())
                .lastTradedPrice(tick.getLastTradedPrice())
                .entryType("PUT")
                .build();
        
        // EMA Quality Score (0-10)
        double emaScore = 0.0;
        if (indicators.getEma9_5min_gt_ema21_5min() != null && !indicators.getEma9_5min_gt_ema21_5min()) emaScore += 5.0;
        if (indicators.getEma9_1min_gt_ema21_1min() != null && !indicators.getEma9_1min_gt_ema21_1min()) emaScore += 5.0;
        quality.setEmaScore(emaScore);
        
        // RSI Quality Score (0-10)
        double rsiScore = 0.0;
        if (indicators.getRsi_5min_lt_44() != null && indicators.getRsi_5min_lt_44()) rsiScore += 5.0;
        if (indicators.getRsi_1min_lt_44() != null && indicators.getRsi_1min_lt_44()) rsiScore += 5.0;
        quality.setRsiScore(rsiScore);
        
        // Volume Quality Score (0-10)
        double volumeScore = 0.0;
        if (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) volumeScore += 5.0;
        if (indicators.getVolume_1min_surge() != null && indicators.getVolume_1min_surge()) volumeScore += 3.0;
        if (indicators.getVolume_surge_multiplier() != null && indicators.getVolume_surge_multiplier() >= 3.0) volumeScore += 2.0;
        quality.setVolumeScore(volumeScore);
        
        // Price Action Quality Score (0-10)
        double priceActionScore = 0.0;
        if (indicators.getPrice_lt_vwap_5min() != null && indicators.getPrice_lt_vwap_5min()) priceActionScore += 5.0;
        if (indicators.getPrice_below_support() != null && indicators.getPrice_below_support()) priceActionScore += 5.0;
        quality.setPriceActionScore(priceActionScore);
        
        // Futuresignal Quality Score (0-10)
        double futuresignalScore = 0.0;
        if (indicators.getFuturesignals() != null && indicators.getFuturesignals().getAllTimeframesBearish()) {
            futuresignalScore = 10.0;
        } else if (indicators.getFuturesignals() != null && 
                   (indicators.getFuturesignals().getFiveMinBearishSurge() || indicators.getFuturesignals().getOneMinBearishSurge())) {
            futuresignalScore = 5.0;
        }
        quality.setFuturesignalScore(futuresignalScore);
        
        // Momentum Quality Score (0-10)
        double momentumScore = 0.0;
        int bearishTimeframes = 0;
        if (indicators.getEma9_1min_gt_ema21_1min() != null && !indicators.getEma9_1min_gt_ema21_1min()) bearishTimeframes++;
        if (indicators.getEma9_5min_gt_ema21_5min() != null && !indicators.getEma9_5min_gt_ema21_5min()) bearishTimeframes++;
        if (indicators.getEma9_15min_gt_ema21_15min() != null && !indicators.getEma9_15min_gt_ema21_15min()) bearishTimeframes++;
        
        if (bearishTimeframes == 3) momentumScore = 10.0;
        else if (bearishTimeframes == 2) momentumScore = 7.0;
        else if (bearishTimeframes == 1) momentumScore = 3.0;
        quality.setMomentumScore(momentumScore);
        
        // Candlestick Pattern Quality Score (0-10)
        double candlestickScore = 0.0;
        
        // High reliability bearish patterns (3 points each)
        if (indicators.getBearish_engulfing_5min() != null && indicators.getBearish_engulfing_5min()) candlestickScore += 3.0;
        if (indicators.getBearish_engulfing_1min() != null && indicators.getBearish_engulfing_1min()) candlestickScore += 3.0;
        if (indicators.getBearish_evening_star_5min() != null && indicators.getBearish_evening_star_5min()) candlestickScore += 3.0;
        if (indicators.getBearish_evening_star_1min() != null && indicators.getBearish_evening_star_1min()) candlestickScore += 3.0;
        
        // Medium reliability bearish patterns (2 points each)
        if (indicators.getShooting_star_5min() != null && indicators.getShooting_star_5min()) candlestickScore += 2.0;
        if (indicators.getShooting_star_1min() != null && indicators.getShooting_star_1min()) candlestickScore += 2.0;
        if (indicators.getBearish_harami_5min() != null && indicators.getBearish_harami_5min()) candlestickScore += 2.0;
        if (indicators.getBearish_harami_1min() != null && indicators.getBearish_harami_1min()) candlestickScore += 2.0;
        if (indicators.getBearish_marubozu_5min() != null && indicators.getBearish_marubozu_5min()) candlestickScore += 2.0;
        if (indicators.getBearish_marubozu_1min() != null && indicators.getBearish_marubozu_1min()) candlestickScore += 2.0;
        if (indicators.getLong_upper_shadow_5min() != null && indicators.getLong_upper_shadow_5min()) candlestickScore += 2.0;
        if (indicators.getLong_upper_shadow_1min() != null && indicators.getLong_upper_shadow_1min()) candlestickScore += 2.0;
        
        // Low reliability bearish patterns (1 point each)
        if (indicators.getLong_body_5min() != null && indicators.getLong_body_5min()) candlestickScore += 1.0;
        if (indicators.getLong_body_1min() != null && indicators.getLong_body_1min()) candlestickScore += 1.0;
        if (indicators.getShort_body_5min() != null && indicators.getShort_body_5min()) candlestickScore += 1.0;
        if (indicators.getShort_body_1min() != null && indicators.getShort_body_1min()) candlestickScore += 1.0;
        
        // Cap the score at 10
        candlestickScore = Math.min(candlestickScore, 10.0);
        quality.setCandlestickScore(candlestickScore);
        
        // Calculate overall quality score
        quality.calculateQualityScore();
        
        return quality;
    }
    
    /**
     * Enhance volume indicators with future tick data using enhanced volume analysis
     * This method ensures volume calculations use actual future data with proper historical baseline
     */
    private void enhanceVolumeIndicatorsWithFutureData(FlattenedIndicators indicators, Tick futureTick) {
        try {
            // Get volume data from future tick
            long futureVolume = futureTick.getVolumeTradedToday();
            
            // Get Nifty index token for comparison
            String niftyIndexToken = indicators.getInstrumentToken(); // Assuming this is the index token
            String niftyFutureToken = String.valueOf(futureTick.getInstrumentToken());
            
            // Use enhanced volume analysis with correct volume data
            PriceVolumeSurgeIndicator.NiftyVolumeAnalysis volumeAnalysis = 
                priceVolumeSurgeIndicator.analyzeNiftyVolume(niftyIndexToken, niftyFutureToken, 
                                                           futureVolume); // ✅ CORRECT: Only future volume needed
            
            if (volumeAnalysis != null) {
                // Update volume surge flags based on enhanced analysis
                PriceVolumeSurgeIndicator.VolumeSurgeResult indexSurge = volumeAnalysis.getIndexSurge();
                PriceVolumeSurgeIndicator.VolumeSurgeResult futureSurge = volumeAnalysis.getFutureSurge();
                
                // Use the future surge since index surge will always be 0 (no volume)
                PriceVolumeSurgeIndicator.VolumeSurgeResult strongerSurge = futureSurge;
                
                // Update indicators based on enhanced analysis
                indicators.setVolume_surge_multiplier(strongerSurge.getVolumeMultiplier());
                indicators.setVolume_1min_surge(strongerSurge.hasSurge());
                indicators.setVolume_5min_surge(strongerSurge.hasSurge());
                indicators.setVolume_15min_surge(strongerSurge.hasSurge());
                
                // Log enhanced volume analysis
                log.debug("Enhanced volume analysis - Index Surge: {}x (no volume), Future Surge: {}x, Correlation: {}, Coordinated: {}", 
                        indexSurge.getVolumeMultiplier(), futureSurge.getVolumeMultiplier(), 
                        volumeAnalysis.getVolumeCorrelation(), volumeAnalysis.isCoordinatedSurge());
                
                // Additional validation for coordinated surge
                if (volumeAnalysis.isCoordinatedSurge()) {
                    log.info("🚀 COORDINATED VOLUME SURGE DETECTED - Index: {}x (no volume), Future: {}x, Total Volume: {}", 
                            indexSurge.getVolumeMultiplier(), futureSurge.getVolumeMultiplier(), volumeAnalysis.getTotalVolume());
                }
            } else {
                // Fallback to simplified calculation if enhanced analysis fails
                if (futureVolume > 0) {
                    double volumeMultiplier = Math.min(futureVolume / 1000000.0, 10.0);
                    indicators.setVolume_surge_multiplier(volumeMultiplier);
                    
                    if (volumeMultiplier >= 2.0) {
                        indicators.setVolume_1min_surge(true);
                        indicators.setVolume_5min_surge(true);
                    } else if (volumeMultiplier >= 1.5) {
                        indicators.setVolume_1min_surge(true);
                        indicators.setVolume_5min_surge(false);
                    } else {
                        indicators.setVolume_1min_surge(false);
                        indicators.setVolume_5min_surge(false);
                    }
                }
            }
            
            log.debug("Enhanced volume indicators with future data - Future Token: {}, Volume: {}, Multiplier: {}", 
                    futureTick.getInstrumentToken(), futureVolume, indicators.getVolume_surge_multiplier());
            
        } catch (Exception e) {
            log.error("Error enhancing volume indicators with future data for future tick: {}", futureTick.getInstrumentToken(), e);
            // Keep existing volume indicators if enhancement fails
        }
    }
    
    /**
     * Validate strong momentum for CALL entry (scalping perspective)
     * Uses configuration to determine which timeframes to check
     */
    private boolean validateStrongMomentum(FlattenedIndicators indicators) {
        // Require at least 2 timeframes showing bullish EMA crossover
        int bullishTimeframes = 0;
        if (indicators.getEma9_1min_gt_ema21_1min() != null && indicators.getEma9_1min_gt_ema21_1min()) bullishTimeframes++;
        if (indicators.getEma9_5min_gt_ema21_5min() != null && indicators.getEma9_5min_gt_ema21_5min()) bullishTimeframes++;
        if (indicators.getEma9_15min_gt_ema21_15min() != null && indicators.getEma9_15min_gt_ema21_15min()) bullishTimeframes++;
        
        // Require strong RSI momentum based on configuration
        int strongRsiTimeframes = 0;
        if (configService.isCallCheck1Min() && indicators.getRsi_1min_gt_56() != null && indicators.getRsi_1min_gt_56()) strongRsiTimeframes++;
        if (configService.isCallCheck5Min() && indicators.getRsi_5min_gt_56() != null && indicators.getRsi_5min_gt_56()) strongRsiTimeframes++;
        if (configService.isCallCheck15Min() && indicators.getRsi_15min_gt_56() != null && indicators.getRsi_15min_gt_56()) strongRsiTimeframes++;
        
        // Use configuration for requirements
        return bullishTimeframes >= 2 && strongRsiTimeframes >= 1;
    }
    
    /**
     * Validate strong futuresignals for CALL entry (scalping perspective)
     * Requires all timeframes to show bullish futuresignals
     */
    private boolean validateStrongFuturesignals(FlattenedIndicators indicators) {
        if (indicators.getFuturesignals() == null) {
            return false;
        }
        
        try {
            // Load futuresignals configuration
            ScalpingEntryLogic entryLogic = scalpingEntryService.loadEntryLogic("rules/scalping-entry-config.json");
            ScalpingEntryLogic.FuturesignalsConfig config = entryLogic.getFuturesignalsConfig();
            
            if (config == null || config.getCallStrategy() == null) {
                // Fallback to default behavior if config not found
                return indicators.getFuturesignals().getAllTimeframesBullish();
            }
            
            // Count bullish timeframes based on enabled timeframes
            int bullishTimeframes = 0;
            int totalTimeframes = 0;
            
            if (config.getEnabledTimeframes().contains("1min")) {
                totalTimeframes++;
                if (indicators.getFuturesignals().getOneMinBullishSurge() != null && 
                    indicators.getFuturesignals().getOneMinBullishSurge()) {
                    bullishTimeframes++;
                }
            }
            
            if (config.getEnabledTimeframes().contains("5min")) {
                totalTimeframes++;
                if (indicators.getFuturesignals().getFiveMinBullishSurge() != null && 
                    indicators.getFuturesignals().getFiveMinBullishSurge()) {
                    bullishTimeframes++;
                }
            }
            
            // Check if 15min is enabled (should be false based on your requirement)
            if (config.getEnabledTimeframes().contains("15min")) {
                totalTimeframes++;
                if (indicators.getFuturesignals().getFifteenMinBullishSurge() != null && 
                    indicators.getFuturesignals().getFifteenMinBullishSurge()) {
                    bullishTimeframes++;
                }
            }
            
            // Determine if validation passes
            boolean isValid;
            if (config.getCallStrategy().isRequireAllTimeframes()) {
                isValid = bullishTimeframes == totalTimeframes;
            } else {
                isValid = bullishTimeframes >= config.getCallStrategy().getMinBullishTimeframes();
            }
            
            // Dynamic debug logging - only show enabled timeframes
            if (!isValid) {
                String timeframeLog = buildFuturesignalsDebugLog(indicators, config.getEnabledTimeframes(), "CALL");
                log.debug("🔍 CALL FUTURESIGNALS DEBUG - {} | Bullish: {}/{} | Required: {} | Valid: {}", 
                    timeframeLog,
                    bullishTimeframes, totalTimeframes,
                    config.getCallStrategy().getMinBullishTimeframes(),
                    isValid);
            } else {
                // Also log when validation passes to see what's happening
                String timeframeLog = buildFuturesignalsDebugLog(indicators, config.getEnabledTimeframes(), "CALL");
                log.debug("✅ CALL FUTURESIGNALS PASSED - {} | Bullish: {}/{} | Required: {} | Valid: {}", 
                    timeframeLog,
                    bullishTimeframes, totalTimeframes,
                    config.getCallStrategy().getMinBullishTimeframes(),
                    isValid);
            }
            
            return isValid;
            
        } catch (Exception e) {
            log.error("Error validating CALL futuresignals", e);
            // Fallback to default behavior
            return indicators.getFuturesignals().getAllTimeframesBullish();
        }
    }
    
    /**
     * Validate strong volume surge for CALL entry (scalping perspective)
     * Uses natural, intelligent validation instead of forced cooldowns
     */
    private boolean validateStrongVolumeSurge(FlattenedIndicators indicators) {
        // 🔥 NATURAL APPROACH: Intelligent validation without forced cooldowns
        String instrumentToken = indicators.getInstrumentToken();
        long currentTime = System.currentTimeMillis();
        
        // Require volume surge in at least 1 timeframe
        int volumeSurgeTimeframes = 0;
        if (indicators.getVolume_1min_surge() != null && indicators.getVolume_1min_surge()) volumeSurgeTimeframes++;
        if (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) volumeSurgeTimeframes++;
        if (indicators.getVolume_15min_surge() != null && indicators.getVolume_15min_surge()) volumeSurgeTimeframes++;
        
        // Require strong volume multiplier (at least 2.5x for quality)
        boolean hasStrongVolumeMultiplier = indicators.getVolume_surge_multiplier() != null && 
                                           indicators.getVolume_surge_multiplier() >= 2.5;
        
        // 🔥 NATURAL: Enhanced volume surge validation with intelligent quality checks
        boolean hasVolumeSurge = volumeSurgeTimeframes >= 1 && hasStrongVolumeMultiplier;
        
        if (hasVolumeSurge) {
            // Check if this is a high-quality volume surge (natural validation)
            boolean isHighQualitySurge = validateVolumeSurgeQualityNatural(instrumentToken, indicators, currentTime);
            
            // Check if volume surge is accompanied by price momentum (natural validation)
            boolean hasPriceMomentum = validatePriceMomentum(instrumentToken, indicators);
            
            // Check if market conditions support this signal (natural validation)
            boolean hasSupportiveMarketConditions = validateMarketConditionsNatural(instrumentToken, indicators);
            
            // Update volume surge tracking for learning
            updateVolumeSurgeTrackingNatural(instrumentToken, indicators, currentTime);
            
            // Only return true if all natural validations pass
            boolean isValidSurge = isHighQualitySurge && hasPriceMomentum && hasSupportiveMarketConditions;
            
            // Volume surge validation complete
            
            return isValidSurge;
        }
        
        return false;
    }
    
    /**
     * 🔥 SIMPLIFIED: Basic volume surge validation (removed complex quality checks)
     */
    private boolean validateVolumeSurgeQualityNatural(String instrumentToken, FlattenedIndicators indicators, long currentTime) {
        Double currentMultiplier = indicators.getVolume_surge_multiplier();
        if (currentMultiplier == null) {
            return false;
        }
        
        // 🔥 SIMPLIFIED: Just check if it's a strong volume surge
        boolean isStrongVolumeSurge = currentMultiplier >= 2.0; // Any surge above 2x is considered strong
        
        return isStrongVolumeSurge;
    }
    
    /**
     * 🔥 NEW: Natural market conditions validation
     */
    private boolean validateMarketConditionsNatural(String instrumentToken, FlattenedIndicators indicators) {
        // Check if multiple timeframes are aligned (natural market condition)
        int alignedTimeframes = 0;
        
        // Check EMA alignment across timeframes
        if (indicators.getEma9_1min_gt_ema21_1min() != null && indicators.getEma9_1min_gt_ema21_1min()) alignedTimeframes++;
        if (indicators.getEma9_5min_gt_ema21_5min() != null && indicators.getEma9_5min_gt_ema21_5min()) alignedTimeframes++;
        if (indicators.getEma9_15min_gt_ema21_15min() != null && indicators.getEma9_15min_gt_ema21_15min()) alignedTimeframes++;
        
        // Check RSI alignment across timeframes
        int rsiAlignedTimeframes = 0;
        if (indicators.getRsi_1min_gt_56() != null && indicators.getRsi_1min_gt_56()) rsiAlignedTimeframes++;
        if (indicators.getRsi_5min_gt_56() != null && indicators.getRsi_5min_gt_56()) rsiAlignedTimeframes++;
        if (indicators.getRsi_15min_gt_56() != null && indicators.getRsi_15min_gt_56()) rsiAlignedTimeframes++;
        
        // Natural market condition: At least 2 timeframes aligned
        boolean hasTimeframeAlignment = alignedTimeframes >= 2;
        boolean hasRsiAlignment = rsiAlignedTimeframes >= 2;
        
        // Check for price action confirmation
        boolean hasPriceActionConfirmation = indicators.getPrice_gt_vwap_5min() != null && 
                                           indicators.getPrice_gt_vwap_5min();
        
        // Natural market conditions require alignment and confirmation
        return hasTimeframeAlignment && (hasRsiAlignment || hasPriceActionConfirmation);
    }
    
    /**
     * 🔥 NEW: Natural volume surge tracking (for learning and adaptation)
     */
    private void updateVolumeSurgeTrackingNatural(String instrumentToken, FlattenedIndicators indicators, long currentTime) {
        // Update tracking for natural learning (no forced cooldowns)
        lastVolumeSurgeTime.put(instrumentToken, currentTime);
        
        log.debug("Natural volume surge tracking updated - Instrument: {}, Time: {}, Multiplier: {}", 
                instrumentToken, currentTime, indicators.getVolume_surge_multiplier());
    }
    
    // REMOVED: Forced cooldown mechanism - replaced with natural validation
    
    /**
     * 🔥 NEW: Validate volume surge quality to prevent low-quality signals
     */
    private boolean validateVolumeSurgeQuality(String instrumentToken, FlattenedIndicators indicators, long currentTime) {
        Double currentMultiplier = indicators.getVolume_surge_multiplier();
        if (currentMultiplier == null) {
            return false;
        }
        
        // Get previous surge data
        Double lastMultiplier = lastVolumeSurgeMultiplier.get(instrumentToken);
        Integer consecutiveSurges = consecutiveVolumeSurges.get(instrumentToken);
        Double avgMultiplier = averageVolumeMultiplier.get(instrumentToken);
        
        // Initialize if first time
        if (lastMultiplier == null) {
            lastMultiplier = 0.0;
            consecutiveSurges = 0;
            avgMultiplier = currentMultiplier;
        }
        
        // Check if this is a significant improvement over last surge
        boolean isSignificantImprovement = currentMultiplier > lastMultiplier * 1.2; // 20% improvement
        
        // Check if multiplier is above average
        boolean isAboveAverage = currentMultiplier > avgMultiplier * 1.1; // 10% above average
        
        // Check if we're not getting too many consecutive surges (avoid persistent signals)
        boolean isNotExcessive = consecutiveSurges < 3; // Max 3 consecutive surges
        
        // High quality surge requires significant improvement and above average multiplier
        boolean isHighQuality = isSignificantImprovement && isAboveAverage && isNotExcessive;
        
        // Update tracking
        if (isHighQuality) {
            consecutiveSurges++;
            avgMultiplier = (avgMultiplier * 0.7) + (currentMultiplier * 0.3); // Weighted average
        } else {
            consecutiveSurges = 0; // Reset if not high quality
        }
        
        lastVolumeSurgeMultiplier.put(instrumentToken, currentMultiplier);
        consecutiveVolumeSurges.put(instrumentToken, consecutiveSurges);
        averageVolumeMultiplier.put(instrumentToken, avgMultiplier);
        
        return isHighQuality;
    }
    
    /**
     * 🔥 NEW: Validate price momentum to ensure volume surge is accompanied by price movement
     */
    private boolean validatePriceMomentum(String instrumentToken, FlattenedIndicators indicators) {
        // Get current price from indicators (we'll need to pass tick data for this)
        // For now, we'll use EMA crossover as a proxy for momentum
        
        // Check EMA momentum (price above EMAs indicates upward momentum)
        int bullishEmaCount = 0;
        if (indicators.getEma9_1min_gt_ema21_1min() != null && indicators.getEma9_1min_gt_ema21_1min()) bullishEmaCount++;
        if (indicators.getEma9_5min_gt_ema21_5min() != null && indicators.getEma9_5min_gt_ema21_5min()) bullishEmaCount++;
        if (indicators.getEma9_15min_gt_ema21_15min() != null && indicators.getEma9_15min_gt_ema21_15min()) bullishEmaCount++;
        
        // Check RSI momentum (RSI above 56 indicates bullish momentum)
        int bullishRsiCount = 0;
        if (indicators.getRsi_1min_gt_56() != null && indicators.getRsi_1min_gt_56()) bullishRsiCount++;
        if (indicators.getRsi_5min_gt_56() != null && indicators.getRsi_5min_gt_56()) bullishRsiCount++;
        if (indicators.getRsi_15min_gt_56() != null && indicators.getRsi_15min_gt_56()) bullishRsiCount++;
        
        // Require at least 2 bullish EMAs and 1 bullish RSI for momentum
        boolean hasEmaMomentum = bullishEmaCount >= 2;
        boolean hasRsiMomentum = bullishRsiCount >= 1;
        
        return hasEmaMomentum && hasRsiMomentum;
    }
    
    // REMOVED: Old volume surge tracking - replaced with natural tracking
    
    // REMOVED: Forced entry signal cooldown - replaced with natural validation
    
    /**
     * Validate strong momentum for PUT entry (scalping perspective)
     * Uses configuration to determine which timeframes to check
     */
    private boolean validateStrongMomentumForPut(FlattenedIndicators indicators) {
        // Require at least 2 timeframes showing bearish EMA crossover
        int bearishTimeframes = 0;
        if (indicators.getEma9_1min_gt_ema21_1min() != null && !indicators.getEma9_1min_gt_ema21_1min()) bearishTimeframes++;
        if (indicators.getEma9_5min_gt_ema21_5min() != null && !indicators.getEma9_5min_gt_ema21_5min()) bearishTimeframes++;
        if (indicators.getEma9_15min_gt_ema21_15min() != null && !indicators.getEma9_15min_gt_ema21_15min()) bearishTimeframes++;
        
        // Require strong RSI momentum based on configuration
        int strongRsiTimeframes = 0;
        if (configService.isPutCheck1Min() && indicators.getRsi_1min_lt_44() != null && indicators.getRsi_1min_lt_44()) strongRsiTimeframes++;
        if (configService.isPutCheck5Min() && indicators.getRsi_5min_lt_44() != null && indicators.getRsi_5min_lt_44()) strongRsiTimeframes++;
        if (configService.isPutCheck15Min() && indicators.getRsi_15min_lt_44() != null && indicators.getRsi_15min_lt_44()) strongRsiTimeframes++;
        
        // Use configuration for requirements
        return bearishTimeframes >= 2 && strongRsiTimeframes >= 1;
    }
    
    /**
     * Validate strong futuresignals for PUT entry (scalping perspective)
     * Requires majority of timeframes to show bearish futuresignals
     */
    private boolean validateStrongFuturesignalsForPut(FlattenedIndicators indicators) {
        if (indicators.getFuturesignals() == null) {
            return false;
        }
        
        try {
            // Load futuresignals configuration
            ScalpingEntryLogic entryLogic = scalpingEntryService.loadEntryLogic("rules/scalping-entry-config.json");
            ScalpingEntryLogic.FuturesignalsConfig config = entryLogic.getFuturesignalsConfig();
            
            if (config == null || config.getPutStrategy() == null) {
                // Fallback to default behavior if config not found
                int bearishTimeframes = 0;
                if (indicators.getFuturesignals().getOneMinBearishSurge() != null && indicators.getFuturesignals().getOneMinBearishSurge()) bearishTimeframes++;
                if (indicators.getFuturesignals().getFiveMinBearishSurge() != null && indicators.getFuturesignals().getFiveMinBearishSurge()) bearishTimeframes++;
                if (indicators.getFuturesignals().getFifteenMinBearishSurge() != null && indicators.getFuturesignals().getFifteenMinBearishSurge()) bearishTimeframes++;
                return bearishTimeframes >= 2;
            }
            
            // Count bearish timeframes based on enabled timeframes
            int bearishTimeframes = 0;
            int totalTimeframes = 0;
            
            if (config.getEnabledTimeframes().contains("1min")) {
                totalTimeframes++;
                if (indicators.getFuturesignals().getOneMinBearishSurge() != null && 
                    indicators.getFuturesignals().getOneMinBearishSurge()) {
                    bearishTimeframes++;
                }
            }
            
            if (config.getEnabledTimeframes().contains("5min")) {
                totalTimeframes++;
                if (indicators.getFuturesignals().getFiveMinBearishSurge() != null && 
                    indicators.getFuturesignals().getFiveMinBearishSurge()) {
                    bearishTimeframes++;
                }
            }
            
            // Check if 15min is enabled (should be false based on your requirement)
            if (config.getEnabledTimeframes().contains("15min")) {
                totalTimeframes++;
                if (indicators.getFuturesignals().getFifteenMinBearishSurge() != null && 
                    indicators.getFuturesignals().getFifteenMinBearishSurge()) {
                    bearishTimeframes++;
                }
            }
            
            // Determine if validation passes
            boolean isValid;
            if (config.getPutStrategy().isRequireAllTimeframes()) {
                isValid = bearishTimeframes == totalTimeframes;
            } else {
                isValid = bearishTimeframes >= config.getPutStrategy().getMinBearishTimeframes();
            }
            
            // Dynamic debug logging - only show enabled timeframes
            if (!isValid) {
                String timeframeLog = buildFuturesignalsDebugLog(indicators, config.getEnabledTimeframes(), "PUT");
                log.debug("🔍 PUT FUTURESIGNALS DEBUG - {} | Bearish: {}/{} | Required: {} | Valid: {}", 
                    timeframeLog,
                    bearishTimeframes, totalTimeframes,
                    config.getPutStrategy().getMinBearishTimeframes(),
                    isValid);
            } else {
                // Also log when validation passes to see what's happening
                String timeframeLog = buildFuturesignalsDebugLog(indicators, config.getEnabledTimeframes(), "PUT");
                log.debug("✅ PUT FUTURESIGNALS PASSED - {} | Bearish: {}/{} | Required: {} | Valid: {}", 
                    timeframeLog,
                    bearishTimeframes, totalTimeframes,
                    config.getPutStrategy().getMinBearishTimeframes(),
                    isValid);
            }
            
            return isValid;
            
        } catch (Exception e) {
            log.error("Error validating PUT futuresignals", e);
            // Fallback to default behavior
            int bearishTimeframes = 0;
            if (indicators.getFuturesignals().getOneMinBearishSurge() != null && indicators.getFuturesignals().getOneMinBearishSurge()) bearishTimeframes++;
            if (indicators.getFuturesignals().getFiveMinBearishSurge() != null && indicators.getFuturesignals().getFiveMinBearishSurge()) bearishTimeframes++;
            if (indicators.getFuturesignals().getFifteenMinBearishSurge() != null && indicators.getFuturesignals().getFifteenMinBearishSurge()) bearishTimeframes++;
            return bearishTimeframes >= 2;
        }
    }
    
    /**
     * Validate strong volume surge for PUT entry (scalping perspective)
     * Uses natural, intelligent validation instead of forced cooldowns
     */
    private boolean validateStrongVolumeSurgeForPut(FlattenedIndicators indicators) {
        // 🔥 NATURAL APPROACH: Intelligent validation without forced cooldowns for PUT
        String instrumentToken = indicators.getInstrumentToken();
        long currentTime = System.currentTimeMillis();
        
        // Require volume surge in at least 1 timeframe
        int volumeSurgeTimeframes = 0;
        if (indicators.getVolume_1min_surge() != null && indicators.getVolume_1min_surge()) volumeSurgeTimeframes++;
        if (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) volumeSurgeTimeframes++;
        if (indicators.getVolume_15min_surge() != null && indicators.getVolume_15min_surge()) volumeSurgeTimeframes++;
        
        // Require strong volume multiplier (at least 2.5x for quality)
        boolean hasStrongVolumeMultiplier = indicators.getVolume_surge_multiplier() != null && 
                                           indicators.getVolume_surge_multiplier() >= 2.5;
        
        // 🔥 NATURAL: Enhanced volume surge validation with intelligent quality checks for PUT
        boolean hasVolumeSurge = volumeSurgeTimeframes >= 1 && hasStrongVolumeMultiplier;
        
        if (hasVolumeSurge) {
            // Check if this is a high-quality volume surge (natural validation)
            boolean isHighQualitySurge = validateVolumeSurgeQualityNatural(instrumentToken, indicators, currentTime);
            
            // Check if volume surge is accompanied by bearish price momentum (natural validation)
            boolean hasBearishMomentum = validateBearishPriceMomentum(instrumentToken, indicators);
            
            // Check if market conditions support this bearish signal (natural validation)
            boolean hasSupportiveMarketConditions = validateBearishMarketConditionsNatural(instrumentToken, indicators);
            
            // Update volume surge tracking for learning
            updateVolumeSurgeTrackingNatural(instrumentToken, indicators, currentTime);
            
            // Only return true if all natural validations pass
            boolean isValidSurge = isHighQualitySurge && hasBearishMomentum && hasSupportiveMarketConditions;
            
            // Bearish volume surge validation complete
            
            return isValidSurge;
        }
        
        return false;
    }
    
    /**
     * 🔥 NEW: Natural bearish market conditions validation
     */
    private boolean validateBearishMarketConditionsNatural(String instrumentToken, FlattenedIndicators indicators) {
        // Check if multiple timeframes are aligned for bearish movement (natural market condition)
        int bearishAlignedTimeframes = 0;
        
        // Check EMA alignment across timeframes (bearish)
        if (indicators.getEma9_1min_gt_ema21_1min() != null && !indicators.getEma9_1min_gt_ema21_1min()) bearishAlignedTimeframes++;
        if (indicators.getEma9_5min_gt_ema21_5min() != null && !indicators.getEma9_5min_gt_ema21_5min()) bearishAlignedTimeframes++;
        if (indicators.getEma9_15min_gt_ema21_15min() != null && !indicators.getEma9_15min_gt_ema21_15min()) bearishAlignedTimeframes++;
        
        // Check RSI alignment across timeframes (bearish)
        int bearishRsiAlignedTimeframes = 0;
        if (indicators.getRsi_1min_lt_44() != null && indicators.getRsi_1min_lt_44()) bearishRsiAlignedTimeframes++;
        if (indicators.getRsi_5min_lt_44() != null && indicators.getRsi_5min_lt_44()) bearishRsiAlignedTimeframes++;
        if (indicators.getRsi_15min_lt_44() != null && indicators.getRsi_15min_lt_44()) bearishRsiAlignedTimeframes++;
        
        // 🔥 TEMPORARY: Relaxed market conditions for testing
        // Natural market condition: At least 1 timeframe aligned for bearish movement (relaxed from 2)
        boolean hasBearishTimeframeAlignment = bearishAlignedTimeframes >= 1; // Relaxed from 2 to 1
        boolean hasBearishRsiAlignment = bearishRsiAlignedTimeframes >= 1; // Relaxed from 2 to 1
        
        // Check for bearish price action confirmation
        boolean hasBearishPriceActionConfirmation = indicators.getPrice_lt_vwap_5min() != null && 
                                                  indicators.getPrice_lt_vwap_5min();
        
        // Natural bearish market conditions require alignment and confirmation (relaxed)
        boolean hasMarketConditions = hasBearishTimeframeAlignment && (hasBearishRsiAlignment || hasBearishPriceActionConfirmation);
        

        
        return hasMarketConditions;
    }
    
    /**
     *  NEW: Validate bearish price momentum for PUT entries
     */
    private boolean validateBearishPriceMomentum(String instrumentToken, FlattenedIndicators indicators) {
        // Check EMA momentum (price below EMAs indicates downward momentum)
        int bearishEmaCount = 0;
        if (indicators.getEma9_1min_gt_ema21_1min() != null && !indicators.getEma9_1min_gt_ema21_1min()) bearishEmaCount++;
        if (indicators.getEma9_5min_gt_ema21_5min() != null && !indicators.getEma9_5min_gt_ema21_5min()) bearishEmaCount++;
        if (indicators.getEma9_15min_gt_ema21_15min() != null && !indicators.getEma9_15min_gt_ema21_15min()) bearishEmaCount++;
        
        // Check RSI momentum (RSI below 44 indicates bearish momentum)
        int bearishRsiCount = 0;
        if (indicators.getRsi_1min_lt_44() != null && indicators.getRsi_1min_lt_44()) bearishRsiCount++;
        if (indicators.getRsi_5min_lt_44() != null && indicators.getRsi_5min_lt_44()) bearishRsiCount++;
        if (indicators.getRsi_15min_lt_44() != null && indicators.getRsi_15min_lt_44()) bearishRsiCount++;
        
        // 🔥 TEMPORARY: Relaxed momentum requirements for testing
        // Require at least 1 bearish EMA and 1 bearish RSI for momentum (relaxed from 2+1)
        boolean hasEmaMomentum = bearishEmaCount >= 1; // Relaxed from 2 to 1
        boolean hasRsiMomentum = bearishRsiCount >= 1;
        
        boolean hasMomentum = hasEmaMomentum && hasRsiMomentum;
        

        
        return hasMomentum;
    }
    
    /**
     * 🔥 NEW: Natural signal tracking for learning and adaptation
     */
    private void updateSignalTrackingNatural(String instrumentToken, boolean isSuccessful) {
        // Track signal success/failure for natural learning
        if (isSuccessful) {
            Integer successful = successfulSignals.get(instrumentToken);
            successful = (successful == null) ? 1 : successful + 1;
            successfulSignals.put(instrumentToken, successful);
        } else {
            Integer failed = failedSignals.get(instrumentToken);
            failed = (failed == null) ? 1 : failed + 1;
            failedSignals.put(instrumentToken, failed);
        }
        
        // Calculate success rate for natural adaptation
        Integer totalSuccessful = successfulSignals.get(instrumentToken);
        Integer totalFailed = failedSignals.get(instrumentToken);
        
        if (totalSuccessful != null && totalFailed != null) {
            double successRate = (double) totalSuccessful / (totalSuccessful + totalFailed);
            signalSuccessRate.put(instrumentToken, successRate);
        }
        
        log.debug("Natural signal tracking updated - Instrument: {}, Success: {}, Failed: {}, Success Rate: {}", 
                instrumentToken, totalSuccessful, totalFailed, signalSuccessRate.get(instrumentToken));
    }
    
    /**
     * Helper method to build dynamic futuresignals debug log
     */
    private String buildFuturesignalsDebugLog(FlattenedIndicators indicators, List<String> enabledTimeframes, String strategyType) {
        StringBuilder timeframeLog = new StringBuilder();
        
        if (enabledTimeframes.contains("1min")) {
            if ("CALL".equals(strategyType)) {
                timeframeLog.append(String.format("1min:%s ", 
                    indicators.getFuturesignals().getOneMinBullishSurge() != null ? 
                    indicators.getFuturesignals().getOneMinBullishSurge() : "null"));
            } else {
                timeframeLog.append(String.format("1min:%s ", 
                    indicators.getFuturesignals().getOneMinBearishSurge() != null ? 
                    indicators.getFuturesignals().getOneMinBearishSurge() : "null"));
            }
        }
        
        if (enabledTimeframes.contains("5min")) {
            if ("CALL".equals(strategyType)) {
                timeframeLog.append(String.format("5min:%s ", 
                    indicators.getFuturesignals().getFiveMinBullishSurge() != null ? 
                    indicators.getFuturesignals().getFiveMinBullishSurge() : "null"));
            } else {
                timeframeLog.append(String.format("5min:%s ", 
                    indicators.getFuturesignals().getFiveMinBearishSurge() != null ? 
                    indicators.getFuturesignals().getFiveMinBearishSurge() : "null"));
            }
        }
        
        if (enabledTimeframes.contains("15min")) {
            if ("CALL".equals(strategyType)) {
                timeframeLog.append(String.format("15min:%s ", 
                    indicators.getFuturesignals().getFifteenMinBullishSurge() != null ? 
                    indicators.getFuturesignals().getFifteenMinBullishSurge() : "null"));
            } else {
                timeframeLog.append(String.format("15min:%s ", 
                    indicators.getFuturesignals().getFifteenMinBearishSurge() != null ? 
                    indicators.getFuturesignals().getFifteenMinBearishSurge() : "null"));
            }
        }
        
        return timeframeLog.toString().trim();
    }
}
