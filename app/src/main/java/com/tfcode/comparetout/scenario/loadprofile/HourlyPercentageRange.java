/*
 * Copyright (c) 2023. Tony Finnerty
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.tfcode.comparetout.scenario.loadprofile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        Map.Entry<Integer, Double> x = mLookup.floorEntry(hour);
        if (!(null == x) && !(null == x.getValue())) return x.getValue();
        else return 0D;
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
