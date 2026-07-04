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

package com.tfcode.comparetout.importers.homeassistant;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class EnergySensors {
    @SerializedName("gridImports")
    public List<String> gridImports;

    @SerializedName("gridExports")
    public List<String> gridExports;

    @SerializedName("solarGeneration")
    public List<String> solarGeneration;

    @SerializedName("batteries")
    public List<BatterySensor> batteries;

    // Individual devices from the Energy Dashboard, user-classified (may be null for
    // configurations persisted before device capture existed).
    @SerializedName("devices")
    public List<DeviceSensor> devices;

    public List<String> getSenorList() {
        List<String> sensorList = new ArrayList<>();
        sensorList.addAll(gridImports);
        sensorList.addAll(gridExports);
        sensorList.addAll(solarGeneration);
        for (BatterySensor battery : batteries) {
            sensorList.add(battery.batteryCharging);
            sensorList.add(battery.batteryDischarging);
        }
        // Classified devices ride along in the same statistics_during_period fetch.
        for (DeviceSensor device : getClassifiedDevices()) {
            sensorList.add(device.statId);
        }
        return sensorList;
    }

    /** Devices assigned a modelled role (EV / hot water / heat pump); OTHER is ignored. */
    public List<DeviceSensor> getClassifiedDevices() {
        List<DeviceSensor> classified = new ArrayList<>();
        if (null == devices) return classified;
        for (DeviceSensor device : devices) {
            if (!(null == device) && !(null == device.statId) && !(null == device.role)
                    && device.role != DeviceSensor.Role.OTHER) {
                classified.add(device);
            }
        }
        return classified;
    }
}
