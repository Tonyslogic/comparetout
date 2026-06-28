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

package com.tfcode.comparetout.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.model.scenario.LoadProfileData;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;
import com.tfcode.comparetout.model.scenario.ScenarioReadiness;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

/**
 * Exercises the {@code scenario_readiness} gate queries and flag transitions added with the readiness matrix
 * (see {@link ScenarioReadiness}). Uses an in-memory Room DB, mirroring {@link ScenarioDAOTest}.
 *
 * <p>The two gates are defensive: a scenario with <b>no</b> readiness row counts as "needs work" only when the
 * underlying output tables agree (no simulation rows ⇒ needs sim; has simulation rows ⇒ needs costing). The
 * worker terminal-state setters upsert a row so the scenario then leaves that defensive no-row clause.</p>
 */
@RunWith(AndroidJUnit4.class)
public class ScenarioReadinessDAOTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private ToutcDB toutcDB;
    private ScenarioDAO scenarioDAO;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        toutcDB = Room.inMemoryDatabaseBuilder(context, ToutcDB.class)
                .allowMainThreadQueries()
                .build();
        scenarioDAO = toutcDB.scenarioDAO();
    }

    @After
    public void tearDown() {
        toutcDB.close();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** A scenario with a load profile + one row of load data (the sim gate's precondition), no readiness row. */
    private long scenarioWithLoadData(String name) {
        Scenario scenario = new Scenario();
        scenario.setScenarioName(name);
        LoadProfile loadProfile = new LoadProfile();
        loadProfile.setAnnualUsage(100.0);
        long id = scenarioDAO.addNewScenarioWithComponents(scenario, new ScenarioComponents(
                scenario, null, null, null, null, loadProfile,
                null, null, null, null, null, null), false);
        long loadProfileID = scenarioDAO.getLoadProfileForScenarioID(id).getLoadProfileIndex();
        LoadProfileData row = new LoadProfileData();
        row.setLoadProfileID(loadProfileID);
        row.setDate("2001-01-01");
        row.setMinute("00:00");
        ArrayList<LoadProfileData> rows = new ArrayList<>();
        rows.add(row);
        scenarioDAO.createLoadProfileDataEntries(rows);
        return id;
    }

    /** Same, but also linked to one panel (for the panel-unblock path). */
    private long scenarioWithLoadDataAndPanel(String name) {
        Scenario scenario = new Scenario();
        scenario.setScenarioName(name);
        LoadProfile loadProfile = new LoadProfile();
        loadProfile.setAnnualUsage(100.0);
        ArrayList<Panel> panels = new ArrayList<>();
        panels.add(new Panel());
        long id = scenarioDAO.addNewScenarioWithComponents(scenario, new ScenarioComponents(
                scenario, null, null, panels, null, loadProfile,
                null, null, null, null, null, null), false);
        long loadProfileID = scenarioDAO.getLoadProfileForScenarioID(id).getLoadProfileIndex();
        LoadProfileData row = new LoadProfileData();
        row.setLoadProfileID(loadProfileID);
        row.setDate("2001-01-01");
        row.setMinute("00:00");
        ArrayList<LoadProfileData> rows = new ArrayList<>();
        rows.add(row);
        scenarioDAO.createLoadProfileDataEntries(rows);
        return id;
    }

    private void addSimRow(long scenarioID) {
        ScenarioSimulationData sd = new ScenarioSimulationData();
        sd.setScenarioID(scenarioID);
        sd.setDate("2001-01-01");
        sd.setMinuteOfDay(0);
        ArrayList<ScenarioSimulationData> rows = new ArrayList<>();
        rows.add(sd);
        scenarioDAO.saveSimulationDataForScenario(rows);
    }

    private long panelIdOf(long scenarioID) {
        return scenarioDAO.getPanelsForScenarioID(scenarioID).get(0).getPanelIndex();
    }

    private boolean needsSim(long id) {
        return scenarioDAO.getScenarioIdsNeedingSimulation().contains(id);
    }

    private boolean needsCosting(long id) {
        return scenarioDAO.getScenarioIdsNeedingCosting().contains(id);
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    // ── defensive gates (no readiness row) ───────────────────────────────────

    @Test
    public void freshScenarioNeedsSimNotCosting() {
        long id = scenarioWithLoadData("Fresh");
        // No row + no sim data + has load data → needs sim; can't be costed until simulated.
        assertTrue(needsSim(id));
        assertFalse(needsCosting(id));
    }

    @Test
    public void preMigrationSimulatedNeedsCostingNotSim() {
        // Mimics a scenario that predates v13: it already has simulation data but no readiness row.
        long id = scenarioWithLoadData("PreMigration");
        addSimRow(id);
        assertFalse("already simulated ⇒ not in the sim gate", needsSim(id));
        assertTrue("has sim data + no row ⇒ defensively needs costing", needsCosting(id));
    }

    // ── worker terminal-state transitions ────────────────────────────────────

    @Test
    public void simulatedThenCosted() {
        long id = scenarioWithLoadData("Run");
        scenarioDAO.markSimulated(id);
        assertFalse("simStatus up-to-date ⇒ leaves sim gate", needsSim(id));
        assertTrue("fresh sim ⇒ needs costing", needsCosting(id));

        scenarioDAO.markCosted(id);
        assertFalse("all pairs costed ⇒ leaves cost gate", needsCosting(id));
        assertFalse(needsSim(id));
    }

    @Test
    public void structuralEditReflagsSimAndCosting() {
        long id = scenarioWithLoadData("Edit");
        scenarioDAO.markSimulated(id);
        scenarioDAO.markCosted(id);
        assertFalse(needsSim(id));
        assertFalse(needsCosting(id));

        // A structural edit deletes sim+costing and marks the scenario needing sim.
        scenarioDAO.markScenarioNeedsSim(id, now());
        assertTrue("needs re-sim", needsSim(id));
        assertFalse("can't cost until re-simulated", needsCosting(id));

        scenarioDAO.markSimulated(id);
        assertTrue("re-simulated ⇒ needs re-costing", needsCosting(id));
    }

    @Test
    public void planAddFlipsAllCostingFlags() {
        long id = scenarioWithLoadData("Priced");
        scenarioDAO.markSimulated(id);
        scenarioDAO.markCosted(id);
        assertFalse(needsCosting(id));

        // Adding/editing a plan invalidates costing for every (simulated) scenario.
        scenarioDAO.markAllScenariosNeedCosting(now());
        assertTrue(needsCosting(id));
        assertFalse("sim is untouched by a plan change", needsSim(id));
    }

    // ── blocked / unblocked (self-heal) ──────────────────────────────────────

    @Test
    public void weatherBlockedExcludedThenUnblocked() {
        long id = scenarioWithLoadData("HeatPump");
        scenarioDAO.markSimBlocked(id, ScenarioReadiness.SIM_BLOCKED_WEATHER);
        assertFalse("blocked on weather ⇒ out of the sim gate", needsSim(id));

        scenarioDAO.unblockWeatherScenario(id, now());
        assertTrue("weather landed ⇒ back in the sim gate", needsSim(id));
    }

    @Test
    public void panelBlockedExcludedThenUnblocked() {
        long id = scenarioWithLoadDataAndPanel("Solar");
        scenarioDAO.markSimBlocked(id, ScenarioReadiness.SIM_BLOCKED_PANEL_DATA);
        assertFalse("blocked on panel data ⇒ out of the sim gate", needsSim(id));

        scenarioDAO.unblockPanelScenarios(panelIdOf(id), now());
        assertTrue("panel data landed ⇒ back in the sim gate", needsSim(id));
    }

    @Test
    public void unblockOnlyAffectsMatchingBlockReason() {
        long id = scenarioWithLoadData("MismatchedUnblock");
        scenarioDAO.markSimBlocked(id, ScenarioReadiness.SIM_BLOCKED_PANEL_DATA);
        // A weather unblock must NOT clear a panel-data block.
        scenarioDAO.unblockWeatherScenario(id, now());
        assertFalse(needsSim(id));
    }

    // ── bulk reset ───────────────────────────────────────────────────────────

    @Test
    public void deleteAllReadinessFallsBackToDefensiveGate() {
        long id = scenarioWithLoadData("Wiped");
        scenarioDAO.markSimulated(id);
        assertFalse(needsSim(id));

        // Mirrors PanelDataRefreshWorker: output is wiped and the readiness rows cleared, so the defensive
        // gate re-derives "needs sim" from the (now empty) simulation table.
        scenarioDAO.deleteAllReadiness();
        assertTrue(needsSim(id));
    }
}
