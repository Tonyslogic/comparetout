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

@Entity(tableName = "scenario2evdivert")
public class Scenario2EVDivert {
    @PrimaryKey(autoGenerate = true)
    private long s2evdID;
    private long evDivertID;
    private long scenarioID;

    public long getS2evdID() {
        return s2evdID;
    }

    public void setS2evdID(long s2evdID) {
        this.s2evdID = s2evdID;
    }

    public long getScenarioID() {
        return scenarioID;
    }

    public void setScenarioID(long scenarioID) {
        this.scenarioID = scenarioID;
    }

    public long getEvDivertID() {
        return evDivertID;
    }

    public void setEvDivertID(long evDivertID) {
        this.evDivertID = evDivertID;
    }
}
