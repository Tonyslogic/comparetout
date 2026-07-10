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

package com.tfcode.comparetout.model.json.priceplan;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

public class PricePlanJsonFile {

    @SerializedName("Supplier")
    public
    String supplier;

    @SerializedName("Plan")
    public
    String plan;

    @SerializedName("Feed")
    public
    Double feed;

    @SerializedName("Standing charges")
    public
    Double standingCharges;

    @SerializedName("Bonus cash")
    public
    Double bonus;

    // Optional when a "Dynamic" block is present: a terms-only file is a valid
    // dynamic plan pending materialisation of its prices.
    @SerializedName("Rates")
    public
    ArrayList<DayRateJson> rates;

    @SerializedName("Active")
    public
    Boolean active;

    @SerializedName("LastUpdate")
    public
    String lastUpdate;

    @SerializedName("Reference")
    public
    String reference;

    @SerializedName("DeemedExport")
    public Boolean deemedExport = false;

    // Optional ISO 3166-1 alpha-2 country the supplier operates in. Absent/empty
    // means "everywhere" — the plan is never hidden by the phone-location filter.
    @SerializedName("Location")
    public String location;

    @SerializedName("Restrictions")
    public RestrictionJson restrictions = new RestrictionJson();

    // Supplier terms of a dynamic (wholesale-tracking) tariff; absent for
    // every conventional plan.
    @SerializedName("Dynamic")
    public DynamicTermsJson dynamic;
}

