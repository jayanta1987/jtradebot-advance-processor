package com.jtradebot.processor.connector;


import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.repository.document.TradeConfig;
import com.jtradebot.processor.service.TickSetupService;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class KiteSetupHandler {
    private final KiteConnect kiteConnect;
    private final KiteInstrumentHandler kiteInstrumentHandler;
    private final KiteTickerHandler kiteTickerHandler;
    private final TickSetupService tickSetupService;

    public void init() throws KiteException, IOException {
        TradeConfig tradeConfig = tickSetupService.getTradeConfig();
        if (null == tradeConfig.getAccessToken()) {
            throw new KiteException("Access token not found");
        }
        kiteConnect.setAccessToken(tradeConfig.getAccessToken());
        kiteTickerHandler.subscribeTickIndex(kiteInstrumentHandler.getNifty50Token());
        kiteTickerHandler.subscribeTick(kiteInstrumentHandler.getNifty50FutureToken());
        kiteTickerHandler.setupWebSocket();
        log.info("Initialized KiteTickerHandler for Nifty50 on {}", tradeConfig.getDate());
    }

    public void generateInstruments() throws IOException, KiteException {
        kiteInstrumentHandler.deleteInstruments();
        kiteInstrumentHandler.setInstrumentTokens("NFO");
    }

    public void subscribeToken(String token) {
        log.info("Subscribing new token: {}", token);
        kiteTickerHandler.subscribeTick(Long.parseLong(token));
    }
}