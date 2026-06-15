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

package com.tfcode.comparetout.model.importers.alphaess;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

@Entity(tableName = "alphaESSTransformedData", primaryKeys = {"sysSn", "date", "minute"})
public class AlphaESSTransformedData {
    @NonNull
    private String sysSn = "";
    @NonNull
    private String date = ""; // YYY-MM-DD
    @NonNull
    private String minute = ""; // HH:mm
    private double pv;
    private double load;
    private double feed;
    private double buy;
    @ColumnInfo(defaultValue = "0")
    private double charge = 0;
    @ColumnInfo(defaultValue = "NULL")
    private Long millisSinceEpoch;

    // v2 flow decomposition (populated by AlphaESSFlowDecomposer; 0 for v1 rows).
    @ColumnInfo(defaultValue = "0") private double pv2load;
    @ColumnInfo(defaultValue = "0") private double pv2bat;
    @ColumnInfo(defaultValue = "0") private double pv2grid;
    @ColumnInfo(defaultValue = "0") private double bat2load;
    @ColumnInfo(defaultValue = "0") private double bat2grid;
    @ColumnInfo(defaultValue = "0") private double grid2load;
    @ColumnInfo(defaultValue = "0") private double grid2bat;
    @ColumnInfo(defaultValue = "0") private double evActual;
    @ColumnInfo(defaultValue = "0") private double batChargeIn;
    @ColumnInfo(defaultValue = "0") private double batDischargeOut;

    public Long getMillisSinceEpoch() {
        return millisSinceEpoch;
    }

    public void setMillisSinceEpoch(Long millisSinceEpoch) {
        this.millisSinceEpoch = millisSinceEpoch;
    }

    public double getCharge() {
        return charge;
    }

    public void setCharge(double charge) {
        this.charge = charge;
    }

    @NonNull
    public String getSysSn() {
        return sysSn;
    }

    public void setSysSn(@NonNull String sysSn) {
        this.sysSn = sysSn;
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

    public double getLoad() {
        return load;
    }

    public void setLoad(double load) {
        this.load = load;
    }

    public double getFeed() { return feed; }

    public void setFeed(double feed) { this.feed = feed;}

    public double getBuy() { return buy; }

    public void setBuy(double buy) { this.buy = buy; }

    public double getPv2load() { return pv2load; }
    public void setPv2load(double pv2load) { this.pv2load = pv2load; }

    public double getPv2bat() { return pv2bat; }
    public void setPv2bat(double pv2bat) { this.pv2bat = pv2bat; }

    public double getPv2grid() { return pv2grid; }
    public void setPv2grid(double pv2grid) { this.pv2grid = pv2grid; }

    public double getBat2load() { return bat2load; }
    public void setBat2load(double bat2load) { this.bat2load = bat2load; }

    public double getBat2grid() { return bat2grid; }
    public void setBat2grid(double bat2grid) { this.bat2grid = bat2grid; }

    public double getGrid2load() { return grid2load; }
    public void setGrid2load(double grid2load) { this.grid2load = grid2load; }

    public double getGrid2bat() { return grid2bat; }
    public void setGrid2bat(double grid2bat) { this.grid2bat = grid2bat; }

    public double getEvActual() { return evActual; }
    public void setEvActual(double evActual) { this.evActual = evActual; }

    public double getBatChargeIn() { return batChargeIn; }
    public void setBatChargeIn(double batChargeIn) { this.batChargeIn = batChargeIn; }

    public double getBatDischargeOut() { return batDischargeOut; }
    public void setBatDischargeOut(double batDischargeOut) { this.batDischargeOut = batDischargeOut; }
}
