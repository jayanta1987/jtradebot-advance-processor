package com.jtradebot.processor.repository;

import com.jtradebot.processor.repository.document.NoTradeZoneFilter;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NoTradeZoneFilterRepository extends MongoRepository<NoTradeZoneFilter, String> {
    
    Optional<NoTradeZoneFilter> findByFilterName(String filterName);
    
    List<NoTradeZoneFilter> findByActiveTrue();
    
    List<NoTradeZoneFilter> findByActive(Boolean active);
    
    List<NoTradeZoneFilter> findByEnabledTrue();
    
    void deleteByFilterName(String filterName);
    
    boolean existsByFilterName(String filterName);
}
