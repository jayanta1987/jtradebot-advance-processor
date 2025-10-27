package com.jtradebot.processor.manager;

import com.jtradebot.processor.handler.DateTimeHandler;
import com.jtradebot.processor.model.indicator.BarSeriesData;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.jtradebot.processor.model.enums.KiteHistoricalDataTimeframeEnum;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.HistoricalData;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DecimalNum;

import java.io.IOException;
import java.time.*;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import static com.jtradebot.processor.handler.DateTimeHandler.getHolidaysAndWeekends;
import static com.jtradebot.processor.handler.DateTimeHandler.goBackInPast;
import static com.jtradebot.processor.model.enums.CandleTimeFrameEnum.*;
import static com.jtradebot.processor.model.enums.KiteHistoricalDataTimeframeEnum.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BarSeriesManager {

    private final ConcurrentHashMap<String, BarSeriesData> instrumentSeriesMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> avg5MinCandleHeightMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> avg3MinCandleHeightMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastVolumeMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Tick> lastTickMap = new ConcurrentHashMap<>();
    private final KiteConnect kiteConnect;

    public void initializeBarSeriesData(String instrumentToken, Date toDate) {
        log.info("Fetching historical data for instrument token: {} to: {}", instrumentToken, toDate);

        try {
            // Timeframe-specific days for historical data
            Date fromDate1Min = goBackInPast(toDate, Calendar.DAY_OF_YEAR, 7);  // 1 min: 2 days (enough for 1 min candles)
            Date fromDate3Min = goBackInPast(toDate, Calendar.DAY_OF_YEAR, 7);  // 3 min: 3 days
            Date fromDate5Min = goBackInPast(toDate, Calendar.DAY_OF_YEAR, 12); // 5 min: 10 days (keep as is)
            Date fromDate15Min = goBackInPast(toDate, Calendar.DAY_OF_YEAR, 15); // 15 min: 20 days
            Date fromDate1Hour = goBackInPast(toDate, Calendar.DAY_OF_YEAR, 20); // 1 hour: 30 days
            Date fromDate1Day = goBackInPast(toDate, Calendar.DAY_OF_YEAR, 60);  // 1 day: 60 days
            
            BarSeriesData seriesData = BarSeriesData.builder()
                    .series1Min(fetchAndConvertToBarSeries(instrumentToken, MINUTE, fromDate1Min, toDate, ONE_MIN))
                    .series3Min(fetchAndConvertToBarSeries(instrumentToken, THREE_MINUTE, fromDate3Min, toDate, THREE_MIN))
                    .series5Min(fetchAndConvertToBarSeries(instrumentToken, FIVE_MINUTE, fromDate5Min, toDate, FIVE_MIN))
                    .series15Min(fetchAndConvertToBarSeries(instrumentToken, FIFTEEN_MINUTE, fromDate15Min, toDate, FIFTEEN_MIN))
                    .series1Hour(fetchAndConvertToBarSeries(instrumentToken, SIXTY_MINUTE, fromDate1Hour, toDate, ONE_HOUR))
                    .series1Day(fetchAndConvertToBarSeries(instrumentToken, DAY, fromDate1Day, toDate, ONE_DAY))
                    .build();

            instrumentSeriesMap.put(instrumentToken, seriesData);
            lastVolumeMap.put(instrumentToken, 0L);

            avg5MinCandleHeightMap.put(instrumentToken, calculateAverageCandleHeight(instrumentToken, FIVE_MIN, 5));
            avg3MinCandleHeightMap.put(instrumentToken, calculateAverageCandleHeight(instrumentToken, THREE_MIN, 5));

            log.info("Initialized series data for instrument: {}", instrumentToken);
        } catch (Exception e) {
            log.error("Failed to fetch historical data for instrument: {} - {}", instrumentToken, e.getMessage());
        }
    }

    public void addTick(String instrumentToken, Tick tick) {
        BarSeriesData seriesData = instrumentSeriesMap.get(instrumentToken);
        if (seriesData == null) {
            log.warn("No series data found for instrument: {}", instrumentToken);
            return;
        }

        LocalDateTime localDateTime = Instant.ofEpochMilli(tick.getTickTimestamp().getTime()).atZone(ZoneId.of("Asia/Kolkata")).toLocalDateTime();
        ZonedDateTime endTime = ZonedDateTime.of(localDateTime, ZoneId.of("Asia/Kolkata"));

        long cumulativeVolume = tick.getVolumeTradedToday();
        long lastVolume = lastVolumeMap.getOrDefault(instrumentToken, 0L);
        double tickVolume = cumulativeVolume - lastVolume;

        lastVolumeMap.put(instrumentToken, cumulativeVolume);
        lastTickMap.put(instrumentToken, tick);
        // Update all series
        updateBarSeries(seriesData.getSeries1Min(), tick, endTime, Duration.ofMinutes(1), tickVolume);
        updateBarSeries(seriesData.getSeries3Min(), tick, endTime, Duration.ofMinutes(3), tickVolume);
        updateBarSeries(seriesData.getSeries5Min(), tick, endTime, Duration.ofMinutes(5), tickVolume);
        updateBarSeries(seriesData.getSeries15Min(), tick, endTime, Duration.ofMinutes(15), tickVolume);
        updateBarSeries(seriesData.getSeries1Hour(), tick, endTime, Duration.ofHours(1), tickVolume);
        updateBarSeries(seriesData.getSeries1Day(), tick, endTime, Duration.ofDays(1), tickVolume);
    }

    private void updateBarSeries(BarSeries series, Tick tick, ZonedDateTime tickTime, Duration duration, double volume) {
        if (series == null) {
            return;
        }
        if (series.isEmpty()) {
            series.addBar(duration, tickTime, tick.getOpenPrice(), tick.getHighPrice(), tick.getLowPrice(), tick.getClosePrice(), volume);
        } else {
            Bar lastBar = series.getLastBar();
            ZonedDateTime lastBarEndTime = lastBar.getEndTime();
            ZonedDateTime lastBarBeginTime = lastBar.getBeginTime();
            ZonedDateTime nextBarBeginTime = lastBarEndTime.plus(duration);
            // Ensure tickTime is in the same zone as lastBarEndTime
            if (!tickTime.getZone().equals(lastBarEndTime.getZone())) {
                tickTime = tickTime.withZoneSameInstant(lastBarEndTime.getZone());
            }

            if (tickTime.isBefore(lastBarEndTime) && tickTime.isAfter(lastBarBeginTime)) {
                lastBar.addPrice(DecimalNum.valueOf(tick.getLastTradedPrice()));
            } else if (!tickTime.isBefore(lastBarEndTime)) { // Equals and after create new bar
                // Only add a new bar if tickTime is strictly after
                log.debug("Creating new bar. Tick time: {}, Last bar end: {}, Next bar start: {}", tickTime, lastBarEndTime, nextBarBeginTime);
                series.addBar(duration, nextBarBeginTime, tick.getLastTradedPrice(), tick.getLastTradedPrice(), tick.getLastTradedPrice(), tick.getLastTradedPrice(), volume);
            }
        }
    }

    private BarSeries fetchAndConvertToBarSeries(String instrumentToken, KiteHistoricalDataTimeframeEnum kiteTimeFrame,
                                                 Date fromDate, Date toDate, CandleTimeFrameEnum timeFrame) {
        HistoricalData historicalData = fetchHistoricalData(instrumentToken, kiteTimeFrame.getTimeframe(), fromDate, toDate);
        return convertToBarSeries(historicalData, timeFrame.name());
    }


    private BarSeries convertToBarSeries(HistoricalData historicalData, String seriesName) {
        BarSeries series = new BaseBarSeriesBuilder().withName(seriesName).build();
        Duration duration = getDurationFromSeriesName(seriesName);
        ZoneId zoneId = ZoneId.of("Asia/Kolkata");
        for (HistoricalData candle : historicalData.dataArrayList) {
            ZonedDateTime endTime = DateTimeHandler.getZonedDateTime(candle.timeStamp, "yyyy-MM-dd'T'HH:mm:ssZ")
                    .withZoneSameInstant(zoneId);
            series.addBar(duration, endTime.plus(duration), candle.open, candle.high, candle.low, candle.close, candle.volume);
        }
        return series;
    }

    private Duration getDurationFromSeriesName(String seriesName) {
        return switch (seriesName) {
            case "ONE_MIN" -> Duration.ofMinutes(1);
            case "THREE_MIN" -> Duration.ofMinutes(3);
            case "FIVE_MIN" -> Duration.ofMinutes(5);
            case "FIFTEEN_MIN" -> Duration.ofMinutes(15);
            case "ONE_HOUR" -> Duration.ofHours(1);
            case "ONE_DAY" -> Duration.ofDays(1);
            default -> throw new IllegalArgumentException("Invalid series name");
        };
    }

    public double calculateAverageCandleHeight(String instrumentToken, CandleTimeFrameEnum timeFrame, int requiredBars) {
        BarSeries series = getBarSeriesForTimeFrame(instrumentToken, timeFrame);
        if (series == null || series.getBarCount() < requiredBars) return 0.0;

        double totalHeight = 0.0;
        for (int i = series.getBarCount() - requiredBars; i < series.getBarCount(); i++) {
            Bar bar = series.getBar(i);
            totalHeight += Math.abs(bar.getClosePrice().doubleValue() - bar.getOpenPrice().doubleValue());
        }

        return totalHeight / requiredBars;
    }

    public BarSeries getBarSeriesForTimeFrame(String instrumentToken, CandleTimeFrameEnum timeFrame) {
        BarSeriesData seriesData = instrumentSeriesMap.get(instrumentToken);
        if (seriesData == null) return null;

        return switch (timeFrame) {
            case ONE_MIN -> seriesData.getSeries1Min();
            case THREE_MIN -> seriesData.getSeries3Min();
            case FIVE_MIN -> seriesData.getSeries5Min();
            case FIFTEEN_MIN -> seriesData.getSeries15Min();
            case ONE_HOUR -> seriesData.getSeries1Hour();
            case ONE_DAY -> seriesData.getSeries1Day();
        };
    }

    public void reset() {
        instrumentSeriesMap.clear();
        avg5MinCandleHeightMap.clear();
        avg3MinCandleHeightMap.clear();
        lastVolumeMap.clear();
    }

    public double getLastNCandleTotalLength(BarSeries series, int n) {
        int barCount = series.getBarCount();
        Bar firstBar = series.getBar(barCount - n);
        Bar lastBar = series.getBar(barCount - 1);

        double firstBarHigh = firstBar.getHighPrice().doubleValue();
        double lastBarLow = lastBar.getLowPrice().doubleValue();

        return Math.abs(firstBarHigh - lastBarLow);
    }

    public HistoricalData fetchHistoricalData(String token, String interval, Date fromDate, Date toDate) {
        try {
            return kiteConnect.getHistoricalData(fromDate, toDate, token, interval, false, false);
        } catch (KiteException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Double getAvgCandleHeightForTimeFrame(String instrumentToken, CandleTimeFrameEnum timeFrame) {
        return switch (timeFrame) {
            case THREE_MIN -> avg3MinCandleHeightMap.get(instrumentToken);
            case FIVE_MIN -> avg5MinCandleHeightMap.get(instrumentToken);
            default -> throw new IllegalStateException("Unexpected value: " + timeFrame);
        };
    }

    public boolean isBackToBackSameCandleColor(BarSeries barSeries, int i) {
        if (barSeries.getBarCount() < i) {
            return false;
        }

        // Check the last i candles for green
        boolean isLastGreen = true;
        for (int j = 0; j < i; j++) {
            Bar bar = barSeries.getBar(barSeries.getEndIndex() - j);
            if (!bar.getClosePrice().isGreaterThan(bar.getOpenPrice())) {
                isLastGreen = false;
                break;
            }
        }

        if (isLastGreen) {
            return true;
        }

        // Check the last i candles for red
        boolean isLastRed = true;
        for (int j = 0; j < i; j++) {
            Bar bar = barSeries.getBar(barSeries.getEndIndex() - j);
            if (!bar.getClosePrice().isLessThan(bar.getOpenPrice())) {
                isLastRed = false;
                break;
            }
        }

        return isLastRed;
    }

    public Tick getLastTick(String instrumentToken) {
        return lastTickMap.get(instrumentToken);
    }

    public boolean isInitialized(String instrumentToken) {
        return !instrumentSeriesMap.isEmpty() && instrumentSeriesMap.containsKey(instrumentToken);
    }

    public boolean isCandleOpen(String instrumentToken, Date tickTimestamp, CandleTimeFrameEnum timeFrame) {
        BarSeries series = getBarSeriesForTimeFrame(instrumentToken, timeFrame);
        if (series == null || series.isEmpty()) return false;

        ZonedDateTime tickTime = tickTimestamp.toInstant().atZone(ZoneId.of("Asia/Kolkata"));
        Bar lastBar = series.getLastBar();
        return tickTime.isAfter(lastBar.getBeginTime()) && tickTime.isBefore(lastBar.getEndTime());
    }

}
