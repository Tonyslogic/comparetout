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

import android.database.sqlite.SQLiteConstraintException;

import com.tfcode.comparetout.model.ScenarioDAO;
import com.tfcode.comparetout.model.ToutcDB;
import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.DischargeToGrid;
import com.tfcode.comparetout.model.scenario.EVCharge;
import com.tfcode.comparetout.model.scenario.EVDivert;
import com.tfcode.comparetout.model.scenario.HWDivert;
import com.tfcode.comparetout.model.scenario.HWSchedule;
import com.tfcode.comparetout.model.scenario.HWSystem;
import com.tfcode.comparetout.model.scenario.HeatPump;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.Scenario2Battery;
import com.tfcode.comparetout.model.scenario.Scenario2DischargeToGrid;
import com.tfcode.comparetout.model.scenario.Scenario2EVCharge;
import com.tfcode.comparetout.model.scenario.Scenario2EVDivert;
import com.tfcode.comparetout.model.scenario.Scenario2HWDivert;
import com.tfcode.comparetout.model.scenario.Scenario2HWSchedule;
import com.tfcode.comparetout.model.scenario.Scenario2HWSystem;
import com.tfcode.comparetout.model.scenario.Scenario2HeatPump;
import com.tfcode.comparetout.model.scenario.Scenario2Inverter;
import com.tfcode.comparetout.model.scenario.Scenario2LoadProfile;
import com.tfcode.comparetout.model.scenario.Scenario2LoadShift;
import com.tfcode.comparetout.model.scenario.Scenario2Panel;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;

import java.util.ArrayList;
import java.util.List;

/**
 * Cross-domain scenario lifecycle orchestration — create/clobber, delete,
 * copy, export, component read, and bulk link — moved verbatim from
 * ScenarioDAO's concrete {@code @Transaction} methods (mega-refactor C9). Each
 * body is the original wrapped in {@code db.runInTransaction} — identical
 * atomicity, and nested lifecycle calls (copy → add, add clobber → delete) nest
 * their transactions exactly as the original nested {@code @Transaction} methods
 * did. Every low-level query stays on ScenarioDAO (the shared primitive layer)
 * and is reached via {@code scenarioDAO}; the per-domain link/save helpers also
 * remain on ScenarioDAO and are called here through it.
 */
public class ScenarioLifecycleOps {

    private final ToutcDB db;
    private final ScenarioDAO scenarioDAO;

    public ScenarioLifecycleOps(ToutcDB db) {
        this.db = db;
        this.scenarioDAO = db.scenarioDAO();
    }

    public long addNewScenarioWithComponents(Scenario scenario, ScenarioComponents components, boolean clobber) {
        return db.runInTransaction(() -> {
            if (clobber) {
                long oldScenarioID = scenarioDAO.getScenarioID(scenario.getScenarioName());
                deleteScenario((int) oldScenarioID);
            }
            long scenarioID = 0;
            try {
                if (!(null == components.inverters) && !components.inverters.isEmpty())
                    scenario.setHasInverters(true);
                if (!(null == components.batteries) && !components.batteries.isEmpty())
                    scenario.setHasBatteries(true);
                if (!(null == components.panels) && !components.panels.isEmpty())
                    scenario.setHasPanels(true);
                if (!(null == components.hwSystem)) scenario.setHasHWSystem(true);
                if (!(null == components.loadProfile)) scenario.setHasLoadProfiles(true);
                if (!(null == components.loadShifts) && !components.loadShifts.isEmpty())
                    scenario.setHasLoadShifts(true);
                if (!(null == components.discharges) && !components.discharges.isEmpty())
                    scenario.setHasDischarges(true);
                if (!(null == components.evCharges) && !components.evCharges.isEmpty())
                    scenario.setHasEVCharges(true);
                if (!(null == components.hwSchedules) && !components.hwSchedules.isEmpty())
                    scenario.setHasHWSchedules(true);
                if (!(null == components.hwDivert) && (components.hwDivert.isActive()))
                    scenario.setHasHWDivert(true);
                if (!(null == components.evDiverts) && (!components.evDiverts.isEmpty()))
                    scenario.setHasEVDivert(true);
                if (!(null == components.heatPumps) && !components.heatPumps.isEmpty())
                    scenario.setHasHeatPump(true);

                scenarioID = scenarioDAO.addNewScenario(scenario);
                if (!(null == components.inverters)) {
                    for (Inverter i : components.inverters) {
                        long inverterID = scenarioDAO.addNewInverter(i);
                        Scenario2Inverter s2i = new Scenario2Inverter();
                        s2i.setScenarioID(scenarioID);
                        s2i.setInverterID(inverterID);
                        scenarioDAO.addNewScenario2Inverter(s2i);
                    }
                }
                if (!(null == components.batteries)) {
                    for (Battery b : components.batteries) {
                        long batteryID = scenarioDAO.addNewBattery(b);
                        Scenario2Battery s2b = new Scenario2Battery();
                        s2b.setScenarioID(scenarioID);
                        s2b.setBatteryID(batteryID);
                        scenarioDAO.addNewScenario2Battery(s2b);
                    }
                }
                if (!(null == components.panels)) {
                    for (Panel p : components.panels) {
                        long panelsID = p.getPanelIndex();
                        if (panelsID == 0) panelsID = scenarioDAO.addNewPanels(p);
                        Scenario2Panel s2p = new Scenario2Panel();
                        s2p.setScenarioID(scenarioID);
                        s2p.setPanelID(panelsID);
                        scenarioDAO.addNewScenario2Panel(s2p);
                    }
                }
                if (!(null == components.hwSystem)) {
                    long hwSystemID = scenarioDAO.addNewHWSystem(components.hwSystem);
                    Scenario2HWSystem s2hws = new Scenario2HWSystem();
                    s2hws.setScenarioID(scenarioID);
                    s2hws.setHwSystemID(hwSystemID);
                    scenarioDAO.addNewScenario2HWSystem(s2hws);
                }
                if (!(null == components.loadProfile)) {
                    long loadProfileID = components.loadProfile.getLoadProfileIndex();
                    if (loadProfileID == 0) loadProfileID = scenarioDAO.addNewLoadProfile(components.loadProfile);
                    Scenario2LoadProfile s2lp = new Scenario2LoadProfile();
                    s2lp.setScenarioID(scenarioID);
                    s2lp.setLoadProfileID(loadProfileID);
                    scenarioDAO.addNewScenario2LoadProfile(s2lp);
                }
                if (!(null == components.loadShifts)) {
                    for (LoadShift ls : components.loadShifts) {
                        long loadShiftID = scenarioDAO.addNewLoadShift(ls);
                        Scenario2LoadShift s2ls = new Scenario2LoadShift();
                        s2ls.setScenarioID(scenarioID);
                        s2ls.setLoadShiftID(loadShiftID);
                        scenarioDAO.addNewScenario2LoadShift(s2ls);
                    }
                }
                if (!(null == components.discharges)) {
                    for (DischargeToGrid discharge : components.discharges) {
                        long dischargeID = scenarioDAO.addNewDischarge(discharge);
                        Scenario2DischargeToGrid s2d = new Scenario2DischargeToGrid();
                        s2d.setScenarioID(scenarioID);
                        s2d.setDischargeID(dischargeID);
                        scenarioDAO.addNewScenario2Discharge(s2d);
                    }
                }
                if (!(null == components.evCharges)) {
                    for (EVCharge evc : components.evCharges) {
                        long evChargeID = scenarioDAO.addNewEVCharge(evc);
                        Scenario2EVCharge s2evc = new Scenario2EVCharge();
                        s2evc.setScenarioID(scenarioID);
                        s2evc.setEvChargeID(evChargeID);
                        scenarioDAO.addNewScenario2EVCharge(s2evc);
                    }
                }
                if (!(null == components.hwSchedules)) {
                    for (HWSchedule hws : components.hwSchedules) {
                        long hwScheduleID = scenarioDAO.addNewHWSchedule(hws);
                        Scenario2HWSchedule s2hws = new Scenario2HWSchedule();
                        s2hws.setScenarioID(scenarioID);
                        s2hws.setHwScheduleID(hwScheduleID);
                        scenarioDAO.addNewScenario2HWSchedule(s2hws);
                    }
                }
                if (!(null == components.hwDivert)) {
                    long hwDivertID = scenarioDAO.addNewHWDivert(components.hwDivert);
                    Scenario2HWDivert s2hwd = new Scenario2HWDivert();
                    s2hwd.setScenarioID(scenarioID);
                    s2hwd.setHwDivertID(hwDivertID);
                    scenarioDAO.addNewScenario2HWDivert(s2hwd);
                }
                if (!(null == components.evDiverts)) {
                    for (EVDivert evd : components.evDiverts) {
                        long evDivertID = scenarioDAO.addNewEVDivert(evd);
                        Scenario2EVDivert s2evd = new Scenario2EVDivert();
                        s2evd.setScenarioID(scenarioID);
                        s2evd.setEvDivertID(evDivertID);
                        scenarioDAO.addNewScenario2EVDivert(s2evd);
                    }
                }
                if (!(null == components.heatPumps)) {
                    for (HeatPump hp : components.heatPumps) {
                        long heatPumpID = scenarioDAO.addNewHeatPump(hp);
                        Scenario2HeatPump s2hp = new Scenario2HeatPump();
                        s2hp.setScenarioID(scenarioID);
                        s2hp.setHeatPumpID(heatPumpID);
                        scenarioDAO.addNewScenario2HeatPump(s2hp);
                    }
                }
            } catch (SQLiteConstraintException e) {
                // The only UNIQUE constraint reachable here is scenarios.scenarioName, so this means a scenario
                // with this name already exists. addNewScenario aborts before assigning scenarioID, so the
                // transaction inserted nothing — return 0 (a clear "not created" sentinel) rather than a dead id
                // the caller mistakes for success and then dereferences (e.g. savePanel -> getScenario(0) -> NPE).
                System.out.println("addNewScenarioWithComponents: duplicate scenario name '"
                        + scenario.getScenarioName() + "' — not created");
                scenarioID = 0;
            }
            return scenarioID;
        });
    }

    public void deleteScenario(int id) {
        db.runInTransaction(() -> {
            scenarioDAO.deleteScenarioRow(id);

            // Remove all junction table relationships
            scenarioDAO.deleteBatteryRelationsForScenario(id);
            scenarioDAO.deleteHeatPumpRelationsForScenario(id);
            scenarioDAO.deleteEVChargeRelationsForScenario(id);
            scenarioDAO.deleteEVDivertRelationsForScenario(id);
            scenarioDAO.deleteHWDivertRelationsForScenario(id);
            scenarioDAO.deleteHWScheduleRelationsForScenario(id);
            scenarioDAO.deleteHWSystemRelationsForScenario(id);
            scenarioDAO.deleteInverterRelationsForScenario(id);
            scenarioDAO.deleteLoadProfileRelationsForScenario(id);
            scenarioDAO.deleteLoadShiftRelationsForScenario(id);
            scenarioDAO.deleteDischargeRelationsForScenario(id);
            scenarioDAO.deletePanelRelationsForScenario(id);

            // Clean up orphaned components (not referenced by any scenario)
            scenarioDAO.deleteOrphanBatteries();
            scenarioDAO.deleteOrphanHeatPumps();
            scenarioDAO.deleteOrphanEVCharges();
            scenarioDAO.deleteOrphanEVDiverts();
            scenarioDAO.deleteOrphanHWDiverts();
            scenarioDAO.deleteOrphanHWSchedules();
            scenarioDAO.deleteOrphanHWSystems();
            scenarioDAO.deleteOrphanInverters();
            scenarioDAO.deleteOrphanLoadProfiles();
            scenarioDAO.deleteOrphanLoadShifts();
            scenarioDAO.deleteOrphanDischarges();
            scenarioDAO.deleteOrphanPanels();

            // Clean up dependent data
            scenarioDAO.deleteOrphanLoadProfileData();
            // Prune paneldata rows whose panel was just orphaned above. Without
            // this, fetched PVGIS rows accumulate in the DB after every scenario
            // delete. Matches the per-panel deletePanelFromScenario flow.
            scenarioDAO.deleteOrphanPanelData();
            scenarioDAO.deleteSimulationDataForScenarioID(id);
            scenarioDAO.deleteCostingDataForScenarioID(id);
            // Drop the readiness row too so it can't orphan once the scenario is gone.
            scenarioDAO.deleteReadinessForScenario(id);
        });
    }

    public void copyScenario(int id) {
        db.runInTransaction(() -> {
            Scenario scenario = scenarioDAO.getScenarioForID(id);
            List<Battery> batteries = scenarioDAO.getBatteriesForScenarioID(id);
            List<HeatPump> heatPumps = scenarioDAO.getHeatPumpsForScenarioID(id);
            List<EVCharge> evCharges = scenarioDAO.getEVChargesForScenarioID(id);
            List<EVDivert> evDiverts = scenarioDAO.getEVDivertForScenarioID(id);
            HWDivert hwDivert = scenarioDAO.getHWDivertForScenarioID(id);
            List<HWSchedule> hwSchedules = scenarioDAO.getHWSchedulesForScenarioID(id);
            HWSystem hwSystem = scenarioDAO.getHWSystemForScenarioID(id);
            List<Inverter> inverters = scenarioDAO.getInvertersForScenarioID(id);
            LoadProfile loadProfile = scenarioDAO.getLoadProfileForScenarioID(id);
            List<LoadShift> loadShifts = scenarioDAO.getLoadShiftsForScenarioID(id);
            List<DischargeToGrid> discharges = scenarioDAO.getDischargesForScenarioID(id);
            List<Panel> panels = scenarioDAO.getPanelsForScenarioID(id);

            scenario.setScenarioName(scenario.getScenarioName() + "_copy");
            scenario.setScenarioIndex(0);
            for (Battery b : batteries) b.setBatteryIndex(0);
            for (HeatPump hp : heatPumps) hp.setHeatPumpIndex(0);
            for (EVCharge e : evCharges) e.setEvChargeIndex(0);
            for (EVDivert d : evDiverts) d.setEvDivertIndex(0);
            if (!(null == hwDivert)) hwDivert.setHwDivertIndex(0);
            for (HWSchedule h : hwSchedules) h.setHwScheduleIndex(0);
            if (!(null == hwSystem)) hwSystem.setHwSystemIndex(0);
            for (Inverter i : inverters) i.setInverterIndex(0);
            if (!(null == loadProfile)) {
                long oldLoadProfileID = loadProfile.getLoadProfileIndex();
                loadProfile.setLoadProfileIndex(0);
                long newLoadProfileID = scenarioDAO.addNewLoadProfile(loadProfile);
                scenarioDAO.copyLoadProfileData(oldLoadProfileID, newLoadProfileID);
                loadProfile.setLoadProfileIndex(newLoadProfileID);
            }
            for (LoadShift l : loadShifts) l.setLoadShiftIndex(0);
            for (DischargeToGrid d : discharges) d.setD2gIndex(0);
            for (Panel p : panels) {
                long oldPanelID = p.getPanelIndex();
                p.setPanelIndex(0);
                long newPanelID = scenarioDAO.addNewPanels(p);
                scenarioDAO.copyPanelData(oldPanelID, newPanelID);
                p.setPanelIndex(newPanelID);
            }

            ScenarioComponents copyComponents = new ScenarioComponents(
                    scenario, inverters, batteries, panels, hwSystem,
                    loadProfile, loadShifts, discharges, evCharges, hwSchedules,
                    hwDivert, evDiverts);
            copyComponents.heatPumps = heatPumps;
            addNewScenarioWithComponents(scenario, copyComponents, false);
        });
    }

    public List<ScenarioComponents> getAllScenariosForExport() {
        return db.runInTransaction(() -> {
            List<ScenarioComponents> ret = new ArrayList<>();
            List<Scenario> scenarios = scenarioDAO.getScenarios();
            for (Scenario scenario: scenarios){
                ScenarioComponents scenarioComponents = new ScenarioComponents(scenario,
                        scenarioDAO.getInvertersForScenarioID(scenario.getScenarioIndex()),
                        scenarioDAO.getBatteriesForScenarioID(scenario.getScenarioIndex()),
                        scenarioDAO.getPanelsForScenarioID(scenario.getScenarioIndex()),
                        scenarioDAO.getHWSystemForScenarioID(scenario.getScenarioIndex()),
                        scenarioDAO.getLoadProfileForScenarioID(scenario.getScenarioIndex()),
                        scenarioDAO.getLoadShiftsForScenarioID(scenario.getScenarioIndex()),
                        scenarioDAO.getDischargesForScenarioID(scenario.getScenarioIndex()),
                        scenarioDAO.getEVChargesForScenarioID(scenario.getScenarioIndex()),
                        scenarioDAO.getHWSchedulesForScenarioID(scenario.getScenarioIndex()),
                        scenarioDAO.getHWDivertForScenarioID(scenario.getScenarioIndex()),
                        scenarioDAO.getEVDivertForScenarioID(scenario.getScenarioIndex())
                );
                scenarioComponents.heatPumps = scenarioDAO.getHeatPumpsForScenarioID(scenario.getScenarioIndex());
                ret.add(scenarioComponents);
            }
            return ret;
        });
    }

    public ScenarioComponents getScenarioComponentsForScenarioID(long scenarioID) {
        return db.runInTransaction(() -> {
            ScenarioComponents components = new ScenarioComponents(
                    scenarioDAO.getScenarioForID(scenarioID),
                    scenarioDAO.getInvertersForScenarioID(scenarioID),
                    scenarioDAO.getBatteriesForScenarioID(scenarioID),
                    scenarioDAO.getPanelsForScenarioID(scenarioID),
                    scenarioDAO.getHWSystemForScenarioID(scenarioID),
                    scenarioDAO.getLoadProfileForScenarioID(scenarioID),
                    scenarioDAO.getLoadShiftsForScenarioID(scenarioID),
                    scenarioDAO.getDischargesForScenarioID(scenarioID),
                    scenarioDAO.getEVChargesForScenarioID(scenarioID),
                    scenarioDAO.getHWSchedulesForScenarioID(scenarioID),
                    scenarioDAO.getHWDivertForScenarioID(scenarioID),
                    scenarioDAO.getEVDivertForScenarioID(scenarioID)
            );
            components.heatPumps = scenarioDAO.getHeatPumpsForScenarioID(scenarioID);
            return components;
        });
    }

    public void linkAllComponentsFromScenario(long fromScenarioID, long toScenarioID, HWDivert hwDivert) {
        db.runInTransaction(() -> {
            Long to = toScenarioID;
            scenarioDAO.linkLoadProfileFromScenario(fromScenarioID, to);
            scenarioDAO.linkEVChargeFromScenario(fromScenarioID, to);
            scenarioDAO.linkInverterFromScenario(fromScenarioID, to);
            scenarioDAO.linkPanelFromScenario(fromScenarioID, to);
            scenarioDAO.linkBatteryFromScenario(fromScenarioID, to);
            scenarioDAO.linkHeatPumpFromScenario(fromScenarioID, to);
            scenarioDAO.linkLoadShiftFromScenario(fromScenarioID, to);
            scenarioDAO.linkDischargeFromScenario(fromScenarioID, to);
            scenarioDAO.linkHWSystemFromScenario(fromScenarioID, to);
            scenarioDAO.linkHWScheduleFromScenario(fromScenarioID, toScenarioID);
            // The HW divert is replayed from wizard/builder state (not linked from the source), but it lives in
            // the same transaction so its has*-flag write can't race/clobber the links' flag writes.
            if (hwDivert != null) scenarioDAO.saveHWDivert(to, hwDivert);
        });
    }
}
