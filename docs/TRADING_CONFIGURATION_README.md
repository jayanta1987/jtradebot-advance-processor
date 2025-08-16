# Trading Configuration System

## Overview

The trading configuration system has been enhanced to support JSON-based configuration for quantity, investment limits, and risk management parameters. This replaces the previously hardcoded values in the Java files.

## Configuration File

The trading configuration is now stored in `src/main/resources/rules/scalping-entry-config.json` under the `tradingConfiguration` section.

### Current Configuration

```json
{
  "tradingConfiguration": {
    "accountBalance": 100000,
    "quantitySettings": {
      "defaultQuantity": 75,
      "minLotSize": 75,
      "maxQuantityPerTrade": 150,
      "quantityIncrement": 75,
      "description": "Quantity settings for options trading (Nifty lot size is 75)"
    },
    "investmentLimits": {
      "maxInvestmentPerTrade": 50000,
      "maxInvestmentPercentage": 50,
      "maxDailyInvestment": 200000,
      "maxDailyLoss": 12000,
      "maxDailyProfit": 30000,
      "description": "Investment and risk limits per trade and daily"
    },
    "riskManagement": {
      "maxRiskPerTradePercentage": 1.0,
      "maxRiskPerDayPercentage": 12.0,
      "maxProfitPerDayPercentage": 30.0,
      "description": "Risk management percentages"
    },
    "tradeSettings": {
      "maxTradePrice": 24000,
      "maxTradeHoldingTimeInSec": 150,
      "description": "General trade settings and limits"
    }
  }
}
```

## Configuration Parameters

### Quantity Settings

- **defaultQuantity**: 75 - Default quantity for trades
- **minLotSize**: 75 - Minimum lot size (Nifty standard)
- **maxQuantityPerTrade**: 150 - Maximum quantity allowed per trade
- **quantityIncrement**: 75 - Increment for quantity adjustments

### Investment Limits

- **maxInvestmentPerTrade**: 50000 - Maximum investment per individual trade
- **maxInvestmentPercentage**: 50 - Maximum percentage of account balance per trade
- **maxDailyInvestment**: 200000 - Maximum total investment per day
- **maxDailyLoss**: 12000 - Maximum daily loss limit
- **maxDailyProfit**: 30000 - Maximum daily profit target

### Risk Management

- **maxRiskPerTradePercentage**: 1.0 - Maximum risk per trade as percentage
- **maxRiskPerDayPercentage**: 12.0 - Maximum daily risk as percentage
- **maxProfitPerDayPercentage**: 30.0 - Maximum daily profit as percentage

### Trade Settings

- **maxTradePrice**: 24000 - Maximum trade price allowed
- **maxTradeHoldingTimeInSec**: 150 - Maximum holding time for trades in seconds

## API Endpoints

### Get Current Configuration
```
GET /api/trading-config/current
```
Returns the complete trading configuration.

### Get Configuration Summary
```
GET /api/trading-config/summary
```
Returns a summary including calculated values like effective maximum investment.

## Implementation Details

### New Components

1. **TradingConfigurationService**: Loads and manages trading configuration from JSON
2. **TradingConfigurationController**: REST API for viewing configuration
3. **Updated ScalpingEntryServiceImpl**: Uses configuration service instead of hardcoded values
4. **Updated TradePreferenceMapper**: Uses configuration service for default values

### Key Changes

1. **Account Balance**: Now configurable via JSON (was hardcoded as 100,000)
2. **Quantity Calculation**: Uses configurable lot size and maximum quantity
3. **Position Sizing**: Respects maximum quantity limits from configuration
4. **Investment Limits**: Enforces both absolute and percentage-based limits

## Usage Examples

### Modifying Quantity
To change the default quantity from 75 to 150 (2 lots):
```json
{
  "quantitySettings": {
    "defaultQuantity": 150,
    "maxQuantityPerTrade": 300
  }
}
```

### Adjusting Investment Limits
To increase maximum investment per trade:
```json
{
  "investmentLimits": {
    "maxInvestmentPerTrade": 75000,
    "maxInvestmentPercentage": 75
  }
}
```

### Risk Management
To be more conservative:
```json
{
  "riskManagement": {
    "maxRiskPerTradePercentage": 0.5,
    "maxRiskPerDayPercentage": 8.0
  }
}
```

## Migration from Hardcoded Values

The following hardcoded values have been moved to JSON configuration:

| Previous Location | Old Value | New Configuration Path |
|------------------|-----------|----------------------|
| ScalpingEntryServiceImpl.java | accountBalance = 100000 | tradingConfiguration.accountBalance |
| TradePreferenceMapper.java | minLotSize = 75 | tradingConfiguration.quantitySettings.minLotSize |
| TradePreferenceMapper.java | maxQuantityPerTradeSafe = 150 | tradingConfiguration.quantitySettings.maxQuantityPerTrade |
| ScalpingEntryServiceImpl.java | lotSize = 75 | tradingConfiguration.quantitySettings.minLotSize |

## Benefits

1. **Flexibility**: Easy to modify trading parameters without code changes
2. **Environment-specific**: Different configurations for different environments
3. **Risk Management**: Centralized control over investment and risk limits
4. **Monitoring**: API endpoints for viewing current configuration
5. **Validation**: Configuration validation and default fallbacks

## Default Values

If the JSON configuration is not available or invalid, the system falls back to these default values:

- Account Balance: 100,000
- Default Quantity: 75
- Min Lot Size: 75
- Max Quantity Per Trade: 150
- Max Investment Per Trade: 50,000
- Max Investment Percentage: 50%
- Max Daily Investment: 200,000
- Max Daily Loss: 12,000
- Max Daily Profit: 30,000
