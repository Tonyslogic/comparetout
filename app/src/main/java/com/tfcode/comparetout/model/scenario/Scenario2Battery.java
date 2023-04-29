/*
 * Copyright (c) 2023. Tony Finnerty
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

@Entity(tableName = "scenario2battery")
public class Scenario2Battery {
    @PrimaryKey(autoGenerate = true)
    private long s2bID;
    private long batteryID;
    private long scenarioID;

    public long getS2bID() {
        return s2bID;
    }

    public void setS2bID(long s2bID) {
        this.s2bID = s2bID;
    }

    public long getScenarioID() {
        return scenarioID;
    }

    public void setScenarioID(long scenarioID) {
        this.scenarioID = scenarioID;
    }

    public long getBatteryID() {
        return batteryID;
    }

    public void setBatteryID(long batteryID) {
        this.batteryID = batteryID;
    }
}
