package com.jtradebot.processor.backTest;

import com.jtradebot.processor.common.ProfileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/backTest")
@RequiredArgsConstructor
@Slf4j
public class BackTestController {
    private final KiteTickBackTester kitetickBackTester;
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

}
