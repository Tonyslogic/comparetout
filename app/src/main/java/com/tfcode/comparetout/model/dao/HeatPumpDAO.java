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

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Update;

import com.tfcode.comparetout.model.scenario.HeatPump;

import java.util.List;

/**
 * Heat-pump queries, moved verbatim from ScenarioDAO (mega-refactor C5). Pure
 * abstract queries only — orchestration lives in
 * {@link com.tfcode.comparetout.model.ops.HeatPumpOps}. Primitives the
 * cross-domain lifecycle transactions still need (addNewHeatPump,
 * getHeatPumpsForScenarioID, relation/orphan deletes) and
 * linkHeatPumpFromScenario remain on ScenarioDAO until phase C9.
 */
@Dao
public abstract class HeatPumpDAO {

    @Query("DELETE FROM scenario2heatpump WHERE scenarioID = :scenarioID AND heatPumpID = :heatPumpID")
    public abstract void deleteHeatPumpFromScenario(Long heatPumpID, Long scenarioID);

    @Update(entity = HeatPump.class)
    public abstract void updateHeatPump(HeatPump heatPump);

    @Query("SELECT scenarioName FROM scenarios WHERE scenarioIndex IN (" +
            "SELECT scenarioID FROM scenario2heatpump WHERE heatPumpID = :heatPumpIndex) AND scenarioIndex != :scenarioID")
    public abstract List<String> getLinkedHeatPumps(long heatPumpIndex, Long scenarioID);
}
