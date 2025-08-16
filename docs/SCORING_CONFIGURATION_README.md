# Scoring Configuration System

## Overview

The trading system now uses a **separated configuration approach** with dedicated JSON files for different aspects of the system:

- **`scalping-entry-config.json`** - Trading parameters (account balance, quantities, risk management)
- **`scoring-config.json`** - Scoring weights, thresholds, and timing parameters

## Configuration Files

### 1. Trading Configuration (`scalping-entry-config.json`)

**Purpose**: Core trading parameters and risk management settings

```json
{
  "tradingConfiguration": {
    "accountBalance": 100000,
    "quantitySettings": {
      "minLotSize": 75,
      "maxQuantityPerTrade": 150
    },
    "investmentLimits": {
      "maxInvestmentPercentage": 50
    },
    "riskManagement": {
      "maxRiskPerDayPercentage": 12.0,
      "maxProfitPerDayPercentage": 30.0
    },
    "tradeSettings": {
      "maxTradeHoldingTimeInSec": 150
    }
  }
}
```

### 2. Scoring Configuration (`scoring-config.json`)

**Purpose**: All scoring weights, thresholds, and timing parameters

```json
{
  "scoringConfiguration": {
    "confidenceScoring": {
      "emaConfidence": 0.2,
      "rsiConfidence": 0.2,
      "volumeConfidence": 0.2,
      "priceActionConfidence": 0.2,
      "resistanceConfidence": 0.2,
      "maxConfidence": 1.0
    },
    "emaScoring": {
      "bullishScore": 1.5,
      "bearishScore": -1.5,
      "maxScore": 3.0,
      "minScore": -3.0
    },
    "rsiScoring": {
      "bullishScore": 1.5,
      "bearishScore": -1.5,
      "maxScore": 3.0,
      "minScore": -3.0
    },
    "volumeScoring": {
      "volume5minPoints": 2.0,
      "volume1minPoints": 1.0,
      "volumeMultiplierThresholds": {
        "veryHigh": {"threshold": 3.0, "bonus": 2.0},
        "high": {"threshold": 2.0, "bonus": 1.0},
        "standard": {"threshold": 1.5, "bonus": 0.5}
      },
      "maxScore": 5.0,
      "minScore": -5.0
    },
    "priceActionScoring": {
      "vwapBullishScore": 1.5,
      "vwapBearishScore": -1.5,
      "resistanceScore": 1.5,
      "supportScore": -1.5,
      "breakoutStrength": {
        "strongBreakout": {"threshold": 2.0, "bonus": 2.0},
        "standardBreakout": {"bonus": 1.0}
      },
      "breakdownStrength": {
        "strongBreakdown": {"threshold": 2.0, "bonus": 2.0},
        "standardBreakdown": {"bonus": 1.0}
      },
      "maxScore": 5.0,
      "minScore": -5.0
    },
    "futuresignalScoring": {
      "bullishScore": 2.0,
      "bearishScore": -2.0,
      "maxScore": 2.0,
      "minScore": -2.0
    },
    "momentumScoring": {
      "weight": 0.15,
      "perfectAlignmentBonus": 2.0,
      "majorityAlignmentBonus": 1.0,
      "rsiAlignmentBonus": 1.0,
      "maxScore": 3.0,
      "minScore": -3.0
    },
    "qualityScoring": {
      "emaQuality": 5.0,
      "rsiQuality": 5.0,
      "volumeQuality": {
        "volume5min": 5.0,
        "volume1min": 3.0,
        "volumeMultiplier": 2.0,
        "volumeMultiplierThreshold": 3.0
      },
      "priceActionQuality": 5.0,
      "futuresignalQuality": 10.0,
      "momentumQuality": {
        "perfectAlignment": 10.0,
        "majorityAlignment": 7.0,
        "singleAlignment": 3.0
      },
      "candlestickQuality": {
        "highReliability": 3.0,
        "mediumReliability": 2.0,
        "lowReliability": 1.0,
        "maxScore": 10.0
      },
      "minQualityThreshold": 0.7
    }
  },
  "thresholdConfiguration": {
    "rsiThresholds": {
      "callBullish": 56.0,
      "putBearish": 44.0,
      "overbought": 70.0,
      "oversold": 30.0,
      "tolerance": 0.1
    },
    "volumeThresholds": {
      "surgeMultiplier": 1.5,
      "highVolumeMultiplier": 2.0,
      "veryHighVolumeMultiplier": 3.0
    },
    "priceThresholds": {
      "supportResistanceTolerance": 0.01,
      "breakoutThreshold": 0.99,
      "breakdownThreshold": 1.01,
      "emaTouchTolerance": 0.001
    },
    "qualityThresholds": {
      "minConfidenceScore": 0.7,
      "minQualityScore": 0.7,
      "minCandlestickScore": 4.0
    }
  },
  "timingConfiguration": {
    "entryCooldownMs": 30000,
    "minDataBars": 20
  }
}
```

## Benefits of Separation

### 1. **Clear Separation of Concerns**
- **Trading Config**: Business logic parameters (money, risk, quantities)
- **Scoring Config**: Technical analysis parameters (weights, thresholds, timing)

### 2. **Easier Maintenance**
- Trading parameters can be adjusted without touching scoring logic
- Scoring weights can be fine-tuned without affecting risk management
- Different teams can work on different configuration files

### 3. **Better Organization**
- Related parameters are grouped together
- Easier to find and modify specific settings
- Reduced file size and complexity

### 4. **Independent Versioning**
- Each configuration can be versioned separately
- Changes to scoring don't affect trading parameters
- Rollback scenarios are more granular

## Usage in Code

### Trading Configuration Service
```java
@Service
public class TradingConfigurationService {
    // Loads from scalping-entry-config.json
    public double getAccountBalance() { ... }
    public int getMinLotSize() { ... }
    public double getMaxInvestmentPercentage() { ... }
}
```

### Scoring Configuration Service
```java
@Service
public class ScoringConfigurationService {
    // Loads from scoring-config.json
    public double getEmaBullishScore() { ... }
    public double getRsiThreshold() { ... }
    public double getVolume5minPoints() { ... }
    public long getEntryCooldownMs() { ... }
}
```

## Configuration Parameters

### Trading Configuration Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `accountBalance` | 100000 | Total account balance for position sizing |
| `minLotSize` | 75 | Minimum lot size (Nifty lot size) |
| `maxQuantityPerTrade` | 150 | Maximum quantity per trade |
| `maxInvestmentPercentage` | 50 | Maximum investment percentage per trade |
| `maxRiskPerDayPercentage` | 12.0 | Maximum daily risk percentage |
| `maxProfitPerDayPercentage` | 30.0 | Maximum daily profit percentage |
| `maxTradeHoldingTimeInSec` | 150 | Maximum trade holding time |

### Scoring Configuration Parameters

#### Confidence Scoring
| Parameter | Default | Description |
|-----------|---------|-------------|
| `emaConfidence` | 0.2 | EMA crossover confidence weight |
| `rsiConfidence` | 0.2 | RSI condition confidence weight |
| `volumeConfidence` | 0.2 | Volume surge confidence weight |
| `priceActionConfidence` | 0.2 | Price action confidence weight |
| `resistanceConfidence` | 0.2 | Resistance level confidence weight |
| `maxConfidence` | 1.0 | Maximum confidence score |

#### EMA Scoring
| Parameter | Default | Description |
|-----------|---------|-------------|
| `bullishScore` | 1.5 | Points for bullish EMA crossover |
| `bearishScore` | -1.5 | Points for bearish EMA crossover |
| `maxScore` | 3.0 | Maximum EMA score |
| `minScore` | -3.0 | Minimum EMA score |

#### RSI Scoring
| Parameter | Default | Description |
|-----------|---------|-------------|
| `bullishScore` | 1.5 | Points for bullish RSI (>56) |
| `bearishScore` | -1.5 | Points for bearish RSI (<44) |
| `maxScore` | 3.0 | Maximum RSI score |
| `minScore` | -3.0 | Minimum RSI score |

#### Volume Scoring
| Parameter | Default | Description |
|-----------|---------|-------------|
| `volume5minPoints` | 2.0 | Points for 5min volume surge |
| `volume1minPoints` | 1.0 | Points for 1min volume surge |
| `veryHigh.threshold` | 3.0 | Very high volume multiplier threshold |
| `veryHigh.bonus` | 2.0 | Bonus points for very high volume |
| `high.threshold` | 2.0 | High volume multiplier threshold |
| `high.bonus` | 1.0 | Bonus points for high volume |
| `standard.threshold` | 1.5 | Standard volume multiplier threshold |
| `standard.bonus` | 0.5 | Bonus points for standard volume |

#### Price Action Scoring
| Parameter | Default | Description |
|-----------|---------|-------------|
| `vwapBullishScore` | 1.5 | Points for price above VWAP |
| `vwapBearishScore` | -1.5 | Points for price below VWAP |
| `resistanceScore` | 1.5 | Points for price above resistance |
| `supportScore` | -1.5 | Points for price below support |
| `strongBreakout.threshold` | 2.0 | Strong breakout threshold |
| `strongBreakout.bonus` | 2.0 | Bonus points for strong breakout |
| `standardBreakout.bonus` | 1.0 | Bonus points for standard breakout |

#### Thresholds
| Parameter | Default | Description |
|-----------|---------|-------------|
| `callBullish` | 56.0 | RSI threshold for bullish CALL signals |
| `putBearish` | 44.0 | RSI threshold for bearish PUT signals |
| `overbought` | 70.0 | RSI overbought threshold |
| `oversold` | 30.0 | RSI oversold threshold |
| `surgeMultiplier` | 1.5 | Volume surge multiplier threshold |
| `supportResistanceTolerance` | 0.01 | Support/resistance tolerance |
| `emaTouchTolerance` | 0.001 | EMA touch tolerance |

#### Timing
| Parameter | Default | Description |
|-----------|---------|-------------|
| `entryCooldownMs` | 30000 | Entry signal cooldown (30 seconds) |
| `minDataBars` | 20 | Minimum data bars required |

## Migration Guide

### From Single Configuration to Separated Configuration

**Before (Single File)**:
```json
{
  "tradingConfiguration": { ... },
  "scoringConfiguration": { ... },
  "thresholdConfiguration": { ... }
}
```

**After (Separated Files)**:
```json
// scalping-entry-config.json
{
  "tradingConfiguration": { ... }
}

// scoring-config.json
{
  "scoringConfiguration": { ... },
  "thresholdConfiguration": { ... },
  "timingConfiguration": { ... }
}
```

### Code Changes Required

1. **TradingConfigurationService**: No changes needed (still loads from `scalping-entry-config.json`)
2. **ScoringConfigurationService**: Updated to load from `scoring-config.json`
3. **Services**: Inject both configuration services as needed

## Best Practices

### 1. **Configuration Management**
- Keep trading and scoring configurations separate
- Use descriptive parameter names
- Include descriptions for all parameters
- Version your configuration files

### 2. **Parameter Tuning**
- Start with default values
- Adjust one parameter at a time
- Test changes thoroughly
- Document parameter changes

### 3. **Validation**
- Validate configuration on startup
- Provide meaningful error messages
- Use fallback default values
- Log configuration loading status

### 4. **Deployment**
- Use environment-specific configurations
- Backup configuration files
- Monitor configuration changes
- Document deployment procedures

## Troubleshooting

### Common Issues

1. **Configuration Not Loading**
   - Check file paths in `ClassPathResource`
   - Verify JSON syntax
   - Check Spring component scanning

2. **Default Values Being Used**
   - Check JSON file structure
   - Verify parameter names match
   - Check for JSON parsing errors

3. **Configuration Changes Not Taking Effect**
   - Restart the application
   - Check for cached values
   - Verify configuration reload logic

### Debug Configuration Loading

```java
@PostConstruct
public void debugConfiguration() {
    log.info("Trading config loaded: {}", tradingConfigService.getAccountBalance());
    log.info("Scoring config loaded: {}", scoringConfigService.getEmaBullishScore());
}
```

## Conclusion

The separated configuration approach provides better organization, maintainability, and flexibility for the trading system. Each configuration file has a clear purpose and can be managed independently, making the system more robust and easier to maintain.
