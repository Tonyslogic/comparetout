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

import com.tfcode.comparetout.model.scenario.HWSchedule;
import com.tfcode.comparetout.model.scenario.HWSystem;
import com.tfcode.comparetout.model.scenario.Scenario2HWSchedule;
import com.tfcode.comparetout.model.scenario.Scenario2HWSystem;

import java.util.List;

/**
 * Hot-water (system + schedule) queries, moved verbatim from ScenarioDAO
 * (mega-refactor C3). Pure abstract queries only — orchestration lives in
 * {@link com.tfcode.comparetout.model.ops.HotWaterOps}. Primitives the
 * cross-domain lifecycle transactions still need (addNew*, getXForScenarioID,
 * relation/orphan deletes) and the link*FromScenario helpers remain on
 * ScenarioDAO until phase C9. HWDivert has no query here: its only accessor,
 * updateHWDivert, is used by saveHWDivert which stays on ScenarioDAO (called
 * by linkAllComponentsFromScenario), so it stays too.
 */
@Dao
public abstract class HotWaterDAO {

    // ---- HW system ----

    @Query("SELECT scenarioName FROM scenarios WHERE scenarioIndex IN (" +
            "SELECT scenarioID FROM scenario2hwsystem WHERE hwSystemID = :hwSystemIndex) AND scenarioIndex != :scenarioID")
    public abstract List<String> getLinkedHWSystems(long hwSystemIndex, Long scenarioID);

    @Update (entity = HWSystem.class)
    public abstract void updateHWSystem(HWSystem battery);

    @Query("SELECT * FROM scenario2hwsystem")
    public abstract LiveData<List<Scenario2HWSystem>> loadHWSystemRelations();

    // ---- HW schedule ----

    @Query("SELECT scenarioName FROM scenarios WHERE scenarioIndex IN (" +
            "SELECT scenarioID FROM scenario2hwschedule WHERE hwScheduleID = :hwScheduleIndex) AND scenarioIndex != :scenarioID")
    public abstract List<String> getLinkedHWSchedules(long hwScheduleIndex, Long scenarioID);

    @Query("SELECT * FROM scenario2hwschedule")
    public abstract  LiveData<List<Scenario2HWSchedule>> loadHWScheduleRelations();

    @Query("DELETE FROM scenario2hwschedule WHERE scenarioID = :scenarioID AND hwScheduleID = :hwScheduleID")
    public abstract void deleteHWScheduleFromScenario1(Long hwScheduleID, Long scenarioID);

    @Update (entity = HWSchedule.class)
    public abstract void updateHWSchedule(HWSchedule hwSchedule);
}
