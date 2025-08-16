package com.jtradebot.processor.controller;

import com.jtradebot.processor.model.trading.IntraDayPreference;
import com.jtradebot.processor.service.TickSetupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tick-setup")
@RequiredArgsConstructor
@Slf4j
public class TickSetupController {
    private final TickSetupService tickSetupService;

    @GetMapping("/tick-dates")
    public List<String> getTickDates() {
        try {
            return tickSetupService.getUniqueDates();
        } catch (Exception e) {
            log.error("Failed to get unique dates: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

}
