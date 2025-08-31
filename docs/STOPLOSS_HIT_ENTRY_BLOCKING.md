# STOPLOSS_HIT Entry Blocking Feature

## Overview

This feature prevents the system from taking new entries in the same 5-minute candle after an order exits with `STOPLOSS_HIT`. This is implemented using in-memory tracking to avoid database calls and ensure fast performance.

## Why This Feature?

- **Risk Management**: Prevents rapid re-entry after a stop loss hit, which could lead to consecutive losses
- **Market Condition Awareness**: If a stop loss was hit, it indicates unfavorable market conditions in that time period
- **Performance**: Uses in-memory tracking instead of database queries for fast execution

## How It Works

### 1. Exit Tracking
When an order exits, the system tracks:
- **Exit Reason**: Only `STOPLOSS_HIT` triggers the blocking mechanism
- **Exit Time**: Timestamp of when the exit occurred
- **5-Minute Candle Key**: Rounded down to the nearest 5-minute boundary (format: `yyyy-MM-dd-HH-mm`)

### 2. Entry Validation
Before creating a new order, the system checks:
- If the last exit was due to `STOPLOSS_HIT`
- If the current time is in the same 5-minute candle as the last exit
- If both conditions are true, entry is blocked

### 3. Candle Boundary Logic
The system rounds timestamps down to 5-minute boundaries:
- `10:12:30` → `10:10:00` (candle key: `2024-01-15-10-10`)
- `10:14:59` → `10:10:00` (candle key: `2024-01-15-10-10`)
- `10:15:00` → `10:15:00` (candle key: `2024-01-15-10-15`)
- `10:19:30` → `10:15:00` (candle key: `2024-01-15-10-15`)

## Implementation Details

### Key Classes Modified

1. **ExitStrategyService.java**
   - Added in-memory tracking fields
   - Added `shouldBlockEntryAfterStopLoss()` method
   - Added `updateExitTracking()` method
   - Added validation in `createOrderEntry()` methods (final blocking point)

### Key Methods

#### `shouldBlockEntryAfterStopLoss(Date currentTime)`
- Returns `true` if entry should be blocked
- Returns `false` if entry is allowed
- Only blocks for `STOPLOSS_HIT` exits in the same 5-minute candle

#### `updateExitTracking(ExitReasonEnum exitReason, Date exitTime)`
- Updates in-memory tracking when an order exits
- Generates 5-minute candle key for the exit time

#### `resetExitTracking()`
- Clears all exit tracking data
- Useful for testing or manual reset

## Implementation Flow

The blocking check is implemented at the **final point** where orders are created:

**Order Creation Level** (`ExitStrategyService.createOrderEntry()` and `createOrderEntryWithScenario()`)
- Final blocking point where all signals have passed
- Returns `null` for blocked orders, preventing any order creation
- Most efficient approach - no unnecessary checks at earlier stages

## Usage Examples

### Scenario 1: Same Candle Entry Blocked
```
10:12:30 - Order exits with STOPLOSS_HIT
10:12:45 - New entry signal generated → BLOCKED ❌
10:13:00 - New entry signal generated → BLOCKED ❌
10:15:00 - New entry signal generated → ALLOWED ✅
```

### Scenario 2: Different Exit Reasons
```
10:12:30 - Order exits with TARGET_HIT
10:12:45 - New entry signal generated → ALLOWED ✅
```

### Scenario 3: No Previous Exit
```
10:12:45 - New entry signal generated → ALLOWED ✅
```

## Logging

The system provides clear logging for debugging:

```
🚫 ENTRY BLOCKED - Recent STOPLOSS_HIT exit in same 5-min candle. Last exit: 15 Jan 2024 10:12:30 IST at candle: 2024-01-15-10-10
✅ ENTRY ALLOWED - Last STOPLOSS_HIT exit was in different 5-min candle. Current: 2024-01-15-10-15, Last: 2024-01-15-10-10
📊 EXIT TRACKING UPDATED - Reason: STOPLOSS_HIT, Time: 15 Jan 2024 10:12:30 IST, Candle: 2024-01-15-10-10
```

## Testing

A comprehensive test suite is included in `ExitStrategyServiceTest.java` that covers:
- Same candle blocking
- Different candle allowing
- Different exit reasons
- No previous exit scenarios
- Candle boundary calculations
- Reset functionality

## Configuration

No additional configuration is required. The feature is enabled by default and uses IST timezone for all calculations.

## Performance Impact

- **Memory**: Minimal - only stores 3 fields per service instance
- **CPU**: Negligible - simple timestamp comparison and string operations
- **Database**: None - completely in-memory implementation
- **Latency**: Minimal - adds microseconds to entry validation

## Future Enhancements

Potential improvements could include:
- Configurable candle timeframes (1min, 3min, 15min, etc.)
- Different blocking rules for different exit reasons
- Persistence of exit tracking across application restarts
- Integration with risk management rules
