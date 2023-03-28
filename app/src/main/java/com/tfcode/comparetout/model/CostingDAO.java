package com.tfcode.comparetout.model;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.tfcode.comparetout.model.costings.Costings;

import java.util.List;

@Dao
public abstract class CostingDAO {

    @Query("SELECT * FROM costings")
    public abstract LiveData<List<Costings>> loadCostings();

    @Insert
    public abstract void saveCosting(Costings costing);

    @Query("DELETE FROM costings WHERE pricePlanID = :id")
    public abstract void deleteRelatedCostings(int id);

    @Query("SELECT * FROM costings " +
            "WHERE (nett = (SELECT MIN(nett) AS bignett FROM costings AS costings_1 WHERE scenarioID = :scenarioID))" +
            "AND scenarioID = :scenarioID")
    public abstract Costings getBestCostingForScenario(Long scenarioID);
}
