package com.jtradebot.processor.handler;

import com.zerodhatech.models.Tick;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class DateTimeHandler {

    public static final int MARKET_CLOSE_HOUR = 15;
    public static final int MARKET_CLOSE_MINUTE = 30;
    public static final int MARKET_OPEN_HOUR = 9;
    public static final int MARKET_OPEN_MINUTE = 15;

    private static final Set<Date> marketHolidays = new HashSet<>();

    public static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";

    static {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            // Add holidays based on the provided chart
            marketHolidays.add(sdf.parse("2024-06-17")); // Bakri Eid
            marketHolidays.add(sdf.parse("2024-07-17")); // Moharram
            marketHolidays.add(sdf.parse("2024-08-15")); // Independence Day
            marketHolidays.add(sdf.parse("2024-10-02")); // Mahatma Gandhi Jayanti
            marketHolidays.add(sdf.parse("2024-11-01")); // Diwali-Laxmi Pujan
            marketHolidays.add(sdf.parse("2024-11-15")); // Gurunanak Jayanti
            marketHolidays.add(sdf.parse("2024-12-25")); // Christmas
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Date getLatestWorkingDay() {
        // call  getLastMarketTime(Date date) with current date
        return getLastMarketTime(new Date());
    }

    private static void adjustToLastWorkingDay(Calendar calendar) {
        // Check if current day is a working day, and adjust if it's not
        while (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY ||
                marketHolidays.contains(calendar.getTime())) {
            if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
                calendar.add(Calendar.DAY_OF_YEAR, -1);  // If Saturday, move to Friday
            } else if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                calendar.add(Calendar.DAY_OF_YEAR, -2);  // If Sunday, move to Friday
            } else {
                calendar.add(Calendar.DAY_OF_YEAR, -1);  // Adjust for holidays
            }
        }
    }

    @Nullable
    public static LocalDateTime getTickTime(Tick tick) {
        LocalDateTime tickTime = null;
        if (tick.getLastTradedTime() != null) {
            Date date = tick.getLastTradedTime();
            tickTime = date.toInstant().atZone(ZoneId.of("Asia/Kolkata")).toLocalDateTime();
        }
        return tickTime;
    }

    @Nullable
    public static LocalDateTime toLocalDateTime(Date date) {
        return date.toInstant().atZone(ZoneId.of("Asia/Kolkata")).toLocalDateTime();
    }

    @Nullable
    public static String getTickTimeString(Tick tick) {
        if (tick.getLastTradedTime() != null) {
            return tick.getLastTradedTime()
                    .toInstant()
                    .atZone(ZoneId.of("Asia/Kolkata"))
                    .toLocalDateTime()
                    .toString(); // ISO 8601 format
        }
        return null;
    }

    public static ZonedDateTime getZonedDateTime(String timeStamp, String format) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
        return ZonedDateTime.parse(timeStamp, formatter.withZone(ZoneId.of("Z")));
    }

    public static Date goBackInPast(Date startDate, int calenderIndicator, int count) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startDate);
        calendar.add(calenderIndicator, -count);
        return calendar.getTime();
    }

    public static ZonedDateTime getCurrentDateTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_FORMAT);
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        return ZonedDateTime.parse(now.format(formatter), formatter);
    }

    public static Date getNextValidWeeklyExpiryDate() {
        Calendar calendar = Calendar.getInstance();
        // Find next Thursday
        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.THURSDAY) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        // Adjust for holidays
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        while (marketHolidays.stream().anyMatch(
                holiday -> sdf.format(holiday).equals(sdf.format(calendar.getTime())))) {
            calendar.add(Calendar.DAY_OF_YEAR, -1);
        }

        return calendar.getTime();
    }

    public static Date getNextValidWeeklyExpiryDate(Date startDate) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startDate);
        // Find next Thursday
        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.THURSDAY) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        // Adjust for holidays
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        while (marketHolidays.stream().anyMatch(
                holiday -> sdf.format(holiday).equals(sdf.format(calendar.getTime())))) {
            calendar.add(Calendar.DAY_OF_YEAR, -1);
        }
        // Set hours, minutes, seconds and milliseconds to zero
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        return calendar.getTime();
    }

    public static boolean isFriday() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY;
    }

    public static Date getLastMarketTime(Date date) {
        // if date is after market close time, return the same date with market close time
        // if date is before market close time, return the same date with one minute before requested date time
        // else return the previous working day with market close time

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        if (calendar.get(Calendar.HOUR_OF_DAY) > MARKET_CLOSE_HOUR ||
                (calendar.get(Calendar.HOUR_OF_DAY) == MARKET_CLOSE_HOUR && calendar.get(Calendar.MINUTE) >= MARKET_CLOSE_MINUTE)) {
            calendar.set(Calendar.HOUR_OF_DAY, MARKET_CLOSE_HOUR);
            calendar.set(Calendar.MINUTE, MARKET_CLOSE_MINUTE);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
        } else if ( // If date is exactly or before market open time on the same day
                calendar.get(Calendar.HOUR_OF_DAY) < MARKET_OPEN_HOUR ||
                        (calendar.get(Calendar.HOUR_OF_DAY) == MARKET_OPEN_HOUR && calendar.get(Calendar.MINUTE) < MARKET_OPEN_MINUTE)) {
            // then get getLatestWorkingDay()
            calendar.setTime(getLastWorkingDayFromDate(date));
            // return same date

        }

        return calendar.getTime();
    }

    public static Date getLastWorkingDayFromDate(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_YEAR, -1);
        adjustToLastWorkingDay(calendar);

        calendar.set(Calendar.HOUR_OF_DAY, MARKET_CLOSE_HOUR);
        calendar.set(Calendar.MINUTE, MARKET_CLOSE_MINUTE);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        return calendar.getTime();
    }

    public static boolean withinTradingHours(int startHour, int startMinute, int endHour, int endMinute, Date tickTimestamp) {
        LocalTime tickTime = tickTimestamp.toInstant().atZone(ZoneId.of("Asia/Kolkata")).toLocalTime();
        LocalTime start = LocalTime.of(startHour, startMinute);
        LocalTime end = LocalTime.of(endHour, endMinute);
        return !tickTime.isBefore(start) && !tickTime.isAfter(end);
    }

    public static int getTradeDurationInSec(Date startTime, Date tickTimestamp) {
        return (int) ((tickTimestamp.getTime() - startTime.getTime()) / 1000);
    }

    public static String formatDate(Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MMM-yyyy");
        return dateFormat.format(date);
    }

    public static int getHolidaysAndWeekends(Date toDate, int lastDays) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(toDate);
        int count = 0;
        for(int i=0; i<lastDays; i++) {
            calendar.add(Calendar.DAY_OF_YEAR, -1);
            if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                    calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY ||
                    marketHolidays.contains(calendar.getTime())) {
                count++;
            }
        }
        return count;
    }

    public static @NotNull String getTodaysDateString(String zoneId, String pattern) {
        ZonedDateTime today = ZonedDateTime.now(ZoneId.of(zoneId));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return today.format(formatter);
    }

    /**
     * Check if market has started based on current time
     * @return true if market has started, false otherwise
     */
    public static boolean isMarketStarted() {
        return isMarketStarted(new Date());
    }

    /**
     * Check if market has started based on given timestamp
     * @param timestamp the timestamp to check
     * @return true if market has started, false otherwise
     */
    public static boolean isMarketStarted(Date timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(timestamp);
        
        // Check if it's a weekend
        if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || 
            calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            return false;
        }
        
        // Check if it's a holiday
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        if (marketHolidays.stream().anyMatch(holiday -> 
            sdf.format(holiday).equals(sdf.format(calendar.getTime())))) {
            return false;
        }
        
        // Check if current time is after market open time
        int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
        int currentMinute = calendar.get(Calendar.MINUTE);
        
        return (currentHour > MARKET_OPEN_HOUR) || 
               (currentHour == MARKET_OPEN_HOUR && currentMinute >= MARKET_OPEN_MINUTE);
    }

    /**
     * Check if market is currently open (between open and close time)
     * @return true if market is open, false otherwise
     */
    public static boolean isMarketOpen() {
        return isMarketOpen(new Date());
    }

    /**
     * Check if market is open based on given timestamp
     * @param timestamp the timestamp to check
     * @return true if market is open, false otherwise
     */
    public static boolean isMarketOpen(Date timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(timestamp);
        
        // Check if it's a weekend
        if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || 
            calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            return false;
        }
        
        // Check if it's a holiday
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        if (marketHolidays.stream().anyMatch(holiday -> 
            sdf.format(holiday).equals(sdf.format(calendar.getTime())))) {
            return false;
        }
        
        // Check if current time is within trading hours
        int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
        int currentMinute = calendar.get(Calendar.MINUTE);
        
        boolean afterOpen = (currentHour > MARKET_OPEN_HOUR) || 
                           (currentHour == MARKET_OPEN_HOUR && currentMinute >= MARKET_OPEN_MINUTE);
        boolean beforeClose = (currentHour < MARKET_CLOSE_HOUR) || 
                             (currentHour == MARKET_CLOSE_HOUR && currentMinute <= MARKET_CLOSE_MINUTE);
        
        return afterOpen && beforeClose;
    }
}
