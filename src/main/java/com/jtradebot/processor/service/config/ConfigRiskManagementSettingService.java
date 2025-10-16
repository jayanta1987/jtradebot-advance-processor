package com.jtradebot.processor.service.config;

import com.jtradebot.processor.repository.RiskManagementSettingRepository;
import com.jtradebot.processor.repository.document.RiskManagementSetting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigRiskManagementSettingService {
    
    private final RiskManagementSettingRepository riskManagementSettingRepository;
    
    public Optional<RiskManagementSetting> getActiveRiskManagementSetting() {
        return riskManagementSettingRepository.findByActiveTrue();
    }
    
    public RiskManagementSetting updateRiskManagementSetting(RiskManagementSetting updatedSetting) {
        RiskManagementSetting existing = riskManagementSettingRepository.findByActiveTrue()
                .orElseThrow(() -> new IllegalArgumentException("No active risk management setting found"));
        
        // Update only the provided fields
        if (updatedSetting.getMinMilestonePoints() != null) {
            existing.setMinMilestonePoints(updatedSetting.getMinMilestonePoints());
        }
        if (updatedSetting.getMaxMilestonePoints() != null) {
            existing.setMaxMilestonePoints(updatedSetting.getMaxMilestonePoints());
        }
        if (updatedSetting.getVolumeSurgeMultiplierMin() != null) {
            existing.setVolumeSurgeMultiplierMin(updatedSetting.getVolumeSurgeMultiplierMin());
        }
        if (updatedSetting.getStopLossPercentage() != null) {
            existing.setStopLossPercentage(updatedSetting.getStopLossPercentage());
        }
        if (updatedSetting.getTargetPercentage() != null) {
            existing.setTargetPercentage(updatedSetting.getTargetPercentage());
        }
        if (updatedSetting.getRsiMaPeriod() != null) {
            existing.setRsiMaPeriod(updatedSetting.getRsiMaPeriod());
        }
        if (updatedSetting.getEnableRsiMaComparison() != null) {
            existing.setEnableRsiMaComparison(updatedSetting.getEnableRsiMaComparison());
        }
        if (updatedSetting.getCallExitThreshold() != null) {
            existing.setCallExitThreshold(updatedSetting.getCallExitThreshold());
        }
        if (updatedSetting.getPutExitThreshold() != null) {
            existing.setPutExitThreshold(updatedSetting.getPutExitThreshold());
        }
        if (updatedSetting.getRsiDivergenceExitEnabled() != null) {
            existing.setRsiDivergenceExitEnabled(updatedSetting.getRsiDivergenceExitEnabled());
        }
        if (updatedSetting.getMarketConditionExitEnabled() != null) {
            existing.setMarketConditionExitEnabled(updatedSetting.getMarketConditionExitEnabled());
        }
        if (updatedSetting.getMarketEndSchedulerEnabled() != null) {
            existing.setMarketEndSchedulerEnabled(updatedSetting.getMarketEndSchedulerEnabled());
        }
        if (updatedSetting.getComments() != null) {
            existing.setComments(updatedSetting.getComments());
        }
        
        existing.setUpdatedAt(LocalDateTime.now());
        existing.setVersion(existing.getVersion() + 1);
        
        RiskManagementSetting saved = riskManagementSettingRepository.save(existing);
        log.info("Updated risk management setting: {}", saved.getId());
        return saved;
    }
}
