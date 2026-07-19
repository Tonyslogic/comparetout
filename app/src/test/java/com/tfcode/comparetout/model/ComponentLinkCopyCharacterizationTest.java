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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.tfcode.comparetout.model.scenario.HWDivert;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadProfileData;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.PanelData;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;
import com.tfcode.comparetout.testdata.FullScenarioFixture;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Characterization of the per-component save/link/copy/delete verticals
 * through the public {@link ToutcRepository} API. The load-bearing semantics:
 * save with index 0 inserts+links+sets the has-flag (index≠0 updates in
 * place); link SHARES the component row; copy DUPLICATES it with a fresh id;
 * delete unlinks and purges only rows no scenario references any more.
 */
public class ComponentLinkCopyCharacterizationTest extends CharacterizationTestBase {

    private static final String NAME = "Fixture · Full Fat";

    private long insertFixture() {
        long id = repo.insertScenarioAndReturnID(FullScenarioFixture.components(), false);
        assertTrue(id > 0);
        return id;
    }

    private long insertEmpty(String name) {
        Scenario s = new Scenario();
        s.setScenarioName(name);
        long id = repo.insertScenarioAndReturnID(new ScenarioComponents(
                s, null, null, null, null, null, null, null, null, null, null, null), false);
        assertTrue(id > 0);
        return id;
    }

    @Test
    public void saveWithIndexZeroInsertsLinksAndSetsEveryFlag() {
        long b = insertEmpty("Target");
        ScenarioComponents f = FullScenarioFixture.components();

        repo.saveInverter(b, f.inverters.get(0));
        repo.saveBatteryForScenario(b, f.batteries.get(0));
        repo.savePanel(b, f.panels.get(0));
        repo.saveHeatPumpForScenario(b, f.heatPumps.get(0));
        repo.saveHWSystemForScenario(b, f.hwSystem);
        repo.saveHWDivert(b, f.hwDivert);
        repo.saveHWScheduleForScenario(b, f.hwSchedules.get(0));
        repo.saveEVChargeForScenario(b, f.evCharges.get(0));
        repo.saveEVDivertForScenario(b, f.evDiverts.get(0));
        repo.saveLoadShiftForScenario(b, f.loadShifts.get(0));
        repo.saveDischargeForScenario(b, f.discharges.get(0));
        repo.saveLoadProfileAndReturnID(b, f.loadProfile);
        awaitDbWrites();

        ScenarioComponents sc = repo.getScenarioComponentsForScenarioID(b);
        assertEquals(1, sc.inverters.size());
        assertEquals(1, sc.batteries.size());
        assertEquals(1, sc.panels.size());
        assertEquals(1, sc.heatPumps.size());
        assertNotNull(sc.hwSystem);
        assertNotNull(sc.hwDivert);
        assertEquals(1, sc.hwSchedules.size());
        assertEquals(1, sc.evCharges.size());
        assertEquals(1, sc.evDiverts.size());
        assertEquals(1, sc.loadShifts.size());
        assertEquals(1, sc.discharges.size());
        assertNotNull(sc.loadProfile);

        Scenario after = sc.scenario;
        assertTrue(after.isHasInverters());
        assertTrue(after.isHasBatteries());
        assertTrue(after.isHasPanels());
        assertTrue(after.isHasHeatPump());
        assertTrue(after.isHasHWSystem());
        assertTrue(after.isHasHWDivert());
        assertTrue(after.isHasHWSchedules());
        assertTrue(after.isHasEVCharges());
        assertTrue(after.isHasEVDivert());
        assertTrue(after.isHasLoadShifts());
        assertTrue(after.isHasDischarges());
        assertTrue(after.isHasLoadProfiles());
    }

    @Test
    public void saveWithExistingIndexUpdatesInPlace() {
        long b = insertEmpty("Target");
        long invId = repo.saveInverter(b, FullScenarioFixture.components().inverters.get(0));
        awaitDbWrites();

        Inverter stored = repo.getScenarioComponentsForScenarioID(b).inverters.get(0);
        assertEquals(invId, stored.getInverterIndex());
        stored.setMaxInverterLoad(9.9);
        repo.saveInverter(b, stored);
        awaitDbWrites();

        ScenarioComponents sc = repo.getScenarioComponentsForScenarioID(b);
        assertEquals("update must not create a second inverter", 1, sc.inverters.size());
        assertEquals(9.9, sc.inverters.get(0).getMaxInverterLoad(), 0.0);
    }

    @Test
    public void linkSharesComponentRows() {
        long a = insertFixture();
        long b = insertEmpty("Target");

        repo.linkInverterFromScenario(a, b);
        repo.linkBatteryFromScenario(a, b);
        repo.linkHWSystemFromScenario(a, b);
        repo.linkEVChargeFromScenario(a, b);
        awaitDbWrites();

        ScenarioComponents sa = repo.getScenarioComponentsForScenarioID(a);
        ScenarioComponents sb = repo.getScenarioComponentsForScenarioID(b);
        assertEquals(ids(sa.inverters, Inverter::getInverterIndex),
                ids(sb.inverters, Inverter::getInverterIndex));
        assertEquals(sa.hwSystem.getHwSystemIndex(), sb.hwSystem.getHwSystemIndex());
        assertEquals(2, sb.batteries.size());
        assertEquals(2, sb.evCharges.size());
        assertTrue(sb.scenario.isHasInverters());

        // From A's perspective, its inverter is now also linked to "Target".
        long sharedInverter = sa.inverters.get(0).getInverterIndex();
        assertTrue(repo.getLinkedInverters(sharedInverter, a).contains("Target"));
    }

    @Test
    public void copyCreatesFreshComponentRows() {
        long a = insertFixture();
        long b = insertEmpty("Target");

        repo.copyPanelFromScenario(a, b);
        repo.copyHeatPumpFromScenario(a, b);
        repo.copyLoadShiftFromScenario(a, b);
        repo.copyHWScheduleFromScenario(a, b);
        repo.copyEVDivertFromScenario(a, b);
        repo.copyDischargeFromScenario(a, b);
        awaitDbWrites();

        ScenarioComponents sa = repo.getScenarioComponentsForScenarioID(a);
        ScenarioComponents sb = repo.getScenarioComponentsForScenarioID(b);
        assertEquals(2, sb.panels.size());
        assertEquals(1, sb.heatPumps.size());
        assertEquals(2, sb.loadShifts.size());
        assertEquals(2, sb.hwSchedules.size());
        assertEquals(1, sb.evDiverts.size());
        assertEquals(2, sb.discharges.size());

        Set<Long> aPanelIds = ids(sa.panels, Panel::getPanelIndex);
        for (Panel p : sb.panels) {
            assertFalse("copied panel must not share the source row",
                    aPanelIds.contains(p.getPanelIndex()));
        }
        assertNotEquals(sa.heatPumps.get(0).getHeatPumpIndex(),
                sb.heatPumps.get(0).getHeatPumpIndex());
    }

    @Test
    public void sharedPanelSurvivesUntilLastReferenceIsDeleted() {
        long a = insertFixture();
        long b = insertEmpty("Target");
        repo.linkPanelFromScenario(a, b);
        awaitDbWrites();

        long panelId = repo.getScenarioComponentsForScenarioID(a).panels.get(0).getPanelIndex();
        repo.deletePanelFromScenario(panelId, a);
        awaitDbWrites();
        assertNotNull("panel still referenced by Target — must survive the orphan purge",
                repo.getPanelForID(panelId));

        repo.deletePanelFromScenario(panelId, b);
        awaitDbWrites();
        assertNull("last reference gone — panel row must be purged",
                repo.getPanelForID(panelId));
    }

    @Test
    public void deletingLastComponentClearsTheHasFlag() {
        long b = insertEmpty("Target");
        long invId = repo.saveInverter(b, FullScenarioFixture.components().inverters.get(0));
        awaitDbWrites();
        assertTrue(repo.getScenarioForID(b).isHasInverters());

        repo.deleteInverterFromScenario(invId, b);
        awaitDbWrites();
        assertTrue(repo.getScenarioComponentsForScenarioID(b).inverters.isEmpty());
        assertFalse(repo.getScenarioForID(b).isHasInverters());
    }

    @Test
    public void loadProfileDataLifecycleAndCopyVsLink() {
        long a = insertFixture();
        long b = insertEmpty("Target");
        long lpId = repo.saveLoadProfileAndReturnID(b, FullScenarioFixture.components().loadProfile);
        awaitDbWrites();

        // Characterized contract: loadProfileDataCheck is TRUE when data is MISSING.
        assertTrue(repo.loadProfileDataCheck(lpId));
        repo.createLoadProfileDataEntries(lpRows(lpId));
        assertFalse(repo.loadProfileDataCheck(lpId));

        // Copy B→A: A gets a FRESH profile row with the data duplicated.
        repo.copyLoadProfileFromScenario(b, a);
        awaitDbWrites();
        long aCopiedLp = repo.getScenarioComponentsForScenarioID(a)
                .loadProfile.getLoadProfileIndex();
        assertNotEquals(lpId, aCopiedLp);
        assertFalse("copied profile must carry the data rows",
                repo.loadProfileDataCheck(aCopiedLp));

        // Link B→A: A now shares B's profile row.
        repo.linkLoadProfileFromScenario(b, a);
        awaitDbWrites();
        assertEquals(lpId, repo.getScenarioComponentsForScenarioID(a)
                .loadProfile.getLoadProfileIndex());
    }

    @Test
    public void panelDataExtras() {
        long a = insertFixture();
        long b = insertEmpty("Target");
        ScenarioComponents sa = repo.getScenarioComponentsForScenarioID(a);
        Panel top = sa.panels.stream().filter(p -> "Top".equals(p.getPanelName()))
                .findFirst().orElseThrow(() -> new AssertionError("Top panel missing"));
        Panel west = sa.panels.stream().filter(p -> "West Field".equals(p.getPanelName()))
                .findFirst().orElseThrow(() -> new AssertionError("West panel missing"));

        // Single-panel link is idempotent (guards the sim double-count bug).
        repo.linkPanelToScenario(top.getPanelIndex(), b);
        repo.linkPanelToScenario(top.getPanelIndex(), b);
        awaitDbWrites();
        assertEquals(1, repo.getScenarioComponentsForScenarioID(b).panels.size());

        // Single-panel copy forks a fresh row.
        repo.copyPanelToScenario(top.getPanelIndex(), b);
        awaitDbWrites();
        ScenarioComponents sb = repo.getScenarioComponentsForScenarioID(b);
        assertEquals(2, sb.panels.size());

        // Panel data presence by PVGIS parameters.
        assertFalse(repo.hasPvgisDataForParameters(53.349, -6.26, 136, 24));
        repo.savePanelData(pdRows(top.getPanelIndex()));
        assertTrue(repo.hasPvgisDataForParameters(53.349, -6.26, 136, 24));
        assertTrue(repo.getScenarioNamesAtLocation(53.349, -6.26, 136, 24).contains(NAME));

        // Characterized contract: checkForMissingPanelData is TRUE when every
        // linked panel has data ("OK"), despite the name.
        assertFalse(repo.checkForMissingPanelData(a));
        repo.savePanelData(pdRows(west.getPanelIndex()));
        assertTrue(repo.checkForMissingPanelData(a));

        repo.removeOldPanelData(top.getPanelIndex());
        assertFalse(repo.hasPvgisDataForParameters(53.349, -6.26, 136, 24));
    }

    @Test
    public void linkAllComponentsSharesEverythingAtOnce() {
        long a = insertFixture();
        long b = insertEmpty("Target");
        HWDivert divert = new HWDivert();
        divert.setActive(true);

        repo.linkAllComponentsFromScenario(a, b, divert);
        awaitDbWrites();

        ScenarioComponents sa = repo.getScenarioComponentsForScenarioID(a);
        ScenarioComponents sb = repo.getScenarioComponentsForScenarioID(b);
        assertEquals(ids(sa.inverters, Inverter::getInverterIndex),
                ids(sb.inverters, Inverter::getInverterIndex));
        assertEquals(ids(sa.panels, Panel::getPanelIndex),
                ids(sb.panels, Panel::getPanelIndex));
        assertEquals(sa.loadProfile.getLoadProfileIndex(),
                sb.loadProfile.getLoadProfileIndex());
        assertEquals(sa.hwSystem.getHwSystemIndex(), sb.hwSystem.getHwSystemIndex());
        assertEquals(sa.heatPumps.get(0).getHeatPumpIndex(),
                sb.heatPumps.get(0).getHeatPumpIndex());
        assertEquals(2, sb.batteries.size());
        assertEquals(2, sb.loadShifts.size());
        assertEquals(2, sb.discharges.size());
        assertEquals(2, sb.evCharges.size());
        assertEquals(2, sb.hwSchedules.size());
        assertNotNull("HW divert is replayed from the passed object, not linked",
                sb.hwDivert);

        // Orphan sweep must keep everything that is referenced.
        repo.deleteOrphanComponents();
        awaitDbWrites();
        ScenarioComponents saAfter = repo.getScenarioComponentsForScenarioID(a);
        assertEquals(2, saAfter.inverters.size());
        assertEquals(2, saAfter.panels.size());
        assertEquals(1, saAfter.heatPumps.size());
    }

    // ---- helpers ----

    private interface Id<T> { long id(T t); }

    private static <T> Set<Long> ids(java.util.List<T> list, Id<T> f) {
        return list.stream().map(f::id).collect(Collectors.toSet());
    }

    private static ArrayList<LoadProfileData> lpRows(long lpId) {
        ArrayList<LoadProfileData> rows = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            LoadProfileData r = new LoadProfileData();
            r.setLoadProfileID(lpId);
            r.setDate("2001-01-01");
            r.setMinute(String.format("%02d:%02d", 0, i * 5));
            r.setLoad(0.25 + i * 0.05);
            r.setMod(i * 5);
            r.setDow(1);
            r.setDo2001(1);
            r.setMillisSinceEpoch(978307200000L + i * 300000L);
            rows.add(r);
        }
        return rows;
    }

    private static ArrayList<PanelData> pdRows(long panelId) {
        ArrayList<PanelData> rows = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            PanelData r = new PanelData();
            r.setPanelID(panelId);
            r.setDate("2001-06-01");
            r.setMinute(String.format("%02d:%02d", 12, i * 5));
            r.setPv(0.4 + i * 0.1);
            r.setMod(720 + i * 5);
            r.setDow(5);
            r.setDo2001(152);
            r.setMillisSinceEpoch(991396800000L + i * 300000L);
            rows.add(r);
        }
        return rows;
    }
}
