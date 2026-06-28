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

package com.tfcode.comparetout.model.scenario;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * A small per-scenario readiness record that tracks whether a scenario needs (re)simulation — and if it
 * can't be simulated yet, <i>why</i> — and whether its costings are stale.
 *
 * <p>Historically the workers derived this on the fly by scanning the heavy output tables:
 * {@code ScenarioDAO.getAllScenariosThatNeedSimulation()} did a {@code NOT IN (SELECT … FROM
 * scenariosimulationdata)} set-difference over the ~105k-rows-per-scenario simulation table, and
 * {@code CostingWorker} loaded every scenario's entire simulation series just to discover (via
 * {@code costingExists}) that all (scenario × plan) pairs were already costed. This table replaces those
 * scans with O(1) indexed flags. The invalidation vectors (scenario edits, plan edits, imports, self-heal
 * data fetches) flip the flags; the workers read them.</p>
 *
 * <p>The table is deliberately tiny — one row per scenario. Per-plan costing precision is still provided
 * cheaply by {@code CostingDAO.costingExists}; this row only gates whether a scenario's simulation series is
 * loaded at all. A <b>missing</b> row is treated by the gate queries as "needs work", so the table can never
 * cause needed work to be skipped (e.g. for scenarios that predate the v13 migration) — it only accelerates
 * the common, already-up-to-date case.</p>
 */
@Entity(tableName = "scenario_readiness")
public class ScenarioReadiness {

    /** Simulation output is present and current — nothing to do. */
    public static final int SIM_UP_TO_DATE = 0;
    /** Ready to simulate: structural inputs changed (or never simulated) and all required data is present. */
    public static final int SIM_NEEDS = 1;
    /** PV/source panels are defined but their {@code paneldata} has not been fetched yet. */
    public static final int SIM_BLOCKED_PANEL_DATA = 2;
    /** A heat pump is defined but its CDS/ERA5 weather is not present yet. */
    public static final int SIM_BLOCKED_WEATHER = 3;

    @PrimaryKey
    private long scenarioID;

    /** One of the {@code SIM_*} states above. Defaults to {@link #SIM_NEEDS} for a freshly-created scenario. */
    @ColumnInfo(defaultValue = "1")
    private int simStatus = SIM_NEEDS;

    /** True when at least one (this scenario × some plan) costing is missing or stale. */
    @ColumnInfo(defaultValue = "1")
    private boolean costingNeeded = true;

    /** Epoch millis of the last flag transition — for observability/debugging only. */
    @ColumnInfo(defaultValue = "0")
    private long updated = 0L;

    public ScenarioReadiness() {}

    public ScenarioReadiness(long scenarioID, int simStatus, boolean costingNeeded, long updated) {
        this.scenarioID = scenarioID;
        this.simStatus = simStatus;
        this.costingNeeded = costingNeeded;
        this.updated = updated;
    }

    public long getScenarioID() {
        return scenarioID;
    }

    public void setScenarioID(long scenarioID) {
        this.scenarioID = scenarioID;
    }

    public int getSimStatus() {
        return simStatus;
    }

    public void setSimStatus(int simStatus) {
        this.simStatus = simStatus;
    }

    public boolean isCostingNeeded() {
        return costingNeeded;
    }

    public void setCostingNeeded(boolean costingNeeded) {
        this.costingNeeded = costingNeeded;
    }

    public long getUpdated() {
        return updated;
    }

    public void setUpdated(long updated) {
        this.updated = updated;
    }
}
