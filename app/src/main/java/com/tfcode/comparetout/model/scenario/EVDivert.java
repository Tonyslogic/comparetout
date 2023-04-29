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

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.tfcode.comparetout.model.IntHolder;

@Entity(tableName = "evdivert")
public class EVDivert {

    @PrimaryKey(autoGenerate = true)
    private long evDivertIndex;

    private String name = "Afternoon-nap";
    private boolean active = true;
    private boolean ev1st = true;
    private int begin = 11;
    private int end = 16;
    private double dailyMax = 16.0;
    private MonthHolder months = new MonthHolder();
    private IntHolder days = new IntHolder();

    public long getEvDivertIndex() {
        return evDivertIndex;
    }

    public void setEvDivertIndex(long evDivertIndex) {
        this.evDivertIndex = evDivertIndex;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isEv1st() {
        return ev1st;
    }

    public void setEv1st(boolean ev1st) {
        this.ev1st = ev1st;
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

    public double getDailyMax() {
        return dailyMax;
    }

    public void setDailyMax(double dailyMax) {
        this.dailyMax = dailyMax;
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
