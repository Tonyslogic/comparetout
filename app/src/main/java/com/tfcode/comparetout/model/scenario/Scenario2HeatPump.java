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

package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scenario2heatpump")
public class Scenario2HeatPump {
    @PrimaryKey(autoGenerate = true)
    private long s2hpID;
    private long heatPumpID;
    private long scenarioID;

    public long getS2hpID() {
        return s2hpID;
    }

    public void setS2hpID(long s2hpID) {
        this.s2hpID = s2hpID;
    }

    public long getScenarioID() {
        return scenarioID;
    }

    public void setScenarioID(long scenarioID) {
        this.scenarioID = scenarioID;
    }

    public long getHeatPumpID() {
        return heatPumpID;
    }

    public void setHeatPumpID(long heatPumpID) {
        this.heatPumpID = heatPumpID;
    }
}
