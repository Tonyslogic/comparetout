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

package com.tfcode.comparetout.model.json.scenario.pgvis;

import com.google.gson.annotations.SerializedName;

public class Hourly {

    @SerializedName("time")
    public String time;

    @SerializedName("G(i)")
    public double gi;

    // PVGIS PV system power output (W), returned when the seriescalc query sets pvcalculation=1.
    // PVGIS has already applied the per-location module-temperature derate (from T2m/wind) and the
    // flat system-loss%, so this is consumed directly instead of re-deriving power from G(i).
    @SerializedName("P")
    public double p;

//    @SerializedName("H_sun")
//    public double h_sun;
//
//    @SerializedName("T2m")
//    public double t2m;
//
//    @SerializedName("WS10m")
//    public double wS10m;

//    @SerializedName("Int")
//    public double int;
}
