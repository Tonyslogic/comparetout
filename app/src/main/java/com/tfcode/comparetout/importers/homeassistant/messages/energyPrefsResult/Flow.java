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

package com.tfcode.comparetout.importers.homeassistant.messages.energyPrefsResult;

import com.google.gson.annotations.SerializedName;

public class Flow {
    @SerializedName("stat_energy_from")
    private String statEnergyFrom;
    @SerializedName("stat_cost")
    private String statCost;
    @SerializedName("entity_energy_price")
    private String entityEnergyPrice;
    @SerializedName("number_energy_price")
    private String numberEnergyPrice;
    @SerializedName("stat_energy_to")
    private String statEnergyTo;
    @SerializedName("stat_compensation")
    private String statCompensation;

    public String getStatEnergyFrom() {
        return statEnergyFrom;
    }

    public void setStatEnergyFrom(String statEnergyFrom) {
        this.statEnergyFrom = statEnergyFrom;
    }

    public String getStatCost() {
        return statCost;
    }

    public void setStatCost(String statCost) {
        this.statCost = statCost;
    }

    public String getEntityEnergyPrice() {
        return entityEnergyPrice;
    }

    public void setEntityEnergyPrice(String entityEnergyPrice) {
        this.entityEnergyPrice = entityEnergyPrice;
    }

    public String getNumberEnergyPrice() {
        return numberEnergyPrice;
    }

    public void setNumberEnergyPrice(String numberEnergyPrice) {
        this.numberEnergyPrice = numberEnergyPrice;
    }

    public String getStatEnergyTo() {
        return statEnergyTo;
    }

    public void setStatEnergyTo(String statEnergyTo) {
        this.statEnergyTo = statEnergyTo;
    }

    public String getStatCompensation() {
        return statCompensation;
    }

    public void setStatCompensation(String statCompensation) {
        this.statCompensation = statCompensation;
    }
}
