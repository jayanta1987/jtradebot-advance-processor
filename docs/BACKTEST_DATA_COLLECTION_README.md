# Backtest Data Collection & Analysis System

## 🎯 Overview

This system automatically collects comprehensive backtest data during live trading sessions and provides analysis capabilities with a **single simple API call**.

## 🚀 Key Features

- **Automatic Data Collection**: Starts automatically when application starts (if enabled)
- **Multiple Ticks Per File**: Stores multiple ticks in single JSON files (configurable)
- **Single POST API**: One simple API call to analyze all collected data
- **Manual Cleanup API**: Clean up old data files when needed via API
- **Async Batch Processing**: Prevents memory issues with non-blocking processing

## 📋 Configuration

Edit `application.yml` to enable automatic data collection:

```yaml
jtradebot:
  backtest-data:
    enabled: true                    # Enable/disable automatic data collection
    batch-size: 100                 # Number of data points to collect before writing to file
    batch-interval-ms: 5000         # Maximum time to wait before writing batch (5 seconds)
    output-directory: "./backtest-data"  # Directory to store data files
    session-prefix: "auto_session"  # Prefix for automatic session IDs
    max-file-size-mb: 50           # Maximum file size before creating new file
    ticks-per-file: 1000           # Number of ticks to store in each JSON file
```

## 🔄 How It Works

1. **Automatic Start**: When application starts, if `enabled: true`, system begins collecting data
2. **Tick Processing**: Each tick is processed and added to queue for batch processing
3. **Batch Writing**: Data is written to files in configurable batches
4. **File Management**: Multiple ticks stored in single JSON files (configurable via `ticks-per-file`)
5. **File Rotation**: New files created when tick limit is reached
6. **Manual Cleanup**: Use API to clean old data when needed

## 📊 What Gets Collected

### For Each Tick:
- **Basic Data**: Timestamp, Index Price, Future Price, Volume, OHLC
- **Flattened Indicators**: All EMA, RSI, Volume, Price action indicators
- **Entry Quality**: CALL and PUT quality scores (0-10)
- **Strategy Decisions**: Whether to make CALL/PUT entry
- **Strategy Score**: Overall strategy score (-10 to +10)
- **Trend Analysis**: Signal strength across timeframes

## 🎯 Simple API Usage

### 1. Check Status
```bash
curl -X GET http://localhost:8091/jtradebot-advance-processor/api/backtest-data/status
```

### 2. Clean Up Old Data (Optional)
```bash
curl -X POST http://localhost:8091/jtradebot-advance-processor/api/backtest-data/cleanup
```

### 3. Analyze All Data (Simple!)
```bash
curl -X POST http://localhost:8091/jtradebot-advance-processor/api/backtest-data/analyze
```

**That's it!** No parameters needed. The APIs automatically:
- **Cleanup**: Deletes all old JSON and CSV files from the backtest directory
- **Analyze**: Finds all JSON files in the `./backtest-data` directory, combines all data, and performs comprehensive analysis

## 📈 Analysis Results

The analysis provides:

### 1. Basic Statistics
- Total data points across all files
- Price range and averages
- Volume statistics
- Session duration

### 2. Entry Analysis
- Total CALL/PUT signals
- Signal rates and percentages
- Signal distribution

### 3. Trend Analysis
- Trend strength distribution
- Average signals by trend strength
- Trend strength patterns

### 4. Indicator Analysis
- EMA, RSI, Volume signal rates
- Indicator satisfaction percentages
- Performance metrics

### 5. Quality Analysis
- Entry quality statistics for CALL/PUT
- Quality score ranges and averages
- Quality distribution

### 6. Time Analysis
- Signal distribution by hour
- Peak activity hours
- Time-based patterns

### 7. File Statistics
- Information about each file processed
- Data points per file
- File sizes

### 8. Recommendations
- Signal frequency assessment
- Quality score recommendations
- Strategy improvement suggestions

## 📋 Example Analysis Output

```json
{
  "success": true,
  "message": "Analysis completed successfully",
  "analysis": {
    "basicStats": {
      "totalDataPoints": 15000,
      "sessionId": "auto_session_1234567890",
      "startTime": "2024-01-15T09:15:00Z",
      "endTime": "2024-01-15T15:30:00Z",
      "minPrice": 24750.0,
      "maxPrice": 24850.0,
      "avgPrice": 24800.0,
      "priceRange": 100.0
    },
    "entryAnalysis": {
      "totalCallSignals": 45,
      "totalPutSignals": 38,
      "totalSignals": 83,
      "callSignalPercentage": 0.3,
      "putSignalPercentage": 0.25
    },
    "trendAnalysis": {
      "trendStrengthDistribution": {
        "WEAK": 8000,
        "MODERATE": 5000,
        "STRONG": 1800,
        "VERY_STRONG": 200
      },
      "avgEmaSignals": 2.1,
      "avgRsiSignals": 1.8,
      "avgVolumeSignals": 1.5
    },
    "indicatorAnalysis": {
      "emaSatisfiedCount": 12000,
      "rsiSatisfiedCount": 9500,
      "volumeSatisfiedCount": 8000,
      "emaSatisfiedPercentage": 80.0,
      "rsiSatisfiedPercentage": 63.3,
      "volumeSatisfiedPercentage": 53.3
    },
    "qualityAnalysis": {
      "callQualityStats": {
        "min": 2.5,
        "max": 9.8,
        "avg": 6.2
      },
      "putQualityStats": {
        "min": 2.1,
        "max": 9.5,
        "avg": 5.8
      }
    },
    "timeAnalysis": {
      "hourlyDistribution": {
        "9": 150,
        "10": 200,
        "11": 180,
        "14": 220,
        "15": 250
      },
      "peakActivityHour": 15,
      "peakActivityCount": 250
    },
    "fileStats": {
      "backtest_data_auto_session_1234567890_2024-01-15_09-15-00_1.json": {
        "dataPoints": 1000,
        "fileSize": 2048576
      },
      "backtest_data_auto_session_1234567890_2024-01-15_10-30-00_2.json": {
        "dataPoints": 500,
        "fileSize": 1048576
      }
    },
    "filesProcessed": 2,
    "recommendations": {
      "signalFrequency": "MODERATE - Current frequency looks good",
      "callQuality": "GOOD - CALL quality is acceptable",
      "putQuality": "GOOD - PUT quality is acceptable"
    }
  }
}
```

## 🎯 Key Insights You'll Get

### Signal Analysis
- **Signal Frequency**: How often your strategy generates signals
- **Signal Quality**: Quality scores when signals occur
- **Trend Correlation**: How signals relate to trend strength

### Indicator Performance
- **EMA Performance**: How often EMA signals are triggered
- **RSI Performance**: RSI signal frequency and quality
- **Volume Performance**: Volume surge detection effectiveness

### Market Conditions
- **Trend Distribution**: Market trend strength patterns
- **Time Patterns**: When signals occur most frequently
- **Price Action**: Market volatility and range analysis

## 🔧 Troubleshooting

### Common Issues:

1. **No Data Collected**
   - Check if `enabled: true` in configuration
   - Verify ticks are being processed
   - Check logs for errors

2. **Memory Issues**
   - Reduce `batch-size` in configuration
   - Increase `batch-interval-ms`
   - Check system memory usage

3. **Large Files**
   - Reduce `ticks-per-file` in configuration
   - Reduce `max-file-size-mb`
   - Monitor disk space

4. **Analysis Errors**
   - Check if JSON files exist in output directory
   - Verify file permissions
   - Check logs for specific error messages

### Logs to Monitor

- `🧹 Starting manual cleanup of backtest data directory` - Manual cleanup started
- `🧹 Cleaned backtest directory` - Cleanup completed
- `🚀 Started automatic backtest data collection` - Collection started
- `📄 Creating new file` - New file created (when tick limit reached)
- `💾 Wrote X data points to file` - Batch written successfully
- `🔍 Starting automatic analysis of all backtest data` - Analysis started
- `📁 Found X JSON files to analyze` - Files found for analysis
- `✅ Analysis completed successfully` - Analysis finished

## 📝 Best Practices

1. **Configure Ticks Per File**: Adjust `ticks-per-file` based on your needs (1000 is a good default)
2. **Monitor Output Directory**: Check the configured output directory for generated files
3. **Collect During Different Market Conditions**: Bullish, bearish, sideways
4. **Analyze Multiple Sessions**: Compare different time periods
5. **Review Recommendations**: Pay attention to system suggestions
6. **Use File Statistics**: Monitor how many data points each file contains

## 🚀 Next Steps

1. **Configure Collection**: Enable data collection in `application.yml`
2. **Run Trading**: Start your trading - data collection is automatic
3. **Run Analysis**: Use the simple POST API to analyze all data
4. **Review Insights**: Check signal rates, indicator performance
5. **Optimize Strategy**: Adjust parameters based on analysis
6. **Repeat**: Collect more data and compare results

## 🎉 Key Improvements

- **Single API Call**: No need to specify file names - just call the analyze endpoint
- **Multiple Files Support**: Automatically reads and combines data from all JSON files
- **Manual Cleanup API**: Clean up old data when needed via API endpoint
- **Configurable File Size**: Control how many ticks are stored per file via `ticks-per-file`
- **File Statistics**: See how many data points each file contains

This system will help you understand exactly how your strategy performs and identify areas for improvement with minimal effort!
