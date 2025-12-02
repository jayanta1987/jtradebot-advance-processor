package com.jtradebot.processor.repository.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDateTime;

@Document(collection = "config_risk_management_setting")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RiskManagementSetting {
    
    @MongoId
    private String id;
    
    // Risk Management Settings (percentages of option entry price)
    private Double minMilestonePointPercentage;
    private Double baseMilestonePointPercentage;
    private Double volumeSurgeMultiplierMin;
    private Double stopLossPercentage;
    private Double targetPercentage;
    
    // RSI Thresholds
    private Integer rsiMaPeriod;
    private Boolean enableRsiMaComparison;
    
    // Exit Signal Configuration
    private Double callExitThreshold;
    private Double putExitThreshold;
    private Boolean rsiDivergenceExitEnabled;
    private Boolean marketConditionExitEnabled;
    
    // Market End Scheduler
    private Boolean marketEndSchedulerEnabled;
    
    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Boolean active;
    
    // Version control
    private Integer version;
    private String comments;
}
