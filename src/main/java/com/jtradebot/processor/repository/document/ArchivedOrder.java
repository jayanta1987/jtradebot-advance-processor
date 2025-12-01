package com.jtradebot.processor.repository.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.model.enums.ExitReasonEnum;
import com.jtradebot.processor.model.MilestoneSystem.Milestone;
import com.jtradebot.processor.model.strategy.DetailedCategoryScore;
import com.jtradebot.processor.model.indicator.Support;
import com.jtradebot.processor.model.indicator.Resistance;
import com.jtradebot.processor.model.NtpDetails;

import lombok.Data;
import lombok.ToString;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Document(collection = "archived_orders")
@CompoundIndex(name = "status_orderType_idx", def = "{'status': 1, 'orderType': 1}")
@CompoundIndex(name = "status_tradingSymbol_idx", def = "{'status': 1, 'tradingSymbol': 1}")
@CompoundIndex(name = "entryTime_status_idx", def = "{'entryTime': 1, 'status': 1}")
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@ToString
public class ArchivedOrder {

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

    private String kiteOrderId;
    private String kiteOrderStatus; // "OPEN", "COMPLETE", "CANCELLED", etc.
    
    @Indexed
    private String status; // "ACTIVE", "CLOSED", "CANCELLED"
    
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
    private Map<String, Object> entryMarketConditionDetails; // Structured market condition details including ATR values: atr5min, atr15min, atr1min
    
    // Scenario-based Entry Information
    private String entryScenarioName;
    private String entryScenarioDescription;
    private Double entryScenarioConfidence;
    private Map<String, Double> entryCategoryScores;
    private Map<String, List<String>> entryMatchedConditions;
    
    // 🔥 NEW: Quality Score and Direction Scores at Entry Time
    private Double entryQualityScore;
    private String entryDominantTrend;
    
    // 🔥 NEW: Detailed Category Scores with Individual Indicator Breakdowns
    private Map<String, DetailedCategoryScore> entryDetailedCallScores;
    private Map<String, DetailedCategoryScore> entryDetailedPutScores;
    
    // Filter Failure Tracking
    private Integer mandatoryFiltersFailed;
    private Integer optionalFiltersFailed;
    private Integer totalFiltersChecked;
    private List<String> filterFailureReason; // List of failed filter names with NTP values
    
    // 🔥 NEW: NTP Details at Entry Time (comprehensive NTP tracking)
    private NtpDetails entryNtpDetails;         // Complete NTP filter results and details at entry time
    
    // Support and Resistance Data at Entry Time
    private Set<Support> entrySupports; // All support levels found at entry time
    private Set<Resistance> entryResistances; // All resistance levels found at entry time
    private Double entryNearestSupport; // Nearest support level at entry time
    private Double entryNearestResistance; // Nearest resistance level at entry time
    
    // Greeks Data at Entry Time
    private Double entryDelta; // Delta value at entry time
    private Double entryGamma; // Gamma value at entry time
    private Double entryTheta; // Theta value at entry time
    private Double entryVega; // Vega value at entry time
    private Double entryImpliedVolatility; // Implied volatility at entry time
    private Double entryTimeToExpiry; // Time to expiry in days at entry time
    private Integer entryStrikePrice; // Strike price of the option
    private String entryOptionType; // CE or PE
    
    // Archive metadata
    private String archivedAt; // Timestamp when order was archived
    
    // Helper methods
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
    
    public boolean isClosed() {
        return "CLOSED".equals(status);
    }
    
    public void markClosed(ExitReasonEnum reason, Double exitPrice, Double exitIndexPrice, Date exitTime) {
        this.status = "CLOSED";
        this.exitReason = reason;
        this.exitPrice = exitPrice;
        this.exitIndexPrice = exitIndexPrice;
        this.exitTime = formatDateToIST(exitTime);
        this.lastUpdated = getCurrentISTTime();
    }
    
    // Overloaded method for String exit time
    public void markClosed(ExitReasonEnum reason, Double exitPrice, Double exitIndexPrice, String exitTime) {
        this.status = "CLOSED";
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

