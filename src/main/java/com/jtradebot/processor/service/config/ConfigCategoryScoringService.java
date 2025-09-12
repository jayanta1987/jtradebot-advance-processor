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

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigCategoryScoringService {
    
    private final CategoryScoringRepository categoryScoringRepository;
    
    public List<CategoryScoring> getAllCategoryScorings() {
        return categoryScoringRepository.findByActiveTrue();
    }
    
    public List<CategoryScoring> getCategoryScoringsByType(String categoryType) {
        return categoryScoringRepository.findByCategoryType(categoryType);
    }
    
    public Optional<CategoryScoring> getCategoryScoring(String categoryType, String categoryName) {
        return categoryScoringRepository.findByCategoryTypeAndCategoryName(categoryType, categoryName);
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
        return saved;
    }
    
    public CategoryScoring updateMultipleIndicatorValues(String categoryType, String categoryName, Map<String, Double> indicatorUpdates) {
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
        return saved;
    }
    
    public void deleteCategoryScoring(String categoryType, String categoryName) {
        if (!categoryScoringRepository.existsByCategoryTypeAndCategoryName(categoryType, categoryName)) {
            throw new IllegalArgumentException("Category scoring with type '" + categoryType + 
                    "' and name '" + categoryName + "' not found");
        }
        
        categoryScoringRepository.deleteByCategoryTypeAndCategoryName(categoryType, categoryName);
        log.info("Deleted category scoring: {} - {}", categoryType, categoryName);
    }
    
    public CategoryScoring deactivateCategoryScoring(String categoryType, String categoryName) {
        CategoryScoring categoryScoring = categoryScoringRepository.findByCategoryTypeAndCategoryName(categoryType, categoryName)
                .orElseThrow(() -> new IllegalArgumentException("Category scoring with type '" + categoryType + 
                        "' and name '" + categoryName + "' not found"));
        
        categoryScoring.setActive(false);
        categoryScoring.setUpdatedAt(LocalDateTime.now());
        
        CategoryScoring saved = categoryScoringRepository.save(categoryScoring);
        log.info("Deactivated category scoring: {} - {}", saved.getCategoryType(), saved.getCategoryName());
        return saved;
    }
}
