package com.tfcode.comparetout.model;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.tfcode.comparetout.model.costings.Costings;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.model.scenario.LoadProfileData;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.PanelData;
import com.tfcode.comparetout.model.scenario.PanelPVSummary;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.Scenario2Battery;
import com.tfcode.comparetout.model.scenario.Scenario2Inverter;
import com.tfcode.comparetout.model.scenario.Scenario2Panel;
import com.tfcode.comparetout.model.scenario.ScenarioBarChartData;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;
import com.tfcode.comparetout.model.scenario.ScenarioLineGraphData;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;
import com.tfcode.comparetout.model.scenario.SimKPIs;
import com.tfcode.comparetout.model.scenario.SimulationInputData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ToutcRepository {
    private final PricePlanDAO pricePlanDAO;
    private final LiveData<Map<PricePlan, List<DayRate>>> allPricePlans;

    private final ScenarioDAO scenarioDAO;
    private final LiveData<List<Scenario>> allScenarios;
    private final LiveData<List<Scenario2Inverter>> inverterRelations;
    private final LiveData<List<Scenario2Panel>> panelRelations;
    private final LiveData<List<Scenario2Battery>> batteryRelations;
    private final LiveData<List<PanelPVSummary>> panelPVSummary;

    private final CostingDAO costingDAO;
    private final LiveData<List<Costings>> allCostings;

    // Note that in order to unit test the WordRepository, you have to remove the Application
    // dependency. This adds complexity and much more code, and this sample is not about testing.
    // See the BasicSample in the android-architecture-components repository at
    // https://github.com/googlesamples
    public ToutcRepository(Application application) {
        ToutcDB db = ToutcDB.getDatabase(application);
        pricePlanDAO = db.pricePlanDAO();
        allPricePlans = pricePlanDAO.loadPricePlans();

        scenarioDAO = db.scenarioDAO();
        allScenarios = scenarioDAO.loadScenarios();
        inverterRelations = scenarioDAO.loadInverterRelations();
        panelRelations = scenarioDAO.loadPanelRelations();
        batteryRelations = scenarioDAO.loadBatteryRelations();
        panelPVSummary = scenarioDAO.getPanelPVSummary();

        costingDAO = db.costingDAO();
        allCostings = costingDAO.loadCostings();
    }

    // Room executes all queries on a separate thread.
    // Observed LiveData will notify the observer when the data has changed.
    public LiveData<Map<PricePlan, List<DayRate>>> getAllPricePlans() {
        return allPricePlans;
    }

    // You must call this on a non-UI thread or your app will throw an exception. Room ensures
    // that you're not doing any long running operations on the main thread, blocking the UI.
    public void insert(PricePlan pp, List<DayRate> drs) {
        ToutcDB.databaseWriteExecutor.execute(() -> pricePlanDAO.addNewPricePlanWithDayRates(pp, drs));
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

    public void insertScenario(ScenarioComponents sc) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                scenarioDAO.addNewScenarioWithComponents(sc.scenario, sc));
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

    public List<Long> getAllScenariosThatNeedCosting() {
        return scenarioDAO.getAllScenariosThatNeedCosting();
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

    public void saveInverter(Long scenarioID, Inverter inverter) {
        scenarioDAO.saveInverter(scenarioID, inverter);
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

    public void savePanel(Long scenarioID, Panel panel) {
        scenarioDAO.savePanel(scenarioID, panel);
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
}
