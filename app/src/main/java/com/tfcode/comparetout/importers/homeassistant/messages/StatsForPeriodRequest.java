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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatsForPeriodRequest extends HAMessageWithID {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private static final ZoneId zoneId = ZoneId.systemDefault();

    @SerializedName("start_time")
    private String start_time;
    @SerializedName("end_time")
    private String end_time;
    @SerializedName("statistic_ids")
    private List<String> statistic_ids;
    @SerializedName("period")
    private String period = "hour";
    @SerializedName("units")
    private Map<String, String> units;
    @SerializedName("types")
    private List<String> types;

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

    public void set5MinutePeriod() {
        this.period = "5minute";
    }
}
