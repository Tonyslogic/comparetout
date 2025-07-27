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

import static java.lang.Math.max;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity(tableName = "PricePlans", indices = {
        @Index(value = {"supplier","planName"}, unique = true) })

/*
  Entity representing an electricity pricing plan with comprehensive tariff structure.

  This Room entity encapsulates all pricing information for an electricity supply plan,
  including time-of-use rates, feed-in tariffs, standing charges, and complex restriction
  rules. The entity supports sophisticated pricing schemes used by modern electricity
  suppliers, including tiered rates, seasonal variations, and usage-based pricing tiers.

  Key pricing components:
  - Time-of-use rates through associated DayRate entities
  - Feed-in tariff rates for solar energy export
  - Daily standing charges and connection fees
  - Sign-up bonuses and promotional credits
  - Deemed export calculations for solar systems
  - Usage restrictions and tiered pricing schemes

  The entity enforces a unique constraint on the combination of supplier and plan name
  to prevent duplicate plan definitions while allowing suppliers to offer multiple
  plans with different names.

  Database relationships:
  - One-to-many with DayRate entities for time-based pricing
  - Referenced by Costings entities for calculation results
  - Supports complex restrictions through embedded Restrictions object

  The plan includes metadata tracking (last update timestamp, reference information)
  to support data management and auditing capabilities.
 */
public class PricePlan {
    @PrimaryKey(autoGenerate = true)
    private long pricePlanIndex;
    @NonNull
    @ColumnInfo(name = "supplier")
    private String supplier = "<SUPPLIER>";
    @NonNull
    @ColumnInfo(name = "planName")
    private String planName = "<PLAN>";
    private double feed = 0.0;
    private double standingCharges = 0.0;
    private double signUpBonus = 0.0;
    @ColumnInfo(name = "deemedExport", defaultValue = "0")
    private boolean deemedExport = false;
    private Restrictions restrictions = new Restrictions();
    @SuppressLint("SimpleDateFormat")
    @NonNull
    @ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")
    private String lastUpdate = (new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
    @NonNull private String reference = "<REFERENCE>";
    private boolean active = true;


    @Override
    public boolean equals(@Nullable Object object)
    {
        if(object == null) return false;
        if(object == this) return true;

        if(object instanceof PricePlan)
        {
            return planName.equals(((PricePlan) object).getPlanName())
                    && supplier.equals(((PricePlan) object).getSupplier());
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return (supplier + planName).hashCode();
    }


    public boolean isDeemedExport() {
        return deemedExport;
    }

    public void setDeemedExport(boolean deemedExport) {
        this.deemedExport = deemedExport;
    }

    public long getPricePlanIndex() {
        return pricePlanIndex;
    }

    public void setPricePlanIndex(long pricePlanIndex) {
        this.pricePlanIndex = pricePlanIndex;
    }


    @NonNull
    public String getSupplier() {
        return supplier;
    }

    public void setSupplier(@NonNull String supplier) {
        this.supplier = supplier;
    }

    @NonNull
    public String getPlanName() {
        return planName;
    }

    public void setPlanName(@NonNull String planName) {
        this.planName = planName;
    }

    public double getFeed() {
        return feed;
    }

    public void setFeed(double feed) {
        this.feed = feed;
    }

    public double getStandingCharges() {
        return standingCharges;
    }

    public void setStandingCharges(double standingCharges) {
        this.standingCharges = standingCharges;
    }

    public double getSignUpBonus() {
        return signUpBonus;
    }

    public void setSignUpBonus(double signUpBonus) {
        this.signUpBonus = signUpBonus;
    }

    @NonNull
    public String getReference() {
        return reference;
    }

    public void setReference(@NonNull String reference) {
        this.reference = reference;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @NonNull
    public String getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(@NonNull String lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public Restrictions getRestrictions() {
        return restrictions;
    }

    public void setRestrictions(Restrictions restrictions) {
        this.restrictions = restrictions;
    }

    public PricePlan copy() {
        PricePlan copy = new PricePlan();
        copy.supplier = supplier;
        copy.planName = planName + " (Copy)";
        copy.feed = feed;
        copy.standingCharges = standingCharges;
        copy.signUpBonus = signUpBonus;
        copy.lastUpdate = lastUpdate;
        copy.reference = reference;
        copy.active = active;
        copy.pricePlanIndex = 0;
        copy.restrictions = restrictions;
        return copy;
    }

    public static final int VALID_PLAN = 0;
    public static final int INVALID_PLAN_DUPLICATE_DAYS = 1;
    public static final int INVALID_PLAN_MISSING_DAYS = 2;
    public static final int INVALID_PLAN_OVERLAPPING_DATE_RANGES = 3;
    public static final int INVALID_PLAN_MISSING_DATES = 4;
    public static final int INVALID_PLAN_BAD_DATE_FORMAT = 5;
    public static final int INVALID_PLAN_NAME_IN_USE = 6;
    public static final int INVALID_PLAN_NO_DAY_RATES = 7;
    public static final int INVALID_PLAN_END_BEFORE_START = 8;
    public static final int INVALID_PLAN_MISSING_MINUTES = 9;
    public int validatePlan(List<DayRate> drs) {
        if (drs.isEmpty()) return INVALID_PLAN_NO_DAY_RATES;
        Map<String, LocalDate[]> dtRanges = new HashMap<>();
        Map<String, List<Integer>> dtDays = new HashMap<>();
        for (DayRate dr : drs) {
            switch (dr.validate()) {
                case DayRate.DR_BAD_END:
                case DayRate.DR_BAD_START:
                    return INVALID_PLAN_BAD_DATE_FORMAT;
                case DayRate.DR_END_BEFORE_START:
                    return INVALID_PLAN_END_BEFORE_START;
            }
            String drKey = dr.getKey();
            dtRanges.put(drKey, dr.get2001DateRange());
            if (dtDays.containsKey(drKey)) {
                List<Integer> daysSoFar = dtDays.get(drKey);
                // check for duplicate days
                for (Integer i: dr.getDays().ints){
                    if (daysSoFar != null) {
                        if (daysSoFar.contains(i)) return INVALID_PLAN_DUPLICATE_DAYS;
                        else daysSoFar.add(i);
                    }
                }
                dtDays.put(drKey, daysSoFar);
            }
            else dtDays.put(drKey, dr.getDays().getCopyOfInts());
        }
        // Check for missing days
        List<Integer> allDays = new ArrayList<>(Arrays.asList(0,1,2,3,4,5,6));
        for (List<Integer> daysInADateRange: dtDays.values()){
            if (!(new HashSet<>(daysInADateRange).containsAll(allDays))) {
                return INVALID_PLAN_MISSING_DAYS;
            }
        }
        // Check for missing dates
        List<Integer> datesSoFar = new ArrayList<>();
        for (LocalDate[] cal: dtRanges.values()){
            int start = cal[0].getDayOfYear();
            int end = cal[1].getDayOfYear();
            for (int day = start; day <= end; day++){
                if (datesSoFar.contains(day)) return INVALID_PLAN_OVERLAPPING_DATE_RANGES;
                else datesSoFar.add(day);
            }
        }
        // Check for overlapping date ranges
        List<Integer> fullYear = new ArrayList<>();
        for (int i = 1; i <= 365; i++) fullYear.add(i);
        if (!new HashSet<>(datesSoFar).containsAll(fullYear)) return INVALID_PLAN_MISSING_DATES;
        // Check that each DayRate covers all 1440 minutes
        for (DayRate dr : drs) {
            int maxRange = 0;
            for (RangeRate rr : dr.getMinuteRateRange().getRates()) {
                maxRange = max(rr.getEnd(), maxRange);
            }
            if (maxRange < 1439) return INVALID_PLAN_MISSING_MINUTES;
        }
        return VALID_PLAN;
    }

    public int checkNameUsageIn(Set<PricePlan> plans){
        for (PricePlan pp: plans) {
            if (this.equals(pp) && this.pricePlanIndex != pp.getPricePlanIndex()) {
                return INVALID_PLAN_NAME_IN_USE;
            }
        }
        return VALID_PLAN;
    }

    public static String getInvalidReason(int reasonCode) {
        switch (reasonCode) {
            case INVALID_PLAN_DUPLICATE_DAYS:
                return "Conflicting days in date ranges";
            case INVALID_PLAN_MISSING_DAYS:
                return "At least one date range is missing a day";
            case INVALID_PLAN_OVERLAPPING_DATE_RANGES:
                return "Date ranges cannot overlap";
            case INVALID_PLAN_MISSING_DATES:
                return "The sum of date ranges does not cover the year";
            case INVALID_PLAN_BAD_DATE_FORMAT:
                return "A date range does not follow MM/DD";
            case INVALID_PLAN_NAME_IN_USE:
                return "The plan name is used by another plan";
            case INVALID_PLAN_NO_DAY_RATES:
                return "The plan must include at least one day rate";
            case INVALID_PLAN_END_BEFORE_START:
                return "Day rates must end after start";
            case INVALID_PLAN_MISSING_MINUTES:
                return "Each day must have a price for every minute (0-1439)";
            default:
                return "Unknown reason for invalidity";
        }
    }
}
