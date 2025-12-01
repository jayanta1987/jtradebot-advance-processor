package com.jtradebot.processor.service.scheduler;

import com.jtradebot.processor.common.ProfileUtil;
import com.jtradebot.processor.repository.ArchivedOrderRepository;
import com.jtradebot.processor.repository.JtradeOrderRepository;
import com.jtradebot.processor.repository.document.ArchivedOrder;
import com.jtradebot.processor.repository.document.JtradeOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for archiving old orders to archived_orders collection.
 * The scheduler runs automatically every 30 minutes only in the live profile.
 * Manual archiving via API is available in both live and local profiles.
 */
@Service
@Profile({"live", "local"})
@RequiredArgsConstructor
@Slf4j
public class OrderCollectionCleanupSchedulerService {

    private final JtradeOrderRepository jtradeOrderRepository;
    private final ArchivedOrderRepository archivedOrderRepository;
    private final Environment environment;

    // DateTimeFormatter matching the format: "07 Oct 2025 09:45:45 IST"
    private static final DateTimeFormatter ENTRY_TIME_FORMATTER = 
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss z");

    /**
     * Scheduled task that runs every 30 minutes to check and cleanup old orders.
     * Runs at 0 and 30 minutes past every hour (e.g., 09:00, 09:30, 10:00, 10:30, etc.)
     * Only runs in the "live" profile - skipped in "local" profile
     */
    @Scheduled(cron = "0 */30 * * * *")
    public void cleanupOldOrdersIfNeeded() {
        // Only run scheduler in live profile
        if (!ProfileUtil.isProfileActive(environment, "live")) {
            return;
        }
        
        try {
            log.warn("🧹 ORDER CLEANUP SCHEDULER - Starting order collection cleanup check");
            archiveOldOrders();
        } catch (Exception e) {
            log.error("❌ ORDER CLEANUP SCHEDULER - Error during order cleanup check: {}", 
                    e.getMessage(), e);
        }
    }

    /**
     * Archives old orders (orders with entry date older than today) to archived_orders collection.
     * This method can be called manually via API or automatically by the scheduler.
     * 
     * @return Map containing success status, message, and details about archived orders
     */
    public Map<String, Object> archiveOldOrders() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.warn("📦 ARCHIVE ORDERS - Starting archive process");

            // Get all orders from the collection
            List<JtradeOrder> allOrders = jtradeOrderRepository.findAll();

            if (allOrders.isEmpty()) {
                log.warn("📭 ARCHIVE ORDERS - No orders found in collection, skipping archive");
                result.put("success", true);
                result.put("message", "No orders found in collection");
                result.put("archivedCount", 0);
                result.put("totalOrders", 0);
                return result;
            }

            log.info("📊 ARCHIVE ORDERS - Found {} orders in collection, checking for old orders", 
                    allOrders.size());

            // Get today's date in IST timezone
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
            log.warn("📅 ARCHIVE ORDERS - Today's date (IST): {}", today);

            // Find orders with entry date older than today
            List<JtradeOrder> oldOrders = findOldOrders(allOrders, today);

            if (!oldOrders.isEmpty()) {
                log.warn("⚠️ ARCHIVE ORDERS - Found {} orders with entry date older than today, moving to archived_orders", 
                        oldOrders.size());
                
                // Move old orders to archived_orders collection
                int archivedCount = moveOrdersToArchive(oldOrders);
                
                // Delete old orders from jtrade_orders collection
                jtradeOrderRepository.deleteAll(oldOrders);
                
                log.info("✅ ARCHIVE ORDERS - Successfully moved {} orders to archived_orders and removed from jtrade_orders", 
                        archivedCount);
                
                result.put("success", true);
                result.put("message", "Successfully archived " + archivedCount + " old orders");
                result.put("archivedCount", archivedCount);
                result.put("totalOrders", allOrders.size());
                result.put("remainingOrders", allOrders.size() - archivedCount);
            } else {
                log.info("✅ ARCHIVE ORDERS - All orders are from today, no archive needed");
                result.put("success", true);
                result.put("message", "All orders are from today, no archive needed");
                result.put("archivedCount", 0);
                result.put("totalOrders", allOrders.size());
                result.put("remainingOrders", allOrders.size());
            }

        } catch (Exception e) {
            log.error("❌ ARCHIVE ORDERS - Error during archive process: {}", 
                    e.getMessage(), e);
            result.put("success", false);
            result.put("message", "Error during archive process: " + e.getMessage());
            result.put("archivedCount", 0);
        }
        
        return result;
    }

    /**
     * Finds all orders in the list that have an entry date older than the given date
     * 
     * @param orders List of orders to check
     * @param today Today's date in IST timezone
     * @return List of orders with entry date older than today
     */
    private List<JtradeOrder> findOldOrders(List<JtradeOrder> orders, LocalDate today) {
        List<JtradeOrder> oldOrders = new ArrayList<>();
        
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
                    oldOrders.add(order);
                }
                
            } catch (Exception e) {
                log.error("❌ ORDER CLEANUP SCHEDULER - Error parsing entryTime '{}' for order {}: {}", 
                        entryTime, order.getId(), e.getMessage());
                // Continue checking other orders even if one fails to parse
            }
        }
        
        return oldOrders;
    }
    
    /**
     * Moves orders to the archived_orders collection using batch save for efficiency
     * 
     * @param orders List of orders to archive
     * @return Number of successfully archived orders
     */
    private int moveOrdersToArchive(List<JtradeOrder> orders) {
        if (orders.isEmpty()) {
            return 0;
        }
        
        String archivedAt = getCurrentISTTime();
        List<ArchivedOrder> archivedOrders = new ArrayList<>();
        
        // Convert all orders to archived orders
        for (JtradeOrder order : orders) {
            try {
                ArchivedOrder archivedOrder = convertToArchivedOrder(order, archivedAt);
                archivedOrders.add(archivedOrder);
            } catch (Exception e) {
                log.error("❌ ARCHIVE ORDERS - Error converting order {} to archived order: {}", 
                        order.getId(), e.getMessage(), e);
            }
        }
        
        // Batch save all archived orders in one DB call
        if (!archivedOrders.isEmpty()) {
            try {
                archivedOrderRepository.saveAll(archivedOrders);
                log.info("📦 ARCHIVE ORDERS - Successfully batch archived {} orders to archived_orders", 
                        archivedOrders.size());
                return archivedOrders.size();
            } catch (Exception e) {
                log.error("❌ ARCHIVE ORDERS - Error batch saving archived orders: {}", 
                        e.getMessage(), e);
                return 0;
            }
        }
        
        return 0;
    }
    
    /**
     * Converts JtradeOrder to ArchivedOrder
     * 
     * @param order Original order
     * @param archivedAt Timestamp when order was archived
     * @return ArchivedOrder instance
     */
    private ArchivedOrder convertToArchivedOrder(JtradeOrder order, String archivedAt) {
        ArchivedOrder archivedOrder = new ArchivedOrder();
        
        // Copy all fields from JtradeOrder to ArchivedOrder
        archivedOrder.setId(order.getId());
        archivedOrder.setOrderType(order.getOrderType());
        archivedOrder.setTradingSymbol(order.getTradingSymbol());
        archivedOrder.setInstrumentToken(order.getInstrumentToken());
        archivedOrder.setEntryTime(order.getEntryTime());
        archivedOrder.setExitTime(order.getExitTime());
        archivedOrder.setEntryPrice(order.getEntryPrice());
        archivedOrder.setExitPrice(order.getExitPrice());
        archivedOrder.setEntryIndexPrice(order.getEntryIndexPrice());
        archivedOrder.setExitIndexPrice(order.getExitIndexPrice());
        archivedOrder.setStopLossPrice(order.getStopLossPrice());
        archivedOrder.setTargetPrice(order.getTargetPrice());
        archivedOrder.setQuantity(order.getQuantity());
        archivedOrder.setKiteOrderId(order.getKiteOrderId());
        archivedOrder.setKiteOrderStatus(order.getKiteOrderStatus());
        archivedOrder.setStatus(order.getStatus());
        archivedOrder.setExitReason(order.getExitReason());
        archivedOrder.setTotalPoints(order.getTotalPoints());
        archivedOrder.setTotalProfit(order.getTotalProfit());
        archivedOrder.setLastUpdated(order.getLastUpdated());
        archivedOrder.setCreatedAt(order.getCreatedAt());
        archivedOrder.setComments(order.getComments());
        archivedOrder.setTargetMilestones(order.getTargetMilestones());
        archivedOrder.setMilestoneHistory(order.getMilestoneHistory());
        archivedOrder.setMinIndexPrice(order.getMinIndexPrice());
        archivedOrder.setMaxIndexPrice(order.getMaxIndexPrice());
        archivedOrder.setEntryConditions(order.getEntryConditions());
        archivedOrder.setEntryMarketConditionSuitable(order.getEntryMarketConditionSuitable());
        archivedOrder.setEntryMarketConditionDetails(order.getEntryMarketConditionDetails());
        archivedOrder.setEntryScenarioName(order.getEntryScenarioName());
        archivedOrder.setEntryScenarioDescription(order.getEntryScenarioDescription());
        archivedOrder.setEntryScenarioConfidence(order.getEntryScenarioConfidence());
        archivedOrder.setEntryCategoryScores(order.getEntryCategoryScores());
        archivedOrder.setEntryMatchedConditions(order.getEntryMatchedConditions());
        archivedOrder.setEntryQualityScore(order.getEntryQualityScore());
        archivedOrder.setEntryDominantTrend(order.getEntryDominantTrend());
        archivedOrder.setEntryDetailedCallScores(order.getEntryDetailedCallScores());
        archivedOrder.setEntryDetailedPutScores(order.getEntryDetailedPutScores());
        archivedOrder.setMandatoryFiltersFailed(order.getMandatoryFiltersFailed());
        archivedOrder.setOptionalFiltersFailed(order.getOptionalFiltersFailed());
        archivedOrder.setTotalFiltersChecked(order.getTotalFiltersChecked());
        archivedOrder.setFilterFailureReason(order.getFilterFailureReason());
        archivedOrder.setEntryNtpDetails(order.getEntryNtpDetails());
        archivedOrder.setEntrySupports(order.getEntrySupports());
        archivedOrder.setEntryResistances(order.getEntryResistances());
        archivedOrder.setEntryNearestSupport(order.getEntryNearestSupport());
        archivedOrder.setEntryNearestResistance(order.getEntryNearestResistance());
        archivedOrder.setEntryDelta(order.getEntryDelta());
        archivedOrder.setEntryGamma(order.getEntryGamma());
        archivedOrder.setEntryTheta(order.getEntryTheta());
        archivedOrder.setEntryVega(order.getEntryVega());
        archivedOrder.setEntryImpliedVolatility(order.getEntryImpliedVolatility());
        archivedOrder.setEntryTimeToExpiry(order.getEntryTimeToExpiry());
        archivedOrder.setEntryStrikePrice(order.getEntryStrikePrice());
        archivedOrder.setEntryOptionType(order.getEntryOptionType());
        
        // Set archive metadata
        archivedOrder.setArchivedAt(archivedAt);
        
        return archivedOrder;
    }
    
    /**
     * Gets current IST time as formatted string
     * 
     * @return Current IST time string
     */
    private String getCurrentISTTime() {
        ZonedDateTime istTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        return istTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss z"));
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
