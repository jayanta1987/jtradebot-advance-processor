package com.jtradebot.processor.service.risk;

import com.jtradebot.processor.config.TradingConfigurationService;
import com.jtradebot.processor.manager.BarSeriesManager;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicRiskManagementService {

    private final BarSeriesManager barSeriesManager;
    private final TradingConfigurationService tradingConfigService;

    /**
     * Calculate dynamic stop loss based on last closed 5-minute candle range
     * Stop Loss = 50% of the candle range (High - Low)
     */
    public double calculateDynamicStopLoss(String instrumentToken, double currentIndexPrice) {
        try {
            // Check if dynamic risk management is enabled
            if (!tradingConfigService.isDynamicRiskManagementEnabled()) {
                log.debug("Dynamic risk management is disabled, using configured default stop loss");
                return tradingConfigService.getDefaultStopLossWhenDisabled();
            }

            // Get the last closed 5-minute candle
            Bar lastClosed5MinCandle = getLastClosed5MinCandle(instrumentToken);

            if (lastClosed5MinCandle == null) {
                log.warn("No 5-minute candle data available for instrument: {}, using configured default stop loss", instrumentToken);
                return tradingConfigService.getDynamicMinStopLoss(); // Use configured min stop loss
            }

            // Calculate candle range
            double candleHigh = lastClosed5MinCandle.getHighPrice().doubleValue();
            double candleLow = lastClosed5MinCandle.getLowPrice().doubleValue();
            double candleRange = candleHigh - candleLow;

            // Calculate dynamic stop loss as configured percentage of candle range
            double stopLossPercentage = tradingConfigService.getDynamicStopLossPercentage();
            double dynamicStopLoss = candleRange * (stopLossPercentage / 100.0);

            // Apply min/max constraints from configuration
            double minStopLoss = tradingConfigService.getDynamicMinStopLoss();
            double maxStopLoss = tradingConfigService.getDynamicMaxStopLoss();
            dynamicStopLoss = Math.max(minStopLoss, Math.min(maxStopLoss, dynamicStopLoss));

          log.info("🎯 DYNAMIC STOP LOSS CALCULATION - Instrument: {} | Candle Range: {} | SL %: {}% | Dynamic SL: {} | Current Index: {}",
              instrumentToken,
              String.format("%.2f", candleRange),
              String.format("%.1f", stopLossPercentage),
              String.format("%.2f", dynamicStopLoss),
              String.format("%.2f", currentIndexPrice));

            return dynamicStopLoss;

        } catch (Exception e) {
            log.error("Error calculating dynamic stop loss for instrument: {}", instrumentToken, e);
            return tradingConfigService.getDynamicMinStopLoss(); // Use configured min stop loss as fallback
        }
    }

    /**
     * Calculate dynamic target as configured multiplier of stop loss
     */
    public double calculateDynamicTarget(double stopLoss) {
        try {
            // Check if dynamic risk management is enabled
            if (!tradingConfigService.isDynamicRiskManagementEnabled()) {
                log.debug("Dynamic risk management is disabled, using configured default target");
                return tradingConfigService.getDefaultTargetWhenDisabled();
            }

            double targetMultiplier = tradingConfigService.getDynamicTargetMultiplier();
            double dynamicTarget = stopLoss * targetMultiplier;

            // Apply min/max constraints for target from configuration
            double minTarget = tradingConfigService.getDynamicMinTarget();
            double maxTarget = tradingConfigService.getDynamicMaxTarget();
            dynamicTarget = Math.max(minTarget, Math.min(maxTarget, dynamicTarget));

            log.info("🎯 DYNAMIC TARGET CALCULATION - Stop Loss: {} | Multiplier: {}x | Target: {}",
                String.format("%.2f", stopLoss),
                String.format("%.1f", targetMultiplier),
                String.format("%.2f", dynamicTarget));

            return dynamicTarget;

        } catch (Exception e) {
            log.error("Error calculating dynamic target for stop loss: {}", stopLoss, e);
            return tradingConfigService.getDynamicMinTarget(); // Use configured min target as fallback
        }
    }

    /**
     * Get the last closed 5-minute candle
     * Note: We need the previous candle, not the current one that's still forming
     */
    private Bar getLastClosed5MinCandle(String instrumentToken) {
        try {
            // Get configurable candle timeframe
            CandleTimeFrameEnum candleTimeframe = getCandleTimeframeFromConfig();
            BarSeries candleSeries = barSeriesManager.getBarSeriesForTimeFrame(instrumentToken, candleTimeframe);

            int minRequiredBars = tradingConfigService.getMinRequiredBars();
            if (candleSeries == null || candleSeries.getBarCount() < minRequiredBars) {
                log.warn("Insufficient {} candle data for instrument: {} (required: {}, available: {})",
                        candleTimeframe, instrumentToken, minRequiredBars,
                        candleSeries != null ? candleSeries.getBarCount() : 0);
                return null;
            }

            // Get the previous candle (last closed candle)
            // Current candle is still forming, so we use the previous one

            return candleSeries.getBar(candleSeries.getEndIndex() - 1);

        } catch (Exception e) {
            log.error("Error getting last closed candle for instrument: {}", instrumentToken, e);
            return null;
        }
    }

    /**
     * Get market volatility score based on recent 5-minute candles
     */
    public double getMarketVolatilityScore(String instrumentToken) {
        try {
            // Get configurable candle timeframe
            CandleTimeFrameEnum candleTimeframe = getCandleTimeframeFromConfig();
            BarSeries candleSeries = barSeriesManager.getBarSeriesForTimeFrame(instrumentToken, candleTimeframe);

            int minRequiredBars = tradingConfigService.getMinRequiredBars();
            if (candleSeries == null || candleSeries.getBarCount() < minRequiredBars) {
                log.warn("Insufficient {} candle data for volatility calculation for instrument: {} (required: {}, available: {})",
                        candleTimeframe, instrumentToken, minRequiredBars,
                        candleSeries != null ? candleSeries.getBarCount() : 0);
                return tradingConfigService.getDefaultVolatilityScore();
            }

            // Calculate average range of last N candles (configurable lookback period)
            double totalRange = 0.0;
            int lookbackPeriod = tradingConfigService.getVolatilityLookbackPeriod();
            int candleCount = Math.min(lookbackPeriod, candleSeries.getBarCount());

            for (int i = candleSeries.getEndIndex() - candleCount + 1; i <= candleSeries.getEndIndex(); i++) {
                Bar bar = candleSeries.getBar(i);
                double range = bar.getHighPrice().doubleValue() - bar.getLowPrice().doubleValue();
                totalRange += range;
            }

            double avgRange = totalRange / candleCount;
            double currentIndexPrice = candleSeries.getLastBar().getClosePrice().doubleValue();

            // Volatility score as percentage of index price
            double volatilityScore = (avgRange / currentIndexPrice) * 100;

            log.debug("📊 MARKET VOLATILITY SCORE - Instrument: {} | Timeframe: {} | Lookback: {} | Avg Range: {:.2f} | Volatility: {:.2f}%",
                    instrumentToken, candleTimeframe, lookbackPeriod, avgRange, volatilityScore);

            return volatilityScore;

        } catch (Exception e) {
            log.error("Error calculating market volatility score for instrument: {}", instrumentToken, e);
            return tradingConfigService.getDefaultVolatilityScore();
        }
    }

    /**
     * Get candle timeframe from configuration
     */
    private CandleTimeFrameEnum getCandleTimeframeFromConfig() {
        try {
            String timeframeStr = tradingConfigService.getCandleTimeframe();
            
            // Handle common timeframe formats
            if (timeframeStr == null || timeframeStr.trim().isEmpty()) {
                log.warn("Candle timeframe is null or empty, using FIVE_MIN as default");
                return CandleTimeFrameEnum.FIVE_MIN;
            }
            
            // Try direct enum value first
            try {
                return CandleTimeFrameEnum.valueOf(timeframeStr.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                // Try to map common formats to enum values
                String normalized = timeframeStr.trim().toLowerCase();
                switch (normalized) {
                    case "1min":
                    case "1_min":
                    case "one_min":
                        return CandleTimeFrameEnum.ONE_MIN;
                    case "3min":
                    case "3_min":
                    case "three_min":
                        return CandleTimeFrameEnum.THREE_MIN;
                    case "5min":
                    case "5_min":
                    case "five_min":
                        return CandleTimeFrameEnum.FIVE_MIN;
                    case "15min":
                    case "15_min":
                    case "fifteen_min":
                        return CandleTimeFrameEnum.FIFTEEN_MIN;
                    case "1hour":
                    case "1_hour":
                    case "one_hour":
                        return CandleTimeFrameEnum.ONE_HOUR;
                    case "1day":
                    case "1_day":
                    case "one_day":
                        return CandleTimeFrameEnum.ONE_DAY;
                    default:
                        throw new IllegalArgumentException("Unknown timeframe format: " + timeframeStr);
                }
            }
        } catch (Exception e) {
            log.warn("Error parsing candle timeframe from config: {}, using FIVE_MIN as default. Error: {}",
                    tradingConfigService.getCandleTimeframe(), e.getMessage());
            return CandleTimeFrameEnum.FIVE_MIN;
        }
    }
}
