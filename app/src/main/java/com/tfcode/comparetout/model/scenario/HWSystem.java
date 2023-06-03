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

import static java.lang.Math.max;
import static java.lang.Math.min;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.List;

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

    public Heat heatWater(int mod, double previousWaterTemp, double availableKWH) {
        Heat heat = new Heat();
        heat.temperature = max(previousWaterTemp, hwIntake);
        heat.kWhUsed = 0;
        // Reduce heat 1st
        if (mod % 60 == 0) {
            // Reduce heat as specified in hwLoss
            heat.temperature = max(hwIntake, heat.temperature - hwLoss/24d);
            // Reduce heat as specified in hwUse
            int hour = mod / 60;
            double usage = 0;
            for (List<Double> use : hwUse.getUsage()) {
                if (use.get(0) == hour) {
                    usage = hwUsage * (use.get(1)/100d);
                    break;
                }
            }
            double m1 = hwCapacity - usage;
            double m2 = usage;
            double temp = (m1 * heat.temperature + m2 * hwIntake) / (m1 + m2);
            heat.temperature = max(hwIntake, temp);
        }
        // The most that could be drawn (kWH) to get to the target temp
        double potentialDiversion = (hwCapacity * 1000) * 4.2d * (hwTarget - heat.temperature);
        potentialDiversion = min(potentialDiversion, availableKWH);
        potentialDiversion = min((hwRate / 12d), potentialDiversion);
        // Cannot be negative
        heat.kWhUsed = max(0, potentialDiversion);
        heat.temperature = ((heat.kWhUsed * 3600000) / (hwCapacity * 4200d)) + heat.temperature;

        return heat;
    }

    public static class Heat {
        public double temperature = 0;
        public double kWhUsed = 0;
    }
}
