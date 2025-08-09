package com.jtradebot.processor.backTest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @PostMapping("/init")
    public Map<String, String> init(@RequestParam(required = true) String fromDate,
                                    @RequestParam(required = true) String toDate, @RequestParam(required = true) BackTestDataFactory.SourceType source) {
        Map<String, String> response = new HashMap<>();
        try {
            kitetickBackTester.init(fromDate, toDate, source);
            response.put("message", "KiteMarketDataHandler initialized successfully");
        } catch (Exception e) {
            response.put("message", "Failed to initialize TickSetupService: " + e.getMessage());
        }
        return response;
    }

}
