# 📊 Dynamic Indicators System Documentation

## 🎯 Overview

This document provides a comprehensive explanation of all configured indicators in the Dynamic Indicators System. The system uses a category-based approach where entry signals require confirmation from multiple indicator families.

## 🏗️ System Architecture

### Entry Logic Requirements
- **CALL (Bullish) Entries**: Require 4 out of 5 categories to be satisfied
- **PUT (Bearish) Entries**: Require 4 out of 5 categories to be satisfied
- **Priority Timeframe**: 5-minute (configurable)
- **Real-time Processing**: Calculated on each tick

---

## 📈 EMA (Exponential Moving Average) Indicators

### Trend Identification Indicators

#### Bullish Trend Indicators
| Indicator | Timeframe | Description |
|-----------|-----------|-------------|
| `ema200_trend_15min` | 15min | Checks if current price is above EMA200 (overall trend identification) |
| `ema34_trend_5min` | 5min | Checks if current price is above EMA34 (medium-term trend) |
| `ema9_trend_5min` | 5min | Checks if current price is above EMA9 (short-term trend) |

#### Bearish Trend Indicators
| Indicator | Timeframe | Description |
|-----------|-----------|-------------|
| `ema200_trend_bearish_15min` | 15min | Checks if current price is below EMA200 (overall bearish trend) |
| `ema34_trend_bearish_5min` | 5min | Checks if current price is below EMA34 (medium-term bearish) |
| `ema9_trend_bearish_5min` | 5min | Checks if current price is below EMA9 (short-term bearish) |

### Candle Touch Patterns (Key Strategy)

#### Bullish Touch Indicators
| Indicator | Timeframe | Description |
|-----------|-----------|-------------|
| `ema5_touch_5min` | 5min | Checks if current price is touching EMA5 (within 0.1% tolerance) |
| `ema5_touch_3min` | 3min | Checks if current price is touching EMA5 (within 0.1% tolerance) |
| `ema5_touch_1min` | 1min | Checks if current price is touching EMA5 (within 0.1% tolerance) |

#### Bearish Touch Indicators
| Indicator | Timeframe | Description |
|-----------|-----------|-------------|
| `ema5_touch_bearish_5min` | 5min | Checks if current price is touching EMA5 from below |

### EMA Relationship Comparisons

#### Bullish EMA Alignments
| Indicator | Timeframe | Description |
|-----------|-----------|-------------|
| `ema9_above_ema34_5min` | 5min | Checks if EMA9 is above EMA34 (bullish alignment) |
| `ema9_above_ema34_3min` | 3min | Checks if EMA9 is above EMA34 (bullish alignment) |
| `ema5_above_ema9_5min` | 5min | Checks if EMA5 is above EMA9 (immediate bullish) |
| `ema5_above_ema9_3min` | 3min | Checks if EMA5 is above EMA9 (immediate bullish) |

#### Bearish EMA Alignments
| Indicator | Timeframe | Description |
|-----------|-----------|-------------|
| `ema9_below_ema34_5min` | 5min | Checks if EMA9 is below EMA34 (bearish alignment) |
| `ema9_below_ema34_3min` | 3min | Checks if EMA9 is below EMA34 (bearish alignment) |
| `ema5_below_ema9_5min` | 5min | Checks if EMA5 is below EMA9 (immediate bearish) |
| `ema5_below_ema9_3min` | 3min | Checks if EMA5 is below EMA9 (immediate bearish) |

---

## 📊 RSI (Relative Strength Index) Indicators

### Bullish RSI Indicators
| Indicator | Timeframe | Threshold | Description |
|-----------|-----------|-----------|-------------|
| `rsi_bullish_5min` | 5min | > 56 | Checks if RSI is above 56 (bullish momentum) |
| `rsi_bullish_3min` | 3min | > 56 | Checks if RSI is above 56 (bullish momentum) |

### Bearish RSI Indicators
| Indicator | Timeframe | Threshold | Description |
|-----------|-----------|-----------|-------------|
| `rsi_bearish_5min` | 5min | < 44 | Checks if RSI is below 44 (bearish momentum) |
| `rsi_bearish_3min` | 3min | < 44 | Checks if RSI is below 44 (bearish momentum) |

---

## 📈 Volume Indicators

### Volume Surge Detection
| Indicator | Timeframe | Multiplier | Description |
|-----------|-----------|------------|-------------|
| `volume_surge_5min` | 5min | 2.0x | Checks if current volume is 2x above average volume |
| `volume_surge_3min` | 3min | 2.0x | Checks if current volume is 2x above average volume |

---

## 💰 Price Action Indicators

### VWAP (Volume Weighted Average Price) Indicators

#### Bullish VWAP
| Indicator | Timeframe | Description |
|-----------|-----------|-------------|
| `price_above_vwap_5min` | 5min | Checks if current price is above VWAP |
| `price_above_vwap_3min` | 3min | Checks if current price is above VWAP |

#### Bearish VWAP
| Indicator | Timeframe | Description |
|-----------|-----------|-------------|
| `price_below_vwap_5min` | 5min | Checks if current price is below VWAP |
| `price_below_vwap_3min` | 3min | Checks if current price is below VWAP |

### Support & Resistance Indicators

#### Bullish (Above Resistance)
| Indicator | Timeframe | Description |
|-----------|-----------|-------------|
| `price_above_resistance_5min` | 5min | Checks if current price is above recent resistance level |
| `price_above_resistance_3min` | 3min | Checks if current price is above recent resistance level |

#### Bearish (Below Support)
| Indicator | Timeframe | Description |
|-----------|-----------|-------------|
| `price_below_support_5min` | 5min | Checks if current price is below recent support level |
| `price_below_support_3min` | 3min | Checks if current price is below recent support level |

---

## 🕯️ Candlestick Pattern Indicators

### Bullish Patterns

#### Single Candle Patterns
| Indicator | Timeframe | Pattern Type | Description |
|-----------|-----------|--------------|-------------|
| `hammer_5min` | 5min | Hammer | Detects hammer pattern (bullish reversal) |
| `hammer_3min` | 3min | Hammer | Detects hammer pattern (bullish reversal) |
| `bullish_marubozu_5min` | 5min | Marubozu | Detects bullish marubozu (strong momentum) |
| `long_lower_shadow_5min` | 5min | Long Shadow | Detects long lower shadow (rejection of lower prices) |

#### Multi-Candle Patterns
| Indicator | Timeframe | Pattern Type | Bars Required | Description |
|-----------|-----------|--------------|---------------|-------------|
| `bullish_engulfing_5min` | 5min | Engulfing | 2 | Detects bullish engulfing (strong reversal) |
| `bullish_engulfing_3min` | 3min | Engulfing | 2 | Detects bullish engulfing (strong reversal) |
| `bullish_morning_star_5min` | 5min | Morning Star | 3 | Detects bullish morning star (3-candle reversal) |
| `bullish_morning_star_3min` | 3min | Morning Star | 3 | Detects bullish morning star (3-candle reversal) |

### Bearish Patterns

#### Single Candle Patterns
| Indicator | Timeframe | Pattern Type | Description |
|-----------|-----------|--------------|-------------|
| `shooting_star_5min` | 5min | Shooting Star | Detects shooting star pattern (bearish reversal) |
| `shooting_star_3min` | 3min | Shooting Star | Detects shooting star pattern (bearish reversal) |
| `bearish_marubozu_5min` | 5min | Marubozu | Detects bearish marubozu (strong momentum) |
| `long_upper_shadow_5min` | 5min | Long Shadow | Detects long upper shadow (rejection of higher prices) |

#### Multi-Candle Patterns
| Indicator | Timeframe | Pattern Type | Bars Required | Description |
|-----------|-----------|--------------|---------------|-------------|
| `bearish_engulfing_5min` | 5min | Engulfing | 2 | Detects bearish engulfing (strong reversal) |
| `bearish_engulfing_3min` | 3min | Engulfing | 2 | Detects bearish engulfing (strong reversal) |
| `bearish_evening_star_5min` | 5min | Evening Star | 3 | Detects bearish evening star (3-candle reversal) |
| `bearish_evening_star_3min` | 3min | Evening Star | 3 | Detects bearish evening star (3-candle reversal) |

---

## 🎯 Category-Based Entry Logic

### CALL (Bullish) Entry Requirements

**Required: 4 out of 5 categories must be satisfied**

#### 1. EMA_TREND Category
- **Purpose**: Confirm trend direction using EMA analysis
- **Required**: At least 1 indicator must be satisfied
- **Indicators**:
  - `ema200_trend_15min`
  - `ema34_trend_5min`
  - `ema9_trend_5min`
  - `ema5_touch_5min`
  - `ema9_above_ema34_5min`
  - `ema5_above_ema9_5min`

#### 2. VOLUME Category
- **Purpose**: Confirm volume surge for momentum
- **Required**: At least 1 indicator must be satisfied
- **Indicators**:
  - `volume_surge_5min`
  - `volume_surge_3min`

#### 3. RSI Category
- **Purpose**: Confirm momentum using RSI
- **Required**: At least 1 indicator must be satisfied
- **Indicators**:
  - `rsi_bullish_5min`
  - `rsi_bullish_3min`

#### 4. PRICE_ACTION Category
- **Purpose**: Confirm price action above key levels
- **Required**: At least 1 indicator must be satisfied
- **Indicators**:
  - `price_above_vwap_5min`
  - `price_above_vwap_3min`
  - `price_above_resistance_5min`
  - `price_above_resistance_3min`

#### 5. CANDLESTICK Category
- **Purpose**: Confirm bullish candlestick patterns
- **Required**: At least 1 indicator must be satisfied
- **Indicators**:
  - `bullish_engulfing_5min`
  - `bullish_engulfing_3min`
  - `bullish_morning_star_5min`
  - `bullish_morning_star_3min`
  - `hammer_5min`
  - `hammer_3min`
  - `bullish_marubozu_5min`
  - `long_lower_shadow_5min`

### PUT (Bearish) Entry Requirements

**Required: 4 out of 5 categories must be satisfied**

#### 1. EMA_TREND Category
- **Purpose**: Confirm bearish trend direction
- **Required**: At least 1 indicator must be satisfied
- **Indicators**:
  - `ema200_trend_bearish_15min`
  - `ema34_trend_bearish_5min`
  - `ema9_trend_bearish_5min`
  - `ema5_touch_bearish_5min`
  - `ema9_below_ema34_5min`
  - `ema5_below_ema9_5min`

#### 2. VOLUME Category
- **Purpose**: Confirm volume surge for momentum
- **Required**: At least 1 indicator must be satisfied
- **Indicators**:
  - `volume_surge_5min`
  - `volume_surge_3min`

#### 3. RSI Category
- **Purpose**: Confirm bearish momentum using RSI
- **Required**: At least 1 indicator must be satisfied
- **Indicators**:
  - `rsi_bearish_5min`
  - `rsi_bearish_3min`

#### 4. PRICE_ACTION Category
- **Purpose**: Confirm price action below key levels
- **Required**: At least 1 indicator must be satisfied
- **Indicators**:
  - `price_below_vwap_5min`
  - `price_below_vwap_3min`
  - `price_below_support_5min`
  - `price_below_support_3min`

#### 5. CANDLESTICK Category
- **Purpose**: Confirm bearish candlestick patterns
- **Required**: At least 1 indicator must be satisfied
- **Indicators**:
  - `bearish_engulfing_5min`
  - `bearish_engulfing_3min`
  - `bearish_evening_star_5min`
  - `bearish_evening_star_3min`
  - `shooting_star_5min`
  - `shooting_star_3min`
  - `bearish_marubozu_5min`
  - `long_upper_shadow_5min`

---

## 🔧 Technical Implementation

### Indicator Types Supported
- **EMA**: Exponential Moving Average calculations
- **RSI**: Relative Strength Index calculations
- **VOLUME**: Volume surge detection
- **VWAP**: Volume Weighted Average Price
- **SUPPORT_RESISTANCE**: Support and resistance level detection
- **CANDLESTICK**: Candlestick pattern recognition

### Comparison Operations
- **GT**: Greater Than
- **LT**: Less Than
- **EQ**: Equal To
- **TREND_ABOVE**: Price above specific EMA
- **CANDLE_TOUCHES**: Price touching specific EMA
- **CROSSOVER**: EMA crossover detection
- **CROSSDOWN**: EMA crossdown detection

### Timeframes Supported
- **1min**: 1-minute candles
- **3min**: 3-minute candles
- **5min**: 5-minute candles (priority)
- **15min**: 15-minute candles

---

## 📊 Configuration Files

### Primary Configuration
- **File**: `src/main/resources/rules/dynamic-indicators-config.json`
- **Purpose**: Defines all indicator configurations and entry logic
- **Note**: Threshold values (RSI, Volume, etc.) are configured in `scalping-entry-config.json`

### Entry Logic Configuration
- **File**: `src/main/resources/rules/scalping-entry-config.json`
- **Purpose**: Defines risk management and entry quality parameters

---

## 🚀 Benefits

1. **Balanced Confirmation**: Requires multiple types of analysis to agree
2. **Reduced False Signals**: Multi-category confirmation reduces noise
3. **Flexible Configuration**: Easy to adjust via JSON files
4. **Professional Grade**: Industry-standard technical analysis
5. **Real-time Processing**: Calculated on each tick for immediate signals
6. **Multi-timeframe Analysis**: Combines different timeframes for robust signals
7. **Candlestick Integration**: Includes proven reversal patterns
8. **Volume Confirmation**: Ensures momentum with volume surge detection

---

## 📈 Example Entry Scenarios

### Successful CALL Entry
```
✅ EMA_TREND: ema5_touch_5min (price touching EMA5)
✅ VOLUME: volume_surge_5min (2x volume surge)
✅ RSI: rsi_bullish_5min (RSI > 56)
✅ PRICE_ACTION: price_above_vwap_5min (price above VWAP)
❌ CANDLESTICK: No pattern detected (but only need 4/5)
Result: CALL ENTRY RECOMMENDED
```

### Successful PUT Entry
```
✅ EMA_TREND: ema9_below_ema34_5min (bearish alignment)
✅ VOLUME: volume_surge_3min (2x volume surge)
✅ RSI: rsi_bearish_5min (RSI < 44)
✅ PRICE_ACTION: price_below_support_5min (price below support)
✅ CANDLESTICK: shooting_star_5min (bearish pattern)
Result: PUT ENTRY RECOMMENDED
```

---

*This documentation covers all 42+ indicators currently implemented in the Dynamic Indicators System.*
