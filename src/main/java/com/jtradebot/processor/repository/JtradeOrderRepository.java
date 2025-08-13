package com.jtradebot.processor.repository;

import com.jtradebot.processor.repository.document.JtradeOrder;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JtradeOrderRepository extends MongoRepository<JtradeOrder, String> {
    
    @Query("{'status': 'ACTIVE'}")
    List<JtradeOrder> findAllActiveOrders();
    
    @Query("{'status': 'ACTIVE', 'orderType': ?0}")
    List<JtradeOrder> findActiveOrdersByType(String orderType);
    
    @Query("{'status': 'ACTIVE', 'tradingSymbol': ?0}")
    List<JtradeOrder> findActiveOrdersBySymbol(String tradingSymbol);
}
