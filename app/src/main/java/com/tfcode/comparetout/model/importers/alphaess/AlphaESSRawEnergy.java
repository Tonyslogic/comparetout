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
import androidx.room.Entity;

@Entity(tableName = "alphaESSRawEnergy", primaryKeys = {"sysSn", "theDate"})
public class AlphaESSRawEnergy {
    @NonNull
    private String sysSn = "";
    @NonNull
    private String theDate = ""; //2023-08-26"
    private double energyCharge;
    private double energypv;
    private double energyOutput;
    private double energyInput;
    private double energyGridCharge;
    private double energyDischarge;
    private double energyChargingPile;

    @NonNull
    public String getSysSn() {
        return sysSn;
    }

    public void setSysSn(@NonNull String sysSn) {
        this.sysSn = sysSn;
    }

    @NonNull
    public String getTheDate() {
        return theDate;
    }

    public void setTheDate(@NonNull String theDate) {
        this.theDate = theDate;
    }

    public double getEnergyCharge() {
        return energyCharge;
    }

    public void setEnergyCharge(double energyCharge) {
        this.energyCharge = energyCharge;
    }

    public double getEnergypv() {
        return energypv;
    }

    public void setEnergypv(double energypv) {
        this.energypv = energypv;
    }

    public double getEnergyOutput() {
        return energyOutput;
    }

    public void setEnergyOutput(double energyOutput) {
        this.energyOutput = energyOutput;
    }

    public double getEnergyInput() {
        return energyInput;
    }

    public void setEnergyInput(double energyInput) {
        this.energyInput = energyInput;
    }

    public double getEnergyGridCharge() {
        return energyGridCharge;
    }

    public void setEnergyGridCharge(double energyGridCharge) {
        this.energyGridCharge = energyGridCharge;
    }

    public double getEnergyDischarge() {
        return energyDischarge;
    }

    public void setEnergyDischarge(double energyDischarge) {
        this.energyDischarge = energyDischarge;
    }

    public double getEnergyChargingPile() {
        return energyChargingPile;
    }

    public void setEnergyChargingPile(double energyChargingPile) {
        this.energyChargingPile = energyChargingPile;
    }
}
