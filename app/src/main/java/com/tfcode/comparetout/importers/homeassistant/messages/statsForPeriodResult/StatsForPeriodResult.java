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

package com.tfcode.comparetout.importers.homeassistant.messages.statsForPeriodResult;

import com.google.gson.annotations.SerializedName;
import com.tfcode.comparetout.importers.homeassistant.EnergySensors;
import com.tfcode.comparetout.importers.homeassistant.messages.HAMessageWithID;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatsForPeriodResult extends HAMessageWithID {
    @SerializedName("success")
    private boolean success;
    @SerializedName("result")
    private Map<String, List<SensorData>> result;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Map<String, List<SensorData>> getResult() {
        return result;
    }

    public void setResult(Map<String, List<SensorData>> result) {
        this.result = result;
    }

    /**
     * Pivot the result to a map of dates to a map of sensor names to changes.
     * @return the pivoted result
     */
    public Map<LocalDateTime, Map<String, Double>> pivotStatsForPeriodResult() {
        Map<LocalDateTime, Map<String, Double>> pivotedResult = new HashMap<>();

        for (Map.Entry<String, List<SensorData>> entry : getResult().entrySet()) {
            String sensorName = entry.getKey();
            List<SensorData> sensorDataList = entry.getValue();

            for (SensorData sensorData : sensorDataList) {
                LocalDateTime date = Instant.ofEpochMilli((long) sensorData.getStart())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
                double change = sensorData.getChange();

                Map<String, Double> sensorChanges = pivotedResult.getOrDefault(date, new HashMap<>());
                sensorChanges.put(sensorName, change);

                pivotedResult.put(date, sensorChanges);
            }
        }
        return pivotedResult;
    }
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final DateTimeFormatter MIN_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    /**
     * Calculate and add the load to the pivoted result.
     * @param sysSn the system serial number
     * @param energySensors the energy sensors
     * @param pivotedResult the pivoted result
     * @return the transformed data as a list of AlphaESSTransformedData
     */

    public List<AlphaESSTransformedData> calculateAndAddLoad(String sysSn,
            EnergySensors energySensors, Map<LocalDateTime, Map<String, Double>> pivotedResult) {
        List<AlphaESSTransformedData> rows = new ArrayList<>();
        for (Map.Entry<LocalDateTime, Map<String, Double>> entry : pivotedResult.entrySet()) {
            LocalDateTime date = entry.getKey();
            Map<String, Double> sensorChanges = entry.getValue();
            double solarGen = sensorChanges.getOrDefault(energySensors.solarGeneration, 0.0);
            double discharge = sensorChanges.getOrDefault(energySensors.batteryDischarging, 0.0);
            double charge = sensorChanges.getOrDefault(energySensors.batteryCharging, 0.0);
            double gridExport = 0D;
            for (String sensor : energySensors.gridExports) {
                gridExport += sensorChanges.getOrDefault(sensor, 0.0);
            }
            double gridImport = 0D;
            for (String sensor : energySensors.gridImports) {
                gridImport += sensorChanges.getOrDefault(sensor, 0.0);
            }
            double load = solarGen + (discharge - charge) + (gridImport - gridExport);
            if (load < 0) load = 0;
            sensorChanges.put("load", load);

            AlphaESSTransformedData row = new AlphaESSTransformedData();
            row.setSysSn(sysSn);
            row.setDate(date.format(DATE_FORMAT));
            row.setMinute(date.format(MIN_FORMAT));
            row.setPv(solarGen);
            row.setLoad(load);
            row.setFeed(gridExport);
            row.setBuy(gridImport);

            rows.add(row);
        }
        return rows;
    }
}
