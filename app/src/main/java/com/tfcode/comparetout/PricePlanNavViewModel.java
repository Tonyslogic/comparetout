package com.tfcode.comparetout;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.tfcode.comparetout.model.DayRate;
import com.tfcode.comparetout.model.PricePlan;
import com.tfcode.comparetout.model.ToutcRepository;

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
}