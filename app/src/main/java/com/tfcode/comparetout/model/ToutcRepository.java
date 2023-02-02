package com.tfcode.comparetout.model;

import android.app.Application;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.Map;

public class ToutcRepository {
    private final PricePlanDAO pricePlanDAO;
    private final LiveData<Map<PricePlan, List<DayRate>>> allPricePlans;

    // Note that in order to unit test the WordRepository, you have to remove the Application
    // dependency. This adds complexity and much more code, and this sample is not about testing.
    // See the BasicSample in the android-architecture-components repository at
    // https://github.com/googlesamples
    public ToutcRepository(Application application) {
        ToutcDB db = ToutcDB.getDatabase(application);
        pricePlanDAO = db.pricePlanDAO();
        allPricePlans = pricePlanDAO.loadPricePlans();
    }

    // Room executes all queries on a separate thread.
    // Observed LiveData will notify the observer when the data has changed.
    public LiveData<Map<PricePlan, List<DayRate>>> getAllPricePlans() {
        return allPricePlans;
    }

    public Map<PricePlan, List<DayRate>> getPricePlan(Integer id) {
        return pricePlanDAO.loadPricePlan(id);
    }

    // You must call this on a non-UI thread or your app will throw an exception. Room ensures
    // that you're not doing any long running operations on the main thread, blocking the UI.
    public void insert(PricePlan pp, List<DayRate> drs) {
        ToutcDB.databaseWriteExecutor.execute(() -> pricePlanDAO.addNewPricePlanWithDayRates(pp, drs));
    }

    public void deletePricePlan(Integer id) {
        ToutcDB.databaseWriteExecutor.execute(() -> {
            pricePlanDAO.deletePricePlan(id);
            System.out.println("Size after delete = " + allPricePlans.getValue().entrySet().size());
        });
    }

    public void deleteAll() {
        ToutcDB.databaseWriteExecutor.execute(pricePlanDAO::deleteAll);
    }

    public void deletePPRow (int id){
        ToutcDB.databaseWriteExecutor.execute(() -> pricePlanDAO.deletePricePlanRow(id));
    }

    public void delpp(PricePlan pp) {
        ToutcDB.databaseWriteExecutor.execute(() -> pricePlanDAO.delpp(pp));
    }

    public void updatePricePlanActiveStatus(int id, boolean checked) {
        ToutcDB.databaseWriteExecutor.execute(() ->
                pricePlanDAO.updatePricePlanActiveStatus(id, checked));
    }
}
