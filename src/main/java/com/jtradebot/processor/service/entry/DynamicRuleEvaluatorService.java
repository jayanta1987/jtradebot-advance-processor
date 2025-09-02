package com.jtradebot.processor.service.entry;

import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.config.ScoringConfigurationService;
import com.jtradebot.processor.config.TradingConfigurationService;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.model.indicator.EntryQuality;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.strategy.ScalpingVolumeSurgeCallRule;
import com.jtradebot.processor.model.strategy.ScalpingVolumeSurgePutRule;
import com.jtradebot.processor.model.strategy.ScalpingEntryDecision;

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


    public ScalpingEntryDecision getEntryDecision(Tick tick, FlattenedIndicators indicators, Boolean inTradingZone) {
        try {
            String instrumentToken = String.valueOf(tick.getInstrumentToken());

            // Calculate quality score once to avoid duplicate calculations
            EntryQuality callQuality = evaluateCallEntryQuality(indicators, tick);
            EntryQuality putQuality = evaluatePutEntryQuality(indicators, tick);
            
            // Use the dominant quality score (same logic as TickProcessService)
            boolean isCallDominant = callQuality.getQualityScore() > putQuality.getQualityScore();
            double qualityScore = isCallDominant ? callQuality.getQualityScore() : putQuality.getQualityScore();

            
            // 🔥 OPTIMIZATION: Use new scenario-based entry evaluation with pre-calculated quality score AND market condition
            ScalpingEntryDecision decision = scalpingEntryService.evaluateEntry(tick, indicators, qualityScore, inTradingZone);
            
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
            
            // Check if we have sufficient BarSeries data for indicator calculations
            ensureSufficientBarSeriesData(String.valueOf(indexTick.getInstrumentToken()));
            
            // Get BarSeries for different timeframes
            BarSeries oneMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(indexTick.getInstrumentToken()), ONE_MIN);
            BarSeries fiveMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(indexTick.getInstrumentToken()), FIVE_MIN);
            BarSeries fifteenMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(indexTick.getInstrumentToken()), FIFTEEN_MIN);
            
            // Initialize rules if not already done
            if (callRule == null || putRule == null) {
                initializeRules();
            }
            
            // Use RuleHelper to flatten indicators
            ruleHelper.flattenEmaIndicators(indicators, oneMinSeries, fiveMinSeries, fifteenMinSeries);
            ruleHelper.flattenRsiIndicators(indicators, oneMinSeries, fiveMinSeries, fifteenMinSeries, 
                callRule.getMinRsiThreshold(), putRule.getMaxRsiThreshold(),
                configService.getRsiMaPeriod(), configService.isEnableRsiMaComparison());
            ruleHelper.flattenPriceActionIndicators(indicators, oneMinSeries, fiveMinSeries, fifteenMinSeries, indexTick);
            ruleHelper.flattenCandlestickPatternIndicators(indicators, oneMinSeries, fiveMinSeries, fifteenMinSeries);
            
            // Calculate futuresignals
            indicators.setFuturesignals(ruleHelper.calculateFuturesignals(indicators));
            
            // Get future tick from map for volume calculations and enhance volume indicators
            String niftyFutureToken = kiteInstrumentHandler.getNifty50FutureToken().toString();
            Tick futureTick = tickDataManager.getLastTick(niftyFutureToken);
            
            if (futureTick != null) {
                // Enhance volume indicators with future data
                ruleHelper.enhanceVolumeIndicatorsWithFutureData(indicators, futureTick);
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
        if (indicators.getRsi_5min_gt_60() != null && indicators.getRsi_5min_gt_60()) rsiScore += scoringConfigService.getRsiQuality();
        if (indicators.getRsi_1min_gt_60() != null && indicators.getRsi_1min_gt_60()) rsiScore += scoringConfigService.getRsiQuality();
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
        if (indicators.getRsi_5min_lt_40() != null && indicators.getRsi_5min_lt_40()) rsiScore += scoringConfigService.getRsiQuality();
        if (indicators.getRsi_1min_lt_40() != null && indicators.getRsi_1min_lt_40()) rsiScore += scoringConfigService.getRsiQuality();
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
}
