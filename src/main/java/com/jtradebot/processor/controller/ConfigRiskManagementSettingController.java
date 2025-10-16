package com.jtradebot.processor.controller;

import com.jtradebot.processor.repository.document.RiskManagementSetting;
import com.jtradebot.processor.service.config.ConfigRiskManagementSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/config/risk-management-setting")
@CrossOrigin(origins = {"http://localhost:5173", "https://jtradebot.com", "https://www.jtradebot.com"})
@RequiredArgsConstructor
@Slf4j
public class ConfigRiskManagementSettingController {
    
    private final ConfigRiskManagementSettingService configRiskManagementSettingService;
    
    @GetMapping
    public ResponseEntity<RiskManagementSetting> getRiskManagementSetting() {
        try {
            Optional<RiskManagementSetting> setting = configRiskManagementSettingService.getActiveRiskManagementSetting();
            return setting.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error retrieving risk management setting", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PatchMapping
    public ResponseEntity<RiskManagementSetting> updateRiskManagementSetting(@RequestBody RiskManagementSetting updatedSetting) {
        try {
            RiskManagementSetting updated = configRiskManagementSettingService.updateRiskManagementSetting(updatedSetting);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid risk management setting update request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error updating risk management setting", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
