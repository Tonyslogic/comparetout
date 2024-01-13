/*
 * Copyright (c) 2023. Tony Finnerty
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

import android.database.sqlite.SQLiteConstraintException;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.RewriteQueriesToDropUnusedColumns;
import androidx.room.Transaction;
import androidx.room.Update;

import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.EVCharge;
import com.tfcode.comparetout.model.scenario.EVDivert;
import com.tfcode.comparetout.model.scenario.HWDivert;
import com.tfcode.comparetout.model.scenario.HWSchedule;
import com.tfcode.comparetout.model.scenario.HWSystem;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.model.scenario.LoadProfileData;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.PanelData;
import com.tfcode.comparetout.model.scenario.PanelPVSummary;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.Scenario2Battery;
import com.tfcode.comparetout.model.scenario.Scenario2EVCharge;
import com.tfcode.comparetout.model.scenario.Scenario2EVDivert;
import com.tfcode.comparetout.model.scenario.Scenario2HWDivert;
import com.tfcode.comparetout.model.scenario.Scenario2HWSchedule;
import com.tfcode.comparetout.model.scenario.Scenario2HWSystem;
import com.tfcode.comparetout.model.scenario.Scenario2Inverter;
import com.tfcode.comparetout.model.scenario.Scenario2LoadProfile;
import com.tfcode.comparetout.model.scenario.Scenario2LoadShift;
import com.tfcode.comparetout.model.scenario.Scenario2Panel;
import com.tfcode.comparetout.model.scenario.ScenarioBarChartData;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;
import com.tfcode.comparetout.model.scenario.ScenarioLineGraphData;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;
import com.tfcode.comparetout.model.scenario.SimKPIs;
import com.tfcode.comparetout.model.scenario.SimulationInputData;

import java.util.ArrayList;
import java.util.List;

@Dao
public abstract class ScenarioDAO {

    @Insert
    abstract long addNewScenario(Scenario scenario);

    @Insert
    abstract long addNewInverter(Inverter inverter);

    @Insert
    abstract long addNewBattery(Battery battery);

    @Insert
    abstract long addNewPanels(Panel panel);

    @Insert
    abstract long addNewHWSystem(HWSystem hwSystem);

    @Insert
    abstract long addNewLoadProfile(LoadProfile loadProfile);

    @Insert
    abstract long addNewLoadShift(LoadShift loadShift);

    @Insert
    abstract long addNewEVCharge(EVCharge evCharge);

    @Insert
    abstract long addNewHWSchedule(HWSchedule hwSchedule);

    @Insert
    abstract long addNewHWDivert(HWDivert hwDivert);

    @Insert
    abstract long addNewEVDivert(EVDivert evDivert);

    @Insert
    abstract void addNewScenario2Inverter(Scenario2Inverter scenario2Inverter);

    @Insert
    abstract void addNewScenario2Battery(Scenario2Battery scenario2Battery);

    @Insert
    abstract void addNewScenario2Panel(Scenario2Panel scenario2Panel);

    @Insert
    abstract void addNewScenario2HWSystem(Scenario2HWSystem scenario2HWSystem);

    @Insert
    abstract void addNewScenario2LoadProfile(Scenario2LoadProfile scenario2LoadProfile);

    @Insert
    abstract void addNewScenario2LoadShift(Scenario2LoadShift scenario2LoadShift);

    @Insert
    abstract void addNewScenario2EVCharge(Scenario2EVCharge scenario2EVCharge);

    @Insert
    abstract void addNewScenario2HWSchedule(Scenario2HWSchedule scenario2HWSchedule);

    @Insert
    abstract void addNewScenario2HWDivert(Scenario2HWDivert scenario2HWDivert);

    @Insert
    abstract void addNewScenario2EVDivert(Scenario2EVDivert scenario2EVDivert);

    @Transaction
    long addNewScenarioWithComponents(Scenario scenario, ScenarioComponents components) {
        long scenarioID = 0;
        try {
            if (!(null == components.inverters) && components.inverters.size() > 0)
                scenario.setHasInverters(true);
            if (!(null == components.batteries) && components.batteries.size() > 0)
                scenario.setHasBatteries(true);
            if (!(null == components.panels) && components.panels.size() > 0)
                scenario.setHasPanels(true);
            if (!(null == components.hwSystem)) scenario.setHasHWSystem(true);
            if (!(null == components.loadProfile)) scenario.setHasLoadProfiles(true);
            if (!(null == components.loadShifts) && components.loadShifts.size() > 0)
                scenario.setHasLoadShifts(true);
            if (!(null == components.evCharges) && components.evCharges.size() > 0)
                scenario.setHasEVCharges(true);
            if (!(null == components.hwSchedules) && components.hwSchedules.size() > 0)
                scenario.setHasHWSchedules(true);
            if (!(null == components.hwDivert) && (components.hwDivert.isActive()))
                scenario.setHasHWDivert(true);
            if (!(null == components.evDiverts) && (components.evDiverts.size() > 0))
                scenario.setHasEVDivert(true);

            scenarioID = addNewScenario(scenario);
            if (!(null == components.inverters)) {
                for (Inverter i : components.inverters) {
                    long inverterID = addNewInverter(i);
                    Scenario2Inverter s2i = new Scenario2Inverter();
                    s2i.setScenarioID(scenarioID);
                    s2i.setInverterID(inverterID);
                    addNewScenario2Inverter(s2i);
                }
            }
            if (!(null == components.batteries)) {
                for (Battery b : components.batteries) {
                    long batteryID = addNewBattery(b);
                    Scenario2Battery s2b = new Scenario2Battery();
                    s2b.setScenarioID(scenarioID);
                    s2b.setBatteryID(batteryID);
                    addNewScenario2Battery(s2b);
                }
            }
            if (!(null == components.panels)) {
                for (Panel p : components.panels) {
                    long panelsID = p.getPanelIndex();
                    if (panelsID == 0) panelsID = addNewPanels(p);
                    Scenario2Panel s2p = new Scenario2Panel();
                    s2p.setScenarioID(scenarioID);
                    s2p.setPanelID(panelsID);
                    addNewScenario2Panel(s2p);
                }
            }
            if (!(null == components.hwSystem)) {
                long hwSystemID = addNewHWSystem(components.hwSystem);
                Scenario2HWSystem s2hws = new Scenario2HWSystem();
                s2hws.setScenarioID(scenarioID);
                s2hws.setHwSystemID(hwSystemID);
                addNewScenario2HWSystem(s2hws);
            }
            if (!(null == components.loadProfile)) {
                long loadProfileID = components.loadProfile.getLoadProfileIndex();
                if (loadProfileID == 0) loadProfileID = addNewLoadProfile(components.loadProfile);
                Scenario2LoadProfile s2lp = new Scenario2LoadProfile();
                s2lp.setScenarioID(scenarioID);
                s2lp.setLoadProfileID(loadProfileID);
                addNewScenario2LoadProfile(s2lp);
            }
            if (!(null == components.loadShifts)) {
                for (LoadShift ls : components.loadShifts) {
                    long loadShiftID = addNewLoadShift(ls);
                    Scenario2LoadShift s2ls = new Scenario2LoadShift();
                    s2ls.setScenarioID(scenarioID);
                    s2ls.setLoadShiftID(loadShiftID);
                    addNewScenario2LoadShift(s2ls);
                }
            }
            if (!(null == components.evCharges)) {
                for (EVCharge evc : components.evCharges) {
                    long evChargeID = addNewEVCharge(evc);
                    Scenario2EVCharge s2evc = new Scenario2EVCharge();
                    s2evc.setScenarioID(scenarioID);
                    s2evc.setEvChargeID(evChargeID);
                    addNewScenario2EVCharge(s2evc);
                }
            }
            if (!(null == components.hwSchedules)) {
                for (HWSchedule hws : components.hwSchedules) {
                    long hwScheduleID = addNewHWSchedule(hws);
                    Scenario2HWSchedule s2hws = new Scenario2HWSchedule();
                    s2hws.setScenarioID(scenarioID);
                    s2hws.setHwScheduleID(hwScheduleID);
                    addNewScenario2HWSchedule(s2hws);
                }
            }
            if (!(null == components.hwDivert)) {
                long hwDivertID = addNewHWDivert(components.hwDivert);
                Scenario2HWDivert s2hwd = new Scenario2HWDivert();
                s2hwd.setScenarioID(scenarioID);
                s2hwd.setHwDivertID(hwDivertID);
                addNewScenario2HWDivert(s2hwd);
            }
            if (!(null == components.evDiverts)) {
                for (EVDivert evd : components.evDiverts) {
                    long evDivertID = addNewEVDivert(evd);
                    Scenario2EVDivert s2evd = new Scenario2EVDivert();
                    s2evd.setScenarioID(scenarioID);
                    s2evd.setEvDivertID(evDivertID);
                    addNewScenario2EVDivert(s2evd);
                }
            }
        } catch (SQLiteConstraintException e) {
            System.out.println("Silently ignoring a duplicate added as new");
        }
        return scenarioID;
    }

    @Query("SELECT * FROM scenarios ORDER BY scenarios.scenarioName ASC")
    public abstract LiveData<List<Scenario>> loadScenarios();

    @Query("SELECT * FROM inverters, scenario2inverter " +
            "WHERE scenarioID = :id AND inverters.inverterIndex = scenario2inverter.inverterID")
    @RewriteQueriesToDropUnusedColumns
    public abstract List<Inverter> getInvertersForScenarioID (long id);

    @Query("SELECT * FROM batteries, scenario2battery " +
            "WHERE scenarioID = :id AND batteries.batteryIndex = scenario2battery.batteryID")
    @RewriteQueriesToDropUnusedColumns
    public abstract List<Battery> getBatteriesForScenarioID(long id);

    @Query("SELECT * FROM panels, scenario2panel " +
            "WHERE scenarioID = :id AND panels.panelIndex = scenario2panel.panelID")
    @RewriteQueriesToDropUnusedColumns
    public abstract List<Panel> getPanelsForScenarioID(long id);

    @Query("SELECT * FROM hwsystem, scenario2hwsystem " +
            "WHERE scenarioID = :id AND hwsystem.hwSystemIndex = scenario2hwsystem.hwSystemID")
    @RewriteQueriesToDropUnusedColumns
    public abstract HWSystem getHWSystemForScenarioID(long id);

    @Query("SELECT * FROM loadprofile, scenario2loadprofile " +
            "WHERE scenarioID = :id AND loadprofile.loadProfileIndex = loadProfileID")
    @RewriteQueriesToDropUnusedColumns
    public abstract LoadProfile getLoadProfileForScenarioID(long id);

    @Query("SELECT * FROM loadshift, scenario2loadshift " +
            "WHERE scenarioID = :id AND loadshift.loadShiftIndex = loadShiftID")
    @RewriteQueriesToDropUnusedColumns
    public abstract List<LoadShift> getLoadShiftsForScenarioID(long id);

    @Query("SELECT * FROM evcharge, scenario2evcharge " +
            "WHERE scenarioID = :id AND evcharge.evChargeIndex = evChargeID")
    @RewriteQueriesToDropUnusedColumns
    public abstract List<EVCharge> getEVChargesForScenarioID(long id);

    @Query("SELECT * FROM hwschedule, scenario2hwschedule " +
            "WHERE scenarioID = :id AND hwschedule.hwScheduleIndex = hwScheduleID")
    @RewriteQueriesToDropUnusedColumns
    public abstract List<HWSchedule> getHWSchedulesForScenarioID(long id);

    @Query("SELECT * FROM hwdivert, scenario2hwdivert " +
            "WHERE scenarioID = :id AND hwdivert.hwDivertIndex = hwDivertID")
    @RewriteQueriesToDropUnusedColumns
    public abstract HWDivert getHWDivertForScenarioID(long id);

    @Query("SELECT * FROM evdivert, scenario2evdivert " +
            "WHERE scenarioID = :id AND evdivert.evDivertIndex = evDivertID")
    @RewriteQueriesToDropUnusedColumns
    public abstract List<EVDivert> getEVDivertForScenarioID(long id);

    @Update (entity = Scenario.class)
    public abstract void updateScenario(Scenario scenario);

    @Query("SELECT * FROM loadprofile, scenario2loadprofile " +
            "WHERE scenarioID = :scenarioID AND loadProfile.loadProfileIndex = loadProfileID")
    @RewriteQueriesToDropUnusedColumns
    public abstract LiveData<LoadProfile> getLoadProfile(Long scenarioID);

    @Query("SELECT * FROM loadprofile WHERE loadProfileIndex = :id")
    public abstract LoadProfile getLoadProfileWithLoadProfileID(long id);

    @Update (entity = LoadProfile.class)
    public abstract void updateLoadProfile(LoadProfile loadProfile);

    @Query("SELECT * FROM scenarios WHERE scenarioIndex = :scenarioID")
    public abstract Scenario getScenario(long scenarioID);


    @Transaction
    public long saveLoadProfile(Long scenarioID, LoadProfile loadProfile) {
        long loadProfileID = loadProfile.getLoadProfileIndex();
        if (loadProfileID == 0) {
            loadProfileID = addNewLoadProfile(loadProfile);
            deleteLoadProfileRelationsForScenario(Math.toIntExact(scenarioID));
        }
        else {
            updateLoadProfile(loadProfile);
        }
        Scenario2LoadProfile s2lp = new Scenario2LoadProfile();
        s2lp.setScenarioID(scenarioID);
        s2lp.setLoadProfileID(loadProfileID);
        addNewScenario2LoadProfile(s2lp);

        Scenario scenario = getScenario(scenarioID);
        scenario.setHasLoadProfiles(true);
        updateScenario(scenario);
        return loadProfileID;
    }

    @Update (entity = Inverter.class)
    public abstract void updateInverter(Inverter inverter);

    @Update (entity = Panel.class)
    public abstract void updatePanel(Panel panel);

    @Transaction
    public long saveInverter(Long scenarioID, Inverter inverter){
        long inverterID = inverter.getInverterIndex();
        if (inverterID == 0) {
            inverterID = addNewInverter(inverter);
            Scenario2Inverter s2i = new Scenario2Inverter();
            s2i.setScenarioID(scenarioID);
            s2i.setInverterID(inverterID);
            addNewScenario2Inverter(s2i);

            Scenario scenario = getScenario(scenarioID);
            scenario.setHasInverters(true);
            updateScenario(scenario);
        }
        else {
            updateInverter(inverter);
        }
        return inverterID;
    }

    @Query("SELECT DISTINCT loadProfileID FROM loadprofiledata WHERE loadProfileID = :id")
    public abstract long loadProfileDataCheck(long id);

    @Query("DELETE FROM loadprofiledata WHERE loadProfileID = :id")
    public abstract void deleteLoadProfileData(long id);

    @Insert(entity = LoadProfileData.class)
    public abstract void createLoadProfileDataEntries(ArrayList<LoadProfileData> rows);

    @Query("DELETE FROM scenariosimulationdata WHERE scenarioID IN (" +
            "SELECT scenarioID FROM scenario2loadprofile WHERE loadProfileID = :loadProfileID) ")
    public abstract void deleteSimulationDataForProfileID(long loadProfileID);

    @Query("DELETE FROM costings WHERE scenarioID IN (" +
            "SELECT scenarioID FROM scenario2loadprofile WHERE loadProfileID = :loadProfileID) ")
    public abstract void deleteCostingDataForProfileID(long loadProfileID);

    @Query("DELETE FROM scenariosimulationdata WHERE scenarioID = (" +
            "SELECT scenarioID FROM scenario2panel WHERE panelID = :panelID) ")
    public abstract void deleteSimulationDataForPanelID(long panelID);

    @Query("DELETE FROM costings WHERE scenarioID = (" +
            "SELECT scenarioID FROM scenario2panel WHERE panelID = :panelID) ")
    public abstract void deleteCostingDataForPanelID(long panelID);

    @Query("SELECT scenarioIndex FROM scenarios " +
            "WHERE scenarioIndex NOT IN (SELECT DISTINCT scenarioID FROM scenariosimulationdata) " +
            "AND scenarioIndex IN (SELECT DISTINCT scenarioID FROM scenario2loadprofile) " +
            "AND (SELECT DISTINCT loadProfileID FROM scenario2loadprofile WHERE scenarioID = scenarioIndex ) IN (SELECT DISTINCT loadProfileID FROM loadprofiledata)")
    public abstract List<Long> getAllScenariosThatNeedSimulation();

    @Query("SELECT * FROM scenarios WHERE scenarioIndex = :scenarioID")
    public abstract Scenario getScenarioForID(long scenarioID);

    @Query("SELECT A.date, A.minute, A.load, A.mod, A.dow, A.do2001, 0 AS TPV FROM " +
            "(SELECT * FROM loadprofiledata WHERE loadProfileID = " +
            "(SELECT loadProfileID FROM scenario2loadprofile WHERE scenarioID = :scenarioID)) AS A ORDER BY A.date, A.mod")
    public abstract List<SimulationInputData> getSimulationInputNoSolar(long scenarioID);

    @Query("SELECT pv FROM paneldata WHERE panelID = :panelID ORDER BY date, mod")
    public abstract List<Double> getSimulationInputForPanel(long panelID);

    @Insert(entity = ScenarioSimulationData.class)
    public abstract void saveSimulationDataForScenario(ArrayList<ScenarioSimulationData> simulationData);

    @Query("SELECT scenarioIndex FROM scenarios " +
            "WHERE scenarioIndex NOT IN (SELECT DISTINCT scenarioID FROM costings) " +
            "OR scenarioIndex IN (SELECT scenarioID FROM (SELECT scenarioID, COUNT(pricePlanId) AS costedPlans FROM costings GROUP BY scenarioID HAVING costedPlans < (SELECT COUNT() FROM PricePlans) )) " +
            "AND (SELECT COUNT(scenarioID) FROM scenariosimulationdata, scenarios WHERE scenariosimulationdata.scenarioID = scenarioIndex) > 0")
    public abstract List<Long> getAllScenariosThatNeedCosting();

    @Query("SELECT * FROM scenariosimulationdata WHERE scenarioID = :scenarioID " +
            "ORDER BY date, minuteOfDay")
    public abstract List<ScenarioSimulationData> getSimulationDataForScenario(long scenarioID);

    @Query("UPDATE scenarios SET isActive = :checked WHERE scenarioIndex = :id")
    public abstract void updateScenarioActiveStatus(int id, boolean checked);

    @Query("SELECT loadProfileIndex FROM loadprofile WHERE loadProfileIndex NOT IN " +
            "(SELECT DISTINCT loadProfileID FROM loadprofiledata)")
    public abstract List<Long> checkForMissingLoadProfileData();

    @Transaction
    public void deleteScenario(int id) {
        deleteScenarioRow(id);
        deleteBatteryRelationsForScenario(id);
        deleteEVChargeRelationsForScenario(id);
        deleteEVDivertRelationsForScenario(id);
        deleteHWDivertRelationsForScenario(id);
        deleteHWScheduleRelationsForScenario(id);
        deleteHWSystemRelationsForScenario(id);
        deleteInverterRelationsForScenario(id);
        deleteLoadProfileRelationsForScenario(id);
        deleteLoadShiftRelationsForScenario(id);
        deletePanelRelationsForScenario(id);

        // Delete orphans e.g. any battery id not in s2b
        deleteOrphanBatteries();
        deleteOrphanEVCharges();
        deleteOrphanEVDiverts();
        deleteOrphanHWDiverts();
        deleteOrphanHWSchedules();
        deleteOrphanHWSystems();
        deleteOrphanInverters();
        deleteOrphanLoadProfiles();
        deleteOrphanLoadShifts();
        deleteOrphanPanels();

        deleteOrphanLoadProfileData();
        deleteSimulationDataForScenarioID(id);
        deleteCostingDataForScenarioID(id);

    }

    @Query("DELETE FROM batteries WHERE batteryIndex NOT IN (SELECT batteryID FROM scenario2battery)")
    public abstract void deleteOrphanBatteries();

    @Query("DELETE FROM evcharge WHERE evChargeIndex NOT IN (SELECT evChargeID FROM scenario2evcharge)")
    public abstract void deleteOrphanEVCharges();

    @Query("DELETE FROM evdivert WHERE evDivertIndex NOT IN (SELECT evDivertID FROM scenario2evdivert)")
    public abstract void deleteOrphanEVDiverts();

    @Query("DELETE FROM hwdivert WHERE hwDivertIndex NOT IN (SELECT hwDivertID FROM scenario2hwdivert)")
    public abstract void deleteOrphanHWDiverts();

    @Query("DELETE FROM hwschedule WHERE hwScheduleIndex NOT IN (SELECT hwScheduleID FROM scenario2hwschedule)")
    public abstract void deleteOrphanHWSchedules();

    @Query("DELETE FROM hwsystem WHERE hwSystemIndex NOT IN (SELECT hwSystemID FROM scenario2hwsystem)")
    public abstract void deleteOrphanHWSystems();

    @Query("DELETE FROM inverters WHERE inverterIndex NOT IN (SELECT inverterID FROM scenario2inverter)")
    public abstract void deleteOrphanInverters();

    @Query("DELETE FROM loadprofile WHERE loadProfileIndex NOT IN (SELECT loadProfileID FROM scenario2loadprofile)")
    public abstract void deleteOrphanLoadProfiles();

    @Query("DELETE FROM loadshift WHERE loadShiftIndex NOT IN (SELECT loadShiftID FROM scenario2loadshift)")
    public abstract void deleteOrphanLoadShifts();

    @Query("DELETE FROM panels WHERE panelIndex NOT IN (SELECT panelID FROM scenario2panel)")
    public abstract void deleteOrphanPanels();

    @Query("DELETE FROM paneldata WHERE panelID NOT IN (SELECT DISTINCT panelIndex FROM panels)")
    public abstract void deleteOrphanPanelData();

    @Query("DELETE FROM loadprofiledata WHERE loadProfileID NOT IN (SELECT loadProfileID FROM scenario2loadprofile)")
    public abstract void deleteOrphanLoadProfileData();

    @Query("DELETE FROM scenarios WHERE scenarioIndex = :id")
    public abstract void deleteScenarioRow(int id);

    @Query("DELETE FROM scenario2battery WHERE scenarioID = :id")
    public abstract void deleteBatteryRelationsForScenario(int id);

    @Query("DELETE FROM scenario2evcharge WHERE scenarioID = :id")
    public abstract void deleteEVChargeRelationsForScenario(int id);

    @Query("DELETE FROM scenario2evdivert WHERE scenarioID = :id")
    public abstract void deleteEVDivertRelationsForScenario(int id);

    @Query("DELETE FROM scenario2hwdivert WHERE scenarioID = :id")
    public abstract void deleteHWDivertRelationsForScenario(int id);

    @Query("DELETE FROM scenario2hwschedule WHERE scenarioID = :id")
    public abstract void deleteHWScheduleRelationsForScenario(int id);

    @Query("DELETE FROM scenario2hwsystem WHERE scenarioID = :id")
    public abstract void deleteHWSystemRelationsForScenario(int id);

    @Query("DELETE FROM scenario2inverter WHERE scenarioID = :id")
    public abstract void deleteInverterRelationsForScenario(int id);

    @Query("DELETE FROM scenario2loadprofile WHERE scenarioID = :id")
    public abstract void deleteLoadProfileRelationsForScenario(int id);

    @Query("DELETE FROM scenario2loadshift WHERE scenarioID = :id")
    public abstract void deleteLoadShiftRelationsForScenario(int id);

    @Query("DELETE FROM scenario2panel WHERE scenarioID = :id")
    public abstract void deletePanelRelationsForScenario(int id);

    @Query("DELETE FROM scenariosimulationdata WHERE scenarioID = :id")
    public abstract void deleteSimulationDataForScenarioID(long id);

    @Query("DELETE FROM costings WHERE scenarioID = :id")
    public abstract void deleteCostingDataForScenarioID(long id);

    @Transaction
    public void copyScenario(int id) {
        Scenario scenario = getScenarioForID(id);
        List<Battery> batteries = getBatteriesForScenarioID(id);
        List<EVCharge> evCharges = getEVChargesForScenarioID(id);
        List<EVDivert> evDiverts = getEVDivertForScenarioID(id);
        HWDivert hwDivert = getHWDivertForScenarioID(id);
        List<HWSchedule> hwSchedules = getHWSchedulesForScenarioID(id);
        HWSystem hwSystem = getHWSystemForScenarioID(id);
        List<Inverter> inverters = getInvertersForScenarioID(id);
        LoadProfile loadProfile = getLoadProfileForScenarioID(id);
        List<LoadShift> loadShifts = getLoadShiftsForScenarioID(id);
        List<Panel> panels = getPanelsForScenarioID(id);

        scenario.setScenarioName(scenario.getScenarioName() + "_copy");
        scenario.setScenarioIndex(0);
        for (Battery b : batteries) b.setBatteryIndex(0);
        for (EVCharge e : evCharges) e.setEvChargeIndex(0);
        for (EVDivert d : evDiverts) d.setEvDivertIndex(0);
        if (!(null == hwDivert)) hwDivert.setHwDivertIndex(0);
        for (HWSchedule h : hwSchedules) h.setHwScheduleIndex(0);
        if (!(null == hwSystem)) hwSystem.setHwSystemIndex(0);
        for (Inverter i : inverters) i.setInverterIndex(0);
        if (!(null == loadProfile)) {
            long oldLoadProfileID = loadProfile.getLoadProfileIndex();
            loadProfile.setLoadProfileIndex(0);
            long newLoadProfileID = addNewLoadProfile(loadProfile);
            copyLoadProfileData(oldLoadProfileID, newLoadProfileID);
            loadProfile.setLoadProfileIndex(newLoadProfileID);
        }
        for (LoadShift l : loadShifts) l.setLoadShiftIndex(0);
        for (Panel p : panels) {
            long oldPanelID = p.getPanelIndex();
            p.setPanelIndex(0);
            long newPanelID = addNewPanels(p);
            copyPanelData(oldPanelID, newPanelID);
            p.setPanelIndex(newPanelID);
        }

        addNewScenarioWithComponents(scenario, new ScenarioComponents(
                scenario, inverters, batteries, panels, hwSystem,
                loadProfile, loadShifts, evCharges, hwSchedules,
                hwDivert, evDiverts));
    }

    @Transaction
    public List<ScenarioComponents> getAllScenariosForExport() {
        List<ScenarioComponents> ret = new ArrayList<>();
        List<Scenario> scenarios = getScenarios();
        for (Scenario scenario: scenarios){
            ScenarioComponents scenarioComponents = new ScenarioComponents(scenario,
                    getInvertersForScenarioID(scenario.getScenarioIndex()),
                    getBatteriesForScenarioID(scenario.getScenarioIndex()),
                    getPanelsForScenarioID(scenario.getScenarioIndex()),
                    getHWSystemForScenarioID(scenario.getScenarioIndex()),
                    getLoadProfileForScenarioID(scenario.getScenarioIndex()),
                    getLoadShiftsForScenarioID(scenario.getScenarioIndex()),
                    getEVChargesForScenarioID(scenario.getScenarioIndex()),
                    getHWSchedulesForScenarioID(scenario.getScenarioIndex()),
                    getHWDivertForScenarioID(scenario.getScenarioIndex()),
                    getEVDivertForScenarioID(scenario.getScenarioIndex())
            );
            ret.add(scenarioComponents);
        }
        return ret;
    }

    @Transaction
    public ScenarioComponents getScenarioComponentsForScenarioID(long scenarioID) {
        return new ScenarioComponents(
                getScenarioForID(scenarioID),
                getInvertersForScenarioID(scenarioID),
                getBatteriesForScenarioID(scenarioID),
                getPanelsForScenarioID(scenarioID),
                getHWSystemForScenarioID(scenarioID),
                getLoadProfileForScenarioID(scenarioID),
                getLoadShiftsForScenarioID(scenarioID),
                getEVChargesForScenarioID(scenarioID),
                getHWSchedulesForScenarioID(scenarioID),
                getHWDivertForScenarioID(scenarioID),
                getEVDivertForScenarioID(scenarioID)
        );
    }

    @Query("SELECT * FROM scenarios")
    public abstract List<Scenario> getScenarios();

    @Transaction
    public void copyLoadProfileFromScenario(long fromScenarioID, Long toScenarioID) {
        LoadProfile lp = getLoadProfileForScenarioID(fromScenarioID);
        long oldLoadProfileID = lp.getLoadProfileIndex();
        lp.setLoadProfileIndex(0L);
        long newLoadProfileID = addNewLoadProfile(lp);

        deleteLoadProfileRelationsForScenario(Math.toIntExact(toScenarioID));
        Scenario2LoadProfile s2lp = new Scenario2LoadProfile();
        s2lp.setScenarioID(toScenarioID);
        s2lp.setLoadProfileID(newLoadProfileID);
        addNewScenario2LoadProfile(s2lp);

        Scenario toScenario = getScenario(toScenarioID);
        toScenario.setHasLoadProfiles(true);
        updateScenario(toScenario);
        copyLoadProfileData(oldLoadProfileID, newLoadProfileID);

        deleteOrphanLoadProfiles();
    }

    @Query("INSERT INTO loadProfiledata (loadProfileID, date, minute, load, mod, dow, do2001) " +
            "SELECT :newLoadProfileID, date, minute, load, mod, dow, do2001 FROM loadProfiledata " +
            "WHERE loadProfileID = :oldLoadProfileID")
    public abstract void copyLoadProfileData(long oldLoadProfileID, long newLoadProfileID);

    @Transaction
    public void linkLoadProfileFromScenario(long fromScenarioID, Long toScenarioID) {
        LoadProfile lp = getLoadProfileForScenarioID(fromScenarioID);
        deleteLoadProfileRelationsForScenario(Math.toIntExact(toScenarioID));

        Scenario2LoadProfile scenario2LoadProfile = new Scenario2LoadProfile();
        scenario2LoadProfile.setScenarioID(toScenarioID);
        scenario2LoadProfile.setLoadProfileID(lp.getLoadProfileIndex());
        addNewScenario2LoadProfile(scenario2LoadProfile);

        Scenario toScenario = getScenario(toScenarioID);
        toScenario.setHasLoadProfiles(true);
        updateScenario(toScenario);

        deleteOrphanLoadProfiles();
    }

    @Query("SELECT sum(pv) AS gen, SUM(Feed) AS sold, SUM(load) + SUM(immersionLoad) + SUM(directEVcharge) AS load, SUM(Buy) AS bought, " +
            "sum(kWHDivToEV) AS evDiv, sum(kWHDivToWater) AS h2oDiv, sum(pvToLoad) AS pvToLoad, sum(pvToCharge) AS pvToCharge, " +
            "sum(load) AS house, sum(immersionLoad) AS h20, sum(directEVcharge) AS EV " +
            "FROM scenariosimulationdata WHERE scenarioID = :scenarioID")
    public abstract SimKPIs getSimKPIsForScenario(Long scenarioID);

    @Query("DELETE FROM scenario2inverter WHERE scenarioID = :scenarioID AND inverterID = :inverterID")
    public abstract void removeScenario2Inverter(Long inverterID, Long scenarioID);

    @Transaction
    public void deleteInverterFromScenario(Long inverterID, Long scenarioID) {
        removeScenario2Inverter(inverterID, scenarioID);
        deleteOrphanInverters();
        List<Inverter> inverters = getInvertersForScenarioID(scenarioID);
        if (inverters.size() == 0) {
            Scenario scenario = getScenario(scenarioID);
            scenario.setHasInverters(false);
            updateScenario(scenario);
        }
    }

    @Transaction
    public void copyInverterFromScenario(long fromScenarioID, Long toScenarioID) {
        List<Inverter> inverters = getInvertersForScenarioID(fromScenarioID);
        for (Inverter inverter: inverters) {
            inverter.setInverterIndex(0L);
            long newInverterID = addNewInverter(inverter);

            Scenario2Inverter s2i = new Scenario2Inverter();
            s2i.setScenarioID(toScenarioID);
            s2i.setInverterID(newInverterID);
            addNewScenario2Inverter(s2i);

            Scenario toScenario = getScenario(toScenarioID);
            toScenario.setHasInverters(true);
            updateScenario(toScenario);
        }

        deleteOrphanInverters();
    }

    @Query("SELECT * FROM scenario2inverter")
    public abstract LiveData<List<Scenario2Inverter>> loadInverterRelations();

    @Transaction
    public void linkInverterFromScenario(long fromScenarioID, Long toScenarioID) {
        List<Inverter> inverters = getInvertersForScenarioID(fromScenarioID);
        for (Inverter lp: inverters) {
            Scenario2Inverter scenario2Inverter = new Scenario2Inverter();
            scenario2Inverter.setScenarioID(toScenarioID);
            scenario2Inverter.setInverterID(lp.getInverterIndex());
            addNewScenario2Inverter(scenario2Inverter);

            Scenario toScenario = getScenario(toScenarioID);
            toScenario.setHasInverters(true);
            updateScenario(toScenario);
        }

        deleteOrphanInverters();
    }

    @Query("SELECT * FROM scenario2panel")
    public abstract LiveData<List<Scenario2Panel>> loadPanelRelations();

    @Query("DELETE FROM scenario2panel WHERE scenarioID = :scenarioID AND panelID = :panelID")
    public abstract void removeScenario2Panel(Long panelID, Long scenarioID);

    @Transaction
    public void deletePanelFromScenario(Long panelID, Long scenarioID) {
        removeScenario2Panel(panelID, scenarioID);
        deleteOrphanPanels();
        deleteOrphanPanelData();
        List<Panel> panels = getPanelsForScenarioID(scenarioID);
        if (panels.size() == 0) {
            Scenario scenario = getScenario(scenarioID);
            scenario.setHasPanels(false);
            updateScenario(scenario);
        }
    }

    @Transaction
    public long savePanel(Long scenarioID, Panel panel) {
        long panelID = panel.getPanelIndex();
        if (panelID == 0) {
            panelID = addNewPanels(panel);
            Scenario2Panel s2p = new Scenario2Panel();
            s2p.setScenarioID(scenarioID);
            s2p.setPanelID(panelID);
            addNewScenario2Panel(s2p);

            Scenario scenario = getScenario(scenarioID);
            scenario.setHasPanels(true);
            updateScenario(scenario);
        }
        else {
            updatePanel(panel);
        }
        return panelID;
    }

    @Transaction
    public void copyPanelFromScenario(long fromScenarioID, Long toScenarioID) {
        List<Panel> panels = getPanelsForScenarioID(fromScenarioID);
        for (Panel panel: panels) {
            long oldPanelID = panel.getPanelIndex();
            panel.setPanelIndex(0L);
            long newPanelID = addNewPanels(panel);

            Scenario2Panel s2p = new Scenario2Panel();
            s2p.setScenarioID(toScenarioID);
            s2p.setPanelID(newPanelID);
            addNewScenario2Panel(s2p);

            Scenario toScenario = getScenario(toScenarioID);
            toScenario.setHasPanels(true);
            updateScenario(toScenario);
            copyPanelData(oldPanelID, newPanelID);
        }

        deleteOrphanPanels();
    }

    @Query("INSERT INTO paneldata (PanelID, date, minute, pv, mod, dow, do2001) " +
            "SELECT :toPanelID, date, minute, pv, mod, dow, do2001 FROM paneldata " +
            "WHERE panelID = :fromPanelID")
    public abstract void copyPanelData(long fromPanelID, long toPanelID);

    @Transaction
    public void linkPanelFromScenario(long fromScenarioID, Long toScenarioID) {
        List<Panel> panels = getPanelsForScenarioID(fromScenarioID);
        for (Panel panel: panels) {
            Scenario2Panel scenario2Panel = new Scenario2Panel();
            scenario2Panel.setScenarioID(toScenarioID);
            scenario2Panel.setPanelID(panel.getPanelIndex());
            addNewScenario2Panel(scenario2Panel);

            Scenario toScenario = getScenario(toScenarioID);
            toScenario.setHasPanels(true);
            updateScenario(toScenario);
        }

        deleteOrphanPanels();
    }

    @Query("SELECT * FROM panels WHERE panelIndex = :panelID")
    public abstract Panel getPanelForID(Long panelID);

    @Insert(entity = PanelData.class, onConflict = OnConflictStrategy.REPLACE)
    public abstract void savePanelData(ArrayList<PanelData> panelDataList);

    @Query("SELECT panelID, substr(Date, 6,2) AS Month, SUM(pv) AS tot FROM paneldata GROUP BY panelID, Month ORDER BY Month ASC")
    public abstract LiveData<List<PanelPVSummary>> getPanelPVSummary();

    @Query("SELECT CASE WHEN " +
            "(SELECT COUNT (DISTINCT paneldata.panelID) AS Found FROM paneldata, scenario2panel WHERE scenario2panel.panelID = paneldata.panelID AND scenarioID = :scenarioID) = " +
            "(SELECT COUNT (DISTINCT panelID) AS Needed FROM scenario2panel WHERE scenarioID = :scenarioID) " +
            "THEN 1 " +
            "ELSE 0 " +
            "END AS OK")
    public abstract boolean checkForMissingPanelData(Long scenarioID);

    @Query("DELETE FROM paneldata WHERE panelID = :panelID")
    public abstract void removePanelData(Long panelID);

    @Query("SELECT scenarioName FROM scenarios WHERE scenarioIndex IN (" +
            "SELECT scenarioID FROM scenario2loadprofile WHERE  loadProfileID = (" +
            "SELECT loadProfileID FROM scenario2loadprofile WHERE scenarioID = :scenarioID) AND scenarioID != :scenarioID )")
    public abstract List<String> getLinkedLoadProfiles(Long scenarioID);

    @Query("SELECT scenarioName FROM scenarios WHERE scenarioIndex IN (" +
            "SELECT scenarioID FROM scenario2inverter WHERE inverterID = :inverterID) AND scenarioIndex != :scenarioID")
    public abstract List<String> getLinkedInverters(Long inverterID, Long scenarioID);

    @Query("SELECT scenarioName FROM scenarios WHERE scenarioIndex IN (" +
            "SELECT scenarioID FROM scenario2panel WHERE panelID = :panelIndex) AND scenarioIndex != :scenarioID")
    public abstract List<String> getLinkedPanels(long panelIndex, Long scenarioID);

    @Query("SELECT * FROM scenario2battery")
    public abstract LiveData<List<Scenario2Battery>> loadBatteryRelations();

    @Query("DELETE FROM scenario2battery WHERE scenarioID = :scenarioID AND batteryID = :batteryID")
    public abstract void deleteBatteryFromScenario(Long batteryID, Long scenarioID);

    @Transaction
    public void saveBatteryForScenario(Long scenarioID, Battery battery) {
        long batteryID = battery.getBatteryIndex();
        if (batteryID == 0) {
            batteryID = addNewBattery(battery);
            Scenario2Battery s2b = new Scenario2Battery();
            s2b.setScenarioID(scenarioID);
            s2b.setBatteryID(batteryID);
            addNewScenario2Battery(s2b);

            Scenario scenario = getScenario(scenarioID);
            scenario.setHasBatteries(true);
            updateScenario(scenario);
        }
        else {
            updateBattery(battery);
        }
    }

    @Update (entity = Battery.class)
    public abstract void updateBattery(Battery battery);

    @Query("SELECT scenarioName FROM scenarios WHERE scenarioIndex IN (" +
            "SELECT scenarioID FROM scenario2battery WHERE batteryID = :batteryIndex) AND scenarioIndex != :scenarioID")
    public abstract List<String> getLinkedBatteries(long batteryIndex, Long scenarioID);

    @Transaction
    public void copyBatteryFromScenario(long fromScenarioID, Long toScenarioID) {
        List<Battery> batteries = getBatteriesForScenarioID(fromScenarioID);
        for (Battery battery: batteries) {
            battery.setBatteryIndex(0L);
            long newBatteryID = addNewBattery(battery);

            Scenario2Battery s2b = new Scenario2Battery();
            s2b.setScenarioID(toScenarioID);
            s2b.setBatteryID(newBatteryID);
            addNewScenario2Battery(s2b);

            Scenario toScenario = getScenario(toScenarioID);
            toScenario.setHasBatteries(true);
            updateScenario(toScenario);
        }

        deleteOrphanBatteries();
    }

    @Transaction
    public void linkBatteryFromScenario(long fromScenarioID, Long toScenarioID) {
        List<Battery> batteries = getBatteriesForScenarioID(fromScenarioID);
        for (Battery battery: batteries) {
            Scenario2Battery scenario2Battery = new Scenario2Battery();
            scenario2Battery.setScenarioID(toScenarioID);
            scenario2Battery.setBatteryID(battery.getBatteryIndex());
            addNewScenario2Battery(scenario2Battery);

            Scenario toScenario = getScenario(toScenarioID);
            toScenario.setHasBatteries(true);
            updateScenario(toScenario);
        }

        deleteOrphanBatteries();
    }

    //##############################################
    @Query("SELECT * FROM scenario2loadshift")
    public abstract LiveData<List<Scenario2LoadShift>> loadLoadShiftRelations();

    @Query("DELETE FROM scenario2loadshift WHERE scenarioID = :scenarioID AND loadShiftID = :loadShiftID")
    public abstract void deleteLoadShiftFromScenario(Long loadShiftID, Long scenarioID);

    @Transaction
    public void saveLoadShiftForScenario(Long scenarioID, LoadShift loadShift) {
        long loadShiftIndex = loadShift.getLoadShiftIndex();
        if (loadShiftIndex == 0) {
            loadShiftIndex = addNewLoadShift(loadShift);
            Scenario2LoadShift s2ls = new Scenario2LoadShift();
            s2ls.setScenarioID(scenarioID);
            s2ls.setLoadShiftID(loadShiftIndex);
            addNewScenario2LoadShift(s2ls);

            Scenario scenario = getScenario(scenarioID);
            scenario.setHasLoadShifts(true);
            updateScenario(scenario);
        }
        else {
            updateLoadShift(loadShift);
        }
    }

    @Update (entity = LoadShift.class)
    public abstract void updateLoadShift(LoadShift loadShift);

    @Query("SELECT scenarioName FROM scenarios WHERE scenarioIndex IN (" +
            "SELECT scenarioID FROM scenario2loadshift WHERE loadShiftID = :loadShiftIndex) AND scenarioIndex != :scenarioID")
    public abstract List<String> getLinkedLoadShifts(long loadShiftIndex, Long scenarioID);

    @Transaction
    public void copyLoadShiftFromScenario(long fromScenarioID, Long toScenarioID) {
        List<LoadShift> loadShifts = getLoadShiftsForScenarioID(fromScenarioID);
        for (LoadShift loadShift: loadShifts) {
            loadShift.setLoadShiftIndex(0L);
            long newLoadShiftID = addNewLoadShift(loadShift);

            Scenario2LoadShift s2ls = new Scenario2LoadShift();
            s2ls.setScenarioID(toScenarioID);
            s2ls.setLoadShiftID(newLoadShiftID);
            addNewScenario2LoadShift(s2ls);

            Scenario toScenario = getScenario(toScenarioID);
            toScenario.setHasLoadShifts(true);
            updateScenario(toScenario);
        }

        deleteOrphanLoadShifts();
    }

    @Transaction
    public void linkLoadShiftFromScenario(long fromScenarioID, Long toScenarioID) {
        List<LoadShift> loadShifts = getLoadShiftsForScenarioID(fromScenarioID);
        for (LoadShift loadShift: loadShifts) {
            Scenario2LoadShift scenario2LoadShift = new Scenario2LoadShift();
            scenario2LoadShift.setScenarioID(toScenarioID);
            scenario2LoadShift.setLoadShiftID(loadShift.getLoadShiftIndex());
            addNewScenario2LoadShift(scenario2LoadShift);

            Scenario toScenario = getScenario(toScenarioID);
            toScenario.setHasLoadShifts(true);
            updateScenario(toScenario);
        }

        deleteOrphanLoadShifts();
    }
    //##############################################

    @Query("SELECT minuteOfDay / 60 AS Hour, sum(load) AS Load, sum(feed) AS Feed, sum(Buy) AS Buy, " +
            "sum(pv) AS PV, sum(pvToCharge) AS PV2Battery, sum(pvToLoad) AS PV2Load, sum(batToLoad) AS Battery2Load, sum(gridToBattery) AS Grid2Battery, " +
            "sum(directEVcharge) AS EVSchedule, sum(immersionLoad) AS HWSchedule, sum(kWHDivToEV) AS EVDivert, sum(kWHDivToWater) AS HWDivert " +
            "FROM scenariosimulationdata WHERE dayOf2001 = :dayOfYear AND scenarioID = :scenarioID " +
            "GROUP BY Hour ORDER BY Hour")
    public abstract List<ScenarioBarChartData> getBarData(Long scenarioID, int dayOfYear);

    @Query("SELECT minuteOfDay, SOC, waterTemp FROM scenariosimulationdata WHERE dayOf2001 = :dayOfYear AND scenarioID = :scenarioID  ORDER BY minuteOfDay")
    public abstract List<ScenarioLineGraphData> getLineData(Long scenarioID, int dayOfYear);

    @Query("SELECT substr(Date, 9) AS Hour, sum(load) AS Load, sum(feed) AS Feed, sum(Buy) AS Buy, " +
            "sum(pv) AS PV, sum(pvToCharge) AS PV2Battery, sum(pvToLoad) AS PV2Load, sum(batToLoad) AS Battery2Load, sum(gridToBattery) AS Grid2Battery, " +
            "sum(directEVcharge) AS EVSchedule, sum(immersionLoad) AS HWSchedule, sum(kWHDivToEV) AS EVDivert, sum(kWHDivToWater) AS HWDivert " +
            "FROM scenariosimulationdata WHERE substr(Date, 6,2) IN (" +
            "SELECT DISTINCT substr(Date, 6,2) AS Month FROM scenariosimulationdata WHERE dayOf2001 = :dayOfYear) " +
            "AND scenarioID = :scenarioID GROUP BY dayOf2001 ORDER BY dayOf2001")
    public abstract List<ScenarioBarChartData> getMonthlyBarData(Long scenarioID, int dayOfYear);

    @Query("SELECT substr(Date, 6,2) AS Hour, sum(load) AS Load, sum(feed) AS Feed, sum(Buy) AS Buy, " +
            "sum(pv) AS PV, sum(pvToCharge) AS PV2Battery, sum(pvToLoad) AS PV2Load, sum(batToLoad) AS Battery2Load, sum(gridToBattery) AS Grid2Battery, " +
            "sum(directEVcharge) AS EVSchedule, sum(immersionLoad) AS HWSchedule, sum(kWHDivToEV) AS EVDivert, sum(kWHDivToWater) AS HWDivert " +
            "FROM scenariosimulationdata WHERE scenarioID = :scenarioID " +
            "GROUP BY substr(Date, 6,2) ORDER BY substr(Date, 6,2)")
    public abstract List<ScenarioBarChartData> getYearBarData(Long scenarioID);

    @Query("SELECT scenarioName FROM scenarios WHERE scenarioIndex IN (" +
            "SELECT scenarioID FROM scenario2hwsystem WHERE hwSystemID = :hwSystemIndex) AND scenarioIndex != :scenarioID")
    public abstract List<String> getLinkedHWSystems(long hwSystemIndex, Long scenarioID);

    @Transaction
    public void saveHWSystemForScenario(Long scenarioID, HWSystem hwSystem) {

        long hwSystemID = hwSystem.getHwSystemIndex();
        if (hwSystemID == 0) {
            hwSystemID = addNewHWSystem(hwSystem);
            deleteHWSystemRelationsForScenario(Math.toIntExact(scenarioID));
            Scenario2HWSystem s2hws = new Scenario2HWSystem();
            s2hws.setScenarioID(scenarioID);
            s2hws.setHwSystemID(hwSystemID);
            addNewScenario2HWSystem(s2hws);

            Scenario scenario = getScenario(scenarioID);
            scenario.setHasHWSystem(true);
            updateScenario(scenario);
        }
        else {
            updateHWSystem(hwSystem);
        }
    }

    @Update (entity = HWSystem.class)
    public abstract void updateHWSystem(HWSystem battery);

    @Transaction
    public void linkHWSystemFromScenario(long fromScenarioID, Long toScenarioID) {
        HWSystem hwSystem = getHWSystemForScenarioID(fromScenarioID);

        deleteHWSystemRelationsForScenario(Math.toIntExact(toScenarioID));

        Scenario2HWSystem scenario2HWSystem = new Scenario2HWSystem();
        scenario2HWSystem.setScenarioID(toScenarioID);
        scenario2HWSystem.setHwSystemID(hwSystem.getHwSystemIndex());
        addNewScenario2HWSystem(scenario2HWSystem);

        Scenario toScenario = getScenario(toScenarioID);
        toScenario.setHasHWSystem(true);
        updateScenario(toScenario);

        deleteOrphanHWSystems();
    }

    @Transaction
    public void copyHWSettingsFromScenario(long fromScenarioID, Long toScenarioID) {
        HWSystem hwSystem = getHWSystemForScenarioID(fromScenarioID);
        hwSystem.setHwSystemIndex(0L);
        long newHWSystemID = addNewHWSystem(hwSystem);

        deleteHWSystemRelationsForScenario(Math.toIntExact(toScenarioID));
        Scenario2HWSystem s2hws = new Scenario2HWSystem();
        s2hws.setScenarioID(toScenarioID);
        s2hws.setHwSystemID(newHWSystemID);
        addNewScenario2HWSystem(s2hws);

        Scenario toScenario = getScenario(toScenarioID);
        toScenario.setHasHWSystem(true);
        updateScenario(toScenario);

        deleteOrphanHWSystems();
    }

    @Query("SELECT * FROM scenario2hwsystem")
    public abstract LiveData<List<Scenario2HWSystem>> loadHWSystemRelations();

    @Transaction
    public void saveHWDivert(Long scenarioID, HWDivert hwDivert) {
        if (hwDivert.getHwDivertIndex() == 0) {
            long hwDivertIndex = addNewHWDivert(hwDivert);
            Scenario2HWDivert s2hwd = new Scenario2HWDivert();
            s2hwd.setHwDivertID(hwDivertIndex);
            s2hwd.setScenarioID(scenarioID);
            addNewScenario2HWDivert(s2hwd);

            Scenario scenario = getScenario(scenarioID);
            scenario.setHasHWDivert(true);
            updateScenario(scenario);
        }
        else updateHWDivert(hwDivert);
    }

    @Update (entity = HWDivert.class)
    public abstract void updateHWDivert(HWDivert hwDivert);

    @Query("SELECT scenarioName FROM scenarios WHERE scenarioIndex IN (" +
            "SELECT scenarioID FROM scenario2hwschedule WHERE hwScheduleID = :hwScheduleIndex) AND scenarioIndex != :scenarioID")
    public abstract List<String> getLinkedHWSchedules(long hwScheduleIndex, Long scenarioID);

    @Query("SELECT * FROM scenario2hwschedule")
    public abstract  LiveData<List<Scenario2HWSchedule>> loadHWScheduleRelations();

    @Query("DELETE FROM scenario2hwschedule WHERE scenarioID = :scenarioID AND hwScheduleID = :hwScheduleID")
    public abstract void deleteHWScheduleFromScenario1(Long hwScheduleID, Long scenarioID);

    @Transaction
    public void deleteHWScheduleFromScenario(Long hwScheduleID, Long scenarioID) {
        deleteHWScheduleFromScenario1(hwScheduleID, scenarioID);
        List<HWSchedule> hwSchedules = getHWSchedulesForScenarioID(scenarioID);
        if (hwSchedules.size() == 0) {
            Scenario scenario = getScenario(scenarioID);
            scenario.setHasHWSchedules(false);
            updateScenario(scenario);
        }
    }

    @Transaction
    public void saveHWScheduleForScenario(Long scenarioID, HWSchedule hwSchedule) {
        long hwScheduleIndex = hwSchedule.getHwScheduleIndex();
        if (hwScheduleIndex == 0) {
            hwScheduleIndex = addNewHWSchedule(hwSchedule);
            Scenario2HWSchedule scenario2HWSchedule = new Scenario2HWSchedule();
            scenario2HWSchedule.setScenarioID(scenarioID);
            scenario2HWSchedule.setHwScheduleID(hwScheduleIndex);
            addNewScenario2HWSchedule(scenario2HWSchedule);

            Scenario scenario = getScenario(scenarioID);
            scenario.setHasHWSchedules(true);
            updateScenario(scenario);
        }
        else {
            updateHWSchedule(hwSchedule);
        }
    }

    @Update (entity = HWSchedule.class)
    public abstract void updateHWSchedule(HWSchedule hwSchedule);

    @Transaction
    public void copyHWScheduleFromScenario(long fromScenarioID, Long toScenarioID) {
        List<HWSchedule> hwSchedules = getHWSchedulesForScenarioID(fromScenarioID);
        for (HWSchedule hwSchedule: hwSchedules) {
            hwSchedule.setHwScheduleIndex(0L);
            long newHWScheduleID = addNewHWSchedule(hwSchedule);

            Scenario2HWSchedule scenario2HWSchedule = new Scenario2HWSchedule();
            scenario2HWSchedule.setScenarioID(toScenarioID);
            scenario2HWSchedule.setHwScheduleID(newHWScheduleID);
            addNewScenario2HWSchedule(scenario2HWSchedule);

            Scenario toScenario = getScenario(toScenarioID);
            toScenario.setHasHWSchedules(true);
            updateScenario(toScenario);
        }

        deleteOrphanHWSchedules();
    }

    @Transaction
    public void linkHWScheduleFromScenario(long fromScenarioID, long toScenarioID) {
        List<HWSchedule> hwSchedules = getHWSchedulesForScenarioID(fromScenarioID);
        for (HWSchedule hwSchedule: hwSchedules) {
            Scenario2HWSchedule scenario2HWSchedule = new Scenario2HWSchedule();
            scenario2HWSchedule.setScenarioID(toScenarioID);
            scenario2HWSchedule.setHwScheduleID(hwSchedule.getHwScheduleIndex());
            addNewScenario2HWSchedule(scenario2HWSchedule);

            Scenario toScenario = getScenario(toScenarioID);
            toScenario.setHasHWSchedules(true);
            updateScenario(toScenario);
        }

        deleteOrphanHWSchedules();
    }

    @Query("SELECT scenarioName FROM scenarios WHERE scenarioIndex IN (" +
            "SELECT scenarioID FROM scenario2evcharge WHERE evChargeID = :evChargeIndex) AND scenarioIndex != :scenarioID")
    public abstract List<String> getLinkedEVCharges(long evChargeIndex, Long scenarioID);

    @Query("SELECT * FROM scenario2evcharge")
    public abstract LiveData<List<Scenario2EVCharge>> loadEVChargeRelations();

    @Query("DELETE FROM scenario2evcharge WHERE scenarioID = :scenarioID AND evChargeID = :evChargeID")
    public abstract void deleteEVChargeFromScenario(Long evChargeID, Long scenarioID);

    @Transaction
    public void saveEVChargeForScenario(Long scenarioID, EVCharge evCharge) {
        long evScheduleIndex = evCharge.getEvChargeIndex();
        if (evScheduleIndex == 0) {
            evScheduleIndex = addNewEVCharge(evCharge);
            Scenario2EVCharge scenario2EVCharge = new Scenario2EVCharge();
            scenario2EVCharge.setScenarioID(scenarioID);
            scenario2EVCharge.setEvChargeID(evScheduleIndex);
            addNewScenario2EVCharge(scenario2EVCharge);

            Scenario scenario = getScenario(scenarioID);
            scenario.setHasEVCharges(true);
            updateScenario(scenario);
        }
        else {
            updateEVCharge(evCharge);
        }
    }

    @Update (entity = EVCharge.class)
    public abstract void updateEVCharge(EVCharge evCharge);

    public void copyEVChargeFromScenario(long fromScenarioID, Long toScenarioID) {
        List<EVCharge> evCharges = getEVChargesForScenarioID(fromScenarioID);
        for (EVCharge evCharge: evCharges) {
            evCharge.setEvChargeIndex(0L);
            long newEVChargeID = addNewEVCharge(evCharge);

            Scenario2EVCharge scenario2EVCharge = new Scenario2EVCharge();
            scenario2EVCharge.setScenarioID(toScenarioID);
            scenario2EVCharge.setEvChargeID(newEVChargeID);
            addNewScenario2EVCharge(scenario2EVCharge);

            Scenario toScenario = getScenario(toScenarioID);
            toScenario.setHasEVCharges(true);
            updateScenario(toScenario);
        }

        deleteOrphanEVCharges();
    }

    public void linkEVChargeFromScenario(long fromScenarioID, Long toScenarioID) {
        List<EVCharge> evCharges = getEVChargesForScenarioID(fromScenarioID);
        for (EVCharge evCharge: evCharges) {

            Scenario2EVCharge scenario2EVCharge = new Scenario2EVCharge();
            scenario2EVCharge.setScenarioID(toScenarioID);
            scenario2EVCharge.setEvChargeID(evCharge.getEvChargeIndex());
            addNewScenario2EVCharge(scenario2EVCharge);

            Scenario toScenario = getScenario(toScenarioID);
            toScenario.setHasEVCharges(true);
            updateScenario(toScenario);
        }

        deleteOrphanEVCharges();
    }

    @Query("SELECT scenarioName FROM scenarios WHERE scenarioIndex IN (" +
            "SELECT scenarioID FROM scenario2evdivert WHERE evDivertID = :evDivertIndex) AND scenarioIndex != :scenarioID")
    public abstract List<String> getLinkedEVDiverts(long evDivertIndex, Long scenarioID);

    @Query("SELECT * FROM scenario2evdivert")
    public abstract LiveData<List<Scenario2EVDivert>> loadEVDivertRelations();

    @Query("DELETE FROM scenario2evdivert WHERE scenarioID = :scenarioID AND evDivertID = :evDivertID")
    public abstract void deleteEVDivertFromScenario(Long evDivertID, Long scenarioID);

    @Transaction
    public void saveEVDivertForScenario(Long scenarioID, EVDivert evDivert) {
        long evScheduleIndex = evDivert.getEvDivertIndex();
        if (evScheduleIndex == 0) {
            evScheduleIndex = addNewEVDivert(evDivert);
            Scenario2EVDivert scenario2EVDivert = new Scenario2EVDivert();
            scenario2EVDivert.setScenarioID(scenarioID);
            scenario2EVDivert.setEvDivertID(evScheduleIndex);
            addNewScenario2EVDivert(scenario2EVDivert);

            Scenario scenario = getScenario(scenarioID);
            scenario.setHasEVDivert(true);
            updateScenario(scenario);
        }
        else {
            updateEVDivert(evDivert);
        }
    }

    @Update (entity = EVDivert.class)
    public abstract void updateEVDivert(EVDivert evDivert);

    public void copyEVDivertFromScenario(long fromScenarioID, Long toScenarioID) {
        List<EVDivert> evDiverts = getEVDivertForScenarioID(fromScenarioID);
        for (EVDivert evDivert: evDiverts) {
            evDivert.setEvDivertIndex(0L);
            long newEVDivertID = addNewEVDivert(evDivert);

            Scenario2EVDivert scenario2EVDivert = new Scenario2EVDivert();
            scenario2EVDivert.setScenarioID(toScenarioID);
            scenario2EVDivert.setEvDivertID(newEVDivertID);
            addNewScenario2EVDivert(scenario2EVDivert);

            Scenario toScenario = getScenario(toScenarioID);
            toScenario.setHasEVDivert(true);
            updateScenario(toScenario);
        }

        deleteOrphanEVDiverts();
    }

    public void linkEVDivertFromScenario(long fromScenarioID, Long toScenarioID) {
        List<EVDivert> evDiverts = getEVDivertForScenarioID(fromScenarioID);
        for (EVDivert evDivert: evDiverts) {

            Scenario2EVDivert scenario2EVDivert = new Scenario2EVDivert();
            scenario2EVDivert.setScenarioID(toScenarioID);
            scenario2EVDivert.setEvDivertID(evDivert.getEvDivertIndex());
            addNewScenario2EVDivert(scenario2EVDivert);

            Scenario toScenario = getScenario(toScenarioID);
            toScenario.setHasEVDivert(true);
            updateScenario(toScenario);
        }

        deleteOrphanEVDiverts();
    }

    @Query("SELECT DISTINCT gridExportMax FROM loadprofile, scenario2loadprofile WHERE loadProfileID = loadProfileIndex AND scenarioID = :scenarioID")
    public abstract double getGridExportMaxForScenario(long scenarioID);
}
