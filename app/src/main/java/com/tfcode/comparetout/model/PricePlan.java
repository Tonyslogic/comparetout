package com.tfcode.comparetout.model;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity(tableName = "PricePlans")
public class PricePlan {
    @PrimaryKey(autoGenerate = true)
    private long id;
    @NonNull private String supplier = "<SUPPLIER>";
    @NonNull private String planName = "<PLAN>";
    private double feed = 0.0;
    private double standingCharges = 0.0;
    private double signUpBonus = 0.0;
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


    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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
        copy.id = 0;
        return copy;
    }

    public static final int VALID_PLAN = 0;
    public static final int INVALID_PLAN_DUPLICATE_DAYS = 1;
    public static final int INVALID_PLAN_MISSING_DAYS = 2;
    public static final int INVALID_PLAN_OVERLAPPING_DATE_RANGES = 3;
    public static final int INVALID_PLAN_MISSING_DATES = 4;
    public static final int INVALID_PLAN_BAD_DATE_FORMAT = 5;
    public static final int INVALID_PLAN_NAME_IN_USE = 6; //??
    public int validatePlan(List<DayRate> drs) {
        Map<String, Calendar[]> dtRanges = new HashMap<>();
        Map<String, List<Integer>> dtDays = new HashMap<>();
        for (DayRate dr : drs) {
            switch (dr.validate()) {
                case DayRate.DR_BAD_END:
                case DayRate.DR_BAD_START:
                    return INVALID_PLAN_BAD_DATE_FORMAT;
            }
            String drKey = dr.getKey();
            dtRanges.put(drKey, dr.get2001DateRange());
            if (dtDays.containsKey(drKey)) {
                List<Integer> daysSoFar = dtDays.get(drKey);
                // check for duplicate days
                for (Integer i: dr.getDays().ints){
                    if (daysSoFar.contains(i)) return INVALID_PLAN_DUPLICATE_DAYS;
                    else daysSoFar.add(i);
                }
                dtDays.put(drKey, daysSoFar);
            }
            else dtDays.put(drKey, dr.getDays().getCopy());
        }
        // Check for missing days
        List<Integer> allDays = new ArrayList<>(Arrays.asList(0,1,2,3,4,5,6));
        for (List<Integer> daysInADateRange: dtDays.values()){
            if (!(daysInADateRange.containsAll(allDays))) {
                return INVALID_PLAN_MISSING_DAYS;
            }
        }
        // Check for missing dates
        List<Integer> datesSoFar = new ArrayList<>();
        for (Calendar[] cal: dtRanges.values()){
            int start = cal[0].get(Calendar.DAY_OF_YEAR);
            int end = cal[1].get(Calendar.DAY_OF_YEAR);
            for (int day = start; day <= end; day++){
                if (datesSoFar.contains(day)) return INVALID_PLAN_OVERLAPPING_DATE_RANGES;
                else datesSoFar.add(day);
            }
        }
        // Check for overlapping date ranges
        List<Integer> fullYear = new ArrayList<>();
        for (int i = 1; i <= 365; i++) fullYear.add(i);
        if (!datesSoFar.containsAll(fullYear)) return INVALID_PLAN_MISSING_DATES;
        return VALID_PLAN;
    }

    public int checkNameUsageIn(Set<PricePlan> plans){
        for (PricePlan pp: plans) {
            if (this.equals(pp) && this.id != pp.getId()) {
                System.out.println("PP " + pp.supplier + " " + pp.planName + " " + pp.id);
                return INVALID_PLAN_NAME_IN_USE;
            }
        }
        return VALID_PLAN;
    }
}
