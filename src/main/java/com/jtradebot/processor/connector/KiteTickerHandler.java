package com.jtradebot.processor.connector;

import com.jtradebot.processor.service.TickProcessService;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Tick;
import com.zerodhatech.ticker.KiteTicker;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@Slf4j
@RequiredArgsConstructor
public class KiteTickerHandler {
    @Getter
    private long lastTickReceivedTime = System.currentTimeMillis();
    private KiteTicker kiteTicker;
    private final KiteConnect kiteConnect;
    private final TickProcessService tickProcessService;
    private final ArrayList<Long> indexTokens = new ArrayList<>();
    private final ArrayList<Long> tokens = new ArrayList<>();

    // Static counter to track the number of open connections
    private static int openConnectionCount = 0;


    private int retryCount = 0;
    @Getter
    private long lastReconnectionAttemptTime = 0;

    public void setupWebSocket() throws KiteException {
        disconnectExistingTicker(); // Disconnect any existing ticker before creating a new one
        kiteTicker = new KiteTicker(kiteConnect.getAccessToken(), kiteConnect.getApiKey());

        kiteTicker.setOnDisconnectedListener(() -> {
            log.info("Disconnected");
            decrementConnectionCount();
            reconnect();
        });

        kiteTicker.setOnOrderUpdateListener(order -> log.info("Order update {}", order.orderId));
        kiteTicker.setOnTickerArrivalListener(ticks -> {
            if(ticks == null || ticks.isEmpty()) {
                log.error("No ticks to process.................................");
                return;
            }
            lastTickReceivedTime = System.currentTimeMillis(); // Update timestamp on tick receipt
            handleTicks(ticks);
        });

        kiteTicker.setTryReconnection(true);
        kiteTicker.setMaximumRetries(10);
        kiteTicker.setMaximumRetryInterval(30);

        kiteTicker.connect();
        incrementConnectionCount();
        log.info("Connection is open: {}, Active connections: {}", kiteTicker.isConnectionOpen(), openConnectionCount);
        kiteTicker.setMode(tokens, KiteTicker.modeFull);
    }

    public void disconnectExistingTicker() {
        if (kiteTicker != null && kiteTicker.isConnectionOpen()) {
            log.warn("Existing connection found. Disconnecting...");
            kiteTicker.disconnect();
            decrementConnectionCount();
        }
    }

    private void handleTicks(ArrayList<Tick> ticks) {
        try {
            tickProcessService.processLiveTicks(ticks);
        } catch (KiteException e) {
            log.error("Error processing live ticks: {}", e.getMessage());
        }
    }

    public void subscribeTickIndex(Long token) {
        indexTokens.add(token);
        subscribeTick(token);
    }

    public void subscribeTick(Long token) {
        if (!tokens.contains(token)) {
            log.warn("Subscribing to token: {}", token);
            addNewToken(token);
            try {
                setupWebSocket();
                reSubscribeTokens();
            } catch (KiteException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void addNewToken(Long token) {
        tokens.clear();
        tokens.addAll(indexTokens);
        tokens.add(token);
    }

    private void reconnect() {
        final int maxRetries = 5;
        final int maxBackoffTime = 30000; // Max backoff time, e.g., 30 seconds
        final int cooldownPeriod = 60000; // 1 minute cooldown between reconnections

        if (retryCount >= maxRetries) {
            log.error("Max reconnection attempts reached. Could not reconnect.");
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastReconnectionAttemptTime < cooldownPeriod) {
            log.warn("Reconnection attempt too soon. Waiting before trying again.");
            return;
        }
        lastReconnectionAttemptTime = currentTime;

        int delay = Math.min(1000 * (int) Math.pow(2, retryCount), maxBackoffTime);
        log.info("Attempting to reconnect... Attempt {} with delay {} ms", retryCount + 1, delay);

        try {
            Thread.sleep(delay);
            setupWebSocket();
            reSubscribeTokens();
            retryCount = 0; // Reset retry count on successful reconnection
            log.info("Reconnection successful. Resetting retry count.");
        } catch (KiteException e) {
            retryCount++;
            lastReconnectionAttemptTime = System.currentTimeMillis(); // Update time on failure
            log.error("Reconnection failed: {}. Retrying in {} ms", e.getMessage(), delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error("Reconnection interrupted: {}", ie.getMessage());
        }
    }


    public void reSubscribeTokens() {
        if (kiteTicker != null && kiteTicker.isConnectionOpen()) {
            kiteTicker.setMode(tokens, KiteTicker.modeFull);
            log.info("Re-subscribed tokens after reconnection");
        }
    }


    public boolean isConnectionOpen() {
        return kiteTicker != null && kiteTicker.isConnectionOpen();
    }

    private synchronized void incrementConnectionCount() {
        openConnectionCount++;
    }

    private synchronized void decrementConnectionCount() {
        if (openConnectionCount > 0) {
            openConnectionCount--;
        }
    }

}
