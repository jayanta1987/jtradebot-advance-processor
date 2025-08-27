package com.jtradebot.processor.service.logging;

import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.indicator.MultiEmaIndicator;
import com.jtradebot.processor.indicator.RsiIndicator;
import com.jtradebot.processor.model.indicator.EmaInfo;
import com.jtradebot.processor.manager.TickDataManager;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;

import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndicatorStatusService {

    private final TickDataManager tickDataManager;

    /**
     * Get detailed EMA status with actual values
     */
    public String getDetailedEmaStatus(FlattenedIndicators indicators, Tick indexTick) {
        if (indicators == null) return "EMA:---";
        
        try {
            // Get BarSeries for different timeframes
            BarSeries oneMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(indexTick.getInstrumentToken()), ONE_MIN);
            BarSeries fiveMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(indexTick.getInstrumentToken()), FIVE_MIN);
            BarSeries fifteenMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(indexTick.getInstrumentToken()), FIFTEEN_MIN);
            
            // Calculate EMA values using MultiEmaIndicator
            MultiEmaIndicator multiEmaIndicator = new MultiEmaIndicator();
            
            String ema1min = "---";
            String ema5min = "---";
            String ema15min = "---";
            
            if (oneMinSeries != null && oneMinSeries.getBarCount() >= 20) {
                EmaInfo emaInfo = multiEmaIndicator.calculateEmaValues(oneMinSeries, ONE_MIN);
                if (emaInfo != null) {
                    double ema9 = emaInfo.getEma9() != null ? emaInfo.getEma9() : 0.0;
                    double ema20 = emaInfo.getEma20() != null ? emaInfo.getEma20() : 0.0;
                    ema1min = String.format("%.1f/%.1f", ema9, ema20);
                }
            }
            
            if (fiveMinSeries != null && fiveMinSeries.getBarCount() >= 20) {
                EmaInfo emaInfo = multiEmaIndicator.calculateEmaValues(fiveMinSeries, FIVE_MIN);
                if (emaInfo != null) {
                    double ema9 = emaInfo.getEma9() != null ? emaInfo.getEma9() : 0.0;
                    double ema20 = emaInfo.getEma20() != null ? emaInfo.getEma20() : 0.0;
                    ema5min = String.format("%.1f/%.1f", ema9, ema20);
                }
            }
            
            if (fifteenMinSeries != null && fifteenMinSeries.getBarCount() >= 20) {
                EmaInfo emaInfo = multiEmaIndicator.calculateEmaValues(fifteenMinSeries, FIFTEEN_MIN);
                if (emaInfo != null) {
                    double ema9 = emaInfo.getEma9() != null ? emaInfo.getEma9() : 0.0;
                    double ema20 = emaInfo.getEma20() != null ? emaInfo.getEma20() : 0.0;
                    ema15min = String.format("%.1f/%.1f", ema9, ema20);
                }
            }
            
            return String.format("EMA:%s|%s|%s", ema1min, ema5min, ema15min);
            
        } catch (Exception e) {
            log.debug("Error getting detailed EMA status: {}", e.getMessage());
            return "EMA:---";
        }
    }

    /**
     * Get detailed RSI status with actual values
     */
    public String getDetailedRsiStatus(FlattenedIndicators indicators, Tick indexTick) {
        if (indicators == null) return "RSI:---";
        
        try {
            // Get BarSeries for different timeframes
            BarSeries oneMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(indexTick.getInstrumentToken()), ONE_MIN);
            BarSeries fiveMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(indexTick.getInstrumentToken()), FIVE_MIN);
            BarSeries fifteenMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(indexTick.getInstrumentToken()), FIFTEEN_MIN);
            
            // Calculate RSI values using RsiIndicator
            RsiIndicator rsiIndicator = new RsiIndicator();
            
            String rsi1min = "---";
            String rsi5min = "---";
            String rsi15min = "---";
            
            if (oneMinSeries != null && oneMinSeries.getBarCount() >= 14) {
                Double rsiValue = rsiIndicator.getRsiValue(oneMinSeries, 14);
                if (rsiValue != null) {
                    rsi1min = String.format("%.1f", rsiValue);
                }
            }
            
            if (fiveMinSeries != null && fiveMinSeries.getBarCount() >= 14) {
                Double rsiValue = rsiIndicator.getRsiValue(fiveMinSeries, 14);
                if (rsiValue != null) {
                    rsi5min = String.format("%.1f", rsiValue);
                }
            }
            
            if (fifteenMinSeries != null && fifteenMinSeries.getBarCount() >= 14) {
                Double rsiValue = rsiIndicator.getRsiValue(fifteenMinSeries, 14);
                if (rsiValue != null) {
                    rsi15min = String.format("%.1f", rsiValue);
                }
            }
            
            return String.format("RSI:%s|%s|%s", rsi1min, rsi5min, rsi15min);
            
        } catch (Exception e) {
            log.debug("Error getting detailed RSI status: {}", e.getMessage());
            return "RSI:---";
        }
    }

    /**
     * Get Volume status in concise format
     */
    public String getVolumeStatus(FlattenedIndicators indicators) {
        if (indicators == null) return "VOL:---";
        
        int surgeCount = 0;
        // Check for volume surge conditions
        if (indicators.getVolume_1min_surge() != null && indicators.getVolume_1min_surge()) surgeCount++;
        if (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) surgeCount++;
        if (indicators.getVolume_15min_surge() != null && indicators.getVolume_15min_surge()) surgeCount++;
        
        String multiplier = "";
        if (indicators.getVolume_surge_multiplier() != null) {
            multiplier = String.format("(%.1fx)", indicators.getVolume_surge_multiplier());
        }
        
        return String.format("VOL:%d/3%s", surgeCount, multiplier);
    }

    /**
     * Get entry signal in concise format
     */
    public String getEntrySignal(boolean shouldCall, boolean shouldPut) {
        if (shouldCall && shouldPut) return "CALL+PUT";
        if (shouldCall) return "CALL";
        if (shouldPut) return "PUT";
        return "NONE";
    }

    /**
     * Calculate entry proximity - how close to entry conditions
     */
    public String getEntryProximity(FlattenedIndicators indicators, Tick indexTick) {
        if (indicators == null) return "PROX:---";
        
        try {
            // Get current price
            double currentPrice = indexTick.getLastTradedPrice();
            
            // Get BarSeries for different timeframes
            BarSeries oneMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(indexTick.getInstrumentToken()), ONE_MIN);
            BarSeries fiveMinSeries = tickDataManager.getBarSeriesForTimeFrame(String.valueOf(indexTick.getInstrumentToken()), FIVE_MIN);
            
            // Calculate EMA values
            MultiEmaIndicator multiEmaIndicator = new MultiEmaIndicator();
            RsiIndicator rsiIndicator = new RsiIndicator();
            
            // Track conditions for CALL and PUT
            int callConditions = 0;
            int putConditions = 0;
            int totalConditions = 0;
            
            // Check 1min timeframe
            if (oneMinSeries != null && oneMinSeries.getBarCount() >= 20) {
                totalConditions++;
                
                // EMA crossover check
                EmaInfo emaInfo = multiEmaIndicator.calculateEmaValues(oneMinSeries, ONE_MIN);
                if (emaInfo != null && emaInfo.getEma9() != null && emaInfo.getEma20() != null) {
                    if (emaInfo.getEma9() > emaInfo.getEma20()) {
                        callConditions++;
                    } else {
                        putConditions++;
                    }
                }
                
                // RSI check
                Double rsiValue = rsiIndicator.getRsiValue(oneMinSeries, 14);
                if (rsiValue != null) {
                    if (rsiValue > 60) {
                        callConditions++;
                    } else if (rsiValue < 40) {
                        putConditions++;
                    }
                }
            }
            
            // Check 5min timeframe
            if (fiveMinSeries != null && fiveMinSeries.getBarCount() >= 20) {
                totalConditions++;
                
                // EMA crossover check
                EmaInfo emaInfo = multiEmaIndicator.calculateEmaValues(fiveMinSeries, FIVE_MIN);
                if (emaInfo != null && emaInfo.getEma9() != null && emaInfo.getEma20() != null) {
                    if (emaInfo.getEma9() > emaInfo.getEma20()) {
                        callConditions++;
                    } else {
                        putConditions++;
                    }
                }
                
                // RSI check
                Double rsiValue = rsiIndicator.getRsiValue(fiveMinSeries, 14);
                if (rsiValue != null) {
                    if (rsiValue > 60) {
                        callConditions++;
                    } else if (rsiValue < 40) {
                        putConditions++;
                    }
                }
            }
            
            // Volume surge check
            if (indicators.getVolume_1min_surge() != null && indicators.getVolume_1min_surge()) {
                callConditions++;
                putConditions++;
            }
            if (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) {
                callConditions++;
                putConditions++;
            }
            
            // Calculate proximity percentages
            double callProximity = totalConditions > 0 ? (double) callConditions / totalConditions * 100 : 0;
            double putProximity = totalConditions > 0 ? (double) putConditions / totalConditions * 100 : 0;
            
            // Determine which direction is closer to entry
            String direction = callProximity > putProximity ? "CALL" : "PUT";
            double maxProximity = Math.max(callProximity, putProximity);
            
            // Format proximity string
            if (maxProximity >= 80) {
                return String.format("PROX:%s %.0f%% 🔥", direction, maxProximity);
            } else if (maxProximity >= 60) {
                return String.format("PROX:%s %.0f%% ⚡", direction, maxProximity);
            } else if (maxProximity >= 40) {
                return String.format("PROX:%s %.0f%% ⚠️", direction, maxProximity);
            } else {
                return String.format("PROX:%s %.0f%%", direction, maxProximity);
            }
            
        } catch (Exception e) {
            log.debug("Error calculating entry proximity: {}", e.getMessage());
            return "PROX:---";
        }
    }
}
