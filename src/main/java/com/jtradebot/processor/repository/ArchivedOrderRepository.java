package com.jtradebot.processor.repository;

import com.jtradebot.processor.repository.document.ArchivedOrder;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArchivedOrderRepository extends MongoRepository<ArchivedOrder, String> {
    
    @Query("{'status': 'ACTIVE'}")
    List<ArchivedOrder> findAllActiveOrders();

    @Query("{'status': ?0}")
    List<ArchivedOrder> findByStatus(String status);

}



