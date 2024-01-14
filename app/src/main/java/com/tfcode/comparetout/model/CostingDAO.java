/*
 * Copyright (c) 2023-2024. Tony Finnerty
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
            "WHERE (net = (SELECT MIN(net) AS bignet FROM costings AS costings_1 WHERE scenarioID = :scenarioID))" +
            "AND scenarioID = :scenarioID")
    public abstract Costings getBestCostingForScenario(Long scenarioID);

    @Query("SELECT EXISTS (SELECT * FROM costings WHERE scenarioID = :scenarioID AND pricePlanId = :pricePlanIndex) AS OK")
    public abstract boolean costingExists(long scenarioID, long pricePlanIndex);

    @Query("SELECT DISTINCT REPLACE(scenarioName, ',', ';') || ', ' || fullPlanName || ', ' || net || ', ' || buy || ', ' || sell || ', ' || " +
            "standingCharges || ', ' || signUpBonus AS line FROM costings, PricePlans WHERE pricePlanIndex = pricePlanID")
    public abstract List<String> getAllComparisonsNow();

    @Query("DELETE FROM costings WHERE pricePlanId NOT IN (SELECT pricePlanIndex FROM PricePlans)")
    public abstract void pruneCostings();
}
