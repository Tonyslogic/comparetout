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

package com.tfcode.comparetout.model.json.scenario;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

/**
 * Per-scenario share/import JSON for a heat pump (Phase 4 of {@code plans/hp/plan.md}). The
 * {@code @SerializedName}s are the public contract; mirror {@code BatteryJson}.
 */
public class HeatPumpJson {

    @SerializedName("Fuel type")
    public String fuelType;

    @SerializedName("Annual fuel use")
    public Double fuelAnnual;

    @SerializedName("Calorific value")
    public Double calorificValue;

    @SerializedName("Boiler efficiency")
    public Double boilerEfficiency;

    @SerializedName("DHW annual kWh")
    public Double dhwAnnualKWh;

    @SerializedName("Space heating fraction")
    public Double spaceHeatingFraction;

    @SerializedName("Desired indoor temp")
    public Double desiredIndoorTemp;

    @SerializedName("Current indoor temp")
    public Double currentIndoorTemp;

    @SerializedName("Balance point")
    public Double balancePoint;

    @SerializedName("Wind coefficient")
    public Double alphaWind;

    @SerializedName("Hourly distribution")
    public ArrayList<Double> hourlyDistribution;

    @SerializedName("Day of week distribution")
    public ArrayList<Double> dowDistribution;

    @SerializedName("Heating season start")
    public Integer heatingSeasonStart;

    @SerializedName("Heating season end")
    public Integer heatingSeasonEnd;

    @SerializedName("Rated COP")
    public Double copRated;

    @SerializedName("COP reference temp")
    public Double copRefTemp;

    @SerializedName("COP slope")
    public Double copSlope;

    @SerializedName("SCOP")
    public Double scop;

    @SerializedName("Capacity kW")
    public Double capacityKw;

    @SerializedName("Backup heater")
    public Boolean backupHeater;

    @SerializedName("Latitude")
    public Double latitude;

    @SerializedName("Longitude")
    public Double longitude;

    @SerializedName("Weather source")
    public String weatherSource;
}
