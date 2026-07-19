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

import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.Scenario2Inverter;

import java.util.List;

/**
 * Inverter-domain queries, moved verbatim from ScenarioDAO (mega-refactor C1).
 * Pure abstract queries only — orchestration lives in
 * {@link com.tfcode.comparetout.model.ops.InverterOps}. Primitive queries the
 * cross-domain lifecycle transactions still need (addNewInverter,
 * getInvertersForScenarioID, relation/orphan deletes) remain on ScenarioDAO
 * until phase C9 so no SQL string is ever duplicated.
 */
@Dao
public abstract class InverterDAO {

    @Update (entity = Inverter.class)
    public abstract void updateInverter(Inverter inverter);

    @Query("DELETE FROM scenario2inverter WHERE scenarioID = :scenarioID AND inverterID = :inverterID")
    public abstract void removeScenario2Inverter(Long inverterID, Long scenarioID);

    @Query("SELECT * FROM scenario2inverter")
    public abstract LiveData<List<Scenario2Inverter>> loadInverterRelations();

    @Query("SELECT scenarioName FROM scenarios WHERE scenarioIndex IN (" +
            "SELECT scenarioID FROM scenario2inverter WHERE inverterID = :inverterID) AND scenarioIndex != :scenarioID")
    public abstract List<String> getLinkedInverters(Long inverterID, Long scenarioID);
}
