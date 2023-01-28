package com.tfcode.comparetout;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

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
        if (scenarios == null) scenarios = new ArrayList<Scenario>();
        Scenario newScenario = new Scenario();
        newScenario.id = 0;
        newScenario.name = "Default";
        scenarios.add(newScenario);
        newScenario = new Scenario();
        newScenario.id = 1;
        newScenario.name = "Loadshift";
        scenarios.add(newScenario);
        newScenario = new Scenario();
        newScenario.id = 2;
        newScenario.name = "EVDiversion";
        scenarios.add(newScenario);
        liveData.setValue(scenarios);
    }
}