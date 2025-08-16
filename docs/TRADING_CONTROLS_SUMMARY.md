# Trading Controls Summary

## 🎛️ **Available Controls**

The trading system has controls in both **JSON configuration files** and **Java code**. Here's what you can control:

## 📁 **JSON Configuration Controls**

### 1. **Volume Surge Controls** (`scalping-entry-config.json`)

```json
{
  "callStrategy": {
    "entryQuality": {
      "minVolumeSurge": 2.5,        // 🔥 MAIN CONTROL: Volume surge threshold
      "minRsiStrength": 60.0,       // RSI threshold for CALL entries
      "minSignalStrength": 6.0      // Minimum signal strength required
    }
  },
  "putStrategy": {
    "entryQuality": {
      "minVolumeSurge": 2.5,        // 🔥 MAIN CONTROL: Volume surge threshold  
      "minRsiStrength": 40.0,       // RSI threshold for PUT entries
      "minSignalStrength": 7.0      // Minimum signal strength required
    }
  }
}
```

### 2. **Risk Management Controls**

```json
{
  "riskManagement": {
    "stopLossPoints": 3.0,          // Stop loss in points
    "targetPoints": 8.0,            // Target profit in points
    "maxHoldingTimeMinutes": 15,    // Maximum holding time
    "useTrailingStop": true,        // Enable trailing stop
    "trailingStopPercentage": 0.2   // Trailing stop percentage
  }
}
```

### 3. **Market Condition Controls**

```json
{
  "marketConditions": {
    "minVolatility": 0.5,           // Minimum volatility required
    "maxVolatility": 3.0,           // Maximum volatility allowed
    "avoidTimeSlots": ["09:15-09:30", "15:15-15:30"], // Avoid these times
    "minLiquidityThreshold": 1000000 // Minimum liquidity required
  }
}
```

## 🔧 **Java Code Controls (Natural Validation)**

### 1. **Volume Surge Quality Controls**

```java
// In ScalpingVolumeSurgeServiceImpl.java

// Natural quality validation thresholds:
boolean isSignificantlyStronger = currentMultiplier > lastMultiplier * 1.15; // 15% improvement
boolean isAboveAdaptiveAverage = currentMultiplier > avgMultiplier * 1.05;  // 5% above average
boolean hasGoodTrackRecord = successRate > 0.4;                             // 40% success rate
boolean isNotExcessive = currentMultiplier < 10.0;                          // Cap at 10x
```

### 2. **Momentum Validation Controls**

```java
// For CALL entries:
boolean hasEmaMomentum = bullishEmaCount >= 2;    // Require 2+ bullish EMAs
boolean hasRsiMomentum = bullishRsiCount >= 1;    // Require 1+ bullish RSI

// For PUT entries:
boolean hasEmaMomentum = bearishEmaCount >= 2;    // Require 2+ bearish EMAs
boolean hasRsiMomentum = bearishRsiCount >= 1;    // Require 1+ bearish RSI
```

### 3. **Market Condition Validation Controls**

```java
// Natural market condition validation:
boolean hasTimeframeAlignment = alignedTimeframes >= 2;     // 2+ timeframes aligned
boolean hasRsiAlignment = rsiAlignedTimeframes >= 2;       // 2+ RSI timeframes aligned
boolean hasPriceActionConfirmation = price_gt_vwap_5min;   // Price above VWAP
```

## 🎯 **Key Controls You Can Adjust**

### **Volume Surge Sensitivity**
```json
"minVolumeSurge": 2.5  // Increase for stricter signals, decrease for more signals
```

**Options:**
- `1.5` - More sensitive (more signals, potentially lower quality)
- `2.0` - Balanced approach
- `2.5` - Current setting (recommended)
- `3.0` - Stricter (fewer signals, higher quality)
- `4.0` - Very strict (only strongest signals)

### **RSI Thresholds**
```json
"minRsiStrength": 60.0  // For CALL entries (higher = more bullish required)
"minRsiStrength": 40.0  // For PUT entries (lower = more bearish required)
```

**Options:**
- CALL: `50-70` (50 = neutral, 70 = very bullish)
- PUT: `30-50` (50 = neutral, 30 = very bearish)

### **Signal Strength Requirements**
```json
"minSignalStrength": 6.0  // CALL entries
"minSignalStrength": 7.0  // PUT entries
```

**Options:**
- `5.0` - More lenient (more entries)
- `6.0-7.0` - Balanced (current)
- `8.0-9.0` - Stricter (fewer entries)
- `10.0` - Very strict (only best signals)

### **Risk Management**
```json
"stopLossPoints": 3.0,     // Stop loss in points
"targetPoints": 8.0,       // Target profit in points
"maxHoldingTimeMinutes": 15 // Maximum holding time
```

## 🔄 **How to Modify Controls**

### **Option 1: Edit JSON Files (Recommended)**
1. Edit `src/main/resources/rules/scalping-entry-config.json`
2. Restart the application
3. Changes take effect immediately

### **Option 2: Modify Java Code**
1. Edit the validation methods in `ScalpingVolumeSurgeServiceImpl.java`
2. Recompile and restart
3. More complex but allows fine-tuning

## 📊 **Control Impact Summary**

| Control | Lower Value | Higher Value | Impact |
|---------|-------------|--------------|---------|
| `minVolumeSurge` | More signals | Fewer signals | Signal frequency |
| `minRsiStrength` | More entries | Fewer entries | Entry strictness |
| `minSignalStrength` | More trades | Fewer trades | Trade quality |
| `stopLossPoints` | Tighter SL | Wider SL | Risk per trade |
| `targetPoints` | Lower targets | Higher targets | Profit potential |

## 🎛️ **Recommended Settings for Different Market Conditions**

### **High Volatility Market**
```json
{
  "minVolumeSurge": 3.0,
  "minRsiStrength": 65.0,
  "minSignalStrength": 7.0,
  "stopLossPoints": 4.0,
  "targetPoints": 10.0
}
```

### **Low Volatility Market**
```json
{
  "minVolumeSurge": 2.0,
  "minRsiStrength": 55.0,
  "minSignalStrength": 5.0,
  "stopLossPoints": 2.0,
  "targetPoints": 6.0
}
```

### **Conservative Trading**
```json
{
  "minVolumeSurge": 3.5,
  "minRsiStrength": 70.0,
  "minSignalStrength": 8.0,
  "stopLossPoints": 2.0,
  "targetPoints": 8.0
}
```

### **Aggressive Trading**
```json
{
  "minVolumeSurge": 1.8,
  "minRsiStrength": 50.0,
  "minSignalStrength": 4.0,
  "stopLossPoints": 4.0,
  "targetPoints": 12.0
}
```

## 🔍 **Monitoring Your Controls**

Watch these log messages to see how your controls are working:

```
🔥 NATURAL HIGH-QUALITY VOLUME SURGE - Multiplier: 3.2x, Momentum: true, Quality: true, Market: true
🚀 NATURAL CALL ENTRY SIGNAL - Quality: 8/10
```

The system will show you exactly which validations are passing or failing based on your current control settings.
