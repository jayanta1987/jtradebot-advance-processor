package com.jtradebot.processor.service.config;

import com.jtradebot.processor.repository.CategoryScoringRepository;
import com.jtradebot.processor.repository.document.CategoryScoring;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigCategoryScoringService {
    
    private final CategoryScoringRepository categoryScoringRepository;
    
    // Cache for CategoryScoring to avoid repeated DB queries
    private final Map<String, CategoryScoring> categoryScoringCache = new ConcurrentHashMap<>();
    private volatile long lastCacheRefreshTime = 0;
    private static final long CACHE_DURATION_MS = 60000; // 1 minute (can be configured)
    
    public List<CategoryScoring> getAllCategoryScorings() {
        // Check if cache needs refresh
        long currentTime = System.currentTimeMillis();
        if ((currentTime - lastCacheRefreshTime) > CACHE_DURATION_MS || categoryScoringCache.isEmpty()) {
            refreshCache();
        }
        
        // Return all values from cache
        log.debug("✅ CACHE HIT - getAllCategoryScorings | Cache age: {}ms | Count: {}", 
                (currentTime - lastCacheRefreshTime), categoryScoringCache.size());
        
        return new java.util.ArrayList<>(categoryScoringCache.values());
    }
    
    public List<CategoryScoring> getCategoryScoringsByType(String categoryType) {
        long startTime = System.currentTimeMillis();
        log.debug("🔍 DB QUERY TRIGGERED - findByCategoryType | Type: {} | Thread: {} | Timestamp: {}", 
                categoryType, Thread.currentThread().getName(), System.currentTimeMillis());
        
        List<CategoryScoring> result = categoryScoringRepository.findByCategoryType(categoryType);
        
        long duration = System.currentTimeMillis() - startTime;
        log.debug("✅ DB QUERY COMPLETED - findByCategoryType | Type: {} | Duration: {}ms | Count: {}", 
                categoryType, duration, result.size());
        
        return result;
    }
    
    public Optional<CategoryScoring> getCategoryScoring(String categoryType, String categoryName) {
        // Check if cache needs refresh
        long currentTime = System.currentTimeMillis();
        if ((currentTime - lastCacheRefreshTime) > CACHE_DURATION_MS || categoryScoringCache.isEmpty()) {
            refreshCache();
        }
        
        // Return from cache
        String cacheKey = categoryType + ":" + categoryName;
        CategoryScoring cached = categoryScoringCache.get(cacheKey);
        
        if (cached != null) {
            log.debug("✅ CACHE HIT - CategoryScoring | Type: {}, Name: {} | Cache age: {}ms", 
                    categoryType, categoryName, (currentTime - lastCacheRefreshTime));
            return Optional.of(cached);
        } else {
            log.debug("❌ CACHE MISS - CategoryScoring | Type: {}, Name: {} | Not found in cache", 
                    categoryType, categoryName);
            return Optional.empty();
        }
    }
    
    /**
     * Refresh cache from database
     * Loads all active category scorings into cache
     */
    private synchronized void refreshCache() {
        // Double-check if another thread already refreshed
        long currentTime = System.currentTimeMillis();
        if ((currentTime - lastCacheRefreshTime) <= CACHE_DURATION_MS && !categoryScoringCache.isEmpty()) {
            return; // Already refreshed by another thread
        }
        
        long startTime = System.currentTimeMillis();
        log.debug("🔍 DB QUERY TRIGGERED (CACHE REFRESH) - findByActiveTrue | Thread: {} | Timestamp: {}", 
                Thread.currentThread().getName(), System.currentTimeMillis());
        
        try {
            List<CategoryScoring> allActive = categoryScoringRepository.findByActiveTrue();
            
            // Clear and rebuild cache
            categoryScoringCache.clear();
            for (CategoryScoring cs : allActive) {
                String key = cs.getCategoryType() + ":" + cs.getCategoryName();
                categoryScoringCache.put(key, cs);
            }
            
            lastCacheRefreshTime = System.currentTimeMillis();
            long duration = System.currentTimeMillis() - startTime;
            
            log.debug("✅ CACHE REFRESHED - CategoryScoring | Duration: {}ms | Cached entries: {} | Valid for: {}ms", 
                    duration, categoryScoringCache.size(), CACHE_DURATION_MS);
            
        } catch (Exception e) {
            log.error("❌ CACHE REFRESH FAILED - CategoryScoring | Error: {}", e.getMessage(), e);
            // Keep old cache on error
        }
    }
    
    /**
     * Manually invalidate cache
     * Call this after any CRUD operations on CategoryScoring
     */
    public void invalidateCache() {
        log.info("🔄 CACHE INVALIDATED MANUALLY - CategoryScoring");
        categoryScoringCache.clear();
        lastCacheRefreshTime = 0;
        // Next getCategoryScoring() call will trigger refresh
    }
    
    public CategoryScoring createCategoryScoring(CategoryScoring categoryScoring) {
        if (categoryScoringRepository.existsByCategoryTypeAndCategoryName(
                categoryScoring.getCategoryType(), categoryScoring.getCategoryName())) {
            throw new IllegalArgumentException("Category scoring with type '" + categoryScoring.getCategoryType() + 
                    "' and name '" + categoryScoring.getCategoryName() + "' already exists");
        }
        
        categoryScoring.setCreatedAt(LocalDateTime.now());
        categoryScoring.setUpdatedAt(LocalDateTime.now());
        categoryScoring.setActive(true);
        categoryScoring.setVersion(1);
        
        CategoryScoring saved = categoryScoringRepository.save(categoryScoring);
        log.info("Created new category scoring: {} - {}", saved.getCategoryType(), saved.getCategoryName());
        
        // Invalidate cache after create
        invalidateCache();
        
        return saved;
    }
    
    public CategoryScoring updateMultipleIndicatorValues(String categoryType, String categoryName, Map<String, Double> indicatorUpdates) {
        log.debug("🔍 DB QUERY TRIGGERED (UPDATE) - findByCategoryTypeAndCategoryName | Type: {}, Name: {} | Thread: {}", 
                categoryType, categoryName, Thread.currentThread().getName());
        
        CategoryScoring existing = categoryScoringRepository.findByCategoryTypeAndCategoryName(categoryType, categoryName)
                .orElseThrow(() -> new IllegalArgumentException("Category scoring with type '" + categoryType + 
                        "' and name '" + categoryName + "' not found"));
        
        Map<String, Double> indicators = existing.getIndicators();
        if (indicators == null) {
            throw new IllegalArgumentException("No indicators found for category scoring");
        }
        
        // Validate that all indicators exist before updating
        for (String indicatorName : indicatorUpdates.keySet()) {
            if (!indicators.containsKey(indicatorName)) {
                throw new IllegalArgumentException("Indicator '" + indicatorName + "' not found in category scoring");
            }
        }
        
        // Update all indicators
        indicators.putAll(indicatorUpdates);
        existing.setIndicators(indicators);
        existing.setUpdatedAt(LocalDateTime.now());
        existing.setVersion(existing.getVersion() + 1);
        
        CategoryScoring saved = categoryScoringRepository.save(existing);
        log.info("Updated {} indicators in category scoring: {} - {}", 
                indicatorUpdates.size(), saved.getCategoryType(), saved.getCategoryName());
        
        // Invalidate cache after update
        invalidateCache();
        
        return saved;
    }
    
    public void deleteCategoryScoring(String categoryType, String categoryName) {
        if (!categoryScoringRepository.existsByCategoryTypeAndCategoryName(categoryType, categoryName)) {
            throw new IllegalArgumentException("Category scoring with type '" + categoryType + 
                    "' and name '" + categoryName + "' not found");
        }
        
        categoryScoringRepository.deleteByCategoryTypeAndCategoryName(categoryType, categoryName);
        log.info("Deleted category scoring: {} - {}", categoryType, categoryName);
        
        // Invalidate cache after delete
        invalidateCache();
    }
    
    public CategoryScoring deactivateCategoryScoring(String categoryType, String categoryName) {
        log.debug("🔍 DB QUERY TRIGGERED (DEACTIVATE) - findByCategoryTypeAndCategoryName | Type: {}, Name: {} | Thread: {}", 
                categoryType, categoryName, Thread.currentThread().getName());
        
        CategoryScoring categoryScoring = categoryScoringRepository.findByCategoryTypeAndCategoryName(categoryType, categoryName)
                .orElseThrow(() -> new IllegalArgumentException("Category scoring with type '" + categoryType + 
                        "' and name '" + categoryName + "' not found"));
        
        categoryScoring.setActive(false);
        categoryScoring.setUpdatedAt(LocalDateTime.now());
        
        CategoryScoring saved = categoryScoringRepository.save(categoryScoring);
        log.info("Deactivated category scoring: {} - {}", saved.getCategoryType(), saved.getCategoryName());
        
        // Invalidate cache after deactivate
        invalidateCache();
        
        return saved;
    }
}
