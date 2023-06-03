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

@Entity(tableName = "hwschedule")
public class HWSchedule {

    @PrimaryKey(autoGenerate = true)
    private long hwScheduleIndex;

    private String name = "Midnight-water";
    private int begin = 2;
    private int end = 6;
    private MonthHolder months = new MonthHolder();
    private IntHolder days = new IntHolder();

    public long getHwScheduleIndex() {
        return hwScheduleIndex;
    }

    public void setHwScheduleIndex(long hwScheduleIndex) {
        this.hwScheduleIndex = hwScheduleIndex;
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

    public boolean equalDate(HWSchedule other) {
        if (this == other) return true;
        return this.months.equals(other.getMonths()) &&
                this.days.equals(other.getDays());
    }
}
