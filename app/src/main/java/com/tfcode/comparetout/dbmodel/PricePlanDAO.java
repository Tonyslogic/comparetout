package com.tfcode.comparetout.dbmodel;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;
import java.util.Map;

@Dao
public abstract class PricePlanDAO {
    @Insert
    abstract long addNewPricePlan(PricePlan pp);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
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

    @Transaction
    public void deletePricePlan(long id) {
        System.out.println("deleting " + id);
        long dels = deleteDayRates(id);
        System.out.println("removed rates " + dels);
        dels = deletePricePlanRow(id);
        System.out.println("removed plans " + dels);
        System.out.println("deleted " + id);
    }

    @Query("DELETE FROM DayRates WHERE pricePlanId = :id")
    public abstract int deleteDayRates(long id);

    @Query("DELETE FROM PricePlans WHERE id = :id")
    public abstract int deletePricePlanRow(long id);

    @Delete
    public abstract void delpp(PricePlan pp);
}
