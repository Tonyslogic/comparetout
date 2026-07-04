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

package com.tfcode.comparetout.importers.homeassistant.messages;

import com.google.gson.annotations.SerializedName;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class StatsForPeriodRequest extends HAMessageWithID {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private static final ZoneId zoneId = ZoneId.systemDefault();

    @SerializedName("start_time")
    private String start_time;
    @SerializedName("end_time")
    private String end_time;
    @SerializedName("statistic_ids")
    private final List<String> statistic_ids;
    @SerializedName("period")
    private String period = "hour";
    // Populated for Gson serialisation; not read locally. Null omits the key so HA returns
    // each statistic in its own stored unit (backfill needs native-unit sums for anchoring).
    @SerializedName("units")
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private Map<String, String> units;
    @SerializedName("types")
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final List<String> types;

    public StatsForPeriodRequest(List<String> stats) {
        setType("recorder/statistics_during_period");
        this.statistic_ids = stats;
        units = new HashMap<>();
        units.put("energy", "kWh");
        units.put("volume", "m³");
        types = new ArrayList<>();
        types.add("change");
    }

    public void setStartAndEndTimes(LocalDateTime start_time, LocalDateTime end_time, int id) {
        this.start_time = ZonedDateTime.of(start_time, zoneId).format(formatter);
        this.end_time = ZonedDateTime.of(end_time, zoneId).format(formatter);
        setId(id);
    }

    /** Proper UTC instants (the legacy setter stamps local wall time with a literal Z). */
    public void setStartAndEndUtc(Instant start, Instant end, int id) {
        this.start_time = start.toString();
        this.end_time = end.toString();
        setId(id);
    }

    public void set5MinutePeriod() {
        this.period = "5minute";
    }

    /** Also request cumulative sums (backfill anchoring / cutoff probing). */
    public void requestSums() {
        if (!types.contains("sum")) types.add("sum");
    }

    /** Drop the unit-conversion map so results arrive in each statistic's stored unit. */
    public void useNativeUnits() {
        this.units = null;
    }
}
