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

package com.tfcode.comparetout.model.json.scenario;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

public class DischargeToGridJson {

    @SerializedName("Name")
    public
    String name;

    @SerializedName("begin")
    public
    Integer begin;

    @SerializedName("end")
    public
    Integer end;

    @SerializedName("stop at")
    public
    Double stopAt;

    @SerializedName("rate")
    public
    Double rate;

    @SerializedName("months")
    public
    ArrayList<Integer> months;

    @SerializedName("days")
    public
    ArrayList<Integer> days;

    @SerializedName("Inverter")
    public
    String inverter;
}
