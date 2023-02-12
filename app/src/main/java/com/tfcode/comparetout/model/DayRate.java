package com.tfcode.comparetout.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.GregorianCalendar;

@Entity(tableName = "DayRates")
public class DayRate {

    @PrimaryKey(autoGenerate = true)
    private long id ;

    private long pricePlanId = 0L;

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

    public static final int DR_OK = 0;
    public static final int DR_BAD_START = 1;
    public static final int DR_BAD_END = 2;
    public static final int DR_END_BEFOR_START = 3;
    public int validate() {
        try {
            getMonthDay(startDate);
        } catch (Exception e) {
            return DR_BAD_START;
        }
        try {
            getMonthDay(endDate);
        } catch (Exception e) {
            return DR_BAD_END;
        }
        LocalDate[] ld = get2001DateRange();
        if (ld[1].getDayOfYear() < ld[0].getDayOfYear())
            return DR_END_BEFOR_START;
        return DR_OK;
    }

    public int[] getMonthDay(@NonNull String startDate) {
        String[] numbers = startDate.split("/");
        int[] ints = new int[numbers.length];
        for (int c = 0; c < numbers.length; c++) {
            ints[c] = Integer.parseInt(numbers[c]);
        }
        return ints;
    }

//    public Calendar[] get2001DateRange() {
//        int[] dm = getMonthDay(getStartDate());
//        Calendar start = new GregorianCalendar(2001, dm[0] - 1, dm[1]);
//        if ((dm[0] == 2) && (dm[1] == 29)) start = new GregorianCalendar(2001, 1, 28);
//        dm = getMonthDay(getEndDate());
//        Calendar end = new GregorianCalendar(2001, dm[0] - 1, dm[1]);
//        if (dm[0] == 2 && dm[1] == 29) end = new GregorianCalendar(2001,1,28);
//        return new Calendar[]{start, end};
//    }

    public LocalDate[] get2001DateRange() {
        int[] dm = getMonthDay(getStartDate());
        LocalDate start = LocalDate.of(2001, dm[0], dm[1]);
        if ((dm[0] == 2) && (dm[1] == 29)) start = LocalDate.of(2001, 2, 28);
        dm = getMonthDay(getEndDate());
        LocalDate end = LocalDate.of(2001, dm[0], dm[1]);
        if ((dm[0] == 2) && (dm[1] == 29)) end = LocalDate.of(2001, 2, 28);
        return new LocalDate[]{start, end};
    }

    public String getKey() {
        return startDate + "," + endDate;
    }

}
