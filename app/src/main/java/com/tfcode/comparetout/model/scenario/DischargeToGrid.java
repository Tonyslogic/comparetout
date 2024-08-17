/*
 * Copyright (c) 2024. Tony Finnerty
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

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.tfcode.comparetout.model.IntHolder;

@Entity(tableName = "discharge2grid")
public class DischargeToGrid {

    @PrimaryKey(autoGenerate = true)
    private long d2gIndex;

    private String name = "Load-shift-name";
    private int begin = 23;
    private int end = 2;
    private double stopAt = 0d;
    private double rate = 0.225d;
    private MonthHolder months = new MonthHolder();
    private IntHolder days = new IntHolder();
    private String inverter = "AlphaESS";

    public long getD2gIndex() {
        return d2gIndex;
    }

    public void setD2gIndex(long d2gIndex) {
        this.d2gIndex = d2gIndex;
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

    public double getRate() {
        return rate;
    }

    public void setRate(double rate) {
        this.rate = rate;
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

    public String getInverter() {
        return inverter;
    }

    public void setInverter(String inverter) {
        this.inverter = inverter;
    }

    public boolean equalDate(DischargeToGrid other) {
        if (this == other) return true;
        return this.months.equals(other.getMonths()) &&
                this.days.equals(other.getDays());
    }
}
