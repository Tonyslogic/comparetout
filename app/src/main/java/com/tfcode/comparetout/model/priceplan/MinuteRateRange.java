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

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

public class MinuteRateRange {
    private ArrayList<RangeRate> mRates = new ArrayList<>();
    private NavigableMap<Integer, Double> mLookup = new TreeMap<>();

    public MinuteRateRange() {
    }

    public static MinuteRateRange fromHours(@NonNull DoubleHolder dh) {
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
        if (begin > end) return;
        if (end > 1440) return;
        if (begin < 0) return;
        mRates.add(new RangeRate(begin, end, cost));
        mergeContiguousRanges();

        // Populate the lookup
        mLookup = new TreeMap<>();
        for (RangeRate rate : mRates) mLookup.put(rate.getBegin(), rate.getPrice());
    }

    /**
     * Inserts a new price range into the existing set of rates.
     * <p>
     * This method ensures that the resulting set of rates remains non-overlapping. If the new range
     * overlaps with any existing ranges, those ranges are adjusted to accommodate the new range.
     * <p>
     * After inserting the new range, the internal lookup structure is updated to reflect the changes.
     *
     * @param begin The starting point of the new price range.
     * @param end   The ending point of the new price range.
     * @param cost  The price associated with the new range.
     */
    public void insert(int begin, int end, double cost) {
        mRates.sort(Comparator.comparingInt(RangeRate::getBegin));
        ArrayList<RangeRate> adjustedRates = new ArrayList<>();
        for (RangeRate rate : mRates) {
            if (end <= rate.getBegin() || begin >= rate.getEnd()) {
                // No overlap
                adjustedRates.add(rate);
            } else {
                // Overlap detected
                if (begin > rate.getBegin()) {
                    adjustedRates.add(new RangeRate(rate.getBegin(), begin, rate.getPrice()));
                }
                if (end < rate.getEnd()) {
                    adjustedRates.add(new RangeRate(end, rate.getEnd(), rate.getPrice()));
                }
            }
            adjustedRates.add(new RangeRate(begin, end, cost));
            adjustedRates.sort(Comparator.comparingInt(RangeRate::getBegin));
            Set<RangeRate> rateSet = new HashSet<>(adjustedRates);
            mRates = new ArrayList<>(rateSet);
            mergeContiguousRanges();

            // Populate the lookup
            mLookup = new TreeMap<>();
            for (RangeRate lookupRate : mRates) mLookup.put(lookupRate.getBegin(), lookupRate.getPrice());
        }
    }

    public void remove(int begin, int end) {
        mRates.sort(Comparator.comparingInt(RangeRate::getBegin));
        RangeRate toRemove = new RangeRate(begin, end, 0D);
        int index = mRates.indexOf(toRemove);
        if (!(index == -1) && (mRates.size() > 1)) {
            mRates.remove(toRemove);
            if (mRates.size() > index) {
                mRates.get(index).setBegin(begin);
            }
        }
        mergeContiguousRanges();

        // Populate the lookup
        mLookup = new TreeMap<>();
        for (RangeRate lookupRate : mRates) mLookup.put(lookupRate.getBegin(), lookupRate.getPrice());
    }

    public void update(int begin, int end, double cost) {
        mRates.sort(Comparator.comparingInt(RangeRate::getBegin));
        ListIterator<RangeRate> iterator = mRates.listIterator();
        RangeRate toUpdate = null;
        RangeRate next = null;

        while (iterator.hasNext()) {
            RangeRate current = iterator.next();
            if (current.getBegin() == begin) {
                toUpdate = current;
                if (iterator.hasNext()) {
                    next = mRates.get(iterator.nextIndex());
                }
                break;
            }
        }

        if (toUpdate == null) {
            throw new IllegalArgumentException("RangeRate to update not found.");
        }

        toUpdate.setEnd(end);
        toUpdate.setPrice(cost);

        if (next != null) {
            next.setBegin(end);
        }
        mergeContiguousRanges();

        // Populate the lookup
        mLookup = new TreeMap<>();
        for (RangeRate lookupRate : mRates) mLookup.put(lookupRate.getBegin(), lookupRate.getPrice());
    }

    private void mergeContiguousRanges() {
        mRates.sort(Comparator.comparingInt(RangeRate::getBegin));
        ArrayList<RangeRate> mergedRates = new ArrayList<>();
        ListIterator<RangeRate> iterator = mRates.listIterator();
        RangeRate previous = null;

        while (iterator.hasNext()) {
            RangeRate current = iterator.next();
            if (previous == null){
                mergedRates.add(current);
                previous = current;
            }
            else {
                if (current.getPrice() == previous.getPrice()) {
                    previous.setEnd(current.getEnd());
                }
                else {
                    mergedRates.add(current);
                    previous = current;
                }
            }
        }
        mRates = mergedRates;
    }

    public double lookup(int minute) {
        Map.Entry<Integer,Double> rate = mLookup.floorEntry(minute);
        if (!(null == rate)) return rate.getValue();
        else return 0D;
    }

    public ArrayList<RangeRate> getRates() {
        return mRates;
    }

}
