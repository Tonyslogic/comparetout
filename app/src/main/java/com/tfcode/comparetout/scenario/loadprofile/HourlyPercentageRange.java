package com.tfcode.comparetout.scenario.loadprofile;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

public class HourlyPercentageRange {
    private final ArrayList<HourlyPercentage> mRates = new ArrayList<>();
    private final NavigableMap<Integer, Double> mLookup = new TreeMap<>();

    public HourlyPercentageRange(DoubleHolder dh) {
        double previousRate = dh.doubles.get(0);
        int hour = 0;
        int begin = 0;
        int end = 0;
        for (double rate : dh.doubles) {
            if (rate != previousRate) {
                mRates.add(new HourlyPercentage(begin, end, previousRate));
                previousRate = rate;
                begin = hour;
            }
            end = hour + 1;
            hour++;
        }
        mRates.add(new HourlyPercentage(begin, end, previousRate));

        // Populate the lookup
        for (HourlyPercentage rate : mRates) mLookup.put(rate.getBegin(), rate.getPercentage());
    }

    public double lookup(int hour) {
        return mLookup.floorEntry(hour).getValue();
    }

    public DoubleHolder getDoubleHolder() {
        DoubleHolder dh = new DoubleHolder();
        List<Double> doubles = new ArrayList<>();
        for (int i = 0; i < 24; i++) doubles.add(lookup(i));
        dh.doubles = doubles;
        return dh;
    }

    public ArrayList<HourlyPercentage> getPercentages() {
        return mRates;
    }

}
