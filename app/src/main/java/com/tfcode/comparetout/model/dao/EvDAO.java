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

import com.tfcode.comparetout.model.scenario.EVCharge;
import com.tfcode.comparetout.model.scenario.EVDivert;
import com.tfcode.comparetout.model.scenario.Scenario2EVCharge;
import com.tfcode.comparetout.model.scenario.Scenario2EVDivert;

import java.util.List;

/**
 * EV (charge + divert) queries, moved verbatim from ScenarioDAO (mega-refactor
 * C4). Pure abstract queries only — orchestration lives in
 * {@link com.tfcode.comparetout.model.ops.EvOps}. Primitives the cross-domain
 * lifecycle transactions still need (addNew*, getXForScenarioID, relation/orphan
 * deletes) and linkEVChargeFromScenario remain on ScenarioDAO until phase C9.
 */
@Dao
public abstract class EvDAO {

    // ---- EV charge ----

    @Query("SELECT scenarioName FROM scenarios WHERE scenarioIndex IN (" +
            "SELECT scenarioID FROM scenario2evcharge WHERE evChargeID = :evChargeIndex) AND scenarioIndex != :scenarioID")
    public abstract List<String> getLinkedEVCharges(long evChargeIndex, Long scenarioID);

    @Query("SELECT * FROM scenario2evcharge")
    public abstract LiveData<List<Scenario2EVCharge>> loadEVChargeRelations();

    @Query("DELETE FROM scenario2evcharge WHERE scenarioID = :scenarioID AND evChargeID = :evChargeID")
    public abstract void deleteEVChargeFromScenario(Long evChargeID, Long scenarioID);

    @Update (entity = EVCharge.class)
    public abstract void updateEVCharge(EVCharge evCharge);

    // ---- EV divert ----

    @Query("SELECT scenarioName FROM scenarios WHERE scenarioIndex IN (" +
            "SELECT scenarioID FROM scenario2evdivert WHERE evDivertID = :evDivertIndex) AND scenarioIndex != :scenarioID")
    public abstract List<String> getLinkedEVDiverts(long evDivertIndex, Long scenarioID);

    @Query("SELECT * FROM scenario2evdivert")
    public abstract LiveData<List<Scenario2EVDivert>> loadEVDivertRelations();

    @Query("DELETE FROM scenario2evdivert WHERE scenarioID = :scenarioID AND evDivertID = :evDivertID")
    public abstract void deleteEVDivertFromScenario(Long evDivertID, Long scenarioID);

    @Update (entity = EVDivert.class)
    public abstract void updateEVDivert(EVDivert evDivert);
}
