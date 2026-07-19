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

package com.tfcode.comparetout.model.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Update;

import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.DischargeToGrid;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.Scenario2Battery;
import com.tfcode.comparetout.model.scenario.Scenario2DischargeToGrid;
import com.tfcode.comparetout.model.scenario.Scenario2LoadShift;

import java.util.List;

/**
 * Battery / load-shift / discharge-to-grid queries, moved verbatim from
 * ScenarioDAO (mega-refactor C2). Pure abstract queries only — orchestration
 * lives in {@link com.tfcode.comparetout.model.ops.BatteryOps}. Primitives the
 * cross-domain lifecycle transactions still need (addNew*, getXForScenarioID,
 * relation/orphan deletes) and the link*FromScenario helpers remain on
 * ScenarioDAO until phase C9 so no SQL string is ever duplicated.
 */
@Dao
public abstract class BatteryDAO {

    // ---- Battery ----

    @Query("SELECT * FROM scenario2battery")
    public abstract LiveData<List<Scenario2Battery>> loadBatteryRelations();

    @Query("DELETE FROM scenario2battery WHERE scenarioID = :scenarioID AND batteryID = :batteryID")
    public abstract void deleteBatteryFromScenario(Long batteryID, Long scenarioID);

    @Update (entity = Battery.class)
    public abstract void updateBattery(Battery battery);

    @Query("SELECT scenarioName FROM scenarios WHERE scenarioIndex IN (" +
            "SELECT scenarioID FROM scenario2battery WHERE batteryID = :batteryIndex) AND scenarioIndex != :scenarioID")
    public abstract List<String> getLinkedBatteries(long batteryIndex, Long scenarioID);

    // ---- LoadShift ----

    @Query("SELECT * FROM scenario2loadshift")
    public abstract LiveData<List<Scenario2LoadShift>> loadLoadShiftRelations();

    @Query("DELETE FROM scenario2loadshift WHERE scenarioID = :scenarioID AND loadShiftID = :loadShiftID")
    public abstract void deleteLoadShiftFromScenario(Long loadShiftID, Long scenarioID);

    @Update (entity = LoadShift.class)
    public abstract void updateLoadShift(LoadShift loadShift);

    @Query("SELECT scenarioName FROM scenarios WHERE scenarioIndex IN (" +
            "SELECT scenarioID FROM scenario2loadshift WHERE loadShiftID = :loadShiftIndex) AND scenarioIndex != :scenarioID")
    public abstract List<String> getLinkedLoadShifts(long loadShiftIndex, Long scenarioID);

    // ---- DischargeToGrid ----

    @Query("SELECT * FROM scenario2discharge")
    public abstract LiveData<List<Scenario2DischargeToGrid>> loadDischargeRelations();

    @Query("DELETE FROM scenario2discharge WHERE scenarioID = :scenarioID AND dischargeID = :dischargeID")
    public abstract void deleteDischargeFromScenario(Long dischargeID, Long scenarioID);

    @Update (entity = DischargeToGrid.class)
    public abstract void updateDischarge(DischargeToGrid discharge);

    @Query("SELECT scenarioName FROM scenarios WHERE scenarioIndex IN (" +
            "SELECT scenarioID FROM scenario2discharge WHERE dischargeID = :dischargeIndex) AND scenarioIndex != :scenarioID")
    public abstract List<String> getLinkedDischarges(long dischargeIndex, Long scenarioID);
}
