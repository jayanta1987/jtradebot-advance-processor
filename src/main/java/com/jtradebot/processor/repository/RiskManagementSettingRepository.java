package com.jtradebot.processor.repository;

import com.jtradebot.processor.repository.document.RiskManagementSetting;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RiskManagementSettingRepository extends MongoRepository<RiskManagementSetting, String> {
    
    Optional<RiskManagementSetting> findByActiveTrue();
    
    List<RiskManagementSetting> findByActive(Boolean active);
    
    Optional<RiskManagementSetting> findByIdAndActiveTrue(String id);
}
