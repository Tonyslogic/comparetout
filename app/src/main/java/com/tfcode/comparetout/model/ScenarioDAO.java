package com.tfcode.comparetout.model;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
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
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.Panel;
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

    @Update(entity = Scenario.class)
    public abstract void updateScenario(Scenario scenario);

    @Transaction
    void addNewScenarioWithComponents(Scenario scenario, ScenarioComponents components) {
        long scenarioID = addNewSceanrio(scenario);
        if (!(null == components.inverters)) {
            for (Inverter i : components.inverters) {
                long inverterID = addNewInverter(i);
                System.out.println("Stored inverter: " + i.getInverterName());
                Scenario2Inverter s2i = new Scenario2Inverter();
                s2i.setScenarioID(scenarioID);
                s2i.setInverterID(inverterID);
                addNewScenario2Inverter(s2i);
                scenario.setHasInverters(true);
                updateScenario(scenario);
            }
        }
        if (!(null == components.batteries)) {
            for (Battery b : components.batteries) {
                long batteryID = addNewBattery(b);
                Scenario2Battery s2b = new Scenario2Battery();
                s2b.setScenarioID(scenarioID);
                s2b.setBatteryID(batteryID);
                addNewScenario2Battery(s2b);
                scenario.setHasBatteries(true);
                updateScenario(scenario);
            }
        }
        if (!(null == components.panels)) {
            for (Panel p : components.panels) {
                long panelsID = addNewPanels(p);
                Scenario2Panel s2p = new Scenario2Panel();
                s2p.setScenarioID(scenarioID);
                s2p.setPanelID(panelsID);
                addNewScenario2Panel(s2p);
                scenario.setHasPanels(true);
                updateScenario(scenario);
            }
        }
        if (!(null == components.hwSystem)) {
            long hwSystemID = addNewHWSystem(components.hwSystem);
            Scenario2HWSystem s2hws = new Scenario2HWSystem();
            s2hws.setScenarioID(scenarioID);
            s2hws.setHwSystemID(hwSystemID);
            addNewScenario2HWSystem(s2hws);
            scenario.setHasHWSystem(true);
            updateScenario(scenario);
        }
        if (!(null == components.loadProfile)) {
            long loadProfileID = addNewLoadProfile(components.loadProfile);
            Scenario2LoadProfile s2lp = new Scenario2LoadProfile();
            s2lp.setScenarioID(scenarioID);
            s2lp.setLoadProfileID(loadProfileID);
            addNewScenario2LoadProfile(s2lp);
            scenario.setHasLoadProfiles(true);
            updateScenario(scenario);
        }
        if (!(null == components.loadShifts)) {
            for (LoadShift ls : components.loadShifts) {
                long loadShiftID = addNewLoadShift(ls);
                Scenario2LoadShift s2ls = new Scenario2LoadShift();
                s2ls.setScenarioID(scenarioID);
                s2ls.setLoadShiftID(loadShiftID);
                addNewScenario2LoadShift(s2ls);
                scenario.setHasLoadShifts(true);
                updateScenario(scenario);
            }
        }
        if (!(null == components.evCharges)) {
            for (EVCharge evc : components.evCharges) {
                long evChargeID = addNewEVCharge(evc);
                Scenario2EVCharge s2evc = new Scenario2EVCharge();
                s2evc.setScenarioID(scenarioID);
                s2evc.setEvChargeID(evChargeID);
                addNewScenario2EVCharge(s2evc);
                scenario.setHasEVCharges(true);
                updateScenario(scenario);
            }
        }
        if (!(null == components.hwSchedules)) {
            for (HWSchedule hws : components.hwSchedules) {
                long hwScheduleID = addNewHWSchedule(hws);
                Scenario2HWSchedule s2hws = new Scenario2HWSchedule();
                s2hws.setScenarioID(scenarioID);
                s2hws.setHwScheduleID(hwScheduleID);
                addNewScenario2HWSchedule(s2hws);
                scenario.setHasHWSchedules(true);
                updateScenario(scenario);
            }
        }
        if (!(null == components.hwDivert)) {
            long hwDivertID = addNewHWDivert(components.hwDivert);
            Scenario2HWDivert s2hwd = new Scenario2HWDivert();
            s2hwd.setScenarioID(scenarioID);
            s2hwd.setHwDivertID(hwDivertID);
            addNewScenario2HWDivert(s2hwd);
            scenario.setHasHWDivert(true);
            updateScenario(scenario);
        }
        if (!(null == components.evDivert)) {
            long evDivertID = addNewEVDivert(components.evDivert);
            Scenario2EVDivert s2evd = new Scenario2EVDivert();
            s2evd.setScenarioID(scenarioID);
            s2evd.setEvDivertID(evDivertID);
            addNewScenario2EVDivert(s2evd);
            scenario.setHasEVDivert(true);
            updateScenario(scenario);
        }
    }

    @Query("SELECT * FROM scenarios ORDER BY scenarios.scenarioName ASC")
    public abstract LiveData<List<Scenario>> loadScenarios();

    @Query("SELECT * FROM inverters, scenario2inverter " +
            "WHERE scenarioID = :id AND inverters.id = scenario2inverter.inverterID")
    @RewriteQueriesToDropUnusedColumns
    public abstract List<Inverter> getInvertersForScenarioID (long id);

    @Query("SELECT * FROM batteries, scenario2battery " +
            "WHERE scenarioID = :id AND batteries.id = scenario2battery.batteryID")
    @RewriteQueriesToDropUnusedColumns
    public abstract List<Battery> getBatteriesForScenarioID(long id);

    @Query("SELECT * FROM panels, scenario2panel " +
            "WHERE scenarioID = :id AND panels.id = scenario2panel.panelID")
    @RewriteQueriesToDropUnusedColumns
    public abstract List<Panel> getPanelsForScenarioID(long id);

    @Query("SELECT * FROM hwsystem, scenario2hwsystem " +
            "WHERE scenarioID = :id AND hwsystem.id = scenario2hwsystem.hwSystemID")
    @RewriteQueriesToDropUnusedColumns
    public abstract HWSystem getHWSystemForScenarioID(long id);

    @Query("SELECT * FROM loadprofile, scenario2loadprofile " +
            "WHERE scenarioID = :id AND loadprofile.id = loadProfileID")
    @RewriteQueriesToDropUnusedColumns
    public abstract LoadProfile getLoadProfileForScenarioID(long id);

    @Query("SELECT * FROM loadshift, scenario2loadshift " +
            "WHERE scenarioID = :id AND loadshift.id = loadShiftID")
    @RewriteQueriesToDropUnusedColumns
    public abstract List<LoadShift> getLoadShiftsForScenarioID(long id);

    @Query("SELECT * FROM evcharge, scenario2evcharge " +
            "WHERE scenarioID = :id AND evcharge.id = evChargeID")
    @RewriteQueriesToDropUnusedColumns
    public abstract List<EVCharge> getEVChargesForScenarioID(long id);

    @Query("SELECT * FROM hwschedule, scenario2hwschedule " +
            "WHERE scenarioID = :id AND hwschedule.id = hwScheduleID")
    @RewriteQueriesToDropUnusedColumns
    public abstract List<HWSchedule> getHWSchedulesForScenarioID(long id);

    @Query("SELECT * FROM hwdivert, scenario2hwdivert " +
            "WHERE scenarioID = :id AND hwdivert.id = hwDivertID")
    @RewriteQueriesToDropUnusedColumns
    public abstract HWDivert getHWDivertForScenarioID(long id);

    @Query("SELECT * FROM evdivert, scenario2evdivert " +
            "WHERE scenarioID = :id AND evdivert.id = evDivertID")
    @RewriteQueriesToDropUnusedColumns
    public abstract EVDivert getEVDivertForScenarioID(long id);
}
