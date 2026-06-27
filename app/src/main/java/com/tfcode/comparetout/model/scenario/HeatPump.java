/*
 * Copyright (c) 2026. Tony Finnerty
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
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * A heat-pump configuration (Phase 4 of {@code plans/hp/plan.md}). Persisted user inputs for the
 * fuel-anchored demand model; the field set mirrors {@code HeatPumpDemandModel.Config} so the engine
 * mapping (Phase 4d, in the component registry) is one-to-one. Defaults match the model's defaults.
 *
 * <p>Distributions reuse {@link HourlyDist} / {@link DOWDist} (percentage weights; the model treats them as
 * relative). The heating-season window is an optional day-of-year pair (null ⇒ year-round). Location is
 * carried here so the heat pump can fetch weather even when the scenario has no PV; {@code weatherSource}
 * selects the offline sample asset vs a live CDS fetch.</p>
 */
@Entity(tableName = "heatpumps")
public class HeatPump {

    @PrimaryKey(autoGenerate = true)
    private long heatPumpIndex;

    // (1) fuel → delivered heat
    private String fuelType = "Kerosene/Oil";   // Kerosene/Oil · Natural gas · LPG · None (new build)
    private double fuelAnnual = 2300d;           // litres/yr (oil/LPG) or kWh/yr (gas)
    private double calorificValue = 10.35d;      // kWh per unit
    private double boilerEfficiency = 0.80d;     // old-boiler seasonal efficiency
    private double dhwAnnualKWh = 2000d;         // fixed DHW carve-out (basic mode)
    private Double spaceHeatingFraction = null;  // advanced: overrides the fixed DHW subtraction when set

    // (1b) fabric anchor (new build, fuelType "None"): when both > 0 they replace the fuel anchor, deriving
    // annual space heat from the building fabric. @ColumnInfo defaults keep the v9→v10 AutoMigration buildable.
    @ColumnInfo(defaultValue = "0")
    private double floorAreaM2 = 0d;             // heated floor area (m²); 0 ⇒ use the fuel anchor
    @ColumnInfo(defaultValue = "0")
    private double heatLossIndex = 0d;           // whole-house HLI (W/K/m², fabric + ventilation); 0 ⇒ fuel anchor

    // (2) setpoint scale
    private double desiredIndoorTemp = 20d;      // setpointNew
    private double currentIndoorTemp = 20d;      // setpointOld (== desired ⇒ no scaling)

    // (3) redistribution
    private double balancePoint = 15.5d;         // T_base
    private double alphaWind = 0.03d;            // wind-infiltration coefficient per m/s
    private HourlyDist hourlyDist = new HourlyDist();
    private DOWDist dowDist = new DOWDist();
    private Integer heatingSeasonStart = null;   // day-of-year (1..365), inclusive; null ⇒ year-round
    private Integer heatingSeasonEnd = null;     // day-of-year (1..365), inclusive

    // (4) COP
    private double copRated = 4.2d;
    private double copRefTemp = 7d;
    private double copSlope = 0.08d;
    private double scop = 3.6d;

    // (5) capacity
    private double capacityKw = 7d;
    private boolean backupHeater = true;

    // weather
    private double latitude = 53.49d;
    private double longitude = -10.015d;
    private String weatherSource = "sample";     // sample (offline asset) | cds (live fetch)

    public long getHeatPumpIndex() { return heatPumpIndex; }
    public void setHeatPumpIndex(long heatPumpIndex) { this.heatPumpIndex = heatPumpIndex; }

    public String getFuelType() { return fuelType; }
    public void setFuelType(String fuelType) { this.fuelType = fuelType; }

    public double getFuelAnnual() { return fuelAnnual; }
    public void setFuelAnnual(double fuelAnnual) { this.fuelAnnual = fuelAnnual; }

    public double getCalorificValue() { return calorificValue; }
    public void setCalorificValue(double calorificValue) { this.calorificValue = calorificValue; }

    public double getBoilerEfficiency() { return boilerEfficiency; }
    public void setBoilerEfficiency(double boilerEfficiency) { this.boilerEfficiency = boilerEfficiency; }

    public double getDhwAnnualKWh() { return dhwAnnualKWh; }
    public void setDhwAnnualKWh(double dhwAnnualKWh) { this.dhwAnnualKWh = dhwAnnualKWh; }

    public Double getSpaceHeatingFraction() { return spaceHeatingFraction; }
    public void setSpaceHeatingFraction(Double spaceHeatingFraction) { this.spaceHeatingFraction = spaceHeatingFraction; }

    public double getFloorAreaM2() { return floorAreaM2; }
    public void setFloorAreaM2(double floorAreaM2) { this.floorAreaM2 = floorAreaM2; }

    public double getHeatLossIndex() { return heatLossIndex; }
    public void setHeatLossIndex(double heatLossIndex) { this.heatLossIndex = heatLossIndex; }

    public double getDesiredIndoorTemp() { return desiredIndoorTemp; }
    public void setDesiredIndoorTemp(double desiredIndoorTemp) { this.desiredIndoorTemp = desiredIndoorTemp; }

    public double getCurrentIndoorTemp() { return currentIndoorTemp; }
    public void setCurrentIndoorTemp(double currentIndoorTemp) { this.currentIndoorTemp = currentIndoorTemp; }

    public double getBalancePoint() { return balancePoint; }
    public void setBalancePoint(double balancePoint) { this.balancePoint = balancePoint; }

    public double getAlphaWind() { return alphaWind; }
    public void setAlphaWind(double alphaWind) { this.alphaWind = alphaWind; }

    public HourlyDist getHourlyDist() { return hourlyDist; }
    public void setHourlyDist(HourlyDist hourlyDist) { this.hourlyDist = hourlyDist; }

    public DOWDist getDowDist() { return dowDist; }
    public void setDowDist(DOWDist dowDist) { this.dowDist = dowDist; }

    public Integer getHeatingSeasonStart() { return heatingSeasonStart; }
    public void setHeatingSeasonStart(Integer heatingSeasonStart) { this.heatingSeasonStart = heatingSeasonStart; }

    public Integer getHeatingSeasonEnd() { return heatingSeasonEnd; }
    public void setHeatingSeasonEnd(Integer heatingSeasonEnd) { this.heatingSeasonEnd = heatingSeasonEnd; }

    public double getCopRated() { return copRated; }
    public void setCopRated(double copRated) { this.copRated = copRated; }

    public double getCopRefTemp() { return copRefTemp; }
    public void setCopRefTemp(double copRefTemp) { this.copRefTemp = copRefTemp; }

    public double getCopSlope() { return copSlope; }
    public void setCopSlope(double copSlope) { this.copSlope = copSlope; }

    public double getScop() { return scop; }
    public void setScop(double scop) { this.scop = scop; }

    public double getCapacityKw() { return capacityKw; }
    public void setCapacityKw(double capacityKw) { this.capacityKw = capacityKw; }

    public boolean isBackupHeater() { return backupHeater; }
    public void setBackupHeater(boolean backupHeater) { this.backupHeater = backupHeater; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getWeatherSource() { return weatherSource; }
    public void setWeatherSource(String weatherSource) { this.weatherSource = weatherSource; }
}
