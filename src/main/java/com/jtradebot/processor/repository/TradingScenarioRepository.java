package com.jtradebot.processor.repository;

import com.jtradebot.processor.repository.document.TradingScenario;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TradingScenarioRepository extends MongoRepository<TradingScenario, String> {
    
    Optional<TradingScenario> findByName(String name);
    
    List<TradingScenario> findByActiveTrue();
    
    List<TradingScenario> findByActive(Boolean active);
    
    void deleteByName(String name);
    
    boolean existsByName(String name);
}
