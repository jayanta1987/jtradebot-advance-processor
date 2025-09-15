package com.jtradebot.processor.handler;

import com.zerodhatech.models.Tick;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
public class DateTimeHandler {

    private static final Set<Date> marketHolidays = new HashSet<>();

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
    // Utility methods for IST time formatting
    public static String formatDateToIST(Date date) {
        if (date == null) return null;
        ZonedDateTime istTime = date.toInstant().atZone(ZoneId.of("Asia/Kolkata"));
        return istTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss z"));
    }

    public static String getCurrentISTTime() {
        ZonedDateTime istTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        return istTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss z"));
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

    public static Date getLastMarketTime(Date date, int marketOpenHour, int marketOpenMinute, 
                                       int marketCloseHour, int marketCloseMinute) {
        // if date is after market close time, return the same date with market close time
        // if date is before market close time, return the same date with one minute before requested date time
        // else return the previous working day with market close time

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        if (calendar.get(Calendar.HOUR_OF_DAY) > marketCloseHour ||
                (calendar.get(Calendar.HOUR_OF_DAY) == marketCloseHour && calendar.get(Calendar.MINUTE) >= marketCloseMinute)) {
            calendar.set(Calendar.HOUR_OF_DAY, marketCloseHour);
            calendar.set(Calendar.MINUTE, marketCloseMinute);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
        } else if ( // If date is exactly or before market open time on the same day
                calendar.get(Calendar.HOUR_OF_DAY) < marketOpenHour ||
                        (calendar.get(Calendar.HOUR_OF_DAY) == marketOpenHour && calendar.get(Calendar.MINUTE) < marketOpenMinute)) {
            // then get getLatestWorkingDay()
            calendar.setTime(getLastWorkingDayFromDate(date, marketCloseHour, marketCloseMinute));
            // return same date

        }

        return calendar.getTime();
    }

    public static Date getLastWorkingDayFromDate(Date date, int marketCloseHour, int marketCloseMinute) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_YEAR, -1);
        adjustToLastWorkingDay(calendar);

        calendar.set(Calendar.HOUR_OF_DAY, marketCloseHour);
        calendar.set(Calendar.MINUTE, marketCloseMinute);
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

    public static boolean isMarketOpen(Date timestamp, int marketOpenHour, int marketOpenMinute, 
                                     int marketCloseHour, int marketCloseMinute) {
        // Convert timestamp to IST timezone for consistent handling
        ZonedDateTime istTime = timestamp.toInstant().atZone(ZoneId.of("Asia/Kolkata"));
        log.debug("current IST tick time: {}", istTime);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(Date.from(istTime.toInstant()));
        
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
        
        // Check if current time is within trading hours using IST time
        int currentHour = istTime.getHour();
        int currentMinute = istTime.getMinute();
        
        boolean afterOpen = (currentHour > marketOpenHour) || 
                           (currentHour == marketOpenHour && currentMinute >= marketOpenMinute);
        boolean beforeClose = (currentHour < marketCloseHour) || 
                             (currentHour == marketCloseHour && currentMinute <= marketCloseMinute);
        
        return afterOpen && beforeClose;
    }
}
