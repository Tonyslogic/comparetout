package com.tfcode.comparetout.lookup;

import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.HourlyRateRange;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class RateLookup {
    private final NavigableMap<Integer, NavigableMap<Integer, HourlyRateRange>> mLookup
            = new TreeMap<>();

    public RateLookup(List<DayRate> drs) {
        for (DayRate dr : drs) {
            String[] startBits = dr.getStartDate().split("/");
            LocalDate start = LocalDate.of(2001, Integer.parseInt(startBits[0]), Integer.parseInt(startBits[1]));
            int startDOY = start.getDayOfYear();

            NavigableMap<Integer, HourlyRateRange> dowRange;
            Map.Entry<Integer, NavigableMap<Integer, HourlyRateRange>> entry = mLookup.floorEntry(startDOY);
            if (null == entry) dowRange = new TreeMap<>();
            else {
                dowRange = mLookup.floorEntry(startDOY).getValue();
                if (null == dowRange) dowRange = new TreeMap<>();
            }

            HourlyRateRange rateRange = new HourlyRateRange(dr.getHours());
            for (Integer day : dr.getDays().ints){
                dowRange.put(day, rateRange);
            }

            mLookup.put(startDOY, dowRange);
        }
    }

    public double getRate(String date, int minuteOfDay, int dayOfWeek) {
        LocalDate qDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        int dayOfYear = qDate.getDayOfYear();
        return mLookup.floorEntry(dayOfYear).getValue().floorEntry(dayOfWeek).getValue().lookup(minuteOfDay/60);
    }
}