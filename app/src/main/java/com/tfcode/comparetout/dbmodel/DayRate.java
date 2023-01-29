package com.tfcode.comparetout.dbmodel;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.tfcode.comparetout.priceplan.DayRateJson;

@Entity(tableName = "DayRates")
public class DayRate {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull private long pricePlanId;

    @NonNull private IntHolder days;
    @NonNull private DoubleHolder hours;
    @NonNull private String startDate;
    @NonNull private String endDate;

    public DayRate(DayRateJson drj) {
        if (drj.endDate == null) setEndDate("12/31");
        else setEndDate(drj.endDate);
        if (drj.startDate == null) setStartDate("01/01");
        else setStartDate(drj.startDate);
        IntHolder ih = new IntHolder();
        ih.ints = drj.days;
        setDays(ih);
        DoubleHolder dh = new DoubleHolder();
        dh.doubles = drj.hours;
        setHours(dh);
    }

    public DayRate() {

    }

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
