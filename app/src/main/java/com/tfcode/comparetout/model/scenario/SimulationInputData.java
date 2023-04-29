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

import androidx.room.ColumnInfo;

public class SimulationInputData {
    @ColumnInfo(name= "date") public String date;
    @ColumnInfo(name= "minute") public String minute;
    @ColumnInfo(name= "load") public double load = 0D;
    @ColumnInfo(name= "mod") public int mod = 0;
    @ColumnInfo(name= "dow") public int dow = 0;
    @ColumnInfo(name= "do2001") public int do2001 = 0;
    @ColumnInfo(name= "TPV") public double tpv = 0D;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getMinute() {
        return minute;
    }

    public void setMinute(String minute) {
        this.minute = minute;
    }

    public double getLoad() {
        return load;
    }

    public void setLoad(double load) {
        this.load = load;
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

    public double getTpv() {
        return tpv;
    }

    public void setTpv(double tpv) {
        this.tpv = tpv;
    }
}
