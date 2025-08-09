package com.jtradebot.processor.service;

import com.jtradebot.processor.handler.DateTimeHandler;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.kafka.KafkaCpDetailsProducer;
import com.jtradebot.processor.kafka.KafkaTickProducer;
import com.jtradebot.processor.manager.CPManager;
import com.jtradebot.processor.manager.StrategyManager;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.CallFutureScores;
import com.jtradebot.processor.model.PutFutureScores;
import com.jtradebot.processor.model.enums.TradeMode;
import com.jtradebot.processor.score.NiftyFutureScoreTracker;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;

import java.util.*;

import static com.jtradebot.processor.mapper.ScoreEventMapper.mapToProcessedScoreEvent;
import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.THREE_MIN;
import static com.jtradebot.processor.model.enums.TradeMode.SCALPING;

@Service
@RequiredArgsConstructor
@Slf4j
public class TickProcessService {

    private final CPManager cpManager;
    private final StrategyManager strategyManager;
    private final TickDataManager tickDataManager;
    private final KafkaCpDetailsProducer kafkaCpDetailsProducer;

    private final TickSetupService tickSetupService;
    private final TickEventTracker tickEventTracker;
    private final KafkaTickProducer kafkaTickProducer;

    private final NiftyFutureScoreTracker niftyFutureScoreTracker;
    private final KiteInstrumentHandler kiteInstrumentHandler;

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    public void processLiveTicks(List<Tick> ticks) {
        // IGNORE IF MARKET NOT STARTED YET
        if (!DateTimeHandler.isMarketOpen()) {
            log.info("Market not started yet. Skipping tick processing. Current time: {}", new Date());
            return;
        }

        Map<Long, Tick> latestTicks = new HashMap<>();
        for (Tick tick : ticks) {
            latestTicks.put(tick.getInstrumentToken(), tick);
        }
        
        for (Tick tick : latestTicks.values()) {
            log.info("Processing tick: {} : {} -> {}, isTradable = {}", tick.getInstrumentToken(), tick.getTickTimestamp(), tick.getLastTradedPrice(), tick.isTradable());
            processFutureTick(tick);
            processIndexTick(tick);
        }
    }

    public void processFutureTick(Tick tick) {
        if (tick.isTradable()) {
            initializeOnFirstTick(tick);
            tickEventTracker.setLastTickEventTimestamp(String.valueOf(tick.getInstrumentToken()), System.currentTimeMillis());
            tickDataManager.add(String.valueOf(tick.getInstrumentToken()), tick);
            kafkaTickProducer.sendTickDetails(tick, null);
        }
    }

    public void processIndexTick(Tick tick) {
        if (tick.isTradable()) {
            return;
        }
        initializeOnFirstTick(tick);
        tickEventTracker.setLastTickEventTimestamp(String.valueOf(tick.getInstrumentToken()), System.currentTimeMillis());
        tickDataManager.add(String.valueOf(tick.getInstrumentToken()), tick);
        cpManager.getCpDetails(tickDataManager.getLastTick(kiteInstrumentHandler.getNifty50Token().toString()), tickDataManager.getNoTradeScoresList()).ifPresentOrElse(cpDetails -> {
            // need to remove jtradebot-order logic to here later to track cp details after placing order
            BarSeries barSeriesForTimeFrame = tickDataManager.getBarSeriesForTimeFrame(kiteInstrumentHandler.getNifty50FutureToken().toString(), THREE_MIN);

            if (barSeriesForTimeFrame != null) {
                CallFutureScores callFutureScores = niftyFutureScoreTracker.getFuturePointsForCallEntry(barSeriesForTimeFrame);
                PutFutureScores putFutureScores = niftyFutureScoreTracker.getFuturePointsForPutEntry(barSeriesForTimeFrame);
                cpManager.addFutureCpDetails(cpDetails, callFutureScores, putFutureScores);
            }

            kafkaCpDetailsProducer.sendCpDetails(mapToProcessedScoreEvent(cpDetails, tick, tickDataManager.getAllSupportMap(), tickDataManager.getAllResistanceMap()));
            TradeMode[] tradeModes = {SCALPING/*, INTRA_DAY*/};
            Arrays.stream(tradeModes)
                    .forEach(tradeMode -> strategyManager.findStrategyBasedEntry(cpDetails, tradeMode));

            if (!"local".equalsIgnoreCase(activeProfile)) {
                kafkaTickProducer.sendTickDetails(tick, cpDetails);
            }
        }, () -> log.error("CP details not found"));
    }

    private void initializeOnFirstTick(Tick tick) {
        if (tickDataManager.isNotInitialized(String.valueOf(tick.getInstrumentToken()))) {
            log.info("Initializing tickDataManager for instrument: {}", tick.getInstrumentToken());
            tickSetupService.connect();
            tickDataManager.initialize(String.valueOf(tick.getInstrumentToken()), DateTimeHandler.getLastMarketTime(tick.getTickTimestamp()));
        }
    }
}
