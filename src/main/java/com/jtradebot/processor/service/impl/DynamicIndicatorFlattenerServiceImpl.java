package com.jtradebot.processor.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtradebot.processor.indicator.MultiEmaIndicator;
import com.jtradebot.processor.indicator.PriceVolumeSurgeIndicator;
import com.jtradebot.processor.indicator.RsiIndicator;
import com.jtradebot.processor.indicator.SupportResistanceIndicator;
import com.jtradebot.processor.indicator.VWAPIndicator;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.indicator.DynamicFlattenedIndicators;
import com.jtradebot.processor.model.indicator.DynamicIndicatorConfig;
import com.jtradebot.processor.model.indicator.EmaInfo;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.jtradebot.processor.repository.document.TickDocument;
import com.jtradebot.processor.service.DynamicIndicatorFlattenerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.*;
import com.jtradebot.processor.candleStick.CandlestickPattern;
import com.jtradebot.processor.model.indicator.Support;
import com.jtradebot.processor.model.indicator.Resistance;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicIndicatorFlattenerServiceImpl implements DynamicIndicatorFlattenerService {

    private final MultiEmaIndicator multiEmaIndicator;
    private final RsiIndicator rsiIndicator;
    private final PriceVolumeSurgeIndicator priceVolumeSurgeIndicator;
    private final VWAPIndicator vwapIndicator;
    private final SupportResistanceIndicator supportResistanceIndicator;
    private final TickDataManager tickDataManager;
    private final ObjectMapper objectMapper;

    @Override
    public DynamicFlattenedIndicators flattenIndicators(TickDocument tickDocument, DynamicIndicatorConfig config) {
        log.debug("Flattening indicators dynamically for instrument: {}", tickDocument.getInstrumentToken());
        
        DynamicFlattenedIndicators flattenedIndicators = DynamicFlattenedIndicators.builder()
                .instrumentToken(String.valueOf(tickDocument.getInstrumentToken()))
                .timestamp(LocalDateTime.ofInstant(tickDocument.getTickTimestamp().toInstant(), ZoneId.systemDefault()))
                .timeframe("5min") // Default timeframe for scalping
                .booleanIndicators(new HashMap<>())
                .numericIndicators(new HashMap<>())
                .stringIndicators(new HashMap<>())
                .rawValues(new HashMap<>())
                .build();
        
        // Process each indicator defined in the configuration
        if (config.getIndicators() != null) {
            for (Map.Entry<String, DynamicIndicatorConfig.IndicatorDefinition> entry : config.getIndicators().entrySet()) {
                try {
                    flattenIndicatorType(tickDocument, flattenedIndicators, entry.getValue());
                } catch (Exception e) {
                    log.error("Error flattening indicator {} for instrument: {}", entry.getKey(), tickDocument.getInstrumentToken(), e);
                }
            }
        }
        
        log.debug("Dynamic indicators flattened successfully for instrument: {}", tickDocument.getInstrumentToken());
        return flattenedIndicators;
    }

    @Override
    public void flattenIndicatorType(TickDocument tickDocument, DynamicFlattenedIndicators flattenedIndicators, 
                                   DynamicIndicatorConfig.IndicatorDefinition indicatorConfig) {
        
        String indicatorType = indicatorConfig.getType().toUpperCase();
        String indicatorName = indicatorConfig.getName();
        
        log.debug("Flattening indicator: {} of type: {}", indicatorName, indicatorType);
        
        switch (indicatorType) {
            case "EMA":
                flattenEmaIndicators(tickDocument, flattenedIndicators, indicatorConfig);
                break;
            case "RSI":
                flattenRsiIndicators(tickDocument, flattenedIndicators, indicatorConfig);
                break;
            case "VOLUME":
                flattenVolumeIndicators(tickDocument, flattenedIndicators, indicatorConfig);
                break;
            case "VWAP":
                flattenVwapIndicators(tickDocument, flattenedIndicators, indicatorConfig);
                break;
            case "SUPPORT_RESISTANCE":
                flattenSupportResistanceIndicators(tickDocument, flattenedIndicators, indicatorConfig);
                break;
            case "CANDLESTICK":
                flattenCandlestickIndicators(tickDocument, flattenedIndicators, indicatorConfig);
                break;
            case "CROSSOVER":
                flattenCrossoverIndicators(tickDocument, flattenedIndicators, indicatorConfig);
                break;
            case "CROSSDOWN":
                flattenCrossdownIndicators(tickDocument, flattenedIndicators, indicatorConfig);
                break;
            default:
                log.warn("Unknown indicator type: {}", indicatorType);
        }
    }

    private void flattenEmaIndicators(TickDocument tickDocument, DynamicFlattenedIndicators flattenedIndicators, 
                                    DynamicIndicatorConfig.IndicatorDefinition config) {
        log.info("🚀 DYNAMIC EMA CALCULATION - Instrument: {}, Timestamp: {}", 
            tickDocument.getInstrumentToken(), tickDocument.getTickTimestamp());
        
        try {
            for (String timeframe : config.getTimeframes()) {
                CandleTimeFrameEnum timeFrameEnum = getTimeFrameEnum(timeframe);
                if (timeFrameEnum == null) continue;
                
                BarSeries barSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tickDocument.getInstrumentToken()), timeFrameEnum);
                if (barSeries == null || barSeries.getBarCount() < 20) continue;
                
                EmaInfo emaInfo = multiEmaIndicator.calculateEmaValues(barSeries, timeFrameEnum);
                Double currentPrice = tickDocument.getLastTradedPrice();
                
                // Log EMA values for debugging
                if (emaInfo != null) {
                    log.info("📈 DYNAMIC EMA VALUES - {}min - EMA5: {:.2f}, EMA34: {:.2f}, EMA5>EMA34: {}", 
                        timeframe, emaInfo.getEma5(), emaInfo.getEma34(), 
                        emaInfo.getEma5() > emaInfo.getEma34());
                }
                
                // Only create indicators based on the configuration
                String comparison = config.getComparison();
                if (comparison != null) {
                    switch (comparison.toUpperCase()) {
                        case "CROSSOVER":
                            String crossoverKey = String.format("ema_%s_crossover", timeframe);
                            flattenedIndicators.setBooleanIndicator(crossoverKey, detectEmaCrossover(barSeries, emaInfo));
                            break;
                        case "CROSSDOWN":
                            String crossdownKey = String.format("ema_%s_crossdown", timeframe);
                            flattenedIndicators.setBooleanIndicator(crossdownKey, detectEmaCrossdown(barSeries, emaInfo));
                            break;
                        case "TREND_ABOVE":
                            // Price above specific EMA (for trend identification)
                            Integer emaPeriod = (Integer) config.getParameters().get("ema1");
                            if (emaPeriod != null) {
                                Double emaValue = getEmaValue(emaInfo, emaPeriod);
                                if (emaValue != null) {
                                    String trendKey = String.format("ema%d_trend_%s", emaPeriod, timeframe);
                                    flattenedIndicators.setBooleanIndicator(trendKey, currentPrice > emaValue);
                                }
                            }
                            break;
                        case "CANDLE_TOUCHES":
                            // Candle touches or follows specific EMA (your key principle)
                            Integer emaPeriodTouch = (Integer) config.getParameters().get("ema1");
                            if (emaPeriodTouch != null) {
                                Double emaValueTouch = getEmaValue(emaInfo, emaPeriodTouch);
                                if (emaValueTouch != null) {
                                    String touchKey = String.format("ema%d_touch_%s", emaPeriodTouch, timeframe);
                                    // Check if candle touches EMA5 (within 0.1% range)
                                    double tolerance = emaValueTouch * 0.001; // 0.1% tolerance
                                    boolean touches = Math.abs(currentPrice - emaValueTouch) <= tolerance;
                                    flattenedIndicators.setBooleanIndicator(touchKey, touches);
                                }
                            }
                            break;
                        case "GT":
                            // Compare two EMAs (e.g., EMA9 > EMA34)
                            Integer ema1 = (Integer) config.getParameters().get("ema1");
                            Integer ema2 = (Integer) config.getParameters().get("ema2");
                            if (ema1 != null && ema2 != null) {
                                Double ema1Value = getEmaValue(emaInfo, ema1);
                                Double ema2Value = getEmaValue(emaInfo, ema2);
                                if (ema1Value != null && ema2Value != null) {
                                    String gtKey = String.format("ema%d_above_ema%d_%s", ema1, ema2, timeframe);
                                    boolean result = ema1Value > ema2Value;
                                    flattenedIndicators.setBooleanIndicator(gtKey, result);
                                    log.info("🔍 EMA COMPARISON - {}min - EMA{}: {:.2f}, EMA{}: {:.2f}, EMA{}>EMA{}: {} (Key: {})", 
                                        timeframe, ema1, ema1Value, ema2, ema2Value, ema1, ema2, result, gtKey);
                                }
                            }
                            break;
                        default:
                            log.warn("Unknown EMA comparison type: {}", comparison);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error flattening EMA indicators", e);
        }
        
        log.info("✅ DYNAMIC EMA CALCULATION COMPLETED - Instrument: {}", tickDocument.getInstrumentToken());
    }
    
    /**
     * Get EMA value based on period
     */
    private Double getEmaValue(EmaInfo emaInfo, Integer period) {
        switch (period) {
            case 5: return emaInfo.getEma5();
            case 9: return emaInfo.getEma9();
            case 20: return emaInfo.getEma20();
            case 34: return emaInfo.getEma34();
            case 200: return emaInfo.getEma200();
            default:
                log.warn("Unsupported EMA period: {}", period);
                return null;
        }
    }

    private void flattenRsiIndicators(TickDocument tickDocument, DynamicFlattenedIndicators flattenedIndicators, 
                                    DynamicIndicatorConfig.IndicatorDefinition config) {
        try {
            for (String timeframe : config.getTimeframes()) {
                CandleTimeFrameEnum timeFrameEnum = getTimeFrameEnum(timeframe);
                if (timeFrameEnum == null) continue;
                
                BarSeries barSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tickDocument.getInstrumentToken()), timeFrameEnum);
                if (barSeries == null || barSeries.getBarCount() < 14) continue;
                
                Double rsiValue = rsiIndicator.getRsiValue(barSeries, 14);
                if (rsiValue == null) continue;
                
                // Only create indicators based on the configuration
                if (config.getThreshold() != null && config.getComparison() != null) {
                    String comparisonKey = String.format("rsi_%s_%s_%.0f", timeframe, config.getComparison().toLowerCase(), config.getThreshold());
                    
                    boolean result = false;
                    switch (config.getComparison().toUpperCase()) {
                        case "GT":
                            result = rsiValue > config.getThreshold();
                            break;
                        case "LT":
                            result = rsiValue < config.getThreshold();
                            break;
                        case "EQ":
                            result = Math.abs(rsiValue - config.getThreshold()) < 0.1;
                            break;
                        default:
                            log.warn("Unknown RSI comparison type: {}", config.getComparison());
                            continue;
                    }
                    flattenedIndicators.setBooleanIndicator(comparisonKey, result);
                }
            }
        } catch (Exception e) {
            log.error("Error flattening RSI indicators", e);
        }
    }

    private void flattenVolumeIndicators(TickDocument tickDocument, DynamicFlattenedIndicators flattenedIndicators, 
                                       DynamicIndicatorConfig.IndicatorDefinition config) {
        try {
            for (String timeframe : config.getTimeframes()) {
                CandleTimeFrameEnum timeFrameEnum = getTimeFrameEnum(timeframe);
                if (timeFrameEnum == null) continue;
                
                BarSeries barSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tickDocument.getInstrumentToken()), timeFrameEnum);
                if (barSeries == null || barSeries.getBarCount() < 20) continue;
                
                // Get current volume from the latest bar
                long currentVolume = barSeries.getBar(barSeries.getBarCount() - 1).getVolume().longValue();
                
                PriceVolumeSurgeIndicator.VolumeSurgeResult surgeResult = 
                    priceVolumeSurgeIndicator.calculateVolumeSurge(String.valueOf(tickDocument.getInstrumentToken()), timeFrameEnum, currentVolume);
                
                // Only create indicators based on the configuration
                if (config.getThreshold() != null && config.getComparison() != null) {
                    String thresholdKey = String.format("volume_%s_multiplier_%s_%.1f", timeframe, config.getComparison().toLowerCase(), config.getThreshold());
                    
                    boolean result = false;
                    switch (config.getComparison().toUpperCase()) {
                        case "GT":
                            result = surgeResult.getVolumeMultiplier() > config.getThreshold();
                            break;
                        case "LT":
                            result = surgeResult.getVolumeMultiplier() < config.getThreshold();
                            break;
                        default:
                            log.warn("Unknown volume comparison type: {}", config.getComparison());
                            continue;
                    }
                    flattenedIndicators.setBooleanIndicator(thresholdKey, result);
                } else {
                    // Default volume surge indicator
                    String surgeKey = String.format("volume_%s_surge", timeframe);
                    flattenedIndicators.setBooleanIndicator(surgeKey, surgeResult.hasSurge());
                }
            }
        } catch (Exception e) {
            log.error("Error flattening volume indicators", e);
        }
    }

    private void flattenVwapIndicators(TickDocument tickDocument, DynamicFlattenedIndicators flattenedIndicators, 
                                     DynamicIndicatorConfig.IndicatorDefinition config) {
        try {
            for (String timeframe : config.getTimeframes()) {
                CandleTimeFrameEnum timeFrameEnum = getTimeFrameEnum(timeframe);
                if (timeFrameEnum == null) continue;
                
                BarSeries barSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tickDocument.getInstrumentToken()), timeFrameEnum);
                if (barSeries == null || barSeries.getBarCount() < 1) continue;
                
                Double vwapValue = vwapIndicator.calculateVWAP(barSeries);
                Double currentPrice = tickDocument.getLastTradedPrice();
                
                if (vwapValue == null || currentPrice == null) continue;
                
                // Only create indicators based on the configuration
                if (config.getComparison() != null) {
                    switch (config.getComparison().toUpperCase()) {
                        case "GT":
                            String gtKey = String.format("price_%s_gt_vwap", timeframe);
                            flattenedIndicators.setBooleanIndicator(gtKey, currentPrice > vwapValue);
                            break;
                        case "LT":
                            String ltKey = String.format("price_%s_lt_vwap", timeframe);
                            flattenedIndicators.setBooleanIndicator(ltKey, currentPrice < vwapValue);
                            break;
                        default:
                            log.warn("Unknown VWAP comparison type: {}", config.getComparison());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error flattening VWAP indicators", e);
        }
    }

    private void flattenSupportResistanceIndicators(TickDocument tickDocument, DynamicFlattenedIndicators flattenedIndicators, 
                                                   DynamicIndicatorConfig.IndicatorDefinition config) {
        try {
            for (String timeframe : config.getTimeframes()) {
                CandleTimeFrameEnum timeFrameEnum = getTimeFrameEnum(timeframe);
                if (timeFrameEnum == null) continue;
                
                BarSeries barSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tickDocument.getInstrumentToken()), timeFrameEnum);
                if (barSeries == null || barSeries.getBarCount() < 20) continue;
                
                Double currentPrice = tickDocument.getLastTradedPrice();
                if (currentPrice == null) continue;
                
                String comparison = config.getComparison();
                
                // Calculate support and resistance levels using simple high/low method
                double recentHigh = calculateRecentHigh(barSeries, 20);
                double recentLow = calculateRecentLow(barSeries, 20);
                
                if ("GT".equals(comparison)) {
                    // Price above resistance
                    String aboveResistanceKey = String.format("price_%s_above_resistance", timeframe);
                    boolean aboveResistance = currentPrice > recentHigh * 0.99;
                    flattenedIndicators.setBooleanIndicator(aboveResistanceKey, aboveResistance);
                } else if ("LT".equals(comparison)) {
                    // Price below support
                    String belowSupportKey = String.format("price_%s_below_support", timeframe);
                    boolean belowSupport = currentPrice < recentLow * 1.01;
                    flattenedIndicators.setBooleanIndicator(belowSupportKey, belowSupport);
                } else {
                    log.warn("Unknown support/resistance comparison type: {}", comparison);
                }
            }
        } catch (Exception e) {
            log.error("Error flattening support/resistance indicators", e);
        }
    }
    
    private void flattenCandlestickIndicators(TickDocument tickDocument, DynamicFlattenedIndicators flattenedIndicators, 
                                             DynamicIndicatorConfig.IndicatorDefinition config) {
        try {
            for (String timeframe : config.getTimeframes()) {
                CandleTimeFrameEnum timeFrameEnum = getTimeFrameEnum(timeframe);
                if (timeFrameEnum == null) continue;
                
                BarSeries barSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tickDocument.getInstrumentToken()), timeFrameEnum);
                if (barSeries == null || barSeries.getBarCount() < 3) continue;
                
                String pattern = (String) config.getParameters().get("pattern");
                Integer barsRequired = (Integer) config.getParameters().get("barsRequired");
                
                if (pattern == null || barsRequired == null) {
                    log.warn("Missing pattern or barsRequired for candlestick indicator");
                    continue;
                }
                
                boolean patternDetected = false;
                int currentIndex = barSeries.getBarCount() - 1;
                
                switch (pattern.toUpperCase()) {
                    case "BULLISH_ENGULFING":
                        if (barsRequired == 2 && currentIndex >= 1) {
                            patternDetected = CandlestickPattern.isBullishEngulfing(
                                barSeries.getBar(currentIndex - 1), barSeries.getBar(currentIndex));
                        }
                        break;
                    case "BEARISH_ENGULFING":
                        if (barsRequired == 2 && currentIndex >= 1) {
                            patternDetected = CandlestickPattern.isBearishEngulfing(
                                barSeries.getBar(currentIndex - 1), barSeries.getBar(currentIndex));
                        }
                        break;
                    case "BULLISH_HARAMI":
                        if (barsRequired == 2 && currentIndex >= 1) {
                            patternDetected = CandlestickPattern.isBullishHarami(
                                barSeries.getBar(currentIndex - 1), barSeries.getBar(currentIndex));
                        }
                        break;
                    case "BEARISH_HARAMI":
                        if (barsRequired == 2 && currentIndex >= 1) {
                            patternDetected = CandlestickPattern.isBearishHarami(
                                barSeries.getBar(currentIndex - 1), barSeries.getBar(currentIndex));
                        }
                        break;
                    case "BULLISH_DOJI_STAR":
                        if (barsRequired == 2 && currentIndex >= 1) {
                            patternDetected = CandlestickPattern.isBullishDojiStar(
                                barSeries.getBar(currentIndex - 1), barSeries.getBar(currentIndex));
                        }
                        break;
                    case "BEARISH_DOJI_STAR":
                        if (barsRequired == 2 && currentIndex >= 1) {
                            patternDetected = CandlestickPattern.isBearishDojiStar(
                                barSeries.getBar(currentIndex - 1), barSeries.getBar(currentIndex));
                        }
                        break;
                    case "BULLISH_MORNING_STAR":
                        if (barsRequired == 3 && currentIndex >= 2) {
                            patternDetected = CandlestickPattern.isBullishMorningStar(
                                barSeries.getBar(currentIndex - 2), barSeries.getBar(currentIndex - 1), barSeries.getBar(currentIndex));
                        }
                        break;
                    case "BEARISH_EVENING_STAR":
                        if (barsRequired == 3 && currentIndex >= 2) {
                            patternDetected = CandlestickPattern.isBearishEveningStar(
                                barSeries.getBar(currentIndex - 2), barSeries.getBar(currentIndex - 1), barSeries.getBar(currentIndex));
                        }
                        break;
                    case "HAMMER":
                        if (barsRequired == 1) {
                            patternDetected = CandlestickPattern.isHammer(barSeries.getBar(currentIndex));
                        }
                        break;
                    case "INVERTED_HAMMER":
                        if (barsRequired == 1) {
                            patternDetected = CandlestickPattern.isInvertedHammer(barSeries.getBar(currentIndex));
                        }
                        break;
                    case "SHOOTING_STAR":
                        if (barsRequired == 1) {
                            patternDetected = CandlestickPattern.isShootingStar(barSeries.getBar(currentIndex));
                        }
                        break;
                    case "HANGING_MAN":
                        if (barsRequired == 1) {
                            patternDetected = CandlestickPattern.isHangingMan(barSeries.getBar(currentIndex));
                        }
                        break;
                    case "DOJI":
                        if (barsRequired == 1) {
                            patternDetected = CandlestickPattern.isDoji(barSeries.getBar(currentIndex));
                        }
                        break;
                    case "SPINNING_TOP":
                        if (barsRequired == 1) {
                            patternDetected = CandlestickPattern.isSpinningTop(barSeries.getBar(currentIndex));
                        }
                        break;
                    case "MARUBOZU":
                        if (barsRequired == 1) {
                            patternDetected = CandlestickPattern.isMarubozu(barSeries.getBar(currentIndex));
                        }
                        break;
                    case "BULLISH_MARUBOZU":
                        if (barsRequired == 1) {
                            patternDetected = CandlestickPattern.isBullishMarubozu(barSeries.getBar(currentIndex));
                        }
                        break;
                    case "BEARISH_MARUBOZU":
                        if (barsRequired == 1) {
                            patternDetected = CandlestickPattern.isBearishMarubozu(barSeries.getBar(currentIndex));
                        }
                        break;
                    case "LONG_BODY":
                        if (barsRequired == 1) {
                            patternDetected = CandlestickPattern.isLongBody(barSeries.getBar(currentIndex));
                        }
                        break;
                    case "SHORT_BODY":
                        if (barsRequired == 1) {
                            patternDetected = CandlestickPattern.isShortBody(barSeries.getBar(currentIndex));
                        }
                        break;
                    case "LONG_LOWER_SHADOW":
                        if (barsRequired == 1) {
                            patternDetected = CandlestickPattern.isLongLowerShadow(barSeries.getBar(currentIndex));
                        }
                        break;
                    case "LONG_UPPER_SHADOW":
                        if (barsRequired == 1) {
                            patternDetected = CandlestickPattern.isLongUpperShadow(barSeries.getBar(currentIndex));
                        }
                        break;
                    default:
                        log.warn("Unknown candlestick pattern: {}", pattern);
                        continue;
                }
                
                String patternKey = String.format("%s_%s", pattern.toLowerCase(), timeframe);
                flattenedIndicators.setBooleanIndicator(patternKey, patternDetected);
            }
        } catch (Exception e) {
            log.error("Error flattening candlestick indicators", e);
        }
    }

    private void flattenCrossoverIndicators(TickDocument tickDocument, DynamicFlattenedIndicators flattenedIndicators, 
                                          DynamicIndicatorConfig.IndicatorDefinition config) {
        // This is a generic crossover detector that can work with any two indicators
        try {
            for (String timeframe : config.getTimeframes()) {
                CandleTimeFrameEnum timeFrameEnum = getTimeFrameEnum(timeframe);
                if (timeFrameEnum == null) continue;
                
                BarSeries barSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tickDocument.getInstrumentToken()), timeFrameEnum);
                if (barSeries == null || barSeries.getBarCount() < 20) continue;
                
                // Get the two indicators to compare from config parameters
                String indicator1 = (String) config.getParameters().get("indicator1");
                String indicator2 = (String) config.getParameters().get("indicator2");
                
                if (indicator1 != null && indicator2 != null) {
                    Double value1 = getIndicatorValue(barSeries, indicator1, timeFrameEnum);
                    Double value2 = getIndicatorValue(barSeries, indicator2, timeFrameEnum);
                    
                    if (value1 != null && value2 != null) {
                        String crossoverKey = String.format("crossover_%s_%s_%s", indicator1, indicator2, timeframe);
                        flattenedIndicators.setBooleanIndicator(crossoverKey, detectCrossover(barSeries, indicator1, indicator2, timeFrameEnum));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error flattening crossover indicators", e);
        }
    }

    private void flattenCrossdownIndicators(TickDocument tickDocument, DynamicFlattenedIndicators flattenedIndicators, 
                                          DynamicIndicatorConfig.IndicatorDefinition config) {
        // This is a generic crossdown detector that can work with any two indicators
        try {
            for (String timeframe : config.getTimeframes()) {
                CandleTimeFrameEnum timeFrameEnum = getTimeFrameEnum(timeframe);
                if (timeFrameEnum == null) continue;
                
                BarSeries barSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(tickDocument.getInstrumentToken()), timeFrameEnum);
                if (barSeries == null || barSeries.getBarCount() < 20) continue;
                
                // Get the two indicators to compare from config parameters
                String indicator1 = (String) config.getParameters().get("indicator1");
                String indicator2 = (String) config.getParameters().get("indicator2");
                
                if (indicator1 != null && indicator2 != null) {
                    Double value1 = getIndicatorValue(barSeries, indicator1, timeFrameEnum);
                    Double value2 = getIndicatorValue(barSeries, indicator2, timeFrameEnum);
                    
                    if (value1 != null && value2 != null) {
                        String crossdownKey = String.format("crossdown_%s_%s_%s", indicator1, indicator2, timeframe);
                        flattenedIndicators.setBooleanIndicator(crossdownKey, detectCrossdown(barSeries, indicator1, indicator2, timeFrameEnum));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error flattening crossdown indicators", e);
        }
    }

    @Override
    public DynamicIndicatorConfig loadConfiguration(String configPath) {
        try {
            ClassPathResource resource = new ClassPathResource(configPath);
            return objectMapper.readValue(resource.getInputStream(), DynamicIndicatorConfig.class);
        } catch (IOException e) {
            log.error("Error loading indicator configuration from: {}", configPath, e);
            return null;
        }
    }

    @Override
    public boolean validateConfiguration(DynamicIndicatorConfig config) {
        if (config == null) return false;
        if (config.getIndicators() == null || config.getIndicators().isEmpty()) return false;
        if (config.getTimeframes() == null || config.getTimeframes().isEmpty()) return false;
        
        for (DynamicIndicatorConfig.IndicatorDefinition indicator : config.getIndicators().values()) {
            if (indicator.getType() == null || indicator.getTimeframes() == null) {
                return false;
            }
        }
        
        return true;
    }

    // Helper methods
    private CandleTimeFrameEnum getTimeFrameEnum(String timeframe) {
        switch (timeframe.toLowerCase()) {
            case "1min": return ONE_MIN;
            case "5min": return FIVE_MIN;
            case "15min": return FIFTEEN_MIN;
            default: return null;
        }
    }

    private boolean detectEmaCrossover(BarSeries barSeries, EmaInfo emaInfo) {
        // Simple crossover detection - can be enhanced
        return emaInfo.getEma9() > emaInfo.getEma20();
    }

    private boolean detectEmaCrossdown(BarSeries barSeries, EmaInfo emaInfo) {
        // Simple crossdown detection - can be enhanced
        return emaInfo.getEma9() < emaInfo.getEma20();
    }

    private double calculateRecentHigh(BarSeries barSeries, int periods) {
        double high = 0.0;
        int startIndex = Math.max(0, barSeries.getBarCount() - periods);
        for (int i = startIndex; i < barSeries.getBarCount(); i++) {
            high = Math.max(high, barSeries.getBar(i).getHighPrice().doubleValue());
        }
        return high;
    }

    private double calculateRecentLow(BarSeries barSeries, int periods) {
        double low = Double.MAX_VALUE;
        int startIndex = Math.max(0, barSeries.getBarCount() - periods);
        for (int i = startIndex; i < barSeries.getBarCount(); i++) {
            low = Math.min(low, barSeries.getBar(i).getLowPrice().doubleValue());
        }
        return low;
    }

    private Double getIndicatorValue(BarSeries barSeries, String indicator, CandleTimeFrameEnum timeframe) {
        // This method would need to be implemented based on the specific indicators
        // For now, return null as placeholder
        return null;
    }

    private boolean detectCrossover(BarSeries barSeries, String indicator1, String indicator2, CandleTimeFrameEnum timeframe) {
        // This method would need to be implemented based on the specific indicators
        // For now, return false as placeholder
        return false;
    }

    private boolean detectCrossdown(BarSeries barSeries, String indicator1, String indicator2, CandleTimeFrameEnum timeframe) {
        // This method would need to be implemented based on the specific indicators
        // For now, return false as placeholder
        return false;
    }
}
