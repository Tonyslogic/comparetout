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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "scenarios", indices = {
        @Index(value = {"scenarioName"}, unique = true) })
public class Scenario {

    @PrimaryKey(autoGenerate = true)
    private long scenarioIndex;

    @NonNull
    private String scenarioName = "<SCENARIO>";

    private boolean hasInverters = false;
    private boolean hasBatteries = false;
    private boolean hasPanels = false;
    private boolean hasIRData = false;
    private boolean hasHWSystem = false;
    private boolean hasLoadProfiles = false;
    private boolean hasLoadShifts = false;
    @ColumnInfo(defaultValue = "0")
    private boolean hasDischarges = false;
    private boolean hasEVCharges = false;
    private boolean hasHWSchedules = false;
    private boolean hasHWDivert = false;
    private boolean hasEVDivert = false;
    private boolean isActive = false;

    public boolean isHasInverters() {
        return hasInverters;
    }

    public void setHasInverters(boolean hasInverters) {
        this.hasInverters = hasInverters;
    }

    public boolean isHasBatteries() {
        return hasBatteries;
    }

    public void setHasBatteries(boolean hasBatteries) {
        this.hasBatteries = hasBatteries;
    }

    public boolean isHasPanels() {
        return hasPanels;
    }

    public void setHasPanels(boolean hasPanels) {
        this.hasPanels = hasPanels;
    }

    public boolean isHasIRData() {
        return hasIRData;
    }

    public void setHasIRData(boolean hasIRData) {
        this.hasIRData = hasIRData;
    }

    public boolean isHasHWSystem() {
        return hasHWSystem;
    }

    public void setHasHWSystem(boolean hasHWSystem) {
        this.hasHWSystem = hasHWSystem;
    }

    public boolean isHasLoadProfiles() {
        return hasLoadProfiles;
    }

    public void setHasLoadProfiles(boolean hasLoadProfiles) {
        this.hasLoadProfiles = hasLoadProfiles;
    }

    public boolean isHasLoadShifts() {
        return hasLoadShifts;
    }

    public void setHasLoadShifts(boolean hasLoadShifts) {
        this.hasLoadShifts = hasLoadShifts;
    }

    public boolean isHasDischarges() {
        return hasDischarges;
    }

    public void setHasDischarges(boolean hasDischarges) {
        this.hasDischarges = hasDischarges;
    }

    public boolean isHasEVCharges() {
        return hasEVCharges;
    }

    public void setHasEVCharges(boolean hasEVCharges) {
        this.hasEVCharges = hasEVCharges;
    }

    public boolean isHasHWSchedules() {
        return hasHWSchedules;
    }

    public void setHasHWSchedules(boolean hasHWSchedules) {
        this.hasHWSchedules = hasHWSchedules;
    }

    public boolean isHasHWDivert() {
        return hasHWDivert;
    }

    public void setHasHWDivert(boolean hasHWDivert) {
        this.hasHWDivert = hasHWDivert;
    }

    public boolean isHasEVDivert() {
        return hasEVDivert;
    }

    public void setHasEVDivert(boolean hasEVDivert) {
        this.hasEVDivert = hasEVDivert;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    @Override
    public boolean equals(@Nullable Object object)
    {
        if(object == null) return false;
        if(object == this) return true;

        if(object instanceof Scenario)
        {
            return scenarioName.equals(((Scenario) object).getScenarioName());
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return scenarioName.hashCode();
    }

    public long getScenarioIndex() {
        return scenarioIndex;
    }

    public void setScenarioIndex(long scenarioIndex) {
        this.scenarioIndex = scenarioIndex;
    }

    @NonNull
    public String getScenarioName() {
        return scenarioName;
    }

    public void setScenarioName(@NonNull String scenarioName) {
        this.scenarioName = scenarioName;
    }
}
