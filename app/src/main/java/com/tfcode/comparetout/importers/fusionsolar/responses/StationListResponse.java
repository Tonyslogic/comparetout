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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code POST /rest/pvms/web/station/v1/station/station-list} — the portal's
 * {@code {success, failCode, data}} envelope. The station array has been
 * observed both directly under {@code data} and under {@code data.list}
 * (portal versions differ), so {@code data} is kept as a raw element and
 * {@link #stations()} accepts either shape. Only the fields we consume are
 * declared; Gson ignores the rest.
 */
public class StationListResponse {

    public Boolean success;
    public Integer failCode;
    public JsonElement data;

    /** The station records, whichever shape {@code data} arrived in. */
    public List<Station> stations() {
        List<Station> stations = new ArrayList<>();
        JsonArray array = null;
        if (null != data && data.isJsonArray()) array = data.getAsJsonArray();
        else if (null != data && data.isJsonObject()) {
            JsonObject o = data.getAsJsonObject();
            if (o.has("list") && o.get("list").isJsonArray())
                array = o.getAsJsonArray("list");
        }
        if (null == array) return stations;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject o = element.getAsJsonObject();
            Station station = new Station();
            station.dn = stringOf(o.get("dn"));
            station.name = stringOf(o.get("name"));
            JsonElement capacity = o.get("installedCapacity");
            if (null != capacity && capacity.isJsonPrimitive()) {
                try {
                    station.installedCapacity = capacity.getAsDouble();
                } catch (NumberFormatException ignored) {
                }
            }
            if (null != station.dn) stations.add(station);
        }
        return stations;
    }

    private static String stringOf(JsonElement element) {
        if (null == element || !element.isJsonPrimitive()) return null;
        return element.getAsString();
    }

    /**
     * One plant. {@code dn} (e.g. {@code NE=33554678}) is the identifier
     * every data call takes; the storage sysSn is derived from it with the
     * {@code NE=} prefix stripped.
     */
    public static class Station {
        public String dn;
        public String name;
        @SerializedName("installedCapacity")
        public Double installedCapacity;
    }
}
