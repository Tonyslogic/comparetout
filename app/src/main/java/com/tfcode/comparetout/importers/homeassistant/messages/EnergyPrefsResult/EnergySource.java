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

public class EnergySource {
    private String type;
    @SerializedName("flow_from")
    private List<Flow> flowFrom;
    @SerializedName("flow_to")
    private List<Flow> flowTo;
    @SerializedName("cost_adjustment_day")
    private double costAdjustmentDay;
    @SerializedName("stat_energy_from")
    private String statEnergyFrom;
    @SerializedName("config_entry_solar_forecast")
    private List<String> configEntrySolarForecast;
    @SerializedName("stat_energy_to")
    private String statEnergyTo;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<Flow> getFlowFrom() {
        return flowFrom;
    }

    public void setFlowFrom(List<Flow> flowFrom) {
        this.flowFrom = flowFrom;
    }

    public List<Flow> getFlowTo() {
        return flowTo;
    }

    public void setFlowTo(List<Flow> flowTo) {
        this.flowTo = flowTo;
    }

    public double getCostAdjustmentDay() {
        return costAdjustmentDay;
    }

    public void setCostAdjustmentDay(double costAdjustmentDay) {
        this.costAdjustmentDay = costAdjustmentDay;
    }

    public String getStatEnergyFrom() {
        return statEnergyFrom;
    }

    public void setStatEnergyFrom(String statEnergyFrom) {
        this.statEnergyFrom = statEnergyFrom;
    }

    public List<String> getConfigEntrySolarForecast() {
        return configEntrySolarForecast;
    }

    public void setConfigEntrySolarForecast(List<String> configEntrySolarForecast) {
        this.configEntrySolarForecast = configEntrySolarForecast;
    }

    public String getStatEnergyTo() {
        return statEnergyTo;
    }

    public void setStatEnergyTo(String statEnergyTo) {
        this.statEnergyTo = statEnergyTo;
    }
}
