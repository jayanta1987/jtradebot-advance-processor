package com.jtradebot.processor.backTest.source;

import com.jtradebot.processor.mapper.TickMapper;
import com.jtradebot.processor.repository.TickRepository;
import com.jtradebot.tickstore.repository.CalculatedTick;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class SourceDBService {
    private final TickRepository tickRepository;

    public Queue<Tick> getBackTestData(Date fromDate, Date toDate, List<Long> instrumentTokens) {
        Queue<Tick> tickQueue = new LinkedList<>();
        Map<String, List<Tick>> timeBasedTicks = fetchTimeBasedTicks(fromDate, toDate, instrumentTokens);
        for (List<Tick> ticks : timeBasedTicks.values()) {
            tickQueue.addAll(ticks);
        }
        return tickQueue;
    }

    public Map<String, List<Tick>> fetchTimeBasedTicks(Date fromDate, Date toDate, List<Long> instrumentTokens) {
        Map<String, List<Tick>> tickMap = new TreeMap<>();
        for (Long instrumentToken : instrumentTokens) {
            List<CalculatedTick> calculatedTicks = fetchTicks(fromDate, toDate, instrumentToken);
            for (CalculatedTick calculatedTick : calculatedTicks) {
                Tick tick = TickMapper.convertToZerodhaTick(calculatedTick.getTick());
                String tickTimeStampKey = tick.getTickTimestamp().toString();
                if (tickMap.containsKey(tickTimeStampKey)) {
                    tickMap.get(tickTimeStampKey).add(tick);
                } else {
                    List<Tick> tickList = new ArrayList<>();
                    tickList.add(tick);
                    tickMap.put(tickTimeStampKey, tickList);
                }
            }
        }
        return tickMap;
    }

    private List<CalculatedTick> fetchTicks(Date fromDate, Date toDate, Long instrumentToken) {
        Sort sort = Sort.by(Sort.Direction.ASC, "tickTimestamp");
        Long startTime = System.currentTimeMillis();
        List<CalculatedTick> ticks = tickRepository.findLastTradedTimesByDateRangeAndInstrumentToken(fromDate, toDate, instrumentToken, sort);
        Long endTime = System.currentTimeMillis();
        log.info("Time taken to fetch data from db: {} seconds for {} records", (endTime - startTime) / 1000.0, ticks.size());
        return ticks;
    }
}