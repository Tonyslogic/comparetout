package com.tfcode.comparetout.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "DayRates")
public class DayRate {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull private Long pricePlanId;

    @NonNull private IntHolder days = new IntHolder();
    @NonNull private DoubleHolder hours = new DoubleHolder();
    @NonNull private String startDate = "01/01";
    @NonNull private String endDate = "12/31";

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
    public long getPricePlanId() {
        return pricePlanId;
    }

    public void setPricePlanId(long pricePlanId) {
        this.pricePlanId = pricePlanId;
    }

    @NonNull
    public IntHolder getDays() {
        return days;
    }

    public void setDays(@NonNull IntHolder days) {
        this.days = days;
    }

    @NonNull
    public DoubleHolder getHours() {
        return hours;
    }

    public void setHours(@NonNull DoubleHolder hours) {
        this.hours = hours;
    }

    @NonNull
    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(@NonNull String startDate) {
        this.startDate = startDate;
    }

    @NonNull
    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(@NonNull String endDate) {
        this.endDate = endDate;
    }

}