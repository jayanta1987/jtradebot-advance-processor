package com.jtradebot.processor.controller;

import com.jtradebot.processor.repository.document.CategoryScoring;
import com.jtradebot.processor.service.config.ConfigCategoryScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/config/category-scoring")
@CrossOrigin(origins = {"http://localhost:5173", "https://jtradebot.com", "https://www.jtradebot.com"})
@RequiredArgsConstructor
@Slf4j
public class ConfigCategoryScoringController {
    
    private final ConfigCategoryScoringService configCategoryScoringService;
    
    @GetMapping
    public ResponseEntity<List<CategoryScoring>> getAllCategoryScorings() {
        try {
            List<CategoryScoring> categoryScorings = configCategoryScoringService.getAllCategoryScorings();
            return ResponseEntity.ok(categoryScorings);
        } catch (Exception e) {
            log.error("Error retrieving category scorings", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/type/{categoryType}")
    public ResponseEntity<List<CategoryScoring>> getCategoryScoringsByType(@PathVariable String categoryType) {
        try {
            List<CategoryScoring> categoryScorings = configCategoryScoringService.getCategoryScoringsByType(categoryType);
            return ResponseEntity.ok(categoryScorings);
        } catch (Exception e) {
            log.error("Error retrieving category scorings for type: {}", categoryType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{categoryType}/{categoryName}")
    public ResponseEntity<CategoryScoring> getCategoryScoring(@PathVariable String categoryType, @PathVariable String categoryName) {
        try {
            Optional<CategoryScoring> categoryScoring = configCategoryScoringService.getCategoryScoring(categoryType, categoryName);
            return categoryScoring.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error retrieving category scoring: {} - {}", categoryType, categoryName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping
    public ResponseEntity<CategoryScoring> createCategoryScoring(@RequestBody CategoryScoring categoryScoring) {
        try {
            CategoryScoring created = configCategoryScoringService.createCategoryScoring(categoryScoring);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid category scoring creation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error creating category scoring", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PatchMapping("/{categoryType}/{categoryName}")
    public ResponseEntity<CategoryScoring> updateMultipleIndicatorValues(@PathVariable String categoryType,
                                                                        @PathVariable String categoryName,
                                                                        @RequestBody Map<String, Double> indicatorUpdates) {
        try {
            if (indicatorUpdates == null || indicatorUpdates.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            CategoryScoring updated = configCategoryScoringService.updateMultipleIndicatorValues(categoryType, categoryName, indicatorUpdates);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid multiple indicator update request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error updating multiple indicators: {} - {}", categoryType, categoryName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @DeleteMapping("/{categoryType}/{categoryName}")
    public ResponseEntity<Void> deleteCategoryScoring(@PathVariable String categoryType, @PathVariable String categoryName) {
        try {
            configCategoryScoringService.deleteCategoryScoring(categoryType, categoryName);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid category scoring deletion request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error deleting category scoring: {} - {}", categoryType, categoryName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PatchMapping("/{categoryType}/{categoryName}/deactivate")
    public ResponseEntity<CategoryScoring> deactivateCategoryScoring(@PathVariable String categoryType, @PathVariable String categoryName) {
        try {
            CategoryScoring deactivated = configCategoryScoringService.deactivateCategoryScoring(categoryType, categoryName);
            return ResponseEntity.ok(deactivated);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid category scoring deactivation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error deactivating category scoring: {} - {}", categoryType, categoryName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
