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
 * GET /v1/electricity-meter-points/{mpan}/meters/{serial}/consumption/ (authenticated).
 * Paginated half-hourly kWh readings; follow {@link #next} until null.
 */
public class ConsumptionResponse {
    @SerializedName("count") public int count;
    @SerializedName("next") public String next;
    @SerializedName("previous") public String previous;
    @SerializedName("results") public List<Reading> results;

    public static class Reading {
        /** kWh consumed in the interval. */
        @SerializedName("consumption") public double consumption;
        /** ISO-8601 with offset, e.g. "2026-06-01T00:00:00+01:00" or "...Z". */
        @SerializedName("interval_start") public String intervalStart;
        @SerializedName("interval_end") public String intervalEnd;
    }
}
