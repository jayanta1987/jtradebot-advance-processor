# Near Support/Resistance Filter Implementation

## Overview

The Near Support/Resistance Filter is an optional filter that prevents trading entries when the price is very near to support/resistance levels or round figure index prices. This helps avoid choppy market conditions and psychological levels where price action can be unpredictable.

## Features

### 1. Support/Resistance Proximity Detection
- **Buffer**: 5 points from support/resistance levels
- **Purpose**: Prevents entries in choppy zones near key technical levels
- **Logic**: Uses `VERY_MIN_SUPPORT_RESISTANCE_DISTANCE_BUFFER = 5`

### 2. Round Figure Level Detection
- **Buffer**: 10 points from round figure levels
- **Levels**: Every 500 points (24000, 24500, 25000, etc.)
- **Purpose**: Avoids psychological support/resistance levels
- **Logic**: Uses modulo operation to dynamically calculate nearest round figure
- **Advantage**: No hardcoded values, works for any price range

### 3. Combined No-Entry Zone Logic
- **Condition**: Price is near support/resistance OR near round figures
- **Action**: Blocks entry when either condition is true
- **Logging**: Detailed logging with price and zone information

## Implementation Details

### 1. SupportResistanceIndicator Enhancements

#### New Methods Added:
```java
// Check if price is very near to support/resistance levels
public boolean isVeryNearSupportResistance(double ltp, Set<Support> supportLevels, Set<Resistance> resistanceLevels)

// Check if price is near round figure index levels
public boolean isNearRoundFigureLevel(double ltp)

// Combined near support/resistance check
public boolean isNearSupportResistanceOrRoundFigure(double ltp, Set<Support> supportLevels, Set<Resistance> resistanceLevels)
```

#### Round Figure Detection Logic:
```java
// Round figure levels are every 500 points
int roundFigureInterval = 500;
int buffer = 10; // Buffer around round figures

// Find the nearest round figure level using modulo operation
int nearestRoundLevel = (int) Math.round(ltp / roundFigureInterval) * roundFigureInterval;

// Check if price is within buffer of the nearest round figure
if (Math.abs(ltp - nearestRoundLevel) <= buffer) {
    return true;
}
```

**Algorithm Explanation:**
- **Dynamic Calculation**: Instead of hardcoded arrays, uses mathematical calculation
- **Modulo Operation**: `Math.round(ltp / 500) * 500` finds the nearest multiple of 500
- **Universal Coverage**: Works for any price range, not limited to predefined values
- **Performance**: O(1) time complexity vs O(n) for array iteration

### 2. FlattenedIndicators Model Updates

#### New Fields Added:
```java
// Near support/resistance indicators
private Boolean near_support_resistance_zone;
private Boolean near_round_figure_level;
private Boolean near_support_resistance_or_round_figure;
```

### 3. RuleHelper Integration

#### Support/Resistance Calculation Enhancement:
```java
// Calculate near support/resistance indicators
indicators.setNear_support_resistance_zone(supportResistanceIndicator.isVeryNearSupportResistance(tick.getLastTradedPrice(), supports, resistances));
indicators.setNear_round_figure_level(supportResistanceIndicator.isNearRoundFigureLevel(tick.getLastTradedPrice()));
indicators.setNear_support_resistance_or_round_figure(supportResistanceIndicator.isNearSupportResistanceOrRoundFigure(tick.getLastTradedPrice(), supports, resistances));
```

### 4. Entry Configuration

#### No-Trade Zones Configuration:
```json
{
  "nearToSupportResistance": {
    "enabled": true,
    "mandatory": false,
    "name": "Near Support/Resistance Filter",
    "description": "Filter out entries when price is very near support/resistance levels or round figure index prices",
    "priority": 9,
    "supportResistanceBuffer": 5,
    "roundFigureBuffer": 10
  }
}
```

### 5. UnstableMarketConditionAnalysisService Integration

#### Filter Case Added:
```java
case "nearToSupportResistance":
    // Check if price is near support/resistance or round figures
    boolean nearSupportResistance = Boolean.TRUE.equals(indicators.getNear_support_resistance_or_round_figure());
    passed = !nearSupportResistance; // Filter passes when NOT near support/resistance
    details = String.format("Near support/resistance check: %s (Price: %.2f)", 
            nearSupportResistance ? "NEAR" : "CLEAR", tick.getLastTradedPrice());
    break;
```

## Configuration Options

### 1. Enable/Disable Filter
```json
"enabled": true  // Set to false to disable the filter
```

### 2. Mandatory vs Optional
```json
"mandatory": false  // Set to true to make it a mandatory filter
```

### 3. Priority
```json
"priority": 9  // Lower number = higher priority
```

### 4. Buffer Configuration
```json
"supportResistanceBuffer": 5,  // Points from support/resistance
"roundFigureBuffer": 10        // Points from round figures
```

## Usage Examples

### 1. Price Near Support Level
- **Price**: 24503
- **Support Level**: 24500
- **Distance**: 3 points
- **Result**: Entry blocked (within 5-point buffer)

### 2. Price Near Round Figure
- **Price**: 24505
- **Round Figure**: 24500
- **Distance**: 5 points
- **Result**: Entry blocked (within 10-point buffer)

### 3. Price Clear of All Zones
- **Price**: 24520
- **Nearest Support**: 24500 (20 points away)
- **Nearest Round Figure**: 24500 (20 points away)
- **Result**: Entry allowed (clear of all buffers)

## Logging

### 1. Debug Level Logs
```
Price 24503.0 is very near support level: 24500
Price 24505.0 is near round figure level: 24500
```

### 2. Info Level Logs
```
🚫 NO ENTRY ZONE - Price: 24503.0, Near Support/Resistance: true, Near Round Figure: true
```

### 3. Filter Application Logs
```
🔍 FILTER CHECK - nearToSupportResistance: FAIL - Near support/resistance check: NEAR (Price: 24503.00)
```

## Testing

### 1. Unit Tests
- **File**: `SupportResistanceIndicatorTest.java`
- **Coverage**: All new methods with various scenarios
- **Test Cases**: 8 test methods covering edge cases

### 2. Test Scenarios
- Price very near support level
- Price very near resistance level
- Price near round figure level
- Price clear of all zones
- Combined no-entry zone logic

## Benefits

### 1. Risk Reduction
- Avoids choppy market conditions
- Prevents entries in unpredictable zones
- Reduces false signals near key levels

### 2. Improved Entry Quality
- Focuses on cleaner price action
- Avoids psychological levels
- Better directional moves

### 3. Configurable
- Optional filter (can be disabled)
- Adjustable buffers
- Priority-based execution

## Future Enhancements

### 1. Dynamic Buffer Adjustment
- Market volatility-based buffer sizing
- Time-based buffer adjustments
- Instrument-specific configurations

### 2. Advanced Level Detection
- Fibonacci retracement levels
- Pivot point levels
- Custom user-defined levels

### 3. Machine Learning Integration
- Historical success rate analysis
- Adaptive buffer optimization
- Pattern recognition for level strength
