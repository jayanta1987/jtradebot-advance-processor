package com.jtradebot.processor.service;

import com.jtradebot.processor.service.trading.TickOrchestrationService;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TickProcessService {

    private final TickOrchestrationService tickOrchestrationService;

    public void processLiveTicks(List<Tick> ticks) throws KiteException {
        try {
            tickOrchestrationService.processLiveTicks(ticks);
        } catch (Exception e) {
            log.error("Error in tick orchestration: {}", e.getMessage());
            throw new KiteException(e.getMessage());
        }
    }
    
    public void processLiveTicks(List<Tick> ticks, boolean skipMarketHoursCheck) throws KiteException {
        try {
            tickOrchestrationService.processLiveTicks(ticks, skipMarketHoursCheck);
        } catch (Exception e) {
            log.error("Error in tick orchestration: {}", e.getMessage());
            throw new KiteException(e.getMessage());
        }
    }
    
}
