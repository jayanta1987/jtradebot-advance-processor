# Flexible Market Filtering System

## Overview

The Flexible Market Filtering System allows you to configure and control market condition filters with the ability to relax certain filters based on your trading strategy requirements. This replaces the previous strict "all-or-nothing" approach with a more nuanced filtering system.

## Configuration

The system is configured in `src/main/resources/rules/scalping-entry-config.json` under the `noTradeZones` section:

```json
{
  "noTradeZones": {
    "enabled": true,
    "description": "Flexible market condition filtering with configurable relaxation",
    "maxFiltersToIgnore": 0,
    "filters": {
      "candleHeight": {
        "enabled": true,
        "name": "Minimum Candle Height",
        "description": "Filter out entries with candle height less than 8.0",
        "threshold": 8.0,
        "priority": 1
      },
      "volumeSurge": {
        "enabled": true,
        "name": "Volume Surge Multiplier",
        "description": "Filter out entries with volume surge less than configured threshold",
        "threshold": 10.0,
        "priority": 2
      },
      "bodyRatio": {
        "enabled": true,
        "name": "Candle Body Ratio",
        "description": "Filter out entries with body ratio less than 0.50",
        "threshold": 0.50,
        "priority": 3
      },
      "ema200TooClose5Min": {
        "enabled": true,
        "name": "EMA 200 Too Close (5min)",
        "description": "Filter out entries when price is too close to EMA 200 (less than 0.2% on 5min)",
        "threshold": 0.002,
        "ntp": 3,
        "priority": 4
      },
      "ema200TooFar1Min": {
        "enabled": true,
        "name": "EMA 200 Too Far (1min)",
        "description": "Filter out entries when price is too far from EMA 200 (more than 0.6% on 1min)",
        "threshold": 0.006,
        "ntp": 2,
        "priority": 5
      },
      "priceBetweenEma34AndEma200": {
        "enabled": true,
        "name": "Price Between EMA34 and EMA200",
        "description": "Filter out entries when price is between EMA34 and EMA200",
        "priority": 6
      },
      "overboughtOversold": {
        "enabled": true,
        "name": "Overbought/Oversold RSI",
        "description": "Filter out entries when RSI is overbought (>80) or oversold (<20)",
        "overboughtThreshold": 80.0,
        "oversoldThreshold": 20.0,
        "priority": 7
      }
    }
  }
}
```

## Key Features

### 1. Configurable Filter Relaxation

- **`maxFiltersToIgnore`**: Controls how many filters can fail while still allowing entry
  - `0`: All filters must pass (strict mode - same as before)
  - `1`: One filter can fail
  - `2`: Two filters can fail
  - etc.

### 2. Individual Filter Configuration

Each filter can be configured with:
- **`enabled`**: Enable/disable the filter
- **`name`**: Human-readable name
- **`description`**: Detailed description
- **`threshold`**: Numeric threshold value (where applicable)
- **`priority`**: Priority order (lower number = higher priority)
- **`overboughtThreshold`/`oversoldThreshold`**: For RSI-based filters

### 3. Available Filters

#### Candle Height Filter
- **Key**: `candleHeight`
- **Purpose**: Ensures minimum candle height for meaningful price movement
- **Threshold**: Minimum candle height in points

#### Volume Surge Filter
- **Key**: `volumeSurge`
- **Purpose**: Ensures sufficient volume confirmation
- **Threshold**: Minimum volume surge multiplier

#### Body Ratio Filter
- **Key**: `bodyRatio`
- **Purpose**: Ensures candles have meaningful body size
- **Threshold**: Minimum body ratio (0.0 to 1.0)

#### EMA 200 Too Close Filter (5min)
- **Key**: `ema200TooClose5Min`
- **Purpose**: Prevents entries when price is too close to EMA 200 on 5min timeframe
- **Threshold**: Minimum distance as percentage of EMA 200 (default: 0.002 = 0.2%)
- **NTP**: 3 points

#### EMA 200 Too Far Filter (1min)
- **Key**: `ema200TooFar1Min`
- **Purpose**: Prevents entries when price is too far from EMA 200 on 1min timeframe
- **Threshold**: Maximum distance as percentage of EMA 200 (default: 0.006 = 0.6%)
- **NTP**: 2 points

#### Price Between EMAs Filter
- **Key**: `priceBetweenEma34AndEma200`
- **Purpose**: Prevents entries when price is between EMA34 and EMA200 (consolidation)
- **Note**: No threshold needed - boolean filter

#### Overbought/Oversold Filter
- **Key**: `overboughtOversold`
- **Purpose**: Prevents entries at extreme RSI levels
- **Thresholds**: Overbought and oversold RSI levels

## Usage Examples

### Conservative Trading (Strict Mode)
```json
{
  "maxFiltersToIgnore": 0
}
```
- All filters must pass
- Maximum protection against poor market conditions
- May miss some opportunities in borderline conditions

### Moderate Trading
```json
{
  "maxFiltersToIgnore": 1
}
```
- One filter can fail
- Balances protection with opportunity capture
- Good for most trading scenarios

### Aggressive Trading
```json
{
  "maxFiltersToIgnore": 2
}
```
- Two filters can fail
- Captures more opportunities
- Higher risk of poor entries

### Custom Filter Configuration
```json
{
  "filters": {
    "candleHeight": {
      "enabled": false  // Disable this filter
    },
    "volumeSurge": {
      "threshold": 5.0  // Lower threshold for more entries
    }
  }
}
```

## Implementation Details

### Service Integration

The system integrates with `UnstableMarketConditionAnalysisService`:

```java
// Check if no-trade-zones filtering is enabled
if (configService.isNoTradeZonesEnabled()) {
    FlexibleFilteringResult filteringResult = checkFlexibleFilteringConditions(tick, indicators);
    return filteringResult.isConditionsMet();
}
```

### Result Classes

- **`FlexibleFilteringResult`**: Overall filtering result with all filter results
- **`FilterResult`**: Individual filter check result with details

### Priority System

Filters are evaluated in priority order (lower number = higher priority). When multiple filters fail, the system considers priority when determining which ones to ignore.

## Migration from Old System

The new system is backward compatible:

1. **If `noTradeZones.enabled = false`**: Falls back to old flat market filtering
2. **If `noTradeZones.enabled = true`**: Uses new flexible filtering
3. **If `maxFiltersToIgnore = 0`**: Behaves exactly like the old strict system

## Monitoring and Logging

The system provides detailed logging:

```
Flexible filtering: 1 filters failed but 1 allowed to ignore. Failed: Volume Surge Multiplier
```

This helps you understand which filters are failing and why entries are being allowed or blocked.

## Best Practices

1. **Start Conservative**: Begin with `maxFiltersToIgnore = 0` and gradually increase
2. **Monitor Performance**: Track entry quality vs. filter relaxation levels
3. **Adjust Thresholds**: Fine-tune individual filter thresholds based on market conditions
4. **Disable Unnecessary Filters**: Turn off filters that don't apply to your strategy
5. **Use Priority Wisely**: Set higher priority for critical filters

## Troubleshooting

### Common Issues

1. **Too Many Rejections**: Increase `maxFiltersToIgnore` or adjust thresholds
2. **Poor Entry Quality**: Decrease `maxFiltersToIgnore` or tighten thresholds
3. **Configuration Errors**: Check JSON syntax and filter key names
4. **Performance Issues**: Disable unused filters to improve performance

### Debug Mode

Enable detailed logging to see individual filter results:

```java
log.info("Filter result: {} - {}", filterResult.getName(), filterResult.getDetails());
```
