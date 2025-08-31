# RSI Divergence Integration for Entry and Exit Signals

## Overview

This document outlines the implementation of RSI (Relative Strength Index) divergence detection and integration into the trading system's entry and exit logic. RSI divergence is a powerful technical analysis tool that can provide early warning signals for potential trend reversals.

## What is RSI Divergence?

RSI divergence occurs when the price action and RSI indicator move in opposite directions:

- **Bullish Divergence**: Price makes lower lows while RSI makes higher lows (potential bullish reversal)
- **Bearish Divergence**: Price makes higher highs while RSI makes lower highs (potential bearish reversal)

## Implementation Details

### 1. RSI Divergence Detection

**File**: `src/main/java/com/jtradebot/processor/indicator/RsiIndicator.java`

The existing `isRsiDivergence()` method has been enhanced to detect both bullish and bearish divergences:

```java
public boolean isRsiDivergence(BarSeries series, int barCount, int lookBackPeriod, DivergenceType divergenceType)
```

**Parameters**:
- `barCount`: RSI calculation period (default: 14)
- `lookBackPeriod`: Number of bars to analyze for divergence (default: 20)
- `divergenceType`: BULLISH, BEARISH, or ANY

### 2. FlattenedIndicators Model Updates

**File**: `src/main/java/com/jtradebot/processor/model/indicator/FlattenedIndicators.java`

Added new fields for RSI divergence signals across all timeframes:

```java
// RSI Divergence indicators
private Boolean rsi_bullish_divergence_1min;
private Boolean rsi_bullish_divergence_5min;
private Boolean rsi_bullish_divergence_15min;
private Boolean rsi_bearish_divergence_1min;
private Boolean rsi_bearish_divergence_5min;
private Boolean rsi_bearish_divergence_15min;
```

### 3. RuleHelper Integration

**File**: `src/main/java/com/jtradebot/processor/service/entry/RuleHelper.java`

Updated `flattenRsiIndicators()` method to calculate RSI divergence for all timeframes:

- **1-minute timeframe**: Requires minimum 30 bars
- **5-minute timeframe**: Requires minimum 30 bars  
- **15-minute timeframe**: Requires minimum 30 bars

### 4. Entry Signal Integration

#### Configuration Updates

**File**: `src/main/resources/rules/scalping-entry-config.json`

Added RSI divergence signals to momentum categories:

```json
"momentum": [
  "rsi_5min_gt_60",
  "rsi_1min_gt_60", 
  "rsi_15min_gt_60",
  "rsi_5min_gt_rsi_ma",
  "rsi_1min_gt_rsi_ma",
  "rsi_15min_gt_rsi_ma",
  "rsi_bullish_divergence_5min",    // NEW
  "rsi_bullish_divergence_1min",    // NEW
  "rsi_bullish_divergence_15min"    // NEW
]
```

#### Quality Score Enhancement

**File**: `src/main/resources/rules/scoring-config.json`

Added RSI divergence bonus to momentum quality scoring:

```json
"momentumQuality": {
  "perfectAlignment": 10.0,
  "majorityAlignment": 7.0,
  "singleAlignment": 3.0,
  "rsiDivergenceBonus": 5.0    // NEW
}
```

#### Service Integration

**Files**: 
- `src/main/java/com/jtradebot/processor/service/entry/ScalpingEntryService.java`
- `src/main/java/com/jtradebot/processor/service/analysis/MarketDirectionService.java`
- `src/main/java/com/jtradebot/processor/service/analysis/CategoryAnalysisService.java`

Updated all services to recognize and evaluate RSI divergence conditions.

### 5. Exit Signal Integration

**File**: `src/main/java/com/jtradebot/processor/service/entry/DynamicRuleEvaluatorService.java`

Enhanced exit logic to include RSI divergence signals:

```java
public boolean shouldMakePutExit(Tick tick) {
    FlattenedIndicators indicators = getFlattenedIndicators(tick);
    // Check for RSI bullish divergence as exit signal for PUT orders
    boolean rsiBullishDivergence = isRsiBullishDivergencePresent(indicators);
    if (rsiBullishDivergence) {
        log.info("🔄 PUT EXIT SIGNAL - RSI Bullish Divergence detected");
        return true;
    }
    // ... existing logic
}

public boolean shouldMakeCallExit(Tick tick) {
    FlattenedIndicators indicators = getFlattenedIndicators(tick);
    // Check for RSI bearish divergence as exit signal for CALL orders
    boolean rsiBearishDivergence = isRsiBearishDivergencePresent(indicators);
    if (rsiBearishDivergence) {
        log.info("🔄 CALL EXIT SIGNAL - RSI Bearish Divergence detected");
        return true;
    }
    // ... existing logic
}
```

## How It Works

### Entry Logic

1. **Signal Detection**: RSI divergence is calculated for 1min, 5min, and 15min timeframes
2. **Category Scoring**: Divergence signals are included in the momentum category scoring
3. **Quality Enhancement**: RSI divergence provides a 5-point bonus to quality scores
4. **Entry Decision**: Strong divergence signals can trigger entries with higher confidence

### Exit Logic

1. **Reversal Detection**: RSI divergence signals potential trend reversals
2. **Exit Triggers**: 
   - Bullish divergence triggers PUT exit (expecting upward movement)
   - Bearish divergence triggers CALL exit (expecting downward movement)
3. **Priority**: RSI divergence exits have high priority in the exit decision hierarchy

## Configuration Parameters

### RSI Divergence Settings

- **Lookback Period**: 20 bars (configurable)
- **RSI Period**: 14 (standard)
- **Minimum Bars Required**: 30 bars for divergence calculation
- **Bonus Points**: 5.0 points added to quality score when divergence is detected

### Timeframe Priority

1. **5-minute**: Primary timeframe for divergence signals
2. **1-minute**: Secondary timeframe for early signals
3. **15-minute**: Tertiary timeframe for trend confirmation

## Logging and Monitoring

### Entry Logs

```
🎯 RSI BULLISH DIVERGENCE BONUS - Added 5.0 points to CALL quality score
🎯 RSI BEARISH DIVERGENCE BONUS - Added 5.0 points to PUT quality score
```

### Exit Logs

```
🔄 PUT EXIT SIGNAL - RSI Bullish Divergence detected
🔄 CALL EXIT SIGNAL - RSI Bearish Divergence detected
```

### Divergence Detection Logs

```
RSI Divergence 5min - Bullish: true, Bearish: false
RSI Divergence 1min - Bullish: false, Bearish: true
RSI Divergence 15min - Bullish: true, Bearish: false
```

## Benefits

1. **Early Warning**: RSI divergence provides early signals of potential reversals
2. **Quality Enhancement**: Divergence signals boost entry quality scores
3. **Risk Management**: Divergence-based exits help protect profits and limit losses
4. **Multi-timeframe**: Analysis across multiple timeframes increases signal reliability

## Risk Considerations

1. **False Signals**: RSI divergence can produce false signals in sideways markets
2. **Timing**: Divergence signals may appear before the actual reversal
3. **Confirmation**: Should be used in conjunction with other technical indicators
4. **Market Conditions**: Effectiveness varies based on market volatility and trend strength

## Future Enhancements

1. **Divergence Strength**: Implement divergence strength measurement
2. **Hidden Divergence**: Add support for hidden divergence patterns
3. **Multiple Indicators**: Extend to other momentum indicators (MACD, Stochastic)
4. **Machine Learning**: Use ML models to improve divergence signal accuracy

## Testing and Validation

To validate the RSI divergence integration:

1. **Backtesting**: Run historical data through the system
2. **Paper Trading**: Test with live data without real money
3. **Signal Analysis**: Monitor divergence signal accuracy and timing
4. **Performance Metrics**: Track entry/exit success rates with divergence signals

## Conclusion

The RSI divergence integration provides a sophisticated technical analysis capability that enhances both entry and exit decision-making. By combining divergence signals with existing indicators, the system can better identify high-probability trading opportunities and manage risk more effectively.
