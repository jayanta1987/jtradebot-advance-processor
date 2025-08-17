package com.jtradebot.processor.service.impl;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.config.ScoringConfigurationService;
import com.jtradebot.processor.indicator.MultiEmaIndicator;
import com.jtradebot.processor.indicator.PriceVolumeSurgeIndicator;
import com.jtradebot.processor.indicator.RsiIndicator;
import com.jtradebot.processor.indicator.SupportResistanceIndicator;
import com.jtradebot.processor.indicator.VWAPIndicator;
import com.jtradebot.processor.candleStick.CandlestickPattern;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.model.indicator.EmaIndicatorInfo;
import com.jtradebot.processor.model.indicator.EmaInfo;
import com.jtradebot.processor.model.indicator.EntryQuality;
import com.jtradebot.processor.model.indicator.Resistance;
import com.jtradebot.processor.model.indicator.Support;
import java.util.Set;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.indicator.FuturesignalData;

import com.jtradebot.processor.model.strategy.ScalpingVolumeSurgeCallRule;
import com.jtradebot.processor.model.strategy.ScalpingVolumeSurgePutRule;
import com.jtradebot.processor.model.strategy.StrategyScore;
import com.jtradebot.processor.model.strategy.ScalpingEntryDecision;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.*;



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
    private final ScoringConfigurationService scoringConfigService;
    
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
            String instrumentToken = String.valueOf(tick.getInstrumentToken());
            
            // Get flattened indicators
            FlattenedIndicators indicators = getFlattenedIndicators(tick);
            
            // Use new scenario-based entry evaluation for CALL direction
            ScalpingEntryDecision decision = scalpingEntryService.evaluateEntry(tick, indicators);
            
            // Only trigger CALL if scenario passes AND market conditions are bullish
            boolean isBullish = isMarketConditionBullish(indicators);
            boolean shouldEntry = decision.isShouldEntry() && isBullish;
            
            if (shouldEntry) {
                log.debug("🚀 CALL ENTRY SIGNAL - Instrument: {}, Price: {}, Scenario: {}, Confidence: {}/10, Bullish: {}, Time: {}", 
                    tick.getInstrumentToken(), tick.getLastTradedPrice(), decision.getScenarioName(), 
                    decision.getConfidence(), isBullish, tick.getTickTimestamp());
            } else {
                log.debug("🔍 CALL ENTRY BLOCKED - Instrument: {}, Scenario: {}, Bullish: {}, Reason: {}", 
                    tick.getInstrumentToken(), decision.getScenarioName(), isBullish, decision.getReason());
            }
            
            return shouldEntry;
            
        } catch (Exception e) {
            log.error("Error evaluating CALL entry for tick: {}", tick.getInstrumentToken(), e);
            return false;
        }
    }
    
    /**
     * Determine if market conditions are bullish for CALL entries
     */
    private boolean isMarketConditionBullish(FlattenedIndicators indicators) {
        if (indicators == null) return false;
        
        int bullishSignals = 0;
        int totalSignals = 0;
        
        // EMA conditions (bullish when EMA9 > EMA21)
        if (Boolean.TRUE.equals(indicators.getEma5_5min_gt_ema34_5min())) bullishSignals++;
        if (Boolean.TRUE.equals(indicators.getEma5_1min_gt_ema34_1min())) bullishSignals++;
        if (Boolean.TRUE.equals(indicators.getEma5_15min_gt_ema34_15min())) bullishSignals++;
        totalSignals += 3;
        
        // Price action conditions (bullish when price > VWAP)
        if (Boolean.TRUE.equals(indicators.getPrice_gt_vwap_5min())) bullishSignals++;
        if (Boolean.TRUE.equals(indicators.getPrice_gt_vwap_1min())) bullishSignals++;
        if (Boolean.TRUE.equals(indicators.getPrice_gt_vwap_15min())) bullishSignals++;
        totalSignals += 3;
        
        // RSI conditions (bullish when RSI > 56)
        if (Boolean.TRUE.equals(indicators.getRsi_5min_gt_56())) bullishSignals++;
        if (Boolean.TRUE.equals(indicators.getRsi_1min_gt_56())) bullishSignals++;
        if (Boolean.TRUE.equals(indicators.getRsi_15min_gt_56())) bullishSignals++;
        totalSignals += 3;
        
        // Candlestick conditions (bullish patterns)
        if (Boolean.TRUE.equals(indicators.getGreen_candle_5min())) bullishSignals++;
        if (Boolean.TRUE.equals(indicators.getGreen_candle_1min())) bullishSignals++;
        totalSignals += 2;
        
        // Require at least 60% of signals to be bullish
        return totalSignals > 0 && (double) bullishSignals / totalSignals >= 0.6;
    }
    
    /**
     * Determine if market conditions are bearish for PUT entries
     */
    private boolean isMarketConditionBearish(FlattenedIndicators indicators) {
        if (indicators == null) return false;
        
        int bearishSignals = 0;
        int totalSignals = 0;
        
        // EMA conditions (bearish when EMA9 < EMA21)
        if (Boolean.TRUE.equals(indicators.getEma5_5min_lt_ema34_5min())) bearishSignals++;
        if (Boolean.TRUE.equals(indicators.getEma5_1min_lt_ema34_1min())) bearishSignals++;
        if (Boolean.TRUE.equals(indicators.getEma5_15min_lt_ema34_15min())) bearishSignals++;
        totalSignals += 3;
        
        // Price action conditions (bearish when price < VWAP)
        if (Boolean.TRUE.equals(indicators.getPrice_lt_vwap_5min())) bearishSignals++;
        if (Boolean.TRUE.equals(indicators.getPrice_lt_vwap_1min())) bearishSignals++;
        if (Boolean.TRUE.equals(indicators.getPrice_lt_vwap_15min())) bearishSignals++;
        totalSignals += 3;
        
        // RSI conditions (bearish when RSI < 44)
        if (Boolean.TRUE.equals(indicators.getRsi_5min_lt_44())) bearishSignals++;
        if (Boolean.TRUE.equals(indicators.getRsi_1min_lt_44())) bearishSignals++;
        if (Boolean.TRUE.equals(indicators.getRsi_15min_lt_44())) bearishSignals++;
        totalSignals += 3;
        
        // Candlestick conditions (bearish patterns)
        if (Boolean.TRUE.equals(indicators.getRed_candle_5min())) bearishSignals++;
        if (Boolean.TRUE.equals(indicators.getRed_candle_1min())) bearishSignals++;
        totalSignals += 2;
        
        // Require at least 60% of signals to be bearish
        return totalSignals > 0 && (double) bearishSignals / totalSignals >= 0.6;
    }
    
    @Override
    public boolean shouldMakePutEntry(Tick tick) {
        try {
            String instrumentToken = String.valueOf(tick.getInstrumentToken());
            
            // Get flattened indicators
            FlattenedIndicators indicators = getFlattenedIndicators(tick);
            
            // Use new scenario-based entry evaluation for PUT direction
            ScalpingEntryDecision decision = scalpingEntryService.evaluateEntry(tick, indicators);
            
            // Only trigger PUT if scenario passes AND market conditions are bearish
            boolean isBearish = isMarketConditionBearish(indicators);
            boolean shouldEntry = decision.isShouldEntry() && isBearish;
            
            if (shouldEntry) {
                log.debug("📉 PUT ENTRY SIGNAL - Instrument: {}, Price: {}, Scenario: {}, Confidence: {}/10, Bearish: {}, Time: {}", 
                    tick.getInstrumentToken(), tick.getLastTradedPrice(), decision.getScenarioName(), 
                    decision.getConfidence(), isBearish, tick.getTickTimestamp());
            } else {
                log.debug("🔍 PUT ENTRY BLOCKED - Instrument: {}, Scenario: {}, Bearish: {}, Reason: {}", 
                    tick.getInstrumentToken(), decision.getScenarioName(), isBearish, decision.getReason());
            }
            
            return shouldEntry;
            
        } catch (Exception e) {
            log.error("Error evaluating PUT entry for tick: {}", tick.getInstrumentToken(), e);
            return false;
        }
    }
    
    @Override
    public ScalpingEntryDecision getEntryDecision(Tick tick) {
        try {
            String instrumentToken = String.valueOf(tick.getInstrumentToken());
            
            // Get flattened indicators
            FlattenedIndicators indicators = getFlattenedIndicators(tick);
            
            // Use new scenario-based entry evaluation
            ScalpingEntryDecision decision = scalpingEntryService.evaluateEntry(tick, indicators);
            
            if (decision.isShouldEntry()) {
                log.debug("🎯 ENTRY DECISION - Instrument: {}, Price: {}, Scenario: {}, Confidence: {}/10, Time: {}", 
                    tick.getInstrumentToken(), tick.getLastTradedPrice(), decision.getScenarioName(), 
                    decision.getConfidence(), tick.getTickTimestamp());
            } else {
                log.debug("🔍 ENTRY BLOCKED - Instrument: {}, Reason: {}", 
                    tick.getInstrumentToken(), decision.getReason());
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
    
    @Override
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
            
            // Skip volume indicators for index tokens (no volume data) - will be calculated with future data later
            // flattenVolumeIndicators(indicators, oneMinSeries, fiveMinSeries, fifteenMinSeries, indexTick);
            
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
                log.warn("⚠️ No future tick available for volume enhancement - Index Token: {}, Expected Future Token: {}", 
                    indexTick.getInstrumentToken(), niftyFutureToken);
            }
            
            // Cache the result
            indicatorsCache.put(instrumentToken, indicators);
            cacheTimestamps.put(instrumentToken, currentTime);
            
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
            
            // Calculate confidence based on how many conditions are met - with null checks
            if (indicators.getEma5_5min_gt_ema34_5min() != null && indicators.getEma5_5min_gt_ema34_5min()) confidence += scoringConfigService.getEmaConfidence();
            if (indicators.getRsi_5min_gt_70() != null && indicators.getRsi_5min_gt_70()) confidence += scoringConfigService.getRsiConfidence();
            if (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) confidence += scoringConfigService.getVolumeConfidence();
            if (indicators.getPrice_gt_vwap_5min() != null && indicators.getPrice_gt_vwap_5min()) confidence += scoringConfigService.getPriceActionConfidence();
            if (indicators.getPrice_above_resistance() != null && indicators.getPrice_above_resistance()) confidence += scoringConfigService.getResistanceConfidence();
            
            return Math.min(confidence, scoringConfigService.getMaxConfidence());
            
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
                               (momentumScore * scoringConfigService.getMomentumWeight()); // Additional momentum weight
            
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
    
    /**
     * Calculate EMA crossover score (-3 to +3)
     */
    private double calculateEmaScore(FlattenedIndicators indicators) {
        double score = 0.0;
        
        // Positive points for bullish EMA crossovers (with null checks)
        if (indicators.getEma5_5min_gt_ema34_5min() != null && indicators.getEma5_5min_gt_ema34_5min()) score += scoringConfigService.getEmaBullishScore();
        if (indicators.getEma5_1min_gt_ema34_1min() != null && indicators.getEma5_1min_gt_ema34_1min()) score += scoringConfigService.getEmaBullishScore();
        
        // Negative points for bearish EMA crossovers (only if we have data)
        if (indicators.getEma5_5min_gt_ema34_5min() != null && !indicators.getEma5_5min_gt_ema34_5min()) score += scoringConfigService.getEmaBearishScore();
        if (indicators.getEma5_1min_gt_ema34_1min() != null && !indicators.getEma5_1min_gt_ema34_1min()) score += scoringConfigService.getEmaBearishScore();
        
        return Math.max(scoringConfigService.getEmaMinScore(), Math.min(scoringConfigService.getEmaMaxScore(), score));
    }
    
    /**
     * Calculate RSI score (-3 to +3)
     */
    private double calculateRsiScore(FlattenedIndicators indicators) {
        double score = 0.0;
        
        // Positive points for bullish RSI conditions (RSI > 56) - with null checks
        if (indicators.getRsi_5min_gt_56() != null && indicators.getRsi_5min_gt_56()) score += scoringConfigService.getRsiBullishScore();
        if (indicators.getRsi_1min_gt_56() != null && indicators.getRsi_1min_gt_56()) score += scoringConfigService.getRsiBullishScore();
        
        // Negative points for bearish RSI conditions (RSI < 44) - with null checks
        if (indicators.getRsi_5min_lt_44() != null && indicators.getRsi_5min_lt_44()) score += scoringConfigService.getRsiBearishScore();
        if (indicators.getRsi_1min_lt_44() != null && indicators.getRsi_1min_lt_44()) score += scoringConfigService.getRsiBearishScore();
        
        return Math.max(scoringConfigService.getRsiMinScore(), Math.min(scoringConfigService.getRsiMaxScore(), score));
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
            if (indicators.getEma5_5min_gt_ema34_5min() != null && indicators.getEma5_1min_gt_ema34_1min() != null) {
                isBullishVolume = indicators.getEma5_5min_gt_ema34_5min() && indicators.getEma5_1min_gt_ema34_1min();
                isBearishVolume = !indicators.getEma5_5min_gt_ema34_5min() && !indicators.getEma5_1min_gt_ema34_1min();
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
            if (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) volumePoints += scoringConfigService.getVolume5minPoints();
            if (indicators.getVolume_1min_surge() != null && indicators.getVolume_1min_surge()) volumePoints += scoringConfigService.getVolume1minPoints();
            
            // Enhanced volume multiplier bonus (relaxed thresholds)
            if (indicators.getVolume_surge_multiplier() != null) {
                if (indicators.getVolume_surge_multiplier() >= scoringConfigService.getVeryHighVolumeMultiplier()) {
                    volumePoints += scoringConfigService.getScoringConfig().getVolumeScoring().getVolumeMultiplierThresholds().getVeryHigh().getBonus();
                } else if (indicators.getVolume_surge_multiplier() >= scoringConfigService.getHighVolumeMultiplier()) {
                    volumePoints += scoringConfigService.getScoringConfig().getVolumeScoring().getVolumeMultiplierThresholds().getHigh().getBonus();
                } else if (indicators.getVolume_surge_multiplier() >= scoringConfigService.getSurgeMultiplier()) {
                    volumePoints += scoringConfigService.getScoringConfig().getVolumeScoring().getVolumeMultiplierThresholds().getStandard().getBonus();
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
        
        return Math.max(scoringConfigService.getVolumeMinScore(), Math.min(scoringConfigService.getVolumeMaxScore(), score));
    }
    
    /**
     * Calculate price action score (-5 to +5) - Enhanced with breakout strength
     */
    private double calculatePriceActionScore(FlattenedIndicators indicators) {
        double score = 0.0;
        
        // Base VWAP points - with null checks
        if (indicators.getPrice_gt_vwap_5min() != null && indicators.getPrice_gt_vwap_5min()) score += scoringConfigService.getVwapBullishScore();
        if (indicators.getPrice_lt_vwap_5min() != null && indicators.getPrice_lt_vwap_5min()) score += scoringConfigService.getVwapBearishScore();
        
        // Support/Resistance points - with null checks
        if (indicators.getPrice_above_resistance() != null && indicators.getPrice_above_resistance()) score += scoringConfigService.getResistanceScore();
        if (indicators.getPrice_below_support() != null && indicators.getPrice_below_support()) score += scoringConfigService.getSupportScore();
        
        // Enhanced breakout strength points
        if (indicators.getBreakoutStrength() != null && indicators.getBreakoutStrength() > 0) {
            // Strong breakout: >2% above resistance
            if (indicators.getBreakoutStrength() > scoringConfigService.getScoringConfig().getPriceActionScoring().getBreakoutStrength().getStrongBreakout().getThreshold()) {
                score += scoringConfigService.getScoringConfig().getPriceActionScoring().getBreakoutStrength().getStrongBreakout().getBonus();
            } else {
                score += scoringConfigService.getScoringConfig().getPriceActionScoring().getBreakoutStrength().getStandardBreakout().getBonus();
            }
        }
        
        if (indicators.getBreakdownStrength() != null && indicators.getBreakdownStrength() > 0) {
            // Strong breakdown: >2% below support
            if (indicators.getBreakdownStrength() > scoringConfigService.getScoringConfig().getPriceActionScoring().getBreakdownStrength().getStrongBreakdown().getThreshold()) {
                score += scoringConfigService.getScoringConfig().getPriceActionScoring().getBreakdownStrength().getStrongBreakdown().getBonus();
            } else {
                score += scoringConfigService.getScoringConfig().getPriceActionScoring().getBreakdownStrength().getStandardBreakdown().getBonus();
            }
        }
        
        return Math.max(scoringConfigService.getPriceActionMinScore(), Math.min(scoringConfigService.getPriceActionMaxScore(), score));
    }
    
    /**
     * Calculate futuresignal score (-2 to +2)
     */
    private double calculateFuturesignalScore(FlattenedIndicators indicators) {
        double score = 0.0;
        
        if (indicators.getFuturesignals() != null) {
            // Positive points for bullish futuresignals
            if (indicators.getFuturesignals().getAllTimeframesBullish()) {
                score += scoringConfigService.getFuturesignalBullishScore();
            }
            // Negative points for bearish futuresignals
            else if (indicators.getFuturesignals().getAllTimeframesBearish()) {
                score += scoringConfigService.getFuturesignalBearishScore();
            }
        }
        
        return Math.max(scoringConfigService.getFuturesignalMinScore(), Math.min(scoringConfigService.getFuturesignalMaxScore(), score));
    }
    
    private void flattenEmaIndicators(FlattenedIndicators indicators, BarSeries oneMinSeries, BarSeries fiveMinSeries, BarSeries fifteenMinSeries) {
        try {
            // Calculate actual EMA values using MultiEmaIndicator
            MultiEmaIndicator emaIndicator = new MultiEmaIndicator();
            
            // 1-minute EMA calculation
            if (oneMinSeries != null && oneMinSeries.getBarCount() >= 21) {
                try {
                    EmaInfo emaInfo_1min = multiEmaIndicator.calculateEmaValues(oneMinSeries, ONE_MIN);
                    double ema5_1min = emaInfo_1min.getEma5();
                    double ema34_1min = emaInfo_1min.getEma34();
                    
                    boolean gtResult = ema5_1min > ema34_1min;
                    boolean ltResult = ema5_1min < ema34_1min;
                    indicators.setEma5_1min_gt_ema34_1min(gtResult);
                    indicators.setEma5_1min_lt_ema34_1min(ltResult);
                    

                } catch (Exception e) {
                    log.error("Error calculating 1min EMA", e);
                    indicators.setEma5_1min_gt_ema34_1min(null);
                    indicators.setEma5_1min_lt_ema34_1min(null);
                }
            } else {
                log.warn("1min BarSeries insufficient data - BarCount: {}", oneMinSeries != null ? oneMinSeries.getBarCount() : 0);
                indicators.setEma5_1min_gt_ema34_1min(null);
                indicators.setEma5_1min_lt_ema34_1min(null);
            }
            
            // 5-minute EMA calculation
            if (fiveMinSeries != null && fiveMinSeries.getBarCount() >= 21) {
                try {
                    EmaInfo emaInfo_5min = multiEmaIndicator.calculateEmaValues(fiveMinSeries, FIVE_MIN);
                    double ema5_5min = emaInfo_5min.getEma5();
                    double ema34_5min = emaInfo_5min.getEma34();
                    
                    boolean gtResult = ema5_5min > ema34_5min;
                    boolean ltResult = ema5_5min < ema34_5min;
                    indicators.setEma5_5min_gt_ema34_5min(gtResult);
                    indicators.setEma5_5min_lt_ema34_5min(ltResult);
                    

                } catch (Exception e) {
                    log.error("Error calculating 5min EMA", e);
                    indicators.setEma5_5min_gt_ema34_5min(null);
                    indicators.setEma5_5min_lt_ema34_5min(null);
                }
            } else {
                log.warn("5min BarSeries insufficient data - BarCount: {}", fiveMinSeries != null ? fiveMinSeries.getBarCount() : 0);
                indicators.setEma5_5min_gt_ema34_5min(null);
                indicators.setEma5_5min_lt_ema34_5min(null);
            }
            
            // 15-minute EMA calculation
            if (fifteenMinSeries != null && fifteenMinSeries.getBarCount() >= 21) {
                try {
                    EmaInfo emaInfo_15min = multiEmaIndicator.calculateEmaValues(fifteenMinSeries, FIFTEEN_MIN);
                    double ema5_15min = emaInfo_15min.getEma5();
                    double ema34_15min = emaInfo_15min.getEma34();
                    
                    boolean gtResult = ema5_15min > ema34_15min;
                    boolean ltResult = ema5_15min < ema34_15min;
                    indicators.setEma5_15min_gt_ema34_15min(gtResult);
                    indicators.setEma5_15min_lt_ema34_15min(ltResult);
                    

                } catch (Exception e) {
                    log.error("Error calculating 15min EMA", e);
                    indicators.setEma5_15min_gt_ema34_15min(null);
                    indicators.setEma5_15min_lt_ema34_15min(null);
                }
            } else {
                log.warn("15min BarSeries insufficient data - BarCount: {}", fifteenMinSeries != null ? fifteenMinSeries.getBarCount() : 0);
                indicators.setEma5_15min_gt_ema34_15min(null);
                indicators.setEma5_15min_lt_ema34_15min(null);
            }
            

            
        } catch (Exception e) {
            log.error("Error flattening EMA indicators", e);
            // Set to null on error to indicate no data
            indicators.setEma5_1min_gt_ema34_1min(null);
            indicators.setEma5_1min_lt_ema34_1min(null);
            indicators.setEma5_5min_gt_ema34_5min(null);
            indicators.setEma5_5min_lt_ema34_5min(null);
            indicators.setEma5_15min_gt_ema34_15min(null);
            indicators.setEma5_15min_lt_ema34_15min(null);
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
                                indicators.setRsi_1min_gt_70(rsi_1min > scoringConfigService.getOverboughtRsi());
            indicators.setRsi_1min_lt_30(rsi_1min < scoringConfigService.getOversoldRsi());
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
                                indicators.setRsi_5min_gt_70(rsi_5min > scoringConfigService.getOverboughtRsi());
            indicators.setRsi_5min_lt_30(rsi_5min < scoringConfigService.getOversoldRsi());
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
                                indicators.setRsi_15min_gt_70(rsi_15min > scoringConfigService.getOverboughtRsi());
            indicators.setRsi_15min_lt_30(rsi_15min < scoringConfigService.getOversoldRsi());
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
        boolean oneMinBullish = indicators.getEma5_1min_gt_ema34_1min() != null && 
                               indicators.getEma5_1min_gt_ema34_1min() && 
                               indicators.getRsi_1min_gt_56() != null && 
                               indicators.getRsi_1min_gt_56();
        
        boolean fiveMinBullish = indicators.getEma5_5min_gt_ema34_5min() != null && 
                                indicators.getEma5_5min_gt_ema34_5min() && 
                                indicators.getRsi_5min_gt_56() != null && 
                                indicators.getRsi_5min_gt_56();
        
        boolean fifteenMinBullish = indicators.getEma5_15min_gt_ema34_15min() != null && 
                                   indicators.getEma5_15min_gt_ema34_15min() && 
                                   indicators.getRsi_15min_gt_56() != null && 
                                   indicators.getRsi_15min_gt_56();
        
        boolean oneMinBearish = indicators.getEma5_1min_gt_ema34_1min() != null && 
                               !indicators.getEma5_1min_gt_ema34_1min() && 
                               indicators.getRsi_1min_lt_44() != null && 
                               indicators.getRsi_1min_lt_44();
        
        boolean fiveMinBearish = indicators.getEma5_5min_gt_ema34_5min() != null && 
                                !indicators.getEma5_5min_gt_ema34_5min() && 
                                indicators.getRsi_5min_lt_44() != null && 
                                indicators.getRsi_5min_lt_44();
        
        boolean fifteenMinBearish = indicators.getEma5_15min_gt_ema34_15min() != null && 
                                   !indicators.getEma5_15min_gt_ema34_15min() && 
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
        if (indicators.getEma5_1min_gt_ema34_1min() != null && indicators.getEma5_1min_gt_ema34_1min()) bullishTimeframes++;
        if (indicators.getEma5_5min_gt_ema34_5min() != null && indicators.getEma5_5min_gt_ema34_5min()) bullishTimeframes++;
        if (indicators.getEma5_15min_gt_ema34_15min() != null && indicators.getEma5_15min_gt_ema34_15min()) bullishTimeframes++;
        
        // Count bearish timeframes
        if (indicators.getEma5_1min_gt_ema34_1min() != null && !indicators.getEma5_1min_gt_ema34_1min()) bearishTimeframes++;
        if (indicators.getEma5_5min_gt_ema34_5min() != null && !indicators.getEma5_5min_gt_ema34_5min()) bearishTimeframes++;
        if (indicators.getEma5_15min_gt_ema34_15min() != null && !indicators.getEma5_15min_gt_ema34_15min()) bearishTimeframes++;
        
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
        if (indicators.getEma5_5min_gt_ema34_5min() != null && indicators.getEma5_5min_gt_ema34_5min()) emaScore += scoringConfigService.getEmaQuality();
        if (indicators.getEma5_1min_gt_ema34_1min() != null && indicators.getEma5_1min_gt_ema34_1min()) emaScore += scoringConfigService.getEmaQuality();
        quality.setEmaScore(emaScore);
        
        // RSI Quality Score (0-10)
        double rsiScore = 0.0;
        if (indicators.getRsi_5min_gt_56() != null && indicators.getRsi_5min_gt_56()) rsiScore += scoringConfigService.getRsiQuality();
        if (indicators.getRsi_1min_gt_56() != null && indicators.getRsi_1min_gt_56()) rsiScore += scoringConfigService.getRsiQuality();
        quality.setRsiScore(rsiScore);
        
        // Volume Quality Score (0-10)
        double volumeScore = 0.0;
        if (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) volumeScore += scoringConfigService.getScoringConfig().getQualityScoring().getVolumeQuality().getVolume5min();
        if (indicators.getVolume_1min_surge() != null && indicators.getVolume_1min_surge()) volumeScore += scoringConfigService.getScoringConfig().getQualityScoring().getVolumeQuality().getVolume1min();
        if (indicators.getVolume_surge_multiplier() != null && indicators.getVolume_surge_multiplier() >= scoringConfigService.getScoringConfig().getQualityScoring().getVolumeQuality().getVolumeMultiplierThreshold()) volumeScore += scoringConfigService.getScoringConfig().getQualityScoring().getVolumeQuality().getVolumeMultiplier();
        quality.setVolumeScore(volumeScore);
        
        // Price Action Quality Score (0-10)
        double priceActionScore = 0.0;
        if (indicators.getPrice_gt_vwap_5min() != null && indicators.getPrice_gt_vwap_5min()) priceActionScore += scoringConfigService.getPriceActionQuality();
        if (indicators.getPrice_above_resistance() != null && indicators.getPrice_above_resistance()) priceActionScore += scoringConfigService.getPriceActionQuality();
        
        // Removed verbose debug logging - only log when there's an actual entry signal
        
        quality.setPriceActionScore(priceActionScore);
        
        // Futuresignal Quality Score (0-10)
        double futuresignalScore = 0.0;
        if (indicators.getFuturesignals() != null && indicators.getFuturesignals().getAllTimeframesBullish()) {
            futuresignalScore = scoringConfigService.getFuturesignalQuality();
        } else if (indicators.getFuturesignals() != null && 
                   (indicators.getFuturesignals().getFiveMinBullishSurge() || indicators.getFuturesignals().getOneMinBullishSurge())) {
            futuresignalScore = scoringConfigService.getFuturesignalQuality() / 2.0; // Half score for partial signals
        }
        quality.setFuturesignalScore(futuresignalScore);
        
        // Momentum Quality Score (0-10)
        double momentumScore = 0.0;
        int bullishTimeframes = 0;
        if (indicators.getEma5_1min_gt_ema34_1min() != null && indicators.getEma5_1min_gt_ema34_1min()) bullishTimeframes++;
        if (indicators.getEma5_5min_gt_ema34_5min() != null && indicators.getEma5_5min_gt_ema34_5min()) bullishTimeframes++;
        if (indicators.getEma5_15min_gt_ema34_15min() != null && indicators.getEma5_15min_gt_ema34_15min()) bullishTimeframes++;
        
        if (bullishTimeframes == 3) momentumScore = scoringConfigService.getScoringConfig().getQualityScoring().getMomentumQuality().getPerfectAlignment();
        else if (bullishTimeframes == 2) momentumScore = scoringConfigService.getScoringConfig().getQualityScoring().getMomentumQuality().getMajorityAlignment();
        else if (bullishTimeframes == 1) momentumScore = scoringConfigService.getScoringConfig().getQualityScoring().getMomentumQuality().getSingleAlignment();
        quality.setMomentumScore(momentumScore);
        
        // Candlestick Pattern Quality Score (0-10)
        double candlestickScore = 0.0;
        
        // High reliability bullish patterns (3 points each)
        if (indicators.getBullish_engulfing_5min() != null && indicators.getBullish_engulfing_5min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getHighReliability();
        if (indicators.getBullish_engulfing_1min() != null && indicators.getBullish_engulfing_1min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getHighReliability();
        if (indicators.getBullish_morning_star_5min() != null && indicators.getBullish_morning_star_5min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getHighReliability();
        if (indicators.getBullish_morning_star_1min() != null && indicators.getBullish_morning_star_1min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getHighReliability();
        
        // Medium reliability bullish patterns (2 points each)
        if (indicators.getHammer_5min() != null && indicators.getHammer_5min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (indicators.getHammer_1min() != null && indicators.getHammer_1min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (indicators.getInverted_hammer_5min() != null && indicators.getInverted_hammer_5min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (indicators.getInverted_hammer_1min() != null && indicators.getInverted_hammer_1min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (indicators.getBullish_harami_5min() != null && indicators.getBullish_harami_5min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (indicators.getBullish_harami_1min() != null && indicators.getBullish_harami_1min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (indicators.getBullish_marubozu_5min() != null && indicators.getBullish_marubozu_5min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (indicators.getBullish_marubozu_1min() != null && indicators.getBullish_marubozu_1min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        
        // Low reliability bullish patterns (1 point each)
        if (indicators.getLong_lower_shadow_5min() != null && indicators.getLong_lower_shadow_5min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getLowReliability();
        if (indicators.getLong_lower_shadow_1min() != null && indicators.getLong_lower_shadow_1min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getLowReliability();
        if (indicators.getLong_body_5min() != null && indicators.getLong_body_5min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getLowReliability();
        if (indicators.getLong_body_1min() != null && indicators.getLong_body_1min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getLowReliability();
        
        // Cap the score at max score
        candlestickScore = Math.min(candlestickScore, scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMaxScore());
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
        if (indicators.getEma5_5min_gt_ema34_5min() != null && !indicators.getEma5_5min_gt_ema34_5min()) emaScore += scoringConfigService.getEmaQuality();
        if (indicators.getEma5_1min_gt_ema34_1min() != null && !indicators.getEma5_1min_gt_ema34_1min()) emaScore += scoringConfigService.getEmaQuality();
        quality.setEmaScore(emaScore);
        
        // RSI Quality Score (0-10)
        double rsiScore = 0.0;
        if (indicators.getRsi_5min_lt_44() != null && indicators.getRsi_5min_lt_44()) rsiScore += scoringConfigService.getRsiQuality();
        if (indicators.getRsi_1min_lt_44() != null && indicators.getRsi_1min_lt_44()) rsiScore += scoringConfigService.getRsiQuality();
        quality.setRsiScore(rsiScore);
        
        // Volume Quality Score (0-10)
        double volumeScore = 0.0;
        if (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) volumeScore += scoringConfigService.getScoringConfig().getQualityScoring().getVolumeQuality().getVolume5min();
        if (indicators.getVolume_1min_surge() != null && indicators.getVolume_1min_surge()) volumeScore += scoringConfigService.getScoringConfig().getQualityScoring().getVolumeQuality().getVolume1min();
        if (indicators.getVolume_surge_multiplier() != null && indicators.getVolume_surge_multiplier() >= scoringConfigService.getScoringConfig().getQualityScoring().getVolumeQuality().getVolumeMultiplierThreshold()) volumeScore += scoringConfigService.getScoringConfig().getQualityScoring().getVolumeQuality().getVolumeMultiplier();
        quality.setVolumeScore(volumeScore);
        
        // Price Action Quality Score (0-10)
        double priceActionScore = 0.0;
        if (indicators.getPrice_lt_vwap_5min() != null && indicators.getPrice_lt_vwap_5min()) priceActionScore += scoringConfigService.getPriceActionQuality();
        if (indicators.getPrice_below_support() != null && indicators.getPrice_below_support()) priceActionScore += scoringConfigService.getPriceActionQuality();
        
        // Debug logging for price action conditions
        // Removed verbose debug logging - only log when there's an actual entry signal
        
        quality.setPriceActionScore(priceActionScore);
        
        // Futuresignal Quality Score (0-10)
        double futuresignalScore = 0.0;
        if (indicators.getFuturesignals() != null && indicators.getFuturesignals().getAllTimeframesBearish()) {
            futuresignalScore = scoringConfigService.getFuturesignalQuality();
        } else if (indicators.getFuturesignals() != null && 
                   (indicators.getFuturesignals().getFiveMinBearishSurge() || indicators.getFuturesignals().getOneMinBearishSurge())) {
            futuresignalScore = scoringConfigService.getFuturesignalQuality() / 2.0; // Half score for partial signals
        }
        quality.setFuturesignalScore(futuresignalScore);
        
        // Momentum Quality Score (0-10)
        double momentumScore = 0.0;
        int bearishTimeframes = 0;
        if (indicators.getEma5_1min_gt_ema34_1min() != null && !indicators.getEma5_1min_gt_ema34_1min()) bearishTimeframes++;
        if (indicators.getEma5_5min_gt_ema34_5min() != null && !indicators.getEma5_5min_gt_ema34_5min()) bearishTimeframes++;
        if (indicators.getEma5_15min_gt_ema34_15min() != null && !indicators.getEma5_15min_gt_ema34_15min()) bearishTimeframes++;
        
        if (bearishTimeframes == 3) momentumScore = scoringConfigService.getScoringConfig().getQualityScoring().getMomentumQuality().getPerfectAlignment();
        else if (bearishTimeframes == 2) momentumScore = scoringConfigService.getScoringConfig().getQualityScoring().getMomentumQuality().getMajorityAlignment();
        else if (bearishTimeframes == 1) momentumScore = scoringConfigService.getScoringConfig().getQualityScoring().getMomentumQuality().getSingleAlignment();
        quality.setMomentumScore(momentumScore);
        
        // Candlestick Pattern Quality Score (0-10)
        double candlestickScore = 0.0;
        
        // High reliability bearish patterns (3 points each)
        if (indicators.getBearish_engulfing_5min() != null && indicators.getBearish_engulfing_5min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getHighReliability();
        if (indicators.getBearish_engulfing_1min() != null && indicators.getBearish_engulfing_1min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getHighReliability();
        if (indicators.getBearish_evening_star_5min() != null && indicators.getBearish_evening_star_5min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getHighReliability();
        if (indicators.getBearish_evening_star_1min() != null && indicators.getBearish_evening_star_1min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getHighReliability();
        
        // Medium reliability bearish patterns (2 points each)
        if (indicators.getShooting_star_5min() != null && indicators.getShooting_star_5min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (indicators.getShooting_star_1min() != null && indicators.getShooting_star_1min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (indicators.getBearish_harami_5min() != null && indicators.getBearish_harami_5min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (indicators.getBearish_harami_1min() != null && indicators.getBearish_harami_1min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (indicators.getBearish_marubozu_5min() != null && indicators.getBearish_marubozu_5min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (indicators.getBearish_marubozu_1min() != null && indicators.getBearish_marubozu_1min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (indicators.getLong_upper_shadow_5min() != null && indicators.getLong_upper_shadow_5min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        if (indicators.getLong_upper_shadow_1min() != null && indicators.getLong_upper_shadow_1min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMediumReliability();
        
        // Low reliability bearish patterns (1 point each)
        if (indicators.getLong_body_5min() != null && indicators.getLong_body_5min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getLowReliability();
        if (indicators.getLong_body_1min() != null && indicators.getLong_body_1min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getLowReliability();
        if (indicators.getShort_body_5min() != null && indicators.getShort_body_5min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getLowReliability();
        if (indicators.getShort_body_1min() != null && indicators.getShort_body_1min()) candlestickScore += scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getLowReliability();
        
        // Cap the score at max score
        candlestickScore = Math.min(candlestickScore, scoringConfigService.getScoringConfig().getQualityScoring().getCandlestickQuality().getMaxScore());
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
}
