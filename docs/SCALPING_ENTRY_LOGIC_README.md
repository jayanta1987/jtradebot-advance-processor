# Scalping Entry Logic System

## Overview

The Scalping Entry Logic System provides comprehensive entry evaluation for scalping CALL and PUT strategies with proper risk management, market condition validation, and entry quality assessment. This system ensures that only high-probability trades are executed with appropriate position sizing and risk controls.

## Key Features

### 🎯 **Comprehensive Entry Evaluation**
- **Mandatory Conditions**: Must-have conditions for entry approval
- **Optional Conditions**: Nice-to-have conditions that boost confidence
- **Confidence Scoring**: Multi-factor confidence calculation
- **Entry Quality Assessment**: A-F grading system for entry quality

### 📊 **Risk Management**
- **Position Sizing**: Dynamic position size calculation based on risk
- **Stop Loss & Targets**: Automatic calculation of risk levels
- **Trailing Stops**: Configurable trailing stop functionality
- **Break Even**: Automatic break-even management
- **Maximum Holding Time**: Time-based exit controls

### 🔄 **Market Condition Validation**
- **Spread Analysis**: Bid-ask spread validation
- **Liquidity Checks**: Volume-based liquidity assessment
- **Time Slot Avoidance**: Avoid high-risk time periods
- **Market Session Detection**: Opening, mid, and closing session handling

### 🎛️ **Entry Quality Assessment**
- **Volume Quality**: Volume surge strength evaluation
- **Momentum Quality**: RSI and momentum assessment
- **Trend Alignment**: Multi-timeframe trend analysis
- **Signal Strength**: Overall signal quality scoring

## Architecture

### Core Components

1. **ScalpingEntryLogic** - Configuration model for entry logic
2. **ScalpingEntryDecision** - Entry decision with all details
3. **ScalpingEntryService** - Service for entry evaluation
4. **MarketConditionValidation** - Market condition validation
5. **StopLossTargetLevels** - Risk level calculations

### Entry Evaluation Flow

```
Market Data → Dynamic Indicators → Entry Conditions → Quality Assessment → Risk Management → Entry Decision
```

## Configuration Structure

### Main Configuration File: `scalping-entry-config.json`

```json
{
  "callStrategy": {
    "entryConditions": {
      "mandatoryConditions": [
        "ema_1min_crossover",
        "ema_5min_crossover",
        "volume_1min_surge",
        "rsi_1min_gt_56",
        "price_1min_gt_vwap"
      ],
      "optionalConditions": [
        "ema_15min_crossover",
        "price_1min_above_resistance"
      ],
      "minMandatoryCount": 6,
      "minOptionalCount": 3,
      "minConfidenceScore": 0.75
    },
    "riskManagement": {
      "maxRiskPerTrade": 1.0,
      "stopLossPoints": 3.0,
      "targetPoints": 8.0,
      "maxHoldingTimeMinutes": 15,
      "useTrailingStop": true
    }
  }
}
```

## Entry Conditions

### CALL Strategy Mandatory Conditions

1. **EMA Crossover**: EMA 9 crosses above EMA 20 on 1min and 5min
2. **Volume Surge**: Volume surge on 1min and 5min timeframes
3. **RSI Bullish**: RSI above 56 on 1min and 5min
4. **Price Action**: Price above VWAP on 1min and 5min

### PUT Strategy Mandatory Conditions

1. **EMA Crossdown**: EMA 9 crosses below EMA 20 on 1min and 5min
2. **Volume Surge**: Volume surge on 1min and 5min timeframes
3. **RSI Bearish**: RSI below 44 on 1min and 5min
4. **Price Action**: Price below VWAP on 1min and 5min

### Optional Conditions

- **15min Timeframe**: Additional confirmation from 15min timeframe
- **Support/Resistance**: Price near key support/resistance levels
- **Volume Confirmation**: Strong volume confirmation across timeframes

## Risk Management

### Position Sizing

```java
Position Size = (Account Balance × Risk Per Trade %) ÷ Stop Loss Points
```

**Example:**
- Account Balance: ₹100,000
- Risk Per Trade: 1%
- Stop Loss: 3 points
- Position Size = (100,000 × 1%) ÷ 3 = 333 shares ≈ 300 shares (rounded to lot size)

### Stop Loss & Targets

**CALL Strategy:**
- Stop Loss: Entry Price - 3 points
- Target: Entry Price + 8 points
- Risk-Reward Ratio: 2.67:1

**PUT Strategy:**
- Stop Loss: Entry Price + 3 points
- Target: Entry Price - 8 points
- Risk-Reward Ratio: 2.67:1

### Trailing Stop

- **Enabled**: Yes
- **Trailing Percentage**: 0.2%
- **Break Even**: After 40% of target is reached

## Entry Quality Assessment

### Quality Grades

| Grade | Score Range | Description |
|-------|-------------|-------------|
| A | 0.9-1.0 | Excellent entry conditions - High probability trade |
| B | 0.8-0.9 | Good entry conditions - Above average probability |
| C | 0.7-0.8 | Average entry conditions - Moderate probability |
| D | 0.6-0.7 | Below average conditions - Lower probability |
| F | 0.0-0.6 | Poor conditions - Avoid entry |

### Quality Factors

1. **Volume Quality (25%)**
   - Volume surge multiplier
   - Volume trend consistency
   - Volume momentum

2. **Momentum Quality (25%)**
   - RSI strength and direction
   - Price momentum
   - Momentum consistency

3. **Trend Alignment (25%)**
   - Multi-timeframe trend alignment
   - EMA crossover strength
   - Trend consistency

4. **Signal Strength (25%)**
   - Overall signal quality
   - Signal confirmation
   - Signal strength

## Market Condition Validation

### Spread Analysis
- **Maximum Spread**: 0.1% of current price
- **Rejection**: If spread exceeds threshold

### Liquidity Requirements
- **Minimum Volume**: 1,000,000 shares
- **Volume Trend**: Increasing volume preferred

### Time Slot Avoidance
- **Opening Session**: 09:15-09:30 (high volatility)
- **Closing Session**: 15:15-15:30 (high volatility)
- **News Time**: Avoid during major news events

### Market Sessions
- **OPENING**: 09:15-10:00 (High volatility, avoid scalping)
- **MID**: 10:00-15:00 (Normal trading conditions)
- **CLOSING**: 15:00-15:30 (High volatility, avoid scalping)

## API Endpoints

### 1. Evaluate Scalping Entry
```http
POST /api/scalping-entry/evaluate
Content-Type: application/json

{
  "instrumentToken": "256265",
  "lastTradedPrice": 19500.0,
  "tickTimestamp": "2024-01-15T10:30:00Z"
}
```

**Response:**
```json
{
  "instrumentToken": "256265",
  "currentPrice": 19500.0,
  "marketConditions": {
    "isValid": true,
    "marketSession": "MID",
    "spreadPercentage": 0.05
  },
  "callDecision": {
    "shouldEnter": true,
    "entryPrice": 19500.0,
    "positionSize": 300,
    "stopLossPrice": 19497.0,
    "targetPrice": 19508.0,
    "confidenceScore": 0.85,
    "entryQuality": {
      "overallScore": 0.88,
      "qualityGrade": "B"
    }
  },
  "putDecision": {
    "shouldEnter": false,
    "rejectionReason": "Insufficient bearish conditions"
  },
  "bestEntry": {
    "strategyType": "CALL",
    "shouldEnter": true
  }
}
```

### 2. Evaluate CALL Entry Only
```http
POST /api/scalping-entry/evaluate-call
```

### 3. Evaluate PUT Entry Only
```http
POST /api/scalping-entry/evaluate-put
```

### 4. Calculate Position Size
```http
POST /api/scalping-entry/calculate-position
Content-Type: application/json

{
  "accountBalance": 100000,
  "riskPerTrade": 1.0,
  "stopLossPoints": 3.0,
  "currentPrice": 19500.0,
  "strategyType": "CALL"
}
```

### 5. Validate Market Conditions
```http
POST /api/scalping-entry/validate-market
Content-Type: application/json

{
  "currentPrice": 19500.0,
  "bidPrice": 19499.0,
  "askPrice": 19501.0,
  "volume": 1500000
}
```

### 6. Get Configuration
```http
GET /api/scalping-entry/config
```

### 7. Health Check
```http
GET /api/scalping-entry/health
```

## Usage Examples

### Basic Entry Evaluation

```java
// Load configurations
DynamicIndicatorConfig indicatorConfig = dynamicIndicatorFlattenerService.loadConfiguration("rules/dynamic-indicators-config.json");
ScalpingEntryLogic entryLogic = scalpingEntryService.loadEntryLogic("rules/scalping-entry-config.json");

// Flatten indicators
DynamicFlattenedIndicators flattenedIndicators = dynamicIndicatorFlattenerService.flattenIndicators(tickDocument, indicatorConfig);

// Evaluate entries
ScalpingEntryDecision callDecision = scalpingEntryService.evaluateCallEntry(flattenedIndicators, indicatorConfig, currentPrice, currentTime);
ScalpingEntryDecision putDecision = scalpingEntryService.evaluatePutEntry(flattenedIndicators, indicatorConfig, currentPrice, currentTime);

// Check if entry is approved
if (callDecision.isShouldEnter()) {
    System.out.println("CALL Entry Approved: " + callDecision.getEntryReason());
    System.out.println("Position Size: " + callDecision.getPositionSize());
    System.out.println("Stop Loss: " + callDecision.getStopLossPrice());
    System.out.println("Target: " + callDecision.getTargetPrice());
}
```

### Position Size Calculation

```java
int positionSize = scalpingEntryService.calculatePositionSize(
    100000,  // Account balance
    1.0,     // Risk per trade %
    3.0,     // Stop loss points
    19500.0  // Current price
);

StopLossTargetLevels levels = scalpingEntryService.calculateStopLossTarget(
    19500.0,  // Entry price
    "CALL",   // Strategy type
    3.0,      // Stop loss points
    8.0       // Target points
);
```

### Market Condition Validation

```java
MarketConditionValidation validation = scalpingEntryService.validateMarketConditions(
    LocalDateTime.now(),
    19500.0,  // Current price
    19499.0,  // Bid price
    19501.0,  // Ask price
    1500000L  // Volume
);

if (validation.isValid()) {
    System.out.println("Market conditions are suitable for entry");
} else {
    System.out.println("Market conditions rejected: " + validation.getRejectionReason());
}
```

## Configuration Management

### Modifying Entry Conditions

To modify entry conditions, update the JSON configuration:

```json
{
  "callStrategy": {
    "entryConditions": {
      "mandatoryConditions": [
        "ema_1min_crossover",
        "ema_5min_crossover",
        "volume_1min_surge",
        "rsi_1min_gt_60",  // Changed from 56 to 60
        "price_1min_gt_vwap"
      ],
      "minMandatoryCount": 5,  // Changed from 6 to 5
      "minConfidenceScore": 0.8  // Changed from 0.75 to 0.8
    }
  }
}
```

### Adjusting Risk Parameters

```json
{
  "callStrategy": {
    "riskManagement": {
      "maxRiskPerTrade": 0.5,  // Changed from 1.0 to 0.5
      "stopLossPoints": 2.0,   // Changed from 3.0 to 2.0
      "targetPoints": 6.0,     // Changed from 8.0 to 6.0
      "maxHoldingTimeMinutes": 10  // Changed from 15 to 10
    }
  }
}
```

### Market Condition Adjustments

```json
{
  "callStrategy": {
    "marketConditions": {
      "maxSpreadPercentage": 0.05,  // Changed from 0.1 to 0.05
      "minLiquidityThreshold": 2000000,  // Changed from 1000000 to 2000000
      "avoidTimeSlots": ["09:15-09:45", "15:00-15:30"]  // Extended time slots
    }
  }
}
```

## Best Practices

### 1. **Conservative Approach**
- Start with higher confidence thresholds
- Use smaller position sizes initially
- Implement strict market condition validation

### 2. **Risk Management**
- Never risk more than 1% per trade
- Always use stop losses
- Implement trailing stops for profit protection

### 3. **Market Timing**
- Avoid trading during opening/closing sessions
- Monitor market volatility
- Check for news events

### 4. **Quality Assessment**
- Only trade A and B grade entries
- Monitor entry quality trends
- Adjust thresholds based on performance

### 5. **Continuous Monitoring**
- Track entry success rates
- Monitor market condition effectiveness
- Adjust parameters based on results

## Performance Monitoring

### Key Metrics to Track

1. **Entry Success Rate**: Percentage of profitable entries
2. **Average Quality Score**: Overall entry quality performance
3. **Market Condition Rejection Rate**: How often market conditions reject entries
4. **Average Holding Time**: Time between entry and exit
5. **Risk-Reward Performance**: Actual vs expected risk-reward ratios

### Performance Optimization

1. **Threshold Adjustment**: Fine-tune confidence and quality thresholds
2. **Condition Refinement**: Add or remove entry conditions based on performance
3. **Market Condition Tuning**: Adjust market condition parameters
4. **Risk Management Optimization**: Optimize position sizing and stop loss levels

## Troubleshooting

### Common Issues

1. **No Entries Being Generated**
   - Check if mandatory conditions are too strict
   - Verify market condition thresholds
   - Review confidence score requirements

2. **Too Many False Signals**
   - Increase confidence score threshold
   - Add more mandatory conditions
   - Tighten market condition validation

3. **Poor Entry Quality**
   - Review quality assessment parameters
   - Check indicator calculation accuracy
   - Verify data quality

4. **Risk Management Issues**
   - Review position sizing calculation
   - Check stop loss and target levels
   - Verify risk per trade percentage

### Debug Endpoints

Use the health check endpoint to verify system status:
```http
GET /api/scalping-entry/health
```

## Conclusion

The Scalping Entry Logic System provides a robust, configurable, and comprehensive solution for scalping entry evaluation. It ensures that only high-quality trades are executed with proper risk management and market condition validation. The system is designed to be flexible and can be easily adjusted based on market conditions and performance requirements.
