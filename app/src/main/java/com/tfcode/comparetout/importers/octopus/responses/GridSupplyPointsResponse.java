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
 * GET /v1/industry/grid-supply-points/?postcode=... (public). Resolves a UK
 * postcode to its GSP region group, e.g. "_C" for London.
 */
public class GridSupplyPointsResponse {
    @SerializedName("count") public int count;
    @SerializedName("results") public List<GroupResult> results;

    public static class GroupResult {
        @SerializedName("group_id") public String groupId;
    }
}
