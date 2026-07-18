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

package com.tfcode.comparetout.importers.fusionsolar.responses;

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code GET /rest/pvms/web/station/v1/overview/energy-balance?timeDim=2} —
 * one day's 5-minute energy balance for one plant.
 *
 * {@code data} is kept as a raw {@link JsonObject} rather than a typed
 * message: the exact series/total names are reconstructed from the oracle
 * project and pending live verification (Phase 0 report), so the accessors
 * take <em>candidate name lists</em> and return the first present match.
 * When a tester report shows different names, only the candidate lists in
 * the massager change — not this class.
 *
 * Portal value conventions: curve samples are kW rendered as JSON strings or
 * numbers; the string {@code "--"} marks a missing sample and maps to null
 * (absent, not zero — the distinction matters before normalisation).
 */
public class EnergyBalanceResponse {

    public Boolean success;
    public Integer failCode;
    public JsonObject data;

    /** The {@code HH:mm} slot labels ({@code xAxis}), or an empty list. */
    public List<String> xAxis() {
        List<String> stamps = new ArrayList<>();
        if (null == data) return stamps;
        JsonElement element = data.get("xAxis");
        if (null == element || !element.isJsonArray()) return stamps;
        for (JsonElement stamp : element.getAsJsonArray())
            stamps.add(stamp.isJsonPrimitive() ? stamp.getAsString() : null);
        return stamps;
    }

    /**
     * The first present series among {@code names}, as one Double per xAxis
     * slot with {@code "--"}/non-numeric samples as null — or null when no
     * candidate name is present (which is how the massager detects that the
     * grid-derivation fallback is needed).
     */
    @Nullable
    public Double[] series(String... names) {
        if (null == data) return null;
        for (String name : names) {
            JsonElement element = data.get(name);
            if (null == element || !element.isJsonArray()) continue;
            JsonArray array = element.getAsJsonArray();
            Double[] values = new Double[array.size()];
            for (int i = 0; i < array.size(); i++) values[i] = numberOf(array.get(i));
            return values;
        }
        return null;
    }

    /**
     * The first present numeric scalar among {@code names} (daily totals),
     * or null when absent/non-numeric ({@code "--"} totals included).
     */
    @Nullable
    public Double scalar(String... names) {
        if (null == data) return null;
        for (String name : names) {
            JsonElement element = data.get(name);
            if (null == element) continue;
            Double value = numberOf(element);
            if (null != value) return value;
        }
        return null;
    }

    /** A primitive as a Double; {@code "--"}, nulls and non-numerics map to null. */
    @Nullable
    static Double numberOf(JsonElement element) {
        if (null == element || !element.isJsonPrimitive()) return null;
        try {
            return Double.parseDouble(element.getAsString());
        } catch (NumberFormatException nfe) {
            return null;
        }
    }
}
