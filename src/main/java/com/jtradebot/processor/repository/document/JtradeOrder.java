package com.jtradebot.processor.repository.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.model.enums.ExitReasonEnum;
import com.jtradebot.processor.model.MilestoneSystem;
import lombok.Data;
import lombok.ToString;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.util.Date;
import java.util.List;

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
    
    private Date entryTime;
    private Date exitTime;
    
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
    
    private Date lastUpdated;
    private Date createdAt;
    
    private String comments;
    
    // Milestone System
    private MilestoneSystem milestoneSystem;
    private Integer currentTargetMilestone;
    private Double totalReleasedProfit;
    private List<String> milestoneHistory;
    
    // Entry Conditions - Store all conditions that were matched when order was created
    private List<String> entryConditions;
    
    // Profitable Trade Filter Information
    private Boolean profitableTradeFilterEnabled;
    private Boolean profitableTradeFilterPassed;
    private String profitableTradeFilterRejectionReason;
    private Double profitableTradeFilterQualityScore;
    private Double profitableTradeFilterCandlestickScore;
    private Double profitableTradeFilterVolumeSurgeMultiplier;
    private Integer profitableTradeFilterOptionalConditionsCount;
    private List<String> profitableTradeFilterPassedRequiredConditions;
    private List<String> profitableTradeFilterFailedRequiredConditions;
    private List<String> profitableTradeFilterPassedOptionalConditions;
    private List<String> profitableTradeFilterFailedOptionalConditions;
    
    // Helper methods
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
    
    public boolean isExited() {
        return "EXITED".equals(status);
    }
    
    public void markExited(ExitReasonEnum reason, Double exitPrice, Double exitIndexPrice) {
        this.status = "EXITED";
        this.exitReason = reason;
        this.exitPrice = exitPrice;
        this.exitIndexPrice = exitIndexPrice;
        this.exitTime = new Date();
        this.lastUpdated = new Date();
    }
    
    public void updateLastUpdated() {
        this.lastUpdated = new Date();
    }
}
