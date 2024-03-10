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

package com.tfcode.comparetout.importers.homeassistant.messages.EnergyPrefsResult;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Result {
    @SerializedName("energy_sources")
    private List<EnergySource> energySources;

    @SerializedName("device_consumption")
    private List<DeviceConsumption> deviceConsumption;

    public List<EnergySource> getEnergySources() {
        return energySources;
    }

    public void setEnergySources(List<EnergySource> energySources) {
        this.energySources = energySources;
    }

    public List<DeviceConsumption> getDeviceConsumption() {
        return deviceConsumption;
    }

    public void setDeviceConsumption(List<DeviceConsumption> deviceConsumption) {
        this.deviceConsumption = deviceConsumption;
    }
}
