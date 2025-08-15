# Condition Count Logging Enhancement

## Overview

This enhancement adds detailed condition counting to the tick logging system to provide more transparency about why entry signals are generated. Instead of just showing the quality score, the system now also displays the count of mandatory and optional conditions that are matched.

## Changes Made

### 1. Enhanced Logging Format

**Before:**
```
📊 Thu Aug 07 14:20:01 IST 2025 | 💰 24467.8 | 🎯 CALL (7.3/10): Quality:7.3/10
```

**After:**
```
📊 Thu Aug 07 14:20:01 IST 2025 | 💰 24467.8 | 🎯 CALL (7.3/10): Quality:7.3/10 | Conditions: M:3/3 O:8/23
```

### 2. Condition Counting Logic

The system now counts and displays:
- **M: X/Y** - Mandatory conditions matched (X out of Y required)
- **O: X/Y** - Optional conditions matched (X out of Y available)

#### CALL Strategy Conditions

**Mandatory Conditions (3 total):**
1. `volume_5min_surge` - 5-minute volume surge detected
2. `rsi_5min_gt_56` - 5-minute RSI > 56
3. `price_5min_gt_vwap` - 5-minute price above VWAP

**Optional Conditions (23 total):**
1. `ema9_5min_gt_ema21_5min` - 5-minute EMA crossover
2. `ema9_1min_gt_ema21_1min` - 1-minute EMA crossover
3. `ema9_15min_gt_ema21_15min` - 15-minute EMA crossover
4. `volume_1min_surge` - 1-minute volume surge
5. `volume_15min_surge` - 15-minute volume surge
6. `rsi_1min_gt_56` - 1-minute RSI > 56
7. `rsi_15min_gt_56` - 15-minute RSI > 56
8. `price_gt_vwap_1min` - 1-minute price above VWAP
9. `price_gt_vwap_15min` - 15-minute price above VWAP
10. `price_above_resistance` - Price above resistance level
11. `bullish_engulfing_5min` - 5-minute bullish engulfing pattern
12. `bullish_engulfing_1min` - 1-minute bullish engulfing pattern
13. `bullish_morning_star_5min` - 5-minute morning star pattern
14. `bullish_morning_star_1min` - 1-minute morning star pattern
15. `hammer_5min` - 5-minute hammer pattern
16. `hammer_1min` - 1-minute hammer pattern
17. `inverted_hammer_5min` - 5-minute inverted hammer pattern
18. `inverted_hammer_1min` - 1-minute inverted hammer pattern
19. `bullish_harami_5min` - 5-minute bullish harami pattern
20. `bullish_harami_1min` - 1-minute bullish harami pattern
21. `bullish_marubozu_5min` - 5-minute bullish marubozu pattern
22. `bullish_marubozu_1min` - 1-minute bullish marubozu pattern
23. `long_lower_shadow_5min` - 5-minute long lower shadow
24. `long_lower_shadow_1min` - 1-minute long lower shadow
25. `long_body_5min` - 5-minute long body
26. `long_body_1min` - 1-minute long body

#### PUT Strategy Conditions

**Mandatory Conditions (3 total):**
1. `volume_5min_surge` - 5-minute volume surge detected
2. `rsi_5min_lt_44` - 5-minute RSI < 44
3. `price_5min_lt_vwap` - 5-minute price below VWAP

**Optional Conditions (23 total):**
1. `ema9_5min_gt_ema21_5min` (false) - 5-minute EMA bearish crossover
2. `ema9_1min_gt_ema21_1min` (false) - 1-minute EMA bearish crossover
3. `ema9_15min_gt_ema21_15min` (false) - 15-minute EMA bearish crossover
4. `volume_1min_surge` - 1-minute volume surge
5. `volume_15min_surge` - 15-minute volume surge
6. `rsi_1min_lt_44` - 1-minute RSI < 44
7. `rsi_15min_lt_44` - 15-minute RSI < 44
8. `price_lt_vwap_1min` - 1-minute price below VWAP
9. `price_lt_vwap_15min` - 15-minute price below VWAP
10. `price_below_support` - Price below support level
11. `bearish_engulfing_5min` - 5-minute bearish engulfing pattern
12. `bearish_engulfing_1min` - 1-minute bearish engulfing pattern
13. `bearish_evening_star_5min` - 5-minute evening star pattern
14. `bearish_evening_star_1min` - 1-minute evening star pattern
15. `shooting_star_5min` - 5-minute shooting star pattern
16. `shooting_star_1min` - 1-minute shooting star pattern
17. `hanging_man_5min` - 5-minute hanging man pattern
18. `hanging_man_1min` - 1-minute hanging man pattern
19. `bearish_harami_5min` - 5-minute bearish harami pattern
20. `bearish_harami_1min` - 1-minute bearish harami pattern
21. `bearish_marubozu_5min` - 5-minute bearish marubozu pattern
22. `bearish_marubozu_1min` - 1-minute bearish marubozu pattern
23. `long_upper_shadow_5min` - 5-minute long upper shadow
24. `long_upper_shadow_1min` - 1-minute long upper shadow
25. `short_body_5min` - 5-minute short body
26. `short_body_1min` - 1-minute short body

## Implementation Details

### Files Modified

1. **`src/main/java/com/jtradebot/processor/service/TickProcessService.java`**
   - Enhanced `getTrendAndConditionsInfo()` method to include condition counts
   - Added `getConditionCounts()` method to count mandatory and optional conditions
   - Updated logging format to show both quality score and condition counts

### Method: `getConditionCounts()`

This private method:
- Takes `FlattenedIndicators` and strategy type as parameters
- Counts mandatory conditions (3 for each strategy)
- Counts optional conditions (23 for each strategy)
- Returns formatted string: `"M:X/Y O:Z/W"`

### Example Output

```
📊 Thu Aug 07 14:20:01 IST 2025 | 💰 24467.8 | 🎯 CALL (7.3/10): Quality:7.3/10 | Conditions: M:3/3 O:8/23
```

This means:
- **Quality Score**: 7.3/10
- **Mandatory Conditions**: 3 out of 3 matched (all required conditions met)
- **Optional Conditions**: 8 out of 23 matched (additional supportive conditions)

## Benefits

1. **Transparency**: Users can now see exactly which conditions are contributing to the signal
2. **Debugging**: Easier to understand why signals are or aren't generated
3. **Optimization**: Helps identify which conditions are most frequently matched
4. **Quality Assessment**: Better understanding of signal strength beyond just the quality score

## Configuration

The condition thresholds are defined in:
- `src/main/resources/rules/scalping-entry-config.json`

Key configuration parameters:
- `minMandatoryCount`: 3 (all mandatory conditions must be met)
- `minOptionalCount`: 5 (at least 5 optional conditions should be met)
- `minConfidenceScore`: 0.6 (minimum confidence threshold)

## Future Enhancements

1. **Detailed Condition Breakdown**: Show which specific conditions are matched
2. **Condition Weighting**: Different weights for different conditions
3. **Historical Analysis**: Track condition match rates over time
4. **Dynamic Thresholds**: Adjust thresholds based on market conditions
