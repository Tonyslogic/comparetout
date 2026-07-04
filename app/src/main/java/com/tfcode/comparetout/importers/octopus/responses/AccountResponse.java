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

import java.util.List;

/**
 * GET /v1/accounts/{account_number}/ (authenticated).
 * Carries the meter-point topology (import/export MPANs, meter serials) and
 * the tariff agreements the price-plan generator uses.
 */
public class AccountResponse {
    @SerializedName("number") public String number;
    @SerializedName("properties") public List<Property> properties;

    public static class Property {
        @SerializedName("id") public long id;
        @SerializedName("moved_in_at") public String movedInAt;
        @SerializedName("moved_out_at") public String movedOutAt;
        @SerializedName("postcode") public String postcode;
        @SerializedName("electricity_meter_points") public List<MeterPoint> electricityMeterPoints;
    }

    public static class MeterPoint {
        @SerializedName("mpan") public String mpan;
        @SerializedName("profile_class") public Integer profileClass;
        @SerializedName("is_export") public boolean isExport;
        @SerializedName("meters") public List<Meter> meters;
        @SerializedName("agreements") public List<Agreement> agreements;
    }

    public static class Meter {
        @SerializedName("serial_number") public String serialNumber;
    }

    public static class Agreement {
        /** e.g. "E-1R-VAR-22-11-01-C" — product code + GSP region letter suffix. */
        @SerializedName("tariff_code") public String tariffCode;
        @SerializedName("valid_from") public String validFrom;
        /** null while the agreement is current. */
        @SerializedName("valid_to") public String validTo;
    }
}
