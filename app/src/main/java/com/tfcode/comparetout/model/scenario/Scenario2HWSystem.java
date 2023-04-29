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

@Entity(tableName = "scenario2hwsystem")
public class Scenario2HWSystem {
    @PrimaryKey(autoGenerate = true)
    private long s2hwsysID;
    private long hwSystemID;
    private long scenarioID;

    public long getS2hwsysID() {
        return s2hwsysID;
    }

    public void setS2hwsysID(long s2hwsysID) {
        this.s2hwsysID = s2hwsysID;
    }

    public long getScenarioID() {
        return scenarioID;
    }

    public void setScenarioID(long scenarioID) {
        this.scenarioID = scenarioID;
    }

    public long getHwSystemID() {
        return hwSystemID;
    }

    public void setHwSystemID(long hwSystemID) {
        this.hwSystemID = hwSystemID;
    }
}