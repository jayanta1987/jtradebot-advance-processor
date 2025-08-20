package com.jtradebot.processor.service.logging;

import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.zerodhatech.models.Tick;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class IndicatorLoggingService {

    /**
     * Log trend analysis with market direction
     */
    public void logTrendAnalysis(Tick tick, String trendInfo, String marketDirectionInfo) {
        log.info("📊 {} | 💰 {} | {}{}", 
            tick.getTickTimestamp(), 
            tick.getLastTradedPrice(), 
            trendInfo, marketDirectionInfo);
    }

    /**
     * Log indicator status summary
     */
    public void logIndicatorStatus(String emaStatus, String rsiStatus, String volumeStatus, String entryProximity) {
        log.debug("📈 INDICATORS - EMA: {} | RSI: {} | VOL: {} | PROX: {}", 
            emaStatus, rsiStatus, volumeStatus, entryProximity);
    }

    /**
     * Log EMA status details
     */
    public void logEmaStatus(String emaStatus) {
        log.debug("📊 EMA Status: {}", emaStatus);
    }

    /**
     * Log market direction analysis
     */
    public void logMarketDirection(boolean isCallDominant, boolean isPutDominant) {
        String callStatus = isCallDominant ? "🟢" : "⚫";
        String putStatus = isPutDominant ? "🟢" : "⚫";
        log.debug("🎯 Market Direction - Call: {} Put: {}", callStatus, putStatus);
    }

}
