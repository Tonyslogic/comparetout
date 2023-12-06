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

package com.tfcode.comparetout;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.tfcode.comparetout.model.costings.Costings;
import com.tfcode.comparetout.model.importers.alphaess.IntervalRow;
import com.tfcode.comparetout.model.importers.alphaess.InverterDateRange;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.ToutcRepository;
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
import com.tfcode.comparetout.model.scenario.PanelPVSummary;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.Scenario2Battery;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ComparisonUIViewModel extends AndroidViewModel {

    private final ToutcRepository toutcRepository;
    private final LiveData<Map<PricePlan, List<DayRate>>> allPricePlans;

    public ComparisonUIViewModel(Application application) {
        super(application);
        toutcRepository = new ToutcRepository(application);
        allPricePlans = toutcRepository.getAllPricePlans();
    }

    public LiveData<Map<PricePlan, List<DayRate>>> getAllPricePlans() {
        return allPricePlans;
    }

    public void insertPricePlan(PricePlan pp, List<DayRate> drs) {
        toutcRepository.insert(pp, drs);
    }

    public void deletePricePlan(Integer id) {
        toutcRepository.deletePricePlan(id);
        toutcRepository.removeCostingsForPricePlan(id);
    }

    public void updatePricePlanActiveStatus(int id, boolean checked) {
        toutcRepository.updatePricePlanActiveStatus(id, checked);
    }

    public void updatePricePlan(PricePlan p, ArrayList<DayRate> drs) {
        toutcRepository.updatePricePlan(p, drs);
        toutcRepository.removeCostingsForPricePlan(p.getPricePlanIndex());
    }

    public void insertScenario(ScenarioComponents sc) {
        toutcRepository.insertScenario(sc);
    }

    public LiveData<List<Scenario>> getAllScenarios() {
        return toutcRepository.getAllScenarios();
    }

    public void updateScenario(Scenario scenario) {
        toutcRepository.updateScenario(scenario);
    }

    public LiveData<LoadProfile> getLoadProfile(Long scenarioID) {
        return toutcRepository.getLoadProfile(scenarioID);
    }

    public void saveLoadProfile(Long scenarioID, LoadProfile loadProfile) {
        toutcRepository.saveLoadProfile(scenarioID, loadProfile);
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

    // AlphaESS methods
    public LiveData<List<InverterDateRange>> getLiveDateRanges() {
        return toutcRepository.getLiveDateRanges();
    }

    public void clearInverterBySN (String sysSN) {
        toutcRepository.clearAlphaESSDataForSN(sysSN);
    }

    public List<IntervalRow> getSumHour(String systemSN, String from, String to) {
        return toutcRepository.getSumHour(systemSN, from, to);
    }

    public List<IntervalRow> getSumDOY(String systemSN, String from, String to) {
        return toutcRepository.getSumDOY(systemSN, from, to);
    }

    public List<IntervalRow> getSumDOW(String systemSN, String from, String to) {
        return toutcRepository.getSumDOW(systemSN, from, to);
    }

    public List<IntervalRow> getSumMonth(String systemSN, String from, String to) {
        return toutcRepository.getSumMonth(systemSN, from, to);
    }

    public List<IntervalRow> getSumYear(String systemSN, String from, String to) {
        return toutcRepository.getSumYear(systemSN, from, to);
    }

    public List<IntervalRow> getAvgHour(String systemSN, String from, String to) {
        return toutcRepository.getAvgHour(systemSN, from, to);
    }

    public List<IntervalRow> getAvgDOY(String systemSN, String from, String to) {
        return toutcRepository.getAvgDOY(systemSN, from, to);
    }

    public List<IntervalRow> getAvgDOW(String systemSN, String from, String to) {
        return toutcRepository.getAvgDOW(systemSN, from, to);
    }

    public List<IntervalRow> getAvgMonth(String systemSN, String from, String to) {
        return toutcRepository.getAvgMonth(systemSN, from, to);
    }

    public List<IntervalRow> getAvgYear(String systemSN, String from, String to) {
        return toutcRepository.getAvgYear(systemSN, from, to);
    }
}