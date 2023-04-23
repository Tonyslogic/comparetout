package com.tfcode.comparetout;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.tfcode.comparetout.model.costings.Costings;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.PanelPVSummary;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.Scenario2Battery;
import com.tfcode.comparetout.model.scenario.Scenario2Inverter;
import com.tfcode.comparetout.model.scenario.Scenario2Panel;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;
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
//        LiveData<List<Scenario>> allScenarios = toutcRepository.getAllScenarios();
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

    public void deletePricePlanRow(int id) {
        toutcRepository.deletePPRow(id);
    }

    public void deleteAll() {
        toutcRepository.deleteAll();
    }

    public void delpp(PricePlan pp) {
        toutcRepository.delpp(pp);
    }

    public Map<PricePlan, List<DayRate>> getPricePlan(Integer id) {
        return toutcRepository.getPricePlan(id);
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
}