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

import android.util.Pair;

import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.MinuteRateRange;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.priceplan.Restriction;
import com.tfcode.comparetout.model.priceplan.Restrictions;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

public class RateLookup {
    private final NavigableMap<Integer, NavigableMap<Integer, MinuteRateRange>> mLookup
            = new TreeMap<>();

    private final Map<Double, Double> mTiers = new TreeMap<>(); // Rate -> used_kWh
    private final Map<Double, Integer> mPeriodStart = new HashMap<>();
    private final Map<Double, Restriction.RestrictionType> mRestrictions = new HashMap<>();
    private final Map<Double, Pair<Integer, Double>> mLimits = new HashMap<>();
    private int mStartDOY = 0;

    public void setStartDOY(int mStartDOY) {
        this.mStartDOY = mStartDOY;
    }

    public RateLookup(PricePlan pricePlan, List<DayRate> drs) {
        // Initialize the rates
        Restrictions restrictions = pricePlan.getRestrictions();
        // Need to check for null as the DB update omitted a default object with no entries
        if (!(null == restrictions)) for (Restriction r : restrictions.getRestrictions()) {
            Restriction.RestrictionType type = r.getPeriodicity();
            Map<String, Pair<Integer, Double>> entries = r.getRestrictionEntries();
            for (Map.Entry<String, Pair<Integer, Double>> entry : entries.entrySet()) {
                Double rate = Double.parseDouble(entry.getKey());
                mRestrictions.put(rate, type);
                mLimits.put(rate, entry.getValue());
            }
        }

        // Initialize the rate lookup
        for (DayRate dr : drs) {
            String[] startBits = dr.getStartDate().split("/");
            LocalDate start = LocalDate.of(2001, Integer.parseInt(startBits[0]), Integer.parseInt(startBits[1]));
            int startDOY = start.getDayOfYear();

            NavigableMap<Integer, MinuteRateRange> dowRange;
            Map.Entry<Integer, NavigableMap<Integer, MinuteRateRange>> entry = mLookup.floorEntry(startDOY);
            if (null == entry) dowRange = new TreeMap<>();
            else {
                dowRange = Objects.requireNonNull(mLookup.floorEntry(startDOY)).getValue();
                if (null == dowRange) dowRange = new TreeMap<>();
            }

            MinuteRateRange rateRange = dr.getMinuteRateRange();
            if (null == rateRange) rateRange = new MinuteRateRange();

            // Check that the rate range is empty. If so, fill it with the values from legacy hours
            if (rateRange.getRates().isEmpty()) {
                rateRange = MinuteRateRange.fromHours(dr.getHours());
            }

            for (Integer day : dr.getDays().ints) {
                dowRange.put(day, rateRange);
            }

            mLookup.put(startDOY, dowRange);
        }
    }

    public double getRate(int do2001, int minuteOfDay, int dayOfWeek, double usedKWH) {
        if (mLookup.isEmpty()) return 0D;

        double rate = getBaseRate(do2001, dayOfWeek, minuteOfDay);

        rate = applyRestrictions(rate, do2001, minuteOfDay, usedKWH);
        return rate;
    }

    private double getBaseRate(int do2001, int dayOfWeek, int minuteOfDay) {
        Map.Entry<Integer, NavigableMap<Integer, MinuteRateRange>> dateEntry = mLookup.floorEntry(do2001);
        if (dateEntry == null) return 0D;

        NavigableMap<Integer, MinuteRateRange> dayMap = dateEntry.getValue();
        Map.Entry<Integer, MinuteRateRange> minuteEntry = dayMap.floorEntry(dayOfWeek);
        if (minuteEntry == null) return 0D;

        MinuteRateRange minuteRateRange = minuteEntry.getValue();
        return minuteRateRange != null ? minuteRateRange.lookup(minuteOfDay) : 0D;
    }

    private double applyRestrictions(double rate, int do2001, int minuteOfDay, double usedKWH) {
        Restriction.RestrictionType type = mRestrictions.get(rate);
        if (type == null) return rate;

        updatePeriod(rate, do2001, minuteOfDay);
        double totalUsedKWH = updateTotalUsed(rate, usedKWH);

        Pair<Integer, Double> limit = mLimits.get(rate);
        if (limit != null && limit.first != null && totalUsedKWH > limit.first) {
            return limit.second; // Return secondary rate if limit exceeded
        } else {
            return rate; // Return base rate
        }
    }

    private int getDurationForRestriction(Restriction.RestrictionType type) {
        switch (type) {
            case annual: return 365;
            case monthly: return 30;
            case bimonthly: return 60;
            default: return 0;
        }
    }
    private void updatePeriod(double rate, int do2001, int minuteOfDay) {
        Restriction.RestrictionType restrictionType = mRestrictions.get(rate);
        if (restrictionType != null) { // Check if restriction exists for the rate
            Integer prevDO2001Obj = mPeriodStart.getOrDefault(rate, mStartDOY);
            int prevDO2001 = prevDO2001Obj != null ? prevDO2001Obj : mStartDOY;
            if (prevDO2001 == mStartDOY) mTiers.put(rate, 0D);
            mPeriodStart.put(rate, do2001);

            int duration = getDurationForRestriction(restrictionType);
            int wrappedDaysSinceStart = (do2001 - mStartDOY + 365) % 365;
            boolean newPeriod = (wrappedDaysSinceStart % duration == 0) && (minuteOfDay == 120);
            boolean isOffset = mStartDOY > 0;
            boolean newYear = do2001 < prevDO2001;
            if (isOffset) newYear = do2001 > 365 ? do2001 > mStartDOY : newYear;

            if (newPeriod || newYear) {
                mTiers.put(rate, 0D);
            }
        }
    }

    private double updateTotalUsed(double rate, double usedKWH) {
        Restriction.RestrictionType restrictionType = mRestrictions.get(rate);
        if (restrictionType != null) {
            Double totalUsedKWHObj = mTiers.getOrDefault(rate, 0D);
            double totalUsedKWH = totalUsedKWHObj != null ? totalUsedKWHObj : 0D;
            totalUsedKWH += usedKWH;
            mTiers.put(rate, totalUsedKWH);
            return totalUsedKWH;
        } else {
            return rate;
        }
    }
}
