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

import java.util.ArrayList;
import java.util.List;

/**
 * {@code recorder/get_statistics_metadata}: fetch metadata (crucially the stored
 * {@code unit_of_measurement}) for the given statistic ids. The backfill uses it for unit
 * alignment — sums written to a real entity statistic must be in that entity's own unit
 * (OQ-6, plans/ha/design.md).
 */
@SuppressWarnings("unused")
public class GetStatisticsMetadataRequest extends HAMessageWithID {

    @SerializedName("statistic_ids")
    private final List<String> statisticIds;

    public GetStatisticsMetadataRequest(List<String> statisticIds, int id) {
        setType("recorder/get_statistics_metadata");
        this.statisticIds = new ArrayList<>(statisticIds);
        setId(id);
    }
}
