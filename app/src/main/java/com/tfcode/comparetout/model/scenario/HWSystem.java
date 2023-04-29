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

@Entity(tableName = "hwsystem")
public class HWSystem {

    @PrimaryKey(autoGenerate = true)
    private long hwSystemIndex;

    private int hwCapacity = 165;
    private int hwUsage = 200;
    private int hwIntake = 15;
    private int hwTarget = 75;
    private int hwLoss = 8;
    private double hwRate = 2.5;
    private HWUse hwUse = new HWUse();

    public long getHwSystemIndex() {
        return hwSystemIndex;
    }

    public void setHwSystemIndex(long hwSystemIndex) {
        this.hwSystemIndex = hwSystemIndex;
    }

    public int getHwCapacity() {
        return hwCapacity;
    }

    public void setHwCapacity(int hwCapacity) {
        this.hwCapacity = hwCapacity;
    }

    public int getHwUsage() {
        return hwUsage;
    }

    public void setHwUsage(int hwUsage) {
        this.hwUsage = hwUsage;
    }

    public int getHwIntake() {
        return hwIntake;
    }

    public void setHwIntake(int hwIntake) {
        this.hwIntake = hwIntake;
    }

    public int getHwTarget() {
        return hwTarget;
    }

    public void setHwTarget(int hwTarget) {
        this.hwTarget = hwTarget;
    }

    public int getHwLoss() {
        return hwLoss;
    }

    public void setHwLoss(int hwLoss) {
        this.hwLoss = hwLoss;
    }

    public double getHwRate() {
        return hwRate;
    }

    public void setHwRate(double hwRate) {
        this.hwRate = hwRate;
    }

    public HWUse getHwUse() {
        return hwUse;
    }

    public void setHwUse(HWUse hwUse) {
        this.hwUse = hwUse;
    }
}
