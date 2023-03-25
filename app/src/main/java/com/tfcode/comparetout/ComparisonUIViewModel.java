package com.tfcode.comparetout;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.tfcode.comparetout.model.costings.Costings;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ComparisonUIViewModel extends AndroidViewModel {

    private final ToutcRepository toutcRepository;
    private final LiveData<Map<PricePlan, List<DayRate>>> allPricePlans;
    private final LiveData<List<Scenario>> allScenarios;
//    private String focusedPricePlanJson = "[]";

    public ComparisonUIViewModel(Application application) {
        super(application);
        toutcRepository = new ToutcRepository(application);
        allPricePlans = toutcRepository.getAllPricePlans();
        allScenarios = toutcRepository.getAllScenarios();
    }

    public LiveData<Map<PricePlan, List<DayRate>>> getAllPricePlans() {
        return allPricePlans;
    }

    public void insertPricePlan(PricePlan pp, List<DayRate> drs) {
        toutcRepository.insert(pp, drs);
    }

    public void deletePricePlan(Integer id) {
        toutcRepository.deletePricePlan(id);
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
}