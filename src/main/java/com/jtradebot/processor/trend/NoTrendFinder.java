package com.jtradebot.processor.trend;

import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.jtradebot.processor.repository.document.Ntp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NoTrendFinder {

    private final TickDataManager tickDataManager;

    // TODO: Old CP-based scoring system removed - this method needs refactoring for new strategy
    public Ntp getNoTradeScore(CandleTimeFrameEnum[] candleTimeFrameEnums) {
        log.warn("getNoTradeScore called but old CP-based scoring system has been removed - returning stub data");
        
        Ntp ntp = new Ntp();
        // Return stub data until refactored for new strategy
        return ntp;
    }
}
