package com.jtradebot.tickstore.service;

import com.jtradebot.processor.repository.TickRepository;
import com.jtradebot.tickstore.model.DateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CalculatedTickService {
    
    private final TickRepository tickRepository;
    

    
    /**
     * Get list of unique dates for verification
     * Use with caution on large datasets
     */
    public List<String> getUniqueDates() {
        try {
            List<DateResult> result = tickRepository.getUniqueDates();
            return result.stream()
                    .map(DateResult::getDate)
                    .filter(date -> date != null)
                    .toList();
        } catch (Exception e) {
            log.error("Error getting unique dates", e);
            return List.of();
        }
    }
} 