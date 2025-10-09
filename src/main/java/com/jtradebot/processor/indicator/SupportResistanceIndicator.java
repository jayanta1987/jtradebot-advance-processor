package com.jtradebot.processor.indicator;


import com.jtradebot.processor.model.indicator.EmaIndicatorInfo;
import com.jtradebot.processor.model.indicator.Resistance;
import com.jtradebot.processor.model.indicator.Support;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.jtradebot.processor.model.enums.TrendEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.EMAIndicator;

import java.util.*;
import java.util.Objects;

/**
 * Enhanced Support and Resistance Indicator using proper technical analysis
 * 
 * This indicator identifies support and resistance levels based on:
 * 
 * 1. SWING HIGHS/LOWS (Pivot Points):
 *    - Swing High: A price peak higher than N candles before and after it
 *    - Swing Low: A price trough lower than N candles before and after it
 *    - These represent actual turning points where price reversed
 * 
 * 2. TREND LINES (Dynamic Support/Resistance):
 *    - Uptrend Lines: Connect series of higher lows (dynamic support)
 *    - Downtrend Lines: Connect series of lower highs (dynamic resistance)
 *    - These are diagonal lines that move with price action
 * 
 * 3. CLUSTERING:
 *    - Multiple swing points close together (within CLUSTER_TOLERANCE) are merged
 *    - Creates "zones" rather than exact price levels
 *    - Stronger zones = more price touches = more significant levels
 * 
 * 4. EMA LEVELS:
 *    - EMAs act as dynamic moving support/resistance
 *    - Complement the static swing-based levels
 * 
 * This approach differs from simple high/low detection by identifying actual
 * market structure (higher highs, lower lows, trend lines, etc.) rather than just extremes.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SupportResistanceIndicator {
    public static Integer MIN_SUPPORT_RESISTANCE_DISTANCE_BUFFER = 10;
    public static Integer VERY_MIN_SUPPORT_RESISTANCE_DISTANCE_BUFFER = 5;

    // Cluster tolerance: levels within this range are considered the same level
    private static final int CLUSTER_TOLERANCE = 15;
    
    // Trend line validation thresholds - OPTIMIZED FOR SCALPING (1min/5min timeframes)
    // For swing trading (15min+), consider using 0.5-1.0% breach and 20-25% tolerance
    private static final double TREND_LINE_BREACH_THRESHOLD = 0.10;  // 0.10% = ~25 points at Nifty 25,000
    private static final double TREND_LINE_TOLERANCE = 0.10;         // Allow 10% of bars to breach before invalidating

    /**
     * Calculate support levels based on swing lows (pivot points)
     * A swing low is a price point lower than surrounding candles on both sides
     */
    public Set<Support> calculateSupports(CandleTimeFrameEnum timeFrame, BarSeries barSeriesForTimeFrame, double ltp, EmaIndicatorInfo emaIndicatorInfo, int[] periods) {
        Set<Support> supports = new TreeSet<>(Comparator.naturalOrder());

        // Find swing lows for each lookback period
        Arrays.stream(periods).forEach(period -> {
            int lookback = Math.min(5, period / 10); // Dynamic lookback based on period
            List<Integer> swingLowIndices = findSwingLows(barSeriesForTimeFrame, period, lookback);
            
            // Convert swing lows to support levels
            for (Integer swingIndex : swingLowIndices) {
                int swingLowPrice = barSeriesForTimeFrame.getBar(swingIndex).getLowPrice().intValue();
                
                // Only add if it's below current price (support should be below price)
                if (swingLowPrice < ltp) {
            Support support = new Support();
            support.setCandleTimeFrame(timeFrame);
            support.setEma(false);
                    support.setSupportValue(swingLowPrice);
                supports.add(support);
            }
            }
        });

        // Add EMA levels as dynamic support (these act as moving support levels)
        EMAIndicator[] emaIndicators = new EMAIndicator[]{emaIndicatorInfo.getEma9(), emaIndicatorInfo.getEma14(),
                emaIndicatorInfo.getEma20(), emaIndicatorInfo.getEma34(), emaIndicatorInfo.getEma200()};

        Arrays.stream(emaIndicators)
                .filter(Objects::nonNull)
                .forEach(emaIndicator -> {
                    int emaValue = emaIndicator.getValue(barSeriesForTimeFrame.getEndIndex()).intValue();
                    if (emaValue < ltp) {
                    Support support = new Support();
                    support.setCandleTimeFrame(timeFrame);
                    support.setEma(true);
                        support.setSupportValue(emaValue);
                        supports.add(support);
                    }
                });
        
        // Cluster nearby support levels to identify stronger zones
        Set<Support> clusteredSupports = clusterSupportLevels(supports);
        
        // Add trend line based support (uptrend lines connecting higher lows)
        List<Integer> trendLineSupports = calculateUptrendLineSupport(barSeriesForTimeFrame, periods, ltp);
        for (Integer trendSupport : trendLineSupports) {
            if (trendSupport < ltp) {
                Support support = new Support();
                support.setCandleTimeFrame(timeFrame);
                support.setEma(false); // Trend lines are not EMAs
                support.setSupportValue(trendSupport);
                clusteredSupports.add(support);
            }
        }
        
        log.debug("📊 SUPPORT ANALYSIS - Swing-based: {} zones, Trend lines: {}, Total: {} levels", 
                  clusteredSupports.size() - trendLineSupports.size(), trendLineSupports.size(), clusteredSupports.size());
        
        return clusteredSupports;
    }

    /**
     * Calculate resistance levels based on swing highs (pivot points)
     * A swing high is a price point higher than surrounding candles on both sides
     */
    public Set<Resistance> calculateResistances(CandleTimeFrameEnum timeFrame, BarSeries barSeriesForTimeFrame, double ltp, EmaIndicatorInfo emaIndicatorInfo, int[] periods) {
        Set<Resistance> resistances = new TreeSet<>(Comparator.naturalOrder());

        // Find swing highs for each lookback period
        Arrays.stream(periods).forEach(period -> {
            int lookback = Math.min(5, period / 10); // Dynamic lookback based on period
            List<Integer> swingHighIndices = findSwingHighs(barSeriesForTimeFrame, period, lookback);
            
            // Convert swing highs to resistance levels
            for (Integer swingIndex : swingHighIndices) {
                int swingHighPrice = barSeriesForTimeFrame.getBar(swingIndex).getHighPrice().intValue();
                
                // Only add if it's above current price (resistance should be above price)
                if (swingHighPrice > ltp) {
            Resistance resistance = new Resistance();
            resistance.setCandleTimeFrame(timeFrame);
            resistance.setEma(false);
                    resistance.setResistanceValue(swingHighPrice);
                resistances.add(resistance);
            }
            }
        });

        // Add EMA levels as dynamic resistance (these act as moving resistance levels)
        EMAIndicator[] emaIndicators = new EMAIndicator[]{emaIndicatorInfo.getEma9(), emaIndicatorInfo.getEma14(),
                emaIndicatorInfo.getEma20(), emaIndicatorInfo.getEma34(), emaIndicatorInfo.getEma200()};

        Arrays.stream(emaIndicators)
                .filter(Objects::nonNull)
                .forEach(emaIndicator -> {
                    int emaValue = emaIndicator.getValue(barSeriesForTimeFrame.getEndIndex()).intValue();
                    if (emaValue > ltp) {
                    Resistance resistance = new Resistance();
                    resistance.setCandleTimeFrame(timeFrame);
                    resistance.setEma(true);
                        resistance.setResistanceValue(emaValue);
                        resistances.add(resistance);
                    }
                });

        // Cluster nearby resistance levels to identify stronger zones
        Set<Resistance> clusteredResistances = clusterResistanceLevels(resistances);
        
        // Add trend line based resistance (downtrend lines connecting lower highs)
        List<Integer> trendLineResistances = calculateDowntrendLineResistance(barSeriesForTimeFrame, periods, ltp);
        for (Integer trendResistance : trendLineResistances) {
            if (trendResistance > ltp) {
                Resistance resistance = new Resistance();
                resistance.setCandleTimeFrame(timeFrame);
                resistance.setEma(false); // Trend lines are not EMAs
                resistance.setResistanceValue(trendResistance);
                clusteredResistances.add(resistance);
            }
        }
        
        log.debug("📊 RESISTANCE ANALYSIS - Swing-based: {} zones, Trend lines: {}, Total: {} levels", 
                  clusteredResistances.size() - trendLineResistances.size(), trendLineResistances.size(), clusteredResistances.size());
        
        return clusteredResistances;
    }

    /**
     * Find swing highs (pivot highs) in the price series
     * A swing high is a candle whose high is greater than 'lookback' candles before and after it
     * 
     * @param series The bar series to analyze
     * @param period The lookback period (how many bars to analyze)
     * @param lookback How many bars on each side to compare (e.g., 3 means compare 3 bars before and 3 after)
     * @return List of indices where swing highs occurred
     */
    private List<Integer> findSwingHighs(BarSeries series, int period, int lookback) {
        List<Integer> swingHighs = new ArrayList<>();
        int endIndex = series.getEndIndex();
        int startIndex = Math.max(0, endIndex - period + 1);
        
        // Need at least lookback bars on each side
        for (int i = startIndex + lookback; i <= endIndex - lookback; i++) {
            boolean isSwingHigh = true;
            double currentHigh = series.getBar(i).getHighPrice().doubleValue();
            
            // Check if current bar's high is greater than all bars within lookback range
            for (int j = i - lookback; j <= i + lookback; j++) {
                if (j == i) continue; // Skip the current bar itself
                
                double compareHigh = series.getBar(j).getHighPrice().doubleValue();
                if (compareHigh >= currentHigh) {
                    isSwingHigh = false;
                    break;
                }
            }
            
            if (isSwingHigh) {
                swingHighs.add(i);
                log.debug("Found swing high at index {}: price = {}", i, currentHigh);
            }
        }
        
        return swingHighs;
    }
    
    /**
     * Find swing lows (pivot lows) in the price series
     * A swing low is a candle whose low is less than 'lookback' candles before and after it
     * 
     * @param series The bar series to analyze
     * @param period The lookback period (how many bars to analyze)
     * @param lookback How many bars on each side to compare (e.g., 3 means compare 3 bars before and 3 after)
     * @return List of indices where swing lows occurred
     */
    private List<Integer> findSwingLows(BarSeries series, int period, int lookback) {
        List<Integer> swingLows = new ArrayList<>();
        int endIndex = series.getEndIndex();
        int startIndex = Math.max(0, endIndex - period + 1);

        // Need at least lookback bars on each side
        for (int i = startIndex + lookback; i <= endIndex - lookback; i++) {
            boolean isSwingLow = true;
            double currentLow = series.getBar(i).getLowPrice().doubleValue();
            
            // Check if current bar's low is less than all bars within lookback range
            for (int j = i - lookback; j <= i + lookback; j++) {
                if (j == i) continue; // Skip the current bar itself
                
                double compareLow = series.getBar(j).getLowPrice().doubleValue();
                if (compareLow <= currentLow) {
                    isSwingLow = false;
                    break;
                }
            }
            
            if (isSwingLow) {
                swingLows.add(i);
                log.debug("Found swing low at index {}: price = {}", i, currentLow);
            }
        }
        
        return swingLows;
    }

    /**
     * Cluster nearby support levels into zones
     * Multiple swing lows close to each other indicate a stronger support zone
     * 
     * @param supports Raw support levels from swing lows
     * @return Clustered support levels with duplicates removed
     */
    private Set<Support> clusterSupportLevels(Set<Support> supports) {
        if (supports.isEmpty()) return supports;
        
        Set<Support> clustered = new TreeSet<>(Comparator.naturalOrder());
        List<Support> sortedSupports = new ArrayList<>(supports);
        sortedSupports.sort(Comparator.comparing(Support::getSupportValue));
        
        int i = 0;
        while (i < sortedSupports.size()) {
            Support current = sortedSupports.get(i);
            List<Support> cluster = new ArrayList<>();
            cluster.add(current);
            
            // Find all supports within CLUSTER_TOLERANCE
            int j = i + 1;
            while (j < sortedSupports.size() && 
                   Math.abs(sortedSupports.get(j).getSupportValue() - current.getSupportValue()) <= CLUSTER_TOLERANCE) {
                cluster.add(sortedSupports.get(j));
                j++;
            }
            
            // Use average of cluster as the representative level
            int avgValue = (int) cluster.stream()
                .mapToInt(Support::getSupportValue)
                .average()
                .orElse(current.getSupportValue());
            
            Support clusteredSupport = new Support();
            clusteredSupport.setSupportValue(avgValue);
            clusteredSupport.setCandleTimeFrame(current.getCandleTimeFrame());
            clusteredSupport.setEma(current.isEma());
            clustered.add(clusteredSupport);
            
            // Log stronger support zones (multiple touches)
            if (cluster.size() > 1) {
                log.debug("🔵 STRONG SUPPORT ZONE at {} (tested {} times)", avgValue, cluster.size());
            }
            
            i = j;
        }
        
        return clustered;
    }
    
    /**
     * Cluster nearby resistance levels into zones
     * Multiple swing highs close to each other indicate a stronger resistance zone
     * 
     * @param resistances Raw resistance levels from swing highs
     * @return Clustered resistance levels with duplicates removed
     */
    private Set<Resistance> clusterResistanceLevels(Set<Resistance> resistances) {
        if (resistances.isEmpty()) return resistances;
        
        Set<Resistance> clustered = new TreeSet<>(Comparator.naturalOrder());
        List<Resistance> sortedResistances = new ArrayList<>(resistances);
        sortedResistances.sort(Comparator.comparing(Resistance::getResistanceValue));
        
        int i = 0;
        while (i < sortedResistances.size()) {
            Resistance current = sortedResistances.get(i);
            List<Resistance> cluster = new ArrayList<>();
            cluster.add(current);
            
            // Find all resistances within CLUSTER_TOLERANCE
            int j = i + 1;
            while (j < sortedResistances.size() && 
                   Math.abs(sortedResistances.get(j).getResistanceValue() - current.getResistanceValue()) <= CLUSTER_TOLERANCE) {
                cluster.add(sortedResistances.get(j));
                j++;
            }
            
            // Use average of cluster as the representative level
            int avgValue = (int) cluster.stream()
                .mapToInt(Resistance::getResistanceValue)
                .average()
                .orElse(current.getResistanceValue());
            
            Resistance clusteredResistance = new Resistance();
            clusteredResistance.setResistanceValue(avgValue);
            clusteredResistance.setCandleTimeFrame(current.getCandleTimeFrame());
            clusteredResistance.setEma(current.isEma());
            clustered.add(clusteredResistance);
            
            // Log stronger resistance zones (multiple touches)
            if (cluster.size() > 1) {
                log.debug("🔴 STRONG RESISTANCE ZONE at {} (tested {} times)", avgValue, cluster.size());
            }
            
            i = j;
        }
        
        return clustered;
    }

    /**
     * Calculate uptrend line support levels
     * An uptrend line connects a series of higher lows, acting as dynamic support
     * 
     * @param series Bar series to analyze
     * @param periods Array of lookback periods
     * @param currentPrice Current market price
     * @return List of support levels where uptrend lines currently are
     */
    private List<Integer> calculateUptrendLineSupport(BarSeries series, int[] periods, double currentPrice) {
        List<Integer> trendLineSupports = new ArrayList<>();
        
        for (int period : periods) {
            int lookback = Math.min(3, period / 20); // Smaller lookback for trend line detection
            List<Integer> swingLowIndices = findSwingLows(series, period, lookback);
            
            // Need at least 2 swing lows to draw a trend line
            if (swingLowIndices.size() < 2) continue;
            
            // Find series of higher lows (uptrend)
            for (int i = 0; i < swingLowIndices.size() - 1; i++) {
                int firstIndex = swingLowIndices.get(i);
                double firstLow = series.getBar(firstIndex).getLowPrice().doubleValue();
                
                for (int j = i + 1; j < swingLowIndices.size(); j++) {
                    int secondIndex = swingLowIndices.get(j);
                    double secondLow = series.getBar(secondIndex).getLowPrice().doubleValue();
                    
                    // Check if this forms a higher low (uptrend)
                    if (secondLow > firstLow) {
                        // Calculate trend line parameters: y = mx + b
                        double slope = (secondLow - firstLow) / (secondIndex - firstIndex);
                        double intercept = firstLow - (slope * firstIndex);
                        
                        // Calculate current trend line level at the latest bar
                        int currentIndex = series.getEndIndex();
                        int trendLineLevel = (int) (slope * currentIndex + intercept);
                        
                        // Validate the trend line is still valid (hasn't been broken significantly)
                        boolean isValid = validateUptrendLine(series, firstIndex, secondIndex, slope, intercept);
                        
                        if (isValid && trendLineLevel > 0 && trendLineLevel < currentPrice) {
                            trendLineSupports.add(trendLineLevel);
                            log.debug("📈 UPTREND LINE SUPPORT at {} (slope: {:.2f}, from bar {} to {})", 
                                     trendLineLevel, slope, firstIndex, secondIndex);
                        }
                    }
                }
            }
        }
        
        return trendLineSupports.stream().distinct().collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Calculate downtrend line resistance levels
     * A downtrend line connects a series of lower highs, acting as dynamic resistance
     * 
     * @param series Bar series to analyze
     * @param periods Array of lookback periods
     * @param currentPrice Current market price
     * @return List of resistance levels where downtrend lines currently are
     */
    private List<Integer> calculateDowntrendLineResistance(BarSeries series, int[] periods, double currentPrice) {
        List<Integer> trendLineResistances = new ArrayList<>();
        
        for (int period : periods) {
            int lookback = Math.min(3, period / 20); // Smaller lookback for trend line detection
            List<Integer> swingHighIndices = findSwingHighs(series, period, lookback);
            
            // Need at least 2 swing highs to draw a trend line
            if (swingHighIndices.size() < 2) continue;
            
            // Find series of lower highs (downtrend)
            for (int i = 0; i < swingHighIndices.size() - 1; i++) {
                int firstIndex = swingHighIndices.get(i);
                double firstHigh = series.getBar(firstIndex).getHighPrice().doubleValue();
                
                for (int j = i + 1; j < swingHighIndices.size(); j++) {
                    int secondIndex = swingHighIndices.get(j);
                    double secondHigh = series.getBar(secondIndex).getHighPrice().doubleValue();
                    
                    // Check if this forms a lower high (downtrend)
                    if (secondHigh < firstHigh) {
                        // Calculate trend line parameters: y = mx + b
                        double slope = (secondHigh - firstHigh) / (secondIndex - firstIndex);
                        double intercept = firstHigh - (slope * firstIndex);
                        
                        // Calculate current trend line level at the latest bar
                        int currentIndex = series.getEndIndex();
                        int trendLineLevel = (int) (slope * currentIndex + intercept);
                        
                        // Validate the trend line is still valid (hasn't been broken significantly)
                        boolean isValid = validateDowntrendLine(series, firstIndex, secondIndex, slope, intercept);
                        
                        if (isValid && trendLineLevel > 0 && trendLineLevel > currentPrice) {
                            trendLineResistances.add(trendLineLevel);
                            log.debug("📉 DOWNTREND LINE RESISTANCE at {} (slope: {:.2f}, from bar {} to {})", 
                                     trendLineLevel, slope, firstIndex, secondIndex);
                        }
                    }
                }
            }
        }
        
        return trendLineResistances.stream().distinct().collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Validate an uptrend line by checking if price has significantly broken below it
     * A trend line is considered invalid if price closes significantly below it
     * 
     * For scalping (1min/5min): Uses 0.15% breach threshold (~37 points at Nifty 25,000)
     * This ensures tight, accurate trend lines suitable for quick entries/exits
     */
    private boolean validateUptrendLine(BarSeries series, int startIndex, int endIndex, double slope, double intercept) {
        int breaches = 0;
        int totalBars = 0;
        
        // Check bars between the two swing points and recent bars
        for (int i = startIndex; i <= series.getEndIndex(); i++) {
            double trendLineValue = slope * i + intercept;
            double closePrice = series.getBar(i).getClosePrice().doubleValue();
            
            totalBars++;
            
            // Significant breach: close is more than TREND_LINE_BREACH_THRESHOLD% below trend line
            double breachThreshold = 1.0 - (TREND_LINE_BREACH_THRESHOLD / 100.0);
            if (closePrice < trendLineValue * breachThreshold) {
                breaches++;
            }
        }
        
        // Trend line is valid if less than TREND_LINE_TOLERANCE% of bars breach it
        return totalBars > 0 && ((double) breaches / totalBars) < TREND_LINE_TOLERANCE;
    }
    
    /**
     * Validate a downtrend line by checking if price has significantly broken above it
     * A trend line is considered invalid if price closes significantly above it
     * 
     * For scalping (1min/5min): Uses 0.15% breach threshold (~37 points at Nifty 25,000)
     * This ensures tight, accurate trend lines suitable for quick entries/exits
     */
    private boolean validateDowntrendLine(BarSeries series, int startIndex, int endIndex, double slope, double intercept) {
        int breaches = 0;
        int totalBars = 0;
        
        // Check bars between the two swing points and recent bars
        for (int i = startIndex; i <= series.getEndIndex(); i++) {
            double trendLineValue = slope * i + intercept;
            double closePrice = series.getBar(i).getClosePrice().doubleValue();
            
            totalBars++;
            
            // Significant breach: close is more than TREND_LINE_BREACH_THRESHOLD% above trend line
            double breachThreshold = 1.0 + (TREND_LINE_BREACH_THRESHOLD / 100.0);
            if (closePrice > trendLineValue * breachThreshold) {
                breaches++;
            }
        }
        
        // Trend line is valid if less than TREND_LINE_TOLERANCE% of bars breach it
        return totalBars > 0 && ((double) breaches / totalBars) < TREND_LINE_TOLERANCE;
    }


    public boolean monitorForCallEntry(BarSeries lowerSeries, Set<Support> supportLevels, Set<Resistance> resistanceLevels, TrendEnum trend) {
        int endIndex = lowerSeries.getEndIndex();
        double currentPrice = lowerSeries.getBar(endIndex).getClosePrice().doubleValue();
        if (trend == TrendEnum.UP) {
            for (Support support : supportLevels) {
                if (currentPrice <= support.getSupportValue()) {
                    log.debug("Potential long entry near support level: {}", support);
                    return true;
                }
            }
            for (Resistance resistance : resistanceLevels) {
                if (currentPrice >= resistance.getResistanceValue()) {
                    log.debug("Potential long entry near resistance level: {}", resistance);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean monitorForPutEntry(BarSeries lowerSeries, Set<Support> supportLevels, Set<Resistance> resistanceLevels, TrendEnum trend) {
        int endIndex = lowerSeries.getEndIndex();
        double currentPrice = lowerSeries.getBar(endIndex).getClosePrice().doubleValue();
        if (trend == TrendEnum.DOWN) {
            for (Resistance resistance : resistanceLevels) {
                if (currentPrice >= resistance.getResistanceValue()) {
                    log.debug("Potential short entry near resistance level: {}", resistance);
                    return true;
                }
            }
            for (Support support : supportLevels) {
                if (currentPrice <= support.getSupportValue()) {
                    log.debug("Potential short entry near support level: {}", support);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if price is very near to support/resistance levels (within 5 points)
     * This helps prevent entries in choppy zones near key levels
     */
    public boolean isVeryNearSupportResistance(double ltp, Set<Support> supportLevels, Set<Resistance> resistanceLevels) {
        // Check if price is very close to any support level
        for (Support support : supportLevels) {
            if (Math.abs(ltp - support.getSupportValue()) <= VERY_MIN_SUPPORT_RESISTANCE_DISTANCE_BUFFER) {
                log.debug("Price {} is very near support level: {}", ltp, support.getSupportValue());
                return true;
            }
        }
        
        // Check if price is very close to any resistance level
        for (Resistance resistance : resistanceLevels) {
            if (Math.abs(ltp - resistance.getResistanceValue()) <= VERY_MIN_SUPPORT_RESISTANCE_DISTANCE_BUFFER) {
                log.debug("Price {} is very near resistance level: {}", ltp, resistance.getResistanceValue());
                return true;
            }
        }
        
        return false;
    }

    /**
     * Check if price is near round figure index levels (like 24500, 25000, etc.)
     * Round figures often act as psychological support/resistance levels
     * Uses modulo operation to detect levels every 500 points
     */
    public boolean isNearRoundFigureLevel(double ltp) {
        // Round figure levels are every 500 points
        int roundFigureInterval = 500;
        int buffer = 10; // Buffer around round figures
        
        // Find the nearest round figure level
        int nearestRoundLevel = (int) Math.round(ltp / roundFigureInterval) * roundFigureInterval;
        
        // Check if price is within buffer of the nearest round figure
        if (Math.abs(ltp - nearestRoundLevel) <= buffer) {
            log.debug("Price {} is near round figure level: {}", ltp, nearestRoundLevel);
            return true;
        }
        
        return false;
    }

    /**
     * Check if price is near support/resistance levels or round figures
     * This is an optional filter to prevent entries in choppy zones
     */
    public boolean isNearSupportResistanceOrRoundFigure(double ltp, Set<Support> supportLevels, Set<Resistance> resistanceLevels) {
        boolean nearSupportResistance = isVeryNearSupportResistance(ltp, supportLevels, resistanceLevels);
        boolean nearRoundFigure = isNearRoundFigureLevel(ltp);
        
        if (nearSupportResistance || nearRoundFigure) {
            log.info("🚫 NEAR SUPPORT/RESISTANCE - Price: {}, Near Support/Resistance: {}, Near Round Figure: {}", 
                    ltp, nearSupportResistance, nearRoundFigure);
            return true;
        }
        
        return false;
    }

}
