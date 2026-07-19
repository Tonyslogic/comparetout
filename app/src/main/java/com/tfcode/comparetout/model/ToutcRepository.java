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

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.tfcode.comparetout.model.costings.Costings;
import com.tfcode.comparetout.model.importers.CostInputRow;
import com.tfcode.comparetout.model.importers.IntervalRow;
import com.tfcode.comparetout.model.importers.InverterDateRange;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawEnergy;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawPower;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformMeta;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.model.importers.alphaess.KPIRow;
import com.tfcode.comparetout.model.importers.alphaess.KeyStatsRow;
import com.tfcode.comparetout.model.importers.alphaess.MaxCalcRow;
import com.tfcode.comparetout.model.importers.alphaess.ScheduleRIInput;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.HeatPump;
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
import com.tfcode.comparetout.model.scenario.Scenario2Battery;
import com.tfcode.comparetout.model.scenario.Scenario2DischargeToGrid;
import com.tfcode.comparetout.model.scenario.Scenario2EVCharge;
import com.tfcode.comparetout.model.scenario.Scenario2EVDivert;
import com.tfcode.comparetout.model.scenario.Scenario2HWSchedule;
import com.tfcode.comparetout.model.scenario.Scenario2HWSystem;
import com.tfcode.comparetout.model.scenario.Scenario2Inverter;
import com.tfcode.comparetout.model.scenario.Scenario2LoadShift;
import com.tfcode.comparetout.model.scenario.Scenario2Panel;
import com.tfcode.comparetout.model.scenario.ScenarioBarChartData;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;
import com.tfcode.comparetout.model.scenario.ScenarioLineGraphData;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;
import com.tfcode.comparetout.model.scenario.SimKPIs;
import com.tfcode.comparetout.model.scenario.MICBreachRow;
import com.tfcode.comparetout.model.scenario.SimulationInputData;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Central repository class for managing all database operations and data access.
 * <p>
 * This class serves as the single point of access for all data operations within
 * the application, implementing the Repository pattern to abstract database access
 * from the UI layer. It coordinates between multiple DAO (Data Access Object) classes
 * to provide a unified interface for managing energy system data, price plans,
 * scenarios, and cost calculations.
 * <p>
 * Key responsibilities:
 * - Price plan management and time-of-use rate schedules
 * - Energy scenario definition and component relationships
 * - Energy system data import and transformation (AlphaESS, ESBN, Home Assistant)
 * - Cost calculation and analysis results
 * - LiveData observers for reactive UI updates
 * <p>
 * The repository manages relationships between:
 * - Scenarios and their associated components (inverters, panels, batteries)
 * - Price plans and their time-based rate structures
 * - Raw energy data and transformed analysis results
 * - Load profiles and consumption patterns
 * <p>
 * This class follows the Android Architecture Components pattern and provides
 * LiveData objects for observing database changes in the UI layer.
 * 
 * @see ToutcDB for database configuration
 * @see PricePlanDAO for price plan operations
 * @see ScenarioDAO for scenario operations
 * @see AlphaEssDAO for energy data operations
 * @see CostingDAO for cost calculation operations
 */
public class ToutcRepository {
    private final PricePlanDAO pricePlanDAO;
    private final LiveData<Map<PricePlan, List<DayRate>>> allPricePlans;

    private final ScenarioDAO scenarioDAO;
    private final com.tfcode.comparetout.model.dao.InverterDAO inverterDAO;
    private final com.tfcode.comparetout.model.ops.InverterOps inverterOps;
    private final com.tfcode.comparetout.model.dao.BatteryDAO batteryDAO;
    private final com.tfcode.comparetout.model.ops.BatteryOps batteryOps;
    private final com.tfcode.comparetout.model.dao.HotWaterDAO hotWaterDAO;
    private final com.tfcode.comparetout.model.ops.HotWaterOps hotWaterOps;
    private final com.tfcode.comparetout.model.dao.EvDAO evDAO;
    private final com.tfcode.comparetout.model.ops.EvOps evOps;
    private final com.tfcode.comparetout.model.dao.HeatPumpDAO heatPumpDAO;
    private final com.tfcode.comparetout.model.ops.HeatPumpOps heatPumpOps;
    private final com.tfcode.comparetout.model.dao.PanelDAO panelDAO;
    private final com.tfcode.comparetout.model.ops.PanelOps panelOps;
    private final LiveData<List<Scenario>> allScenarios;
    private final LiveData<List<Scenario2Inverter>> inverterRelations;
    private final LiveData<List<Scenario2Panel>> panelRelations;
    private final LiveData<List<Scenario2Battery>> batteryRelations;
    private final LiveData<List<Scenario2LoadShift>> loadShiftRelations;
    private final LiveData<List<Scenario2DischargeToGrid>> dischargeRelations;
    private final LiveData<List<Scenario2HWSystem>> hwSystemRelations;
    private final LiveData<List<Scenario2HWSchedule>> hwScheduleRelations;
    private final LiveData<List<Scenario2EVCharge>> evChargeRelations;
    private final LiveData<List<Scenario2EVDivert>> evDivertRelations;
    private final LiveData<List<PanelPVSummary>> panelPVSummary;

    private final CostingDAO costingDAO;
    private final LiveData<List<Costings>> allCostings;

    private final AlphaEssDAO alphaEssDAO;
    private final LiveData<List<InverterDateRange>> alphaESSDateRangesBySN;
    private final LiveData<List<InverterDateRange>> esbnHDFDateRangesByMPRN;
    private final LiveData<List<InverterDateRange>> homeAssistantDateRange;
    private final LiveData<List<InverterDateRange>> scenarioDateRange;

    // Note that in order to unit test the WordRepository, you have to remove the Application
    // dependency. This adds complexity and much more code, and this sample is not about testing.
    // See the BasicSample in the android-architecture-components repository at
    // https://github.com/googlesamples
    
    /**
     * Constructor for ToutcRepository.
     * <p>
     * Initializes all DAO instances and sets up LiveData observers for each data type.
     * The repository follows the singleton pattern through the database instance to
     * ensure consistent data access across the application.
     * <p>
     * The constructor establishes connections to all major data categories:
     * - Price plans and rate schedules
     * - Energy scenarios and component configurations
     * - Cost calculations and analysis results
     * - Energy data from various sources (AlphaESS, ESBN, Home Assistant)
     * 
     * @param application Application context for database initialization
     */
    public ToutcRepository(Application application) {
        this(ToutcDB.getDatabase(application));
    }

    /**
     * Test seam: build against a caller-supplied DB (e.g. Room in-memory) so
     * characterization tests avoid the file-backed singleton. Package-private
     * on purpose — production code must keep using the Application constructor.
     */
    ToutcRepository(ToutcDB db) {
        pricePlanDAO = db.pricePlanDAO();
        allPricePlans = pricePlanDAO.loadPricePlans();

        scenarioDAO = db.scenarioDAO();
        inverterDAO = db.inverterDAO();
        inverterOps = new com.tfcode.comparetout.model.ops.InverterOps(db);
        batteryDAO = db.batteryDAO();
        batteryOps = new com.tfcode.comparetout.model.ops.BatteryOps(db);
        hotWaterDAO = db.hotWaterDAO();
        hotWaterOps = new com.tfcode.comparetout.model.ops.HotWaterOps(db);
        evDAO = db.evDAO();
        evOps = new com.tfcode.comparetout.model.ops.EvOps(db);
        heatPumpDAO = db.heatPumpDAO();
        heatPumpOps = new com.tfcode.comparetout.model.ops.HeatPumpOps(db);
        panelDAO = db.panelDAO();
        panelOps = new com.tfcode.comparetout.model.ops.PanelOps(db);
        allScenarios = scenarioDAO.loadScenarios();
        inverterRelations = inverterDAO.loadInverterRelations();
        panelRelations = panelDAO.loadPanelRelations();
        batteryRelations = batteryDAO.loadBatteryRelations();
        loadShiftRelations = batteryDAO.loadLoadShiftRelations();
        dischargeRelations = batteryDAO.loadDischargeRelations();
        hwSystemRelations = hotWaterDAO.loadHWSystemRelations();
        hwScheduleRelations = hotWaterDAO.loadHWScheduleRelations();
        evChargeRelations = evDAO.loadEVChargeRelations();
        evDivertRelations = evDAO.loadEVDivertRelations();
        panelPVSummary = panelDAO.getPanelPVSummary();

        costingDAO = db.costingDAO();
        allCostings = costingDAO.loadCostings();

        alphaEssDAO = db.alphaEssDAO();
        alphaESSDateRangesBySN = alphaEssDAO.loadDateRanges();
        esbnHDFDateRangesByMPRN = alphaEssDAO.loadESBNHDFDateRanges();
        homeAssistantDateRange = alphaEssDAO.loadHomeAssistantDateRange();
        scenarioDateRange = scenarioDAO.loadDateRanges();
    }

    // Room executes all queries on a separate thread.
    // Observed LiveData will notify the observer when the data has changed.
    public LiveData<Map<PricePlan, List<DayRate>>> getAllPricePlans() {
        return allPricePlans;
    }

    // You must call this on a non-UI thread or your app will throw an exception. Room ensures
    // that you're not doing any long running operations on the main thread, blocking the UI.
    public void insert(PricePlan pp, List<DayRate> drs, boolean clobber) {
        ToutcDB.databaseWriteExecutor.execute(() -> {
            long id = pricePlanDAO.addNewPricePlanWithDayRates(pp, drs, clobber);
            if (clobber) costingDAO.deleteRelatedCostings((int) id);
            // A new/replaced plan means every scenario now has a missing costing for it.
            scenarioDAO.markAllScenariosNeedCosting(System.currentTimeMillis());
        });
    }

    public void deletePricePlan(Integer id) {
        ToutcDB.databaseWriteExecutor.execute(() -> {
            pricePlanDAO.deletePricePlan(id);
//            System.out.println("Size after delete = " + allPricePlans.getValue().entrySet().size());
        });
    }

    public void updatePricePlanActiveStatus(int id, boolean checked) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                pricePlanDAO.updatePricePlanActiveStatus(id, checked));
    }

    public void updatePricePlan(PricePlan pp, ArrayList<DayRate> drs) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                pricePlanDAO.updatePricePlanWithDayRates(pp, drs));
    }

    public void insertScenario(ScenarioComponents sc, boolean clobber) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.addNewScenarioWithComponents(sc.scenario, sc, clobber));
    }

    public long insertScenarioAndReturnID(ScenarioComponents sc, boolean clobber) {
        return scenarioDAO.addNewScenarioWithComponents(sc.scenario, sc, clobber);
    }


    public LiveData<List<Scenario>> getAllScenarios() {
        return allScenarios;
    }

    public void updateScenario(Scenario scenario) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.updateScenario(scenario));
    }

    public LiveData<LoadProfile> getLoadProfile(Long scenarioID) {
        return scenarioDAO.getLoadProfile(scenarioID);
    }

    public void saveLoadProfile(Long scenarioID, LoadProfile loadProfile) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.saveLoadProfile(scenarioID, loadProfile));
    }

    public long saveLoadProfileAndReturnID(Long scenarioID, LoadProfile loadProfile) {
        return scenarioDAO.saveLoadProfile(scenarioID, loadProfile);
    }

    // Methods for Worker (LoadProfileData)
    public boolean loadProfileDataCheck(long id) {
        return (scenarioDAO.loadProfileDataCheck(id) != id);
    }

    public void deleteLoadProfileData(long id) {
        scenarioDAO.deleteLoadProfileData(id);
    }

    public LoadProfile getLoadProfileWithLoadProfileID(long mLoadProfileID) {
        return scenarioDAO.getLoadProfileWithLoadProfileID(mLoadProfileID);
    }

    public void createLoadProfileDataEntries(ArrayList<LoadProfileData> rows) {
        scenarioDAO.createLoadProfileDataEntries(rows);
    }

    public void deleteSimulationDataForProfileID(long loadProfileID) {
        scenarioDAO.deleteSimulationDataForProfileID(loadProfileID);
        scenarioDAO.markProfileScenariosNeedSim(loadProfileID, System.currentTimeMillis());
    }

    public void deleteCostingDataForProfileID(long loadProfileID) {
        scenarioDAO.deleteCostingDataForProfileID(loadProfileID);
        scenarioDAO.markProfileScenariosNeedCosting(loadProfileID, System.currentTimeMillis());
    }

    public void deleteSimulationDataForPanelID(long panelID) {
        scenarioDAO.deleteSimulationDataForPanelID(panelID);
        scenarioDAO.markPanelScenarioNeedsSim(panelID, System.currentTimeMillis());
    }

    public void deleteCostingDataForPanelID(long panelID) {
        scenarioDAO.deleteCostingDataForPanelID(panelID);
        scenarioDAO.markPanelScenarioNeedsCosting(panelID, System.currentTimeMillis());
    }

    public List<Long> getAllScenariosThatNeedSimulation() {
        return scenarioDAO.getAllScenariosThatNeedSimulation();
    }

    // ── readiness matrix (scenario_readiness) — fast gates + worker terminal-state setters ──

    public List<Long> getScenarioIdsNeedingSimulation() {
        return scenarioDAO.getScenarioIdsNeedingSimulation();
    }

    public List<Long> getScenarioIdsNeedingCosting() {
        return scenarioDAO.getScenarioIdsNeedingCosting();
    }

    /** Simulation succeeded for a scenario → up-to-date; costing now stale. */
    public void markSimulated(long scenarioID) {
        scenarioDAO.markSimulated(scenarioID);
    }

    /** Simulation can't run yet (record the blocked reason so the gate skips it). */
    public void markSimBlocked(long scenarioID, int blockedStatus) {
        scenarioDAO.markSimBlocked(scenarioID, blockedStatus);
    }

    /** All (scenario × plan) costings present → costing up-to-date. */
    public void markCosted(long scenarioID) {
        scenarioDAO.markCosted(scenarioID);
    }

    /** Self-heal: panel data for this panel landed → unblock its scenario if it was blocked on panel data. */
    public void unblockPanelScenarios(long panelID) {
        scenarioDAO.unblockPanelScenarios(panelID, System.currentTimeMillis());
    }

    /** Self-heal: CDS weather for this scenario landed → unblock it if it was blocked on weather. */
    public void unblockWeatherScenario(long scenarioID) {
        scenarioDAO.unblockWeatherScenario(scenarioID, System.currentTimeMillis());
    }

    public Scenario getScenarioForID(long scenarioID) {
        return scenarioDAO.getScenarioForID(scenarioID);
    }

    public ScenarioComponents getScenarioComponentsForScenarioID(long scenarioID) {
        return scenarioDAO.getScenarioComponentsForScenarioID(scenarioID);
    }

    public void saveSimulationDataForScenario(ArrayList<ScenarioSimulationData> simulationData) {
        scenarioDAO.saveSimulationDataForScenario(simulationData);
    }

    public List<Long> getAllScenariosThatMayNeedCosting() {
        return scenarioDAO.getAllScenariosThatMayNeedCosting();
    }

    public List<PricePlan> getAllPricePlansNow() {
        return pricePlanDAO.loadPricePlansNow();
    }

    public List<ScenarioSimulationData> getSimulationDataForScenario(long scenarioID) {
        return scenarioDAO.getSimulationDataForScenario(scenarioID);
    }

    public void saveCosting(Costings costing) {
        costingDAO.saveCosting(costing);
    }

    public LiveData<List<Costings>> getAllCostings() {
        return allCostings;
    }

    public List<DayRate> getAllDayRatesForPricePlanID(long id) {
        return pricePlanDAO.getAllDayRatesForPricePlanID(id);
    }

    public void updateScenarioActiveStatus(int id, boolean checked) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.updateScenarioActiveStatus(id, checked));
    }

    public List<Long> checkForMissingLoadProfileData() {
        return scenarioDAO.checkForMissingLoadProfileData();
    }

    public void deleteScenario(int id) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.deleteScenario(id));
    }

    public void deleteRelatedCostings(int id) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                costingDAO.deleteRelatedCostings(id));
    }

    public void copyScenario(int id) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.copyScenario(id));
    }

    public List<ScenarioComponents> getAllScenariosForExport() {
        return scenarioDAO.getAllScenariosForExport();
    }

    public Map<PricePlan, List<DayRate>> getAllPricePlansForExport() {
        return pricePlanDAO.getAllPricePlansForExport();
    }

    public List<Scenario> getScenarios() {
        return scenarioDAO.getScenarios();
    }

    public void copyLoadProfileFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
        scenarioDAO.copyLoadProfileFromScenario(fromScenarioID, toScenarioID));
    }

    public void linkLoadProfileFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.linkLoadProfileFromScenario(fromScenarioID, toScenarioID));
    }

    public SimKPIs getSimKPIsForScenario(Long scenarioID) {
        return scenarioDAO.getSimKPIsForScenario(scenarioID);
    }

    public Costings getBestCostingForScenario(Long scenarioID) {
        return costingDAO.getBestCostingForScenario(scenarioID);
    }

    public List<Costings> getAllCostingsForScenario(Long scenarioID) {
        return costingDAO.getAllCostingsForScenario(scenarioID);
    }

    public List<Inverter> getInvertersForScenario(Long scenarioID) {
        return scenarioDAO.getInvertersForScenarioID(scenarioID);
    }

    public long saveInverter(Long scenarioID, Inverter inverter) {
        return inverterOps.saveInverter(scenarioID, inverter);
    }

    public void deleteInverterFromScenario(Long inverterID, Long scenarioID) {
        inverterOps.deleteInverterFromScenario(inverterID, scenarioID);
    }

    public void copyInverterFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                inverterOps.copyInverterFromScenario(fromScenarioID, toScenarioID));
    }

    public LiveData<List<Scenario2Inverter>> getAllInverterRelations() {
        return inverterRelations;
    }

    public void linkInverterFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.linkInverterFromScenario(fromScenarioID, toScenarioID));
    }

    public LiveData<List<Scenario2Panel>> getAllPanelRelations() {
        return panelRelations;
    }

    public List<Panel> getPanelsForScenario(Long scenarioID) {
        return scenarioDAO.getPanelsForScenarioID(scenarioID);
    }

    public void deletePanelFromScenario(Long panelID, Long scenarioID) {
        panelOps.deletePanelFromScenario(panelID, scenarioID);
    }

    public long savePanel(Long scenarioID, Panel panel) {
        return panelOps.savePanel(scenarioID, panel);
    }

    /** Clone the generated time-series of one panel onto another (used by the wizard's COPY path so a copied
     *  scenario keeps its PV data instead of re-fetching or losing it). Synchronous, mirroring savePanel. */
    public void copyPanelData(long fromPanelID, long toPanelID) {
        scenarioDAO.copyPanelData(fromPanelID, toPanelID);
    }

    public void copyPanelFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                panelOps.copyPanelFromScenario(fromScenarioID, toScenarioID));
    }

    /** Idempotently link one panel to a scenario (Directors per-string share — no duplicate junction rows). */
    public void linkPanelToScenario(long panelID, long scenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                panelOps.linkPanelToScenario(panelID, scenarioID));
    }

    /** Copy one panel (+ its data) onto a scenario (Directors per-string FORK). */
    public void copyPanelToScenario(long panelID, long scenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                panelOps.copyPanelToScenario(panelID, scenarioID));
    }

    public void linkPanelFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.linkPanelFromScenario(fromScenarioID, toScenarioID));
    }

    public Panel getPanelForID(Long panelID) {
        return scenarioDAO.getPanelForID(panelID);
    }

    public void savePanelData(ArrayList<PanelData> panelDataList) {
        panelDAO.savePanelData(panelDataList);
        // Panel data landed → unblock any scenario that was waiting on this panel's data (self-heal).
        // Centralised here so every panel-data writer (PVGIS direct/legacy, source PV, importer) benefits
        // without knowing about readiness. A no-op for scenarios that weren't blocked on panel data.
        java.util.Set<Long> panelIds = new java.util.HashSet<>();
        for (PanelData row : panelDataList) panelIds.add(row.getPanelID());
        long now = System.currentTimeMillis();
        for (Long panelID : panelIds) scenarioDAO.unblockPanelScenarios(panelID, now);
    }

    public LiveData<List<PanelPVSummary>> getPanelDataSummary() {
        return panelPVSummary;
    }

    public boolean hasPvgisDataForParameters(double lat, double lon, int azimuth, int slope) {
        return panelDAO.countPanelDataForParameters(lat, lon, azimuth, slope) > 0;
    }

    /** Scenario names whose panels sit at this PVGIS location/orientation (for the PVGIS cache view). */
    public List<String> getScenarioNamesAtLocation(double lat, double lon, int azimuth, int slope) {
        return panelDAO.getScenarioNamesAtLocation(lat, lon, azimuth, slope);
    }

    public void updatePanel(Panel panel) {
        panelDAO.updatePanel(panel);
    }

    public boolean checkForMissingPanelData(Long scenarioID) {
        return panelDAO.checkForMissingPanelData(scenarioID);
    }

    public void removeCostingsForPricePlan(long pricePlanIndex) {
        ToutcDB.databaseWriteExecutor.execute(() -> {
            costingDAO.deleteRelatedCostings((int) pricePlanIndex);
            // An edited plan invalidates its costing across every scenario.
            scenarioDAO.markAllScenariosNeedCosting(System.currentTimeMillis());
        });
    }

    public void pruneCostings() {
        ToutcDB.databaseWriteExecutor.execute(costingDAO::pruneCostings);
    }

    public boolean costingExists(long scenarioID, long pricePlanIndex) {
        return costingDAO.costingExists(scenarioID, pricePlanIndex);
    }

    public void removeOldPanelData(Long panelID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
            panelDAO.removePanelData(panelID));
    }

    public List<SimulationInputData> getSimulationInputNoSolar(long scenarioID) {
        return scenarioDAO.getSimulationInputNoSolar(scenarioID);
    }

    public List<SimulationInputData> getPVRowsForPanel(long panelID) {
        return scenarioDAO.getPVRowsForPanel(panelID);
    }

    public int countGridImportBreaches(long scenarioID, double capPerInterval) {
        return scenarioDAO.countGridImportBreaches(scenarioID, capPerInterval);
    }

    public List<MICBreachRow> getTopGridImportBreaches(long scenarioID, double capPerInterval, int limit) {
        return scenarioDAO.getTopGridImportBreaches(scenarioID, capPerInterval, limit);
    }


    public List<String> getLinkedLoadProfiles(Long scenarioID) {
        return  scenarioDAO.getLinkedLoadProfiles(scenarioID);
    }

    public List<String> getLinkedInverters(Long inverterID, Long scenarioID) {
        return  inverterDAO.getLinkedInverters(inverterID, scenarioID);
    }

    public List<String> getLinkedPanels(long panelIndex, Long scenarioID) {
        return panelDAO.getLinkedPanels(panelIndex, scenarioID);
    }

    public LiveData<List<Scenario2Battery>> getAllBatteryRelations() {
        return batteryRelations;
    }

    public List<Battery> getBatteriesForScenarioID(Long scenarioID) {
        return scenarioDAO.getBatteriesForScenarioID(scenarioID);
    }

    public void deleteBatteryFromScenario(Long batteryID, Long scenarioID) {
        batteryDAO.deleteBatteryFromScenario(batteryID, scenarioID);
    }

    /** Remove component rows no longer referenced by any scenario — left behind whenever an edit-save
     *  deletes-and-reinserts a scenario's components (battery, inverter, schedules, etc.). Reuses the same
     *  per-type DAO cleanup queries that {@code deleteScenario} already runs, so orphans don't accumulate. */
    public void deleteOrphanComponents() {
        scenarioDAO.deleteOrphanBatteries();
        scenarioDAO.deleteOrphanInverters();
        scenarioDAO.deleteOrphanLoadShifts();
        scenarioDAO.deleteOrphanDischarges();
        scenarioDAO.deleteOrphanEVCharges();
        scenarioDAO.deleteOrphanEVDiverts();
        scenarioDAO.deleteOrphanHWSchedules();
        scenarioDAO.deleteOrphanHWSystems();
        scenarioDAO.deleteOrphanHWDiverts();
        scenarioDAO.deleteOrphanLoadProfiles();
        scenarioDAO.deleteOrphanLoadProfileData();
        scenarioDAO.deleteOrphanPanels();
        scenarioDAO.deleteOrphanPanelData();
        scenarioDAO.deleteOrphanHeatPumps();
    }

    public void saveBatteryForScenario(Long scenarioID, Battery battery) {
        batteryOps.saveBatteryForScenario(scenarioID, battery);
    }

    public List<String> getLinkedBatteries(long batteryIndex, Long scenarioID) {
        return batteryDAO.getLinkedBatteries(batteryIndex, scenarioID);
    }

    public void copyBatteryFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
        batteryOps.copyBatteryFromScenario(fromScenarioID, toScenarioID));
    }

    public void linkBatteryFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
        scenarioDAO.linkBatteryFromScenario(fromScenarioID, toScenarioID));
    }

    public void deleteHeatPumpFromScenario(Long heatPumpID, Long scenarioID) {
        heatPumpDAO.deleteHeatPumpFromScenario(heatPumpID, scenarioID);
    }

    public void saveHeatPumpForScenario(Long scenarioID, HeatPump heatPump) {
        heatPumpOps.saveHeatPumpForScenario(scenarioID, heatPump);
    }

    public List<String> getLinkedHeatPumps(long heatPumpIndex, Long scenarioID) {
        return heatPumpDAO.getLinkedHeatPumps(heatPumpIndex, scenarioID);
    }

    public void copyHeatPumpFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
        heatPumpOps.copyHeatPumpFromScenario(fromScenarioID, toScenarioID));
    }

    public void linkHeatPumpFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
        scenarioDAO.linkHeatPumpFromScenario(fromScenarioID, toScenarioID));
    }

    public LiveData<List<Scenario2LoadShift>> getAllLoadShiftRelations() {
        return loadShiftRelations;
    }

    public List<LoadShift> getLoadShiftsForScenarioID(Long scenarioID) {
        return scenarioDAO.getLoadShiftsForScenarioID(scenarioID);
    }

    public void deleteLoadShiftFromScenario(Long loadShiftID, Long scenarioID) {
        batteryDAO.deleteLoadShiftFromScenario(loadShiftID, scenarioID);
    }

    public void saveLoadShiftForScenario(Long scenarioID, LoadShift loadShift) {
        batteryOps.saveLoadShiftForScenario(scenarioID, loadShift);
    }

    public List<String> getLinkedLoadShifts(long loadShiftIndex, Long scenarioID) {
        return batteryDAO.getLinkedLoadShifts(loadShiftIndex, scenarioID);
    }

    public void copyLoadShiftFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                batteryOps.copyLoadShiftFromScenario(fromScenarioID, toScenarioID));
    }

    public void linkLoadShiftFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.linkLoadShiftFromScenario(fromScenarioID, toScenarioID));
    }

    public void deleteSimulationDataForScenarioID(Long scenarioID) {
        scenarioDAO.deleteSimulationDataForScenarioID(scenarioID);
        // Sim output gone → scenario needs re-sim (and therefore re-costing).
        scenarioDAO.markScenarioNeedsSim(scenarioID, System.currentTimeMillis());
    }

    public void deleteCostingDataForScenarioID(Long scenarioID) {
        scenarioDAO.deleteCostingDataForScenarioID(scenarioID);
        scenarioDAO.markScenarioNeedsCosting(scenarioID, System.currentTimeMillis());
    }

    public List<ScenarioBarChartData> getBarData(Long scenarioID, int dayOfYear) {
        return scenarioDAO.getBarData(scenarioID, dayOfYear);
    }

    public List<ScenarioLineGraphData> getLineData(Long scenarioID, int dayOfYear) {
        return scenarioDAO.getLineData(scenarioID, dayOfYear);
    }

    public List<ScenarioBarChartData> getMonthlyBarData(Long scenarioID, int dayOfYear) {
        return scenarioDAO.getMonthlyBarData(scenarioID, dayOfYear);
    }

    public List<ScenarioBarChartData> getYearBarData(Long scenarioID) {
        return scenarioDAO.getYearBarData(scenarioID);
    }

    public List<String> getLinkedHWSystems(long hwSystemIndex, Long scenarioID) {
        return hotWaterDAO.getLinkedHWSystems(hwSystemIndex, scenarioID);
    }

    public HWSystem getHWSystemForScenarioID(Long scenarioID) {
        return scenarioDAO.getHWSystemForScenarioID(scenarioID);
    }

    public void saveHWSystemForScenario(Long scenarioID, HWSystem hwSystem) {
        hotWaterOps.saveHWSystemForScenario(scenarioID, hwSystem);
    }

    public void linkHWSystemFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.linkHWSystemFromScenario(fromScenarioID, toScenarioID));
    }

    /**
     * Link every component of {@code fromScenarioID} onto {@code toScenarioID} as one atomic transaction —
     * used by the wizard's "Create scenario → Link from existing" path instead of firing each link separately
     * (which raced on the 8-thread write executor and crashed on a missing optional component).
     */
    public void linkAllComponentsFromScenario(long fromScenarioID, long toScenarioID,
                                              com.tfcode.comparetout.model.scenario.HWDivert hwDivert) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.linkAllComponentsFromScenario(fromScenarioID, toScenarioID, hwDivert));
    }

    public void copyHWSettingsFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                hotWaterOps.copyHWSettingsFromScenario(fromScenarioID, toScenarioID));
    }

    public LiveData<List<Scenario2HWSystem>> getAllHWSystemRelations() {
        return hwSystemRelations;
    }

    public void saveHWDivert(Long scenarioID, HWDivert hwDivert) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.saveHWDivert(scenarioID, hwDivert));
    }

    public List<String> getLinkedHWSchedules(long hwScheduleIndex, Long scenarioID) {
        return hotWaterDAO.getLinkedHWSchedules(hwScheduleIndex, scenarioID);
    }

    public LiveData<List<Scenario2HWSchedule>> getAllHWScheduleRelations() {
        return  hwScheduleRelations;
    }

    public List<HWSchedule> getHWSchedulesForScenario(Long scenarioID) {
        return scenarioDAO.getHWSchedulesForScenarioID(scenarioID);
    }

    public void deleteHWScheduleFromScenario(Long hwScheduleID, Long scenarioID) {
        hotWaterOps.deleteHWScheduleFromScenario(hwScheduleID, scenarioID);
    }

    public void saveHWScheduleForScenario(Long scenarioID, HWSchedule hwSchedule) {
        hotWaterOps.saveHWScheduleForScenario(scenarioID, hwSchedule);
    }

    public void copyHWScheduleFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                hotWaterOps.copyHWScheduleFromScenario(fromScenarioID, toScenarioID));
    }

    public void linkHWScheduleFromScenario(long fromScenarioID, long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.linkHWScheduleFromScenario(fromScenarioID, toScenarioID));
    }

    public List<String> getLinkedEVCharges(long evChargeIndex, Long scenarioID) {
        return evDAO.getLinkedEVCharges(evChargeIndex, scenarioID);
    }

    public LiveData<List<Scenario2EVCharge>> getAllEVChargeRelations() {
        return evChargeRelations;
    }

    public List<EVCharge> getEVChargesForScenario(Long scenarioID) {
        return scenarioDAO.getEVChargesForScenarioID(scenarioID);
    }

    public void deleteEVChargeFromScenario(Long evChargeID, Long scenarioID) {
        evDAO.deleteEVChargeFromScenario(evChargeID, scenarioID);
    }

    public void saveEVChargeForScenario(Long scenarioID, EVCharge evCharge) {
        evOps.saveEVChargeForScenario(scenarioID, evCharge);
    }

    public void copyEVChargeFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                evOps.copyEVChargeFromScenario(fromScenarioID, toScenarioID));
    }

    public void linkEVChargeFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.linkEVChargeFromScenario(fromScenarioID, toScenarioID));
    }

    public List<String> getLinkedEVDiverts(long evDivertIndex, Long scenarioID) {
        return evDAO.getLinkedEVDiverts(evDivertIndex, scenarioID);
    }

    public LiveData<List<Scenario2EVDivert>> getAllEVDivertRelations() {
        return evDivertRelations;
    }

    public List<EVDivert> getEVDivertsForScenario(Long scenarioID) {
        return scenarioDAO.getEVDivertForScenarioID(scenarioID);
    }

    public void deleteEVDivertFromScenario(Long evDivertID, Long scenarioID) {
        evDAO.deleteEVDivertFromScenario(evDivertID, scenarioID);
    }

    public void saveEVDivertForScenario(Long scenarioID, EVDivert evDivert) {
        evOps.saveEVDivertForScenario(scenarioID, evDivert);
    }

    public void copyEVDivertFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                evOps.copyEVDivertFromScenario(fromScenarioID, toScenarioID));
    }

    public void linkEVDivertFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                evOps.linkEVDivertFromScenario(fromScenarioID, toScenarioID));
    }

    public double getGridExportMaxForScenario(long scenarioID) {
        return scenarioDAO.getGridExportMaxForScenario(scenarioID);
    }

    public List<String> getAllComparisonsNow() {
        return costingDAO.getAllComparisonsNow();
    }

    // Methods for AlphaESS
    public LiveData<List<InverterDateRange>> getLiveDateRanges() {
        return alphaESSDateRangesBySN;
    }

    public LiveData<List<InverterDateRange>> getESBNLiveDateRanges() {
        return esbnHDFDateRangesByMPRN;
    }

    public LiveData<List<InverterDateRange>> getHALiveDateRanges() {
        return homeAssistantDateRange;
    }

    public void addRawEnergy(AlphaESSRawEnergy energy) {
        alphaEssDAO.addRawEnergy(energy);
    }

    public void addTransformedData(List<AlphaESSTransformedData> data){
        alphaEssDAO.addTransformedData(data);
    }

    public void addRawPower(List<AlphaESSRawPower> powerEntityList) {
        alphaEssDAO.addRawPower(powerEntityList);
    }

    public void clearAlphaESSDataForSN(String systemSN) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                alphaEssDAO.clearAlphaESSDataForSN(systemSN));
    }

    public void deleteInverterDatesBySN(String sysSN, LocalDateTime selectedStart, LocalDateTime selectedEnd) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                alphaEssDAO.deleteInverterDatesBySN(sysSN, selectedStart, selectedEnd));
    }

    public boolean checkSysSnForDataOnDate(String sysSn, String date) {
        return alphaEssDAO.checkSysSnForDataOnDate(sysSn, date);
    }

    public List<IntervalRow> getSumHour(String systemSN, String from, String to) {
        return alphaEssDAO.sumHour(systemSN, from, to);
    }

    public List<IntervalRow> getSumDOY(String systemSN, String from, String to) {
        return alphaEssDAO.sumDOY(systemSN, from, to);
    }

    public List<IntervalRow> getSumByDate(String systemSN, String from, String to) {
        return alphaEssDAO.sumByDate(systemSN, from, to);
    }

    public List<IntervalRow> getSumDOW(String systemSN, String from, String to) {
        return alphaEssDAO.sumDOW(systemSN, from, to);
    }

    public List<IntervalRow> getSumMonth(String systemSN, String from, String to) {
        return alphaEssDAO.sumMonth(systemSN, from, to);
    }

    public List<IntervalRow> getSumYear(String systemSN, String from, String to) {
        return alphaEssDAO.sumYear(systemSN, from, to);
    }

    public List<IntervalRow> getAvgHour(String systemSN, String from, String to) {
        return alphaEssDAO.avgHour(systemSN, from, to);
    }

    public List<IntervalRow> getAvgDOY(String systemSN, String from, String to) {
        return alphaEssDAO.avgDOY(systemSN, from, to);
    }

    public List<IntervalRow> getAvgDOW(String systemSN, String from, String to) {
        return alphaEssDAO.avgDOW(systemSN, from, to);
    }

    public List<IntervalRow> getAvgMonth(String systemSN, String from, String to) {
        return alphaEssDAO.avgMonth(systemSN, from, to);
    }

    public List<IntervalRow> getAvgYear(String systemSN, String from, String to) {
        return alphaEssDAO.avgYear(systemSN, from, to);
    }

    public List<CostInputRow> getSelectedAlphaESSData(String serialNumber, String from, String to) {
        return alphaEssDAO.getSelectedAlphaESSData(serialNumber, from, to);
    }

    public List<AlphaESSRawPower> getAlphaESSPowerForSharing(String serialNumber, String from) {
        return alphaEssDAO.getAlphaESSPowerForSharing(serialNumber, from);
    }

    public List<AlphaESSRawEnergy> getAlphaESSEnergyForSharing(String serialNumber) {
        return alphaEssDAO.getAlphaESSEnergyForSharing(serialNumber);
    }
    public AlphaESSRawEnergy getAlphaESSEnergyForDate (String serialNumber, String date) {
        return alphaEssDAO.getAlphaESSEnergyForDate(serialNumber, date);
    }

    public List<String> getExportDatesForSN(String serialNumber) {
        return alphaEssDAO.getExportDatesForSN(serialNumber);
    }

    public List<KeyStatsRow> getKeyStats(String from, String to, String systemSN) {
        return alphaEssDAO.getKeyStats(from, to, systemSN);
    }

    public List<KeyStatsRow> getHAKeyStats(String from, String to, String systemSN) {
        return alphaEssDAO.getHAKeyStats(from, to, systemSN);
    }

    public KPIRow getKPIs(String from, String to, String systemSN) {
        return alphaEssDAO.getKPIs(from, to, systemSN);
    }

    public List<AlphaESSTransformedData> getAlphaESSTransformedData(String systemSN, String from, String to) {
        return alphaEssDAO.getAlphaESSTransformedData(systemSN, from, to);
    }

    public Double getBaseLoad(String systemSN, String from, String to) {
        return alphaEssDAO.getBaseLoad(systemSN, from, to);
    }

    public Double getLosses(String systemSN) {
        return alphaEssDAO.getLosses(systemSN);
    }

    public List<Double> getChargeModelInput(String systemSN, int low, int high) {
        return alphaEssDAO.getChargeModelInput(systemSN, low, high);
    }

    public List<Double> getDischargeStopInput(String systemSN) {
        return alphaEssDAO.getDischargeStopInput(systemSN);
    }

    public List<MaxCalcRow> getMaxCalcInput(String systemSN) {
        return alphaEssDAO.getMaxCalcInput(systemSN);
    }

    public List<ScheduleRIInput> getScheduleRIInput(String systemSN, String from , String to) {
        return alphaEssDAO.getScheduleRIInput(systemSN, from, to);
    }

    public String getLatestDateForSn(String systemSN) {
        return alphaEssDAO.getLatestDateForSn(systemSN);
    }

    public double getHAPopv(String systemSN) {
        return alphaEssDAO.getHAPopv(systemSN);
    }

    public double getHAPoinv(String systemSN) {
        return alphaEssDAO.getHAPoinv(systemSN);
    }

    public List<IntervalRow> getSimSumHour(String scenarioID, String from, String to) {
        return scenarioDAO.sumHour(scenarioID, from, to);
    }

    public List<IntervalRow> getSimSumDOY(String scenarioID, String from, String to) {
        return scenarioDAO.sumDOY(scenarioID, from, to);
    }

    public List<IntervalRow> getSimSumDOW(String scenarioID, String from, String to) {
        return scenarioDAO.sumDOW(scenarioID, from, to);
    }

    public List<IntervalRow> getSimSumMonth(String scenarioID, String from, String to) {
        return scenarioDAO.sumMonth(scenarioID, from, to);
    }

    public List<IntervalRow> getSimSumYear(String scenarioID, String from, String to) {
        return scenarioDAO.sumYear(scenarioID, from, to);
    }

    public List<IntervalRow> getSimAvgHour(String scenarioID, String from, String to) {
        return scenarioDAO.avgHour(scenarioID, from, to);
    }

    public List<IntervalRow> getSimAvgDOY(String scenarioID, String from, String to) {
        return scenarioDAO.avgDOY(scenarioID, from, to);
    }

    public List<IntervalRow> getSimAvgDOW(String scenarioID, String from, String to) {
        return scenarioDAO.avgDOW(scenarioID, from, to);
    }

    public List<IntervalRow> getSimAvgMonth(String scenarioID, String from, String to) {
        return scenarioDAO.avgMonth(scenarioID, from, to);
    }

    public List<IntervalRow> getSimAvgYear(String scenarioID, String from, String to) {
        return scenarioDAO.avgYear(scenarioID, from, to);
    }

    public LiveData<List<InverterDateRange>> getScenarioLiveDateRanges() {
        return scenarioDateRange;
    }

    public void linkDischargeFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
        scenarioDAO.linkDischargeFromScenario(fromScenarioID, toScenarioID));
    }

    public void copyDischargeFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
        batteryOps.copyDischargeFromScenario(fromScenarioID, toScenarioID));
    }

    public void saveDischargeForScenario(Long scenarioID, DischargeToGrid discharge) {
        batteryOps.saveDischargeForScenario(scenarioID, discharge);
    }

    public LiveData<List<Scenario2DischargeToGrid>> getAllDischargeRelations() {
        return dischargeRelations;
    }

    public List<DischargeToGrid> getDischargesForScenario(Long scenarioID) {
        return scenarioDAO.getDischargesForScenarioID(scenarioID);
    }

    public void deleteDischargeFromScenario(Long dischargeID, Long scenarioID) {
        batteryDAO.deleteDischargeFromScenario(dischargeID, scenarioID);
    }

    public List<String> getLinkedDischarges(long d2gIndex, Long scenarioID) {
        return batteryDAO.getLinkedDischarges(d2gIndex, scenarioID);
    }

    public List<ComparisonSenarioRow> getCompareScenarios() {
        return scenarioDAO.getCompareScenarios();
    }

    public InverterDateRange getDateRange(String sysSN) {
        return alphaEssDAO.getDateRange(sysSN);
    }

    public InverterDateRange getSimDateRanges(String sysSN) {
        return scenarioDAO.getSimDateRanges(sysSN);
    }

    // ---- AlphaESS transform meta (v2 enrichment tracking) ----

    public AlphaESSTransformMeta getAlphaESSTransformMeta(String sysSN) {
        return alphaEssDAO.getTransformMeta(sysSN);
    }

    public void upsertAlphaESSTransformMeta(AlphaESSTransformMeta meta) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                alphaEssDAO.upsertTransformMeta(meta));
    }

    /** UI binds against this to decide whether to surface the Migrate button per SN. */
    public LiveData<List<AlphaESSTransformMeta>> getAllAlphaESSTransformMetaLive() {
        return alphaEssDAO.observeAllTransformMeta();
    }

    /** Stamp the SN's transform meta as up-to-date for {@code TRANSFORM_VERSION_CURRENT}. */
    public void stampAlphaESSTransformCurrent(String sysSn) {
        AlphaESSTransformMeta meta = new AlphaESSTransformMeta();
        meta.setSysSn(sysSn);
        meta.setTransformVersion(AlphaESSTransformMeta.TRANSFORM_VERSION_CURRENT);
        meta.setLastMigratedAt(System.currentTimeMillis());
        upsertAlphaESSTransformMeta(meta);
    }

    /**
     * Stamp v2 only when it is safe: either the SN had no processed rows before
     * the caller's write (so everything it just wrote is v2), or the existing
     * meta already says v2. Otherwise leaves the meta alone so the UI keeps
     * surfacing the Migrate button for the un-migrated history.
     */
    public void stampAlphaESSTransformCurrentIfSafe(String sysSn, boolean snHadNoRowsBefore) {
        if (snHadNoRowsBefore) {
            stampAlphaESSTransformCurrent(sysSn);
            return;
        }
        AlphaESSTransformMeta existing = alphaEssDAO.getTransformMeta(sysSn);
        if (existing != null && existing.getTransformVersion() >= AlphaESSTransformMeta.TRANSFORM_VERSION_CURRENT) {
            stampAlphaESSTransformCurrent(sysSn);
        }
    }
}
