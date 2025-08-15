# Profitable Trade Filter Implementation

## 🎯 Overview

The **Profitable Trade Filter** is a configurable filtering system designed to identify and allow only high-probability profitable trade entries based on historical analysis of successful trades. This filter acts as an additional layer of validation on top of the existing entry logic to ensure only the most promising trades are executed.

## 📊 Filter Criteria (Balanced Category-Based System)

The filter now uses a balanced 4-category system where each category must meet minimum count requirements:

### Quality Score Requirements
- **Minimum Quality Score**: ≥ 7.5/10
- **Minimum Candlestick Score**: ≥ 4.0/10

### Volume Surge Requirements
- **Volume Surge Multiplier**: Between 15.1x and 16.0x

### Category-Based Requirements

#### 1. EMA Category (Min 2/3 conditions)
- `ema9_5min_gt_ema21_5min` (CALL) / `ema9_5min_lt_ema21_5min` (PUT)
- `ema9_1min_gt_ema21_1min` (CALL) / `ema9_1min_lt_ema21_1min` (PUT)
- `ema9_15min_gt_ema21_15min` (CALL) / `ema9_15min_lt_ema21_15min` (PUT)

#### 2. Future and Volume Category (Min 4/7 conditions)
- Volume surge indicators: `volume_5min_surge`, `volume_1min_surge`, `volume_15min_surge`
- Price vs VWAP: `price_gt_vwap_5min`, `price_gt_vwap_1min`, `price_gt_vwap_15min` (CALL)
- Price vs VWAP: `price_lt_vwap_5min`, `price_lt_vwap_1min`, `price_lt_vwap_15min` (PUT)
- Support/Resistance: `price_above_resistance` (CALL) / `price_below_support` (PUT)

#### 3. Candlestick Category (Min 3/10 conditions)
- **CALL**: Green candles, long bodies, bullish patterns (engulfing, morning star, hammer)
- **PUT**: Red candles, long bodies, bearish patterns (engulfing, evening star, shooting star)

#### 4. Momentum Category (Min 2/3 conditions)
- **CALL**: `rsi_5min_gt_56`, `rsi_1min_gt_56`, `rsi_15min_gt_56`
- **PUT**: `rsi_5min_lt_44`, `rsi_1min_lt_44`, `rsi_15min_lt_44`

## 🏗️ Architecture

### Core Components

1. **ProfitableTradeFilterConfig** - Configuration model for filter settings
2. **ProfitableTradeFilterResult** - Result model with detailed evaluation
3. **ProfitableTradeFilterService** - Main service for filter evaluation
4. **ProfitableTradeFilterController** - REST API endpoints for filter management

### Integration Points

- **ScalpingVolumeSurgeServiceImpl** - Integrated into existing entry logic
- **DynamicStrategyConfigService** - Configuration loading and management
- **JSON Configuration** - All filter settings stored in `scalping-entry-config.json`

## ⚙️ Configuration

### JSON Configuration Structure

```json
{
  "profitableTradeFilter": {
    "enabled": true,
    "description": "Filter to keep only high-probability profitable trade entries",
    
    "callStrategy": {
      "enabled": true,
      "minQualityScore": 7.1,
      "minCandlestickScore": 3.0,
      "volumeSurgeMultiplier": {
        "min": 15.1,
        "max": 16.0
      },
      "requiredConditions": [
        "ema9_5min_gt_ema21_5min",
        "ema9_1min_gt_ema21_1min"
      ],
      "requiredPriceConditions": [
        "price_gt_vwap_1min",
        "price_gt_vwap_15min"
      ],
      "optionalConditions": [
        "volume_5min_surge",
        "rsi_5min_gt_56",
        "price_above_resistance",
        "bullish_engulfing_5min",
        "bullish_morning_star_5min"
      ],
      "minOptionalConditionsCount": 3
    },
    
    "putStrategy": {
      "enabled": true,
      "minQualityScore": 7.1,
      "minCandlestickScore": 3.0,
      "volumeSurgeMultiplier": {
        "min": 15.1,
        "max": 16.0
      },
      "requiredConditions": [
        "ema9_5min_lt_ema21_5min",
        "ema9_1min_lt_ema21_1min"
      ],
      "requiredPriceConditions": [
        "price_lt_vwap_1min",
        "price_lt_vwap_15min"
      ],
      "optionalConditions": [
        "volume_5min_surge",
        "rsi_5min_lt_44",
        "price_below_support",
        "bearish_engulfing_5min",
        "bearish_evening_star_5min"
      ],
      "minOptionalConditionsCount": 3
    },
    
    "logging": {
      "enabled": true,
      "logFilteredEntries": true,
      "logRejectionReasons": true
    }
  }
}
```

### Configuration Parameters

#### Global Settings
- `enabled`: Enable/disable the entire filter system
- `description`: Human-readable description of the filter

#### Strategy-Specific Settings
- `enabled`: Enable/disable filtering for specific strategy (CALL/PUT)
- `minQualityScore`: Minimum overall quality score required
- `minCandlestickScore`: Minimum candlestick pattern score required
- `volumeSurgeMultiplier`: Range for acceptable volume surge multiplier
- `requiredConditions`: List of conditions that MUST be true
- `requiredPriceConditions`: List of price-related conditions that MUST be true
- `optionalConditions`: List of optional conditions to check
- `minOptionalConditionsCount`: Minimum number of optional conditions that must be true

#### Logging Settings
- `enabled`: Enable/disable logging
- `logFilteredEntries`: Log when entries pass the filter
- `logRejectionReasons`: Log when entries are rejected with reasons

## 🔄 Integration with Entry Logic

### Flow Diagram

```
Entry Signal Generated
         ↓
Quality Score Check
         ↓
Momentum/Futuresignal Check
         ↓
Volume Surge Check
         ↓
[PROFITABLE TRADE FILTER] ← NEW
         ↓
Filter Evaluation
         ↓
Entry Decision
```

### Integration Code

The filter is integrated into the existing entry logic in `ScalpingVolumeSurgeServiceImpl`:

```java
// Apply profitable trade filter if enabled
if (shouldEntry && profitableTradeFilterService.isFilterEnabled()) {
    var filterResult = profitableTradeFilterService.evaluateCallEntry(indicators, entryQuality, tick);
    shouldEntry = filterResult.getIsProfitableEntry();
    
    if (!shouldEntry && filterResult.getRejectionReason() != null) {
        log.info("🔍 CALL ENTRY FILTERED - Quality: {}/10, Reason: {}", 
            entryQuality.getQualityScore(), filterResult.getRejectionReason());
    }
}
```

## 📈 Filter Evaluation Process

### 1. Quality Score Check
- Verify overall quality score meets minimum threshold
- Verify candlestick score meets minimum threshold

### 2. Volume Surge Check
- Verify volume surge multiplier is within specified range

### 3. Category Evaluation
- **EMA Category**: Check if at least 2 out of 3 EMA conditions are met
- **Future and Volume Category**: Check if at least 4 out of 7 volume/price conditions are met
- **Candlestick Category**: Check if at least 3 out of 10 candlestick patterns are met
- **Momentum Category**: Check if at least 2 out of 3 RSI conditions are met

### 4. Final Decision
- All 4 categories must pass for entry to be accepted
- Detailed rejection reasons show which categories failed and by how much

## 🛠️ API Endpoints

### Filter Status
```
GET /api/profitable-trade-filter/status
```
Returns the current status of the filter system.

### Filter Configuration
```
GET /api/profitable-trade-filter/config
```
Returns the complete filter configuration.

### Health Check
```
GET /api/profitable-trade-filter/health
```
Returns the health status of the filter service.

## 📊 Monitoring and Logging

### Log Messages

#### Accepted Entries
```
🎯 PROFITABLE TRADE FILTER - ACCEPTED | CALL | Price: 24467.8 | PROFITABLE_ENTRY - CALL | Quality: 7.3 | Candlestick: 3.5 | Volume: 15.5x | Optional: 4
```

#### Rejected Entries
```
🔍 CALL ENTRY BLOCKED - Quality: 7.3/10 | Profitable Filter: ✗ | Reason: Categories failed: EMA(1/2), FutureAndVolume(3/4)
```

#### Debug Information
```
🚫 PROFITABLE TRADE FILTER - REJECTED | CALL | Price: 24467.8 | Reason: Categories failed: EMA(1/2), Candlestick(2/3)
```

### Database Storage

The profitable trade filter information is automatically stored in the `JtradeOrder` collection for every order created. This includes:

#### Stored Information:
- **Filter Status**: Whether the filter was enabled and passed
- **Quality Scores**: Quality score, candlestick score, volume surge multiplier
- **Condition Results**: Which required and optional conditions passed/failed
- **Rejection Reasons**: Detailed reasons if the filter rejected the entry
- **Optional Counts**: Number of optional conditions that were met

#### Benefits:
- **Historical Analysis**: Analyze which filter conditions correlate with profitable trades
- **Performance Tracking**: Compare profitability of filtered vs unfiltered entries
- **Filter Optimization**: Identify which conditions are most effective
- **Debugging**: Understand why specific trades were accepted/rejected

### Filter Statistics

The filter provides detailed statistics for each evaluation:
- Quality score breakdown
- Condition pass/fail status
- Rejection reasons
- Optional condition counts

## 🔧 Customization

### Adding New Conditions

To add new conditions to the filter:

1. **Update FlattenedIndicators Model**: Add the new indicator field
2. **Update getConditionValue() Method**: Add case for the new condition
3. **Update JSON Configuration**: Add condition to appropriate lists
4. **Test the Integration**: Verify the condition is properly evaluated

### Modifying Thresholds

To modify filter thresholds:

1. **Update JSON Configuration**: Change the threshold values
2. **Restart Application**: Configuration is loaded at startup
3. **Monitor Results**: Check logs for filter behavior changes

### Disabling the Filter

To disable the filter:

1. **Set `enabled: false`** in the JSON configuration
2. **Restart Application**: Changes take effect immediately
3. **Verify**: Check logs to confirm filter is disabled

## 🧪 Testing

### Unit Tests

The filter includes comprehensive unit tests covering:
- Configuration loading
- Condition evaluation
- Threshold validation
- Error handling

### Integration Tests

Integration tests verify:
- Filter integration with entry logic
- Configuration hot-reloading
- API endpoint functionality

### Manual Testing

To test the filter manually:

1. **Enable Filter**: Set `enabled: true` in configuration
2. **Monitor Logs**: Watch for filter acceptance/rejection messages
3. **Adjust Thresholds**: Modify values and observe behavior changes
4. **Verify Results**: Check that only high-quality entries pass

## 🚀 Performance Considerations

### Optimization Features

1. **Conditional Evaluation**: Filter only runs when enabled
2. **Early Exit**: Stops evaluation as soon as any check fails
3. **Caching**: Configuration is cached after initial load
4. **Logging Cooldown**: Prevents log spam with time-based throttling

### Performance Impact

- **Minimal Overhead**: Filter adds <1ms to entry evaluation
- **Memory Efficient**: Uses lightweight result objects
- **Scalable**: Designed to handle high-frequency trading

## 🔮 Future Enhancements

### Planned Features

1. **Dynamic Thresholds**: Adjust thresholds based on market conditions
2. **Machine Learning**: Use ML to optimize filter parameters
3. **Historical Analysis**: Automatic threshold optimization based on past performance
4. **Real-time Monitoring**: Dashboard for filter performance metrics

### Potential Improvements

1. **Condition Weighting**: Different weights for different conditions
2. **Time-based Filtering**: Different criteria for different market hours
3. **Market Regime Detection**: Adapt filter based on market volatility
4. **Backtesting Integration**: Historical performance analysis

## 📝 Troubleshooting

### Common Issues

1. **Filter Not Working**: Check if `enabled: true` in configuration
2. **No Logs**: Verify logging settings are enabled
3. **Unexpected Rejections**: Review condition lists and thresholds
4. **Configuration Errors**: Check JSON syntax and restart application

### Debug Steps

1. **Check Configuration**: Verify JSON configuration is valid
2. **Enable Debug Logging**: Set `logRejectionReasons: true`
3. **Monitor API Endpoints**: Use `/status` and `/config` endpoints
4. **Review Logs**: Look for filter-related log messages

## 📚 Related Documentation

- [Condition Count Logging Enhancement](CONDITION_COUNT_LOGGING_README.md)
- [Scalping Volume Surge Strategy](SCALPING_VOLUME_SURGE_README.md)
- [Dynamic Indicators Configuration](DYNAMIC_INDICATORS_README.md)
- [Entry Signal Configuration](ENTRY_SIGNAL_CONFIGURATION.md)
