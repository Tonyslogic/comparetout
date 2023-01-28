package com.tfcode.comparetout;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.tfcode.comparetout.dbmodel.DayRate;
import com.tfcode.comparetout.dbmodel.DoubleHolder;
import com.tfcode.comparetout.dbmodel.IntHolder;
import com.tfcode.comparetout.dbmodel.PricePlan;
import com.tfcode.comparetout.dbmodel.ToutcRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PricePlanNavViewModel extends AndroidViewModel {

    private final ToutcRepository toutcRepository;

    private final LiveData<Map<PricePlan, List<DayRate>>> allPricePlans;

    public PricePlanNavViewModel(Application application) {
        super(application);
        toutcRepository = new ToutcRepository(application);
        allPricePlans = toutcRepository.getAllPricePlans();
    }

    LiveData<Map<PricePlan, List<DayRate>>> getAllPricePlans() {
        return allPricePlans;
    }

    public void insertPricePlan(PricePlan pp, List<DayRate> drs) {
        toutcRepository.insert(pp, drs);
    }
}