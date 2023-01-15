package com.tfcode.comparetout;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

public class PricePlanNavViewModel extends ViewModel {
    // TODO: Implement the ViewModel
    private final MutableLiveData<List<Plan>> liveData = new MutableLiveData<>();

    public LiveData<List<Plan>> getPlan() {
        return liveData;
    }

    void doAction() {
        List<Plan> plans = liveData.getValue();
        if (plans == null) plans = new ArrayList<Plan>();
        Plan newPlan = new Plan();
        newPlan.id = 0;
        newPlan.supplier = "Energia";
        newPlan.plan = "SmartEV 40%";
        plans.add(newPlan);
        newPlan = new Plan();
        newPlan.id = 1;
        newPlan.supplier = "EI";
        newPlan.plan = "NightSaver";
        plans.add(newPlan);
        newPlan = new Plan();
        newPlan.id = 2;
        newPlan.supplier = "SSE";
        newPlan.plan = "SomePlan";
        plans.add(newPlan);
        liveData.setValue(plans);
        // depending on the action, do necessary business logic calls and update the
        // userLiveData.
    }
}