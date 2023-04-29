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

import java.util.ArrayList;

public class HWSystemJson {

    @SerializedName("HWCapacity")
    public
    Integer hwCapacity;

    @SerializedName("HWUsage")
    public
    Integer hwUsage;

    @SerializedName("HWIntake")
    public
    Integer hwIntake;

    @SerializedName("HWTarget")
    public
    Integer hwTarget;

    @SerializedName("HWLoss")
    public
    Integer hwLoss;

    @SerializedName("HWRate")
    public
    Double hwRate;

    @SerializedName("HWUse")
    public
    ArrayList<ArrayList<Double>> hwUse;
}
