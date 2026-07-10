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

package com.tfcode.comparetout.model.scenario;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
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
    private String inverter = "AlphaESS";
    // v16 date-aware, minute-granular scheduling (MM/DD, DayRate semantics).
    // Defaults reproduce pre-v16 behaviour exactly: full year, window derived
    // from the legacy whole-hour begin/end. Dormant until the engine wires them.
    @NonNull
    @ColumnInfo(defaultValue = "01/01")
    private String startDate = "01/01";
    @NonNull
    @ColumnInfo(defaultValue = "12/31")
    private String endDate = "12/31";
    @ColumnInfo(defaultValue = "-1")
    private int beginMinute = -1;
    @ColumnInfo(defaultValue = "-1")
    private int endMinute = -1;

    public String getInverter() {
        return inverter;
    }

    public void setInverter(String inverter) {
        this.inverter = inverter;
    }

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

    public int getBeginMinute() {
        return beginMinute;
    }

    public void setBeginMinute(int beginMinute) {
        this.beginMinute = beginMinute;
    }

    public int getEndMinute() {
        return endMinute;
    }

    public void setEndMinute(int endMinute) {
        this.endMinute = endMinute;
    }

    /** Minute-of-day window start; -1 falls back to the legacy whole-hour begin. */
    public int getEffectiveBeginMinute() {
        return beginMinute >= 0 ? beginMinute : begin * 60;
    }

    /**
     * Minute-of-day window end (exclusive); -1 falls back to the legacy hours.
     * The engine's legacy check is {@code begin <= hour && hour <= end} — the
     * end hour is INCLUDED — so the derived window ends at {@code (end+1)*60}.
     */
    public int getEffectiveEndMinute() {
        return endMinute >= 0 ? endMinute : (end + 1) * 60;
    }

    public boolean equalDateAndInverter(LoadShift other) {
        if (this == other) return true;
        return this.inverter.equals(other.getInverter()) &&
                this.months.equals(other.getMonths()) &&
                this.days.equals(other.getDays()) &&
                this.startDate.equals(other.getStartDate()) &&
                this.endDate.equals(other.getEndDate());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LoadShift loadShift = (LoadShift) o;
        return (loadShiftIndex == loadShift.loadShiftIndex && this.equalDateAndInverter((LoadShift) o));
    }

    @NonNull
    @Override
    public String toString() {
        return "LoadShift{" +
                "loadShiftIndex=" + loadShiftIndex +
                ", name='" + name + '\'' +
                ", begin=" + begin +
                ", end=" + end +
                ", stopAt=" + stopAt +
                ", months=" + months +
                ", days=" + days +
                ", inverter='" + inverter + '\'' +
                '}';
    }
}
