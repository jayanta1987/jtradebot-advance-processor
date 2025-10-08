package com.jtradebot.processor.service.scheduler;

import com.jtradebot.processor.repository.JtradeOrderRepository;
import com.jtradebot.processor.repository.document.JtradeOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Scheduler service that runs every 30 minutes to check if any orders in the jtrade_orders collection
 * have an entry date older than today's date. If found, it clears the entire collection.
 * This scheduler runs only in the live profile.
 */
@Service
@Profile("live")
@RequiredArgsConstructor
@Slf4j
public class OrderCollectionCleanupSchedulerService {

    private final JtradeOrderRepository jtradeOrderRepository;

    // DateTimeFormatter matching the format: "07 Oct 2025 09:45:45 IST"
    private static final DateTimeFormatter ENTRY_TIME_FORMATTER = 
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss z");

    /**
     * Scheduled task that runs every 30 minutes to check and cleanup old orders.
     * Runs at 0 and 30 minutes past every hour (e.g., 09:00, 09:30, 10:00, 10:30, etc.)
     */
    @Scheduled(cron = "0 */30 * * * *")
    public void cleanupOldOrdersIfNeeded() {
        try {
            log.warn("🧹 ORDER CLEANUP SCHEDULER - Starting order collection cleanup check");

            // Get all orders from the collection
            List<JtradeOrder> allOrders = jtradeOrderRepository.findAll();

            if (allOrders.isEmpty()) {
                log.warn("📭 ORDER CLEANUP SCHEDULER - No orders found in collection, skipping cleanup");
                return;
            }

            log.info("📊 ORDER CLEANUP SCHEDULER - Found {} orders in collection, checking for old orders", 
                    allOrders.size());

            // Get today's date in IST timezone
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
            log.warn("📅 ORDER CLEANUP SCHEDULER - Today's date (IST): {}", today);

            // Check if any order has an entry date older than today
            boolean hasOldOrders = checkForOldOrders(allOrders, today);

            if (hasOldOrders) {
                log.warn("⚠️ ORDER CLEANUP SCHEDULER - Found orders with entry date older than today, clearing all orders");
                
                // Delete all orders from the collection
                jtradeOrderRepository.deleteAll();
                
                log.info("✅ ORDER CLEANUP SCHEDULER - Successfully cleared {} orders from collection", 
                        allOrders.size());
            } else {
                log.info("✅ ORDER CLEANUP SCHEDULER - All orders are from today, no cleanup needed");
            }

        } catch (Exception e) {
            log.error("❌ ORDER CLEANUP SCHEDULER - Error during order cleanup check: {}", 
                    e.getMessage(), e);
        }
    }

    /**
     * Checks if any order in the list has an entry date older than the given date
     * 
     * @param orders List of orders to check
     * @param today Today's date in IST timezone
     * @return true if any order has an entry date older than today, false otherwise
     */
    private boolean checkForOldOrders(List<JtradeOrder> orders, LocalDate today) {
        for (JtradeOrder order : orders) {
            String entryTime = order.getEntryTime();
            
            if (entryTime == null || entryTime.trim().isEmpty()) {
                log.warn("⚠️ ORDER CLEANUP SCHEDULER - Order {} has null/empty entryTime, skipping", 
                        order.getId());
                continue;
            }

            try {
                // Parse the entry time and extract only the date part
                LocalDate entryDate = parseEntryDate(entryTime);
                
                log.warn("🔍 ORDER CLEANUP SCHEDULER - Order {} - EntryTime: {}, EntryDate: {}, Today: {}",
                        order.getId(), entryTime, entryDate, today);

                // Compare only the date part (day comparison)
                if (entryDate.isBefore(today)) {
                    log.warn("🚨 ORDER CLEANUP SCHEDULER - Found old order - ID: {}, EntryTime: {}, EntryDate: {}, Today: {}", 
                            order.getId(), entryTime, entryDate, today);
                    return true;
                }
                
            } catch (Exception e) {
                log.error("❌ ORDER CLEANUP SCHEDULER - Error parsing entryTime '{}' for order {}: {}", 
                        entryTime, order.getId(), e.getMessage());
                // Continue checking other orders even if one fails to parse
            }
        }
        
        return false;
    }

    /**
     * Parses the entry time string and extracts the date part
     * Format: "07 Oct 2025 09:45:45 IST"
     * 
     * @param entryTime Entry time string
     * @return LocalDate extracted from the entry time
     */
    private LocalDate parseEntryDate(String entryTime) {
        // Parse the full timestamp first
        java.time.ZonedDateTime zonedDateTime = java.time.ZonedDateTime.parse(
                entryTime, ENTRY_TIME_FORMATTER);
        
        // Extract only the date part
        return zonedDateTime.toLocalDate();
    }
}
