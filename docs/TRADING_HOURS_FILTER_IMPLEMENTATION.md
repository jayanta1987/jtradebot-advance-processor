# Trading Hours Filter and Market End Scheduler Implementation

## Overview

This implementation adds two key features to the trading system:

1. **Trading Hours Filter**: A mandatory filter that prevents trading entries outside of specified trading hours
2. **Market End Scheduler**: An automated scheduler that closes all active trades at market end time

## Features

### 1. Trading Hours Filter
- **Type**: Mandatory filter (cannot be bypassed)
- **Default Hours**: 9:15 AM to 3:30 PM IST (Indian Standard Time)
- **Purpose**: Ensures trading only occurs during market hours
- **Priority**: 10 (highest priority to ensure it's checked first)

### 2. Market End Scheduler
- **Schedule**: Every weekday at 3:30 PM IST
- **Purpose**: Automatically closes all active trades at market end
- **Exit Reason**: `MARKET_END_CLOSURE`
- **Safety Check**: Verifies market is actually closed before executing

## Implementation Details

### 1. Configuration Updates

#### ScalpingEntryConfig.java
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public static class NoTradeFilter {
    // ... existing fields ...
    
    // Trading hours fields
    private Integer startHour;
    private Integer startMinute;
    private Integer endHour;
    private Integer endMinute;
}
```

#### Entry Configuration (scalping-entry-config.json)
```json
{
  "tradingHours": {
    "enabled": true,
    "mandatory": true,
    "name": "Trading Hours Filter",
    "description": "Filter out entries outside of trading hours (9:15 AM to 3:30 PM IST)",
    "priority": 10,
    "startHour": 9,
    "startMinute": 15,
    "endHour": 15,
    "endMinute": 30
  }
}
```

### 2. Filter Implementation

#### UnstableMarketConditionAnalysisService.java
```java
case "tradingHours":
    // Check if current time is within trading hours
    boolean withinTradingHours = DateTimeHandler.withinTradingHours(
        filter.getStartHour(), filter.getStartMinute(), 
        filter.getEndHour(), filter.getEndMinute(), 
        tick.getTickTimestamp()
    );
    passed = withinTradingHours; // Filter passes when within trading hours
    details = String.format("Trading hours check: %s (Time: %s, Start: %02d:%02d, End: %02d:%02d)", 
            withinTradingHours ? "WITHIN" : "OUTSIDE", 
            new java.text.SimpleDateFormat("HH:mm:ss").format(tick.getTickTimestamp()),
            filter.getStartHour(), filter.getStartMinute(),
            filter.getEndHour(), filter.getEndMinute());
    break;
```

### 3. Market End Scheduler

#### MarketEndSchedulerService.java
```java
@Service
@Slf4j
@RequiredArgsConstructor
public class MarketEndSchedulerService {
    
    private final ExitStrategyService exitStrategyService;
    
    /**
     * Close all active trades at market end time (3:30 PM IST)
     * Runs every day at 3:30 PM IST
     */
    @Scheduled(cron = "0 30 15 * * MON-FRI") // Every weekday at 3:30 PM IST
    public void closeAllActiveTradesAtMarketEnd() {
        // Implementation details...
    }
}
```

### 4. Exit Reason Enum Update

#### ExitReasonEnum.java
```java
public enum ExitReasonEnum {
    // ... existing reasons ...
    FORCE_EXIT,
    MARKET_END_CLOSURE  // New exit reason for market end closures
}
```

## Configuration Options

### 1. Trading Hours Filter
```json
{
  "enabled": true,        // Enable/disable the filter
  "mandatory": true,      // Must be true for trading hours
  "priority": 10,         // Highest priority
  "startHour": 9,         // Market start hour
  "startMinute": 15,      // Market start minute
  "endHour": 15,          // Market end hour
  "endMinute": 30         // Market end minute
}
```

### 2. Scheduler Configuration
- **Cron Expression**: `0 30 15 * * MON-FRI` (Every weekday at 3:30 PM IST)
- **Timezone**: Asia/Kolkata (IST)
- **Safety Check**: Verifies market is closed before executing

## Usage Examples

### 1. Trading Hours Validation
- **9:00 AM**: ❌ Entry blocked (before market open)
- **9:15 AM**: ✅ Entry allowed (market open)
- **12:00 PM**: ✅ Entry allowed (during market hours)
- **3:30 PM**: ✅ Entry allowed (market close)
- **3:31 PM**: ❌ Entry blocked (after market close)
- **4:00 PM**: ❌ Entry blocked (after market close)

### 2. Market End Closure
- **3:30 PM**: Scheduler triggers
- **Active Orders**: All active orders are closed
- **Exit Reason**: `MARKET_END_CLOSURE`
- **Logging**: Detailed logs for each closed order

## Logging

### 1. Trading Hours Filter Logs
```
🔍 FILTER CHECK - tradingHours: PASS - Trading hours check: WITHIN (Time: 10:30:00, Start: 09:15, End: 15:30)
🔍 FILTER CHECK - tradingHours: FAIL - Trading hours check: OUTSIDE (Time: 08:30:00, Start: 09:15, End: 15:30)
```

### 2. Market End Scheduler Logs
```
🕒 MARKET END SCHEDULER - Starting market end trade closure process
🕒 MARKET END SCHEDULER - Found 3 active orders to close
🕒 MARKET END SCHEDULER - Closing order: order123 (Type: CALL_BUY, Symbol: NIFTY24AUG24500CE)
🕒 MARKET END SCHEDULER - Successfully closed order: order123
🕒 MARKET END SCHEDULER - Market end trade closure process completed
```

## Testing

### 1. Unit Tests
- **File**: `TradingHoursFilterTest.java`
- **Coverage**: 6 test methods covering various scenarios
- **Test Cases**:
  - Within trading hours
  - Before trading hours
  - After trading hours
  - At market open
  - At market close
  - One minute after market close

### 2. Test Scenarios
```java
@Test
void testTradingHoursFilter_WithinTradingHours_ShouldPass() {
    // Test during trading hours (10:30 AM)
    Tick tick = createTickWithTime(10, 30, 0);
    // ... test implementation
}

@Test
void testTradingHoursFilter_BeforeTradingHours_ShouldFail() {
    // Test before trading hours (8:30 AM)
    Tick tick = createTickWithTime(8, 30, 0);
    // ... test implementation
}
```

## Benefits

### 1. Risk Management
- **Prevents Overnight Positions**: No trades outside market hours
- **Automatic Cleanup**: All positions closed at market end
- **Regulatory Compliance**: Adheres to market trading hours

### 2. System Reliability
- **Mandatory Filter**: Cannot be accidentally bypassed
- **Automated Closure**: No manual intervention required
- **Comprehensive Logging**: Full audit trail of closures

### 3. Operational Efficiency
- **Scheduled Execution**: Runs automatically at market end
- **Error Handling**: Graceful handling of closure failures
- **Performance**: Efficient batch processing of orders

## Future Enhancements

### 1. Dynamic Trading Hours
- **Holiday Detection**: Automatic holiday adjustment
- **Extended Hours**: Support for pre/post market trading
- **Time Zone Support**: Multiple market time zones

### 2. Advanced Scheduling
- **Flexible Timing**: Configurable closure times
- **Partial Closures**: Close specific order types
- **Manual Override**: Emergency closure capabilities

### 3. Enhanced Monitoring
- **Real-time Status**: Live trading hours status
- **Alert System**: Notifications for closures
- **Performance Metrics**: Closure success rates
