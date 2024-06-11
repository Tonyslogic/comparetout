/*
 * Copyright (c) 2024. Tony Finnerty
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

package com.tfcode.comparetout.model.json.priceplan;

import com.google.gson.annotations.SerializedName;

public class MinuteRangeCostJson {

    @SerializedName("startMinute")
    public int startMinute;

    @SerializedName("endMinute")
    public int endMinute;

    @SerializedName("cost")
    public double cost;

    /*
     * Default constructor for Gson
     */
    public MinuteRangeCostJson() {
    }

    public MinuteRangeCostJson(int startMinute, int endMinute, double cost) {
        this.startMinute = startMinute;
        this.endMinute = endMinute;
        this.cost = cost;
    }
}
