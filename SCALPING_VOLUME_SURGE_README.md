# SCALPING_FUTURE_VOLUME_SURGE Strategy System

## 🎯 Overview

The **SCALPING_FUTURE_VOLUME_SURGE** system is a complete replacement for the previous CP-based trading strategy. This new system focuses exclusively on scalping opportunities based on volume surge detection and technical indicator alignment across multiple timeframes.

## 🏗️ Architecture

### Core Components

1. **FlattenedIndicators Model** - Holds all technical indicators as boolean/numeric values
2. **FuturesignalData Model** - Manages futuresignals for each timeframe (1min, 5min, 15min)
3. **ScalpingVolumeSurgeService** - Main service for strategy evaluation
4. **ScalpingVolumeSurgeController** - REST API endpoints for strategy evaluation
5. **Strategy Rules** - Configurable rules for CALL and PUT entries

### Key Features

- ✅ **Dual Strategy Support** - Both CALL and PUT entry strategies
- ✅ **Multi-Timeframe Analysis** - 1min, 5min, and 15min timeframes
- ✅ **Futuresignal Integration** - Bullish/bearish signals across all timeframes
- ✅ **Flattened Indicators** - Simple boolean evaluations for fast execution
- ✅ **Volume Surge Detection** - Configurable volume surge thresholds
- ✅ **Risk Management** - Built-in stop-loss and target management

## 📊 Strategy Logic

### CALL Entry Conditions (Bullish)

All of the following must be true:

1. **EMA Crossover**: EMA9 > EMA21 across timeframes
2. **RSI Overbought**: RSI > 70 across timeframes
3. **Volume Surge**: Volume > 2x average volume
4. **Price Action**: Price above VWAP and resistance levels
5. **Futuresignals**: All timeframes show bullish signals

### PUT Entry Conditions (Bearish)

All of the following must be true:

1. **EMA Crossover**: EMA9 < EMA21 across timeframes
2. **RSI Oversold**: RSI < 30 across timeframes
3. **Volume Surge**: Volume > 2x average volume
4. **Price Action**: Price below VWAP and support levels
5. **Futuresignals**: All timeframes show bearish signals

## 🔧 Configuration

### Strategy Rules

The strategy is configured via `src/main/resources/rules/scalping-volume-surge-rules.json`:

```json
{
  "callRule": {
    "minVolumeSurgeMultiplier": 2.0,
    "minRsiThreshold": 70.0,
    "stopLossPercentage": 0.5,
    "targetPercentage": 1.0
  },
  "putRule": {
    "minVolumeSurgeMultiplier": 2.0,
    "maxRsiThreshold": 30.0,
    "stopLossPercentage": 0.5,
    "targetPercentage": 1.0
  }
}
```

### Timeframes

- **1 Minute**: For immediate signal confirmation
- **5 Minutes**: Primary timeframe for scalping decisions
- **15 Minutes**: Trend confirmation and filtering

## 🚀 API Endpoints

### Strategy Evaluation

```http
POST /api/scalping-volume-surge/evaluate
Content-Type: application/json

{
  "instrumentToken": "123456",
  "lastTradedPrice": 18500.0,
  "tickTimestamp": "2024-01-01T09:15:00Z"
}
```

### Response Format

```json
{
  "instrumentToken": "123456",
  "timestamp": "2024-01-01T09:15:00Z",
  "lastTradedPrice": 18500.0,
  "recommendedStrategy": "CALL",
  "strategyConfidence": 0.85,
  "shouldMakeCallEntry": true,
  "shouldMakePutEntry": false,
  "flattenedIndicators": {
    "ema9_5min_gt_ema21_5min": true,
    "rsi_5min_gt_70": true,
    "volume_5min_surge": true,
    "price_gt_vwap_5min": true,
    "price_above_resistance": true
  }
}
```

### Health Check

```http
GET /api/scalping-volume-surge/health
```

### Strategy Rules

```http
GET /api/scalping-volume-surge/rules
```

## 📈 Indicator Flattening

### What Gets Flattened

1. **EMA Indicators**
   - `ema9_1min_gt_ema21_1min`: Boolean
   - `ema9_5min_gt_ema21_5min`: Boolean
   - `ema9_15min_gt_ema21_15min`: Boolean

2. **RSI Indicators**
   - `rsi_1min_gt_70`: Boolean (overbought)
   - `rsi_5min_gt_70`: Boolean (overbought)
   - `rsi_1min_lt_30`: Boolean (oversold)
   - `rsi_5min_lt_30`: Boolean (oversold)

3. **Volume Indicators**
   - `volume_1min_surge`: Boolean
   - `volume_5min_surge`: Boolean
   - `volume_surge_multiplier`: Double

4. **Price Action Indicators**
   - `price_gt_vwap_5min`: Boolean
   - `price_above_resistance`: Boolean
   - `price_below_support`: Boolean

5. **Futuresignals**
   - `oneMinBullishSurge`: Boolean
   - `fiveMinBullishSurge`: Boolean
   - `fifteenMinBullishSurge`: Boolean
   - `allTimeframesBullish`: Boolean
   - `allTimeframesBearish`: Boolean

## 🔄 Data Flow

```
Tick Input → BarSeries Creation → Indicator Calculation → Flattening → Rule Evaluation → Entry Decision
     ↓
1. Get BarSeries for 1min, 5min, 15min
2. Calculate EMA, RSI, Volume, VWAP, S/R
3. Flatten to boolean/numeric values
4. Evaluate CALL/PUT conditions
5. Return strategy recommendation
```

## 🎛️ Integration Points

### Existing System

- **TickDataManager**: Provides BarSeries data for different timeframes
- **MultiEmaIndicator**: EMA calculations
- **RsiIndicator**: RSI calculations
- **PriceVolumeSurgeIndicator**: Volume surge detection
- **VWAPIndicator**: VWAP calculations
- **SupportResistanceIndicator**: Support/Resistance levels

### New Components

- **ScalpingVolumeSurgeService**: Main strategy logic
- **FlattenedIndicators**: Data model for flattened indicators
- **FuturesignalData**: Futuresignal management
- **Strategy Rules**: Configurable entry conditions

## 🚫 What Was Removed

1. **CP (Call/Put) Calculation System**
   - `CPManager` class
   - `CallTrendPointFinder` and `PutTrendPointFinder`
   - `CallData`, `PutData` models
   - All CP-related scoring systems

2. **Multi-Strategy System**
   - `StrategyManager`
   - `TradeStrategyFactory`
   - `IntraDayTradeStrategy`
   - `ScalpingTradeStrategy` (old version)

3. **CP-Related Models & Events**
   - `CpDetails` model
   - `CpDetailsEvent`
   - CP-related Kafka topics

## 🔮 Future Enhancements

1. **Real Indicator Integration**: Connect to actual indicator calculations
2. **Dynamic Rule Loading**: Load rules from database/configuration
3. **Backtesting Framework**: Historical strategy performance analysis
4. **Machine Learning**: Adaptive threshold optimization
5. **Real-time Monitoring**: Live strategy performance dashboard

## 🧪 Testing

### Unit Tests

```bash
mvn test -Dtest=ScalpingVolumeSurgeServiceTest
```

### Integration Tests

```bash
mvn test -Dtest=ScalpingVolumeSurgeControllerTest
```

### Manual Testing

```bash
# Test strategy evaluation
curl -X POST http://localhost:8091/api/scalping-volume-surge/evaluate \
  -H "Content-Type: application/json" \
  -d '{"instrumentToken":"123456","lastTradedPrice":18500.0}'

# Check health
curl http://localhost:8091/api/scalping-volume-surge/health
```

## 📝 Notes

- The current implementation uses placeholder values for indicators
- Real indicator integration requires connecting to existing `BarSeries` data
- Futuresignals are simplified and need enhancement for production use
- Risk management parameters are configurable via JSON rules
- The system is designed to be easily extensible for new indicators

## 🤝 Contributing

1. Follow the existing code style and patterns
2. Add unit tests for new functionality
3. Update this README for any changes
4. Ensure all indicators are properly flattened
5. Maintain the dual strategy approach (CALL + PUT)
