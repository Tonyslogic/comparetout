package com.tfcode.comparetout.model;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Upsert;

import java.util.ArrayList;
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
            dr.setId(0);
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

    @Query("SELECT * FROM PricePlans JOIN DayRates ON PricePlans.id = DayRates.pricePlanId ORDER BY PricePlans.supplier ASC, PricePlans.planName ASC ")
    public abstract LiveData<Map<PricePlan, List<DayRate>>> loadPricePlans();

    @Query("SELECT * FROM PricePlans JOIN DayRates ON PricePlans.id = DayRates.pricePlanId WHERE PricePlans.id = :id")
    public abstract Map<PricePlan, List<DayRate>> loadPricePlan(long id);

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

    @Query("UPDATE PricePlans SET active = :checked WHERE id = :id")
    public abstract void updatePricePlanActiveStatus(int id, boolean checked);

    @Upsert(entity = PricePlan.class )
    public abstract void updatePricePlan(PricePlan pp);

    @Upsert(entity = DayRate.class)
    public abstract void updateDayRate(List<DayRate> drs);

    @Transaction
    public void updatePricePlanWithDayRates(PricePlan pp, ArrayList<DayRate> drs) {
        System.out.println("DAO Updating " + pp.getPlanName() + "," + pp.getId());
        if (pp.getId() == 0) {
            System.out.println("DAO creation from Update ");
            addNewPricePlanWithDayRates(pp, drs);
        }
        else {
            updatePricePlan(pp);
            System.out.println("DAO read " + getNameForPlanID(pp.getId()) + " for " + pp.getId());
            System.out.println("DAO count of dayRates = " + drs.size());
            updateDayRate(drs);
        }

    }

    @Query("SELECT planName FROM PricePlans WHERE id = :id")
    public abstract String getNameForPlanID (long id);
}
