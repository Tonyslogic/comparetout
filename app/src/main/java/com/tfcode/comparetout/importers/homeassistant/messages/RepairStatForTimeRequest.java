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

public class RepairStatForTimeRequest extends HAMessageWithID {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private static final ZoneId zoneId = ZoneId.systemDefault();

    @SerializedName("statistic_id")
    private String statistic_id;
    @SerializedName("start_time")
    private String start_time;
    @SerializedName("adjustment")
    private double adjustment;
    @SerializedName("adjustment_unit_of_measurement")
    private String adjustment_unit_of_measurement = "kWh";

    public RepairStatForTimeRequest(String stat, LocalDateTime time, double adjustment) {
        setType("recorder/recorder/adjust_sum_statistics");
        this.statistic_id = stat;
        this.start_time = ZonedDateTime.of(time, zoneId).format(formatter);
        this.adjustment = adjustment;
    }
}
