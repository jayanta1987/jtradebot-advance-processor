# Migration Guide: Legacy to Dynamic Configuration System

## Overview

This guide helps you migrate from the legacy `scalping-volume-surge-rules.json` configuration to the new dynamic configuration system using `dynamic-indicators-config.json` and `scalping-entry-config.json`.

## Migration Status

### ✅ **Completed Migrations**

1. **DynamicStrategyConfigService** - New service that loads both dynamic configurations
2. **ScalpingVolumeSurgeController** - Updated to use new dynamic configuration
3. **ScalpingVolumeSurgeServiceImpl** - Updated to use new dynamic configuration

### ⚠️ **Remaining Services to Migrate**

1. **ExitStrategyServiceImpl** - Still uses old StrategyConfigService
2. **TickProcessService** - Still uses old StrategyConfigService

## Step-by-Step Migration

### Step 1: Update ExitStrategyServiceImpl

**File:** `src/main/java/com/jtradebot/processor/service/impl/ExitStrategyServiceImpl.java`

**Change:**
```java
// OLD
import com.jtradebot.processor.config.StrategyConfigService;
private final StrategyConfigService configService;

// NEW
import com.jtradebot.processor.config.DynamicStrategyConfigService;
private final DynamicStrategyConfigService configService;
```

### Step 2: Update TickProcessService

**File:** `src/main/java/com/jtradebot/processor/service/TickProcessService.java`

**Change:**
```java
// OLD
import com.jtradebot.processor.config.StrategyConfigService;
private final StrategyConfigService configService;

// NEW
import com.jtradebot.processor.config.DynamicStrategyConfigService;
private final DynamicStrategyConfigService configService;
```

### Step 3: Remove Legacy Configuration Service

**File:** `src/main/java/com/jtradebot/processor/config/StrategyConfigService.java`

**Action:** Delete this file after all services are migrated.

### Step 4: Remove Legacy Configuration File

**File:** `src/main/resources/rules/scalping-volume-surge-rules.json`

**Action:** Delete this file after confirming all services work with new configuration.

## Configuration Mapping

### Legacy to New Configuration Mapping

| Legacy Config | New Dynamic Config | Location |
|---------------|-------------------|----------|
| `callRule.thresholds.minRsiThreshold` | `callStrategy.entryQuality.minRsiStrength` | `scalping-entry-config.json` |
| `callRule.thresholds.minVolumeSurgeMultiplier` | `callStrategy.entryQuality.minVolumeSurge` | `scalping-entry-config.json` |
| `callRule.riskManagement.stopLossPoints` | `callStrategy.riskManagement.stopLossPoints` | `scalping-entry-config.json` |
| `callRule.riskManagement.targetPoints` | `callStrategy.riskManagement.targetPoints` | `scalping-entry-config.json` |
| `callRule.riskManagement.maxHoldingTimeMinutes` | `callStrategy.riskManagement.maxHoldingTimeMinutes` | `scalping-entry-config.json` |
| `putRule.thresholds.maxRsiThreshold` | `putStrategy.entryQuality.minRsiStrength` | `scalping-entry-config.json` |
| `putRule.thresholds.minVolumeSurgeMultiplier` | `putStrategy.entryQuality.minVolumeSurge` | `scalping-entry-config.json` |

### New Dynamic Configuration Structure

```
dynamic-indicators-config.json
├── strategy
├── version
├── description
├── timeframes
├── thresholds
├── indicators
│   ├── ema_crossover_1min
│   ├── rsi_bullish_1min
│   ├── volume_surge_1min
│   └── ...
├── callConditions
└── putConditions

scalping-entry-config.json
├── callStrategy
│   ├── entryConditions
│   ├── riskManagement
│   ├── entryQuality
│   └── marketConditions
├── putStrategy
│   ├── entryConditions
│   ├── riskManagement
│   ├── entryQuality
│   └── marketConditions
└── commonSettings
```

## Benefits of Migration

### ✅ **Advantages of New System**

1. **Fully Dynamic**: All indicators configurable via JSON
2. **Better Organization**: Separate indicator and entry logic configurations
3. **Enhanced Features**: Quality assessment, market condition validation
4. **Risk Management**: Comprehensive risk management configuration
5. **PUT Strategy Support**: Full support for bearish strategies
6. **Crossover/Crossdown**: Generic crossover detection
7. **API Integration**: REST endpoints for testing and integration

### 🔄 **Backward Compatibility**

The `DynamicStrategyConfigService` provides backward compatibility by:

1. **Mapping Methods**: All old method calls are mapped to new configuration
2. **Default Values**: Sensible defaults for missing configurations
3. **Legacy Support**: Can still access legacy configuration during transition

## Testing the Migration

### 1. Health Check

```bash
# Check if new configuration is loaded
curl http://localhost:8080/api/scalping-entry/health

# Check if old endpoints still work
curl http://localhost:8080/api/scalping-volume-surge/health
```

### 2. Configuration Validation

```bash
# Get new dynamic configuration
curl http://localhost:8080/api/dynamic-indicators/config

# Get scalping entry configuration
curl http://localhost:8080/api/scalping-entry/config
```

### 3. Strategy Evaluation

```bash
# Test new dynamic evaluation
curl -X POST http://localhost:8080/api/scalping-entry/evaluate \
  -H "Content-Type: application/json" \
  -d '{"instrumentToken": "256265", "lastTradedPrice": 19500.0}'

# Test old evaluation (should still work)
curl -X POST http://localhost:8080/api/scalping-volume-surge/evaluate \
  -H "Content-Type: application/json" \
  -d '{"instrumentToken": "256265", "lastTradedPrice": 19500.0}'
```

## Rollback Plan

If issues arise during migration:

1. **Keep Legacy Files**: Don't delete `StrategyConfigService.java` and `scalping-volume-surge-rules.json` immediately
2. **Gradual Migration**: Migrate services one by one
3. **Testing**: Test each service after migration
4. **Fallback**: Can revert to old configuration if needed

## Final Steps

After successful migration:

1. **Remove Legacy Files**:
   - Delete `src/main/java/com/jtradebot/processor/config/StrategyConfigService.java`
   - Delete `src/main/resources/rules/scalping-volume-surge-rules.json`

2. **Update Documentation**:
   - Update README files
   - Update API documentation
   - Update configuration guides

3. **Clean Up**:
   - Remove unused imports
   - Remove unused dependencies
   - Update build scripts if needed

## Verification Checklist

- [ ] All services use `DynamicStrategyConfigService`
- [ ] All endpoints return correct responses
- [ ] Configuration is loaded from new JSON files
- [ ] Legacy configuration is no longer referenced
- [ ] New dynamic features work correctly
- [ ] PUT strategy evaluation works
- [ ] Entry quality assessment works
- [ ] Market condition validation works
- [ ] Risk management calculations work
- [ ] API endpoints are accessible

## Support

If you encounter issues during migration:

1. Check the application logs for configuration loading errors
2. Verify JSON syntax in configuration files
3. Test individual endpoints to isolate issues
4. Use the health check endpoints to verify service status
5. Review the backward compatibility methods in `DynamicStrategyConfigService`
