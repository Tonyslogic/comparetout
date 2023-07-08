/*
 * Copyright (c) 2023. Tony Finnerty
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.tfcode.comparetout.model;

import android.database.sqlite.SQLiteConstraintException;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Upsert;

import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.PricePlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Dao
public abstract class PricePlanDAO {
    @Insert()
    abstract long addNewPricePlan(PricePlan pp);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void addNewDayRate(DayRate dr);

    @Transaction
    void addNewPricePlanWithDayRates(PricePlan pp, List<DayRate> drs) {
        try {
            long pricePlanID = addNewPricePlan(pp);
            for (DayRate dr : drs) {
                dr.setDayRateIndex(0);
                dr.setPricePlanId(pricePlanID);
                addNewDayRate(dr);
            }
        }
        catch (SQLiteConstraintException e) {
            System.out.println("Silently ignoring a duplicate added as new");
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

    @Query("SELECT * FROM PricePlans JOIN DayRates ON PricePlans.pricePlanIndex = DayRates.pricePlanId ORDER BY PricePlans.supplier ASC, PricePlans.planName ASC ")
    public abstract LiveData<Map<PricePlan, List<DayRate>>> loadPricePlans();

    @Transaction
    public void deletePricePlan(long id) {
        deleteDayRatesInPlan(id);
        deletePricePlanRow(id);
    }

    @Query("DELETE FROM DayRates WHERE pricePlanId = :id")
    public abstract void deleteDayRatesInPlan(long id);

    @Query("DELETE FROM PricePlans WHERE pricePlanIndex = :id")
    public abstract void deletePricePlanRow(long id);

    @Delete
    public abstract void deletePricePlan(PricePlan pp);

    @Query("UPDATE PricePlans SET active = :checked WHERE pricePlanIndex = :id")
    public abstract void updatePricePlanActiveStatus(int id, boolean checked);

    @Upsert(entity = PricePlan.class )
    public abstract void updatePricePlan(PricePlan pp);

    @Upsert(entity = DayRate.class)
    public abstract void updateDayRate(List<DayRate> drs);

    @Transaction
    public void updatePricePlanWithDayRates(PricePlan pp, ArrayList<DayRate> drs) {
        if (pp.getPricePlanIndex() == 0) {
            addNewPricePlanWithDayRates(pp, drs);
        }
        else {
            List<DayRate> oldDayRates = getAllDayRatesForPricePlanID(pp.getPricePlanIndex());
            List<Long> oldIDs = new ArrayList<>();
            for (DayRate oldDayRate : oldDayRates) oldIDs.add(oldDayRate.getDayRateIndex());
            List<Long> newIDs = new ArrayList<>();
            for (DayRate updatedDayRate: drs) newIDs.add(updatedDayRate.getDayRateIndex());
            oldIDs.removeAll(newIDs);
            for (Long toRemove: oldIDs) deleteDayRate(toRemove);
            updatePricePlan(pp);
            updateDayRate(drs);
        }

    }

    @Query("DELETE FROM DayRates WHERE dayRateIndex = :toRemove")
    public abstract void deleteDayRate(Long toRemove);

    @Query("SELECT planName FROM PricePlans WHERE pricePlanIndex = :id")
    public abstract String getNameForPlanID (long id);

    @Query("SELECT * FROM priceplans")
    public abstract List<PricePlan> loadPricePlansNow();

    @Query("SELECT * FROM dayrates WHERE pricePlanId = :id")
    public abstract List<DayRate> getAllDayRatesForPricePlanID(long id);


    @Query("SELECT * FROM PricePlans JOIN DayRates ON PricePlans.pricePlanIndex = DayRates.pricePlanId ORDER BY PricePlans.supplier ASC, PricePlans.planName ASC ")
    public abstract Map<PricePlan, List<DayRate>> getAllPricePlansForExport();
}
