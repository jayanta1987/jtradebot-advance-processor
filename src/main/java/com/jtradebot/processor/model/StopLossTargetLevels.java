package com.jtradebot.processor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StopLossTargetLevels {
    private double entryPrice;
    private double stopLossPrice;
    private double targetPrice;
    private double stopLossPoints;
    private double targetPoints;
    private double riskRewardRatio;
    private String strategyType; // CALL or PUT
}
