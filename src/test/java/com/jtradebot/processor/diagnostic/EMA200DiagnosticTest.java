package com.jtradebot.processor.diagnostic;

import com.jtradebot.processor.indicator.MultiEmaIndicator;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.ta4j.core.BarSeries;

import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.FIVE_MIN;

/**
 * Diagnostic test to identify EMA 200 calculation discrepancies
 * between system and TradingView
 */
@SpringBootTest
@ActiveProfiles("local")
@Slf4j
public class EMA200DiagnosticTest {

    @Autowired
    private TickDataManager tickDataManager;
    
    @Autowired
    private MultiEmaIndicator multiEmaIndicator;

    @Test
    void diagnosticEMA200Calculation() {
        // Replace with your actual instrument token
        String instrumentToken = "256265"; // Nifty 50 token
        
        try {
            // Get 5-minute BarSeries
            BarSeries fiveMinSeries = tickDataManager.getBarSeriesForTimeFrame(instrumentToken, FIVE_MIN);
            
            if (fiveMinSeries == null || fiveMinSeries.getBarCount() < 200) {
                log.error("Insufficient data for EMA 200 calculation. Bar count: {}", 
                    fiveMinSeries != null ? fiveMinSeries.getBarCount() : 0);
                return;
            }
            
            // Calculate EMA 200
            var emaInfo = multiEmaIndicator.calculateEmaValues(fiveMinSeries, FIVE_MIN);
            double ema200 = emaInfo.getEma200();
            
            // Get current price
            double currentPrice = fiveMinSeries.getLastBar().getClosePrice().doubleValue();
            double distance = currentPrice - ema200;
            
            // Log diagnostic information
            log.info("=== EMA 200 DIAGNOSTIC REPORT ===");
            log.info("Instrument Token: {}", instrumentToken);
            log.info("Timeframe: 5-minute");
            log.info("Total Bars: {}", fiveMinSeries.getBarCount());
            log.info("Current Price: {}", currentPrice);
            log.info("EMA 200 (System): {}", ema200);
            log.info("EMA 200 Distance: {}", distance);
            log.info("TradingView EMA 200: 25605 (Expected)");
            log.info("Difference: {}", ema200 - 25605);
            log.info("Percentage Difference: {}%", ((ema200 - 25605) / 25605) * 100);
            
            // Check last few bars for data quality
            log.info("=== LAST 5 BARS DATA ===");
            for (int i = Math.max(0, fiveMinSeries.getBarCount() - 5); i < fiveMinSeries.getBarCount(); i++) {
                var bar = fiveMinSeries.getBar(i);
                log.info("Bar {}: O={}, H={}, L={}, C={}, V={}, Time={}", 
                    i, 
                    bar.getOpenPrice().doubleValue(),
                    bar.getHighPrice().doubleValue(),
                    bar.getLowPrice().doubleValue(),
                    bar.getClosePrice().doubleValue(),
                    bar.getVolume().doubleValue(),
                    bar.getEndTime()
                );
            }
            
            // Manual EMA calculation verification
            log.info("=== MANUAL EMA 200 VERIFICATION ===");
            verifyManualEMACalculation(fiveMinSeries);
            
        } catch (Exception e) {
            log.error("Error in EMA 200 diagnostic: {}", e.getMessage(), e);
        }
    }
    
    private void verifyManualEMACalculation(BarSeries series) {
        try {
            // Manual EMA 200 calculation for verification
            double multiplier = 2.0 / (200 + 1); // EMA smoothing factor
            double ema = series.getBar(0).getClosePrice().doubleValue(); // Start with first close
            
            log.info("Manual EMA calculation:");
            log.info("Multiplier: {}", multiplier);
            log.info("Starting EMA: {}", ema);
            
            // Calculate EMA for first 10 bars to see the pattern
            for (int i = 1; i < Math.min(10, series.getBarCount()); i++) {
                double close = series.getBar(i).getClosePrice().doubleValue();
                ema = (close * multiplier) + (ema * (1 - multiplier));
                log.info("Bar {}: Close={}, EMA={}", i, close, ema);
            }
            
            // Calculate final EMA
            for (int i = 10; i < series.getBarCount(); i++) {
                double close = series.getBar(i).getClosePrice().doubleValue();
                ema = (close * multiplier) + (ema * (1 - multiplier));
            }
            
            log.info("Final Manual EMA 200: {}", ema);
            
        } catch (Exception e) {
            log.error("Error in manual EMA calculation: {}", e.getMessage(), e);
        }
    }
}
