package com.tfcode.comparetout;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.tfcode.comparetout.model.scenario.Scenario;

import java.util.ArrayList;
import java.util.List;

public class ScenarioNavViewModel extends AndroidViewModel {
    // TODO: Implement the ViewModel
    private final MutableLiveData<List<Scenario>> liveData = new MutableLiveData<>();

    public ScenarioNavViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<Scenario>> getScenario() {
        return liveData;
    }

    void doAction() {
        List<Scenario> scenarios = liveData.getValue();
        if (scenarios == null) scenarios = new ArrayList<>();
        Scenario newScenario = new Scenario();
        newScenario.setId(0);
        newScenario.setScenarioName("Default");
        scenarios.add(newScenario);
        newScenario = new Scenario();
        newScenario.setId(1);
        newScenario.setScenarioName("Loadshift");
        scenarios.add(newScenario);
        newScenario = new Scenario();
        newScenario.setId(2);
        newScenario.setScenarioName("EVDiversion");
        scenarios.add(newScenario);
        liveData.setValue(scenarios);
    }
}