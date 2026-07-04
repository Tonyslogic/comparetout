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

package com.tfcode.comparetout.importers.octopus.responses;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * GET /v1/products/{code}/ (public). The per-region tariff maps are keyed by
 * GSP group id ("_A".."_P"), then by payment method ("direct_debit_monthly",
 * "varying", ...). The rate values here are a convenience snapshot — the
 * plan generator uses the tariff {@code code} to fetch the authoritative
 * standard-unit-rates / standing-charges series.
 */
public class ProductDetailResponse {
    @SerializedName("code") public String code;
    @SerializedName("full_name") public String fullName;
    @SerializedName("display_name") public String displayName;
    @SerializedName("description") public String description;
    @SerializedName("is_variable") public boolean isVariable;
    @SerializedName("is_tracker") public boolean isTracker;
    @SerializedName("is_prepay") public boolean isPrepay;
    @SerializedName("is_business") public boolean isBusiness;
    @SerializedName("brand") public String brand;
    @SerializedName("available_to") public String availableTo;

    @SerializedName("single_register_electricity_tariffs")
    public Map<String, Map<String, Tariff>> singleRegisterElectricityTariffs;

    /** Economy-7 style day/night products. */
    @SerializedName("dual_register_electricity_tariffs")
    public Map<String, Map<String, Tariff>> dualRegisterElectricityTariffs;

    public static class Tariff {
        /** Full tariff code, e.g. "E-1R-VAR-22-11-01-C". */
        @SerializedName("code") public String code;
        @SerializedName("standard_unit_rate_inc_vat") public Double standardUnitRateIncVat;
        @SerializedName("day_unit_rate_inc_vat") public Double dayUnitRateIncVat;
        @SerializedName("night_unit_rate_inc_vat") public Double nightUnitRateIncVat;
        /** pence per day. */
        @SerializedName("standing_charge_inc_vat") public Double standingChargeIncVat;
    }
}
