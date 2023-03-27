package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.tfcode.comparetout.model.IntHolder;

@Entity(tableName = "loadshift")
public class LoadShift {

    @PrimaryKey(autoGenerate = true)
    private long loadShiftIndex;

    private String name = "Load-shift-name";
    private int begin = 2;
    private int end = 6;
    private double stopAt = 80d;
    private MonthHolder months = new MonthHolder();
    private IntHolder days = new IntHolder();

    public long getLoadShiftIndex() {
        return loadShiftIndex;
    }

    public void setLoadShiftIndex(long loadShiftIndex) {
        this.loadShiftIndex = loadShiftIndex;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getBegin() {
        return begin;
    }

    public void setBegin(int begin) {
        this.begin = begin;
    }

    public int getEnd() {
        return end;
    }

    public void setEnd(int end) {
        this.end = end;
    }

    public double getStopAt() {
        return stopAt;
    }

    public void setStopAt(double stopAt) {
        this.stopAt = stopAt;
    }

    public MonthHolder getMonths() {
        return months;
    }

    public void setMonths(MonthHolder months) {
        this.months = months;
    }

    public IntHolder getDays() {
        return days;
    }

    public void setDays(IntHolder days) {
        this.days = days;
    }
}
