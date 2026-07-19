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
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.tfcode.comparetout.model.scenario.ScenarioReadiness;

import java.util.List;

/**
 * Readiness-matrix (scenario_readiness) queries — the sim/costing gate reads,
 * invalidation markers, self-heal unblocks, and the readiness upsert — moved
 * verbatim (SQL byte-identical) from ScenarioDAO (mega-refactor C8). Pure
 * abstract queries only; the concrete terminal-state setters
 * (markSimulated/markSimBlocked/markCosted) live in
 * {@link com.tfcode.comparetout.model.ops.ReadinessOps}.
 * deleteReadinessForScenario stays on ScenarioDAO until C9 (called by the
 * deleteScenario lifecycle transaction). simStatus int literals mirror the
 * ScenarioReadiness.SIM_* constants (0 up-to-date, 1 needs, 2 blocked on panel
 * data, 3 blocked on weather).
 */
@Dao
public abstract class ReadinessDAO {
    @Query("SELECT scenarioIndex FROM scenarios")
    public abstract List<Long> getAllScenariosThatMayNeedCosting();

    /** Scenarios ready to simulate: flagged SIM_NEEDS, or no row yet AND not already simulated. Keeps the
     *  old load-profile-data precondition so we never attempt a scenario with no load data. */
    @Query("SELECT scenarioIndex FROM scenarios " +
            "WHERE scenarioIndex IN (SELECT DISTINCT scenarioID FROM scenario2loadprofile) " +
            "AND (SELECT DISTINCT loadProfileID FROM scenario2loadprofile WHERE scenarioID = scenarioIndex) IN (SELECT DISTINCT loadProfileID FROM loadprofiledata) " +
            "AND (scenarioIndex IN (SELECT scenarioID FROM scenario_readiness WHERE simStatus = 1) " +
            "  OR (scenarioIndex NOT IN (SELECT scenarioID FROM scenario_readiness) " +
            "      AND scenarioIndex NOT IN (SELECT DISTINCT scenarioID FROM scenariosimulationdata)))")
    public abstract List<Long> getScenarioIdsNeedingSimulation();

    /** Scenarios needing (re)costing: flagged costingNeeded with sim up-to-date, or no row yet AND has
     *  simulation data to cost. Per-plan precision is still handled by CostingDAO.costingExists. */
    @Query("SELECT scenarioIndex FROM scenarios " +
            "WHERE scenarioIndex IN (SELECT scenarioID FROM scenario_readiness WHERE costingNeeded = 1 AND simStatus = 0) " +
            "  OR (scenarioIndex NOT IN (SELECT scenarioID FROM scenario_readiness) " +
            "      AND scenarioIndex IN (SELECT DISTINCT scenarioID FROM scenariosimulationdata))")
    public abstract List<Long> getScenarioIdsNeedingCosting();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void replaceReadiness(ScenarioReadiness readiness);

    @Query("UPDATE scenario_readiness SET simStatus = 1, costingNeeded = 1, updated = :now WHERE scenarioID = :scenarioID")
    public abstract void markScenarioNeedsSim(long scenarioID, long now);

    @Query("UPDATE scenario_readiness SET costingNeeded = 1, updated = :now WHERE scenarioID = :scenarioID")
    public abstract void markScenarioNeedsCosting(long scenarioID, long now);

    @Query("UPDATE scenario_readiness SET simStatus = 1, costingNeeded = 1, updated = :now " +
            "WHERE scenarioID IN (SELECT scenarioID FROM scenario2loadprofile WHERE loadProfileID = :loadProfileID)")
    public abstract void markProfileScenariosNeedSim(long loadProfileID, long now);

    @Query("UPDATE scenario_readiness SET costingNeeded = 1, updated = :now " +
            "WHERE scenarioID IN (SELECT scenarioID FROM scenario2loadprofile WHERE loadProfileID = :loadProfileID)")
    public abstract void markProfileScenariosNeedCosting(long loadProfileID, long now);

    @Query("UPDATE scenario_readiness SET simStatus = 1, costingNeeded = 1, updated = :now " +
            "WHERE scenarioID IN (SELECT scenarioID FROM scenario2panel WHERE panelID = :panelID)")
    public abstract void markPanelScenarioNeedsSim(long panelID, long now);

    @Query("UPDATE scenario_readiness SET costingNeeded = 1, updated = :now " +
            "WHERE scenarioID IN (SELECT scenarioID FROM scenario2panel WHERE panelID = :panelID)")
    public abstract void markPanelScenarioNeedsCosting(long panelID, long now);

    /** A plan was added or edited → every scenario's costing for that plan is now missing/stale. */
    @Query("UPDATE scenario_readiness SET costingNeeded = 1, updated = :now")
    public abstract void markAllScenariosNeedCosting(long now);

    @Query("UPDATE scenario_readiness SET simStatus = 1, updated = :now " +
            "WHERE simStatus = 2 AND scenarioID IN (SELECT scenarioID FROM scenario2panel WHERE panelID = :panelID)")
    public abstract void unblockPanelScenarios(long panelID, long now);

    @Query("UPDATE scenario_readiness SET simStatus = 1, updated = :now WHERE simStatus = 3 AND scenarioID = :scenarioID")
    public abstract void unblockWeatherScenario(long scenarioID, long now);

    /** Bulk reset (e.g. the one-time panel-data rollout, which wipes all sim/costing output) — clearing
     *  every row lets the defensive gate re-derive readiness from the (now empty) output tables. */
    @Query("DELETE FROM scenario_readiness")
    public abstract void deleteAllReadiness();

}
