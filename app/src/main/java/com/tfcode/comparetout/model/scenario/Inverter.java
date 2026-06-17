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

@Entity(tableName = "inverters")
public class Inverter implements Comparable<Inverter> {

    /** Dispatch order: serve load, then discharge the battery, then import from the grid (legacy default). */
    public static final int DISPATCH_LOAD_BATTERY_GRID = 0;
    /** Dispatch order: serve load, then import from the grid, preserving the battery. */
    public static final int DISPATCH_LOAD_GRID_BATTERY = 1;

    @PrimaryKey(autoGenerate = true)
    private long inverterIndex;

    @NonNull
    private String inverterName = "<INVERTER>";

    private double minExcess = 0.008;
    private double maxInverterLoad = 5.0;
    private int mpptCount = 2;
    private int ac2dcLoss = 5;
    private int dc2acLoss = 5;
    private int dc2dcLoss = 0;

    // Per-inverter dispatch mode (Phase 4). Defaults to the legacy load->battery->grid order so existing
    // scenarios are unchanged; the @ColumnInfo default lets Room auto-migrate the new NOT NULL column.
    @ColumnInfo(defaultValue = "0")
    private int dispatchMode = DISPATCH_LOAD_BATTERY_GRID;

    public long getInverterIndex() {
        return inverterIndex;
    }

    public void setInverterIndex(long inverterIndex) {
        this.inverterIndex = inverterIndex;
    }

    @NonNull
    public String getInverterName() {
        return inverterName;
    }

    public void setInverterName(@NonNull String inverterName) {
        this.inverterName = inverterName;
    }

    public double getMinExcess() {
        return minExcess;
    }

    public void setMinExcess(double minExcess) {
        this.minExcess = minExcess;
    }

    public double getMaxInverterLoad() {
        return maxInverterLoad;
    }

    public void setMaxInverterLoad(double maxInverterLoad) {
        this.maxInverterLoad = maxInverterLoad;
    }

    public int getMpptCount() {
        return mpptCount;
    }

    public void setMpptCount(int mpptCount) {
        this.mpptCount = mpptCount;
    }

    public int getAc2dcLoss() {
        return ac2dcLoss;
    }

    public void setAc2dcLoss(int ac2dcLoss) {
        this.ac2dcLoss = ac2dcLoss;
    }

    public int getDc2acLoss() {
        return dc2acLoss;
    }

    public void setDc2acLoss(int dc2acLoss) {
        this.dc2acLoss = dc2acLoss;
    }

    public int getDc2dcLoss() {
        return dc2dcLoss;
    }

    public void setDc2dcLoss(int dc2dcLoss) {
        this.dc2dcLoss = dc2dcLoss;
    }

    public int getDispatchMode() {
        return dispatchMode;
    }

    public void setDispatchMode(int dispatchMode) {
        this.dispatchMode = dispatchMode;
    }

    @Override
    public int compareTo(Inverter o) {
        return Long.compare(this.getInverterIndex(), o.getInverterIndex());
    }
}
