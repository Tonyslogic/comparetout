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

@Entity(tableName = "scenario2loadprofile")
public class Scenario2LoadProfile {
    @PrimaryKey(autoGenerate = true)
    private long s2lpID;
    private long loadProfileID;
    private long scenarioID;

    public long getS2lpID() {
        return s2lpID;
    }

    public void setS2lpID(long s2lpID) {
        this.s2lpID = s2lpID;
    }

    public long getScenarioID() {
        return scenarioID;
    }

    public void setScenarioID(long scenarioID) {
        this.scenarioID = scenarioID;
    }

    public long getLoadProfileID() {
        return loadProfileID;
    }

    public void setLoadProfileID(long loadProfileID) {
        this.loadProfileID = loadProfileID;
    }
}