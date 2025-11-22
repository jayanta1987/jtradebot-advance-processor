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
            Date fromDate1Min = goBackInPast(toDate, Calendar.DAY_OF_YEAR, 7);  // 1 min: 7 days
            Date fromDate3Min = goBackInPast(toDate, Calendar.DAY_OF_YEAR, 7);  // 3 min: 7 days
            Date fromDate5Min = goBackInPast(toDate, Calendar.DAY_OF_YEAR, 12); // 5 min: 12 days
            Date fromDate15Min = goBackInPast(toDate, Calendar.DAY_OF_YEAR, 15); // 15 min: 15 days
            Date fromDate1Hour = goBackInPast(toDate, Calendar.DAY_OF_YEAR, 20); // 1 hour: 20 days
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
            // For empty series, calculate bar start time and then add duration to get end time
            // addBar() expects the end time as the second parameter
            ZonedDateTime barStartTime = calculateBarStartTime(tickTime, duration);
            ZonedDateTime barEndTime = barStartTime.plus(duration);
            try {
                series.addBar(duration, barEndTime, tick.getOpenPrice(), tick.getHighPrice(), tick.getLowPrice(), tick.getClosePrice(), volume);
            } catch (IllegalArgumentException e) {
                // If bar already exists (shouldn't happen for empty series, but handle gracefully)
                log.warn("Failed to add bar to empty series - bar may already exist. Bar end time: {}, Error: {}", barEndTime, e.getMessage());
                // Re-check if series is still empty or if another thread added a bar
                if (!series.isEmpty()) {
                    // Series is no longer empty, process as normal
                    Bar lastBar = series.getLastBar();
                    ZonedDateTime lastBarEndTime = lastBar.getEndTime();
                    if (!tickTime.getZone().equals(lastBarEndTime.getZone())) {
                        tickTime = tickTime.withZoneSameInstant(lastBarEndTime.getZone());
                    }
                    boolean isSameDay = tickTime.toLocalDate().equals(lastBarEndTime.toLocalDate());
                    boolean isWithinBar = isSameDay && !tickTime.isBefore(lastBar.getBeginTime()) && tickTime.isBefore(lastBarEndTime);
                    if (isWithinBar) {
                        lastBar.addPrice(DecimalNum.valueOf(tick.getLastTradedPrice()));
                    }
                }
            }
        } else {
            Bar lastBar = series.getLastBar();
            ZonedDateTime lastBarEndTime = lastBar.getEndTime();
            ZonedDateTime lastBarBeginTime = lastBar.getBeginTime();
            
            // Ensure tickTime is in the same zone as lastBarEndTime
            if (!tickTime.getZone().equals(lastBarEndTime.getZone())) {
                tickTime = tickTime.withZoneSameInstant(lastBarEndTime.getZone());
            }

            // Check if tick is within the current bar's time range
            // For daily bars (duration >= 1 day), check time range directly since bars span two days
            // For intraday bars, also check if it's the same day
            boolean isDailyBar = duration.toDays() >= 1;
            boolean isSameDay = tickTime.toLocalDate().equals(lastBarEndTime.toLocalDate());
            
            // Include the bar begin time (use !isBefore instead of isAfter to allow equality)
            // For daily bars, only check time range. For intraday bars, also require same day.
            boolean isWithinBar = !tickTime.isBefore(lastBarBeginTime) && tickTime.isBefore(lastBarEndTime);
            if (!isDailyBar) {
                // For intraday bars, also require same day
                isWithinBar = isWithinBar && isSameDay;
            }
            
            if (isWithinBar) {
                lastBar.addPrice(DecimalNum.valueOf(tick.getLastTradedPrice()));
            } else {
                // Need to create a new bar - tick is either from a different day or at/after the current bar end
                ZonedDateTime nextBarBeginTime;
                
                // Calculate what bar start time this tick should belong to
                ZonedDateTime calculatedBarStart = calculateBarStartTime(tickTime, duration);
                ZonedDateTime calculatedBarEnd = calculatedBarStart.plus(duration);
                
                // If the calculated bar matches the last bar's start time, tick should update the existing bar
                if (calculatedBarStart.equals(lastBarBeginTime)) {
                    log.debug("Tick belongs to existing bar based on calculated bar start. Updating last bar. " +
                            "Tick time: {}, Last bar start: {}, Last bar end: {}", 
                            tickTime, lastBarBeginTime, lastBarEndTime);
                    lastBar.addPrice(DecimalNum.valueOf(tick.getLastTradedPrice()));
                    return;
                }
                
                // If calculated bar end is not after last bar end, tick belongs to existing bar
                if (!calculatedBarEnd.isAfter(lastBarEndTime)) {
                    log.debug("Tick belongs to existing bar - calculated bar end {} is not after last bar end {}. " +
                            "Tick time: {}, Calculated start: {}. Updating last bar instead.",
                            calculatedBarEnd, lastBarEndTime, tickTime, calculatedBarStart);
                    lastBar.addPrice(DecimalNum.valueOf(tick.getLastTradedPrice()));
                    return;
                }
                
                // Calculate new bar start time
                if (isDailyBar) {
                    // For daily bars, use the calculated bar start
                    nextBarBeginTime = calculatedBarStart;
                } else {
                    // For intraday bars
                    boolean isDifferentDay = !isSameDay;
                    if (isDifferentDay) {
                        // Cross-day for intraday bars
                        nextBarBeginTime = calculatedBarStart;
                    } else {
                        // Same day: check if tick time exactly equals the last bar end time
                        if (tickTime.equals(lastBarEndTime)) {
                            // Tick is exactly at the bar boundary - update the last bar
                            lastBar.addPrice(DecimalNum.valueOf(tick.getLastTradedPrice()));
                            return;
                        }
                        // Next bar starts where the last bar ended (bars are adjacent)
                        nextBarBeginTime = lastBarEndTime;
                    }
                }
                
                log.debug("Creating new bar. Tick time: {}, Last bar end: {}, Calculated new bar start: {}", 
                         tickTime, lastBarEndTime, nextBarBeginTime);
                
                // addBar() expects the end time, so add duration to the begin time
                ZonedDateTime nextBarEndTime = nextBarBeginTime.plus(duration);
                
                // Validate that the new bar end time is strictly after the last bar end time
                // This prevents duplicate bars or bars that overlap
                if (!nextBarEndTime.isAfter(lastBarEndTime)) {
                    log.warn("Skipping bar creation - calculated end time {} is not after last bar end time {}. Tick time: {}, Duration: {}",
                            nextBarEndTime, lastBarEndTime, tickTime, duration);
                    // Instead of creating a new bar, update the last bar
                    lastBar.addPrice(DecimalNum.valueOf(tick.getLastTradedPrice()));
                    return;
                }
                
                try {
                    series.addBar(duration, nextBarEndTime, tick.getLastTradedPrice(), tick.getLastTradedPrice(), tick.getLastTradedPrice(), tick.getLastTradedPrice(), volume);
                } catch (IllegalArgumentException e) {
                    // If bar already exists (race condition or duplicate tick), update the last bar instead
                    log.warn("Failed to add bar - bar may already exist. Bar end time: {}, Last bar end: {}, Error: {}. Updating last bar instead.",
                            nextBarEndTime, lastBarEndTime, e.getMessage());
                    // Update the existing last bar with the tick price
                    lastBar.addPrice(DecimalNum.valueOf(tick.getLastTradedPrice()));
                }
            }
        }
    }
    
    /**
     * Calculate the start time of the bar that should contain the given tick time.
     * This aligns the tick time to the appropriate bar boundary based on the duration.
     */
    private ZonedDateTime calculateBarStartTime(ZonedDateTime tickTime, Duration duration) {
        LocalDateTime localDateTime = tickTime.toLocalDateTime();
        
        // For daily bars, start at market open (09:15:00)
        if (duration.toDays() >= 1) {
            return localDateTime.toLocalDate().atStartOfDay()
                    .atZone(tickTime.getZone())
                    .withHour(9).withMinute(15).withSecond(0).withNano(0);
        }
        
        // For hourly bars, align to the hour boundary starting from market open (09:15)
        if (duration.toHours() >= 1) {
            int hour = localDateTime.getHour();
            int minute = localDateTime.getMinute();
            
            // If before market open, align to market open (09:15)
            if (hour < 9 || (hour == 9 && minute < 15)) {
                return localDateTime.toLocalDate().atStartOfDay()
                        .atZone(tickTime.getZone())
                        .withHour(9).withMinute(15).withSecond(0).withNano(0);
            }
            
            // Calculate hours since market open (09:15)
            long minutesSinceOpen = ((hour - 9) * 60) + (minute - 15);
            long hoursSinceOpen = minutesSinceOpen / 60;
            long alignedHours = (hoursSinceOpen / duration.toHours()) * duration.toHours();
            
            return localDateTime.toLocalDate().atStartOfDay()
                    .atZone(tickTime.getZone())
                    .withHour(9).withMinute(15)
                    .plusHours(alignedHours)
                    .withSecond(0).withNano(0);
        }
        
        // For minute-based bars (1min, 3min, 5min, 15min), align to minute boundaries starting from market open (09:15)
        long minutesSinceMidnight = (localDateTime.getHour() * 60) + localDateTime.getMinute();
        long marketOpenMinutes = (9 * 60) + 15; // 09:15 = 555 minutes
        
        // If before market open, align to market open
        if (minutesSinceMidnight < marketOpenMinutes) {
            return localDateTime.toLocalDate().atStartOfDay()
                    .atZone(tickTime.getZone())
                    .withHour(9).withMinute(15).withSecond(0).withNano(0);
        }
        
        // Calculate minutes since market open
        long minutesSinceOpen = minutesSinceMidnight - marketOpenMinutes;
        long durationMinutes = duration.toMinutes();
        
        // Align to the appropriate bar boundary
        long alignedMinutes = (minutesSinceOpen / durationMinutes) * durationMinutes;
        
        return localDateTime.toLocalDate().atStartOfDay()
                .atZone(tickTime.getZone())
                .withHour(9).withMinute(15)
                .plusMinutes(alignedMinutes)
                .withSecond(0).withNano(0);
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
