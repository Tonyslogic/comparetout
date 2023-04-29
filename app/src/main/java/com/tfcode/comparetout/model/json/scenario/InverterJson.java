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

public class InverterJson {

    @SerializedName("Name")
    public
    String name;

    @SerializedName("MinExcess")
    public
    Double minExcess;

    @SerializedName("MaxInverterLoad")
    public
    Double maxInverterLoad;

    @SerializedName("MPPTCount")
    public
    Integer mPPTCount;

    @SerializedName("AC2DCLoss")
    public
    Integer ac2dcLoss;

    @SerializedName("DC2ACLoss")
    public
    Integer dc2acLoss;

    @SerializedName("DC2DCLoss")
    public
    Integer dc2dcLoss;
}
