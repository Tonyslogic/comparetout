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

package com.tfcode.comparetout.util;

import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.HourlyRateRange;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
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
                dowRange = Objects.requireNonNull(mLookup.floorEntry(startDOY)).getValue();
                if (null == dowRange) dowRange = new TreeMap<>();
            }

            HourlyRateRange rateRange = new HourlyRateRange(dr.getHours());
            for (Integer day : dr.getDays().ints){
                dowRange.put(day, rateRange);
            }

            mLookup.put(startDOY, dowRange);
        }
    }

    public double getRate(int do2001, int minuteOfDay, int dayOfWeek) {
        return Objects.requireNonNull(Objects.requireNonNull(mLookup.floorEntry(do2001))
                .getValue()
                .floorEntry(dayOfWeek))
                .getValue()
                .lookup(minuteOfDay/60);
    }
}
