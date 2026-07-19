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

package com.tfcode.comparetout.testdata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.tfcode.comparetout.model.scenario.HeatPump;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;

import org.junit.Test;

/**
 * Smoke test for the Phase 0 full-fat fixture: every component type present,
 * and the newer optional fields (schedule windows, PV provenance, heat pump)
 * survive the production JsonTools parse path.
 */
public class FullScenarioFixtureTest {

    @Test
    public void everyComponentTypePresent() {
        ScenarioComponents sc = FullScenarioFixture.components();

        assertNotNull(sc.scenario);
        assertEquals("Fixture · Full Fat", sc.scenario.getScenarioName());
        assertEquals(2, sc.inverters.size());
        assertEquals(2, sc.batteries.size());
        assertEquals(2, sc.panels.size());
        assertNotNull(sc.hwSystem);
        assertNotNull(sc.loadProfile);
        assertEquals(2, sc.loadShifts.size());
        assertEquals(2, sc.discharges.size());
        assertEquals(2, sc.evCharges.size());
        assertEquals(2, sc.hwSchedules.size());
        assertNotNull(sc.hwDivert);
        assertTrue(sc.hwDivert.isActive());
        assertEquals(1, sc.evDiverts.size());
        assertEquals(1, sc.heatPumps.size());
    }

    @Test
    public void scheduleWindowFieldsSurviveParse() {
        ScenarioComponents sc = FullScenarioFixture.components();

        LoadShift windowed = sc.loadShifts.get(1);
        assertEquals("Fixture · Windowed shift", windowed.getName());
        assertEquals("2026-06-01", windowed.getStartDate());
        assertEquals("2026-08-31", windowed.getEndDate());
        assertEquals(30, windowed.getBeginMinute());
        assertEquals(45, windowed.getEndMinute());
    }

    @Test
    public void panelProvenanceSurvivesParse() {
        ScenarioComponents sc = FullScenarioFixture.components();

        Panel west = sc.panels.get(1);
        assertEquals("West Field", west.getPanelName());
        assertEquals("AlphaESS", west.getDataSource());
        assertEquals("2024-03-01", west.getDataStartDate());
        assertEquals("2025-02-28", west.getDataEndDate());
        assertEquals(9, west.getSystemLoss());
    }

    @Test
    public void heatPumpFieldsSurviveParse() {
        ScenarioComponents sc = FullScenarioFixture.components();

        HeatPump hp = sc.heatPumps.get(0);
        assertEquals("Oil", hp.getFuelType());
        assertEquals(1200.0, hp.getFuelAnnual(), 0.0);
        assertEquals(0.82, hp.getBoilerEfficiency(), 0.0);
        assertEquals(Double.valueOf(0.85), hp.getSpaceHeatingFraction());
        assertEquals(Integer.valueOf(10), hp.getHeatingSeasonStart());
        assertEquals(Integer.valueOf(4), hp.getHeatingSeasonEnd());
        assertEquals(3.8, hp.getScop(), 0.0);
        assertTrue(hp.isBackupHeater());
        assertEquals("CDS", hp.getWeatherSource());
    }
}
