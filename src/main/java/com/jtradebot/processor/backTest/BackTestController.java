package com.jtradebot.processor.backTest;

import com.jtradebot.processor.service.ScalpingVolumeSurgeService;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.common.ProfileUtil;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;

@RestController
@RequestMapping("/backTest")
@RequiredArgsConstructor
@Slf4j
public class BackTestController {
    private final KiteTickBackTester kitetickBackTester;
    private final ScalpingVolumeSurgeService scalpingVolumeSurgeService;
    private final TickDataManager tickDataManager;
    private final Environment environment;

    @PostMapping("/init")
    public Map<String, Object> init(@RequestParam String fromDate,
                                    @RequestParam String toDate,
                                    @RequestParam BackTestDataFactory.SourceType source) {
        Map<String, Object> response = new HashMap<>();
        
        // Check if we're in live profile - restrict backtesting
        if (ProfileUtil.isProfileActive(environment, "live")) {
            log.warn("🚫 BACKTEST BLOCKED - Cannot run backtest in LIVE profile");
            response.put("message", "Backtest is not allowed in LIVE profile. Please switch to LOCAL profile for backtesting.");
            response.put("error", "BACKTEST_RESTRICTED_IN_LIVE_PROFILE");
            response.put("status", "BLOCKED");
            return response;
        }
        
        try {
            log.info("✅ BACKTEST INITIALIZING - Profile: LOCAL, From: {}, To: {}, Source: {}", fromDate, toDate, source);
            
            // Initialize the existing backtest system
            kitetickBackTester.init(fromDate, toDate, source);
            response.put("message", "KiteMarketDataHandler initialized successfully");
            response.put("strategy", "DEFAULT");
            response.put("status", "SUCCESS");

        } catch (Exception e) {
            log.error("Error initializing backtest", e);
            response.put("message", "Failed to initialize TickSetupService: " + e.getMessage());
            response.put("error", e.getMessage());
            response.put("status", "ERROR");
        }
        return response;
    }

    /**
     * Process ticks with SCALPING_FUTURE_VOLUME_SURGE strategy
     */
    private List<StrategyResult> processTicksWithStrategy(Queue<Tick> tickQueue) {
        List<StrategyResult> results = new ArrayList<>();

        while (!tickQueue.isEmpty()) {
            Tick tick = tickQueue.poll();
            if (tick != null) {
                try {
                    // Add tick to TickDataManager for indicator calculations
                    tickDataManager.add(String.valueOf(tick.getInstrumentToken()), tick);

                    // Process tick with strategy
                    StrategyResult result = processTickWithStrategy(tick);
                    results.add(result);

                } catch (Exception e) {
                    log.error("Error processing tick for strategy: {}", tick.getInstrumentToken(), e);
                }
            }
        }

        return results;
    }

    /**
     * Process individual tick with strategy
     */
    private StrategyResult processTickWithStrategy(Tick tick) {
        StrategyResult result = new StrategyResult();
        result.setTimestamp(tick.getTickTimestamp());
        result.setInstrumentToken(tick.getInstrumentToken());
        result.setLastTradedPrice(tick.getLastTradedPrice());

        try {
            // Get flattened indicators
            FlattenedIndicators indicators = scalpingVolumeSurgeService.getFlattenedIndicators(tick);
            result.setFlattenedIndicators(indicators);

            // Get strategy recommendation
            String recommendedStrategy = scalpingVolumeSurgeService.getRecommendedStrategy(tick);
            result.setRecommendedStrategy(recommendedStrategy);

            // Get strategy confidence
            Double strategyConfidence = scalpingVolumeSurgeService.getStrategyConfidence(tick);
            result.setStrategyConfidence(strategyConfidence);

            // Check entry conditions
            boolean shouldMakeCallEntry = scalpingVolumeSurgeService.shouldMakeCallEntry(tick);
            boolean shouldMakePutEntry = scalpingVolumeSurgeService.shouldMakePutEntry(tick);

            result.setShouldMakeCallEntry(shouldMakeCallEntry);
            result.setShouldMakePutEntry(shouldMakePutEntry);

            // Determine action
            if (shouldMakeCallEntry) {
                result.setAction("CALL_ENTRY");
            } else if (shouldMakePutEntry) {
                result.setAction("PUT_ENTRY");
            } else {
                result.setAction("NO_ACTION");
            }

        } catch (Exception e) {
            log.error("Error processing tick with strategy: {}", tick.getInstrumentToken(), e);
            result.setAction("ERROR");
            result.setErrorMessage(e.getMessage());
        }

        return result;
    }

    private Date parseDate(String dateStr) throws Exception {
        // Simple date parsing - you might want to use a more robust approach
        return new Date(Long.parseLong(dateStr));
    }

    /**
     * Strategy result for individual tick
     */
    public static class StrategyResult {
        private Date timestamp;
        private Long instrumentToken;
        private Double lastTradedPrice;
        private FlattenedIndicators flattenedIndicators;
        private String recommendedStrategy;
        private Double strategyConfidence;
        private Boolean shouldMakeCallEntry;
        private Boolean shouldMakePutEntry;
        private String action;
        private String errorMessage;

        // Getters and setters
        public Date getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Date timestamp) {
            this.timestamp = timestamp;
        }

        public Long getInstrumentToken() {
            return instrumentToken;
        }

        public void setInstrumentToken(Long instrumentToken) {
            this.instrumentToken = instrumentToken;
        }

        public Double getLastTradedPrice() {
            return lastTradedPrice;
        }

        public void setLastTradedPrice(Double lastTradedPrice) {
            this.lastTradedPrice = lastTradedPrice;
        }

        public FlattenedIndicators getFlattenedIndicators() {
            return flattenedIndicators;
        }

        public void setFlattenedIndicators(FlattenedIndicators flattenedIndicators) {
            this.flattenedIndicators = flattenedIndicators;
        }

        public String getRecommendedStrategy() {
            return recommendedStrategy;
        }

        public void setRecommendedStrategy(String recommendedStrategy) {
            this.recommendedStrategy = recommendedStrategy;
        }

        public Double getStrategyConfidence() {
            return strategyConfidence;
        }

        public void setStrategyConfidence(Double strategyConfidence) {
            this.strategyConfidence = strategyConfidence;
        }

        public Boolean getShouldMakeCallEntry() {
            return shouldMakeCallEntry;
        }

        public void setShouldMakeCallEntry(Boolean shouldMakeCallEntry) {
            this.shouldMakeCallEntry = shouldMakeCallEntry;
        }

        public Boolean getShouldMakePutEntry() {
            return shouldMakePutEntry;
        }

        public void setShouldMakePutEntry(Boolean shouldMakePutEntry) {
            this.shouldMakePutEntry = shouldMakePutEntry;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }
}
