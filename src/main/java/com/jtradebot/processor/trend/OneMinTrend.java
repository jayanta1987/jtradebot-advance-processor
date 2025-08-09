package com.jtradebot.processor.trend;

import com.jtradebot.processor.model.enums.TrendEnum;
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
public class OneMinTrend {

    private double previousShortAvg = 0;
    private double shortTrendingPoint = 0;
    private static final int SHORT_MAX_SIZE = 20;
    private Deque<Double> shortTrendingPointsDeque;

    private double previousMidAvg = 0;
    private double midTrendingPoint = 0;
    private static final int MID_MAX_SIZE = 1000;
    private Deque<Double> midTrendingPointsDeque;


    public OneMinTrend() {
        this.shortTrendingPointsDeque = new LinkedList<>();
        this.midTrendingPointsDeque = new LinkedList<>();
    }

    public synchronized void addTrendingPoint(double point) {
        // Update previous averages
        previousShortAvg = getAverageShortTrendingPoint();
        previousMidAvg = getAverageMidTrendingPoint();

        addShortTP(point);
        addMidTP(point);
    }

    private void addShortTP(double point) {
        if (shortTrendingPointsDeque.size() == SHORT_MAX_SIZE) {
            shortTrendingPointsDeque.removeFirst();
        }
        shortTrendingPointsDeque.addLast(point);
        shortTrendingPoint = 0;
        for (Double tp : shortTrendingPointsDeque) {
            if (tp == null) {
                log.error("Null value found in trending points");
                continue;
            }
            shortTrendingPoint += tp;
        }
    }

    private void addMidTP(double point) {
        if (midTrendingPointsDeque.size() == MID_MAX_SIZE) {
            midTrendingPointsDeque.removeFirst();
        }
        midTrendingPointsDeque.addLast(point);
        midTrendingPoint = 0;
        for (Double tp : midTrendingPointsDeque) {
            if (tp == null) {
                log.error("Null value found in trending points");
                continue;
            }
            midTrendingPoint += tp;
        }
    }

    public double getAverageShortTrendingPoint() {
        if (shortTrendingPointsDeque.isEmpty()) {
            return 0;
        }
        return shortTrendingPoint / shortTrendingPointsDeque.size();
    }

    public double getAverageMidTrendingPoint() {
        if (midTrendingPointsDeque.isEmpty()) {
            return 0;
        }
        return midTrendingPoint / midTrendingPointsDeque.size();
    }

    public double getLastTrendingPoint() {
        return shortTrendingPointsDeque.getLast();
    }

    public TrendEnum getTrend() {
        double cp = getLastTrendingPoint();
        if (cp > 0) {
            return TrendEnum.UP;
        } else if (cp < 0) {
            return TrendEnum.DOWN;
        } else {
            return TrendEnum.SIDEWAYS;
        }
    }

    public void reset() {
        shortTrendingPointsDeque.clear();
        midTrendingPointsDeque.clear();
        shortTrendingPoint = 0;
        midTrendingPoint = 0;
        previousShortAvg = 0;
        previousMidAvg = 0;
    }
}
