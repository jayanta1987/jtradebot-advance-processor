package com.jtradebot.processor.service.logging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
@Slf4j
public class PerformanceLoggingService {

    private final Map<String, Instant> startTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> operationCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> totalDurations = new ConcurrentHashMap<>();

    /**
     * Start timing an operation
     */
    public void startTiming(String operationName) {
        startTimes.put(operationName, Instant.now());
    }

    /**
     * End timing an operation and log the duration
     */
    public void endTiming(String operationName) {
        Instant startTime = startTimes.remove(operationName);
        if (startTime != null) {
            Duration duration = Duration.between(startTime, Instant.now());
            log.debug("⏱️ {} completed in {} ms", operationName, duration.toMillis());
            
            // Update performance metrics
            operationCounts.merge(operationName, 1L, Long::sum);
            totalDurations.merge(operationName, duration.toMillis(), Long::sum);
        }
    }

    /**
     * Log performance metrics for an operation
     */
    public void logPerformanceMetrics(String operationName) {
        Long count = operationCounts.get(operationName);
        Long totalDuration = totalDurations.get(operationName);
        
        if (count != null && totalDuration != null && count > 0) {
            double averageDuration = (double) totalDuration / count;
            log.info("📊 Performance Metrics - {}: {} operations, avg {} ms, total {} ms", 
                operationName, count, String.format("%.2f", averageDuration), totalDuration);
        }
    }

    /**
     * Log tick processing performance
     */
    public void logTickProcessingPerformance(int tickCount, long processingTimeMs) {
        double avgTimePerTick = (double) processingTimeMs / tickCount;
        log.info("📊 Tick Processing Performance - {} ticks in {} ms (avg {:.2f} ms/tick)", 
            tickCount, processingTimeMs, avgTimePerTick);
    }

    /**
     * Log indicator calculation performance
     */
    public void logIndicatorCalculationPerformance(String indicatorType, long calculationTimeMs) {
        log.debug("📊 {} calculation completed in {} ms", indicatorType, calculationTimeMs);
    }

    /**
     * Log order execution performance
     */
    public void logOrderExecutionPerformance(String orderType, long executionTimeMs) {
        log.info("📊 {} order execution completed in {} ms", orderType, executionTimeMs);
    }

    /**
     * Log memory usage
     */
    public void logMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        double usedPercentage = (double) usedMemory / maxMemory * 100;
        
        log.info("💾 Memory Usage - Used: {} MB, Free: {} MB, Total: {} MB, Max: {} MB ({:.1f}%)", 
            usedMemory / 1024 / 1024,
            freeMemory / 1024 / 1024,
            totalMemory / 1024 / 1024,
            maxMemory / 1024 / 1024,
            usedPercentage);
    }

    /**
     * Log cache hit/miss ratio
     */
    public void logCachePerformance(String cacheName, long hits, long misses) {
        long total = hits + misses;
        if (total > 0) {
            double hitRatio = (double) hits / total * 100;
            log.debug("📊 Cache Performance - {}: {}/{} hits ({:.1f}% hit ratio)", 
                cacheName, hits, total, hitRatio);
        }
    }

    /**
     * Log database operation performance
     */
    public void logDatabasePerformance(String operation, long durationMs) {
        if (durationMs > 100) {
            log.warn("🐌 Slow Database Operation - {} took {} ms", operation, durationMs);
        } else {
            log.debug("📊 Database Operation - {} completed in {} ms", operation, durationMs);
        }
    }

    /**
     * Log API call performance
     */
    public void logApiCallPerformance(String apiName, long durationMs, boolean success) {
        String status = success ? "✅" : "❌";
        if (durationMs > 1000) {
            log.warn("🐌 Slow API Call - {} {} took {} ms", status, apiName, durationMs);
        } else {
            log.debug("📊 API Call - {} {} completed in {} ms", status, apiName, durationMs);
        }
    }

    /**
     * Log thread pool performance
     */
    public void logThreadPoolPerformance(String poolName, int activeThreads, int maxThreads, int queueSize) {
        double utilization = (double) activeThreads / maxThreads * 100;
        log.debug("📊 Thread Pool - {}: {}/{} active threads ({:.1f}% utilization), queue size: {}", 
            poolName, activeThreads, maxThreads, utilization, queueSize);
    }

    /**
     * Log garbage collection performance
     */
    public void logGarbageCollectionPerformance(String gcName, long durationMs, long freedMemory) {
        log.info("🗑️ Garbage Collection - {}: {} ms, freed {} MB", 
            gcName, durationMs, freedMemory / 1024 / 1024);
    }

    /**
     * Log system load
     */
    public void logSystemLoad() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        double systemLoad = getSystemLoadAverage();
        
        if (systemLoad > 0) {
            double loadPerCore = systemLoad / availableProcessors;
            log.info("📊 System Load - {:.2f} total, {:.2f} per core ({} cores)", 
                systemLoad, loadPerCore, availableProcessors);
        }
    }

    /**
     * Log performance warning
     */
    public void logPerformanceWarning(String operation, long durationMs, long thresholdMs) {
        log.warn("⚠️ Performance Warning - {} took {} ms (threshold: {} ms)", 
            operation, durationMs, thresholdMs);
    }

    /**
     * Log performance error
     */
    public void logPerformanceError(String operation, long durationMs, long timeoutMs) {
        log.error("❌ Performance Error - {} timed out after {} ms (timeout: {} ms)", 
            operation, durationMs, timeoutMs);
    }

    /**
     * Reset performance metrics
     */
    public void resetPerformanceMetrics() {
        operationCounts.clear();
        totalDurations.clear();
        startTimes.clear();
        log.info("🔄 Performance metrics reset");
    }

    /**
     * Get system load average (simplified implementation)
     */
    private double getSystemLoadAverage() {
        try {
            // This is a simplified implementation
            // In a real application, you might use ManagementFactory.getOperatingSystemMXBean()
            return 0.0; // Placeholder
        } catch (Exception e) {
            log.debug("Could not get system load average: {}", e.getMessage());
            return 0.0;
        }
    }
}
