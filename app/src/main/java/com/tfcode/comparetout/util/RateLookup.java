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

/**
 * Efficient rate lookup utility for complex time-of-use electricity pricing calculations.
 * 
 * This class provides high-performance electricity rate lookups that account for multiple
 * pricing variables including time of day, day of week, seasonal variations, and usage
 * restrictions. It pre-processes price plan data into optimized lookup structures to
 * minimize calculation overhead during cost analysis operations.
 * 
 * Key capabilities:
 * - Time-based rate lookups with minute-level precision
 * - Seasonal date range handling with day-of-year calculations
 * - Day-of-week specific pricing (weekday vs weekend rates)
 * - Usage tier restrictions (monthly/annual allowances)
 * - Secondary rate application when limits are exceeded
 * 
 * The lookup system uses NavigableMap structures for efficient range queries,
 * allowing O(log n) time complexity for rate retrievals. This is critical for
 * performance when processing large simulation datasets containing thousands
 * of energy usage data points.
 * 
 * Rate restrictions are tracked across calculation periods to properly apply
 * tiered pricing schemes where rates change based on cumulative usage within
 * monthly or annual periods. The class handles period rollovers and maintains
 * accurate usage counters throughout the calculation process.
 */
public class RateLookup {
    private final NavigableMap<Integer, NavigableMap<Integer, MinuteRateRange>> mLookup
            = new TreeMap<>();

    private final Map<Double, Double> mTiers = new TreeMap<>(); // Rate -> used_kWh
    private final Map<Double, Integer> mPeriodStart = new HashMap<>();
    private final Map<Double, Restriction.RestrictionType> mRestrictions = new HashMap<>();
    private final Map<Double, Pair<Integer, Double>> mLimits = new HashMap<>();
    private int mStartDOY = 0;

    /**
     * Set the starting day of year for usage period calculations.
     * 
     * @param mStartDOY the day of year (1-365) when the billing period begins
     */
    public void setStartDOY(int mStartDOY) {
        this.mStartDOY = mStartDOY;
    }

    /**
     * Initialize the rate lookup system from price plan data.
     * 
     * Processes the price plan and day rate information to build optimized
     * lookup structures. This includes parsing date ranges, organizing rates
     * by day of week, handling legacy hour-based rates, and setting up
     * usage restriction tracking for tiered pricing schemes.
     * 
     * The constructor performs several optimization steps:
     * - Converts date strings to day-of-year integers for efficient comparison
     * - Builds NavigableMap structures for O(log n) lookups
     * - Handles backward compatibility with hour-based rate definitions
     * - Sets up restriction tracking for usage-based tier calculations
     * 
     * @param pricePlan the price plan containing restriction definitions
     * @param drs list of day rates defining time-based pricing structures
     */
    public RateLookup(PricePlan pricePlan, List<DayRate> drs) {
        // Initialize usage restrictions for tiered pricing
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

        // Build the optimized rate lookup structure
        for (DayRate dr : drs) {
            String[] startBits = dr.getStartDate().split("/");
            LocalDate start = LocalDate.of(2001, Integer.parseInt(startBits[0]), Integer.parseInt(startBits[1]));
            int startDOY = start.getDayOfYear();

            // Find or create the day-of-week mapping for this date range
            NavigableMap<Integer, MinuteRateRange> dowRange;
            Map.Entry<Integer, NavigableMap<Integer, MinuteRateRange>> entry = mLookup.floorEntry(startDOY);
            if (null == entry) dowRange = new TreeMap<>();
            else {
                dowRange = Objects.requireNonNull(mLookup.floorEntry(startDOY)).getValue();
                if (null == dowRange) dowRange = new TreeMap<>();
            }

            MinuteRateRange rateRange = dr.getMinuteRateRange();
            if (null == rateRange) rateRange = new MinuteRateRange();

            // Handle backward compatibility - convert legacy hour-based rates
            if (rateRange.getRates().isEmpty()) {
                rateRange = MinuteRateRange.fromHours(dr.getHours());
            }

            // Apply this rate range to all specified days of the week
            for (Integer day : dr.getDays().ints) {
                dowRange.put(day, rateRange);
            }

            mLookup.put(startDOY, dowRange);
        }
    }

    /**
     * Get the electricity rate for a specific time and usage amount.
     * 
     * This is the main public interface for rate lookups. It combines base rate
     * calculation with usage restriction logic to determine the final applicable
     * rate. The method handles complex pricing schemes including tiered rates
     * where the price changes based on cumulative monthly or annual usage.
     * 
     * @param do2001 day of year in 2001 (1-365) for seasonal rate determination
     * @param minuteOfDay minute within the day (0-1439) for time-of-use rates
     * @param dayOfWeek day of week (0=Sunday, 6=Saturday) for weekday/weekend rates
     * @param usedKWH energy consumption amount in kWh for tier calculations
     * @return the applicable electricity rate in the price plan's currency unit
     */
    public double getRate(int do2001, int minuteOfDay, int dayOfWeek, double usedKWH) {
        if (mLookup.isEmpty()) return 0D;

        double rate = getBaseRate(do2001, dayOfWeek, minuteOfDay);

        rate = applyRestrictions(rate, do2001, minuteOfDay, usedKWH);
        return rate;
    }

    /**
     * Retrieve the base electricity rate before applying usage restrictions.
     * 
     * Uses the optimized lookup structure to find the appropriate rate based
     * on temporal factors only. The method performs efficient range queries
     * using NavigableMap.floorEntry() to find the most recent applicable
     * rate definition for the given date and time parameters.
     * 
     * @param do2001 day of year for seasonal rate lookup
     * @param dayOfWeek day of week for weekday/weekend rate differentiation
     * @param minuteOfDay minute of day for time-of-use rate determination
     * @return the base electricity rate before restriction adjustments
     */
    private double getBaseRate(int do2001, int dayOfWeek, int minuteOfDay) {
        Map.Entry<Integer, NavigableMap<Integer, MinuteRateRange>> dateEntry = mLookup.floorEntry(do2001);
        if (dateEntry == null) return 0D;

        NavigableMap<Integer, MinuteRateRange> dayMap = dateEntry.getValue();
        Map.Entry<Integer, MinuteRateRange> minuteEntry = dayMap.floorEntry(dayOfWeek);
        if (minuteEntry == null) return 0D;

        MinuteRateRange minuteRateRange = minuteEntry.getValue();
        return minuteRateRange != null ? minuteRateRange.lookup(minuteOfDay) : 0D;
    }

    /**
     * Apply usage-based restrictions and tier adjustments to the base rate.
     * 
     * Handles complex tiered pricing schemes where rates change based on
     * cumulative usage within specific periods (monthly, annual, etc.).
     * Updates usage tracking counters and applies secondary rates when
     * usage limits are exceeded.
     * 
     * @param rate the base rate before restriction application
     * @param do2001 day of year for period boundary calculations
     * @param minuteOfDay minute of day for period reset timing
     * @param usedKWH energy consumption to add to usage tracking
     * @return the final rate after applying all applicable restrictions
     */
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

    /**
     * Get the duration in days for a specific restriction type.
     * 
     * @param type the restriction periodicity type
     * @return number of days in the restriction period
     */
    private int getDurationForRestriction(Restriction.RestrictionType type) {
        switch (type) {
            case annual: return 365;
            case monthly: return 30;
            case bimonthly: return 60;
            default: return 0;
        }
    }
    
    /**
     * Update period tracking for usage-based restrictions.
     * 
     * Monitors billing period boundaries and resets usage counters when
     * new periods begin. Handles year boundaries and custom start dates
     * to ensure accurate tier calculations across different billing cycles.
     * 
     * @param rate the rate being tracked for usage restrictions
     * @param do2001 current day of year for period boundary detection
     * @param minuteOfDay current minute for precise period reset timing
     */
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

            // Reset usage counters at period boundaries
            if (newPeriod || newYear) {
                mTiers.put(rate, 0D);
            }
        }
    }

    /**
     * Update cumulative usage tracking for tiered rate calculations.
     * 
     * Maintains running totals of energy consumption for each rate that has
     * usage-based restrictions. These totals are used to determine when
     * usage thresholds are exceeded and secondary rates should apply.
     * 
     * @param rate the rate to update usage tracking for
     * @param usedKWH additional energy consumption to add to the total
     * @return the updated cumulative usage amount for this rate
     */
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
