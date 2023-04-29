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

@Entity(tableName = "evcharge")
public class EVCharge {

    @PrimaryKey(autoGenerate = true)
    private long evChargeIndex;

    private String name = "EV Schedule";
    private int begin = 2;
    private int end = 6;
    private double draw = 7.5;
    private MonthHolder months = new MonthHolder();
    private IntHolder days = new IntHolder();

    public long getEvChargeIndex() {
        return evChargeIndex;
    }

    public void setEvChargeIndex(long evChargeIndex) {
        this.evChargeIndex = evChargeIndex;
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

    public double getDraw() {
        return draw;
    }

    public void setDraw(double draw) {
        this.draw = draw;
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
