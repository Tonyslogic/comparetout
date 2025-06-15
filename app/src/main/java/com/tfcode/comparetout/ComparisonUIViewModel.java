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

package com.tfcode.comparetout;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.costings.Costings;
import com.tfcode.comparetout.model.importers.CostInputRow;
import com.tfcode.comparetout.model.importers.IntervalRow;
import com.tfcode.comparetout.model.importers.InverterDateRange;
import com.tfcode.comparetout.model.importers.alphaess.KPIRow;
import com.tfcode.comparetout.model.importers.alphaess.KeyStatsRow;
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
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.Panel;
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
import com.tfcode.comparetout.model.scenario.SimKPIs;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Primary ViewModel for managing UI state and data operations across the TOUTC application.
 * 
 * This AndroidViewModel serves as the central data management layer, coordinating between
 * the user interface and the underlying repository/database systems. It provides reactive
 * data streams using LiveData to ensure UI components stay synchronized with data changes,
 * while encapsulating complex business logic and data transformation operations.
 * 
 * Key responsibilities:
 * - Price plan management (creation, updates, deletion, activation status)
 * - Energy system scenario management with component relationships
 * - Cost calculation coordination and result caching
 * - Data import/export operations for various energy system formats
 * - UI state management for comparison and analysis views
 * - Background work coordination for long-running calculations
 * 
 * The ViewModel follows Android Architecture Components best practices by:
 * - Surviving configuration changes to maintain UI state
 * - Providing reactive data streams through LiveData
 * - Separating UI logic from data access logic
 * - Managing the lifecycle of data operations
 * 
 * Data flow architecture:
 * UI Components → ViewModel → Repository → Database/DAOs
 * 
 * The class handles complex energy system data relationships including many-to-many
 * associations between scenarios and components (inverters, batteries, panels, etc.),
 * ensuring data integrity while providing efficient access patterns for UI consumption.
 */
public class ComparisonUIViewModel extends AndroidViewModel {

    private final ToutcRepository toutcRepository;
    private final LiveData<Map<PricePlan, List<DayRate>>> allPricePlans;

    /**
     * Initialize the ViewModel with repository access and reactive data streams.
     * 
     * Sets up the core data connections and initializes LiveData streams that
     * UI components will observe for automatic updates when underlying data changes.
     * 
     * @param application the application context for repository initialization
     */
    public ComparisonUIViewModel(Application application) {
        super(application);
        toutcRepository = new ToutcRepository(application);
        allPricePlans = toutcRepository.getAllPricePlans();
    }

    /**
     * Get all price plans with their associated day rates as reactive data.
     * 
     * @return LiveData containing a map of price plans to their day rate definitions
     */
    public LiveData<Map<PricePlan, List<DayRate>>> getAllPricePlans() {
        return allPricePlans;
    }

    /**
     * Insert a new price plan with its rate structure.
     * 
     * Creates a new electricity tariff definition in the database with the option
     * to overwrite existing plans. Automatically triggers cost calculation cleanup
     * to ensure calculation results remain consistent with available plans.
     * 
     * @param pp the price plan definition to insert
     * @param drs list of day rates defining the time-based pricing structure
     * @param clobber whether to overwrite existing plans with matching identifiers
     */
    public void insertPricePlan(PricePlan pp, List<DayRate> drs, boolean clobber) {
        toutcRepository.insert(pp, drs, clobber);
        toutcRepository.pruneCostings();
    }

    /**
     * Delete a price plan and all associated data.
     * 
     * Removes the price plan definition and all dependent cost calculations
     * to maintain database integrity and prevent orphaned calculation results.
     * 
     * @param id the price plan identifier to delete
     */
    public void deletePricePlan(Integer id) {
        toutcRepository.deletePricePlan(id);
        toutcRepository.removeCostingsForPricePlan(id);
    }

    /**
     * Update the active status of a price plan for cost calculations.
     * 
     * @param id the price plan identifier
     * @param checked whether the plan should be included in cost comparisons
     */
    public void updatePricePlanActiveStatus(int id, boolean checked) {
        toutcRepository.updatePricePlanActiveStatus(id, checked);
    }

    /**
     * Update an existing price plan with new rate information.
     * 
     * Modifies the price plan definition and clears associated cost calculations
     * since rate changes invalidate previous calculation results.
     * 
     * @param p the updated price plan definition
     * @param drs the updated day rate structure
     */
    public void updatePricePlan(PricePlan p, ArrayList<DayRate> drs) {
        toutcRepository.updatePricePlan(p, drs);
        toutcRepository.removeCostingsForPricePlan(p.getPricePlanIndex());
    }

    /**
     * Remove cost calculations for a specific price plan.
     * 
     * @param pricePlanID the price plan identifier to clear calculations for
     */
    public void removeCostingsForPricePlan(long pricePlanID){
        toutcRepository.removeCostingsForPricePlan(pricePlanID);
    }

    /**
     * Insert a complete energy system scenario with all components.
     * 
     * @param sc the scenario components structure containing all system definitions
     * @param clobber whether to overwrite existing scenarios with matching identifiers
     */
    public void insertScenario(ScenarioComponents sc, boolean clobber) {
        toutcRepository.insertScenario(sc, clobber);
    }

    /**
     * Insert a scenario and return its database identifier for further operations.
     * 
     * @param sc the scenario components to insert
     * @param clobber whether to overwrite existing scenarios
     * @return the database-assigned scenario identifier
     */
    public long insertScenarioAndReturnID(ScenarioComponents sc, boolean clobber) {
        return toutcRepository.insertScenarioAndReturnID(sc, clobber);
    }

    /**
     * Get all energy system scenarios as reactive data.
     * 
     * @return LiveData containing the list of all defined scenarios
     */
    public LiveData<List<Scenario>> getAllScenarios() {
        return toutcRepository.getAllScenarios();
    }

    /**
     * Update an existing scenario definition.
     * 
     * @param scenario the updated scenario definition
     */
    public void updateScenario(Scenario scenario) {
        toutcRepository.updateScenario(scenario);
    }

    public LiveData<LoadProfile> getLoadProfile(Long scenarioID) {
        return toutcRepository.getLoadProfile(scenarioID);
    }

    public void saveLoadProfile(Long scenarioID, LoadProfile loadProfile) {
        toutcRepository.saveLoadProfile(scenarioID, loadProfile);
    }

    public long saveLoadProfileAndReturnID(Long scenarioID, LoadProfile loadProfile) {
        return toutcRepository.saveLoadProfileAndReturnID(scenarioID, loadProfile);
    }

    public LiveData<List<Costings>> getAllComparisons() {
        return toutcRepository.getAllCostings();
    }

    public void updateScenarioActiveStatus(int id, boolean checked) {
        toutcRepository.updateScenarioActiveStatus(id, checked);
    }

    public void deleteScenario(int id) {
        toutcRepository.deleteScenario(id);
    }

    public void deleteRelatedCostings(int id) {
        toutcRepository.deleteRelatedCostings(id);
    }

    public void copyScenario(int id) {
        toutcRepository.copyScenario(id);
    }

    public List<ScenarioComponents> getAllScenariosForExport() {
        return toutcRepository.getAllScenariosForExport();
    }

    public Map<PricePlan, List<DayRate>> getAllPricePlansForExport() {
        return toutcRepository.getAllPricePlansForExport();
    }

    public void copyLoadProfileFromScenario(long fromScenarioID, Long toScenarioID) {
        toutcRepository.copyLoadProfileFromScenario(fromScenarioID, toScenarioID);
    }

    public void linkLoadProfileFromScenario(long fromScenarioID, Long toScenarioID) {
        toutcRepository.linkLoadProfileFromScenario(fromScenarioID, toScenarioID);
    }

    public SimKPIs getSimKPIsForScenario(Long scenarioID) {
        return toutcRepository.getSimKPIsForScenario (scenarioID);
    }

    public Costings getBestCostingForScenario(Long scenarioID) {
        return toutcRepository.getBestCostingForScenario(scenarioID);
    }

    public List<Inverter> getInvertersForScenario(Long scenarioID) {
        return toutcRepository.getInvertersForScenario(scenarioID);
    }

    public void saveInverterForScenario(Long scenarioID, Inverter inverter) {
        toutcRepository.saveInverter(scenarioID, inverter);
    }

    public void deleteInverterFromScenario(Long inverterID, Long scenarioID) {
        toutcRepository.deleteInverterFromScenario(inverterID, scenarioID);
    }

    public void copyInverterFromScenario(long fromScenarioID, Long toScenarioID) {
        toutcRepository.copyInverterFromScenario(fromScenarioID, toScenarioID);
    }

    public LiveData<List<Scenario2Inverter>> getAllInverterRelations() {
        return toutcRepository.getAllInverterRelations();
    }

    public void linkInverterFromScenario(long fromScenarioID, Long toScenarioID) {
        toutcRepository.linkInverterFromScenario(fromScenarioID, toScenarioID);
    }

    public LiveData<List<Scenario2Panel>> getAllPanelRelations() {
        return toutcRepository.getAllPanelRelations();
    }

    public List<Panel> getPanelsForScenario(Long scenarioID) {
        return toutcRepository.getPanelsForScenario(scenarioID);
    }

    public void deletePanelFromScenario(Long panelID, Long scenarioID) {
        toutcRepository.deletePanelFromScenario(panelID, scenarioID);
    }

    public void savePanelForScenario(Long scenarioID, Panel panel) {
        toutcRepository.savePanel(scenarioID, panel);
    }

    public void copyPanelFromScenario(long fromScenarioID, Long toScenarioID) {
        toutcRepository.copyPanelFromScenario(fromScenarioID, toScenarioID);
    }

    public void linkPanelFromScenario(long fromScenarioID, Long toScenarioID) {
        toutcRepository.linkPanelFromScenario(fromScenarioID, toScenarioID);
    }

    public Panel getPanelForID(Long panelID) {
        return toutcRepository.getPanelForID(panelID);
    }

    public LiveData<List<PanelPVSummary>> getPanelDataSummary() {
        return toutcRepository.getPanelDataSummary();
    }

    public void updatePanel(Panel panel) {
        toutcRepository.updatePanel(panel);
    }

    public void removeOldPanelData(Long panelID) {
        toutcRepository.removeOldPanelData(panelID);
    }

    public void deleteSimulationDataForPanelID(long panelIndex) {
        toutcRepository.deleteSimulationDataForPanelID(panelIndex);
    }

    public void deleteCostingDataForPanelID(long panelIndex) {
        toutcRepository.deleteCostingDataForPanelID(panelIndex);
    }

    public List<String> getLinkedLoadProfiles(Long scenarioID) {
        return toutcRepository.getLinkedLoadProfiles(scenarioID);
    }

    public List<String> getLinkedInverters(Long inverterID, Long scenarioID) {
        return toutcRepository.getLinkedInverters(inverterID, scenarioID);
    }

    public List<String> getLinkedPanels(long panelIndex, Long scenarioID) {
        return toutcRepository.getLinkedPanels(panelIndex, scenarioID);
    }

    public LiveData<List<Scenario2Battery>> getAllBatteryRelations() {
        return toutcRepository.getAllBatteryRelations();
    }

    public List<Battery> getBatteriesForScenario(Long scenarioID) {
        return toutcRepository.getBatteriesForScenarioID(scenarioID);
    }

    public void deleteBatteryFromScenario(Long batteryID, Long scenarioID) {
        toutcRepository.deleteBatteryFromScenario(batteryID, scenarioID);
    }

    public void saveBatteryForScenario(Long scenarioID, Battery battery) {
        toutcRepository.saveBatteryForScenario(scenarioID, battery);
    }

    public List<String> getLinkedBatteries(long batteryIndex, Long scenarioID) {
        return toutcRepository.getLinkedBatteries(batteryIndex, scenarioID);
    }

    public void copyBatteryFromScenario(long fromScenarioID, Long toScenarioID) {
        toutcRepository.copyBatteryFromScenario(fromScenarioID, toScenarioID);
    }

    public void linkBatteryFromScenario(long fromScenarioID, Long toScenarioID) {
        toutcRepository.linkBatteryFromScenario(fromScenarioID, toScenarioID);
    }

    public LiveData<List<Scenario2LoadShift>> getAllLoadShiftRelations() {
        return toutcRepository.getAllLoadShiftRelations();
    }

    public List<LoadShift> getLoadShiftsForScenario(Long scenarioID) {
        return toutcRepository.getLoadShiftsForScenarioID(scenarioID);
    }

    public void deleteLoadShiftFromScenario(Long loadShiftID, Long scenarioID) {
        toutcRepository.deleteLoadShiftFromScenario(loadShiftID, scenarioID);
    }

    public void saveLoadShiftForScenario(Long scenarioID, LoadShift loadShift) {
        toutcRepository.saveLoadShiftForScenario(scenarioID, loadShift);
    }

    public List<String> getLinkedLoadShifts(long loadShiftIndex, Long scenarioID) {
        return toutcRepository.getLinkedLoadShifts(loadShiftIndex, scenarioID);
    }

    public void copyLoadShiftFromScenario(long fromScenarioID, Long toScenarioID) {
        toutcRepository.copyLoadShiftFromScenario(fromScenarioID, toScenarioID);
    }

    public void linkLoadShiftFromScenario(long fromScenarioID, Long toScenarioID) {
        toutcRepository.linkLoadShiftFromScenario(fromScenarioID, toScenarioID);
    }

    public boolean checkForMissingPanelData(Long scenarioID) {
        return toutcRepository.checkForMissingPanelData(scenarioID);
    }

    public void deleteSimulationDataForScenarioID(Long scenarioID) {
        toutcRepository.deleteSimulationDataForScenarioID(scenarioID);
    }

    public void deleteCostingDataForScenarioID(Long scenarioID) {
        toutcRepository.deleteCostingDataForScenarioID(scenarioID);
    }

    public List<ScenarioBarChartData> getBarData(Long scenarioID, int dayOfYear) {
        return toutcRepository.getBarData(scenarioID, dayOfYear);
    }

    public List<ScenarioLineGraphData> getLineData(Long scenarioID, int dayOfYear) {
        return toutcRepository.getLineData(scenarioID, dayOfYear);
    }

    public List<ScenarioBarChartData> getMonthlyBarData(Long scenarioID, int dayOfYear) {
        return toutcRepository.getMonthlyBarData(scenarioID, dayOfYear);
    }

    public List<ScenarioBarChartData> getYearBarData(Long scenarioID) {
        return toutcRepository.getYearBarData(scenarioID);
    }

    public ScenarioComponents getScenarioComponentsForID(Long scenarioID) {
        return toutcRepository.getScenarioComponentsForScenarioID(scenarioID);
    }

    public List<String> getLinkedHWSystems(long hwSystemIndex, Long scenarioID) {
        return toutcRepository.getLinkedHWSystems(hwSystemIndex, scenarioID);
    }

    public HWSystem getHWSystemForScenarioID(Long scenarioID) {
        return toutcRepository.getHWSystemForScenarioID(scenarioID);
    }

    public void saveHWSystemForScenario(Long scenarioID, HWSystem hwSystem) {
        toutcRepository.saveHWSystemForScenario(scenarioID, hwSystem);
    }

    public void linkHWSystemFromScenario(long fromScenarioID, Long toScenarioID) {
        toutcRepository.linkHWSystemFromScenario(fromScenarioID, toScenarioID);
    }

    public void copyHWSettingsFromScenario(long fromScenarioID, Long toScenarioID) {
        toutcRepository.copyHWSettingsFromScenario(fromScenarioID, toScenarioID);
    }

    public LiveData<List<Scenario2HWSystem>> getAllHWSystemRelations() {
        return toutcRepository.getAllHWSystemRelations();
    }

    public void saveHWDivert(Long scenarioID, HWDivert hwDivert) {
        toutcRepository.saveHWDivert(scenarioID, hwDivert);
    }

    public List<String> getLinkedHWSchedules(long hwScheduleIndex, Long scenarioID) {
        return toutcRepository.getLinkedHWSchedules(hwScheduleIndex, scenarioID);
    }

    public LiveData<List<Scenario2HWSchedule>> getAllHWScheduleRelations() {
        return toutcRepository.getAllHWScheduleRelations();
    }

    public List<HWSchedule> getHWSchedulesForScenario(Long scenarioID) {
        return toutcRepository.getHWSchedulesForScenario(scenarioID);
    }

    public void deleteHWScheduleFromScenario(Long loadShiftID, Long scenarioID) {
        toutcRepository.deleteHWScheduleFromScenario(loadShiftID, scenarioID);
    }

    public void saveHWScheduleForScenario(Long scenarioID, HWSchedule hwSchedule) {
        toutcRepository.saveHWScheduleForScenario(scenarioID, hwSchedule);
    }

    public void copyHWScheduleFromScenario(long fromScenarioID, Long toScenarioID) {
        toutcRepository.copyHWScheduleFromScenario(fromScenarioID, toScenarioID);
    }

    public void linkHWScheduleFromScenario(long fromScenarioID, Long mScenarioID) {
        toutcRepository.linkHWScheduleFromScenario(fromScenarioID, mScenarioID);
    }

    public List<String> getLinkedEVCharges(long evChargeIndex, Long scenarioID) {
        return toutcRepository.getLinkedEVCharges(evChargeIndex, scenarioID);
    }

    public LiveData<List<Scenario2EVCharge>> getAllEVChargeRelations() {
        return toutcRepository.getAllEVChargeRelations();
    }

    public List<EVCharge> getEVChargesForScenario(Long scenarioID) {
        return toutcRepository.getEVChargesForScenario(scenarioID);
    }

    public void deleteEVChargeFromScenario(Long evChargeID, Long scenarioID) {
        toutcRepository.deleteEVChargeFromScenario(evChargeID, scenarioID);
    }

    public void saveEVChargeForScenario(Long scenarioID, EVCharge evCharge) {
        toutcRepository.saveEVChargeForScenario(scenarioID, evCharge);
    }

    public void copyEVChargeFromScenario(long fromScenarioID, Long toScenarioID) {
        toutcRepository.copyEVChargeFromScenario(fromScenarioID, toScenarioID);
    }

    public void linkEVChargeFromScenario(long fromScenarioID, Long toScenarioID) {
        toutcRepository.linkEVChargeFromScenario(fromScenarioID, toScenarioID);
    }

    public List<String> getLinkedEVDiverts(long evDivertIndex, Long scenarioID) {
        return toutcRepository.getLinkedEVDiverts(evDivertIndex, scenarioID);
    }

    public LiveData<List<Scenario2EVDivert>> getAllEVDivertRelations() {
        return toutcRepository.getAllEVDivertRelations();
    }

    public List<EVDivert> getEVDivertsForScenario(Long scenarioID) {
        return toutcRepository.getEVDivertsForScenario(scenarioID);
    }

    public void deleteEVDivertFromScenario(Long evDivertID, Long scenarioID) {
        toutcRepository.deleteEVDivertFromScenario(evDivertID, scenarioID);
    }

    public void saveEVDivertForScenario(Long scenarioID, EVDivert evDivert) {
        toutcRepository.saveEVDivertForScenario(scenarioID, evDivert);
    }

    public void copyEVDivertFromScenario(long fromScenarioID, Long toScenarioID) {
        toutcRepository.copyEVDivertFromScenario(fromScenarioID, toScenarioID);
    }

    public void linkEVDivertFromScenario(long fromScenarioID, Long toScenarioID) {
        toutcRepository.linkEVDivertFromScenario(fromScenarioID, toScenarioID);
    }

    public List<String> getAllComparisonsNow() {
        return toutcRepository.getAllComparisonsNow();
    }

    public void linkDischargeFromScenario(long fromScenarioID, Long toScenarioID) {
        toutcRepository.linkDischargeFromScenario(fromScenarioID, toScenarioID);
    }

    public void copyDischargeFromScenario(long fromScenarioID, Long toScenarioID) {
        toutcRepository.copyDischargeFromScenario(fromScenarioID, toScenarioID);
    }

    public void saveDischargeForScenario(Long scenarioID, DischargeToGrid discharge) {
        toutcRepository.saveDischargeForScenario(scenarioID, discharge);
    }

    public LiveData<List<Scenario2DischargeToGrid>> getAllDischargeRelations() {
        return toutcRepository.getAllDischargeRelations();
    }

    public List<DischargeToGrid> getDischargesForScenario(Long scenarioID) {
        return toutcRepository.getDischargesForScenario(scenarioID);
    }

    public void deleteDischargeFromScenario(Long dischargeID, Long scenarioID) {
        toutcRepository.deleteDischargeFromScenario(dischargeID, scenarioID);
    }

    public List<String> getLinkedDischarges(long d2gIndex, Long scenarioID) {
        return toutcRepository.getLinkedDischarges(d2gIndex, scenarioID);
    }

    public InverterDateRange getDateRangeForSystem(@NotNull Importer importer, String sysSN) {
        if (importer == Importer.SIMULATION) {
            return toutcRepository.getSimDateRanges(sysSN);
        }
        return toutcRepository.getDateRange(sysSN);
    }

    public enum Importer {
        ALPHAESS,
        ESBNHDF,
        HOME_ASSISTANT,
        SIMULATION
    }

    // Importer methods
    public LiveData<List<InverterDateRange>> getLiveDateRanges(Importer importer) {
        switch (importer){
            case ALPHAESS: return toutcRepository.getLiveDateRanges();
            case ESBNHDF: return toutcRepository.getESBNLiveDateRanges();
            case HOME_ASSISTANT: return toutcRepository.getHALiveDateRanges();
            case SIMULATION: return toutcRepository.getScenarioLiveDateRanges();
            default:
                return null;
        }
    }

    public void clearInverterBySN (Importer importer, String sysSN) {
        switch (importer){
            case ALPHAESS:
            case ESBNHDF:
            case HOME_ASSISTANT:
                toutcRepository.clearAlphaESSDataForSN(sysSN); break;
        }
    }

    public void deleteInverterDatesBySN(Importer importer, String sysSN, LocalDateTime selectedStart, LocalDateTime selectedEnd) {
        switch (importer){
            case ALPHAESS:
            case ESBNHDF:
            case HOME_ASSISTANT:
                toutcRepository.deleteInverterDatesBySN(sysSN, selectedStart, selectedEnd); break;
        }
    }

    public List<IntervalRow> getSumHour(Importer importer, String systemSN, String from, String to) {
        switch (importer){
            case ALPHAESS:
            case ESBNHDF:
            case HOME_ASSISTANT:
                return toutcRepository.getSumHour(systemSN, from, to);
            case SIMULATION:
                return toutcRepository.getSimSumHour(systemSN, from, to);
            default: return null;
        }
    }

    public List<IntervalRow> getSumDOY(Importer importer, String systemSN, String from, String to) {
        switch (importer){
            case ALPHAESS:
            case ESBNHDF:
            case HOME_ASSISTANT:
                return toutcRepository.getSumDOY(systemSN, from, to);
            case SIMULATION:
                return toutcRepository.getSimSumDOY(systemSN, from, to);
            default: return null;
        }
    }

    public List<IntervalRow> getSumDOW(Importer importer, String systemSN, String from, String to) {
        switch (importer){
            case ALPHAESS:
            case ESBNHDF:
            case HOME_ASSISTANT:
                return toutcRepository.getSumDOW(systemSN, from, to);
            case SIMULATION:
                return toutcRepository.getSimSumDOW(systemSN, from, to);
            default: return null;
        }
    }

    public List<IntervalRow> getSumMonth(Importer importer, String systemSN, String from, String to) {
        switch (importer){
            case ALPHAESS:
            case ESBNHDF:
            case HOME_ASSISTANT:
                return toutcRepository.getSumMonth(systemSN, from, to);
            case SIMULATION:
                return toutcRepository.getSimSumMonth(systemSN, from, to);
            default: return null;
        }
    }

    public List<IntervalRow> getSumYear(Importer importer, String systemSN, String from, String to) {
        switch (importer){
            case ALPHAESS:
            case ESBNHDF:
            case HOME_ASSISTANT:
                return toutcRepository.getSumYear(systemSN, from, to);
            case SIMULATION:
                return toutcRepository.getSimSumYear(systemSN, from, to);
            default: return null;
        }
    }

    public List<IntervalRow> getAvgHour(Importer importer, String systemSN, String from, String to) {
        switch (importer){
            case ALPHAESS:
            case ESBNHDF:
            case HOME_ASSISTANT:
                return toutcRepository.getAvgHour(systemSN, from, to);
            case SIMULATION:
                return toutcRepository.getSimAvgHour(systemSN, from, to);
            default: return null;
        }
    }

    public List<IntervalRow> getAvgDOY(Importer importer, String systemSN, String from, String to) {
        switch (importer){
            case ALPHAESS:
            case ESBNHDF:
            case HOME_ASSISTANT:
                return toutcRepository.getAvgDOY(systemSN, from, to);
            case SIMULATION:
                return toutcRepository.getSimAvgDOY(systemSN, from, to);
            default: return null;
        }
    }

    public List<IntervalRow> getAvgDOW(Importer importer, String systemSN, String from, String to) {
        switch (importer){
            case ALPHAESS:
            case ESBNHDF:
            case HOME_ASSISTANT:
                return toutcRepository.getAvgDOW(systemSN, from, to);
            case SIMULATION:
                return toutcRepository.getSimAvgDOW(systemSN, from, to);
            default: return null;
        }
    }

    public List<IntervalRow> getAvgMonth(Importer importer, String systemSN, String from, String to) {
        switch (importer){
            case ALPHAESS:
            case ESBNHDF:
            case HOME_ASSISTANT:
                return toutcRepository.getAvgMonth(systemSN, from, to);
            case SIMULATION:
                return toutcRepository.getSimAvgMonth(systemSN, from, to);
            default: return null;
        }
    }

    public List<IntervalRow> getAvgYear(Importer importer, String systemSN, String from, String to) {
        switch (importer){
            case ALPHAESS:
            case ESBNHDF:
            case HOME_ASSISTANT:
                return toutcRepository.getAvgYear(systemSN, from, to);
            case SIMULATION:
                return toutcRepository.getSimAvgYear(systemSN, from, to);
            default: return null;
        }
    }

    public List<CostInputRow> getSelectedAlphaESSData(Importer importer, String serialNumber, String from, String to) {
        switch (importer){
            case ALPHAESS:
            case ESBNHDF:
            case HOME_ASSISTANT:
                return toutcRepository.getSelectedAlphaESSData(serialNumber, from, to);
        }
        return null;
    }

    public List<KeyStatsRow> getKeyStats(Importer importer, String from, String to, String systemSN) {
        switch (importer){
            case ALPHAESS: return toutcRepository.getKeyStats(from, to, systemSN);
            case HOME_ASSISTANT:  return toutcRepository.getHAKeyStats(from, to, systemSN);
            case ESBNHDF: return null;
        }
        return null;
    }

    public KPIRow getKPIs(Importer importer, String from, String to, String systemSN) {
        switch (importer){
            case ALPHAESS:
            case HOME_ASSISTANT:  return toutcRepository.getKPIs(from, to, systemSN);
            case ESBNHDF: return null;
        }
        return null;
    }
}