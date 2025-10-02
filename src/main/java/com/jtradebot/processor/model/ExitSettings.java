package com.jtradebot.processor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExitSettings {

    // Milestone Based Exit Configuration
    private boolean milestoneBasedExitEnabled;
    private String milestoneBasedExitDescription;

    // Price Movement Pattern Based Exit Configuration
    private boolean priceMovementExitEnabled;
    private String priceMovementExitDescription;

    // Additional Exit Settings
    private boolean timeBasedExitEnabled;
    private boolean strategyBasedExitEnabled;
    private boolean stopLossTargetExitEnabled;

    // Configuration Metadata
    private String lastModifiedBy;
    private String lastModifiedAt;

    // Default constructor
    public ExitSettings() {
        this.milestoneBasedExitEnabled = true;
        this.priceMovementExitEnabled = true;
        this.timeBasedExitEnabled = true;
        this.strategyBasedExitEnabled = true;
        this.stopLossTargetExitEnabled = true;
        this.milestoneBasedExitDescription = "Exit based on milestone system (trailing stop loss and target milestones)";
        this.priceMovementExitDescription = "Exit based on price movement patterns (reversal and neutral signals)";
        this.lastModifiedBy = "SYSTEM";
        this.lastModifiedAt = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))
            .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss"));
    }

    // Helper method to update last modified time
    public void updateLastModified() {
        this.lastModifiedAt = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))
            .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss"));
    }
}
