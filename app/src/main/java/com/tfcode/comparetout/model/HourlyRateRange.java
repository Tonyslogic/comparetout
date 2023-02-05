package com.tfcode.comparetout.model;

import java.util.ArrayList;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.IntStream;

public class HourlyRateRange {
    private final ArrayList<HourlyRate> mRates = new ArrayList<>();
    private final NavigableMap<Integer, Double> mLookup = new TreeMap<>();

    public HourlyRateRange(DoubleHolder dh) {
        double previousRate = dh.doubles.get(0);
        int hour = 0;
        int begin = 0;
        int end = 0;
        for (double rate : dh.doubles) {
            if (rate != previousRate) {
                mRates.add(new HourlyRate(begin, end, previousRate));
                previousRate = rate;
                begin = hour;
            }
            end = hour + 1;
            hour++;
        }
        mRates.add(new HourlyRate(begin, end -1, previousRate));

        // Populate the lookup
        for (HourlyRate rate : mRates) mLookup.put(rate.getBegin(), rate.getPrice());
    }

    public double lookup(int hour) {
        return mLookup.floorEntry(hour).getValue();
    }

    public DoubleHolder getDoubleHolder() {
        DoubleHolder dh = new DoubleHolder();
        IntStream.iterate(0, i -> i + 24)
            .forEach(i -> dh.doubles.add(lookup(i)));
        return dh;
    }

    public ArrayList<HourlyRate> getRates() {
        return mRates;
    }

}
