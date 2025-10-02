package com.jtradebot.processor.config;

import com.jtradebot.processor.model.ExitSettings;
import com.jtradebot.processor.repository.TradeConfigRepository;
import com.jtradebot.processor.repository.document.TradeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import static com.jtradebot.processor.handler.DateTimeHandler.getTodaysDateString;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExitSettingsService implements InitializingBean {

    private final TradeConfigRepository tradeConfigRepository;
    private ExitSettings exitSettings;

    @Override
    public void afterPropertiesSet() {
        loadExitSettings();
    }

    /**
     * Load exit settings from database
     */
    private void loadExitSettings() {
        try {
            String today = getTodaysDateString("Asia/Kolkata", "'IST-'yyyy-MM-dd");
            TradeConfig tradeConfig = tradeConfigRepository.findByDate(today).orElse(null);
            
            if (tradeConfig != null && tradeConfig.getExitSettings() != null) {
                this.exitSettings = tradeConfig.getExitSettings();
                log.info("✅ Exit settings loaded from database - Milestone: {}, PriceMovement: {}, Time: {}, Strategy: {}, StopLoss: {}", 
                    exitSettings.isMilestoneBasedExitEnabled(), exitSettings.isPriceMovementExitEnabled(),
                    exitSettings.isTimeBasedExitEnabled(), exitSettings.isStrategyBasedExitEnabled(), exitSettings.isStopLossTargetExitEnabled());
            } else {
                // Create default exit settings
                this.exitSettings = new ExitSettings();
                log.info("📝 Created default exit settings - All exit types enabled");
            }
        } catch (Exception e) {
            log.error("❌ Error loading exit settings, using defaults", e);
            this.exitSettings = new ExitSettings();
        }
    }

    /**
     * Get current exit settings
     */
    public ExitSettings getExitSettings() {
        return exitSettings;
    }

    /**
     * Update exit settings
     */
    public void updateExitSettings(ExitSettings newExitSettings) {
        try {
            // Update the cached settings
            this.exitSettings = newExitSettings;
            this.exitSettings.updateLastModified();
            
            // Update in database
            String today = getTodaysDateString("Asia/Kolkata", "'IST-'yyyy-MM-dd");
            TradeConfig tradeConfig = tradeConfigRepository.findByDate(today).orElse(null);
            
            if (tradeConfig != null) {
                tradeConfig.setExitSettings(newExitSettings);
                tradeConfigRepository.save(tradeConfig);
                
                log.info("✅ Exit settings updated - Milestone: {}, PriceMovement: {}, Time: {}, Strategy: {}, StopLoss: {}", 
                    newExitSettings.isMilestoneBasedExitEnabled(), newExitSettings.isPriceMovementExitEnabled(),
                    newExitSettings.isTimeBasedExitEnabled(), newExitSettings.isStrategyBasedExitEnabled(), newExitSettings.isStopLossTargetExitEnabled());
            } else {
                log.warn("⚠️ No trade config found for today ({}) to update exit settings", today);
            }
        } catch (Exception e) {
            log.error("❌ Error updating exit settings", e);
        }
    }

    /**
     * Refresh exit settings from database
     */
    public void refreshExitSettings() {
        log.info("🔄 Refreshing exit settings from database");
        loadExitSettings();
    }

    // Convenience methods for checking specific exit types
    public boolean isMilestoneBasedExitEnabled() {
        return exitSettings != null && exitSettings.isMilestoneBasedExitEnabled();
    }

    public boolean isPriceMovementExitEnabled() {
        return exitSettings != null && exitSettings.isPriceMovementExitEnabled();
    }

    public boolean isTimeBasedExitEnabled() {
        return exitSettings != null && exitSettings.isTimeBasedExitEnabled();
    }

    public boolean isStrategyBasedExitEnabled() {
        return exitSettings != null && exitSettings.isStrategyBasedExitEnabled();
    }

    public boolean isStopLossTargetExitEnabled() {
        return exitSettings != null && exitSettings.isStopLossTargetExitEnabled();
    }
}
