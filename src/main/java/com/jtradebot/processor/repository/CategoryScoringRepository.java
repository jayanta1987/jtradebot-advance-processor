package com.jtradebot.processor.repository;

import com.jtradebot.processor.repository.document.CategoryScoring;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryScoringRepository extends MongoRepository<CategoryScoring, String> {
    
    Optional<CategoryScoring> findByCategoryTypeAndCategoryName(String categoryType, String categoryName);
    
    List<CategoryScoring> findByCategoryType(String categoryType);
    
    List<CategoryScoring> findByActiveTrue();
    
    List<CategoryScoring> findByActive(Boolean active);
    
    void deleteByCategoryTypeAndCategoryName(String categoryType, String categoryName);
    
    boolean existsByCategoryTypeAndCategoryName(String categoryType, String categoryName);
}
