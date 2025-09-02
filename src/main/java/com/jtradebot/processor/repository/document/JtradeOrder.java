package com.jtradebot.processor.repository.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.model.enums.ExitReasonEnum;
import com.jtradebot.processor.model.MilestoneSystem.Milestone;

import lombok.Data;
import lombok.ToString;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Document(collection = "jtrade_orders")
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@ToString
public class JtradeOrder {

    @MongoId
    private String id;

    private OrderTypeEnum orderType;
    
    private String tradingSymbol;
    private Long instrumentToken;
    
    private String entryTime;
    private String exitTime;
    
    private Double entryPrice;
    private Double exitPrice;
    
    private Double entryIndexPrice;
    private Double exitIndexPrice;
    
    private Double stopLossPrice;
    private Double targetPrice;
    
    private Integer quantity;
    
    @Indexed
    private String status; // "ACTIVE", "EXITED", "CANCELLED"
    
    private ExitReasonEnum exitReason;
    
    private Double totalPoints;
    private Double totalProfit;
    
    private String lastUpdated;
    private String createdAt;
    
    private String comments;
    
    // Milestone System - Simplified (only target milestones and history)
    private List<Milestone> targetMilestones;
    private List<String> milestoneHistory;
    
    // Index Price Tracking
    private Double minIndexPrice;
    private Double maxIndexPrice;
    
    // Entry Conditions - Store all conditions that were matched when order was created
    private List<String> entryConditions;
    
    // Market Condition Details at Entry Time
    private Boolean entryMarketConditionSuitable;
    private Map<String, Object> entryMarketConditionDetails; // Structured market condition details
    
    // Scenario-based Entry Information
    private String entryScenarioName;
    private String entryScenarioDescription;
    private Double entryScenarioConfidence;
    private Map<String, Integer> entryCategoryScores;
    private Map<String, List<String>> entryMatchedConditions;
    
    // 🔥 NEW: Quality Score and Direction Scores at Entry Time
    private Double entryQualityScore;
    private Map<String, Integer> entryCallScores;
    private Map<String, Integer> entryPutScores;
    private String entryDominantTrend;
    
    // 🔥 NEW: Detailed Category Scores with Individual Indicator Breakdowns
    private Map<String, Object> entryDetailedCallScores;
    private Map<String, Object> entryDetailedPutScores;
    
    // Filter Failure Tracking
    private Integer mandatoryFiltersFailed;
    private Integer optionalFiltersFailed;
    private Integer totalFiltersChecked;
    private List<String> filterFailureReason; // List of failed filter names with NTP values
    
    // Helper methods
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
    
    public boolean isExited() {
        return "EXITED".equals(status);
    }
    
    public void markExited(ExitReasonEnum reason, Double exitPrice, Double exitIndexPrice, Date exitTime) {
        this.status = "EXITED";
        this.exitReason = reason;
        this.exitPrice = exitPrice;
        this.exitIndexPrice = exitIndexPrice;
        this.exitTime = formatDateToIST(exitTime);
        this.lastUpdated = getCurrentISTTime();
    }
    
    // Overloaded method for String exit time
    public void markExited(ExitReasonEnum reason, Double exitPrice, Double exitIndexPrice, String exitTime) {
        this.status = "EXITED";
        this.exitReason = reason;
        this.exitPrice = exitPrice;
        this.exitIndexPrice = exitIndexPrice;
        this.exitTime = exitTime;
        this.lastUpdated = getCurrentISTTime();
    }
    
    public void updateLastUpdated() {
        this.lastUpdated = getCurrentISTTime();
    }
    
    // Utility methods for IST time formatting
    private String formatDateToIST(Date date) {
        if (date == null) return null;
        ZonedDateTime istTime = date.toInstant().atZone(ZoneId.of("Asia/Kolkata"));
        return istTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss z"));
    }
    
    private String getCurrentISTTime() {
        ZonedDateTime istTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        return istTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss z"));
    }
}
