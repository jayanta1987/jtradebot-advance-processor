package com.jtradebot.processor.service.scheduler;

import com.jtradebot.processor.common.ProfileUtil;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.repository.InstrumentRepository;
import com.jtradebot.processor.repository.document.Instrument;
import com.jtradebot.processor.service.notification.ExceptionNotifier;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

import static com.jtradebot.processor.handler.DateTimeHandler.getTodaysDateString;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstrumentFreshnessCheckerService {

    private final InstrumentRepository instrumentRepository;
    private final KiteInstrumentHandler kiteInstrumentHandler;
    private final ExceptionNotifier exceptionNotifier;
    private final Environment environment;

    /**
     * Instrument freshness checker scheduler that runs every 5 minutes
     * Checks if instrument data is current and refreshes if needed
     */
    @Scheduled(cron = "0 */5 * * * *") // Run every 5 minutes at 0 seconds (e.g., 12:00:00, 12:05:00, 12:10:00)
    public void checkInstrumentFreshness() {
        try {
            // Only run in live profile
            if (!ProfileUtil.isProfileActive(environment, "live")) {
                log.debug("Instrument freshness checker skipped - not in live profile");
                return;
            }

            log.debug("🔄 INSTRUMENT FRESHNESS CHECKER - Starting instrument freshness check");

            // Check if instruments exist and are current
            if (isInstrumentDataCurrent()) {
                log.info("✅ INSTRUMENT FRESHNESS CHECKER - Instrument data is current, no refresh needed");
                return;
            }

            log.info("🔄 INSTRUMENT FRESHNESS CHECKER - Instrument data is outdated, refreshing...");

            // Refresh instruments
            refreshInstruments();

            log.info("✅ INSTRUMENT FRESHNESS CHECKER - Instrument refresh completed successfully");

        } catch (Exception e) {
            log.error("❌ INSTRUMENT FRESHNESS CHECKER - Error in instrument freshness check: {}", e.getMessage(), e);

            // Send failure notification
            exceptionNotifier.sendInstrumentGenerationFailureNotification(
                    "Failed to check/refresh instrument freshness", e);
        }
    }

    /**
     * Check if instrument data is current (created today) - OPTIMIZED VERSION
     */
    private boolean isInstrumentDataCurrent() {
        try {
            // Get today's date in IST format
            String todayDate = getTodaysDateString("Asia/Kolkata", "'IST-'yyyy-MM-dd");

            // Use existsByCreatedAt for maximum performance - only checks if any record exists
            boolean exists = instrumentRepository.existsByCreatedAt(todayDate);

            if (!exists) {
                log.info("📊 INSTRUMENT CHECK - No instruments found for today's date: {}", todayDate);
                return false;
            }

            // Only get count if we need to log it (optional optimization)
            long count = instrumentRepository.countByCreatedAt(todayDate);
            log.info("📊 INSTRUMENT CHECK - Found {} instruments for today's date: {}", count, todayDate);
            return true;

        } catch (Exception e) {
            log.error("❌ INSTRUMENT CHECK - Error checking instrument freshness: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Refresh instruments by calling the Kite API
     */
    private void refreshInstruments() {
        try {
            log.info("🔄 INSTRUMENT REFRESH - Starting instrument refresh from Kite API");

            // Delete existing instruments
            kiteInstrumentHandler.deleteInstruments();
            log.info("🗑️ INSTRUMENT REFRESH - Deleted existing instruments");

            // Generate new instruments
            kiteInstrumentHandler.saveInstrumentTokens("NFO");
            log.info("✅ INSTRUMENT REFRESH - Generated new instruments from Kite API");

            // Verify the refresh was successful
            String todayDate = getTodaysDateString("Asia/Kolkata", "'IST-'yyyy-MM-dd");
            List<Instrument> newInstruments = instrumentRepository.findByCreatedAt(todayDate);

            if (newInstruments.isEmpty()) {
                throw new RuntimeException("No instruments found after refresh for date: " + todayDate);
            }

            log.info("✅ INSTRUMENT REFRESH - Successfully refreshed {} instruments", newInstruments.size());

            // Send success notification
            exceptionNotifier.sendInstrumentGenerationSuccessNotification(newInstruments.size());

        } catch (IOException e) {
            log.error("❌ INSTRUMENT REFRESH - IOException during instrument refresh: {}", e.getMessage(), e);
            throw new RuntimeException("IOException during instrument refresh", e);
        } catch (KiteException e) {
            log.error("❌ INSTRUMENT REFRESH - KiteException during instrument refresh: {}", e.getMessage(), e);
            throw new RuntimeException("KiteException during instrument refresh", e);
        } catch (Exception e) {
            log.error("❌ INSTRUMENT REFRESH - Error during instrument refresh: {}", e.getMessage(), e);
            throw new RuntimeException("Error during instrument refresh", e);
        }
    }

    /**
     * Manually check instrument freshness and return detailed results
     * This method can be called by API endpoints to return results instead of just logging
     */
    public InstrumentFreshnessResult checkInstrumentFreshnessWithResults() {
        InstrumentFreshnessResult result = new InstrumentFreshnessResult();

        try {
            // Only run in live profile
            /*if (!ProfileUtil.isProfileActive(environment, "live")) {
                result.setSuccess(false);
                result.setMessage("Instrument freshness check skipped - not in live profile");
                return result;
            }*/

            String todayDate = getTodaysDateString("Asia/Kolkata", "'IST-'yyyy-MM-dd");
            result.setTodayDate(todayDate);

            // Check current instruments using optimized queries
            boolean exists = instrumentRepository.existsByCreatedAt(todayDate);
            long count = exists ? instrumentRepository.countByCreatedAt(todayDate) : 0;

            result.setCurrentInstrumentCount((int) count);
            result.setCurrent(exists);

            if (!exists) {
                result.setMessage("No instruments found for today's date: " + todayDate);
                result.setNeedsRefresh(true);
            } else {
                result.setMessage("Found " + count + " instruments for today's date: " + todayDate);
                result.setNeedsRefresh(false);
            }

            result.setSuccess(true);

        } catch (Exception e) {
            log.error("❌ INSTRUMENT FRESHNESS CHECK - Error in instrument freshness check: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setMessage("Error during instrument freshness check: " + e.getMessage());
            result.setNeedsRefresh(true);
        }

        return result;
    }

    /**
     * Result class for instrument freshness check
     */
    @Getter
    @Setter
    public static class InstrumentFreshnessResult {
        private boolean success;
        private String message;
        private String todayDate;
        private int currentInstrumentCount;
        private boolean isCurrent;
        private boolean needsRefresh;
    }
}
