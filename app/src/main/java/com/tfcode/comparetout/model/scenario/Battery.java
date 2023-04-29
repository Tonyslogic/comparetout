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

@Entity(tableName = "batteries")
public class Battery {

    @PrimaryKey(autoGenerate = true)
    private long batteryIndex;

    private double batterySize = 5.7;
    private double dischargeStop = 19.6;
    private ChargeModel chargeModel = new ChargeModel();
    private double maxDischarge = 0.225;
    private double maxCharge = 0.225;
    private double storageLoss = 1.0;
    private String inverter = "AlphaESS";

    public long getBatteryIndex() {
        return batteryIndex;
    }

    public void setBatteryIndex(long batteryIndex) {
        this.batteryIndex = batteryIndex;
    }

    public double getBatterySize() {
        return batterySize;
    }

    public void setBatterySize(double batterySize) {
        this.batterySize = batterySize;
    }

    public double getDischargeStop() {
        return dischargeStop;
    }

    public void setDischargeStop(double dischargeStop) {
        this.dischargeStop = dischargeStop;
    }

    public ChargeModel getChargeModel() {
        return chargeModel;
    }

    public void setChargeModel(ChargeModel chargeModel) {
        this.chargeModel = chargeModel;
    }

    public double getMaxDischarge() {
        return maxDischarge;
    }

    public void setMaxDischarge(double maxDischarge) {
        this.maxDischarge = maxDischarge;
    }

    public double getMaxCharge() {
        return maxCharge;
    }

    public void setMaxCharge(double maxCharge) {
        this.maxCharge = maxCharge;
    }

    public double getStorageLoss() {
        return storageLoss;
    }

    public void setStorageLoss(double storageLoss) {
        this.storageLoss = storageLoss;
    }

    public String getInverter() {
        return inverter;
    }

    public void setInverter(String inverter) {
        this.inverter = inverter;
    }
}
