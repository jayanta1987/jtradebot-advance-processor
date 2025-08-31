# Consecutive Same Color Candles Filter Implementation

## Overview

The Consecutive Same Color Candles Filter is an optional filter added to the scalping entry configuration that prevents trades when there are too many consecutive candles of the same color (all green or all red). This filter helps avoid entering trades during strong trending periods that may be overextended and due for a reversal.

## Configuration

### Filter Settings

The consecutive same color candles filter is configured in `src/main/resources/rules/scalping-entry-config.json`:

```json
"consecutiveSameColorCandles": {
  "enabled": true,
  "mandatory": false,
  "name": "Consecutive Same Color Candles",
  "description": "Filter out entries when there are too many consecutive same color candles",
  "maxConsecutiveCount": 3,
  "analysisWindow": 10,
  "priority": 8,
  "timeframe": "FIVE_MIN"
}
```

### Configuration Parameters

- **enabled**: `true` - Filter is active
- **mandatory**: `false` - Filter is optional (can be ignored up to maxOptionalFiltersToIgnore)
- **maxConsecutiveCount**: `3` - Maximum number of consecutive same color candles allowed
- **analysisWindow**: `10` - Number of bars to analyze for consecutive pattern detection
- **priority**: `8` - Execution priority in the filter chain
- **timeframe**: `"FIVE_MIN"` - Candle timeframe to analyze (ONE_MIN, FIVE_MIN, FIFTEEN_MIN, etc.)

## Implementation Details

### Core Calculation

The consecutive same color candles calculation is implemented in `UnstableMarketConditionAnalysisService`:

```java
/**
 * Calculate the number of consecutive same color candles
 * @param barSeries The bar series to analyze
 * @param analysisWindow Number of bars to analyze (default 10)
 * @return Number of consecutive same color candles
 */
private int calculateConsecutiveSameColorCandles(BarSeries barSeries, int analysisWindow) {
    try {
        if (barSeries.getBarCount() < analysisWindow) {
            return 0;
        }

        int consecutiveCount = 0;
        String currentColor = null;

        // Start from the most recent bar and go backwards
        for (int i = barSeries.getBarCount() - 1; i >= barSeries.getBarCount() - analysisWindow; i--) {
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
```

### Candle Color Determination

```java
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
```

### Filter Integration

The filter is integrated into the flexible filtering system in `checkIndividualFilter()`:

```java
case "consecutiveSameColorCandles":
    String instrumentToken = String.valueOf(tick.getInstrumentToken());
    
    // Get timeframe from filter configuration, default to FIVE_MIN
    String timeframeStr = filter.getTimeframe() != null ? filter.getTimeframe() : "FIVE_MIN";
    com.jtradebot.processor.model.enums.CandleTimeFrameEnum timeframe = 
        com.jtradebot.processor.model.enums.CandleTimeFrameEnum.valueOf(timeframeStr);
    
    // Get analysis window from filter configuration, default to 10
    int analysisWindow = filter.getAnalysisWindow() != null ? filter.getAnalysisWindow() : 10;
    
    BarSeries barSeries = tickDataManager.getBarSeriesForTimeFrame(instrumentToken, timeframe);
    int consecutiveSameColorCount = calculateConsecutiveSameColorCandles(barSeries, analysisWindow);
    passed = consecutiveSameColorCount < filter.getMaxConsecutiveCount();
    details = String.format("Consecutive same color candles: %d (max allowed: %d, timeframe: %s, analysis window: %d)", 
            consecutiveSameColorCount, filter.getMaxConsecutiveCount(), timeframeStr, analysisWindow);
    break;
```

## Candle Color Classification

### Color Categories

1. **GREEN Candle**: Close price > Open price (bullish)
2. **RED Candle**: Close price < Open price (bearish)
3. **NEUTRAL Candle**: Close price = Open price (doji/neutral)

### Analysis Logic

- **Analysis Window**: Configurable (default: 10 bars)
- **Timeframe**: Configurable (default: FIVE_MIN)
- **Direction**: Analyzes from most recent to oldest
- **Streak Counting**: Counts consecutive candles of the same color
- **Break Condition**: Stops counting when a different color is encountered

## Filter Behavior

### Filter Logic

- **Max Consecutive Count 3**: Maximum 2 consecutive same color candles allowed
- **Pass**: Consecutive count < 3
- **Fail**: Consecutive count ≥ 3

### Examples

#### Example 1: Acceptable Pattern (PASS)
```
Recent candles: [GREEN, GREEN, RED, GREEN, GREEN, GREEN, RED, GREEN, GREEN, GREEN]
Consecutive count: 2 (last 2 are GREEN)
Result: PASS (2 < 3)
```

#### Example 2: Overextended Pattern (FAIL)
```
Recent candles: [GREEN, GREEN, GREEN, RED, GREEN, GREEN, GREEN, RED, GREEN, GREEN]
Consecutive count: 3 (last 3 are GREEN)
Result: FAIL (3 ≥ 3)
```

#### Example 3: Mixed Pattern (PASS)
```
Recent candles: [RED, GREEN, RED, GREEN, RED, GREEN, RED, GREEN, RED, GREEN]
Consecutive count: 1 (alternating colors)
Result: PASS (1 < 3)
```

## Benefits

### 1. Trend Exhaustion Detection
- Prevents entries during overextended trends
- Avoids catching the end of strong moves
- Reduces risk of entering before reversals

### 2. Risk Management
- Protects against momentum exhaustion
- Reduces exposure to potential trend reversals
- Improves entry timing quality

### 3. Market Psychology
- Recognizes when market sentiment is too one-sided
- Avoids entering during extreme bullish/bearish periods
- Waits for more balanced market conditions

### 4. Flexible Configuration
- Optional filter that can be ignored if needed
- Configurable threshold for different market conditions
- Priority-based execution in the filter chain

## Trading Strategy Integration

### When to Use

- **Strong Trending Markets**: Filter out overextended moves
- **Momentum Exhaustion**: Avoid entering during exhaustion phases
- **Reversal Setups**: Wait for color changes before entering

### When to Disable

- **Range-Bound Markets**: When markets are choppy with frequent color changes
- **High-Frequency Trading**: When quick entries are needed
- **Strong Trend Following**: When you want to ride strong trends

## Monitoring and Logging

The filter provides detailed logging for monitoring:

```
Consecutive same color candles: 2 (max allowed: 3, timeframe: FIVE_MIN, analysis window: 10)
```

This allows for real-time monitoring of:
- Current consecutive candle count
- Filter pass/fail decisions
- Market pattern recognition

## Testing

The implementation includes comprehensive tests in `UnstableMarketConditionAnalysisServiceTest`:

- **Configuration Tests**: Verify filter structure and properties
- **Logic Tests**: Test different consecutive candle scenarios
- **Integration Tests**: Verify complete filter integration

### Test Scenarios

1. **Below Threshold**: 3 consecutive candles should pass
2. **At Threshold**: 5 consecutive candles should fail
3. **Above Threshold**: 7 consecutive candles should fail
4. **Zero Count**: 0 consecutive candles should pass

## Usage in Trading Strategy

The consecutive same color candles filter works in conjunction with other filters:

1. **Mandatory Filters**: Must always pass (candle height, etc.)
2. **Optional Filters**: Can fail up to `maxOptionalFiltersToIgnore` (including consecutive candles)
3. **Flexible Filtering**: System allows some optional filters to fail while still permitting entries

### Filter Priority

- **Priority 8**: Executes after directional strength filter (priority 7)
- **Execution Order**: Runs in the filter chain after other technical filters
- **Integration**: Works seamlessly with existing filtering logic

## Configuration Recommendations

### Conservative Settings
```json
"maxConsecutiveCount": 2,    // More restrictive, fewer consecutive candles allowed
"analysisWindow": 5,         // Smaller analysis window
"timeframe": "FIVE_MIN"
```

### Moderate Settings
```json
"maxConsecutiveCount": 3,    // Default setting, balanced approach
"analysisWindow": 10,        // Standard analysis window
"timeframe": "FIVE_MIN"
```

### Aggressive Settings
```json
"maxConsecutiveCount": 5,    // Less restrictive, allows more consecutive candles
"analysisWindow": 15,        // Larger analysis window
"timeframe": "FIVE_MIN"
```

### Short-term Analysis
```json
"maxConsecutiveCount": 2,    // Quick pattern detection
"analysisWindow": 5,         // Small analysis window
"timeframe": "ONE_MIN"
```

### Long-term Analysis
```json
"maxConsecutiveCount": 7,    // Longer pattern detection
"analysisWindow": 20,        // Large analysis window
"timeframe": "FIFTEEN_MIN"
```

## Market Conditions

### Ideal for Filter
- **Strong Trending Markets**: When trends are overextended
- **Momentum Exhaustion**: When moves are losing steam
- **Reversal Setups**: When waiting for trend changes

### Less Effective
- **Range-Bound Markets**: When markets are choppy
- **Low Volatility**: When candles are small and frequent
- **News-Driven Markets**: When fundamentals override technical patterns

This filter provides an additional layer of protection against entering trades during potentially overextended market conditions, improving the overall quality and timing of trade entries.
