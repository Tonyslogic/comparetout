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

package com.tfcode.comparetout.importers.homeassistant;

import com.google.gson.annotations.SerializedName;

import java.util.Locale;

/**
 * An "Individual devices" entry from the HA Energy Dashboard, classified against the app's
 * component schemata. HA does not carry a device's purpose, so the role is user-assigned
 * (with a name-based suggestion); {@code OTHER} means untracked/ignored.
 * <p>
 * Device energy is already inside the computed load. {@code MARK} (default, adjust=false)
 * keeps load as the measured total and additionally stores the device slice
 * (evActual/hwActual/hpActual); {@code adjust=true} also subtracts the slice from load at
 * ingestion — lossy, opt-in per device (plans/ha/design.md §1c).
 */
public class DeviceSensor {

    public enum Role {
        EV, HOT_WATER, HEAT_PUMP, OTHER
    }

    @SerializedName("statId")
    public String statId;

    @SerializedName("role")
    public Role role = Role.OTHER;

    @SerializedName("label")
    public String label;

    @SerializedName("adjust")
    public boolean adjust = false;

    public DeviceSensor() {
    }

    public DeviceSensor(String statId, String label) {
        this.statId = statId;
        this.label = (null == label || label.isEmpty()) ? statId : label;
        this.role = suggestRole(statId + " " + this.label);
    }

    /**
     * Name-based role suggestion. Token-matched (not substring) so e.g. "device" does not
     * match "ev". The user always has the final say in the classification dialog.
     */
    public static Role suggestRole(String name) {
        if (null == name) return Role.OTHER;
        String[] tokens = name.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        boolean heat = false;
        boolean pump = false;
        for (String token : tokens) {
            switch (token) {
                case "ev":
                case "car":
                case "zappi":
                case "wallbox":
                case "charger":
                    return Role.EV;
                case "water":
                case "immersion":
                case "dhw":
                case "eddi":
                    return Role.HOT_WATER;
                case "hp":
                case "ashp":
                case "heatpump":
                    return Role.HEAT_PUMP;
                case "heat":
                    heat = true;
                    break;
                case "pump":
                    pump = true;
                    break;
            }
        }
        if (heat && pump) return Role.HEAT_PUMP;
        return Role.OTHER;
    }
}
