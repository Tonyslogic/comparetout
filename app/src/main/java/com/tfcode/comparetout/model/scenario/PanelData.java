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
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

@Entity(tableName = "paneldata", primaryKeys = {"panelID", "date", "minute"})
public class PanelData {

    // LOCAL TIME -- transform before writing!!

    private long panelID;
    @NonNull
    private String date = ""; // YYY-MM-DD
    @NonNull
    private String minute = ""; // HH:mm
    private double pv;
    private int mod; // 0-1435
    private int dow; // the day-of-week, from 1 (Monday) to 7 (Sunday) https://docs.oracle.com/javase/8/docs/api/java/time/LocalDate.html
    private int do2001;
    @ColumnInfo(defaultValue = "NULL")
    private Integer millisSinceEpoch;

    public Integer getMillisSinceEpoch() {
        return millisSinceEpoch;
    }

    public void setMillisSinceEpoch(int millisSinceEpoch) {
        this.millisSinceEpoch = millisSinceEpoch;
    }

    public long getPanelID() {
        return panelID;
    }

    public void setPanelID(long panelID) {
        this.panelID = panelID;
    }

    @NonNull
    public String getDate() {
        return date;
    }

    public void setDate(@NonNull String date) {
        this.date = date;
    }

    @NonNull
    public String getMinute() {
        return minute;
    }

    public void setMinute(@NonNull String minute) {
        this.minute = minute;
    }

    public double getPv() {
        return pv;
    }

    public void setPv(double pv) {
        this.pv = pv;
    }

    public int getMod() {
        return mod;
    }

    public void setMod(int mod) {
        this.mod = mod;
    }

    public int getDow() {
        return dow;
    }

    public void setDow(int dow) {
        this.dow = dow;
    }

    public int getDo2001() {
        return do2001;
    }

    public void setDo2001(int do2001) {
        this.do2001 = do2001;
    }
}
