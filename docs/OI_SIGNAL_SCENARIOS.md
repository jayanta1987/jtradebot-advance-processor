# OI Signal Scenarios and Scoring Analysis

## When OI Bullish Signals Show

### 1. **LONG_BUILDUP** (Strong Bullish Signal)
**Condition:** OI Increasing + Price Rising (Green Candle)
- **What it means:** New long positions are being built while price is rising
- **Market interpretation:** Strong institutional buying, bullish momentum building
- **Example:** 
  - Nifty moves from 26,200 → 26,250 (price ↑)
  - Aggregate OI across all strikes increases by +100,000 contracts (OI ↑)
  - **Result:** `oi_bullish_signal_5min = true` (LONG_BUILDUP)

### 2. **SHORT_COVERING** (Weak Bullish Signal)
**Condition:** OI Decreasing + Price Rising (Green Candle)
- **What it means:** Short positions are being closed while price is rising
- **Market interpretation:** Weak bullish move - shorts covering, not new buying
- **Example:**
  - Nifty moves from 26,200 → 26,250 (price ↑)
  - Aggregate OI decreases by -50,000 contracts (OI ↓)
  - **Result:** `oi_bullish_signal_5min = true` (SHORT_COVERING)

---

## When OI Bearish Signals Show

### 1. **SHORT_BUILDUP** (Strong Bearish Signal)
**Condition:** OI Increasing + Price Falling (Red Candle)
- **What it means:** New short positions are being built while price is falling
- **Market interpretation:** Strong institutional selling, bearish momentum building
- **Example:**
  - Nifty moves from 26,200 → 26,150 (price ↓)
  - Aggregate OI increases by +100,000 contracts (OI ↑)
  - **Result:** `oi_bearish_signal_5min = true` (SHORT_BUILDUP)

### 2. **LONG_UNWINDING** (Weak Bearish Signal)
**Condition:** OI Decreasing + Price Falling (Red Candle)
- **What it means:** Long positions are being closed while price is falling
- **Market interpretation:** Weak bearish move - longs unwinding, not new selling
- **Example:**
  - Nifty moves from 26,200 → 26,150 (price ↓)
  - Aggregate OI decreases by -50,000 contracts (OI ↓)
  - **Result:** `oi_bearish_signal_5min = true` (LONG_UNWINDING)

---

## Current Scoring Configuration

```json
{
  "oi_bullish_signal_5min": 2.0,
  "oi_bullish_signal_1min": 0.5,
  "oi_bullish_signal_15min": 0.5
}
```

### Analysis of Current Weights

#### **5min Timeframe (Weight: 2.0)** ✅ **APPROPRIATE**
- **Why highest weight:**
  - Most reliable timeframe for scalping
  - Not too noisy (unlike 1min)
  - Not too slow (unlike 15min)
  - Captures meaningful OI changes
  - Balances signal quality vs. responsiveness

#### **1min Timeframe (Weight: 0.5)** ✅ **APPROPRIATE**
- **Why lower weight:**
  - Very noisy - can give false signals
  - OI changes in 1min can be temporary
  - Good for early detection but less reliable
  - Lower weight prevents over-reliance on short-term noise

#### **15min Timeframe (Weight: 0.5)** ⚠️ **MIGHT NEED ADJUSTMENT**
- **Current weight:** 0.5 (same as 1min)
- **Considerations:**
  - More reliable than 1min (less noise)
  - Shows longer-term trend
  - But slower to react
  - **Suggestion:** Could be 0.75 or 1.0 (higher than 1min, lower than 5min)

---

## Recommended Scoring Adjustments

### Option 1: Keep Current (Conservative)
```
5min: 2.0  (primary signal)
1min: 0.5  (early warning, low weight)
15min: 0.5 (trend confirmation, low weight)
```
**Total max OI score:** 3.0 points

### Option 2: Balanced (Recommended)
```
5min: 2.0  (primary signal)
1min: 0.5  (early warning, low weight)
15min: 1.0 (trend confirmation, medium weight)
```
**Total max OI score:** 3.5 points
**Rationale:** 15min is more reliable than 1min, should have higher weight

### Option 3: Aggressive (Higher OI Importance)
```
5min: 2.5  (primary signal, increased)
1min: 0.5  (early warning, low weight)
15min: 1.0 (trend confirmation, medium weight)
```
**Total max OI score:** 4.0 points
**Rationale:** If OI is a key factor, increase overall weight

---

## Signal Strength Comparison

| Signal Type | Strength | Reliability | Use Case |
|------------|----------|-------------|----------|
| LONG_BUILDUP | ⭐⭐⭐⭐⭐ | High | Strong CALL entry signal |
| SHORT_COVERING | ⭐⭐⭐ | Medium | Weak CALL entry signal |
| SHORT_BUILDUP | ⭐⭐⭐⭐⭐ | High | Strong PUT entry signal |
| LONG_UNWINDING | ⭐⭐⭐ | Medium | Weak PUT entry signal |

---

## Timeframe Reliability

| Timeframe | Reliability | Speed | Best For |
|-----------|------------|-------|----------|
| 1min | ⭐⭐ | Fast | Early detection, but noisy |
| 5min | ⭐⭐⭐⭐⭐ | Medium | **Primary signal** - best balance |
| 15min | ⭐⭐⭐⭐ | Slow | Trend confirmation, less noise |

---

## Current Implementation Details

### How Signals are Calculated:
1. **Aggregate OI Change:** Calculates average OI change across ALL strikes (±200 range)
2. **Time Window Comparison:**
   - 1min: Compares OI from 1 minute ago vs. now
   - 5min: Compares OI from 5 minutes ago vs. now
   - 15min: Compares OI from 15 minutes ago vs. now
3. **Price Movement:** Uses Nifty Future candle direction (bullish/bearish)
4. **Signal Logic:** Combines OI change + price movement to determine signal type

### Example Calculation:
```
Current Time: 11:40:00
5min Signal Calculation:
- Get OI from 11:35:00 (5 min ago)
- Get OI from 11:40:00 (now)
- Calculate: avgOIChange = (current OI - previous OI) / strikeCount
- If avgOIChange > 0 AND candle is bullish → LONG_BUILDUP → bullish = true
```

---

## Recommendations

### For General Trading (5-15 minute holds):
1. **Current weights are reasonable** for a conservative approach
2. **Consider increasing 15min weight** to 0.75 or 1.0 (it's more reliable than 1min)
3. **Monitor signal quality** - if 1min gives too many false signals, consider reducing to 0.25
4. **Test different weight combinations** based on backtesting results

### For Scalping (150 seconds / 2.5 minutes):
**Recommended weights for 150-second scalping:**

```json
{
  "oi_bullish_signal_1min": 2.5,   // PRIMARY - Most relevant for 150-sec trades
  "oi_bullish_signal_5min": 1.0,   // SECONDARY - Still relevant
  "oi_bullish_signal_15min": 0.25  // TERTIARY - Less relevant for short trades
}
```

**Rationale:**
- **1min (2.5)**: Highest weight because:
  - Trade duration is 150 seconds (2.5 minutes)
  - 1min signals occur within trade window
  - Captures immediate momentum
  - Most actionable for scalping
  
- **5min (1.0)**: Medium weight because:
  - Trade completes before 5min candle closes
  - Still provides context but less critical
  - Reduced from 2.0 since trade is shorter
  
- **15min (0.25)**: Lower weight because:
  - Way too long for 150-second trades
  - Provides trend context but not actionable
  - Reduced from 0.5 since it's less relevant

**Total Max OI Score for Scalping:** 3.75 points (vs. 3.0 for general trading)

**Alternative (More Aggressive):**
```json
{
  "oi_bullish_signal_1min": 3.0,   // Very high - primary scalping signal
  "oi_bullish_signal_5min": 1.0,   // Secondary
  "oi_bullish_signal_15min": 0.0   // Ignore for scalping
}
```
**Total Max OI Score:** 4.0 points (if 15min ignored)

