package com.jtradebot.processor.service;

import com.jtradebot.processor.config.DayTradingSettingService;
import com.jtradebot.processor.model.ExitSettings;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.jtradebot.processor.handler.DateTimeHandler.getTodaysDateString;

@Service
@RequiredArgsConstructor
@Slf4j
public class TickSetupService {

    private final KiteConnect kiteConnect;
    private final TradeConfigRepository tradeConfigRepository;
    private final TickRepository tickRepository;
    private final DayTradingSettingService dayTradingSettingService;
    
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
        
        // Get trade preferences from TradingConfigService
        TradeConfig.TradePreference tradePreference = dayTradingSettingService.getDefaultTradePreference();
        ExitSettings exitSettings = dayTradingSettingService.getExitSettings();
        
        log.info("🔧 SAVING TRADE CONFIG - Date: {}, TradePreference: {}, ExitSettings: {}", 
                formattedDate, tradePreference != null ? "LOADED" : "NULL", exitSettings != null ? "LOADED" : "NULL");
        
        // Fail fast if configuration is not available
        if (tradePreference == null) {
            throw new RuntimeException("TradePreference is NULL from TradingConfigService. Configuration not properly loaded at startup.");
        }
        
        if (exitSettings == null) {
            throw new RuntimeException("ExitSettings is NULL from TradingConfigService. Configuration not properly loaded at startup.");
        }
        
        // Validate that TradePreference has required values
        if (tradePreference.getMaxProfitPerDay() <= 0) {
            String errorMsg = "TradePreference.maxProfitPerDay is not set or invalid: " + tradePreference.getMaxProfitPerDay() + ". Configuration must be properly set.";
            log.error("❌ CONFIGURATION ERROR: {}", errorMsg);
            throw new RuntimeException(errorMsg);
        }
        
        if (tradePreference.getMaxLossPerDay() <= 0) {
            String errorMsg = "TradePreference.maxLossPerDay is not set or invalid: " + tradePreference.getMaxLossPerDay() + ". Configuration must be properly set.";
            log.error("❌ CONFIGURATION ERROR: {}", errorMsg);
            throw new RuntimeException(errorMsg);
        }
        
        log.info("🔧 TRADE PREFERENCE VALIDATION - MaxProfitPerDay: {}, MaxLossPerDay: {}", 
                tradePreference.getMaxProfitPerDay(), tradePreference.getMaxLossPerDay());
        
        if (existingConfig != null) {
            // Update existing record
            existingConfig.setAccessToken(user.accessToken);
            existingConfig.setTradePreference(tradePreference);
            existingConfig.setUpdatedAt(currentTimestamp);
            tradeConfigRepository.save(existingConfig);
            // Invalidate cache for this date
            tradeConfigCache.remove(formattedDate);
            log.info("✅ Updated existing TradeConfig for date: {} with tradePreference and invalidated cache", formattedDate);
        } else {
            // Create new record
            TradeConfig newConfig = new TradeConfig();
            newConfig.setDate(formattedDate);
            newConfig.setAccessToken(user.accessToken);
            newConfig.setTradePreference(tradePreference);
            newConfig.setExitSettings(exitSettings);
            newConfig.setCreatedAt(currentTimestamp);
            newConfig.setUpdatedAt(currentTimestamp);
            try {
                tradeConfigRepository.save(newConfig);
                // Invalidate cache for this date
                tradeConfigCache.remove(formattedDate);
                log.info("✅ Created new TradeConfig for date: {} with tradePreference and exitSettings, invalidated cache", formattedDate);
            } catch (DuplicateKeyException e) {
                log.warn("Duplicate TradeConfig detected for date: {}. Removing existing and creating new one.", formattedDate);
                // Remove existing record and create new one
                tradeConfigRepository.deleteByDate(formattedDate);
                tradeConfigRepository.save(newConfig);
                // Invalidate cache for this date
                tradeConfigCache.remove(formattedDate);
                log.info("✅ Successfully replaced TradeConfig for date: {} with tradePreference and exitSettings, invalidated cache", formattedDate);
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
     * Delete all calculated_ticks for a specific date
     * @param dateString The date in format yyyy-MM-dd (e.g., "2024-10-09")
     * @return Number of documents deleted
     */
    public long deleteCalculatedTicksByDate(String dateString) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            dateFormat.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Kolkata"));
            
            // Parse the date string
            Date targetDate = dateFormat.parse(dateString);
            
            // Set up start and end of the day (IST timezone)
            Calendar calendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"));
            calendar.setTime(targetDate);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            Date startOfDay = calendar.getTime();
            
            calendar.set(Calendar.HOUR_OF_DAY, 23);
            calendar.set(Calendar.MINUTE, 59);
            calendar.set(Calendar.SECOND, 59);
            calendar.set(Calendar.MILLISECOND, 999);
            Date endOfDay = calendar.getTime();
            
            log.info("🗑️ Deleting calculated_ticks for date: {} (from {} to {})", dateString, startOfDay, endOfDay);
            
            // Find all ticks for this date
            List<CalculatedTick> ticksToDelete = tickRepository.findAllLastTradedTimes().stream()
                .filter(calculatedTick -> {
                    TickModel tick = calculatedTick.getTick();
                    Date lastTradedTime = tick.getLastTradedTime();
                    return lastTradedTime != null && 
                           !lastTradedTime.before(startOfDay) && 
                           !lastTradedTime.after(endOfDay);
                })
                .toList();
            
            // Delete the ticks
            if (!ticksToDelete.isEmpty()) {
                tickRepository.deleteAll(ticksToDelete);
                log.info("✅ Deleted {} calculated_ticks for date: {}", ticksToDelete.size(), dateString);
                return ticksToDelete.size();
            } else {
                log.info("ℹ️ No calculated_ticks found for date: {}", dateString);
                return 0;
            }
            
        } catch (Exception e) {
            log.error("❌ Error deleting calculated_ticks for date: {}", dateString, e);
            throw new RuntimeException("Failed to delete calculated_ticks for date: " + dateString, e);
        }
    }

    /**
     * Update a specific field in the trade preference or exit settings
     * @param field The field name to update (e.g., "tradePreference.maxInvestment" or "exitSettings.priceMovementExitEnabled")
     * @param value The new value for the field
     * @return true if update was successful, false otherwise
     */
    public boolean updateTradePreference(String field, Object value) {
        try {
            String formattedDate = getTodaysDateString("Asia/Kolkata", "'IST-'yyyy-MM-dd");
            TradeConfig tradeConfig = tradeConfigRepository.findByDate(formattedDate).orElse(null);
            
            if (tradeConfig == null) {
                log.error("No TradeConfig found for date: {}", formattedDate);
                return false;
            }
            
            // Parse the field path (e.g., "tradePreference.maxInvestment" or "exitSettings.priceMovementExitEnabled")
            String[] fieldParts = field.split("\\.");
            if (fieldParts.length != 2) {
                log.error("Invalid field format: {}. Expected format: tradePreference.fieldName or exitSettings.fieldName", field);
                return false;
            }
            
            String section = fieldParts[0];
            String fieldName = fieldParts[1];
            String currentTimestamp = getCurrentISTTimestamp();
            
            if ("tradePreference".equals(section)) {
                return updateTradePreferenceField(tradeConfig, fieldName, value, currentTimestamp);
            } else if ("exitSettings".equals(section)) {
                return updateExitSettingsField(tradeConfig, fieldName, value, currentTimestamp);
            } else {
                log.error("Invalid section: {}. Expected: tradePreference or exitSettings", section);
                return false;
            }
            
        } catch (IllegalArgumentException e) {
            log.error("Validation error updating trade preference: {}", e.getMessage());
            throw e; // Re-throw to be handled by controller
        } catch (Exception e) {
            log.error("Failed to update trade preference for field {}: {}", field, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Update a specific field in the trade preference section
     */
    private boolean updateTradePreferenceField(TradeConfig tradeConfig, String fieldName, Object value, String currentTimestamp) {
            // Ensure tradePreference exists
            if (tradeConfig.getTradePreference() == null) {
                tradeConfig.setTradePreference(dayTradingSettingService.getDefaultTradePreference());
            }
        
        TradeConfig.TradePreference preferences = tradeConfig.getTradePreference();
        
        // Update the specific field based on field name
        switch (fieldName) {
            case "maxInvestment":
                if (value instanceof Number) {
                    double maxInvestment = ((Number) value).doubleValue();
                    if (maxInvestment <= 0) {
                        throw new IllegalArgumentException("Max investment must be greater than 0");
                    }
                    preferences.setMaxInvestment(maxInvestment);
                    log.info("Updated maxInvestment to: {}", maxInvestment);
                } else {
                    throw new IllegalArgumentException("Max investment must be a number");
                }
                break;
                
            case "minQuantity":
                if (value instanceof Number) {
                    int minQuantity = ((Number) value).intValue();
                    if (minQuantity <= 0) {
                        throw new IllegalArgumentException("Min quantity must be greater than 0");
                    }
                    preferences.setMinQuantity(minQuantity);
                    log.info("Updated minQuantity to: {}", minQuantity);
                } else {
                    throw new IllegalArgumentException("Min quantity must be a number");
                }
                break;
                
            case "maxQuantity":
                if (value instanceof Number) {
                    int maxQuantity = ((Number) value).intValue();
                    if (maxQuantity <= 0) {
                        throw new IllegalArgumentException("Max quantity must be greater than 0");
                    }
                    preferences.setMaxQuantity(maxQuantity);
                    log.info("Updated maxQuantity to: {}", maxQuantity);
                } else {
                    throw new IllegalArgumentException("Max quantity must be a number");
                }
                break;
                
            case "maxLossPerDay":
                if (value instanceof Number) {
                    double maxLossPerDay = ((Number) value).doubleValue();
                    if (maxLossPerDay < 0) {
                        throw new IllegalArgumentException("Max loss per day must be greater than or equal to 0");
                    }
                    preferences.setMaxLossPerDay(maxLossPerDay);
                    log.info("Updated maxLossPerDay to: {}", maxLossPerDay);
                } else {
                    throw new IllegalArgumentException("Max loss per day must be a number");
                }
                break;
                
            case "maxProfitPerDay":
                if (value instanceof Number) {
                    double maxProfitPerDay = ((Number) value).doubleValue();
                    if (maxProfitPerDay < 0) {
                        throw new IllegalArgumentException("Max profit per day must be greater than or equal to 0");
                    }
                    preferences.setMaxProfitPerDay(maxProfitPerDay);
                    log.info("Updated maxProfitPerDay to: {}", maxProfitPerDay);
                } else {
                    throw new IllegalArgumentException("Max profit per day must be a number");
                }
                break;
                
            case "maxTradeHoldingTimeInSec":
                if (value instanceof Number) {
                    long maxHoldingTime = ((Number) value).longValue();
                    if (maxHoldingTime <= 0) {
                        throw new IllegalArgumentException("Max trade holding time must be greater than 0");
                    }
                    preferences.setMaxTradeHoldingTimeInSec(maxHoldingTime);
                    log.info("Updated maxTradeHoldingTimeInSec to: {}", maxHoldingTime);
                } else {
                    throw new IllegalArgumentException("Max trade holding time must be a number");
                }
                break;
                
            case "enableTradeAfterStopLossHit":
                if (value instanceof Boolean) {
                    boolean enableTrade = (Boolean) value;
                    preferences.setEnableTradeAfterStopLossHit(enableTrade);
                    log.info("Updated enableTradeAfterStopLossHit to: {}", enableTrade);
                } else {
                    throw new IllegalArgumentException("enableTradeAfterStopLossHit must be a boolean");
                }
                break;
                
            case "stopLossBlockTimeframe":
                if (value instanceof String) {
                    String timeframe = ((String) value).trim().toUpperCase();
                    // Validate that it's a valid CandleTimeFrameEnum value
                    try {
                        com.jtradebot.processor.model.enums.CandleTimeFrameEnum.valueOf(timeframe);
                        preferences.setStopLossBlockTimeframe(timeframe);
                        log.info("Updated stopLossBlockTimeframe to: {}", timeframe);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("stopLossBlockTimeframe must be a valid candle timeframe (ONE_MIN, THREE_MIN, FIVE_MIN, FIFTEEN_MIN, ONE_HOUR, ONE_DAY)");
                    }
                } else {
                    throw new IllegalArgumentException("stopLossBlockTimeframe must be a string");
                }
                break;
                
            default:
                log.error("Unknown tradePreference field: {}", fieldName);
                return false;
        }
        
        // Update the timestamp and save
        tradeConfig.setUpdatedAt(currentTimestamp);
        tradeConfigRepository.save(tradeConfig);
        
        // Invalidate cache for this date
        String formattedDate = getTodaysDateString("Asia/Kolkata", "'IST-'yyyy-MM-dd");
        tradeConfigCache.remove(formattedDate);
        log.info("Successfully updated tradePreference.{} to {} and invalidated cache", fieldName, value);
        
        return true;
    }
    
    /**
     * Update a specific field in the exit settings section
     */
    private boolean updateExitSettingsField(TradeConfig tradeConfig, String fieldName, Object value, String currentTimestamp) {
        // Ensure exitSettings exists
        if (tradeConfig.getExitSettings() == null) {
            tradeConfig.setExitSettings(new com.jtradebot.processor.model.ExitSettings());
        }
        
        com.jtradebot.processor.model.ExitSettings exitSettings = tradeConfig.getExitSettings();
        
        // Update the specific field based on field name
        switch (fieldName) {
            case "milestoneBasedExitEnabled":
                boolean milestoneEnabled = parseBooleanValue(value);
                exitSettings.setMilestoneBasedExitEnabled(milestoneEnabled);
                log.info("Updated milestoneBasedExitEnabled to: {}", milestoneEnabled);
                break;
                
            case "priceMovementExitEnabled":
                boolean priceMovementEnabled = parseBooleanValue(value);
                exitSettings.setPriceMovementExitEnabled(priceMovementEnabled);
                log.info("Updated priceMovementExitEnabled to: {}", priceMovementEnabled);
                break;
                
            case "timeBasedExitEnabled":
                boolean timeBasedEnabled = parseBooleanValue(value);
                exitSettings.setTimeBasedExitEnabled(timeBasedEnabled);
                log.info("Updated timeBasedExitEnabled to: {}", timeBasedEnabled);
                break;
                
            case "strategyBasedExitEnabled":
                boolean strategyBasedEnabled = parseBooleanValue(value);
                exitSettings.setStrategyBasedExitEnabled(strategyBasedEnabled);
                log.info("Updated strategyBasedExitEnabled to: {}", strategyBasedEnabled);
                break;
                
            case "stopLossTargetExitEnabled":
                boolean stopLossTargetEnabled = parseBooleanValue(value);
                exitSettings.setStopLossTargetExitEnabled(stopLossTargetEnabled);
                log.info("Updated stopLossTargetExitEnabled to: {}", stopLossTargetEnabled);
                break;
                
            case "milestoneBasedExitDescription":
                if (value instanceof String) {
                    exitSettings.setMilestoneBasedExitDescription((String) value);
                    log.info("Updated milestoneBasedExitDescription to: {}", value);
                } else {
                    throw new IllegalArgumentException("Milestone based exit description must be a string");
                }
                break;
                
            case "priceMovementExitDescription":
                if (value instanceof String) {
                    exitSettings.setPriceMovementExitDescription((String) value);
                    log.info("Updated priceMovementExitDescription to: {}", value);
                } else {
                    throw new IllegalArgumentException("Price movement exit description must be a string");
                }
                break;
                
            default:
                log.error("Unknown exitSettings field: {}", fieldName);
                return false;
        }
        
        // Update the last modified timestamp
        exitSettings.updateLastModified();
        
        // Update the timestamp and save
        tradeConfig.setUpdatedAt(currentTimestamp);
        tradeConfigRepository.save(tradeConfig);
        
        // Invalidate cache for this date
        String formattedDate = getTodaysDateString("Asia/Kolkata", "'IST-'yyyy-MM-dd");
        tradeConfigCache.remove(formattedDate);
        log.info("Successfully updated exitSettings.{} to {} and invalidated cache", fieldName, value);
        
        return true;
    }
    
    /**
     * Parse a boolean value from various input types
     */
    private boolean parseBooleanValue(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            String stringValue = ((String) value).toLowerCase().trim();
            if ("true".equals(stringValue) || "1".equals(stringValue)) {
                return true;
            } else if ("false".equals(stringValue) || "0".equals(stringValue)) {
                return false;
            } else {
                throw new IllegalArgumentException("Invalid boolean value: " + value + ". Expected: true, false, 1, or 0");
            }
        } else if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        } else {
            throw new IllegalArgumentException("Invalid boolean value: " + value + ". Expected: boolean, string, or number");
        }
    }
    


}
