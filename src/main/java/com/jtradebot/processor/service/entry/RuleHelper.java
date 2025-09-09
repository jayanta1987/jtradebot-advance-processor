package com.jtradebot.processor.service.entry;

import com.jtradebot.processor.candleStick.CandlestickPattern;
import com.jtradebot.processor.config.ScoringConfigurationService;
import com.jtradebot.processor.indicator.*;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.enums.DivergenceType;
import com.jtradebot.processor.model.indicator.*;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.util.Set;

import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RuleHelper {

    private final MultiEmaIndicator multiEmaIndicator;
    private final RsiIndicator rsiIndicator;
    private final VWAPIndicator vwapIndicator;
    private final SupportResistanceIndicator supportResistanceIndicator;
    private final PriceVolumeSurgeIndicator priceVolumeSurgeIndicator;
    private final MACDIndicator macdIndicator;
    private final ScoringConfigurationService scoringConfigService;
    private final OIIndicator oiIndicator;

    /**
     * Flatten EMA indicators for all timeframes
     */
    public void flattenEmaIndicators(FlattenedIndicators indicators, BarSeries oneMinSeries, BarSeries fiveMinSeries, BarSeries fifteenMinSeries) {
        try {
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

                    // Calculate EMA 200 distance for 1min timeframe
                    double ema200_1min = emaInfo_1min.getEma200();
                    double currentPrice = oneMinSeries.getLastBar().getClosePrice().doubleValue();
                    double ema200Distance1min = currentPrice - ema200_1min;
                    indicators.setEma200_distance_1min(ema200Distance1min);
                    
                    // Calculate new EMA price position indicators for 1min
                    indicators.setPrice_above_ema5_1min(currentPrice > ema5_1min);
                    indicators.setPrice_above_ema34_1min(currentPrice > ema34_1min);
                    indicators.setPrice_below_ema5_1min(currentPrice < ema5_1min);
                    indicators.setPrice_below_ema34_1min(currentPrice < ema34_1min);
                    
                    // Calculate EMA crossover indicators for 1min
                    if (oneMinSeries.getBarCount() >= 2) {
                        EmaInfo prevEmaInfo_1min = multiEmaIndicator.calculateEmaValues(
                            oneMinSeries.getSubSeries(0, oneMinSeries.getBarCount() - 1), ONE_MIN);
                        double prevEma5_1min = prevEmaInfo_1min.getEma5();
                        double prevEma34_1min = prevEmaInfo_1min.getEma34();
                        
                        // Bullish crossover: EMA5 crosses above EMA34
                        boolean bullishCrossover = prevEma5_1min <= prevEma34_1min && ema5_1min > ema34_1min;
                        // Bearish crossover: EMA5 crosses below EMA34
                        boolean bearishCrossover = prevEma5_1min >= prevEma34_1min && ema5_1min < ema34_1min;
                        
                        indicators.setEma_crossover_bullish_1min(bullishCrossover);
                        indicators.setEma_crossover_bearish_1min(bearishCrossover);
                    } else {
                        indicators.setEma_crossover_bullish_1min(false);
                        indicators.setEma_crossover_bearish_1min(false);
                    }
                } catch (Exception e) {
                    log.error("Error calculating 1min EMA", e);
                    indicators.setEma5_1min_gt_ema34_1min(null);
                    indicators.setEma5_1min_lt_ema34_1min(null);
                    indicators.setEma200_distance_1min(null);
                    indicators.setPrice_above_ema5_1min(null);
                    indicators.setPrice_above_ema34_1min(null);
                    indicators.setPrice_below_ema5_1min(null);
                    indicators.setPrice_below_ema34_1min(null);
                    indicators.setEma_crossover_bullish_1min(null);
                    indicators.setEma_crossover_bearish_1min(null);
                }
                
                // Calculate MACD indicators for 1min
                if (oneMinSeries != null && oneMinSeries.getBarCount() >= 26) {
                    try {
                        MACDIndicator.MACDResult macdResult1min = macdIndicator.calculateMACD(oneMinSeries);
                        indicators.setMacd_bullish_crossover_1min(macdResult1min.isBullishCrossover());
                        indicators.setMacd_bearish_crossover_1min(macdResult1min.isBearishCrossover());
                        indicators.setMacd_above_zero_1min(macdResult1min.isAboveZero());
                    } catch (Exception e) {
                        log.error("Error calculating 1min MACD", e);
                        indicators.setMacd_bullish_crossover_1min(null);
                        indicators.setMacd_above_zero_1min(null);
                    }
                } else {
                    indicators.setMacd_bullish_crossover_1min(null);
                    indicators.setMacd_above_zero_1min(null);
                }
            } else {
                log.warn("1min BarSeries insufficient data - BarCount: {}", oneMinSeries != null ? oneMinSeries.getBarCount() : 0);
                indicators.setEma5_1min_gt_ema34_1min(null);
                indicators.setEma5_1min_lt_ema34_1min(null);
                indicators.setEma200_distance_1min(null);
                indicators.setPrice_above_ema5_1min(null);
                indicators.setPrice_above_ema34_1min(null);
                indicators.setPrice_below_ema5_1min(null);
                indicators.setPrice_below_ema34_1min(null);
                indicators.setEma_crossover_bullish_1min(null);
                indicators.setEma_crossover_bearish_1min(null);
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

                    // Calculate EMA 200 distance for 5min timeframe
                    double ema200_5min = emaInfo_5min.getEma200();
                    double currentPrice = fiveMinSeries.getLastBar().getClosePrice().doubleValue();
                    double ema200Distance5min = currentPrice - ema200_5min;
                    indicators.setEma200_distance_5min(ema200Distance5min);
                    indicators.setEma34_5min(ema34_5min);
                    indicators.setEma200_5min(ema200_5min);
                    
                    // Calculate new EMA price position indicators for 5min
                    indicators.setPrice_above_ema5_5min(currentPrice > ema5_5min);
                    indicators.setPrice_above_ema34_5min(currentPrice > ema34_5min);
                    indicators.setPrice_below_ema5_5min(currentPrice < ema5_5min);
                    indicators.setPrice_below_ema34_5min(currentPrice < ema34_5min);
                    
                    // Calculate EMA crossover indicators for 5min
                    if (fiveMinSeries.getBarCount() >= 2) {
                        EmaInfo prevEmaInfo_5min = multiEmaIndicator.calculateEmaValues(
                            fiveMinSeries.getSubSeries(0, fiveMinSeries.getBarCount() - 1), FIVE_MIN);
                        double prevEma5_5min = prevEmaInfo_5min.getEma5();
                        double prevEma34_5min = prevEmaInfo_5min.getEma34();
                        
                        // Bullish crossover: EMA5 crosses above EMA34
                        boolean bullishCrossover = prevEma5_5min <= prevEma34_5min && ema5_5min > ema34_5min;
                        // Bearish crossover: EMA5 crosses below EMA34
                        boolean bearishCrossover = prevEma5_5min >= prevEma34_5min && ema5_5min < ema34_5min;
                        
                        indicators.setEma_crossover_bullish_5min(bullishCrossover);
                        indicators.setEma_crossover_bearish_5min(bearishCrossover);
                    } else {
                        indicators.setEma_crossover_bullish_5min(false);
                        indicators.setEma_crossover_bearish_5min(false);
                    }
                    
                    // Calculate MACD indicators for 5min
                    MACDIndicator.MACDResult macdResult5min = macdIndicator.calculateMACD(fiveMinSeries);
                    indicators.setMacd_bullish_crossover_5min(macdResult5min.isBullishCrossover());
                    indicators.setMacd_bearish_crossover_5min(macdResult5min.isBearishCrossover());
                    indicators.setMacd_above_zero_5min(macdResult5min.isAboveZero());
                    indicators.setMacd_below_zero_5min(macdResult5min.isBelowZero());
                } catch (Exception e) {
                    log.error("Error calculating 5min EMA", e);
                    indicators.setEma5_5min_gt_ema34_5min(null);
                    indicators.setEma5_5min_lt_ema34_5min(null);
                    indicators.setEma200_distance_5min(null);
                    indicators.setEma34_5min(null);
                    indicators.setEma200_5min(null);
                    indicators.setPrice_above_ema5_5min(null);
                    indicators.setPrice_above_ema34_5min(null);
                    indicators.setPrice_below_ema5_5min(null);
                    indicators.setPrice_below_ema34_5min(null);
                    indicators.setEma_crossover_bullish_5min(null);
                    indicators.setEma_crossover_bearish_5min(null);
                }
            } else {
                log.warn("5min BarSeries insufficient data - BarCount: {}", fiveMinSeries != null ? fiveMinSeries.getBarCount() : 0);
                indicators.setEma5_5min_gt_ema34_5min(null);
                indicators.setEma5_5min_lt_ema34_5min(null);
                indicators.setEma200_distance_5min(null);
                indicators.setEma34_5min(null);
                indicators.setEma200_5min(null);
                indicators.setPrice_above_ema5_5min(null);
                indicators.setPrice_above_ema34_5min(null);
                indicators.setPrice_below_ema5_5min(null);
                indicators.setPrice_below_ema34_5min(null);
                indicators.setEma_crossover_bullish_5min(null);
                indicators.setEma_crossover_bearish_5min(null);
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

                    // Calculate EMA 200 distance for 15min timeframe
                    double ema200_15min = emaInfo_15min.getEma200();
                    double currentPrice = fifteenMinSeries.getLastBar().getClosePrice().doubleValue();
                    double ema200Distance15min = currentPrice - ema200_15min;
                    indicators.setEma200_distance_15min(ema200Distance15min);
                    
                    // Calculate MACD indicators for 15min
                    MACDIndicator.MACDResult macdResult15min = macdIndicator.calculateMACD(fifteenMinSeries);
                    indicators.setMacd_bullish_crossover_15min(macdResult15min.isBullishCrossover());
                    indicators.setMacd_bearish_crossover_15min(macdResult15min.isBearishCrossover());
                    indicators.setMacd_above_zero_15min(macdResult15min.isAboveZero());
                    indicators.setMacd_below_zero_15min(macdResult15min.isBelowZero());
                } catch (Exception e) {
                    log.error("Error calculating 15min EMA", e);
                    indicators.setEma5_15min_gt_ema34_15min(null);
                    indicators.setEma5_15min_lt_ema34_15min(null);
                    indicators.setEma200_distance_15min(null);
                }
            } else {
                log.warn("15min BarSeries insufficient data - BarCount: {}", fifteenMinSeries != null ? fifteenMinSeries.getBarCount() : 0);
                indicators.setEma5_15min_gt_ema34_15min(null);
                indicators.setEma5_15min_lt_ema34_15min(null);
                indicators.setEma200_distance_15min(null);
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

    /**
     * Flatten RSI indicators for all timeframes
     */
    public void flattenRsiIndicators(FlattenedIndicators indicators, BarSeries oneMinSeries, BarSeries fiveMinSeries, BarSeries fifteenMinSeries, double callRsiThreshold, double putRsiThreshold, int rsiMaPeriod, boolean enableRsiMaComparison) {
        try {
            log.debug("RSI Thresholds - Call: {}, Put: {}, MA Period: {}, Enable MA: {}", callRsiThreshold, putRsiThreshold, rsiMaPeriod, enableRsiMaComparison);

            // 1-minute RSI calculation - Reduced minimum bars for backtesting
            if (oneMinSeries != null && oneMinSeries.getBarCount() >= 10) {
                double rsi_1min = rsiIndicator.getRsiValue(oneMinSeries, 14);
                log.debug("1min RSI: {} (BarCount: {})", rsi_1min, oneMinSeries.getBarCount());

                indicators.setRsi_1min_gt_80(rsi_1min > 80);
                indicators.setRsi_1min_lt_20(rsi_1min < 20);
                indicators.setRsi_1min_gt_60(rsi_1min > callRsiThreshold);
                indicators.setRsi_1min_lt_40(rsi_1min < putRsiThreshold);

                // RSI MA comparison
                if (enableRsiMaComparison && oneMinSeries.getBarCount() >= rsiMaPeriod) {
                    double rsiMa_1min = rsiIndicator.getRsiMaValue(oneMinSeries, 14, rsiMaPeriod);
                    indicators.setRsi_1min_gt_rsi_ma(rsi_1min > rsiMa_1min);
                    indicators.setRsi_1min_lt_rsi_ma(rsi_1min < rsiMa_1min);
                } else {
                    indicators.setRsi_1min_gt_rsi_ma(null);
                    indicators.setRsi_1min_lt_rsi_ma(null);
                }

                // RSI Divergence calculation for 1min
                if (oneMinSeries.getBarCount() >= 30) {
                    boolean bullishDivergence = rsiIndicator.isRsiDivergence(oneMinSeries, 14, 20, DivergenceType.BULLISH);
                    boolean bearishDivergence = rsiIndicator.isRsiDivergence(oneMinSeries, 14, 20, DivergenceType.BEARISH);
                    indicators.setRsi_bullish_divergence_1min(bullishDivergence);
                    indicators.setRsi_bearish_divergence_1min(bearishDivergence);
                    if (bullishDivergence || bearishDivergence) {
                        log.info("RSI Divergence 1min - Bullish: {}, Bearish: {}", bullishDivergence, bearishDivergence);
                    }
                } else {
                    indicators.setRsi_bullish_divergence_1min(null);
                    indicators.setRsi_bearish_divergence_1min(null);
                }
            } else {
                log.debug("1min BarSeries insufficient data - BarCount: {}", oneMinSeries != null ? oneMinSeries.getBarCount() : 0);
                indicators.setRsi_1min_gt_80(null);
                indicators.setRsi_1min_lt_20(null);
                indicators.setRsi_1min_gt_60(null);
                indicators.setRsi_1min_lt_40(null);
                indicators.setRsi_1min_gt_rsi_ma(null);
                indicators.setRsi_1min_lt_rsi_ma(null);
            }

            // 5-minute RSI calculation - Reduced minimum bars for backtesting
            if (fiveMinSeries != null && fiveMinSeries.getBarCount() >= 10) {
                double rsi_5min = rsiIndicator.getRsiValue(fiveMinSeries, 14);
                log.debug("5min RSI: {} (BarCount: {})", rsi_5min, fiveMinSeries.getBarCount());

                indicators.setRsi_5min_gt_80(rsi_5min > 80);
                indicators.setRsi_5min_lt_20(rsi_5min < 20);
                indicators.setRsi_5min_gt_60(rsi_5min > callRsiThreshold);
                indicators.setRsi_5min_lt_40(rsi_5min < putRsiThreshold);

                // RSI MA comparison
                if (enableRsiMaComparison && fiveMinSeries.getBarCount() >= rsiMaPeriod) {
                    double rsiMa_5min = rsiIndicator.getRsiMaValue(fiveMinSeries, 14, rsiMaPeriod);
                    indicators.setRsi_5min_gt_rsi_ma(rsi_5min > rsiMa_5min);
                    indicators.setRsi_5min_lt_rsi_ma(rsi_5min < rsiMa_5min);
                } else {
                    indicators.setRsi_5min_gt_rsi_ma(null);
                    indicators.setRsi_5min_lt_rsi_ma(null);
                }

                // RSI Divergence calculation for 5min
                if (fiveMinSeries.getBarCount() >= 30) {
                    boolean bullishDivergence = rsiIndicator.isRsiDivergence(fiveMinSeries, 14, 20, DivergenceType.BULLISH);
                    boolean bearishDivergence = rsiIndicator.isRsiDivergence(fiveMinSeries, 14, 20, DivergenceType.BEARISH);
                    indicators.setRsi_bullish_divergence_5min(bullishDivergence);
                    indicators.setRsi_bearish_divergence_5min(bearishDivergence);
                    if (bullishDivergence || bearishDivergence) {
                        log.info("RSI Divergence 5min - Bullish: {}, Bearish: {}", bullishDivergence, bearishDivergence);
                    }
                } else {
                    indicators.setRsi_bullish_divergence_5min(null);
                    indicators.setRsi_bearish_divergence_5min(null);
                }
            } else {
                log.debug("5min BarSeries insufficient data - BarCount: {}", fiveMinSeries != null ? fiveMinSeries.getBarCount() : 0);
                indicators.setRsi_5min_gt_80(null);
                indicators.setRsi_5min_lt_20(null);
                indicators.setRsi_5min_gt_60(null);
                indicators.setRsi_5min_lt_40(null);
                indicators.setRsi_5min_gt_rsi_ma(null);
                indicators.setRsi_5min_lt_rsi_ma(null);
                indicators.setRsi_bullish_divergence_5min(null);
                indicators.setRsi_bearish_divergence_5min(null);
            }

            // 15-minute RSI calculation - Reduced minimum bars for backtesting
            if (fifteenMinSeries != null && fifteenMinSeries.getBarCount() >= 10) {
                double rsi_15min = rsiIndicator.getRsiValue(fifteenMinSeries, 14);
                log.debug("15min RSI: {} (BarCount: {})", rsi_15min, fifteenMinSeries.getBarCount());

                indicators.setRsi_15min_gt_80(rsi_15min > 80);
                indicators.setRsi_15min_lt_20(rsi_15min < 20);
                indicators.setRsi_15min_gt_60(rsi_15min > callRsiThreshold);
                indicators.setRsi_15min_lt_40(rsi_15min < putRsiThreshold);

                // RSI MA comparison
                if (enableRsiMaComparison && fifteenMinSeries.getBarCount() >= rsiMaPeriod) {
                    double rsiMa_15min = rsiIndicator.getRsiMaValue(fifteenMinSeries, 14, rsiMaPeriod);
                    indicators.setRsi_15min_gt_rsi_ma(rsi_15min > rsiMa_15min);
                    indicators.setRsi_15min_lt_rsi_ma(rsi_15min < rsiMa_15min);
                } else {
                    indicators.setRsi_15min_gt_rsi_ma(null);
                    indicators.setRsi_15min_lt_rsi_ma(null);
                }

                // RSI Divergence calculation for 15min
                if (fifteenMinSeries.getBarCount() >= 30) {
                    boolean bullishDivergence = rsiIndicator.isRsiDivergence(fifteenMinSeries, 14, 20, DivergenceType.BULLISH);
                    boolean bearishDivergence = rsiIndicator.isRsiDivergence(fifteenMinSeries, 14, 20, DivergenceType.BEARISH);
                    indicators.setRsi_bullish_divergence_15min(bullishDivergence);
                    indicators.setRsi_bearish_divergence_15min(bearishDivergence);
                    if (bullishDivergence || bearishDivergence) {
                        log.info("RSI Divergence 15min - Bullish: {}, Bearish: {}", bullishDivergence, bearishDivergence);
                    }
                } else {
                    indicators.setRsi_bullish_divergence_15min(null);
                    indicators.setRsi_bearish_divergence_15min(null);
                }
            } else {
                log.debug("15min BarSeries insufficient data - BarCount: {}", fifteenMinSeries != null ? fifteenMinSeries.getBarCount() : 0);
                indicators.setRsi_15min_gt_80(null);
                indicators.setRsi_15min_lt_20(null);
                indicators.setRsi_15min_gt_60(null);
                indicators.setRsi_15min_lt_40(null);
                indicators.setRsi_15min_gt_rsi_ma(null);
                indicators.setRsi_15min_lt_rsi_ma(null);
                indicators.setRsi_bullish_divergence_15min(null);
                indicators.setRsi_bearish_divergence_15min(null);
            }

        } catch (Exception e) {
            log.error("Error flattening RSI indicators", e);
            // Set all to null on error
            indicators.setRsi_1min_gt_80(null);
            indicators.setRsi_1min_lt_20(null);
            indicators.setRsi_1min_gt_60(null);
            indicators.setRsi_1min_lt_40(null);
            indicators.setRsi_1min_gt_rsi_ma(null);
            indicators.setRsi_1min_lt_rsi_ma(null);
            indicators.setRsi_bullish_divergence_1min(null);
            indicators.setRsi_bearish_divergence_1min(null);
            indicators.setRsi_5min_gt_80(null);
            indicators.setRsi_5min_lt_20(null);
            indicators.setRsi_5min_gt_60(null);
            indicators.setRsi_5min_lt_40(null);
            indicators.setRsi_5min_gt_rsi_ma(null);
            indicators.setRsi_5min_lt_rsi_ma(null);
            indicators.setRsi_15min_gt_80(null);
            indicators.setRsi_15min_lt_20(null);
            indicators.setRsi_15min_gt_60(null);
            indicators.setRsi_15min_lt_40(null);
            indicators.setRsi_15min_gt_rsi_ma(null);
            indicators.setRsi_15min_lt_rsi_ma(null);
        }
    }

    /**
     * Flatten price action indicators
     */
    public void flattenPriceActionIndicators(FlattenedIndicators indicators, BarSeries oneMinSeries, BarSeries fiveMinSeries, BarSeries fifteenMinSeries, Tick tick) {
        try {
            // Support/Resistance indicators
            if (fiveMinSeries != null && fiveMinSeries.getBarCount() >= 20) {
                try {
                    // Create EMA indicator info for support/resistance calculation
                    var emaInfo = multiEmaIndicator.createEmaInfoForTimeframes(fiveMinSeries, FIVE_MIN);

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

                    // Calculate near support/resistance indicators
                    indicators.setNear_support_resistance_zone(supportResistanceIndicator.isVeryNearSupportResistance(tick.getLastTradedPrice(), supports, resistances));
                    indicators.setNear_round_figure_level(supportResistanceIndicator.isNearRoundFigureLevel(tick.getLastTradedPrice()));
                    indicators.setNear_support_resistance_or_round_figure(supportResistanceIndicator.isNearSupportResistanceOrRoundFigure(tick.getLastTradedPrice(), supports, resistances));

                } catch (Exception e) {
                    log.warn("Error calculating support/resistance levels", e);
                    indicators.setPrice_above_resistance(false);
                    indicators.setPrice_below_support(false);
                    indicators.setNear_support_resistance_zone(false);
                    indicators.setNear_round_figure_level(false);
                    indicators.setNear_support_resistance_or_round_figure(false);
                }
            }

        } catch (Exception e) {
            log.error("Error flattening price action indicators", e);
        }
    }

    /**
     * Flatten candlestick pattern indicators
     */
    public void flattenCandlestickPatternIndicators(FlattenedIndicators indicators, BarSeries oneMinSeries, BarSeries fiveMinSeries, BarSeries fifteenMinSeries) {
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

    /**
     * Process candlestick patterns for a specific timeframe
     */
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
            
            // Directional long body patterns
            setPatternIndicator(indicators, "bullish_long_body", timeframe, CandlestickPattern.isBullishLongBody(currentBar));
            setPatternIndicator(indicators, "bearish_long_body", timeframe, CandlestickPattern.isBearishLongBody(currentBar));

            // Additional patterns
            setPatternIndicator(indicators, "wick_rejection_filter_bullish", timeframe, CandlestickPattern.isBullishWickRejectionFilter(currentBar));
            setPatternIndicator(indicators, "wick_rejection_filter_bearish", timeframe, CandlestickPattern.isWickRejectionFilter(currentBar));

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


            // Bearish patterns
            setPatternIndicator(indicators, "bearish_engulfing", timeframe, CandlestickPattern.isBearishEngulfing(previousBar, currentBar));
            setPatternIndicator(indicators, "bearish_harami", timeframe, CandlestickPattern.isBearishHarami(previousBar, currentBar));


            // Additional patterns
            setPatternIndicator(indicators, "inside_bar_breakout", timeframe, CandlestickPattern.isInsideBarBreakout(previousBar, currentBar));
            setPatternIndicator(indicators, "inside_bar_breakdown", timeframe, CandlestickPattern.isInsideBarBreakdown(previousBar, currentBar));
            
            // Current candle crossed previous high/low patterns (only for 5min timeframe)
            if ("5min".equals(timeframe)) {
                setPatternIndicator(indicators, "current_candle_crossed_above_prev_high", timeframe, CandlestickPattern.isCurrentCandleCrossedAbovePrevHigh(previousBar, currentBar));
                setPatternIndicator(indicators, "current_candle_crossed_below_prev_low", timeframe, CandlestickPattern.isCurrentCandleCrossedBelowPrevLow(previousBar, currentBar));
            }
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

    /**
     * Set pattern indicator for a specific timeframe
     */
    private void setPatternIndicator(FlattenedIndicators indicators, String patternName, String timeframe, boolean value) {
        String fieldName = patternName + "_" + timeframe;

        switch (fieldName) {
            // Bullish patterns
            case "bullish_engulfing_1min":
                indicators.setBullish_engulfing_1min(value);
                break;
            case "bullish_engulfing_3min":
                indicators.setBullish_engulfing_3min(value);
                break;
            case "bullish_engulfing_5min":
                indicators.setBullish_engulfing_5min(value);
                break;
            case "bullish_harami_1min":
                indicators.setBullish_harami_1min(value);
                break;
            case "bullish_harami_3min":
                indicators.setBullish_harami_3min(value);
                break;
            case "bullish_harami_5min":
                indicators.setBullish_harami_5min(value);
                break;
            case "bullish_morning_star_1min":
                indicators.setBullish_morning_star_1min(value);
                break;
            case "bullish_morning_star_3min":
                indicators.setBullish_morning_star_3min(value);
                break;
            case "bullish_morning_star_5min":
                indicators.setBullish_morning_star_5min(value);
                break;
            case "hammer_1min":
                indicators.setHammer_1min(value);
                break;
            case "hammer_3min":
                indicators.setHammer_3min(value);
                break;
            case "hammer_5min":
                indicators.setHammer_5min(value);
                break;
            case "inverted_hammer_1min":
                indicators.setInverted_hammer_1min(value);
                break;
            case "inverted_hammer_3min":
                indicators.setInverted_hammer_3min(value);
                break;
            case "inverted_hammer_5min":
                indicators.setInverted_hammer_5min(value);
                break;
            case "bullish_marubozu_1min":
                indicators.setBullish_marubozu_1min(value);
                break;
            case "bullish_marubozu_3min":
                indicators.setBullish_marubozu_3min(value);
                break;
            case "bullish_marubozu_5min":
                indicators.setBullish_marubozu_5min(value);
                break;
            case "long_lower_shadow_1min":
                indicators.setLong_lower_shadow_1min(value);
                break;
            case "long_lower_shadow_3min":
                indicators.setLong_lower_shadow_3min(value);
                break;
            case "long_lower_shadow_5min":
                indicators.setLong_lower_shadow_5min(value);
                break;

            // Bearish patterns
            case "bearish_engulfing_1min":
                indicators.setBearish_engulfing_1min(value);
                break;
            case "bearish_engulfing_3min":
                indicators.setBearish_engulfing_3min(value);
                break;
            case "bearish_engulfing_5min":
                indicators.setBearish_engulfing_5min(value);
                break;
            case "bearish_harami_1min":
                indicators.setBearish_harami_1min(value);
                break;
            case "bearish_harami_3min":
                indicators.setBearish_harami_3min(value);
                break;
            case "bearish_harami_5min":
                indicators.setBearish_harami_5min(value);
                break;
            case "bearish_evening_star_1min":
                indicators.setBearish_evening_star_1min(value);
                break;
            case "bearish_evening_star_3min":
                indicators.setBearish_evening_star_3min(value);
                break;
            case "bearish_evening_star_5min":
                indicators.setBearish_evening_star_5min(value);
                break;
            case "shooting_star_1min":
                indicators.setShooting_star_1min(value);
                break;
            case "shooting_star_3min":
                indicators.setShooting_star_3min(value);
                break;
            case "shooting_star_5min":
                indicators.setShooting_star_5min(value);
                break;
            case "hanging_man_1min":
                indicators.setHanging_man_1min(value);
                break;
            case "hanging_man_3min":
                indicators.setHanging_man_3min(value);
                break;
            case "hanging_man_5min":
                indicators.setHanging_man_5min(value);
                break;
            case "bearish_marubozu_1min":
                indicators.setBearish_marubozu_1min(value);
                break;
            case "bearish_marubozu_3min":
                indicators.setBearish_marubozu_3min(value);
                break;
            case "bearish_marubozu_5min":
                indicators.setBearish_marubozu_5min(value);
                break;
            case "long_upper_shadow_1min":
                indicators.setLong_upper_shadow_1min(value);
                break;
            case "long_upper_shadow_3min":
                indicators.setLong_upper_shadow_3min(value);
                break;
            case "long_upper_shadow_5min":
                indicators.setLong_upper_shadow_5min(value);
                break;

            // Neutral patterns
            case "doji_1min":
                indicators.setDoji_1min(value);
                break;
            case "doji_3min":
                indicators.setDoji_3min(value);
                break;
            case "doji_5min":
                indicators.setDoji_5min(value);
                break;
            case "spinning_top_1min":
                indicators.setSpinning_top_1min(value);
                break;
            case "spinning_top_3min":
                indicators.setSpinning_top_3min(value);
                break;
            case "spinning_top_5min":
                indicators.setSpinning_top_5min(value);
                break;
            case "marubozu_1min":
                indicators.setMarubozu_1min(value);
                break;
            case "marubozu_3min":
                indicators.setMarubozu_3min(value);
                break;
            case "marubozu_5min":
                indicators.setMarubozu_5min(value);
                break;
            case "long_body_1min":
                indicators.setLong_body_1min(value);
                break;
            case "long_body_3min":
                indicators.setLong_body_3min(value);
                break;
            case "long_body_5min":
                indicators.setLong_body_5min(value);
                break;
            case "bullish_long_body_1min":
                indicators.setBullish_long_body_1min(value);
                break;
            case "bullish_long_body_3min":
                indicators.setBullish_long_body_3min(value);
                break;
            case "bullish_long_body_5min":
                indicators.setBullish_long_body_5min(value);
                break;
            case "bearish_long_body_1min":
                indicators.setBearish_long_body_1min(value);
                break;
            case "bearish_long_body_3min":
                indicators.setBearish_long_body_3min(value);
                break;
            case "bearish_long_body_5min":
                indicators.setBearish_long_body_5min(value);
                break;
            case "short_body_1min":
                indicators.setShort_body_1min(value);
                break;
            case "short_body_3min":
                indicators.setShort_body_3min(value);
                break;
            case "short_body_5min":
                indicators.setShort_body_5min(value);
                break;

            // Candle color patterns
            case "green_candle_1min":
                indicators.setGreen_candle_1min(value);
                break;
            case "green_candle_3min":
                indicators.setGreen_candle_3min(value);
                break;
            case "green_candle_5min":
                indicators.setGreen_candle_5min(value);
                break;
            case "red_candle_1min":
                indicators.setRed_candle_1min(value);
                break;
            case "red_candle_3min":
                indicators.setRed_candle_3min(value);
                break;
            case "red_candle_5min":
                indicators.setRed_candle_5min(value);
                break;

            // Additional patterns
            case "inside_bar_breakout_1min":
                indicators.setInside_bar_breakout_1min(value);
                break;
            case "inside_bar_breakout_3min":
                indicators.setInside_bar_breakout_3min(value);
                break;
            case "inside_bar_breakout_5min":
                indicators.setInside_bar_breakout_5min(value);
                break;
            case "inside_bar_breakdown_1min":
                indicators.setInside_bar_breakdown_1min(value);
                break;
            case "inside_bar_breakdown_3min":
                indicators.setInside_bar_breakdown_3min(value);
                break;
            case "inside_bar_breakdown_5min":
                indicators.setInside_bar_breakdown_5min(value);
                break;
            case "wick_rejection_filter_bearish_1min":
                indicators.setWick_rejection_filter_bearish_1min(value);
                break;
            case "wick_rejection_filter_bearish_3min":
                indicators.setWick_rejection_filter_bearish_3min(value);
                break;
            case "wick_rejection_filter_bearish_5min":
                indicators.setWick_rejection_filter_bearish_5min(value);
                break;
            case "wick_rejection_filter_bullish_1min":
                indicators.setWick_rejection_filter_bullish_1min(value);
                break;
            case "wick_rejection_filter_bullish_3min":
                indicators.setWick_rejection_filter_bullish_3min(value);
                break;
            case "wick_rejection_filter_bullish_5min":
                indicators.setWick_rejection_filter_bullish_5min(value);
                break;
            case "current_candle_crossed_above_prev_high_5min":
                indicators.setCurrent_candle_crossed_above_prev_high_5min(value);
                break;
            case "current_candle_crossed_below_prev_low_5min":
                indicators.setCurrent_candle_crossed_below_prev_low_5min(value);
                break;
        }
    }

    /**
     * Calculate futuresignals based on indicators
     */
    public FuturesignalData calculateFuturesignals(FlattenedIndicators indicators) {
        // Enhanced futuresignal calculation using correct RSI thresholds
        boolean oneMinBullish = indicators.getEma5_1min_gt_ema34_1min() != null &&
                indicators.getEma5_1min_gt_ema34_1min() &&
                indicators.getRsi_1min_gt_60() != null &&
                indicators.getRsi_1min_gt_60();

        boolean fiveMinBullish = indicators.getEma5_5min_gt_ema34_5min() != null &&
                indicators.getEma5_5min_gt_ema34_5min() &&
                indicators.getRsi_5min_gt_60() != null &&
                indicators.getRsi_5min_gt_60();

        boolean fifteenMinBullish = indicators.getEma5_15min_gt_ema34_15min() != null &&
                indicators.getEma5_15min_gt_ema34_15min() &&
                indicators.getRsi_15min_gt_60() != null &&
                indicators.getRsi_15min_gt_60();

        boolean oneMinBearish = indicators.getEma5_1min_gt_ema34_1min() != null &&
                !indicators.getEma5_1min_gt_ema34_1min() &&
                indicators.getRsi_1min_lt_40() != null &&
                indicators.getRsi_1min_lt_40();

        boolean fiveMinBearish = indicators.getEma5_5min_gt_ema34_5min() != null &&
                !indicators.getEma5_5min_gt_ema34_5min() &&
                indicators.getRsi_5min_lt_40() != null &&
                indicators.getRsi_5min_lt_40();

        boolean fifteenMinBearish = indicators.getEma5_15min_gt_ema34_15min() != null &&
                !indicators.getEma5_15min_gt_ema34_15min() &&
                indicators.getRsi_15min_lt_40() != null &&
                indicators.getRsi_15min_lt_40();

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
     * Enhance volume indicators with future tick data
     */
    public void enhanceVolumeIndicatorsWithFutureData(FlattenedIndicators indicators, Tick futureTick) {
        try {
            // Get volume data from future tick
            long futureVolume = futureTick.getVolumeTradedToday();

            // Get Nifty index token for comparison
            String niftyIndexToken = indicators.getInstrumentToken(); // Assuming this is the index token
            String niftyFutureToken = String.valueOf(futureTick.getInstrumentToken());

            // Use enhanced volume analysis with correct volume data
            PriceVolumeSurgeIndicator.NiftyVolumeAnalysis volumeAnalysis =
                    priceVolumeSurgeIndicator.analyzeNiftyVolume(niftyFutureToken, futureVolume);

            if (volumeAnalysis != null) {
                // Update volume surge flags based on enhanced analysis
                PriceVolumeSurgeIndicator.VolumeSurgeResult indexSurge = volumeAnalysis.getIndexSurge();
                PriceVolumeSurgeIndicator.VolumeSurgeResult futureSurge = volumeAnalysis.getFutureSurge();

                // Use the future surge since index surge will always be 0 (no volume)

                // Update indicators based on enhanced analysis
                indicators.setVolume_surge_multiplier(futureSurge.getVolumeMultiplier());
                indicators.setVolume_1min_surge(futureSurge.isSurge());
                indicators.setVolume_5min_surge(futureSurge.isSurge());
                indicators.setVolume_15min_surge(futureSurge.isSurge());

                // Set new combined price-volume indicators for all timeframes
                if (futureSurge.isSurge()) {
                    // Analyze price-volume direction for each timeframe
                    PriceVolumeSurgeIndicator.PriceVolumeDirectionResult result1min = 
                        priceVolumeSurgeIndicator.analyzePriceVolumeDirection(niftyFutureToken, ONE_MIN);
                    PriceVolumeSurgeIndicator.PriceVolumeDirectionResult result5min = 
                        priceVolumeSurgeIndicator.analyzePriceVolumeDirection(niftyFutureToken, FIVE_MIN);
                    PriceVolumeSurgeIndicator.PriceVolumeDirectionResult result15min = 
                        priceVolumeSurgeIndicator.analyzePriceVolumeDirection(niftyFutureToken, FIFTEEN_MIN);
                    
                    // Set the combined indicators
                    indicators.setPrice_volume_bullish_surge_1min(result1min.isBullishSurge());
                    indicators.setPrice_volume_bullish_surge_5min(result5min.isBullishSurge());
                    indicators.setPrice_volume_bullish_surge_15min(result15min.isBullishSurge());
                    
                    indicators.setPrice_volume_bearish_surge_1min(result1min.isBearishSurge());
                    indicators.setPrice_volume_bearish_surge_5min(result5min.isBearishSurge());
                    indicators.setPrice_volume_bearish_surge_15min(result15min.isBearishSurge());
                } else {
                    // No volume surge, set all combined indicators to false
                    indicators.setPrice_volume_bullish_surge_1min(false);
                    indicators.setPrice_volume_bullish_surge_5min(false);
                    indicators.setPrice_volume_bullish_surge_15min(false);
                    
                    indicators.setPrice_volume_bearish_surge_1min(false);
                    indicators.setPrice_volume_bearish_surge_5min(false);
                    indicators.setPrice_volume_bearish_surge_15min(false);
                }

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
                    double volumeMultiplier = Math.min(futureVolume / 1000000.0, scoringConfigService.getScoringConfig().getQualityScoring().getVolumeQuality().getVolumeMultiplierThreshold());
                    indicators.setVolume_surge_multiplier(volumeMultiplier);

                    if (volumeMultiplier >= scoringConfigService.getHighVolumeMultiplier()) {
                        indicators.setVolume_1min_surge(true);
                        indicators.setVolume_5min_surge(true);
                    } else if (volumeMultiplier >= scoringConfigService.getSurgeMultiplier()) {
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
     * Check if market conditions are bullish for CALL entries
     */
    public boolean isMarketConditionBullish(FlattenedIndicators indicators, double ratio) {
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

        // RSI conditions (bullish when RSI > 60)
        if (Boolean.TRUE.equals(indicators.getRsi_5min_gt_60())) bullishSignals++;
        if (Boolean.TRUE.equals(indicators.getRsi_1min_gt_60())) bullishSignals++;
        if (Boolean.TRUE.equals(indicators.getRsi_15min_gt_60())) bullishSignals++;
        totalSignals += 3;

        // Candlestick conditions (bullish patterns)
        if (Boolean.TRUE.equals(indicators.getGreen_candle_5min())) bullishSignals++;
        if (Boolean.TRUE.equals(indicators.getGreen_candle_1min())) bullishSignals++;
        totalSignals += 2;

        // Require at least 60% of signals to be bullish
        return (double) bullishSignals / totalSignals >= ratio;
    }

    /**
     * Check if market conditions are bearish for PUT entries
     */
    public boolean isMarketConditionBearish(FlattenedIndicators indicators, double ratio) {
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

        // RSI conditions (bearish when RSI < 40)
        if (Boolean.TRUE.equals(indicators.getRsi_5min_lt_40())) bearishSignals++;
        if (Boolean.TRUE.equals(indicators.getRsi_1min_lt_40())) bearishSignals++;
        if (Boolean.TRUE.equals(indicators.getRsi_15min_lt_40())) bearishSignals++;
        totalSignals += 3;

        // Candlestick conditions (bearish patterns)
        if (Boolean.TRUE.equals(indicators.getRed_candle_5min())) bearishSignals++;
        if (Boolean.TRUE.equals(indicators.getRed_candle_1min())) bearishSignals++;
        totalSignals += 2;

        // Require at least 60% of signals to be bearish
        return (double) bearishSignals / totalSignals >= ratio;
    }


    /**
     * Enhance OI indicators using future tick data (real OI data) instead of index data.
     * Uses OIIndicator to classify signals into 4 cases (long buildup, short buildup, short covering, long unwinding).
     */
    public void enhanceOIIndicatorsWithFutureData(
            FlattenedIndicators indicators,
            Tick futureTick,
            TickDataManager tickDataManager) {

        try {
            String futureToken = String.valueOf(futureTick.getInstrumentToken());

            // Get future BarSeries for price data
            BarSeries oneMinSeries = tickDataManager.getBarSeriesForTimeFrame(futureToken, ONE_MIN);
            BarSeries fiveMinSeries = tickDataManager.getBarSeriesForTimeFrame(futureToken, FIVE_MIN);
            BarSeries fifteenMinSeries = tickDataManager.getBarSeriesForTimeFrame(futureToken, FIFTEEN_MIN);

            double currentOI = futureTick.getOi();

            log.info("Enhancing OI indicators - Token: {}, Current OI: {}",
                    futureToken, currentOI);

            // Process OI signals per timeframe
            processOiSignalsForTimeframe(indicators, futureToken, currentOI, oneMinSeries, "1min");
            processOiSignalsForTimeframe(indicators, futureToken, currentOI, fiveMinSeries, "5min");
            processOiSignalsForTimeframe(indicators, futureToken, currentOI, fifteenMinSeries, "15min");

            log.info("OI signals enhanced with future data successfully");

        } catch (Exception e) {
            log.error("Error enhancing OI signals with future data", e);
        }
    }

    private void processOiSignalsForTimeframe(
            FlattenedIndicators indicators,
            String futureToken,
            double currentOI,
            BarSeries series,
            String timeframe) {

        if (series == null || series.getBarCount() < 3) {
            log.warn("{} Future BarSeries insufficient for OI", timeframe);
            setNullOiSignals(indicators, timeframe);
            return;
        }

        try {
            Bar currentBar = series.getLastBar();
            boolean isBullishCandle = currentBar.getClosePrice().isGreaterThan(currentBar.getOpenPrice());
            boolean isBearishCandle = currentBar.getClosePrice().isLessThan(currentBar.getOpenPrice());

            // Call OIIndicator
            OIIndicator.OISignal signal = oiIndicator.calculateOiSignal(
                    futureToken, currentOI, isBullishCandle, isBearishCandle);

            // Map OISignal -> FlattenedIndicators
            switch (timeframe) {
                case "1min":
                    indicators.setOi_bullish_signal_1min(
                            signal == OIIndicator.OISignal.LONG_BUILDUP || signal == OIIndicator.OISignal.SHORT_COVERING);
                    indicators.setOi_bearish_signal_1min(
                            signal == OIIndicator.OISignal.SHORT_BUILDUP || signal == OIIndicator.OISignal.LONG_UNWINDING);
                    break;

                case "5min":
                    indicators.setOi_bullish_signal_5min(
                            signal == OIIndicator.OISignal.LONG_BUILDUP || signal == OIIndicator.OISignal.SHORT_COVERING);
                    indicators.setOi_bearish_signal_5min(
                            signal == OIIndicator.OISignal.SHORT_BUILDUP || signal == OIIndicator.OISignal.LONG_UNWINDING);
                    break;

                case "15min":
                    indicators.setOi_bullish_signal_15min(
                            signal == OIIndicator.OISignal.LONG_BUILDUP || signal == OIIndicator.OISignal.SHORT_COVERING);
                    indicators.setOi_bearish_signal_15min(
                            signal == OIIndicator.OISignal.SHORT_BUILDUP || signal == OIIndicator.OISignal.LONG_UNWINDING);
                    break;
            }

            log.debug("{} OI signal classified: {}", timeframe, signal);

        } catch (Exception e) {
            log.error("Error calculating {} OI signals", timeframe, e);
            setNullOiSignals(indicators, timeframe);
        }
    }

    private void setNullOiSignals(FlattenedIndicators indicators, String timeframe) {
        switch (timeframe) {
            case "1min":
                indicators.setOi_bullish_signal_1min(null);
                indicators.setOi_bearish_signal_1min(null);
                break;
            case "5min":
                indicators.setOi_bullish_signal_5min(null);
                indicators.setOi_bearish_signal_5min(null);
                break;
            case "15min":
                indicators.setOi_bullish_signal_15min(null);
                indicators.setOi_bearish_signal_15min(null);
                break;
        }
    }


}
