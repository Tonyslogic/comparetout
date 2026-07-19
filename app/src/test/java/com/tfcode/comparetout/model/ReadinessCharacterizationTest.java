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

import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.MinuteRateRange;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.scenario.LoadProfileData;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;
import com.tfcode.comparetout.model.scenario.ScenarioReadiness;
import com.tfcode.comparetout.testdata.FullScenarioFixture;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Characterization of the readiness matrix (scenario_readiness) gates through
 * the public {@link ToutcRepository} API: defensive no-row behavior, worker
 * terminal-state setters, blocked states with targeted self-heal unblocks,
 * and the invalidation markers fired by data deletion and plan changes.
 * The APPEND→KEEP invariants live here — assert current behavior exactly.
 */
public class ReadinessCharacterizationTest extends CharacterizationTestBase {

    private long scenarioId;
    private long lpId;
    private long panelId;

    /** Fixture scenario; the sim gate additionally needs load-profile data rows. */
    private void seedScenario(boolean withLoadData) {
        scenarioId = repo.insertScenarioAndReturnID(FullScenarioFixture.components(), false);
        assertTrue(scenarioId > 0);
        ScenarioComponents sc = repo.getScenarioComponentsForScenarioID(scenarioId);
        lpId = sc.loadProfile.getLoadProfileIndex();
        panelId = sc.panels.get(0).getPanelIndex();
        if (withLoadData) addLoadData();
    }

    private void addLoadData() {
        ArrayList<LoadProfileData> rows = new ArrayList<>();
        LoadProfileData r = new LoadProfileData();
        r.setLoadProfileID(lpId);
        r.setDate("2001-01-01");
        r.setMinute("00:00");
        r.setLoad(0.4);
        r.setMod(0);
        r.setDow(1);
        r.setDo2001(1);
        r.setMillisSinceEpoch(978307200000L);
        rows.add(r);
        repo.createLoadProfileDataEntries(rows);
    }

    private boolean needsSim() {
        return repo.getScenarioIdsNeedingSimulation().contains(scenarioId);
    }

    private boolean needsCosting() {
        return repo.getScenarioIdsNeedingCosting().contains(scenarioId);
    }

    @Test
    public void simGateRequiresLoadProfileDataEvenForNoRowScenarios() {
        seedScenario(false);
        assertFalse("no load-profile data → never offered for simulation", needsSim());

        addLoadData();
        assertTrue("no readiness row + load data + no sim output → needs sim (defensive clause)",
                needsSim());
        assertTrue("legacy derive-by-scan gate must agree",
                repo.getAllScenariosThatNeedSimulation().contains(scenarioId));
    }

    @Test
    public void markSimulatedClearsSimGateAndRaisesCostingGate() {
        seedScenario(true);
        repo.markSimulated(scenarioId);

        assertFalse(needsSim());
        assertTrue("fresh sim invalidates costing", needsCosting());
    }

    @Test
    public void markCostedSettlesBothGates() {
        seedScenario(true);
        repo.markSimulated(scenarioId);
        repo.markCosted(scenarioId);

        assertFalse(needsSim());
        assertFalse(needsCosting());
    }

    @Test
    public void blockedScenarioIsSkippedUntilTheMatchingUnblock() {
        seedScenario(true);

        repo.markSimBlocked(scenarioId, ScenarioReadiness.SIM_BLOCKED_PANEL_DATA);
        assertFalse("blocked on panel data → gate skips it", needsSim());
        repo.unblockWeatherScenario(scenarioId);
        assertFalse("weather unblock must not touch a panel-data block", needsSim());
        repo.unblockPanelScenarios(panelId);
        assertTrue("panel-data landed → back to needs-sim", needsSim());

        repo.markSimBlocked(scenarioId, ScenarioReadiness.SIM_BLOCKED_WEATHER);
        assertFalse(needsSim());
        repo.unblockPanelScenarios(panelId);
        assertFalse("panel unblock must not touch a weather block", needsSim());
        repo.unblockWeatherScenario(scenarioId);
        assertTrue(needsSim());
    }

    @Test
    public void unblockNeverDisturbsAnUpToDateScenario() {
        seedScenario(true);
        repo.markSimulated(scenarioId);
        repo.markCosted(scenarioId);

        repo.unblockPanelScenarios(panelId);
        repo.unblockWeatherScenario(scenarioId);
        assertFalse("self-heal unblocks only flip still-blocked rows", needsSim());
        assertFalse(needsCosting());
    }

    @Test
    public void dataDeletionMarkersInvalidateTheRightGates() {
        seedScenario(true);
        repo.markSimulated(scenarioId);
        repo.markCosted(scenarioId);

        // Costing wiped for a panel's scenario → costing stale, sim untouched.
        repo.deleteCostingDataForPanelID(panelId);
        assertFalse(needsSim());
        assertTrue(needsCosting());

        // Sim wiped for the profile's scenarios → sim stale; the costing gate
        // requires simStatus up-to-date, so the scenario leaves it again.
        repo.deleteSimulationDataForProfileID(lpId);
        assertTrue(needsSim());
        assertFalse("costing gate only lists scenarios whose sim is up to date",
                needsCosting());
    }

    @Test
    public void newPricePlanInvalidatesCostingEverywhere() {
        seedScenario(true);
        repo.markSimulated(scenarioId);
        repo.markCosted(scenarioId);
        assertFalse(needsCosting());

        DayRate flat = new DayRate();
        flat.setStartDate("01/01");
        flat.setEndDate("12/31");
        MinuteRateRange mrr = new MinuteRateRange();
        mrr.add(0, 1440, 30.0);
        flat.setMinuteRateRange(mrr);
        repo.insert(new PricePlan(), Collections.singletonList(flat), false);
        awaitDbWrites();

        assertTrue("a new plan means every settled scenario needs costing again",
                needsCosting());
    }
}
