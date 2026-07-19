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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.DischargeToGrid;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;
import com.tfcode.comparetout.testdata.FullScenarioFixture;

import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Characterization of the scenario lifecycle through the public
 * {@link ToutcRepository} API: insert (clobber true/false), full component
 * round-trip, copy, delete with orphan purge, export, active toggling.
 * Asserts CURRENT behavior ahead of the ScenarioDAO/ToutcRepository split —
 * these tests must pass unchanged after every backend phase.
 */
public class ScenarioLifecycleCharacterizationTest extends CharacterizationTestBase {

    private static final String NAME = "Fixture · Full Fat";

    private long insertFixture() {
        long id = repo.insertScenarioAndReturnID(FullScenarioFixture.components(), false);
        assertTrue("fixture insert should create a scenario", id > 0);
        return id;
    }

    @Test
    public void insertRoundTripsEveryComponentType() {
        long id = insertFixture();
        ScenarioComponents actual = repo.getScenarioComponentsForScenarioID(id);

        Scenario s = actual.scenario;
        assertEquals(NAME, s.getScenarioName());
        // Capability flags are derived from component presence on insert.
        assertTrue(s.isHasInverters());
        assertTrue(s.isHasBatteries());
        assertTrue(s.isHasPanels());
        assertTrue(s.isHasHWSystem());
        assertTrue(s.isHasLoadProfiles());
        assertTrue(s.isHasLoadShifts());
        assertTrue(s.isHasDischarges());
        assertTrue(s.isHasEVCharges());
        assertTrue(s.isHasHWSchedules());
        assertTrue(s.isHasHWDivert());
        assertTrue(s.isHasEVDivert());
        assertTrue(s.isHasHeatPump());

        // Inverters (matched by name — read order is not part of the contract).
        assertEquals(2, actual.inverters.size());
        Inverter giv = byName(actual.inverters, Inverter::getInverterName, "GivEnergy");
        assertEquals(0.012, giv.getMinExcess(), 0.0);
        assertEquals(3.6, giv.getMaxInverterLoad(), 0.0);
        assertEquals(1, giv.getMpptCount());
        assertEquals(1, giv.getDispatchMode());

        // Batteries (matched by owning inverter).
        assertEquals(2, actual.batteries.size());
        Battery b2 = byName(actual.batteries, Battery::getInverter, "GivEnergy");
        assertEquals(9.5, b2.getBatterySize(), 0.0);
        assertEquals(10.0, b2.getDischargeStop(), 0.0);
        assertEquals(0.3, b2.getMaxDischarge(), 0.0);
        assertEquals(1.5, b2.getStorageLoss(), 0.0);

        // Panels incl. provenance.
        assertEquals(2, actual.panels.size());
        Panel west = byName(actual.panels, Panel::getPanelName, "West Field");
        assertEquals(9, west.getPanelCount());
        assertEquals(410, west.getPanelkWp());
        assertEquals(220, west.getAzimuth());
        assertEquals("AlphaESS", west.getDataSource());
        assertEquals("2024-03-01", west.getDataStartDate());
        assertEquals(9, west.getSystemLoss());

        // Hot water.
        assertEquals(165, actual.hwSystem.getHwCapacity());
        assertEquals(2.5, actual.hwSystem.getHwRate(), 0.0);
        assertEquals(2, actual.hwSchedules.size());
        assertEquals("2026-06-15",
                byName(actual.hwSchedules, hws -> hws.getName(), "Fixture · Windowed heat").getStartDate());
        assertTrue(actual.hwDivert.isActive());

        // Load profile.
        assertEquals(6144.79, actual.loadProfile.getAnnualUsage(), 0.0);
        assertEquals(0.3, actual.loadProfile.getHourlyBaseLoad(), 0.0);
        assertEquals(15.0, actual.loadProfile.getGridImportMax(), 0.0);
        assertEquals(6.0, actual.loadProfile.getGridExportMax(), 0.0);

        // Schedules with window fields.
        assertEquals(2, actual.loadShifts.size());
        LoadShift windowedShift =
                byName(actual.loadShifts, LoadShift::getName, "Fixture · Windowed shift");
        assertEquals("2026-06-01", windowedShift.getStartDate());
        assertEquals(30, windowedShift.getBeginMinute());
        assertEquals(45, windowedShift.getEndMinute());
        assertEquals(95.0, windowedShift.getStopAt(), 0.0);

        assertEquals(2, actual.discharges.size());
        DischargeToGrid export =
                byName(actual.discharges, DischargeToGrid::getName, "Fixture · Windowed export");
        assertEquals(0.25, export.getRate(), 0.0);
        assertEquals("2027-01-31", export.getEndDate());

        assertEquals(2, actual.evCharges.size());
        assertEquals(3.3,
                byName(actual.evCharges, evc -> evc.getName(), "Fixture · Windowed charge").getDraw(), 0.0);
        assertEquals(1, actual.evDiverts.size());
        assertEquals(1.4, actual.evDiverts.get(0).getMinimum(), 0.0);
        assertEquals(16.0, actual.evDiverts.get(0).getDailyMax(), 0.0);

        // Heat pump.
        assertEquals(1, actual.heatPumps.size());
        assertEquals(3.8, actual.heatPumps.get(0).getScop(), 0.0);
        assertEquals("Oil", actual.heatPumps.get(0).getFuelType());
        assertEquals("CDS", actual.heatPumps.get(0).getWeatherSource());
    }

    @Test
    public void duplicateNameWithoutClobberReturnsZeroAndLeavesOriginalIntact() {
        long id = insertFixture();
        long second = repo.insertScenarioAndReturnID(FullScenarioFixture.components(), false);

        assertEquals("duplicate name without clobber must return the 0 sentinel", 0, second);
        List<Scenario> all = repo.getScenarios();
        assertEquals(1, all.size());
        assertEquals(id, all.get(0).getScenarioIndex());
        assertEquals(2, repo.getScenarioComponentsForScenarioID(id).inverters.size());
    }

    @Test
    public void clobberReplacesSameNameScenarioAndPurgesOldComponents() {
        long oldId = insertFixture();
        long oldPanelId = repo.getScenarioComponentsForScenarioID(oldId)
                .panels.get(0).getPanelIndex();

        ScenarioComponents replacement = FullScenarioFixture.components();
        replacement.batteries.get(0).setBatterySize(7.7);
        long newId = repo.insertScenarioAndReturnID(replacement, true);

        assertTrue(newId > 0);
        assertNotEquals(oldId, newId);
        List<Scenario> all = repo.getScenarios();
        assertEquals(1, all.size());
        assertEquals(newId, all.get(0).getScenarioIndex());

        ScenarioComponents actual = repo.getScenarioComponentsForScenarioID(newId);
        assertEquals(7.7,
                byName(actual.batteries, Battery::getInverter, "AlphaESS").getBatterySize(), 0.0);
        assertNull("clobber must orphan-purge the replaced scenario's panels",
                repo.getPanelForID(oldPanelId));
    }

    @Test
    public void copyScenarioDuplicatesComponentsWithFreshIds() {
        long id = insertFixture();
        repo.copyScenario((int) id);
        awaitDbWrites();

        List<Scenario> all = repo.getScenarios();
        assertEquals(2, all.size());
        Scenario copy = all.stream()
                .filter(s -> (NAME + "_copy").equals(s.getScenarioName()))
                .findFirst().orElseThrow(() -> new AssertionError("copy not found"));

        ScenarioComponents original = repo.getScenarioComponentsForScenarioID(id);
        ScenarioComponents copied =
                repo.getScenarioComponentsForScenarioID(copy.getScenarioIndex());

        assertEquals(2, copied.inverters.size());
        assertEquals(2, copied.batteries.size());
        assertEquals(2, copied.panels.size());
        assertEquals(2, copied.loadShifts.size());
        assertEquals(2, copied.discharges.size());
        assertEquals(2, copied.evCharges.size());
        assertEquals(2, copied.hwSchedules.size());
        assertEquals(1, copied.evDiverts.size());
        assertEquals(1, copied.heatPumps.size());
        assertNotNull(copied.hwSystem);
        assertNotNull(copied.hwDivert);

        // Copy semantics: duplicated rows, not shared rows.
        assertNotEquals(original.loadProfile.getLoadProfileIndex(),
                copied.loadProfile.getLoadProfileIndex());
        List<Long> originalPanelIds = original.panels.stream()
                .map(Panel::getPanelIndex).collect(Collectors.toList());
        for (Panel p : copied.panels) {
            assertTrue("copied panel must have a fresh id",
                    !originalPanelIds.contains(p.getPanelIndex()));
        }
    }

    @Test
    public void deleteScenarioPurgesComponentsRelationsAndOrphans() {
        long id = insertFixture();
        ScenarioComponents before = repo.getScenarioComponentsForScenarioID(id);
        long panelId = before.panels.get(0).getPanelIndex();

        repo.deleteScenario((int) id);
        awaitDbWrites();

        assertTrue(repo.getScenarios().isEmpty());
        ScenarioComponents after = repo.getScenarioComponentsForScenarioID(id);
        assertNull(after.scenario);
        assertTrue(after.inverters.isEmpty());
        assertTrue(after.batteries.isEmpty());
        assertTrue(after.panels.isEmpty());
        assertTrue(after.heatPumps.isEmpty());
        assertNull("sole-scenario delete must orphan-purge its panels",
                repo.getPanelForID(panelId));
    }

    @Test
    public void deleteLeavesOtherScenariosComponentsUntouched() {
        long id = insertFixture();
        repo.copyScenario((int) id);
        awaitDbWrites();
        Scenario copy = repo.getScenarios().stream()
                .filter(s -> (NAME + "_copy").equals(s.getScenarioName()))
                .findFirst().orElseThrow(() -> new AssertionError("copy not found"));

        repo.deleteScenario((int) id);
        awaitDbWrites();

        // The copy's (unshared) components survive the original's orphan purge.
        ScenarioComponents survivor =
                repo.getScenarioComponentsForScenarioID(copy.getScenarioIndex());
        assertEquals(2, survivor.inverters.size());
        assertEquals(2, survivor.panels.size());
        assertEquals(1, survivor.heatPumps.size());
        assertNotNull(survivor.loadProfile);
    }

    @Test
    public void exportListsEveryScenarioFullyPopulated() {
        long id = insertFixture();
        repo.copyScenario((int) id);
        awaitDbWrites();

        List<ScenarioComponents> export = repo.getAllScenariosForExport();
        assertEquals(2, export.size());
        for (ScenarioComponents sc : export) {
            assertEquals(2, sc.inverters.size());
            assertEquals(2, sc.panels.size());
            assertEquals(1, sc.heatPumps.size());
            assertNotNull(sc.hwSystem);
            assertNotNull(sc.loadProfile);
            assertNotNull(sc.hwDivert);
        }
    }

    @Test
    public void activeStatusTogglesThroughTheRepository() {
        long id = insertFixture();

        repo.updateScenarioActiveStatus((int) id, true);
        awaitDbWrites();
        assertTrue(findScenario(id).isActive());

        repo.updateScenarioActiveStatus((int) id, false);
        awaitDbWrites();
        assertTrue(!findScenario(id).isActive());
    }

    private Scenario findScenario(long id) {
        return repo.getScenarios().stream()
                .filter(s -> s.getScenarioIndex() == id)
                .findFirst().orElseThrow(() -> new AssertionError("scenario " + id + " missing"));
    }

    private interface Namer<T> { String name(T t); }

    private static <T> T byName(List<T> list, Namer<T> namer, String name) {
        for (T t : list) if (name.equals(namer.name(t))) return t;
        throw new AssertionError("No element named '" + name + "'");
    }
}
