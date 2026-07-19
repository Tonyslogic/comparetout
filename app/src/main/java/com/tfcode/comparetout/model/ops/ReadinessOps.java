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

package com.tfcode.comparetout.model.ops;

import com.tfcode.comparetout.model.ToutcDB;
import com.tfcode.comparetout.model.dao.ReadinessDAO;
import com.tfcode.comparetout.model.scenario.ScenarioReadiness;

/**
 * Readiness terminal-state setters, moved verbatim from ScenarioDAO's concrete
 * {@code @Transaction} methods (mega-refactor C8). Each body is the original
 * wrapped in {@code db.runInTransaction} — identical atomicity — upserting a
 * readiness row via {@link ReadinessDAO#replaceReadiness}.
 */
public class ReadinessOps {

    private final ToutcDB db;
    private final ReadinessDAO readinessDAO;

    public ReadinessOps(ToutcDB db) {
        this.db = db;
        this.readinessDAO = db.readinessDAO();
    }

    /** Simulation succeeded → up-to-date; a fresh sim invalidates costing. */
    public void markSimulated(long scenarioID) {
        db.runInTransaction(() -> readinessDAO.replaceReadiness(new ScenarioReadiness(scenarioID,
                ScenarioReadiness.SIM_UP_TO_DATE, true, System.currentTimeMillis())));
    }

    /** Simulation can't run yet — record why (SIM_BLOCKED_PANEL_DATA / SIM_BLOCKED_WEATHER) so the gate
     *  skips it until a self-heal fetch unblocks it. */
    public void markSimBlocked(long scenarioID, int blockedStatus) {
        db.runInTransaction(() -> readinessDAO.replaceReadiness(new ScenarioReadiness(scenarioID,
                blockedStatus, true, System.currentTimeMillis())));
    }

    /** All (this scenario × plan) costings are present → costing up-to-date. */
    public void markCosted(long scenarioID) {
        db.runInTransaction(() -> readinessDAO.replaceReadiness(new ScenarioReadiness(scenarioID,
                ScenarioReadiness.SIM_UP_TO_DATE, false, System.currentTimeMillis())));
    }
}
