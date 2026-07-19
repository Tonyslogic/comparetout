/*
 * Copyright (c) 2023-2024. Tony Finnerty
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

import com.tfcode.comparetout.model.importers.IntervalRow;
import com.tfcode.comparetout.model.importers.InverterDateRange;
import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.DischargeToGrid;
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
import com.tfcode.comparetout.model.scenario.HeatPump;
import com.tfcode.comparetout.model.scenario.Scenario2Battery;
import com.tfcode.comparetout.model.scenario.Scenario2HeatPump;
import com.tfcode.comparetout.model.scenario.Scenario2DischargeToGrid;
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
import com.tfcode.comparetout.model.scenario.ScenarioReadiness;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;
import com.tfcode.comparetout.model.scenario.SimKPIs;
import com.tfcode.comparetout.model.scenario.MICBreachRow;
import com.tfcode.comparetout.model.scenario.SimulationInputData;

import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object managing complex energy system scenario modeling.
 * <p>
 * This DAO orchestrates the most complex data relationships in the application,
 * managing energy system scenarios and their many-to-many relationships with
 * various energy system components. Each scenario can contain multiple:
 * <p>
 * **Core Components:**
 * - Inverters: Power conversion equipment specifications
 * - Batteries: Energy storage system configurations  
 * - Panels: Solar panel arrays with generation profiles
 * - Load Profiles: Electricity consumption patterns
 * <p>
 * **Advanced Components:**
 * - Hot Water Systems: Thermal storage and heating schedules
 * - EV Charging: Electric vehicle charging profiles and diversions
 * - Load Shifting: Demand response and time-of-use optimization
 * - Grid Discharge: Battery-to-grid energy sales
 * <p>
 * **Database Architecture:**
 * The system uses junction tables (Scenario2Component pattern) to manage
 * many-to-many relationships, allowing components to be shared between
 * scenarios while maintaining independent configurations.
 * <p>
 * **Key Operations:**
 * - Transactional scenario creation with complete component graphs
 * - Cascading deletions with orphan cleanup to maintain referential integrity
 * - Component sharing via linking/copying between scenarios
 * - Simulation data aggregation for energy flow analysis
 * - Complex time-based queries for visualization and reporting
 * <p>
 * **Simulation Pipeline:**
 * 1. Scenario definition with components
 * 2. Load profile and solar generation data preparation
 * 3. Energy simulation calculations (external to DAO)
 * 4. Simulation result storage and aggregation queries
 * 5. Cost analysis integration with pricing data
 */
@Dao
public abstract class ScenarioDAO {

    @Insert
    abstract long addNewScenario(Scenario scenario);

    @Insert
    public abstract long addNewInverter(Inverter inverter); // public: shared with InverterOps (C1)

    @Insert
    public abstract long addNewBattery(Battery battery); // public: shared with BatteryOps (C2)

    @Insert
    public abstract long addNewHeatPump(HeatPump heatPump); // public: shared with HeatPumpOps (C5)

    @Insert
    public abstract long addNewPanels(Panel panel); // public: shared with PanelOps (C6)

    @Insert
    public abstract long addNewHWSystem(HWSystem hwSystem); // public: shared with HotWaterOps (C3)

    @Insert
    abstract long addNewLoadProfile(LoadProfile loadProfile);

    @Insert
    public abstract long addNewLoadShift(LoadShift loadShift); // public: shared with BatteryOps (C2)

    @Insert
    public abstract long addNewDischarge(DischargeToGrid discharge); // public: shared with BatteryOps (C2)

    @Insert
    public abstract long addNewEVCharge(EVCharge evCharge); // public: shared with EvOps (C4)

    @Insert
    public abstract long addNewHWSchedule(HWSchedule hwSchedule); // public: shared with HotWaterOps (C3)

    @Insert
    abstract long addNewHWDivert(HWDivert hwDivert);

    @Insert
    public abstract long addNewEVDivert(EVDivert evDivert); // public: shared with EvOps (C4)

    @Insert
    public abstract void addNewScenario2Inverter(Scenario2Inverter scenario2Inverter); // public: shared with InverterOps (C1)

    @Insert
    public abstract void addNewScenario2Battery(Scenario2Battery scenario2Battery); // public: shared with BatteryOps (C2)

    @Insert
    public abstract void addNewScenario2HeatPump(Scenario2HeatPump scenario2HeatPump); // public: shared with HeatPumpOps (C5)

    @Insert
    public abstract void addNewScenario2Panel(Scenario2Panel scenario2Panel); // public: shared with PanelOps (C6)

    @Insert
    public abstract void addNewScenario2HWSystem(Scenario2HWSystem scenario2HWSystem); // public: shared with HotWaterOps (C3)

    @Insert
    abstract void addNewScenario2LoadProfile(Scenario2LoadProfile scenario2LoadProfile);

    @Insert
    public abstract void addNewScenario2LoadShift(Scenario2LoadShift scenario2LoadShift); // public: shared with BatteryOps (C2)

    @Insert
    public abstract void addNewScenario2Discharge(Scenario2DischargeToGrid scenario2Discharge); // public: shared with BatteryOps (C2)

    @Insert
    public abstract void addNewScenario2EVCharge(Scenario2EVCharge scenario2EVCharge); // public: shared with EvOps (C4)

    @Insert
    public abstract void addNewScenario2HWSchedule(Scenario2HWSchedule scenario2HWSchedule); // public: shared with HotWaterOps (C3)

    @Insert
    abstract void addNewScenario2HWDivert(Scenario2HWDivert scenario2HWDivert);

    @Insert
    public abstract void addNewScenario2EVDivert(Scenario2EVDivert scenario2EVDivert); // public: shared with EvOps (C4)

    @Query("SELECT scenarioIndex FROM scenarios WHERE scenarioName = :scenarioName")
    public abstract long getScenarioID(String scenarioName);

    /**
     * Create a complete scenario with all its component relationships atomically.
     * <p>
     * This highly complex transactional method manages the creation of a scenario
     * along with all its associated components and their many-to-many relationships.
     * <p>
     * **Process Overview:**
     * 1. Set scenario capability flags based on component presence
     * 2. Insert the main scenario record
     * 3. For each component type:
     *    - Insert component records (if needed)
     *    - Create junction table entries linking components to scenario
     * 4. Handle existing components (panels, load profiles) that may be reused
     * <p>
     * **Component Handling:**
     * - New components: Insert with ID=0, get generated ID, create junction record
     * - Existing components: Use existing ID, create junction record only
     * - Component sharing: Multiple scenarios can reference same component
     * <p>
     * **Capability Flags:**
     * The scenario record maintains boolean flags (hasInverters, hasBatteries, etc.)
     * for UI optimization - avoiding expensive JOIN queries to check component presence.
     * <p>
     * **Clobber Mode:**
     * If clobber=true, any existing scenario with the same name is deleted first,
     * allowing for scenario replacement during import operations.
     * 
     * @param scenario The main scenario record to create
     * @param components Container object with all component lists
     * @param clobber If true, replace any existing scenario with same name
     * @return The generated scenarioIndex, or 0 if creation failed
     */
    @Transaction
    long addNewScenarioWithComponents(Scenario scenario, ScenarioComponents components, boolean clobber) {
        if (clobber) {
            long oldScenarioID = getScenarioID(scenario.getScenarioName());
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
            if (!(null == components.discharges)) {
                for (DischargeToGrid discharge : components.discharges) {
                    long dischargeID = addNewDischarge(discharge);
                    Scenario2DischargeToGrid s2d = new Scenario2DischargeToGrid();
                    s2d.setScenarioID(scenarioID);
                    s2d.setDischargeID(dischargeID);
                    addNewScenario2Discharge(s2d);
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
            if (!(null == components.heatPumps)) {
                for (HeatPump hp : components.heatPumps) {
                    long heatPumpID = addNewHeatPump(hp);
                    Scenario2HeatPump s2hp = new Scenario2HeatPump();
                    s2hp.setScenarioID(scenarioID);
                    s2hp.setHeatPumpID(heatPumpID);
                    addNewScenario2HeatPump(s2hp);
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
    }

    /**
     * Load all scenarios for reactive UI display.
     * @return LiveData list of scenarios ordered by name
     */
    @Query("SELECT * FROM scenarios ORDER BY scenarios.scenarioName ASC")
    public abstract LiveData<List<Scenario>> loadScenarios();

    /**
     * Get all inverters associated with a specific scenario.
     * <p>
     * Query: SELECT * FROM inverters, scenario2inverter 
     *        WHERE scenarioID = :id AND inverters.inverterIndex = scenario2inverter.inverterID
     * <p>
     * This query JOINs inverters with the junction table to find all inverters
     * linked to the given scenario. The @RewriteQueriesToDropUnusedColumns
     * annotation optimizes the query by only selecting needed columns.
     * 
     * @param id The scenarioIndex to find inverters for
     * @return List of Inverter entities associated with the scenario
     */
    @Query("SELECT * FROM inverters, scenario2inverter " +
            "WHERE scenarioID = :id AND inverters.inverterIndex = scenario2inverter.inverterID")
    @RewriteQueriesToDropUnusedColumns
    public abstract List<Inverter> getInvertersForScenarioID (long id);

    /**
     * Get all batteries associated with a specific scenario.
     * Uses the same junction table pattern as inverters.
     * 
     * @param id The scenarioIndex to find batteries for
     * @return List of Battery entities associated with the scenario
     */
    @Query("SELECT * FROM batteries, scenario2battery " +
            "WHERE scenarioID = :id AND batteries.batteryIndex = scenario2battery.batteryID")
    @RewriteQueriesToDropUnusedColumns
    public abstract List<Battery> getBatteriesForScenarioID(long id);

    @Query("SELECT * FROM heatpumps, scenario2heatpump " +
            "WHERE scenarioID = :id AND heatpumps.heatPumpIndex = scenario2heatpump.heatPumpID")
    @RewriteQueriesToDropUnusedColumns
    public abstract List<HeatPump> getHeatPumpsForScenarioID(long id);

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

    @Query("SELECT * FROM discharge2grid, scenario2discharge " +
            "WHERE scenarioID = :id AND discharge2grid.d2gIndex = dischargeID")
    @RewriteQueriesToDropUnusedColumns
    public abstract List<DischargeToGrid> getDischargesForScenarioID(long id);

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

    // updateInverter → InverterDAO (mega-refactor C1)
    // saveInverter → InverterOps (mega-refactor C1)
    // updatePanel → PanelDAO (mega-refactor C6)

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

    @Query("SELECT A.date, A.minute, A.load, A.mod, A.dow, A.do2001, 0 AS TPV, A.millisSinceEpoch FROM " +
            "(SELECT * FROM loadprofiledata WHERE loadProfileID = " +
            "(SELECT loadProfileID FROM scenario2loadprofile WHERE scenarioID = :scenarioID)) AS A ORDER BY A.date, A.mod")
    public abstract List<SimulationInputData> getSimulationInputNoSolar(long scenarioID);

    @Query("SELECT date, minute, mod, dow, do2001, 0 AS load, pv AS TPV, millisSinceEpoch " +
            "FROM paneldata WHERE panelID = :panelID ORDER BY date, mod")
    public abstract List<SimulationInputData> getPVRowsForPanel(long panelID);

    @Insert(entity = ScenarioSimulationData.class)
    public abstract void saveSimulationDataForScenario(ArrayList<ScenarioSimulationData> simulationData);

    @Query("SELECT scenarioIndex FROM scenarios")
    public abstract List<Long> getAllScenariosThatMayNeedCosting();

    @Query("SELECT * FROM scenariosimulationdata WHERE scenarioID = :scenarioID " +
            "ORDER BY date, minuteOfDay")
    public abstract List<ScenarioSimulationData> getSimulationDataForScenario(long scenarioID);

    /** Count of intervals whose grid import exceeded the MIC (item 4c). capPerInterval = gridImportMax/12 (kWh). */
    @Query("SELECT COUNT(*) FROM scenariosimulationdata WHERE scenarioID = :scenarioID AND buy > :capPerInterval")
    public abstract int countGridImportBreaches(long scenarioID, double capPerInterval);

    /** The worst (highest-import) MIC-breach intervals for a scenario, capped at :limit (item 4c). */
    @Query("SELECT date, minuteOfDay, buy AS buy FROM scenariosimulationdata " +
            "WHERE scenarioID = :scenarioID AND buy > :capPerInterval ORDER BY buy DESC LIMIT :limit")
    public abstract List<MICBreachRow> getTopGridImportBreaches(long scenarioID, double capPerInterval, int limit);

    @Query("UPDATE scenarios SET isActive = :checked WHERE scenarioIndex = :id")
    public abstract void updateScenarioActiveStatus(int id, boolean checked);

    @Query("SELECT loadProfileIndex FROM loadprofile WHERE loadProfileIndex NOT IN " +
            "(SELECT DISTINCT loadProfileID FROM loadprofiledata)")
    public abstract List<Long> checkForMissingLoadProfileData();

    // ──────────────────────────────────────────────────────────────────────────────────────────────
    // Readiness matrix (scenario_readiness) — see ScenarioReadiness. Replaces the old derive-by-scan
    // gates (getAllScenariosThatNeedSimulation / getAllScenariosThatMayNeedCosting). simStatus int
    // literals below mirror the ScenarioReadiness.SIM_* constants (0 up-to-date, 1 needs, 2 blocked on
    // panel data, 3 blocked on weather) — SQL can't reference the Java constants, so keep them in sync.
    //
    // A MISSING readiness row is treated as "needs work" by both gates (defensive lazy-seed), but guarded
    // by the underlying data so a scenario that predates the v13 migration is NOT needlessly recomputed:
    //   - sim gate: no row counts as "needs sim" only if it has no rows in scenariosimulationdata;
    //   - cost gate: no row counts as "needs costing" only if it DOES have simulation data to cost.
    // Worker terminal-state setters upsert a row so the scenario then leaves the defensive no-row clause.
    // ──────────────────────────────────────────────────────────────────────────────────────────────

    /** Scenarios ready to simulate: flagged SIM_NEEDS, or no row yet AND not already simulated. Keeps the
     *  old load-profile-data precondition so we never attempt a scenario with no load data. */
    @Query("SELECT scenarioIndex FROM scenarios " +
            "WHERE scenarioIndex IN (SELECT DISTINCT scenarioID FROM scenario2loadprofile) " +
            "AND (SELECT DISTINCT loadProfileID FROM scenario2loadprofile WHERE scenarioID = scenarioIndex) IN (SELECT DISTINCT loadProfileID FROM loadprofiledata) " +
            "AND (scenarioIndex IN (SELECT scenarioID FROM scenario_readiness WHERE simStatus = 1) " +
            "  OR (scenarioIndex NOT IN (SELECT scenarioID FROM scenario_readiness) " +
            "      AND scenarioIndex NOT IN (SELECT DISTINCT scenarioID FROM scenariosimulationdata)))")
    public abstract List<Long> getScenarioIdsNeedingSimulation();

    /** Scenarios needing (re)costing: flagged costingNeeded with sim up-to-date, or no row yet AND has
     *  simulation data to cost. Per-plan precision is still handled by CostingDAO.costingExists. */
    @Query("SELECT scenarioIndex FROM scenarios " +
            "WHERE scenarioIndex IN (SELECT scenarioID FROM scenario_readiness WHERE costingNeeded = 1 AND simStatus = 0) " +
            "  OR (scenarioIndex NOT IN (SELECT scenarioID FROM scenario_readiness) " +
            "      AND scenarioIndex IN (SELECT DISTINCT scenarioID FROM scenariosimulationdata))")
    public abstract List<Long> getScenarioIdsNeedingCosting();

    // ── worker terminal-state setters (upsert so the row exists and leaves the no-row defensive clause) ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void replaceReadiness(ScenarioReadiness readiness);

    /** Simulation succeeded → up-to-date; a fresh sim invalidates costing. */
    @Transaction
    public void markSimulated(long scenarioID) {
        replaceReadiness(new ScenarioReadiness(scenarioID,
                ScenarioReadiness.SIM_UP_TO_DATE, true, System.currentTimeMillis()));
    }

    /** Simulation can't run yet — record why (SIM_BLOCKED_PANEL_DATA / SIM_BLOCKED_WEATHER) so the gate
     *  skips it until a self-heal fetch unblocks it. */
    @Transaction
    public void markSimBlocked(long scenarioID, int blockedStatus) {
        replaceReadiness(new ScenarioReadiness(scenarioID,
                blockedStatus, true, System.currentTimeMillis()));
    }

    /** All (this scenario × plan) costings are present → costing up-to-date. */
    @Transaction
    public void markCosted(long scenarioID) {
        replaceReadiness(new ScenarioReadiness(scenarioID,
                ScenarioReadiness.SIM_UP_TO_DATE, false, System.currentTimeMillis()));
    }

    // ── invalidation markers (plain UPDATE; a no-row scenario is already covered by the defensive gate) ──

    @Query("UPDATE scenario_readiness SET simStatus = 1, costingNeeded = 1, updated = :now WHERE scenarioID = :scenarioID")
    public abstract void markScenarioNeedsSim(long scenarioID, long now);

    @Query("UPDATE scenario_readiness SET costingNeeded = 1, updated = :now WHERE scenarioID = :scenarioID")
    public abstract void markScenarioNeedsCosting(long scenarioID, long now);

    @Query("UPDATE scenario_readiness SET simStatus = 1, costingNeeded = 1, updated = :now " +
            "WHERE scenarioID IN (SELECT scenarioID FROM scenario2loadprofile WHERE loadProfileID = :loadProfileID)")
    public abstract void markProfileScenariosNeedSim(long loadProfileID, long now);

    @Query("UPDATE scenario_readiness SET costingNeeded = 1, updated = :now " +
            "WHERE scenarioID IN (SELECT scenarioID FROM scenario2loadprofile WHERE loadProfileID = :loadProfileID)")
    public abstract void markProfileScenariosNeedCosting(long loadProfileID, long now);

    @Query("UPDATE scenario_readiness SET simStatus = 1, costingNeeded = 1, updated = :now " +
            "WHERE scenarioID IN (SELECT scenarioID FROM scenario2panel WHERE panelID = :panelID)")
    public abstract void markPanelScenarioNeedsSim(long panelID, long now);

    @Query("UPDATE scenario_readiness SET costingNeeded = 1, updated = :now " +
            "WHERE scenarioID IN (SELECT scenarioID FROM scenario2panel WHERE panelID = :panelID)")
    public abstract void markPanelScenarioNeedsCosting(long panelID, long now);

    /** A plan was added or edited → every scenario's costing for that plan is now missing/stale. */
    @Query("UPDATE scenario_readiness SET costingNeeded = 1, updated = :now")
    public abstract void markAllScenariosNeedCosting(long now);

    // ── self-heal unblock (only flips a still-blocked row back to SIM_NEEDS; never disturbs up-to-date) ──

    @Query("UPDATE scenario_readiness SET simStatus = 1, updated = :now " +
            "WHERE simStatus = 2 AND scenarioID IN (SELECT scenarioID FROM scenario2panel WHERE panelID = :panelID)")
    public abstract void unblockPanelScenarios(long panelID, long now);

    @Query("UPDATE scenario_readiness SET simStatus = 1, updated = :now WHERE simStatus = 3 AND scenarioID = :scenarioID")
    public abstract void unblockWeatherScenario(long scenarioID, long now);

    @Query("DELETE FROM scenario_readiness WHERE scenarioID = :scenarioID")
    public abstract void deleteReadinessForScenario(long scenarioID);

    /** Bulk reset (e.g. the one-time panel-data rollout, which wipes all sim/costing output) — clearing
     *  every row lets the defensive gate re-derive readiness from the (now empty) output tables. */
    @Query("DELETE FROM scenario_readiness")
    public abstract void deleteAllReadiness();

    /**
     * Completely delete a scenario and all its relationships.
     * <p>
     * This complex cascading deletion ensures referential integrity by:
     * 1. Deleting the main scenario record
     * 2. Removing all junction table entries (Scenario2Component records)
     * 3. Cleaning up orphaned components not referenced by other scenarios
     * 4. Removing associated simulation and costing data
     * <p>
     * **Deletion Order (Critical for Foreign Key Constraints):**
     * - Main scenario record first
     * - All junction table relationships
     * - Orphaned component cleanup
     * - Related data cleanup (simulation results, cost calculations)
     * <p>
     * **Orphan Cleanup:**
     * Components shared between scenarios are preserved if still referenced.
     * Only components with no remaining scenario references are deleted.
     * 
     * @param id The scenarioIndex to delete
     */
    @Transaction
    public void deleteScenario(int id) {
        deleteScenarioRow(id);
        
        // Remove all junction table relationships
        deleteBatteryRelationsForScenario(id);
        deleteHeatPumpRelationsForScenario(id);
        deleteEVChargeRelationsForScenario(id);
        deleteEVDivertRelationsForScenario(id);
        deleteHWDivertRelationsForScenario(id);
        deleteHWScheduleRelationsForScenario(id);
        deleteHWSystemRelationsForScenario(id);
        deleteInverterRelationsForScenario(id);
        deleteLoadProfileRelationsForScenario(id);
        deleteLoadShiftRelationsForScenario(id);
        deleteDischargeRelationsForScenario(id);
        deletePanelRelationsForScenario(id);

        // Clean up orphaned components (not referenced by any scenario)
        deleteOrphanBatteries();
        deleteOrphanHeatPumps();
        deleteOrphanEVCharges();
        deleteOrphanEVDiverts();
        deleteOrphanHWDiverts();
        deleteOrphanHWSchedules();
        deleteOrphanHWSystems();
        deleteOrphanInverters();
        deleteOrphanLoadProfiles();
        deleteOrphanLoadShifts();
        deleteOrphanDischarges();
        deleteOrphanPanels();

        // Clean up dependent data
        deleteOrphanLoadProfileData();
        // Prune paneldata rows whose panel was just orphaned above. Without
        // this, fetched PVGIS rows accumulate in the DB after every scenario
        // delete. Matches the per-panel deletePanelFromScenario flow.
        deleteOrphanPanelData();
        deleteSimulationDataForScenarioID(id);
        deleteCostingDataForScenarioID(id);
        // Drop the readiness row too so it can't orphan once the scenario is gone.
        deleteReadinessForScenario(id);
    }

    /**
     * Remove orphaned battery records not referenced by any scenario.
     * <p>
     * Query: DELETE FROM batteries WHERE batteryIndex NOT IN (SELECT batteryID FROM scenario2battery)
     * <p>
     * This maintenance query removes battery records that have no corresponding
     * entries in the junction table, indicating they're not associated with any scenario.
     * Essential for preventing database bloat from deleted scenarios.
     */
    @Query("DELETE FROM batteries WHERE batteryIndex NOT IN (SELECT batteryID FROM scenario2battery)")
    public abstract void deleteOrphanBatteries();

    @Query("DELETE FROM heatpumps WHERE heatPumpIndex NOT IN (SELECT heatPumpID FROM scenario2heatpump)")
    public abstract void deleteOrphanHeatPumps();

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

    @Query("DELETE FROM discharge2grid WHERE d2gIndex NOT IN (SELECT dischargeID FROM scenario2discharge)")
    public abstract void deleteOrphanDischarges();

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

    @Query("DELETE FROM scenario2heatpump WHERE scenarioID = :id")
    public abstract void deleteHeatPumpRelationsForScenario(int id);

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

    @Query("DELETE FROM scenario2discharge WHERE scenarioID = :id")
    public abstract void deleteDischargeRelationsForScenario(int id);

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
        List<HeatPump> heatPumps = getHeatPumpsForScenarioID(id);
        List<EVCharge> evCharges = getEVChargesForScenarioID(id);
        List<EVDivert> evDiverts = getEVDivertForScenarioID(id);
        HWDivert hwDivert = getHWDivertForScenarioID(id);
        List<HWSchedule> hwSchedules = getHWSchedulesForScenarioID(id);
        HWSystem hwSystem = getHWSystemForScenarioID(id);
        List<Inverter> inverters = getInvertersForScenarioID(id);
        LoadProfile loadProfile = getLoadProfileForScenarioID(id);
        List<LoadShift> loadShifts = getLoadShiftsForScenarioID(id);
        List<DischargeToGrid> discharges = getDischargesForScenarioID(id);
        List<Panel> panels = getPanelsForScenarioID(id);

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
            long newLoadProfileID = addNewLoadProfile(loadProfile);
            copyLoadProfileData(oldLoadProfileID, newLoadProfileID);
            loadProfile.setLoadProfileIndex(newLoadProfileID);
        }
        for (LoadShift l : loadShifts) l.setLoadShiftIndex(0);
        for (DischargeToGrid d : discharges) d.setD2gIndex(0);
        for (Panel p : panels) {
            long oldPanelID = p.getPanelIndex();
            p.setPanelIndex(0);
            long newPanelID = addNewPanels(p);
            copyPanelData(oldPanelID, newPanelID);
            p.setPanelIndex(newPanelID);
        }

        ScenarioComponents copyComponents = new ScenarioComponents(
                scenario, inverters, batteries, panels, hwSystem,
                loadProfile, loadShifts, discharges, evCharges, hwSchedules,
                hwDivert, evDiverts);
        copyComponents.heatPumps = heatPumps;
        addNewScenarioWithComponents(scenario, copyComponents, false);
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
                    getDischargesForScenarioID(scenario.getScenarioIndex()),
                    getEVChargesForScenarioID(scenario.getScenarioIndex()),
                    getHWSchedulesForScenarioID(scenario.getScenarioIndex()),
                    getHWDivertForScenarioID(scenario.getScenarioIndex()),
                    getEVDivertForScenarioID(scenario.getScenarioIndex())
            );
            scenarioComponents.heatPumps = getHeatPumpsForScenarioID(scenario.getScenarioIndex());
            ret.add(scenarioComponents);
        }
        return ret;
    }

    @Transaction
    public ScenarioComponents getScenarioComponentsForScenarioID(long scenarioID) {
        ScenarioComponents components = new ScenarioComponents(
                getScenarioForID(scenarioID),
                getInvertersForScenarioID(scenarioID),
                getBatteriesForScenarioID(scenarioID),
                getPanelsForScenarioID(scenarioID),
                getHWSystemForScenarioID(scenarioID),
                getLoadProfileForScenarioID(scenarioID),
                getLoadShiftsForScenarioID(scenarioID),
                getDischargesForScenarioID(scenarioID),
                getEVChargesForScenarioID(scenarioID),
                getHWSchedulesForScenarioID(scenarioID),
                getHWDivertForScenarioID(scenarioID),
                getEVDivertForScenarioID(scenarioID)
        );
        components.heatPumps = getHeatPumpsForScenarioID(scenarioID);
        return components;
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

    @Query("INSERT INTO loadProfiledata (loadProfileID, date, minute, load, mod, dow, do2001, millisSinceEpoch) " +
            "SELECT :newLoadProfileID, date, minute, load, mod, dow, do2001, millisSinceEpoch FROM loadProfiledata " +
            "WHERE loadProfileID = :oldLoadProfileID")
    public abstract void copyLoadProfileData(long oldLoadProfileID, long newLoadProfileID);

    @Transaction
    public void linkLoadProfileFromScenario(long fromScenarioID, Long toScenarioID) {
        LoadProfile lp = getLoadProfileForScenarioID(fromScenarioID);
        if (lp == null) return; // source scenario has no load profile → nothing to link (don't NPE)
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

    @Query("SELECT sum(pv) AS gen, SUM(Feed) AS sold, SUM(load) + SUM(immersionLoad) + SUM(directEVcharge) + SUM(heatPumpLoad) AS load, SUM(Buy) AS bought, " +
            "sum(kWHDivToEV) AS evDiv, sum(kWHDivToWater) AS h2oDiv, sum(pvToLoad) AS pvToLoad, sum(pvToCharge) AS pvToCharge, " +
            "sum(load) AS house, sum(immersionLoad) AS h20, sum(directEVcharge) AS EV " +
            "FROM scenariosimulationdata WHERE scenarioID = :scenarioID")
    public abstract SimKPIs getSimKPIsForScenario(Long scenarioID);

    // removeScenario2Inverter → InverterDAO (mega-refactor C1)
    // deleteInverterFromScenario → InverterOps (mega-refactor C1)
    // copyInverterFromScenario → InverterOps (mega-refactor C1)
    // loadInverterRelations → InverterDAO (mega-refactor C1)

    // linkInverterFromScenario stays here until C9: it is called by the
    // cross-domain lifecycle @Transaction linkAllComponentsFromScenario below.
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

    // loadPanelRelations → PanelDAO (mega-refactor C6)
    // removeScenario2Panel → PanelDAO (mega-refactor C6)
    // deletePanelFromScenario → PanelOps (mega-refactor C6)
    // savePanel → PanelOps (mega-refactor C6)
    // copyPanelFromScenario → PanelOps (mega-refactor C6)

    // copyPanelData stays here until C9: called by the copyScenario lifecycle
    // @Transaction (line ~875). PanelOps.copy* call it via scenarioDAO.
    @Query("INSERT INTO paneldata (PanelID, date, minute, pv, mod, dow, do2001, millisSinceEpoch) " +
            "SELECT :toPanelID, date, minute, pv, mod, dow, do2001, millisSinceEpoch FROM paneldata " +
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

    // countPanelLink → PanelDAO (mega-refactor C6)
    // linkPanelToScenario → PanelOps (mega-refactor C6)
    // copyPanelToScenario → PanelOps (mega-refactor C6)

    // getPanelForID stays here until its direct consumer (SnapshotImporter,
    // which holds scenarioDAO) migrates. PanelOps.copyPanelToScenario calls it
    // via scenarioDAO.
    @Query("SELECT * FROM panels WHERE panelIndex = :panelID")
    public abstract Panel getPanelForID(Long panelID);

    /** All panels across all scenarios (used by the one-time paneldata rollout refresh). */
    @Query("SELECT * FROM panels")
    public abstract List<Panel> getAllPanels();

    // savePanelData → PanelDAO (mega-refactor C6)
    // getPanelPVSummary → PanelDAO (mega-refactor C6)
    // countPanelDataForParameters → PanelDAO (mega-refactor C6)
    // getScenarioNamesAtLocation → PanelDAO (mega-refactor C6)
    // checkForMissingPanelData → PanelDAO (mega-refactor C6)
    // removePanelData → PanelDAO (mega-refactor C6)
    // getAllPanels + deleteAllPanelData stay here until PanelDataRefreshWorker
    // (direct scenarioDAO consumer) migrates.

    /** Wipe all generated PV data (one-time paneldata rollout refresh — it is regenerated afterwards). */
    @Query("DELETE FROM paneldata")
    public abstract void deleteAllPanelData();

    /** Wipe all simulation output (one-time rollout refresh — scenarios re-simulate). */
    @Query("DELETE FROM scenariosimulationdata")
    public abstract void deleteAllSimulationData();

    @Query("SELECT scenarioName FROM scenarios WHERE scenarioIndex IN (" +
            "SELECT scenarioID FROM scenario2loadprofile WHERE  loadProfileID = (" +
            "SELECT loadProfileID FROM scenario2loadprofile WHERE scenarioID = :scenarioID) AND scenarioID != :scenarioID )")
    public abstract List<String> getLinkedLoadProfiles(Long scenarioID);

    // getLinkedInverters → InverterDAO (mega-refactor C1)
    // getLinkedPanels → PanelDAO (mega-refactor C6)

    // loadBatteryRelations → BatteryDAO (mega-refactor C2)
    // deleteBatteryFromScenario → BatteryDAO (mega-refactor C2)
    // saveBatteryForScenario → BatteryOps (mega-refactor C2)
    // updateBattery → BatteryDAO (mega-refactor C2)
    // getLinkedBatteries → BatteryDAO (mega-refactor C2)
    // copyBatteryFromScenario → BatteryOps (mega-refactor C2)

    // linkBatteryFromScenario stays here until C9: called by the cross-domain
    // lifecycle @Transaction linkAllComponentsFromScenario.
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

    // ---- Heat pump (mirrors the battery helpers; Phase 4 of plans/hp/plan.md) ----

    // deleteHeatPumpFromScenario → HeatPumpDAO (mega-refactor C5)
    // updateHeatPump → HeatPumpDAO (mega-refactor C5)
    // getLinkedHeatPumps → HeatPumpDAO (mega-refactor C5)
    // saveHeatPumpForScenario → HeatPumpOps (mega-refactor C5)
    // copyHeatPumpFromScenario → HeatPumpOps (mega-refactor C5)

    // linkHeatPumpFromScenario stays here until C9: called by the cross-domain
    // lifecycle @Transaction linkAllComponentsFromScenario.
    @Transaction
    public void linkHeatPumpFromScenario(long fromScenarioID, Long toScenarioID) {
        List<HeatPump> heatPumps = getHeatPumpsForScenarioID(fromScenarioID);
        for (HeatPump heatPump : heatPumps) {
            Scenario2HeatPump scenario2HeatPump = new Scenario2HeatPump();
            scenario2HeatPump.setScenarioID(toScenarioID);
            scenario2HeatPump.setHeatPumpID(heatPump.getHeatPumpIndex());
            addNewScenario2HeatPump(scenario2HeatPump);

            Scenario toScenario = getScenario(toScenarioID);
            toScenario.setHasHeatPump(true);
            updateScenario(toScenario);
        }

        deleteOrphanHeatPumps();
    }

    //##############################################
    // loadLoadShiftRelations → BatteryDAO (mega-refactor C2)
    // deleteLoadShiftFromScenario → BatteryDAO (mega-refactor C2)
    // saveLoadShiftForScenario → BatteryOps (mega-refactor C2)
    // updateLoadShift → BatteryDAO (mega-refactor C2)
    // getLinkedLoadShifts → BatteryDAO (mega-refactor C2)
    // copyLoadShiftFromScenario → BatteryOps (mega-refactor C2)

    // linkLoadShiftFromScenario stays here until C9: called by the cross-domain
    // lifecycle @Transaction linkAllComponentsFromScenario.
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

    // loadDischargeRelations → BatteryDAO (mega-refactor C2)
    // deleteDischargeFromScenario → BatteryDAO (mega-refactor C2)
    // saveDischargeForScenario → BatteryOps (mega-refactor C2)
    // updateDischarge → BatteryDAO (mega-refactor C2)
    // getLinkedDischarges → BatteryDAO (mega-refactor C2)
    // copyDischargeFromScenario → BatteryOps (mega-refactor C2)

    // linkDischargeFromScenario stays here until C9: called by the cross-domain
    // lifecycle @Transaction linkAllComponentsFromScenario.
    @Transaction
    public void linkDischargeFromScenario(long fromScenarioID, Long toScenarioID) {
        List<DischargeToGrid> discharges = getDischargesForScenarioID(fromScenarioID);
        for (DischargeToGrid discharge: discharges) {
            Scenario2DischargeToGrid scenario2Discharge = new Scenario2DischargeToGrid();
            scenario2Discharge.setScenarioID(toScenarioID);
            scenario2Discharge.setDischargeID(discharge.getD2gIndex());
            addNewScenario2Discharge(scenario2Discharge);

            Scenario toScenario = getScenario(toScenarioID);
            toScenario.setHasDischarges(true);
            updateScenario(toScenario);
        }

        deleteOrphanDischarges();
    }
    //##############################################

    /**
     * Generate hourly energy flow data for bar chart visualization.
     * <p>
     * Query breakdown:
     * - minuteOfDay / 60 AS Hour: Convert minute of day to hour (0-23)
     * - Multiple SUM() aggregations: Total energy flows by category
     * - GROUP BY Hour: Aggregate all data points for each hour of the day
     * - WHERE dayOf2001 = :dayOfYear: Filter to specific day of year
     * <p>
     * **Energy Flow Categories:**
     * - Load: Total electricity consumption
     * - Feed: Electricity exported to grid  
     * - Buy: Electricity imported from grid
     * - PV: Solar generation
     * - PV2Battery, PV2Load: Solar energy routing
     * - Battery2Load, Grid2Battery: Battery energy flows
     * - EVSchedule, HWSchedule: Scheduled charging/heating
     * - EVDivert, HWDivert: Excess solar diversion
     * - Bat2Grid: Battery discharge to grid
     * <p>
     * Result: 24 rows (one per hour) showing energy flows for the selected day,
     * used for detailed hourly energy balance visualization.
     * 
     * @param scenarioID The scenario to analyze
     * @param dayOfYear The day of year (1-365/366) to visualize
     * @return Hourly energy flow data for bar chart display
     */
    @Query("SELECT minuteOfDay / 60 AS Hour, sum(load) AS Load, sum(feed) AS Feed, sum(Buy) AS Buy, " +
            "sum(pv) AS PV, sum(pvToCharge) AS PV2Battery, sum(pvToLoad) AS PV2Load, sum(batToLoad) AS Battery2Load, sum(gridToBattery) AS Grid2Battery, " +
            "sum(directEVcharge) AS EVSchedule, sum(immersionLoad) AS HWSchedule, sum(kWHDivToEV) AS EVDivert, sum(kWHDivToWater) AS HWDivert, " +
            "sum(battery2Grid) AS Bat2Grid, sum(heatPumpLoad) AS HeatPump, sum(heatPumpBackupLoad) AS HeatPumpBackup, sum(heatPumpHeat) AS HeatPumpHeat, avg(heatPumpCop) AS HeatPumpCop, avg(heatPumpOutdoorTemp) AS HeatPumpTemp, avg(heatPumpWindSpeed) AS HeatPumpWind FROM scenariosimulationdata WHERE dayOf2001 = :dayOfYear AND scenarioID = :scenarioID " +
            "GROUP BY Hour ORDER BY Hour")
    public abstract List<ScenarioBarChartData> getBarData(Long scenarioID, int dayOfYear);

    /**
     * Get scenario simulation data for line graph visualization.
     * <p>
     * Query: SELECT minuteOfDay, SOC, waterTemp FROM scenariosimulationdata 
     *        WHERE dayOf2001 = :dayOfYear AND scenarioID = :scenarioID ORDER BY minuteOfDay
     * <p>
     * This query retrieves continuous time-series data for line graphs:
     * - minuteOfDay: Time axis (0-1439 minutes)
     * - SOC: Battery State of Charge percentage
     * - waterTemp: Hot water system temperature
     * <p>
     * Used for detailed temporal analysis showing how battery and thermal
     * storage systems respond to energy flows throughout the day.
     * 
     * @param scenarioID The scenario to analyze
     * @param dayOfYear The day of year to visualize
     * @return Time-series data for line graph display
     */
    @Query("SELECT minuteOfDay, SOC, waterTemp, heatPumpCop, heatPumpOutdoorTemp, heatPumpWindSpeed FROM scenariosimulationdata WHERE dayOf2001 = :dayOfYear AND scenarioID = :scenarioID  ORDER BY minuteOfDay")
    public abstract List<ScenarioLineGraphData> getLineData(Long scenarioID, int dayOfYear);

    @Query("SELECT substr(Date, 9) AS Hour, sum(load) AS Load, sum(feed) AS Feed, sum(Buy) AS Buy, " +
            "sum(pv) AS PV, sum(pvToCharge) AS PV2Battery, sum(pvToLoad) AS PV2Load, sum(batToLoad) AS Battery2Load, sum(gridToBattery) AS Grid2Battery, " +
            "sum(directEVcharge) AS EVSchedule, sum(immersionLoad) AS HWSchedule, sum(kWHDivToEV) AS EVDivert, sum(kWHDivToWater) AS HWDivert, " +
            "sum(battery2Grid) AS Bat2Grid, sum(heatPumpLoad) AS HeatPump, sum(heatPumpBackupLoad) AS HeatPumpBackup, sum(heatPumpHeat) AS HeatPumpHeat, avg(heatPumpCop) AS HeatPumpCop, avg(heatPumpOutdoorTemp) AS HeatPumpTemp, avg(heatPumpWindSpeed) AS HeatPumpWind FROM scenariosimulationdata WHERE substr(Date, 6,2) IN (" +
            "SELECT DISTINCT substr(Date, 6,2) AS Month FROM scenariosimulationdata WHERE dayOf2001 = :dayOfYear) " +
            "AND scenarioID = :scenarioID GROUP BY dayOf2001 ORDER BY dayOf2001")
    public abstract List<ScenarioBarChartData> getMonthlyBarData(Long scenarioID, int dayOfYear);

    @Query("SELECT substr(Date, 6,2) AS Hour, sum(load) AS Load, sum(feed) AS Feed, sum(Buy) AS Buy, " +
            "sum(pv) AS PV, sum(pvToCharge) AS PV2Battery, sum(pvToLoad) AS PV2Load, sum(batToLoad) AS Battery2Load, sum(gridToBattery) AS Grid2Battery, " +
            "sum(directEVcharge) AS EVSchedule, sum(immersionLoad) AS HWSchedule, sum(kWHDivToEV) AS EVDivert, sum(kWHDivToWater) AS HWDivert, " +
            "sum(battery2Grid) AS Bat2Grid, sum(heatPumpLoad) AS HeatPump, sum(heatPumpBackupLoad) AS HeatPumpBackup, sum(heatPumpHeat) AS HeatPumpHeat, avg(heatPumpCop) AS HeatPumpCop, avg(heatPumpOutdoorTemp) AS HeatPumpTemp, avg(heatPumpWindSpeed) AS HeatPumpWind FROM scenariosimulationdata WHERE scenarioID = :scenarioID " +
            "GROUP BY substr(Date, 6,2) ORDER BY substr(Date, 6,2)")
    public abstract List<ScenarioBarChartData> getYearBarData(Long scenarioID);

    // getLinkedHWSystems → HotWaterDAO (mega-refactor C3)
    // saveHWSystemForScenario → HotWaterOps (mega-refactor C3)
    // updateHWSystem → HotWaterDAO (mega-refactor C3)

    // linkHWSystemFromScenario stays here until C9: called by the cross-domain
    // lifecycle @Transaction linkAllComponentsFromScenario.
    @Transaction
    public void linkHWSystemFromScenario(long fromScenarioID, Long toScenarioID) {
        HWSystem hwSystem = getHWSystemForScenarioID(fromScenarioID);
        if (hwSystem == null) return; // source scenario has no hot-water system → nothing to link (don't NPE)

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

    // copyHWSettingsFromScenario → HotWaterOps (mega-refactor C3)
    // loadHWSystemRelations → HotWaterDAO (mega-refactor C3)

    // saveHWDivert (+ updateHWDivert) stays here until C9: called by the
    // cross-domain lifecycle @Transaction linkAllComponentsFromScenario.
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

    // getLinkedHWSchedules → HotWaterDAO (mega-refactor C3)
    // loadHWScheduleRelations → HotWaterDAO (mega-refactor C3)
    // deleteHWScheduleFromScenario1 → HotWaterDAO (mega-refactor C3)
    // deleteHWScheduleFromScenario → HotWaterOps (mega-refactor C3)
    // saveHWScheduleForScenario → HotWaterOps (mega-refactor C3)
    // updateHWSchedule → HotWaterDAO (mega-refactor C3)
    // copyHWScheduleFromScenario → HotWaterOps (mega-refactor C3)

    // linkHWScheduleFromScenario stays here until C9: called by the cross-domain
    // lifecycle @Transaction linkAllComponentsFromScenario.
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

    // getLinkedEVCharges → EvDAO (mega-refactor C4)
    // loadEVChargeRelations → EvDAO (mega-refactor C4)
    // deleteEVChargeFromScenario → EvDAO (mega-refactor C4)
    // saveEVChargeForScenario → EvOps (mega-refactor C4)
    // updateEVCharge → EvDAO (mega-refactor C4)
    // copyEVChargeFromScenario → EvOps (mega-refactor C4)

    // linkEVChargeFromScenario stays here until C9: called by the cross-domain
    // lifecycle @Transaction linkAllComponentsFromScenario. (Not @Transaction.)
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

    // Entire EV-divert block → EvDAO / EvOps (mega-refactor C4):
    //   getLinkedEVDiverts, loadEVDivertRelations, deleteEVDivertFromScenario,
    //   updateEVDivert → EvDAO; saveEVDivertForScenario, copyEVDivertFromScenario,
    //   linkEVDivertFromScenario → EvOps. linkEVDivertFromScenario is NOT called
    //   by linkAllComponentsFromScenario (unlike the EV-charge link), so it moved
    //   rather than staying for C9.

    /**
     * Atomically link every component of {@code fromScenarioID} onto {@code toScenarioID} in a single
     * transaction on one thread (the order mirrors the wizard's "Create scenario → Link from existing" path).
     * Previously the wizard fired these ten links as separate fire-and-forget tasks on the 8-thread write
     * executor: they raced (the read-modify-write of the scenario's has* flags could lose updates), and a
     * missing optional component (e.g. no hot-water system) threw an NPE mid-race that crashed the app before
     * the load-profile/inverter links had run — losing them. Running them in order under one @Transaction
     * (with the singleton links now null-safe) removes both problems.
     */
    @Transaction
    public void linkAllComponentsFromScenario(long fromScenarioID, long toScenarioID, HWDivert hwDivert) {
        Long to = toScenarioID;
        linkLoadProfileFromScenario(fromScenarioID, to);
        linkEVChargeFromScenario(fromScenarioID, to);
        linkInverterFromScenario(fromScenarioID, to);
        linkPanelFromScenario(fromScenarioID, to);
        linkBatteryFromScenario(fromScenarioID, to);
        linkHeatPumpFromScenario(fromScenarioID, to);
        linkLoadShiftFromScenario(fromScenarioID, to);
        linkDischargeFromScenario(fromScenarioID, to);
        linkHWSystemFromScenario(fromScenarioID, to);
        linkHWScheduleFromScenario(fromScenarioID, toScenarioID);
        // The HW divert is replayed from wizard/builder state (not linked from the source), but it lives in
        // the same transaction so its has*-flag write can't race/clobber the links' flag writes.
        if (hwDivert != null) saveHWDivert(to, hwDivert);
    }

    @Query("SELECT DISTINCT gridExportMax FROM loadprofile, scenario2loadprofile WHERE loadProfileID = loadProfileIndex AND scenarioID = :scenarioID")
    public abstract double getGridExportMaxForScenario(long scenarioID);



    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "sum(pvToCharge) AS PV2BAT, sum(pvToLoad) AS PV2LOAD, sum(batToLoad) AS BAT2LOAD, " +
            "sum(gridToBattery) AS GRID2BAT, sum(directEVcharge) AS EVSCHEDULE, sum(kWHDivToEV) AS EVDIVERT, " +
            "sum(immersionLoad) AS HWSCHEDULE, sum(kWHDivToWater) AS HWDIVERT, sum(battery2Grid) AS BAT2GRID, " +
            "sum(pvToCharge + gridToBattery) AS BAT_CHARGE, sum(batToLoad + battery2Grid) AS BAT_DISCHARGE, " +
            "0 AS PV2GRID, 0 AS GRID2LOAD, 0 AS EV_ACTUAL, 0 AS BAT_CHARGE_IN, 0 AS BAT_DISCHARGE_OUT, 0 AS HW_ACTUAL, 0 AS HP_ACTUAL, sum(heatPumpLoad) AS HEATPUMP, sum(heatPumpBackupLoad) AS HEATPUMPBACKUP, sum(heatPumpHeat) AS HEATPUMPHEAT, avg(heatPumpCop) AS HEATPUMPCOP, avg(heatPumpOutdoorTemp) AS HEATPUMPTEMP, avg(heatPumpWindSpeed) AS HEATPUMPWIND, " +
            "cast ((minuteOfDay / 60) as INTEGER) AS INTERVAL " +
            "FROM scenariosimulationdata WHERE date >= :from AND date <= :to AND scenarioID = :sysSN GROUP BY  INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumHour(String sysSN, String from, String to);

    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "sum(pvToCharge) AS PV2BAT, sum(pvToLoad) AS PV2LOAD, sum(batToLoad) AS BAT2LOAD, " +
            "sum(gridToBattery) AS GRID2BAT, sum(directEVcharge) AS EVSCHEDULE, sum(kWHDivToEV) AS EVDIVERT, " +
            "sum(immersionLoad) AS HWSCHEDULE, sum(kWHDivToWater) AS HWDIVERT, sum(battery2Grid) AS BAT2GRID, " +
            "sum(pvToCharge + gridToBattery) AS BAT_CHARGE, sum(batToLoad + battery2Grid) AS BAT_DISCHARGE, " +
            "0 AS PV2GRID, 0 AS GRID2LOAD, 0 AS EV_ACTUAL, 0 AS BAT_CHARGE_IN, 0 AS BAT_DISCHARGE_OUT, 0 AS HW_ACTUAL, 0 AS HP_ACTUAL, sum(heatPumpLoad) AS HEATPUMP, sum(heatPumpBackupLoad) AS HEATPUMPBACKUP, sum(heatPumpHeat) AS HEATPUMPHEAT, avg(heatPumpCop) AS HEATPUMPCOP, avg(heatPumpOutdoorTemp) AS HEATPUMPTEMP, avg(heatPumpWindSpeed) AS HEATPUMPWIND, " +
            "cast (strftime('%j', date) as INTEGER) AS INTERVAL " +
            "FROM scenariosimulationdata WHERE date >= :from AND date <= :to AND scenarioID = :sysSN GROUP BY INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumDOY(String sysSN, String from, String to);

    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "sum(pvToCharge) AS PV2BAT, sum(pvToLoad) AS PV2LOAD, sum(batToLoad) AS BAT2LOAD, " +
            "sum(gridToBattery) AS GRID2BAT, sum(directEVcharge) AS EVSCHEDULE, sum(kWHDivToEV) AS EVDIVERT, " +
            "sum(immersionLoad) AS HWSCHEDULE, sum(kWHDivToWater) AS HWDIVERT, sum(battery2Grid) AS BAT2GRID, " +
            "sum(pvToCharge + gridToBattery) AS BAT_CHARGE, sum(batToLoad + battery2Grid) AS BAT_DISCHARGE, " +
            "0 AS PV2GRID, 0 AS GRID2LOAD, 0 AS EV_ACTUAL, 0 AS BAT_CHARGE_IN, 0 AS BAT_DISCHARGE_OUT, 0 AS HW_ACTUAL, 0 AS HP_ACTUAL, sum(heatPumpLoad) AS HEATPUMP, sum(heatPumpBackupLoad) AS HEATPUMPBACKUP, sum(heatPumpHeat) AS HEATPUMPHEAT, avg(heatPumpCop) AS HEATPUMPCOP, avg(heatPumpOutdoorTemp) AS HEATPUMPTEMP, avg(heatPumpWindSpeed) AS HEATPUMPWIND, " +
            "cast (strftime('%w', date) as INTEGER) AS INTERVAL " +
            "FROM scenariosimulationdata WHERE date >= :from AND date <= :to AND scenarioID = :sysSN GROUP BY INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumDOW(String sysSN, String from, String to);

    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "sum(pvToCharge) AS PV2BAT, sum(pvToLoad) AS PV2LOAD, sum(batToLoad) AS BAT2LOAD, " +
            "sum(gridToBattery) AS GRID2BAT, sum(directEVcharge) AS EVSCHEDULE, sum(kWHDivToEV) AS EVDIVERT, " +
            "sum(immersionLoad) AS HWSCHEDULE, sum(kWHDivToWater) AS HWDIVERT, sum(battery2Grid) AS BAT2GRID, " +
            "sum(pvToCharge + gridToBattery) AS BAT_CHARGE, sum(batToLoad + battery2Grid) AS BAT_DISCHARGE, " +
            "0 AS PV2GRID, 0 AS GRID2LOAD, 0 AS EV_ACTUAL, 0 AS BAT_CHARGE_IN, 0 AS BAT_DISCHARGE_OUT, 0 AS HW_ACTUAL, 0 AS HP_ACTUAL, sum(heatPumpLoad) AS HEATPUMP, sum(heatPumpBackupLoad) AS HEATPUMPBACKUP, sum(heatPumpHeat) AS HEATPUMPHEAT, avg(heatPumpCop) AS HEATPUMPCOP, avg(heatPumpOutdoorTemp) AS HEATPUMPTEMP, avg(heatPumpWindSpeed) AS HEATPUMPWIND, " +
            "strftime('%Y', date) || strftime('%m', date) AS INTERVAL " +
            "FROM scenariosimulationdata WHERE date >= :from AND date <= :to AND scenarioID = :sysSN GROUP BY INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumMonth(String sysSN, String from, String to);

    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "sum(pvToCharge) AS PV2BAT, sum(pvToLoad) AS PV2LOAD, sum(batToLoad) AS BAT2LOAD, " +
            "sum(gridToBattery) AS GRID2BAT, sum(directEVcharge) AS EVSCHEDULE, sum(kWHDivToEV) AS EVDIVERT, " +
            "sum(immersionLoad) AS HWSCHEDULE, sum(kWHDivToWater) AS HWDIVERT, sum(battery2Grid) AS BAT2GRID, " +
            "sum(pvToCharge + gridToBattery) AS BAT_CHARGE, sum(batToLoad + battery2Grid) AS BAT_DISCHARGE, " +
            "0 AS PV2GRID, 0 AS GRID2LOAD, 0 AS EV_ACTUAL, 0 AS BAT_CHARGE_IN, 0 AS BAT_DISCHARGE_OUT, 0 AS HW_ACTUAL, 0 AS HP_ACTUAL, sum(heatPumpLoad) AS HEATPUMP, sum(heatPumpBackupLoad) AS HEATPUMPBACKUP, sum(heatPumpHeat) AS HEATPUMPHEAT, avg(heatPumpCop) AS HEATPUMPCOP, avg(heatPumpOutdoorTemp) AS HEATPUMPTEMP, avg(heatPumpWindSpeed) AS HEATPUMPWIND, " +
            "cast (strftime('%Y', date) as INTEGER) AS INTERVAL " +
            "FROM scenariosimulationdata WHERE date >= :from AND date <= :to AND scenarioID = :sysSN GROUP BY INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumYear(String sysSN, String from, String to);

    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, " +
            "avg(PV2BAT) AS PV2BAT, avg(PV2LOAD) AS PV2LOAD, avg(BAT2LOAD) AS BAT2LOAD, avg(GRID2BAT) AS GRID2BAT, " +
            "avg(EVSCHEDULE) AS EVSCHEDULE, avg(EVDIVERT) AS EVDIVERT, avg(HWSCHEDULE) AS HWSCHEDULE, avg(HWDIVERT) AS HWDIVERT," +
            "avg(BAT2GRID) AS BAT2GRID, avg(BAT_CHARGE) AS BAT_CHARGE, avg(BAT_DISCHARGE) AS BAT_DISCHARGE, avg(PV2GRID) AS PV2GRID, avg(GRID2LOAD) AS GRID2LOAD, avg(EV_ACTUAL) AS EV_ACTUAL, avg(BAT_CHARGE_IN) AS BAT_CHARGE_IN, avg(BAT_DISCHARGE_OUT) AS BAT_DISCHARGE_OUT, avg(HW_ACTUAL) AS HW_ACTUAL, avg(HP_ACTUAL) AS HP_ACTUAL, avg(HEATPUMP) AS HEATPUMP, avg(HEATPUMPBACKUP) AS HEATPUMPBACKUP, avg(HEATPUMPHEAT) AS HEATPUMPHEAT, avg(HEATPUMPCOP) AS HEATPUMPCOP, avg(HEATPUMPTEMP) AS HEATPUMPTEMP, avg(HEATPUMPWIND) AS HEATPUMPWIND, INTERVAL FROM (" +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "sum(pvToCharge) AS PV2BAT, sum(pvToLoad) AS PV2LOAD, sum(batToLoad) AS BAT2LOAD, " +
            "sum(gridToBattery) AS GRID2BAT, sum(directEVcharge) AS EVSCHEDULE, sum(kWHDivToEV) AS EVDIVERT, " +
            "sum(immersionLoad) AS HWSCHEDULE, sum(kWHDivToWater) AS HWDIVERT, sum(battery2Grid) AS BAT2GRID, " +
            "sum(pvToCharge + gridToBattery) AS BAT_CHARGE, sum(batToLoad + battery2Grid) AS BAT_DISCHARGE, " +
            "0 AS PV2GRID, 0 AS GRID2LOAD, 0 AS EV_ACTUAL, 0 AS BAT_CHARGE_IN, 0 AS BAT_DISCHARGE_OUT, 0 AS HW_ACTUAL, 0 AS HP_ACTUAL, sum(heatPumpLoad) AS HEATPUMP, sum(heatPumpBackupLoad) AS HEATPUMPBACKUP, sum(heatPumpHeat) AS HEATPUMPHEAT, avg(heatPumpCop) AS HEATPUMPCOP, avg(heatPumpOutdoorTemp) AS HEATPUMPTEMP, avg(heatPumpWindSpeed) AS HEATPUMPWIND, " +
            "cast ((minuteOfDay / 60) as INTEGER) AS INTERVAL " +
            " FROM scenariosimulationdata WHERE date >= :from AND date <= :to AND scenarioID = :sysSN " +
            " GROUP BY cast (strftime('%Y', date) as integer), cast (strftime('%j', date) as integer), INTERVAL ORDER BY INTERVAL " +
            " ) GROUP BY INTERVAL")
    public abstract List<IntervalRow> avgHour(String sysSN, String from, String to);

    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, " +
            "avg(PV2BAT) AS PV2BAT, avg(PV2LOAD) AS PV2LOAD, avg(BAT2LOAD) AS BAT2LOAD, avg(GRID2BAT) AS GRID2BAT, " +
            "avg(EVSCHEDULE) AS EVSCHEDULE, avg(EVDIVERT) AS EVDIVERT, avg(HWSCHEDULE) AS HWSCHEDULE, avg(HWDIVERT) AS HWDIVERT," +
            "avg(BAT2GRID) AS BAT2GRID, avg(BAT_CHARGE) AS BAT_CHARGE, avg(BAT_DISCHARGE) AS BAT_DISCHARGE, avg(PV2GRID) AS PV2GRID, avg(GRID2LOAD) AS GRID2LOAD, avg(EV_ACTUAL) AS EV_ACTUAL, avg(BAT_CHARGE_IN) AS BAT_CHARGE_IN, avg(BAT_DISCHARGE_OUT) AS BAT_DISCHARGE_OUT, avg(HW_ACTUAL) AS HW_ACTUAL, avg(HP_ACTUAL) AS HP_ACTUAL, avg(HEATPUMP) AS HEATPUMP, avg(HEATPUMPBACKUP) AS HEATPUMPBACKUP, avg(HEATPUMPHEAT) AS HEATPUMPHEAT, avg(HEATPUMPCOP) AS HEATPUMPCOP, avg(HEATPUMPTEMP) AS HEATPUMPTEMP, avg(HEATPUMPWIND) AS HEATPUMPWIND, INTERVAL FROM ( " +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "sum(pvToCharge) AS PV2BAT, sum(pvToLoad) AS PV2LOAD, sum(batToLoad) AS BAT2LOAD, " +
            "sum(gridToBattery) AS GRID2BAT, sum(directEVcharge) AS EVSCHEDULE, sum(kWHDivToEV) AS EVDIVERT, " +
            "sum(immersionLoad) AS HWSCHEDULE, sum(kWHDivToWater) AS HWDIVERT, sum(battery2Grid) AS BAT2GRID, " +
            "sum(pvToCharge + gridToBattery) AS BAT_CHARGE, sum(batToLoad + battery2Grid) AS BAT_DISCHARGE, " +
            "0 AS PV2GRID, 0 AS GRID2LOAD, 0 AS EV_ACTUAL, 0 AS BAT_CHARGE_IN, 0 AS BAT_DISCHARGE_OUT, 0 AS HW_ACTUAL, 0 AS HP_ACTUAL, sum(heatPumpLoad) AS HEATPUMP, sum(heatPumpBackupLoad) AS HEATPUMPBACKUP, sum(heatPumpHeat) AS HEATPUMPHEAT, avg(heatPumpCop) AS HEATPUMPCOP, avg(heatPumpOutdoorTemp) AS HEATPUMPTEMP, avg(heatPumpWindSpeed) AS HEATPUMPWIND, " +
            "cast (strftime('%j', date) as INTEGER) AS INTERVAL " +
            " FROM scenariosimulationdata WHERE date >= :from AND date <= :to AND scenarioID = :sysSN GROUP BY cast (strftime('%Y', date) as integer), INTERVAL ORDER BY INTERVAL " +
            " ) GROUP BY INTERVAL")
    public abstract List<IntervalRow> avgDOY(String sysSN, String from, String to);

    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, " +
            "avg(PV2BAT) AS PV2BAT, avg(PV2LOAD) AS PV2LOAD, avg(BAT2LOAD) AS BAT2LOAD, avg(GRID2BAT) AS GRID2BAT, " +
            "avg(EVSCHEDULE) AS EVSCHEDULE, avg(EVDIVERT) AS EVDIVERT, avg(HWSCHEDULE) AS HWSCHEDULE, avg(HWDIVERT) AS HWDIVERT," +
            "avg(BAT2GRID) AS BAT2GRID, avg(BAT_CHARGE) AS BAT_CHARGE, avg(BAT_DISCHARGE) AS BAT_DISCHARGE, avg(PV2GRID) AS PV2GRID, avg(GRID2LOAD) AS GRID2LOAD, avg(EV_ACTUAL) AS EV_ACTUAL, avg(BAT_CHARGE_IN) AS BAT_CHARGE_IN, avg(BAT_DISCHARGE_OUT) AS BAT_DISCHARGE_OUT, avg(HW_ACTUAL) AS HW_ACTUAL, avg(HP_ACTUAL) AS HP_ACTUAL, avg(HEATPUMP) AS HEATPUMP, avg(HEATPUMPBACKUP) AS HEATPUMPBACKUP, avg(HEATPUMPHEAT) AS HEATPUMPHEAT, avg(HEATPUMPCOP) AS HEATPUMPCOP, avg(HEATPUMPTEMP) AS HEATPUMPTEMP, avg(HEATPUMPWIND) AS HEATPUMPWIND, INTERVAL FROM (" +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "sum(pvToCharge) AS PV2BAT, sum(pvToLoad) AS PV2LOAD, sum(batToLoad) AS BAT2LOAD, " +
            "sum(gridToBattery) AS GRID2BAT, sum(directEVcharge) AS EVSCHEDULE, sum(kWHDivToEV) AS EVDIVERT, " +
            "sum(immersionLoad) AS HWSCHEDULE, sum(kWHDivToWater) AS HWDIVERT, sum(battery2Grid) AS BAT2GRID, " +
            "sum(pvToCharge + gridToBattery) AS BAT_CHARGE, sum(batToLoad + battery2Grid) AS BAT_DISCHARGE, " +
            "0 AS PV2GRID, 0 AS GRID2LOAD, 0 AS EV_ACTUAL, 0 AS BAT_CHARGE_IN, 0 AS BAT_DISCHARGE_OUT, 0 AS HW_ACTUAL, 0 AS HP_ACTUAL, sum(heatPumpLoad) AS HEATPUMP, sum(heatPumpBackupLoad) AS HEATPUMPBACKUP, sum(heatPumpHeat) AS HEATPUMPHEAT, avg(heatPumpCop) AS HEATPUMPCOP, avg(heatPumpOutdoorTemp) AS HEATPUMPTEMP, avg(heatPumpWindSpeed) AS HEATPUMPWIND, " +
            "cast (strftime('%w', date) as INTEGER) AS INTERVAL " +
            " FROM scenariosimulationdata WHERE date >= :from AND date <= :to AND scenarioID = :sysSN " +
            " GROUP BY cast (strftime('%Y', date) as integer), cast (strftime('%W', date) as integer), INTERVAL ORDER BY INTERVAL" +
            " ) GROUP BY INTERVAL")
    public abstract List<IntervalRow> avgDOW(String sysSN, String from, String to);

    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, " +
            "avg(PV2BAT) AS PV2BAT, avg(PV2LOAD) AS PV2LOAD, avg(BAT2LOAD) AS BAT2LOAD, avg(GRID2BAT) AS GRID2BAT, " +
            "avg(EVSCHEDULE) AS EVSCHEDULE, avg(EVDIVERT) AS EVDIVERT, avg(HWSCHEDULE) AS HWSCHEDULE, avg(HWDIVERT) AS HWDIVERT," +
            "avg(BAT2GRID) AS BAT2GRID, avg(BAT_CHARGE) AS BAT_CHARGE, avg(BAT_DISCHARGE) AS BAT_DISCHARGE, avg(PV2GRID) AS PV2GRID, avg(GRID2LOAD) AS GRID2LOAD, avg(EV_ACTUAL) AS EV_ACTUAL, avg(BAT_CHARGE_IN) AS BAT_CHARGE_IN, avg(BAT_DISCHARGE_OUT) AS BAT_DISCHARGE_OUT, avg(HW_ACTUAL) AS HW_ACTUAL, avg(HP_ACTUAL) AS HP_ACTUAL, avg(HEATPUMP) AS HEATPUMP, avg(HEATPUMPBACKUP) AS HEATPUMPBACKUP, avg(HEATPUMPHEAT) AS HEATPUMPHEAT, avg(HEATPUMPCOP) AS HEATPUMPCOP, avg(HEATPUMPTEMP) AS HEATPUMPTEMP, avg(HEATPUMPWIND) AS HEATPUMPWIND, INTERVAL FROM (" +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "sum(pvToCharge) AS PV2BAT, sum(pvToLoad) AS PV2LOAD, sum(batToLoad) AS BAT2LOAD, " +
            "sum(gridToBattery) AS GRID2BAT, sum(directEVcharge) AS EVSCHEDULE, sum(kWHDivToEV) AS EVDIVERT, " +
            "sum(immersionLoad) AS HWSCHEDULE, sum(kWHDivToWater) AS HWDIVERT, sum(battery2Grid) AS BAT2GRID, " +
            "sum(pvToCharge + gridToBattery) AS BAT_CHARGE, sum(batToLoad + battery2Grid) AS BAT_DISCHARGE, " +
            "0 AS PV2GRID, 0 AS GRID2LOAD, 0 AS EV_ACTUAL, 0 AS BAT_CHARGE_IN, 0 AS BAT_DISCHARGE_OUT, 0 AS HW_ACTUAL, 0 AS HP_ACTUAL, sum(heatPumpLoad) AS HEATPUMP, sum(heatPumpBackupLoad) AS HEATPUMPBACKUP, sum(heatPumpHeat) AS HEATPUMPHEAT, avg(heatPumpCop) AS HEATPUMPCOP, avg(heatPumpOutdoorTemp) AS HEATPUMPTEMP, avg(heatPumpWindSpeed) AS HEATPUMPWIND, " +
            "strftime('%m', date) as INTERVAL" +
            " FROM scenariosimulationdata WHERE date >= :from AND date <= :to AND scenarioID = :sysSN " +
            " GROUP BY INTERVAL ORDER BY INTERVAL, date) GROUP BY INTERVAL")
    public abstract List<IntervalRow> avgMonth(String sysSN, String from, String to);

    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, " +
            "avg(PV2BAT) AS PV2BAT, avg(PV2LOAD) AS PV2LOAD, avg(BAT2LOAD) AS BAT2LOAD, avg(GRID2BAT) AS GRID2BAT, " +
            "avg(EVSCHEDULE) AS EVSCHEDULE, avg(EVDIVERT) AS EVDIVERT, avg(HWSCHEDULE) AS HWSCHEDULE, avg(HWDIVERT) AS HWDIVERT," +
            "avg(BAT2GRID) AS BAT2GRID, avg(BAT_CHARGE) AS BAT_CHARGE, avg(BAT_DISCHARGE) AS BAT_DISCHARGE, avg(PV2GRID) AS PV2GRID, avg(GRID2LOAD) AS GRID2LOAD, avg(EV_ACTUAL) AS EV_ACTUAL, avg(BAT_CHARGE_IN) AS BAT_CHARGE_IN, avg(BAT_DISCHARGE_OUT) AS BAT_DISCHARGE_OUT, avg(HW_ACTUAL) AS HW_ACTUAL, avg(HP_ACTUAL) AS HP_ACTUAL, avg(HEATPUMP) AS HEATPUMP, avg(HEATPUMPBACKUP) AS HEATPUMPBACKUP, avg(HEATPUMPHEAT) AS HEATPUMPHEAT, avg(HEATPUMPCOP) AS HEATPUMPCOP, avg(HEATPUMPTEMP) AS HEATPUMPTEMP, avg(HEATPUMPWIND) AS HEATPUMPWIND, INTERVAL FROM (" +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "sum(pvToCharge) AS PV2BAT, sum(pvToLoad) AS PV2LOAD, sum(batToLoad) AS BAT2LOAD, " +
            "sum(gridToBattery) AS GRID2BAT, sum(directEVcharge) AS EVSCHEDULE, sum(kWHDivToEV) AS EVDIVERT, " +
            "sum(immersionLoad) AS HWSCHEDULE, sum(kWHDivToWater) AS HWDIVERT, sum(battery2Grid) AS BAT2GRID, " +
            "sum(pvToCharge + gridToBattery) AS BAT_CHARGE, sum(batToLoad + battery2Grid) AS BAT_DISCHARGE, " +
            "0 AS PV2GRID, 0 AS GRID2LOAD, 0 AS EV_ACTUAL, 0 AS BAT_CHARGE_IN, 0 AS BAT_DISCHARGE_OUT, 0 AS HW_ACTUAL, 0 AS HP_ACTUAL, sum(heatPumpLoad) AS HEATPUMP, sum(heatPumpBackupLoad) AS HEATPUMPBACKUP, sum(heatPumpHeat) AS HEATPUMPHEAT, avg(heatPumpCop) AS HEATPUMPCOP, avg(heatPumpOutdoorTemp) AS HEATPUMPTEMP, avg(heatPumpWindSpeed) AS HEATPUMPWIND, " +
            "cast (strftime('%Y', date) as INTEGER) AS INTERVAL" +
            " FROM scenariosimulationdata WHERE date >= :from AND date <= :to AND scenarioID = :sysSN GROUP BY INTERVAL ORDER BY INTERVAL )")
    public abstract List<IntervalRow> avgYear(String sysSN, String from, String to);

    @Query("SELECT cast(scenarioID AS TEXT) AS sysSn, MIN(date) AS start, MAX(date) AS finish FROM scenariosimulationdata GROUP by scenarioID")
    public abstract LiveData<List<InverterDateRange>> loadDateRanges();

    @Query("SELECT DISTINCT 'SIMULATION' AS CATEGORY, scenarioName AS sysSN, scenarioID FROM scenarios, scenariosimulationdata where scenarioID = scenarioIndex " +
            "UNION " +
            "SELECT DISTINCT 'ESBNHDF' AS CATEGORY, sysSn, '0' FROM alphaESSTransformedData")
    public abstract List<ComparisonSenarioRow> getCompareScenarios();

    @Query("SELECT cast(scenarioID AS TEXT) AS sysSn, MIN(date) AS start, MAX(date) AS finish FROM scenariosimulationdata WHERE scenarioID = :sysSN")
    public abstract InverterDateRange getSimDateRanges(String sysSN);
}
