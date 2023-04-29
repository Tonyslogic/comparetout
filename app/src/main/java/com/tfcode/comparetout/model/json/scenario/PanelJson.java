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

package com.tfcode.comparetout.model.json.scenario;

import com.google.gson.annotations.SerializedName;

public class PanelJson {

    @SerializedName("PanelCount")
    public
    Integer panelCount;

    @SerializedName("PanelkWp")
    public
    Integer panelkWp;

    @SerializedName("Azimuth")
    public
    Integer azimuth;

    @SerializedName("Slope")
    public
    Integer slope;

    @SerializedName("Latitude")
    public
    Double latitude;

    @SerializedName("Longitude")
    public
    Double longitude;

    @SerializedName("Inverter")
    public
    String inverter;

    @SerializedName("MPPT")
    public
    Integer mppt;

    @SerializedName("PanelName")
    public
    String panelName;

    @SerializedName("Optimized")
    public
    Boolean optimized;
}
