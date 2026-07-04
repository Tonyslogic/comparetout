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

import java.util.List;

/** Result frame for {@code recorder/get_statistics_metadata}. */
@SuppressWarnings("unused")
public class GetStatisticsMetadataResult extends HAMessageWithID {

    public static class StatisticMetadata {
        @SerializedName("statistic_id")
        private String statisticId;
        @SerializedName("unit_of_measurement")
        private String unitOfMeasurement;
        @SerializedName("has_sum")
        private boolean hasSum;
        @SerializedName("name")
        private String name;
        @SerializedName("source")
        private String source;
        @SerializedName("unit_class")
        private String unitClass;

        public String getStatisticId() {
            return statisticId;
        }

        public String getUnitOfMeasurement() {
            return unitOfMeasurement;
        }

        public boolean isHasSum() {
            return hasSum;
        }

        public String getName() {
            return name;
        }

        public String getSource() {
            return source;
        }

        public String getUnitClass() {
            return unitClass;
        }
    }

    @SerializedName("result")
    private List<StatisticMetadata> result;

    public List<StatisticMetadata> getResult() {
        return result;
    }
}
