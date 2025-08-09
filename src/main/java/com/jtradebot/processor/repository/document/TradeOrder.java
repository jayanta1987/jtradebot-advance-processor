package com.jtradebot.processor.repository.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.model.enums.TradeMode;
import com.jtradebot.processor.repository.Transaction;
import lombok.Data;
import lombok.ToString;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.util.Collections;
import java.util.Date;
import java.util.*;
import java.util.Set;

@Document(collection = "order")
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@ToString
public class TradeOrder {

    @MongoId
    private String id;

    private OrderTypeEnum orderType;

    private Double totalPoints;
    private Date startTime;
    private Date endTime;

    // TODO: EntryRule removed from old CP-based system
    private TradeMode tradeMode;
    private double entryIndexPrice;
    private double exitIndexPrice;
    private double maxIndexPrice;
    private double minIndexPrice;

    private String tradingSymbol;
    private Long instrumentToken;
    private int maxAllowedQuantity;

    private int indexStopLoss;
    private int indexTarget;

    private int indexTarget2;
    private int indexTarget3;
    private Ntp ntp;
    private OrderInfo orderInfo;

    @Indexed
    private String status;

    @Indexed
    private String refOrderId;

    private boolean isBadEntry;

    private Transaction transaction;

    private Set<Double> milestonesSL = Collections.emptySet();
    private int maxMileStoneSL = 0;
    private int lastStopLossMilestoneReached = 0;

    private Set<Double> milestonesTG = Collections.emptySet();
    private int maxMileStoneTG = 0;
    private int lastTargetMilestoneReached = 0;

    private double totalProfit;
    private double totalReleasedProfit;
    private List<String> comments = new ArrayList<>();

    public double getIndexTargetDistance() {
        return Math.abs(this.indexTarget - this.entryIndexPrice);
    }

    public double getIndexStopLossDistance() {
        return Math.abs(this.entryIndexPrice - this.indexStopLoss);
    }

    public void addComment(String comment) {
        if (this.comments != null) {
            this.comments.add(comment);
        }
    }

}
