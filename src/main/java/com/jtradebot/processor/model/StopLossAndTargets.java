package com.jtradebot.processor.model;

import lombok.Data;

import java.util.Set;

@Data
public class StopLossAndTargets {
    private boolean isValid;
    private double stopLoss;
    private double target1;
    private double target2;
    private double target3;
    private double target4;
    private int quantity;
    private Set<Double> fibLevels;

    public void reset() {
        this.isValid = false;
        this.stopLoss = 0.0;
        this.target1 = 0.0;
        this.target2 = 0.0;
        this.target3 = 0.0;
        this.target4 = 0.0;
        this.quantity = 0;
        this.fibLevels = null;
    }
}
