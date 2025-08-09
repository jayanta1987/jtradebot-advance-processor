package com.jtradebot.processor.repository;

import com.jtradebot.processor.repository.document.TradeConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.time.LocalDate;
import java.util.Optional;

public interface TradeConfigRepository extends MongoRepository<TradeConfig, String> {
    Optional<TradeConfig> findByDate(String date);
    
    void deleteByDate(String date);
}
