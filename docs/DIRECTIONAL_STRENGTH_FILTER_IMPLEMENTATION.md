# Directional Strength Filter Implementation

## Overview

The Directional Strength Filter is an optional filter added to the scalping entry configuration that ensures trades are only taken when there is sufficient directional movement in the market. This filter helps prevent entries in choppy or sideways markets where directional movement is weak.

## Configuration

### Filter Settings

The directional strength filter is configured in `src/main/resources/rules/scalping-entry-config.json`:

```json
"directionalStrength": {
  "enabled": true,
  "mandatory": false,
  "name": "Directional Strength",
  "description": "Filter out entries with directional strength less than 0.5 (50%)",
  "threshold": 0.5,
  "priority": 7
}
```

### Configuration Parameters

- **enabled**: `true` - Filter is active
- **mandatory**: `false` - Filter is optional (can be ignored up to maxOptionalFiltersToIgnore)
- **threshold**: `0.5` - Minimum directional strength required (50%)
- **priority**: `7` - Execution priority in the filter chain

## Implementation Details

### Core Calculation

The directional strength is calculated in `UnstableMarketConditionAnalysisService.calculateDirectionalStrength()`:

```java
public double calculateDirectionalStrength(Tick tick, FlattenedIndicators indicators) {
    int bullishSignals = 0;
    int bearishSignals = 0;
    int totalSignals = 0;

    // EMA signals (6 total)
    if (Boolean.TRUE.equals(indicators.getEma5_5min_gt_ema34_5min())) bullishSignals++;
    if (Boolean.TRUE.equals(indicators.getEma5_1min_gt_ema34_1min())) bullishSignals++;
    if (Boolean.TRUE.equals(indicators.getEma5_15min_gt_ema34_15min())) bullishSignals++;

    if (Boolean.TRUE.equals(indicators.getEma5_5min_lt_ema34_5min())) bearishSignals++;
    if (Boolean.TRUE.equals(indicators.getEma5_1min_lt_ema34_1min())) bearishSignals++;
    if (Boolean.TRUE.equals(indicators.getEma5_15min_lt_ema34_15min())) bearishSignals++;
    totalSignals += 6;

    // RSI signals (6 total)
    if (Boolean.TRUE.equals(indicators.getRsi_5min_gt_60())) bullishSignals++;
    if (Boolean.TRUE.equals(indicators.getRsi_1min_gt_60())) bullishSignals++;
    if (Boolean.TRUE.equals(indicators.getRsi_15min_gt_60())) bullishSignals++;

    if (Boolean.TRUE.equals(indicators.getRsi_5min_lt_40())) bearishSignals++;
    if (Boolean.TRUE.equals(indicators.getRsi_1min_lt_40())) bearishSignals++;
    if (Boolean.TRUE.equals(indicators.getRsi_15min_lt_40())) bearishSignals++;
    totalSignals += 6;

    // Price action signals (6 total)
    if (Boolean.TRUE.equals(indicators.getPrice_gt_vwap_5min())) bullishSignals++;
    if (Boolean.TRUE.equals(indicators.getPrice_gt_vwap_1min())) bullishSignals++;
    if (Boolean.TRUE.equals(indicators.getPrice_gt_vwap_15min())) bullishSignals++;

    if (Boolean.TRUE.equals(indicators.getPrice_lt_vwap_5min())) bearishSignals++;
    if (Boolean.TRUE.equals(indicators.getPrice_lt_vwap_1min())) bearishSignals++;
    if (Boolean.TRUE.equals(indicators.getPrice_lt_vwap_15min())) bearishSignals++;
    totalSignals += 6;

    // Calculate directional strength as ratio of dominant signals
    int dominantSignals = Math.max(bullishSignals, bearishSignals);
    double directionalStrength = (double) dominantSignals / totalSignals;
    
    return Math.min(directionalStrength, 1.0);
}
```

### Filter Integration

The filter is integrated into the flexible filtering system in `checkIndividualFilter()`:

```java
case "directionalStrength":
    double directionalStrength = calculateDirectionalStrength(tick, indicators);
    passed = directionalStrength >= filter.getThreshold();
    details = String.format("Directional strength: %.2f (threshold: %.2f)", 
            directionalStrength, filter.getThreshold());
    break;
```

## Signal Categories

The directional strength calculation considers **18 total signals** across three categories:

### 1. EMA Signals (6 signals)
- **Bullish**: EMA5 > EMA34 for 1min, 5min, and 15min timeframes
- **Bearish**: EMA5 < EMA34 for 1min, 5min, and 15min timeframes

### 2. RSI Signals (6 signals)
- **Bullish**: RSI > 60 for 1min, 5min, and 15min timeframes
- **Bearish**: RSI < 40 for 1min, 5min, and 15min timeframes

### 3. Price Action Signals (6 signals)
- **Bullish**: Price > VWAP for 1min, 5min, and 15min timeframes
- **Bearish**: Price < VWAP for 1min, 5min, and 15min timeframes

## Calculation Formula

```
Directional Strength = max(BullishSignals, BearishSignals) / TotalSignals
```

Where:
- `TotalSignals = 18` (6 EMA + 6 RSI + 6 Price Action)
- `DominantSignals = max(BullishSignals, BearishSignals)`
- Result is capped at 1.0 (100%)

## Interpretation

### Strength Levels

- **0.0 - 0.25**: Very weak directional strength (flat market)
- **0.25 - 0.35**: Weak directional strength
- **0.35 - 0.50**: Moderate directional strength
- **0.50 - 0.75**: Strong directional strength
- **0.75 - 1.0**: Very strong directional strength

### Filter Behavior

- **Threshold 0.5**: Requires at least 9 out of 18 signals to be in the same direction
- **Pass**: Directional strength ≥ 0.5 (50%)
- **Fail**: Directional strength < 0.5 (50%)

## Example Calculations

### Example 1: Strong Bullish Direction
- **Bullish signals**: 15 (EMA: 3, RSI: 3, Price: 9)
- **Bearish signals**: 3 (EMA: 3, RSI: 0, Price: 0)
- **Directional Strength**: max(15, 3) / 18 = 15/18 = 0.83 (83%)
- **Result**: PASS (0.83 ≥ 0.5)

### Example 2: Weak Direction
- **Bullish signals**: 8 (EMA: 2, RSI: 3, Price: 3)
- **Bearish signals**: 10 (EMA: 4, RSI: 3, Price: 3)
- **Directional Strength**: max(8, 10) / 18 = 10/18 = 0.56 (56%)
- **Result**: PASS (0.56 ≥ 0.5)

### Example 3: Flat Market
- **Bullish signals**: 6 (EMA: 2, RSI: 2, Price: 2)
- **Bearish signals**: 6 (EMA: 2, RSI: 2, Price: 2)
- **Directional Strength**: max(6, 6) / 18 = 6/18 = 0.33 (33%)
- **Result**: FAIL (0.33 < 0.5)

## Benefits

### 1. Improved Trade Quality
- Prevents entries in choppy, sideways markets
- Ensures sufficient directional movement for profitable trades
- Reduces false signals during low volatility periods

### 2. Risk Management
- Reduces exposure to unfavorable market conditions
- Prevents losses from flat market whipsaws
- Improves overall win rate

### 3. Flexible Configuration
- Optional filter that can be ignored if needed
- Configurable threshold for different market conditions
- Priority-based execution in the filter chain

## Testing

The implementation includes comprehensive tests in `UnstableMarketConditionAnalysisServiceTest`:

- **Configuration Tests**: Verify filter structure and properties
- **Logic Tests**: Test different directional strength scenarios
- **Integration Tests**: Verify complete filter integration

## Monitoring

The filter logs detailed information for monitoring:

```
directionalStrength calculation - Bullish: 12, Bearish: 6, Total: 18, Strength: 0.67
```

This allows for real-time monitoring of directional strength calculations and filter decisions.

## Usage in Trading Strategy

The directional strength filter works in conjunction with other filters:

1. **Mandatory Filters**: Must always pass (candle height, etc.)
2. **Optional Filters**: Can fail up to `maxOptionalFiltersToIgnore` (including directional strength)
3. **Flexible Filtering**: System allows some optional filters to fail while still permitting entries

This provides a balance between strict filtering and opportunity capture, ensuring trades are only taken when market conditions are favorable but not overly restrictive.
