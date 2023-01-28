package com.tfcode.comparetout.dbmodel;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;
import java.util.Map;

@Dao
public abstract class PricePlanDAO {
    @Insert
    abstract long addNewPricePlan(PricePlan pp);

    @Insert
    abstract void addNewDayRate(DayRate dr);

    @Transaction
    void addNewPricePlanWithDayRates(PricePlan pp, List<DayRate> drs) {
        long pricePlanID = addNewPricePlan(pp);
        for (DayRate dr : drs) {
            dr.setPricePlanId(pricePlanID);
            addNewDayRate(dr);
        }
    }

    @Transaction
    void deleteAll() {
        clearDayRates();
        clearPricePlans();
    }

    @Query("DELETE FROM DayRates")
    abstract void clearDayRates();

    @Query("DELETE FROM PricePlans")
    abstract void clearPricePlans();

    @Query("SELECT * FROM PricePlans JOIN DayRates ON PricePlans.id = DayRates.pricePlanId")
    public abstract LiveData<Map<PricePlan, List<DayRate>>> loadPricePlans();
}
