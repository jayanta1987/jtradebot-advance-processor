package com.jtradebot.processor.trend;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Deque;
import java.util.LinkedList;

@Component
@Setter
@Getter
@Slf4j
public class LongTrend {

    private double previousAvg = 0;
    private double confirmationPoint = 0;
    private static final int MAX_SIZE = 20;
    private Deque<Double> confirmationPointDeque;


    public LongTrend() {
        this.confirmationPointDeque = new LinkedList<>();
    }

    public synchronized void addTrendingPoint(double point) {
        // Update previous averages
        previousAvg = getAverageShortTrendingPoint();
        addShortTP(point);
    }

    private void addShortTP(double point) {
        if (confirmationPointDeque.size() == MAX_SIZE) {
            confirmationPointDeque.removeFirst();
        }
        confirmationPointDeque.addLast(point);
        confirmationPoint = 0;
        for (Double tp : confirmationPointDeque) {
            if (tp == null) {
                log.error("Null value found in trending points");
                continue;
            }
            confirmationPoint += tp;
        }
    }

    public double getAverageShortTrendingPoint() {
        if (confirmationPointDeque.isEmpty()) {
            return 0;
        }
        return confirmationPoint / confirmationPointDeque.size();
    }

    public void reset() {
        confirmationPointDeque.clear();
        confirmationPoint = 0;
        previousAvg = 0;
    }

    /*public synchronized TrendDirection isTrendReversed() {
        if (confirmationPointDeque.size() < MAX_SIZE) {
            return TrendDirection.NO_REVERSAL;
        }

        double currentAvg = getAverageShortTrendingPoint();

        // Check for trend reversal
        if (previousAvg < 0 && currentAvg > 0) {
            previousAvg = currentAvg;
            return TrendDirection.PUT_TO_CALL_REVERSED;
        } else if (previousAvg > 0 && currentAvg < 0) {
            previousAvg = currentAvg;
            return TrendDirection.CALL_TO_PUT_REVERSED;
        }

        previousAvg = currentAvg;
        return TrendDirection.NO_REVERSAL;
    }*/

}
