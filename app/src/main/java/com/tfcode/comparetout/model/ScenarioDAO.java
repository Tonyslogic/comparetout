package com.tfcode.comparetout.model;

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
import com.tfcode.comparetout.model.scenario.ScenarioComponents;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;
import com.tfcode.comparetout.model.scenario.SimKPIs;
import com.tfcode.comparetout.model.scenario.SimulationInputData;

import java.util.ArrayList;
import java.util.List;

@Dao
public abstract class ScenarioDAO {

    @Insert
    abstract long addNewSceanrio(Scenario scenario);

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
    void addNewScenarioWithComponents(Scenario scenario, ScenarioComponents components) {
        if (!(null == components.inverters)) scenario.setHasInverters(true);
        if (!(null == components.batteries)) scenario.setHasBatteries(true);
        if (!(null == components.panels)) scenario.setHasPanels(true);
        if (!(null == components.hwSystem)) scenario.setHasHWSystem(true);
        if (!(null == components.loadProfile)) scenario.setHasLoadProfiles(true);
        if (!(null == components.loadShifts)) scenario.setHasLoadShifts(true);
        if (!(null == components.evCharges)) scenario.setHasEVCharges(true);
        if (!(null == components.hwSchedules)) scenario.setHasHWSchedules(true);
        if (!(null == components.hwDivert)) scenario.setHasHWDivert(true);
        if (!(null == components.evDivert)) scenario.setHasEVDivert(true);

        long scenarioID = addNewSceanrio(scenario);
        if (!(null == components.inverters)) {
            for (Inverter i : components.inverters) {
                long inverterID = addNewInverter(i);
                System.out.println("Stored inverter: " + i.getInverterName());
                Scenario2Inverter s2i = new Scenario2Inverter();
                s2i.setScenarioID(scenarioID);
                s2i.setInverterID(inverterID);
                addNewScenario2Inverter(s2i);
//                scenario.setHasInverters(true);
//                updateScenario(scenario);
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
                long panelsID = addNewPanels(p);
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
            long loadProfileID = addNewLoadProfile(components.loadProfile);
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
        if (!(null == components.evDivert)) {
            long evDivertID = addNewEVDivert(components.evDivert);
            Scenario2EVDivert s2evd = new Scenario2EVDivert();
            s2evd.setScenarioID(scenarioID);
            s2evd.setEvDivertID(evDivertID);
            addNewScenario2EVDivert(s2evd);
        }
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
    public abstract EVDivert getEVDivertForScenarioID(long id);

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
    public void saveLoadProfile(Long scenarioID, LoadProfile loadProfile) {
        long loadProfileID = loadProfile.getLoadProfileIndex();
        if (loadProfileID == 0) {
            loadProfileID = addNewLoadProfile(loadProfile);
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
    }

    @Update (entity = Inverter.class)
    public abstract void updateInverter(Inverter inverter);

    @Update (entity = Panel.class)
    public abstract void updatePanel(Panel panel);

    @Transaction
    public void saveInverter(Long scenarioID, Inverter inverter){
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
    }

    @Query("SELECT DISTINCT loadProfileID FROM loadprofiledata WHERE loadProfileID = :id")
    public abstract long loadProfileDataCheck(long id);

    @Query("DELETE FROM loadprofiledata WHERE loadProfileID = :id")
    public abstract void deleteLoadProfileData(long id);

    @Insert(entity = LoadProfileData.class)
    public abstract void createLoadProfileDataEntries(ArrayList<LoadProfileData> rows);

    @Query("DELETE FROM scenariosimulationdata WHERE scenarioID = (" +
            "SELECT scenarioID FROM scenario2loadprofile WHERE loadProfileID = :loadProfileID) ")
    public abstract void deleteSimulationDataForProfileID(long loadProfileID);

    @Query("DELETE FROM costings WHERE scenarioID = (" +
            "SELECT scenarioID FROM scenario2loadprofile WHERE loadProfileID = :loadProfileID) ")
    public abstract void deleteCostingDataForProfileID(long loadProfileID);

//    @Query("SELECT scenarioIndex FROM scenarios " +
//            "WHERE scenarioIndex NOT IN (SELECT DISTINCT scenarioID FROM scenariosimulationdata) " +
//            "AND scenarioIndex IN (SELECT DISTINCT scenarioID FROM scenario2loadprofile)")
    @Query("SELECT scenarioIndex FROM scenarios " +
            "WHERE scenarioIndex NOT IN (SELECT DISTINCT scenarioID FROM scenariosimulationdata) " +
            "AND scenarioIndex IN (SELECT DISTINCT scenarioID FROM scenario2loadprofile) " +
            "AND (SELECT DISTINCT loadProfileID FROM scenario2loadprofile WHERE scenarioID = scenarioIndex ) IN (SELECT DISTINCT loadProfileID FROM loadprofiledata)")
    public abstract List<Long> getAllScenariosThatNeedSimulation();

    @Query("SELECT * FROM scenarios WHERE scenarioIndex = :scenarioID")
    public abstract Scenario getScenarioForID(long scenarioID);

//    @Query("SELECT * FROM loadprofiledata WHERE loadProfileID = (" +
//            "SELECT DISTINCT loadProfileID FROM scenario2loadprofile WHERE scenarioID = :scenarioID)")
    @Query("SELECT A.date, A.minute, A.load, A.mod, A.dow, A.do2001, IFNULL(B.TPV, 0) AS TPV FROM " +
            "(SELECT * FROM loadprofiledata WHERE loadProfileID = (SELECT loadProfileID FROM scenario2loadprofile WHERE scenarioID = :scenarioID)) AS A " +
            "LEFT JOIN " +
            "(SELECT do2001, mod -1 AS mod, SUM(PV) AS TPV FROM paneldata WHERE panelID IN (SELECT panelID FROM scenario2panel WHERE scenarioID = :scenarioID) GROUP BY do2001, mod) AS B " +
            "ON A.do2001 = B.do2001 AND A.mod = B.mod")
    public abstract List<SimulationInputData> getSimulationInput(long scenarioID);

    @Insert(entity = ScenarioSimulationData.class)
    public abstract void saveSimulationDataForScenario(ArrayList<ScenarioSimulationData> simulationData);

//    @Query("SELECT scenarioIndex FROM scenarios " +
//            "WHERE scenarioIndex NOT IN (SELECT scenarioID FROM costings) " +
//            "AND (SELECT COUNT() FROM PricePlans) > 0 " +
//            "AND (SELECT COUNT(scenarioID) FROM scenariosimulationdata, scenarios WHERE scenariosimulationdata.scenarioID = scenarioIndex) > 0 ")
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

    @Transaction
    public void copyScenario(int id) {
        Scenario scenario = getScenarioForID(id);
        List<Battery> batteries = getBatteriesForScenarioID(id);
        List<EVCharge> evCharges = getEVChargesForScenarioID(id);
        EVDivert evDivert = getEVDivertForScenarioID(id);
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
        evDivert.setEvDivertIndex(0);
        hwDivert.setHwDivertIndex(0);
        for (HWSchedule h : hwSchedules) h.setHwScheduleIndex(0);
        hwSystem.setHwSystemIndex(0);
        for (Inverter i : inverters) i.setInverterIndex(0);
        loadProfile.setLoadProfileIndex(0);
        for (LoadShift l : loadShifts) l.setLoadShiftIndex(0);
        for (Panel p : panels) p.setPanelIndex(0);

        addNewScenarioWithComponents(scenario, new ScenarioComponents(
                scenario, inverters, batteries, panels, hwSystem,
                loadProfile, loadShifts, evCharges, hwSchedules,
                hwDivert, evDivert));
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

    @Query("SELECT * FROM scenarios")
    public abstract List<Scenario> getScenarios();

    @Transaction
    public void copyLoadProfileFromScenario(long fromScenarioID, Long toScenarioID) {
        LoadProfile lp = getLoadProfileForScenarioID(fromScenarioID);
        lp.setLoadProfileIndex(0L);
        long newLoadProfileID = addNewLoadProfile(lp);

        System.out.println("copyLoadProfileFromScenario, new LPID=" + newLoadProfileID);

        deleteLoadProfileRelationsForScenario(Math.toIntExact(toScenarioID));
        Scenario2LoadProfile s2lp = new Scenario2LoadProfile();
        s2lp.setScenarioID(toScenarioID);
        s2lp.setLoadProfileID(newLoadProfileID);
        addNewScenario2LoadProfile(s2lp);

        Scenario toScenario = getScenario(toScenarioID);
        toScenario.setHasLoadProfiles(true);
        updateScenario(toScenario);

        deleteOrphanLoadProfiles();
    }

    //DEBUG
    @Query("SELECT * FROM scenario2loadprofile")
    public abstract List<Scenario2LoadProfile> getS2LP();

    @Query("SELECT * FROM loadprofile")
    public abstract List<LoadProfile> getLP();

    @Query("SELECT * FROM scenarios")
    public abstract List<Scenario> getScen();

    @Query("SELECT loadprofile.loadProfileIndex, annualUsage, hourlyBaseLoad," +
            "gridImportMax, distributionSource, gridExportMax, hourlyDist, dowDist, monthlyDist FROM loadprofile, scenario2loadprofile " +
            "WHERE scenarioID = :scenarioID AND loadProfile.loadProfileIndex = loadProfileID")
    public abstract List<LoadProfile> get1LP(long scenarioID);

    // END DEBUG

    @Transaction
    public void linkLoadProfileFromScenario(long fromScenarioID, Long toScenarioID) {
        LoadProfile lp = getLoadProfileForScenarioID(fromScenarioID);
        System.out.println("Linking LP with id= " + lp.getLoadProfileIndex());

        deleteLoadProfileRelationsForScenario(Math.toIntExact(toScenarioID));

        Scenario2LoadProfile scenario2LoadProfile = new Scenario2LoadProfile();
        scenario2LoadProfile.setScenarioID(toScenarioID);
        scenario2LoadProfile.setLoadProfileID(lp.getLoadProfileIndex());
        System.out.println("prep:" +scenario2LoadProfile.getS2lpID() + ", " + scenario2LoadProfile.getLoadProfileID() + ", " + scenario2LoadProfile.getScenarioID());
        addNewScenario2LoadProfile(scenario2LoadProfile);

        Scenario toScenario = getScenario(toScenarioID);
        toScenario.setHasLoadProfiles(true);
        updateScenario(toScenario);

        deleteOrphanLoadProfiles();
    }

    @Query("SELECT sum(pv) AS gen, SUM(Feed) AS sold, SUM(load) AS load, SUM(Buy) AS bought " +
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

    public void copyInverterFromScenario(long fromScenarioID, Long toScenarioID) {
        List<Inverter> inverters = getInvertersForScenarioID(fromScenarioID);
        for (Inverter inverter: inverters) {
            inverter.setInverterIndex(0L);
            long newInverterID = addNewInverter(inverter);

            System.out.println("copyInverterFromScenario, new IID=" + newInverterID);

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
            System.out.println("Linking inverter with id= " + lp.getInverterIndex());

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
        List<Panel> panels = getPanelsForScenarioID(scenarioID);
        if (panels.size() == 0) {
            Scenario scenario = getScenario(scenarioID);
            scenario.setHasInverters(false);
            updateScenario(scenario);
        }
    }
    @Transaction
    public void savePanel(Long scenarioID, Panel panel) {
        long panleID = panel.getPanelIndex();
        if (panleID == 0) {
            panleID = addNewPanels(panel);
            Scenario2Panel s2p = new Scenario2Panel();
            s2p.setScenarioID(scenarioID);
            s2p.setPanelID(panleID);
            addNewScenario2Panel(s2p);

            Scenario scenario = getScenario(scenarioID);
            scenario.setHasPanels(true);
            updateScenario(scenario);
        }
        else {
            updatePanel(panel);
        }
    }

    public void copyPanelFromScenario(long fromScenarioID, Long toScenarioID) {
        List<Panel> panels = getPanelsForScenarioID(fromScenarioID);
        for (Panel panel: panels) {
            panel.setPanelIndex(0L);
            long newPanelID = addNewPanels(panel);

            System.out.println("copyPanelFromScenario, new IID=" + newPanelID);

            Scenario2Panel s2p = new Scenario2Panel();
            s2p.setScenarioID(toScenarioID);
            s2p.setPanelID(newPanelID);
            addNewScenario2Panel(s2p);

            Scenario toScenario = getScenario(toScenarioID);
            toScenario.setHasPanels(true);
            updateScenario(toScenario);
        }

        deleteOrphanPanels();
    }

    @Transaction
    public void linkPanelFromScenario(long fromScenarioID, Long toScenarioID) {
        List<Panel> panels = getPanelsForScenarioID(fromScenarioID);
        for (Panel panel: panels) {
            System.out.println("Linking panel with id= " + panel.getPanelIndex());

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
}
