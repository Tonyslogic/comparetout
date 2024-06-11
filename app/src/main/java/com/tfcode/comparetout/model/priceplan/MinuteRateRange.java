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

package com.tfcode.comparetout.model.priceplan;

import java.util.ArrayList;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class MinuteRateRange {
    private final ArrayList<RangeRate> mRates = new ArrayList<>();
    private final NavigableMap<Integer, Double> mLookup = new TreeMap<>();

    public MinuteRateRange() {
    }

    public static MinuteRateRange fromHours(DoubleHolder dh) {
        MinuteRateRange ret = new MinuteRateRange();
        double previousRate = dh.doubles.get(0);
        int minute = 0;
        int begin = 0;
        int end = 0;
        for (double rate : dh.doubles) {
            if (rate != previousRate) {
                ret.mRates.add(new RangeRate(begin, end, previousRate));
                previousRate = rate;
                begin = minute;
            }
            end = minute + 60;
            minute = minute + 60;
        }
        ret.mRates.add(new RangeRate(begin, end - 60, previousRate));

        // Populate the lookup
        for (RangeRate rate : ret.mRates) ret.mLookup.put(rate.getBegin(), rate.getPrice());

        return ret;
    }

    public void add(int begin, int end, double cost) {
        mRates.add(new RangeRate(begin, end - 1, cost));

        // Populate the lookup
        for (RangeRate rate : mRates) mLookup.put(rate.getBegin(), rate.getPrice());
    }

    public double lookup(int minute) {
        Map.Entry<Integer,Double> entry = mLookup.floorEntry(minute);
        if (!(null == entry)) return entry.getValue();
        else return 0D;
    }

    public ArrayList<RangeRate> getRates() {
        return mRates;
    }

}
