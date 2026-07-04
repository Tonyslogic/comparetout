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

package com.tfcode.comparetout.importers.homeassistant.messages;

import com.google.gson.annotations.SerializedName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code recorder/import_statistics}: write a batch of hourly long-term statistic points.
 * <p>
 * Points overwrite by {@code (statistic_id, start)} so re-running a range is idempotent. The
 * {@code sum} of each point is the statistic's <em>cumulative running total</em>, not the
 * hourly delta — callers must emit monotonic sums anchored to the value preceding the range.
 * <p>
 * API currency (recorder statistics API changes, 2025-10-16): {@code mean_type} replaces the
 * deprecated {@code has_mean} (int enum, 0 = NONE) and {@code unit_class} is expected.
 * Target rules: a {@code .}-delimited id (real entity statistic) requires
 * {@code source: "recorder"}; a {@code :}-delimited external id requires the source to match
 * the id's domain prefix (e.g. {@code comparetout:ha_grid_import} → {@code "comparetout"}).
 */
@SuppressWarnings("unused")
public class ImportStatisticsRequest extends HAMessageWithID {

    public static class Metadata {
        @SerializedName("statistic_id")
        private final String statisticId;
        @SerializedName("source")
        private final String source;
        @SerializedName("name")
        private final String name;
        @SerializedName("unit_of_measurement")
        private final String unitOfMeasurement;
        @SerializedName("has_sum")
        private final boolean hasSum = true;
        @SerializedName("unit_class")
        private final String unitClass;
        @SerializedName("mean_type")
        private final int meanType = 0; // StatisticMeanType.NONE

        public Metadata(String statisticId, String source, String name,
                        String unitOfMeasurement, String unitClass) {
            this.statisticId = statisticId;
            this.source = source;
            this.name = name;
            this.unitOfMeasurement = unitOfMeasurement;
            this.unitClass = unitClass;
        }
    }

    public static class StatisticPoint {
        @SerializedName("start")
        private final String start; // hour-aligned UTC ISO instant
        @SerializedName("sum")
        private final double sum;   // cumulative running total in the statistic's unit
        @SerializedName("state")
        private final Double state; // optional last-known state

        public StatisticPoint(long startUtcMillis, double sum, Double state) {
            this.start = Instant.ofEpochMilli(startUtcMillis).toString();
            this.sum = sum;
            this.state = state;
        }
    }

    @SerializedName("metadata")
    private final Metadata metadata;
    @SerializedName("stats")
    private final List<StatisticPoint> stats;

    public ImportStatisticsRequest(Metadata metadata, List<StatisticPoint> stats, int id) {
        setType("recorder/import_statistics");
        this.metadata = metadata;
        this.stats = new ArrayList<>(stats);
        setId(id);
    }
}
