# High Quality Entry Logic Implementation

## Overview

The High Quality Entry Logic is a dynamic feature that relaxes category requirements when the quality score is exceptionally high. This allows for more flexible entry conditions when the system has high confidence in the trade signal.

## Configuration

The feature is configured in `src/main/resources/rules/scalping-entry-config.json`:

```json
{
  "profitableTradeFilter": {
    "highQualityEntryLogic": {
      "enabled": true,
      "highQualityThreshold": 8.0,
      "relaxedCategoryRequirement": 1,
      "description": "When QualityScore >= 8.0, relax category requirements to just 1 condition per category instead of minCount"
    }
  }
}
```

### Configuration Parameters

- **enabled**: Boolean to enable/disable the feature
- **highQualityThreshold**: Quality score threshold (default: 8.0)
- **relaxedCategoryRequirement**: Number of conditions required per category when high quality logic is applied (default: 1)
- **description**: Human-readable description of the feature

## How It Works

### Normal Entry Logic (Quality Score < 8.0)
- Each category must meet its configured `minCount` requirements:
  - EMA category: 2 out of 3 conditions
  - FutureAndVolume category: 4 out of 7 conditions
  - Candlestick category: 3 out of 10 conditions
  - Momentum category: 2 out of 3 conditions

### High Quality Entry Logic (Quality Score >= 8.0)
- All categories are relaxed to require only **1 condition per category**
- This allows entries when the quality score is very high, even if not all category conditions are met
- The system still requires at least 1 condition from each category to ensure basic signal validation

## Implementation Details

### Model Changes

1. **ProfitableTradeFilterConfig.java**: Added `HighQualityEntryLogic` configuration class
2. **ProfitableTradeFilterResult.java**: Added `highQualityEntryLogicApplied` field to track when the feature is used

### Service Changes

1. **ProfitableTradeFilterServiceImpl.java**: 
   - Modified `evaluateCategory()` method to accept quality score parameter
   - Added `getRequiredCountForCategory()` method to determine requirements based on quality score
   - Added `isHighQualityEntryLogicApplied()` helper method
   - Updated both `evaluateCallEntry()` and `evaluatePutEntry()` methods

### Logging

The system logs when high quality entry logic is applied:

```
🎯 HIGH QUALITY ENTRY LOGIC APPLIED - Quality Score: 8.5 >= 8.0 | Using relaxed requirement: 1 (instead of 2)
```

And the result summary includes `[HQ]` indicator:

```
PROFITABLE_ENTRY [HQ] - CALL | Quality: 8.5 | Candlestick: 5.0 | Volume: 15.5x | Categories: EMA:✓, FV:✓, CS:✓, M:✓
```

## Testing

The implementation includes comprehensive tests in `ProfitableTradeFilterServiceTest.java`:

1. **High Quality Test**: Verifies that when quality score >= 8.0, relaxed requirements (1 condition per category) are applied
2. **Normal Quality Test**: Verifies that when quality score < 8.0, normal strict requirements are applied

## Benefits

1. **Flexibility**: Allows entries in high-confidence scenarios even with fewer category conditions
2. **Risk Management**: Still maintains minimum validation (1 condition per category)
3. **Configurable**: All thresholds and requirements can be adjusted via JSON configuration
4. **Transparent**: Clear logging shows when the feature is applied
5. **Backward Compatible**: Existing behavior is preserved when feature is disabled

## Usage Examples

### Scenario 1: High Quality Signal (Quality Score: 8.5)
- **Result**: High quality entry logic applied
- **Requirements**: 1 condition per category
- **Outcome**: Entry allowed with relaxed requirements

### Scenario 2: Normal Quality Signal (Quality Score: 7.6)
- **Result**: Normal entry logic applied
- **Requirements**: Full minCount requirements per category
- **Outcome**: Entry follows strict category validation

### Scenario 3: Low Quality Signal (Quality Score: 7.0)
- **Result**: Normal entry logic applied
- **Requirements**: Full minCount requirements per category
- **Outcome**: Entry likely rejected due to insufficient category conditions

## Configuration Management

The feature can be easily adjusted by modifying the JSON configuration:

- **Increase threshold**: Set `highQualityThreshold` to 8.5 for more strict high quality requirements
- **Decrease threshold**: Set `highQualityThreshold` to 7.5 for more frequent high quality logic application
- **Disable feature**: Set `enabled` to `false`
- **Adjust relaxed requirements**: Change `relaxedCategoryRequirement` to 2 for slightly stricter relaxed requirements
