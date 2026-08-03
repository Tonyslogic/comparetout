/*
 * Copyright (c) 2026. Tony Finnerty
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

package com.tfcode.comparetout.model.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import com.tfcode.comparetout.model.priceplan.PlanCombination;

import java.util.List;

/**
 * Ticked (import plan × export plan) pairings.
 *
 * <p>A new DAO rather than methods on {@code PricePlanDAO}: pairings are a
 * separate concern with their own table, and the standing rule keeps the legacy
 * plan/costing DAOs closed to unrelated additions.
 *
 * <p>Note what is <b>not</b> here: nothing that gates costing. Every active pair
 * is costed regardless of whether it is ticked (the buy/sell totals decompose —
 * plans/region/import-plans.md §1.2). A row here makes a pair <i>visible</i>, and
 * suppresses the import plan's bundled row (§3.3).
 */
@Dao
public abstract class CombinationDAO {

    /** Every ticked pairing, for reactive UI binding. */
    @Query("SELECT * FROM plan_combinations")
    public abstract LiveData<List<PlanCombination>> loadCombinations();

    /** Every ticked pairing, synchronously — for workers on a background thread. */
    @Query("SELECT * FROM plan_combinations")
    public abstract List<PlanCombination> getCombinationsNow();

    /** The export plans ticked for one import plan. */
    @Query("SELECT exportPlanID FROM plan_combinations WHERE importPlanID = :importPlanID")
    public abstract List<Long> getExportPlanIdsFor(long importPlanID);

    /** True when this import plan has at least one ticked export pairing —
     *  the test that suppresses its bundled row and zeroes its scalar feed. */
    @Query("SELECT EXISTS (SELECT 1 FROM plan_combinations WHERE importPlanID = :importPlanID)")
    public abstract boolean hasPairing(long importPlanID);

    /** Tick a pairing, or re-stamp its source if already ticked. */
    @Upsert
    public abstract void upsert(PlanCombination combination);

    @Upsert
    public abstract void upsertAll(List<PlanCombination> combinations);

    /** Untick one pairing. */
    @Query("DELETE FROM plan_combinations " +
            "WHERE importPlanID = :importPlanID AND exportPlanID = :exportPlanID")
    public abstract void delete(long importPlanID, long exportPlanID);

    /** Drop every pairing that names this plan on either side — called when a
     *  plan is deleted, alongside the costing cleanup. */
    @Query("DELETE FROM plan_combinations " +
            "WHERE importPlanID = :planID OR exportPlanID = :planID")
    public abstract void deleteForPlan(long planID);

    /**
     * Drop pairings whose import or export plan no longer exists. The mirror of
     * {@code CostingDAO.pruneCostings}; unlike costings there is no bundled
     * sentinel here, so both sides are tested unconditionally.
     */
    @Query("DELETE FROM plan_combinations " +
            "WHERE importPlanID NOT IN (SELECT pricePlanIndex FROM PricePlans) " +
            "   OR exportPlanID NOT IN (SELECT pricePlanIndex FROM PricePlans)")
    public abstract void pruneCombinations();

    @Query("DELETE FROM plan_combinations")
    public abstract void deleteAll();
}
