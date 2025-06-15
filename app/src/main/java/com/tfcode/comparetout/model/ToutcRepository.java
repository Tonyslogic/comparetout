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
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.model.importers.alphaess.KPIRow;
import com.tfcode.comparetout.model.importers.alphaess.KeyStatsRow;
import com.tfcode.comparetout.model.importers.alphaess.MaxCalcRow;
import com.tfcode.comparetout.model.importers.alphaess.ScheduleRIInput;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.PricePlan;
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
import com.tfcode.comparetout.model.scenario.SimulationInputData;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Central repository class for managing all database operations and data access.
 * 
 * This class serves as the single point of access for all data operations within
 * the application, implementing the Repository pattern to abstract database access
 * from the UI layer. It coordinates between multiple DAO (Data Access Object) classes
 * to provide a unified interface for managing energy system data, price plans,
 * scenarios, and cost calculations.
 * 
 * Key responsibilities:
 * - Price plan management and time-of-use rate schedules
 * - Energy scenario definition and component relationships
 * - Energy system data import and transformation (AlphaESS, ESBN, Home Assistant)
 * - Cost calculation and analysis results
 * - LiveData observers for reactive UI updates
 * 
 * The repository manages relationships between:
 * - Scenarios and their associated components (inverters, panels, batteries)
 * - Price plans and their time-based rate structures
 * - Raw energy data and transformed analysis results
 * - Load profiles and consumption patterns
 * 
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
     * 
     * Initializes all DAO instances and sets up LiveData observers for each data type.
     * The repository follows the singleton pattern through the database instance to
     * ensure consistent data access across the application.
     * 
     * The constructor establishes connections to all major data categories:
     * - Price plans and rate schedules
     * - Energy scenarios and component configurations
     * - Cost calculations and analysis results
     * - Energy data from various sources (AlphaESS, ESBN, Home Assistant)
     * 
     * @param application Application context for database initialization
     */
    public ToutcRepository(Application application) {
        ToutcDB db = ToutcDB.getDatabase(application);
        pricePlanDAO = db.pricePlanDAO();
        allPricePlans = pricePlanDAO.loadPricePlans();

        scenarioDAO = db.scenarioDAO();
        allScenarios = scenarioDAO.loadScenarios();
        inverterRelations = scenarioDAO.loadInverterRelations();
        panelRelations = scenarioDAO.loadPanelRelations();
        batteryRelations = scenarioDAO.loadBatteryRelations();
        loadShiftRelations = scenarioDAO.loadLoadShiftRelations();
        dischargeRelations = scenarioDAO.loadDischargeRelations();
        hwSystemRelations = scenarioDAO.loadHWSystemRelations();
        hwScheduleRelations = scenarioDAO.loadHWScheduleRelations();
        evChargeRelations = scenarioDAO.loadEVChargeRelations();
        evDivertRelations = scenarioDAO.loadEVDivertRelations();
        panelPVSummary = scenarioDAO.getPanelPVSummary();

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
    }

    public void deleteCostingDataForProfileID(long loadProfileID) {
        scenarioDAO.deleteCostingDataForProfileID(loadProfileID);
    }

    public void deleteSimulationDataForPanelID(long panelID) {
        scenarioDAO.deleteSimulationDataForPanelID(panelID);
    }

    public void deleteCostingDataForPanelID(long panelID) {
        scenarioDAO.deleteCostingDataForPanelID(panelID);
    }

    public List<Long> getAllScenariosThatNeedSimulation() {
        return scenarioDAO.getAllScenariosThatNeedSimulation();
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

    public List<Inverter> getInvertersForScenario(Long scenarioID) {
        return scenarioDAO.getInvertersForScenarioID(scenarioID);
    }

    public long saveInverter(Long scenarioID, Inverter inverter) {
        return scenarioDAO.saveInverter(scenarioID, inverter);
    }

    public void deleteInverterFromScenario(Long inverterID, Long scenarioID) {
        scenarioDAO.deleteInverterFromScenario(inverterID, scenarioID);
    }

    public void copyInverterFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.copyInverterFromScenario(fromScenarioID, toScenarioID));
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
        scenarioDAO.deletePanelFromScenario(panelID, scenarioID);
    }

    public long savePanel(Long scenarioID, Panel panel) {
        return scenarioDAO.savePanel(scenarioID, panel);
    }

    public void copyPanelFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.copyPanelFromScenario(fromScenarioID, toScenarioID));
    }

    public void linkPanelFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.linkPanelFromScenario(fromScenarioID, toScenarioID));
    }

    public Panel getPanelForID(Long panelID) {
        return scenarioDAO.getPanelForID(panelID);
    }

    public void savePanelData(ArrayList<PanelData> panelDataList) {
        scenarioDAO.savePanelData(panelDataList);
    }

    public LiveData<List<PanelPVSummary>> getPanelDataSummary() {
        return panelPVSummary;
    }

    public void updatePanel(Panel panel) {
        scenarioDAO.updatePanel(panel);
    }

    public boolean checkForMissingPanelData(Long scenarioID) {
        return scenarioDAO.checkForMissingPanelData(scenarioID);
    }

    public void removeCostingsForPricePlan(long pricePlanIndex) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                costingDAO.deleteRelatedCostings((int)pricePlanIndex));
    }

    public void pruneCostings() {
        ToutcDB.databaseWriteExecutor.execute(costingDAO::pruneCostings);
    }

    public boolean costingExists(long scenarioID, long pricePlanIndex) {
        return costingDAO.costingExists(scenarioID, pricePlanIndex);
    }

    public void removeOldPanelData(Long panelID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
            scenarioDAO.removePanelData(panelID));
    }

    public List<SimulationInputData> getSimulationInputNoSolar(long scenarioID) {
        return scenarioDAO.getSimulationInputNoSolar(scenarioID);
    }

    public List<Double> getSimulationInputForPanel(long panelID) {
        return scenarioDAO.getSimulationInputForPanel(panelID);
    }


    public List<String> getLinkedLoadProfiles(Long scenarioID) {
        return  scenarioDAO.getLinkedLoadProfiles(scenarioID);
    }

    public List<String> getLinkedInverters(Long inverterID, Long scenarioID) {
        return  scenarioDAO.getLinkedInverters(inverterID, scenarioID);
    }

    public List<String> getLinkedPanels(long panelIndex, Long scenarioID) {
        return scenarioDAO.getLinkedPanels(panelIndex, scenarioID);
    }

    public LiveData<List<Scenario2Battery>> getAllBatteryRelations() {
        return batteryRelations;
    }

    public List<Battery> getBatteriesForScenarioID(Long scenarioID) {
        return scenarioDAO.getBatteriesForScenarioID(scenarioID);
    }

    public void deleteBatteryFromScenario(Long batteryID, Long scenarioID) {
        scenarioDAO.deleteBatteryFromScenario(batteryID, scenarioID);
    }

    public void saveBatteryForScenario(Long scenarioID, Battery battery) {
        scenarioDAO.saveBatteryForScenario(scenarioID, battery);
    }

    public List<String> getLinkedBatteries(long batteryIndex, Long scenarioID) {
        return scenarioDAO.getLinkedBatteries(batteryIndex, scenarioID);
    }

    public void copyBatteryFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
        scenarioDAO.copyBatteryFromScenario(fromScenarioID, toScenarioID));
    }

    public void linkBatteryFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
        scenarioDAO.linkBatteryFromScenario(fromScenarioID, toScenarioID));
    }

    public LiveData<List<Scenario2LoadShift>> getAllLoadShiftRelations() {
        return loadShiftRelations;
    }

    public List<LoadShift> getLoadShiftsForScenarioID(Long scenarioID) {
        return scenarioDAO.getLoadShiftsForScenarioID(scenarioID);
    }

    public void deleteLoadShiftFromScenario(Long loadShiftID, Long scenarioID) {
        scenarioDAO.deleteLoadShiftFromScenario(loadShiftID, scenarioID);
    }

    public void saveLoadShiftForScenario(Long scenarioID, LoadShift loadShift) {
        scenarioDAO.saveLoadShiftForScenario(scenarioID, loadShift);
    }

    public List<String> getLinkedLoadShifts(long loadShiftIndex, Long scenarioID) {
        return scenarioDAO.getLinkedLoadShifts(loadShiftIndex, scenarioID);
    }

    public void copyLoadShiftFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.copyLoadShiftFromScenario(fromScenarioID, toScenarioID));
    }

    public void linkLoadShiftFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.linkLoadShiftFromScenario(fromScenarioID, toScenarioID));
    }

    public void deleteSimulationDataForScenarioID(Long scenarioID) {
        scenarioDAO.deleteSimulationDataForScenarioID(scenarioID);
    }

    public void deleteCostingDataForScenarioID(Long scenarioID) {
        scenarioDAO.deleteCostingDataForScenarioID(scenarioID);
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
        return scenarioDAO.getLinkedHWSystems(hwSystemIndex, scenarioID);
    }

    public HWSystem getHWSystemForScenarioID(Long scenarioID) {
        return scenarioDAO.getHWSystemForScenarioID(scenarioID);
    }

    public void saveHWSystemForScenario(Long scenarioID, HWSystem hwSystem) {
        scenarioDAO.saveHWSystemForScenario(scenarioID, hwSystem);
    }

    public void linkHWSystemFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.linkHWSystemFromScenario(fromScenarioID, toScenarioID));
    }

    public void copyHWSettingsFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.copyHWSettingsFromScenario(fromScenarioID, toScenarioID));
    }

    public LiveData<List<Scenario2HWSystem>> getAllHWSystemRelations() {
        return hwSystemRelations;
    }

    public void saveHWDivert(Long scenarioID, HWDivert hwDivert) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.saveHWDivert(scenarioID, hwDivert));
    }

    public List<String> getLinkedHWSchedules(long hwScheduleIndex, Long scenarioID) {
        return scenarioDAO.getLinkedHWSchedules(hwScheduleIndex, scenarioID);
    }

    public LiveData<List<Scenario2HWSchedule>> getAllHWScheduleRelations() {
        return  hwScheduleRelations;
    }

    public List<HWSchedule> getHWSchedulesForScenario(Long scenarioID) {
        return scenarioDAO.getHWSchedulesForScenarioID(scenarioID);
    }

    public void deleteHWScheduleFromScenario(Long hwScheduleID, Long scenarioID) {
        scenarioDAO.deleteHWScheduleFromScenario(hwScheduleID, scenarioID);
    }

    public void saveHWScheduleForScenario(Long scenarioID, HWSchedule hwSchedule) {
        scenarioDAO.saveHWScheduleForScenario(scenarioID, hwSchedule);
    }

    public void copyHWScheduleFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.copyHWScheduleFromScenario(fromScenarioID, toScenarioID));
    }

    public void linkHWScheduleFromScenario(long fromScenarioID, long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.linkHWScheduleFromScenario(fromScenarioID, toScenarioID));
    }

    public List<String> getLinkedEVCharges(long evChargeIndex, Long scenarioID) {
        return scenarioDAO.getLinkedEVCharges(evChargeIndex, scenarioID);
    }

    public LiveData<List<Scenario2EVCharge>> getAllEVChargeRelations() {
        return evChargeRelations;
    }

    public List<EVCharge> getEVChargesForScenario(Long scenarioID) {
        return scenarioDAO.getEVChargesForScenarioID(scenarioID);
    }

    public void deleteEVChargeFromScenario(Long evChargeID, Long scenarioID) {
        scenarioDAO.deleteEVChargeFromScenario(evChargeID, scenarioID);
    }

    public void saveEVChargeForScenario(Long scenarioID, EVCharge evCharge) {
        scenarioDAO.saveEVChargeForScenario(scenarioID, evCharge);
    }

    public void copyEVChargeFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.copyEVChargeFromScenario(fromScenarioID, toScenarioID));
    }

    public void linkEVChargeFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.linkEVChargeFromScenario(fromScenarioID, toScenarioID));
    }

    public List<String> getLinkedEVDiverts(long evDivertIndex, Long scenarioID) {
        return scenarioDAO.getLinkedEVDiverts(evDivertIndex, scenarioID);
    }

    public LiveData<List<Scenario2EVDivert>> getAllEVDivertRelations() {
        return evDivertRelations;
    }

    public List<EVDivert> getEVDivertsForScenario(Long scenarioID) {
        return scenarioDAO.getEVDivertForScenarioID(scenarioID);
    }

    public void deleteEVDivertFromScenario(Long evDivertID, Long scenarioID) {
        scenarioDAO.deleteEVDivertFromScenario(evDivertID, scenarioID);
    }

    public void saveEVDivertForScenario(Long scenarioID, EVDivert evDivert) {
        scenarioDAO.saveEVDivertForScenario(scenarioID, evDivert);
    }

    public void copyEVDivertFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.copyEVDivertFromScenario(fromScenarioID, toScenarioID));
    }

    public void linkEVDivertFromScenario(long fromScenarioID, Long toScenarioID) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.linkEVDivertFromScenario(fromScenarioID, toScenarioID));
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
        scenarioDAO.copyDischargeFromScenario(fromScenarioID, toScenarioID));
    }

    public void saveDischargeForScenario(Long scenarioID, DischargeToGrid discharge) {
        scenarioDAO.saveDischargeForScenario(scenarioID, discharge);
    }

    public LiveData<List<Scenario2DischargeToGrid>> getAllDischargeRelations() {
        return dischargeRelations;
    }

    public List<DischargeToGrid> getDischargesForScenario(Long scenarioID) {
        return scenarioDAO.getDischargesForScenarioID(scenarioID);
    }

    public void deleteDischargeFromScenario(Long dischargeID, Long scenarioID) {
        scenarioDAO.deleteDischargeFromScenario(dischargeID, scenarioID);
    }

    public List<String> getLinkedDischarges(long d2gIndex, Long scenarioID) {
        return scenarioDAO.getLinkedDischarges(d2gIndex, scenarioID);
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
}
