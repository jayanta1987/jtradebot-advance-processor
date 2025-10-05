package com.jtradebot.processor.repository;

import com.jtradebot.processor.repository.document.TradingScenario;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface TradingScenarioRepository extends MongoRepository<TradingScenario, String> {
    
    Optional<TradingScenario> findByName(String name);
    
    List<TradingScenario> findByActiveTrue();
    
    List<TradingScenario> findByActive(Boolean active);
    
    void deleteByName(String name);
    
    boolean existsByName(String name);
    
    @Query("{'name': ?0}")
    @Update("{'$set': {'active': ?1, 'updatedAt': ?2}}")
    void updateActiveStatus(String name, Boolean active, LocalDateTime updatedAt);
    
    @Query("{'name': ?0}")
    @Update("{'$set': ?1, '$inc': {'version': 1}}")
    void updateScenarioFields(String name, Map<String, Object> fieldUpdates);
}
