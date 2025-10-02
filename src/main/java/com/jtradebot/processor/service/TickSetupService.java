package com.jtradebot.processor.service;

import com.jtradebot.processor.repository.TickRepository;
import com.jtradebot.processor.repository.TradeConfigRepository;
import com.jtradebot.tickstore.repository.CalculatedTick;
import com.jtradebot.tickstore.repository.TickModel;
import com.jtradebot.processor.repository.document.TradeConfig;
import com.jtradebot.processor.model.ExitSettings;
import com.mongodb.DuplicateKeyException;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.models.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.jtradebot.processor.handler.DateTimeHandler.getTodaysDateString;
import static com.jtradebot.processor.mapper.TradePreferenceMapper.getDefaultTradePreference;

@Service
@RequiredArgsConstructor
@Slf4j
public class TickSetupService {

    private final KiteConnect kiteConnect;
    private final TradeConfigRepository tradeConfigRepository;
    private final TickRepository tickRepository;
    
    // Cache for TradeConfig to avoid repeated database calls
    private final Map<String, TradeConfig> tradeConfigCache = new ConcurrentHashMap<>();
    private volatile String currentCachedDate = null;
    
    /**
     * Get current IST timestamp as string
     */
    private String getCurrentISTTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

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
        
        String currentTimestamp = getCurrentISTTimestamp();
        
        if (existingConfig != null) {
            // Update existing record
            existingConfig.setAccessToken(user.accessToken);
            existingConfig.setTradePreference(getDefaultTradePreference());
            existingConfig.setUpdatedAt(currentTimestamp);
            tradeConfigRepository.save(existingConfig);
            // Invalidate cache for this date
            tradeConfigCache.remove(formattedDate);
            log.info("Updated existing TradeConfig for date: {} and invalidated cache", formattedDate);
        } else {
            // Create new record
            TradeConfig newConfig = new TradeConfig();
            newConfig.setDate(formattedDate);
            newConfig.setAccessToken(user.accessToken);
            newConfig.setTradePreference(getDefaultTradePreference());
            newConfig.setExitSettings(getDefaultExitSettings());
            newConfig.setCreatedAt(currentTimestamp);
            newConfig.setUpdatedAt(currentTimestamp);
            try {
                tradeConfigRepository.save(newConfig);
                // Invalidate cache for this date
                tradeConfigCache.remove(formattedDate);
                log.info("Created new TradeConfig for date: {} and invalidated cache", formattedDate);
            } catch (DuplicateKeyException e) {
                log.warn("Duplicate TradeConfig detected for date: {}. Removing existing and creating new one.", formattedDate);
                // Remove existing record and create new one
                tradeConfigRepository.deleteByDate(formattedDate);
                tradeConfigRepository.save(newConfig);
                // Invalidate cache for this date
                tradeConfigCache.remove(formattedDate);
                log.info("Successfully replaced TradeConfig for date: {} and invalidated cache", formattedDate);
            }
        }
    }

    public @NotNull TradeConfig getTradeConfig() {
        String formattedDate = getTodaysDateString("Asia/Kolkata", "'IST-'yyyy-MM-dd");
        
        // Check if we need to invalidate cache (new day)
        if (!formattedDate.equals(currentCachedDate)) {
            tradeConfigCache.clear();
            currentCachedDate = formattedDate;
            log.info("Cache invalidated for new date: {}", formattedDate);
        }
        
        // Return cached value if available
        TradeConfig cachedConfig = tradeConfigCache.get(formattedDate);
        if (cachedConfig != null) {
            log.debug("TradeConfig returned from cache for date {}", formattedDate);
            return cachedConfig;
        }
        
        // Fetch from database and cache
        TradeConfig tradeConfig = tradeConfigRepository.findByDate(formattedDate).orElse(new TradeConfig());
        tradeConfig.setDate(formattedDate);
        tradeConfigCache.put(formattedDate, tradeConfig);
        log.info("TradeConfig fetched from database and cached for date {}", formattedDate);
        return tradeConfig;
    }

    public boolean isAccessTokensPresent() {
        TradeConfig tradeConfig = getTradeConfig();
        return tradeConfig.getAccessToken() != null;
    }
    
    /**
     * Manually invalidate the TradeConfig cache
     * Useful when TradeConfig is updated from external sources
     */
    public void invalidateTradeConfigCache() {
        tradeConfigCache.clear();
        currentCachedDate = null;
        log.info("TradeConfig cache manually invalidated");
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

    /**
     * Get default exit settings with all exit types enabled
     */
    private ExitSettings getDefaultExitSettings() {
        ExitSettings defaultExitSettings = new ExitSettings();
        defaultExitSettings.setMilestoneBasedExitEnabled(true);
        defaultExitSettings.setPriceMovementExitEnabled(true);
        defaultExitSettings.setTimeBasedExitEnabled(true);
        defaultExitSettings.setStrategyBasedExitEnabled(true);
        defaultExitSettings.setStopLossTargetExitEnabled(true);
        defaultExitSettings.setLastModifiedBy("SYSTEM");
        defaultExitSettings.updateLastModified();
        
        log.info("📝 Created default exit settings - All exit types enabled");
        return defaultExitSettings;
    }

}
