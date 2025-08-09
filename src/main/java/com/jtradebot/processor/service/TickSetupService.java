package com.jtradebot.processor.service;

import com.jtradebot.processor.repository.TickRepository;
import com.jtradebot.processor.repository.TradeConfigRepository;
import com.jtradebot.tickstore.repository.CalculatedTick;
import com.jtradebot.tickstore.repository.TickModel;
import com.jtradebot.processor.repository.document.TradeConfig;
import com.mongodb.DuplicateKeyException;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.models.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;

import static com.jtradebot.processor.handler.DateTimeHandler.getTodaysDateString;
import static com.jtradebot.processor.mapper.TradePreferenceMapper.getDefaultTradePreference;

@Service
@RequiredArgsConstructor
@Slf4j
public class TickSetupService {

    private final KiteConnect kiteConnect;
    private final TradeConfigRepository tradeConfigRepository;
    private final TickRepository tickRepository;

    public void connect() {
        TradeConfig tradeConfig = getTradeConfig();
        String accessToken = tradeConfig.getAccessToken();
        if (accessToken == null) {
            throw new RuntimeException("Access token not found in TradeConfig");
        }
        kiteConnect.setAccessToken(accessToken);
    }

    public void saveDefaultTradeConfig(User user) {
        String formattedDate = getTodaysDateString("Asia/Kolkata", "'IST-'yyyy-MM-dd");
        
        // Use upsert logic to prevent race conditions
        TradeConfig existingConfig = tradeConfigRepository.findByDate(formattedDate).orElse(null);
        
        if (existingConfig != null) {
            // Update existing record
            existingConfig.setAccessToken(user.accessToken);
            existingConfig.setTradePreference(getDefaultTradePreference());
            tradeConfigRepository.save(existingConfig);
            log.info("Updated existing TradeConfig for date: {}", formattedDate);
        } else {
            // Create new record
            TradeConfig newConfig = new TradeConfig();
            newConfig.setDate(formattedDate);
            newConfig.setAccessToken(user.accessToken);
            newConfig.setTradePreference(getDefaultTradePreference());
            try {
                tradeConfigRepository.save(newConfig);
                log.info("Created new TradeConfig for date: {}", formattedDate);
            } catch (DuplicateKeyException e) {
                log.warn("Duplicate TradeConfig detected for date: {}. Removing existing and creating new one.", formattedDate);
                // Remove existing record and create new one
                tradeConfigRepository.deleteByDate(formattedDate);
                tradeConfigRepository.save(newConfig);
                log.info("Successfully replaced TradeConfig for date: {}", formattedDate);
            }
        }
    }

    public @NotNull TradeConfig getTradeConfig() {
        String formattedDate = getTodaysDateString("Asia/Kolkata", "'IST-'yyyy-MM-dd");
        TradeConfig tradeConfig = tradeConfigRepository.findByDate(formattedDate).orElse(new TradeConfig());
        tradeConfig.setDate(formattedDate);
        log.info("TradeConfig fetched for date {}", formattedDate);
        return tradeConfig;
    }

    public boolean isAccessTokensPresent() {
        TradeConfig tradeConfig = getTradeConfig();
        return tradeConfig.getAccessToken() != null;
    }

    public List<String> getUniqueDates() {
        List<CalculatedTick> calculatedTicks = tickRepository.findAllLastTradedTimes();
        Set<String> uniqueDates = new HashSet<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        dateFormat.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Kolkata"));

        for (CalculatedTick calculatedTick : calculatedTicks) {
            TickModel tick = calculatedTick.getTick();
            Date lastTradedTime = tick.getLastTradedTime();
            if (lastTradedTime != null) {
                uniqueDates.add(dateFormat.format(lastTradedTime));
            }
        }
        return new ArrayList<>(uniqueDates);
    }

}
