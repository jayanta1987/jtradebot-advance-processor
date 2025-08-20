package com.jtradebot.processor.service.scheduler;

import com.jtradebot.processor.service.ExitStrategyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExitStrategySchedulerService {
    
    private final ExitStrategyService exitStrategyService;
    
    /**
     * Update orders to database every 10 seconds
     */
    @Scheduled(fixedRate = 10000) // 10 seconds
    public void updateOrdersToDatabase() {
        try {
            exitStrategyService.updateOrdersToDatabase();
        } catch (Exception e) {
            log.error("Error in scheduled order database update: {}", e.getMessage(), e);
        }
    }
}
