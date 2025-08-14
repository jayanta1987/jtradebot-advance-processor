# Dynamic Indicators System

## Overview

The Dynamic Indicators System is a fully configurable and extensible framework for calculating and evaluating technical indicators for trading strategies. Unlike the previous hardcoded approach, this system allows you to define indicators, their parameters, and trading conditions entirely through JSON configuration files.

## Key Features

### 🎯 **Fully Dynamic Configuration**
- All indicators are defined in JSON configuration files
- No code changes required to add new indicators or modify existing ones
- Support for multiple timeframes (1min, 5min, 15min)
- Configurable thresholds and parameters

### 📊 **Comprehensive Indicator Support**
- **EMA Indicators**: Crossover, crossdown, and comparison operations
- **RSI Indicators**: Overbought/oversold conditions with configurable thresholds
- **Volume Indicators**: Surge detection, low volume conditions, multipliers
- **VWAP Indicators**: Price vs VWAP comparisons
- **Support/Resistance**: Dynamic level detection and price positioning
- **Crossover/Crossdown**: Generic crossover detection between any two indicators

### 🔄 **CALL/PUT Strategy Support**
- Separate conditions for bullish (CALL) and bearish (PUT) strategies
- Configurable minimum condition requirements
- Confidence scoring with multiple factors
- Detailed reasoning for trading decisions

### 🎛️ **Flexible Comparison Operations**
- **GT**: Greater than threshold
- **LT**: Less than threshold  
- **EQ**: Equal to threshold
- **CROSSOVER**: Indicator crosses above another
- **CROSSDOWN**: Indicator crosses below another

## Architecture

### Core Components

1. **DynamicIndicatorConfig** - Configuration model for indicator definitions
2. **DynamicFlattenedIndicators** - Dynamic storage for indicator values
3. **DynamicIndicatorFlattenerService** - Service to flatten indicators based on configuration
4. **DynamicStrategyEvaluatorService** - Service to evaluate trading strategies
5. **DynamicIndicatorController** - REST API endpoints for testing and integration

### Data Flow

```
JSON Config → DynamicIndicatorConfig → DynamicIndicatorFlattenerService → DynamicFlattenedIndicators → DynamicStrategyEvaluatorService → StrategyEvaluationResult
```

## Configuration Structure

### Main Configuration File: `dynamic-indicators-config.json`

```json
{
  "strategy": "DYNAMIC_SCALPING_VOLUME_SURGE",
  "version": "2.0",
  "description": "Dynamic indicator configuration for scalping strategy",
  
  "timeframes": ["1min", "5min", "15min"],
  
  "thresholds": {
    "volumeSurgeMultiplier": 2.0,
    "rsiOverbought": 70.0,
    "rsiOversold": 30.0,
    "rsiBullish": 56.0,
    "rsiBearish": 44.0
  },
  
  "indicators": {
    "ema_crossover_1min": {
      "name": "EMA Crossover 1min",
      "type": "EMA",
      "timeframes": ["1min"],
      "parameters": {
        "ema1": 9,
        "ema2": 20
      },
      "comparison": "CROSSOVER",
      "description": "EMA 9 crosses above EMA 20 on 1min timeframe"
    }
  },
  
  "callConditions": {
    "requiredIndicators": ["ema_crossover_1min", "rsi_bullish_1min"],
    "minRequiredCount": 10
  },
  
  "putConditions": {
    "requiredIndicators": ["ema_crossdown_1min", "rsi_bearish_1min"],
    "minRequiredCount": 10
  }
}
```

### Indicator Definition Structure

Each indicator in the configuration follows this structure:

```json
{
  "name": "Human readable name",
  "type": "INDICATOR_TYPE",
  "timeframes": ["1min", "5min", "15min"],
  "parameters": {
    // Indicator-specific parameters
  },
  "comparison": "COMPARISON_TYPE",
  "threshold": 70.0, // Optional threshold value
  "description": "Description of what this indicator does"
}
```

## Supported Indicator Types

### 1. EMA (Exponential Moving Average)
```json
{
  "type": "EMA",
  "parameters": {
    "ema1": 9,
    "ema2": 20
  },
  "comparison": "CROSSOVER" // or "CROSSDOWN"
}
```

### 2. RSI (Relative Strength Index)
```json
{
  "type": "RSI",
  "parameters": {
    "period": 14
  },
  "comparison": "GT", // or "LT", "EQ"
  "threshold": 70.0
}
```

### 3. VOLUME
```json
{
  "type": "VOLUME",
  "parameters": {
    "historicalPeriods": 20,
    "recentPeriods": 5
  },
  "comparison": "GT",
  "threshold": 2.0
}
```

### 4. VWAP (Volume Weighted Average Price)
```json
{
  "type": "VWAP",
  "parameters": {},
  "comparison": "GT" // Price > VWAP
}
```

### 5. SUPPORT_RESISTANCE
```json
{
  "type": "SUPPORT_RESISTANCE",
  "parameters": {
    "lookbackPeriods": 20
  },
  "comparison": "GT" // Price > Resistance
}
```

### 6. CROSSOVER/CROSSDOWN
```json
{
  "type": "CROSSOVER",
  "parameters": {
    "indicator1": "ema_9",
    "indicator2": "rsi"
  }
}
```

## API Endpoints

### 1. Flatten Indicators
```http
POST /api/dynamic-indicators/flatten
Content-Type: application/json

{
  "instrumentToken": "256265",
  "lastTradedPrice": 19500.0,
  "tickTimestamp": "2024-01-15T10:30:00Z"
}
```

### 2. Get Configuration
```http
GET /api/dynamic-indicators/config
```

### 3. Evaluate Strategy
```http
POST /api/dynamic-indicators/evaluate
Content-Type: application/json

{
  "instrumentToken": "256265",
  "booleanIndicators": {
    "ema_1min_crossover": true,
    "rsi_1min_gt_70": false
  },
  "numericIndicators": {
    "volume_1min_multiplier": 2.5,
    "rsi_1min_value": 65.0
  }
}
```

### 4. Get Supported Types
```http
GET /api/dynamic-indicators/types
```

### 5. Health Check
```http
GET /api/dynamic-indicators/health
```

## Usage Examples

### Adding a New Indicator

1. **Define the indicator in JSON config:**
```json
{
  "macd_bullish_5min": {
    "name": "MACD Bullish 5min",
    "type": "MACD",
    "timeframes": ["5min"],
    "parameters": {
      "fastPeriod": 12,
      "slowPeriod": 26,
      "signalPeriod": 9
    },
    "comparison": "GT",
    "threshold": 0.0,
    "description": "MACD above signal line on 5min timeframe"
  }
}
```

2. **Add to strategy conditions:**
```json
{
  "callConditions": {
    "requiredIndicators": [
      "ema_crossover_1min",
      "macd_bullish_5min"
    ],
    "minRequiredCount": 8
  }
}
```

3. **Implement the indicator logic in `DynamicIndicatorFlattenerServiceImpl`**

### Modifying Thresholds

Simply update the threshold values in the JSON configuration:

```json
{
  "thresholds": {
    "volumeSurgeMultiplier": 3.0,  // Changed from 2.0
    "rsiOverbought": 75.0,         // Changed from 70.0
    "rsiOversold": 25.0            // Changed from 30.0
  }
}
```

### Adding New Timeframes

1. **Update the timeframes array:**
```json
{
  "timeframes": ["1min", "5min", "15min", "30min"]
}
```

2. **Add indicators for the new timeframe:**
```json
{
  "ema_crossover_30min": {
    "name": "EMA Crossover 30min",
    "type": "EMA",
    "timeframes": ["30min"],
    "parameters": {
      "ema1": 9,
      "ema2": 20
    },
    "comparison": "CROSSOVER"
  }
}
```

## Strategy Evaluation

The system evaluates strategies based on:

1. **Condition Satisfaction**: Count of satisfied conditions vs required minimum
2. **Confidence Scoring**: 
   - Base confidence from condition ratio
   - Strength multiplier based on indicator values
   - Timeframe alignment bonus
3. **Strategy Selection**: Choose between CALL/PUT based on confidence scores

### Confidence Calculation

```
Final Confidence = Base Confidence × Strength Multiplier × Timeframe Bonus
```

Where:
- **Base Confidence**: Ratio of satisfied conditions to total required
- **Strength Multiplier**: Bonus for strong indicator values (e.g., high volume surge)
- **Timeframe Bonus**: Bonus for alignment across multiple timeframes

## Benefits Over Previous System

### ✅ **Flexibility**
- No code changes needed for new indicators
- Easy threshold adjustments
- Support for any number of timeframes

### ✅ **Maintainability**
- All logic centralized in configuration
- Clear separation of concerns
- Easy to test and validate

### ✅ **Extensibility**
- Simple to add new indicator types
- Support for complex crossover scenarios
- Configurable strategy conditions

### ✅ **PUT Strategy Support**
- Full support for bearish strategies
- Separate conditions for CALL vs PUT
- Balanced evaluation of both directions

### ✅ **Crossover/Crossdown Support**
- Generic crossover detection
- Support for any two indicators
- Configurable crossover types

## Migration Guide

### From Old System to New System

1. **Extract current indicators** from `FlattenedIndicators.java`
2. **Convert to JSON format** using the new configuration structure
3. **Update service calls** to use `DynamicIndicatorFlattenerService`
4. **Test with new endpoints** to ensure compatibility
5. **Gradually migrate** existing logic to use dynamic system

### Example Migration

**Old hardcoded approach:**
```java
flattenedIndicators.setEma9_1min_gt_ema21_1min(emaInfo_1min.getEma9() > emaInfo_1min.getEma20());
```

**New dynamic approach:**
```json
{
  "ema_crossover_1min": {
    "type": "EMA",
    "timeframes": ["1min"],
    "parameters": {"ema1": 9, "ema2": 20},
    "comparison": "CROSSOVER"
  }
}
```

## Future Enhancements

1. **More Indicator Types**: MACD, Bollinger Bands, Stochastic, etc.
2. **Advanced Crossovers**: Multiple indicator crossovers
3. **Machine Learning Integration**: Dynamic threshold optimization
4. **Real-time Configuration Updates**: Hot-reload configuration changes
5. **Backtesting Integration**: Historical strategy evaluation
6. **Performance Optimization**: Caching and parallel processing

## Troubleshooting

### Common Issues

1. **Configuration Not Loading**
   - Check file path: `src/main/resources/rules/dynamic-indicators-config.json`
   - Validate JSON syntax
   - Check file permissions

2. **Indicators Not Calculating**
   - Verify indicator type is supported
   - Check required parameters
   - Ensure timeframe is valid

3. **Strategy Not Evaluating**
   - Verify condition names match indicator keys
   - Check minimum required count
   - Validate threshold values

### Debug Endpoints

Use the health check endpoint to verify system status:
```http
GET /api/dynamic-indicators/health
```

## Conclusion

The Dynamic Indicators System provides a powerful, flexible, and maintainable solution for technical indicator calculation and strategy evaluation. It eliminates the need for hardcoded indicator logic while providing full support for both CALL and PUT strategies with comprehensive crossover/crossdown detection capabilities.
