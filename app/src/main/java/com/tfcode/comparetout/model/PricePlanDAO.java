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

/**
 * Data Access Object for managing electricity price plans and their associated day rates.
 * 
 * This DAO handles the complex relationship between PricePlans and DayRates tables,
 * where each price plan can have multiple day rates (different pricing for different
 * days of the week or time periods). The class provides comprehensive CRUD operations
 * with transactional integrity to ensure data consistency.
 * 
 * Key relationships managed:
 * - PricePlans (1) -> DayRates (Many) via pricePlanIndex/pricePlanId foreign key
 * - Plans are uniquely identified by supplier + planName combination
 * - Day rates inherit the parent plan's ID when inserted
 * 
 * Complex operations supported:
 * - Transactional plan creation with associated day rates
 * - Bulk updates maintaining referential integrity
 * - Cascading deletions to prevent orphaned day rates
 * - LiveData queries for reactive UI updates
 */
@Dao
public abstract class PricePlanDAO {
    /**
     * Insert a new price plan and return its generated ID.
     * @param pp The price plan to insert
     * @return The generated pricePlanIndex for the inserted plan
     */
    @Insert()
    abstract long addNewPricePlan(PricePlan pp);

    /**
     * Insert or replace a day rate record.
     * Uses REPLACE strategy to handle updates of existing day rates.
     * @param dr The day rate to insert/replace
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void addNewDayRate(DayRate dr);

    /**
     * Atomically create a price plan with its associated day rates.
     * 
     * This transactional method ensures data integrity when creating complex
     * price structures. If clobber is true, any existing plan with the same
     * supplier+planName combination is deleted first.
     * 
     * Process:
     * 1. Check for existing plan if clobber requested
     * 2. Insert new price plan record
     * 3. Associate all day rates with the new plan ID
     * 4. Insert day rates with proper foreign key references
     * 
     * @param pp The price plan to create
     * @param drs List of day rates to associate with the plan
     * @param clobber If true, replace any existing plan with same name
     * @return The ID of the created price plan, or 0 if creation failed
     */
    @Transaction
    long addNewPricePlanWithDayRates(PricePlan pp, List<DayRate> drs, boolean clobber) {
        long pricePlanID = 0;
        if (clobber) {
            // Find existing plan by concatenated supplier+planName identifier
            long oldPricePlanID = getPricePlanID(pp.getSupplier() + pp.getPlanName());
            deletePricePlan(oldPricePlanID);
        }
        try {
            pricePlanID = addNewPricePlan(pp);
            // Associate each day rate with the newly created plan
            for (DayRate dr : drs) {
                dr.setDayRateIndex(0);  // Reset index for new insertion
                dr.setPricePlanId(pricePlanID);  // Link to parent plan
                addNewDayRate(dr);
            }
        }
        catch (SQLiteConstraintException e) {
            // Silently handle duplicate insertions (defensive programming)
            System.out.println("Silently ignoring a duplicate added as new");
        }
        return pricePlanID;
    }

    /**
     * Find a price plan ID by its unique supplier+planName combination.
     * 
     * Query: SELECT pricePlanIndex FROM PricePlans WHERE supplier || planName = :planSupplierName
     * 
     * This concatenates supplier and planName fields to create a unique identifier
     * for price plan lookup. The concatenation approach allows flexible matching
     * while maintaining uniqueness constraints.
     * 
     * @param planSupplierName Concatenated supplier+planName string
     * @return The pricePlanIndex of the matching plan, or 0 if not found
     */
    @Query("SELECT pricePlanIndex FROM PricePlans WHERE supplier || planName  = :planSupplierName")
    public abstract long getPricePlanID(String planSupplierName);

    /**
     * Delete all price plans and day rates from the database.
     * This cascading delete ensures referential integrity by removing
     * day rates before their parent price plans.
     */
    @Transaction
    void deleteAll() {
        clearDayRates();    // Remove child records first
        clearPricePlans();  // Then remove parent records
    }

    /**
     * Remove all day rate records from the database.
     */
    @Query("DELETE FROM DayRates")
    abstract void clearDayRates();

    /**
     * Remove all price plan records from the database.
     */
    @Query("DELETE FROM PricePlans")
    abstract void clearPricePlans();

    /**
     * Load all price plans with their associated day rates for reactive UI binding.
     * 
     * Query: SELECT * FROM PricePlans JOIN DayRates ON PricePlans.pricePlanIndex = DayRates.pricePlanId 
     *        ORDER BY PricePlans.supplier ASC, PricePlans.planName ASC
     * 
     * This complex query performs an INNER JOIN to associate each price plan with its
     * day rates, creating a Map<PricePlan, List<DayRate>> result. The Room framework
     * automatically groups the joined results by the parent entity (PricePlan).
     * 
     * Ordering is alphabetical by supplier first, then plan name for consistent
     * presentation in the UI.
     * 
     * @return LiveData containing a map of price plans to their day rates lists
     */
    @Query("SELECT * FROM PricePlans JOIN DayRates ON PricePlans.pricePlanIndex = DayRates.pricePlanId ORDER BY PricePlans.supplier ASC, PricePlans.planName ASC ")
    public abstract LiveData<Map<PricePlan, List<DayRate>>> loadPricePlans();

    /**
     * Delete a price plan and all its associated day rates.
     * This cascading delete maintains referential integrity.
     * 
     * @param id The pricePlanIndex of the plan to delete
     */
    @Transaction
    public void deletePricePlan(long id) {
        deleteDayRatesInPlan(id);  // Remove child records first
        deletePricePlanRow(id);    // Then remove parent record
    }

    /**
     * Delete all day rates associated with a specific price plan.
     * @param id The pricePlanId foreign key to match
     */
    @Query("DELETE FROM DayRates WHERE pricePlanId = :id")
    public abstract void deleteDayRatesInPlan(long id);

    /**
     * Delete a specific price plan record by its primary key.
     * @param id The pricePlanIndex primary key
     */
    @Query("DELETE FROM PricePlans WHERE pricePlanIndex = :id")
    public abstract void deletePricePlanRow(long id);

    /**
     * Delete a price plan using the entity object.
     * @param pp The PricePlan entity to delete
     */
    @Delete
    public abstract void deletePricePlan(PricePlan pp);

    /**
     * Update the active status of a price plan.
     * @param id The pricePlanIndex to update
     * @param checked The new active status
     */
    @Query("UPDATE PricePlans SET active = :checked WHERE pricePlanIndex = :id")
    public abstract void updatePricePlanActiveStatus(int id, boolean checked);

    /**
     * Insert or update a price plan record.
     * @param pp The price plan to upsert
     */
    @Upsert(entity = PricePlan.class )
    public abstract void updatePricePlan(PricePlan pp);

    /**
     * Insert or update multiple day rate records.
     * @param drs The list of day rates to upsert
     */
    @Upsert(entity = DayRate.class)
    public abstract void updateDayRate(List<DayRate> drs);

    /**
     * Update a price plan and its day rates atomically.
     * 
     * This complex transactional method handles both new plan creation and
     * existing plan updates. For updates, it performs differential synchronization:
     * 
     * Process for updates:
     * 1. Get current day rates for the plan
     * 2. Compare existing IDs with updated IDs
     * 3. Delete day rates that are no longer present
     * 4. Update/insert the remaining day rates
     * 
     * This approach ensures that removed day rates are properly deleted while
     * preserving unchanged rates and adding new ones.
     * 
     * @param pp The price plan to update
     * @param drs The new list of day rates for the plan
     */
    @Transaction
    public void updatePricePlanWithDayRates(PricePlan pp, ArrayList<DayRate> drs) {
        if (pp.getPricePlanIndex() == 0) {
            // New plan - use existing creation method
            addNewPricePlanWithDayRates(pp, drs, false);
        }
        else {
            // Existing plan - perform differential update
            List<DayRate> oldDayRates = getAllDayRatesForPricePlanID(pp.getPricePlanIndex());
            
            // Extract IDs for comparison
            List<Long> oldIDs = new ArrayList<>();
            for (DayRate oldDayRate : oldDayRates) oldIDs.add(oldDayRate.getDayRateIndex());
            List<Long> newIDs = new ArrayList<>();
            for (DayRate updatedDayRate: drs) newIDs.add(updatedDayRate.getDayRateIndex());
            
            // Find day rates to remove (in old but not in new)
            oldIDs.removeAll(newIDs);
            for (Long toRemove: oldIDs) deleteDayRate(toRemove);
            
            // Update the plan and its day rates
            updatePricePlan(pp);
            updateDayRate(drs);
        }
    }

    /**
     * Delete a specific day rate by its primary key.
     * @param toRemove The dayRateIndex to delete
     */
    @Query("DELETE FROM DayRates WHERE dayRateIndex = :toRemove")
    public abstract void deleteDayRate(Long toRemove);

    /**
     * Get the plan name for a given price plan ID.
     * @param id The pricePlanIndex to look up
     * @return The planName string
     */
    @Query("SELECT planName FROM PricePlans WHERE pricePlanIndex = :id")
    public abstract String getNameForPlanID (long id);

    /**
     * Load all price plans immediately (not reactive).
     * @return List of all PricePlan entities
     */
    @Query("SELECT * FROM priceplans")
    public abstract List<PricePlan> loadPricePlansNow();

    /**
     * Get all day rates for a specific price plan ID.
     * @param id The pricePlanId foreign key
     * @return List of DayRate entities for the plan
     */
    @Query("SELECT * FROM dayrates WHERE pricePlanId = :id")
    public abstract List<DayRate> getAllDayRatesForPricePlanID(long id);

    /**
     * Export all price plans with their day rates for data export functionality.
     * 
     * Query: SELECT * FROM PricePlans JOIN DayRates ON PricePlans.pricePlanIndex = DayRates.pricePlanId 
     *        ORDER BY PricePlans.supplier ASC, PricePlans.planName ASC
     * 
     * Similar to loadPricePlans() but returns immediate results rather than LiveData,
     * making it suitable for export operations that need to run on background threads.
     * 
     * @return Map of price plans to their associated day rate lists
     */
    @Query("SELECT * FROM PricePlans JOIN DayRates ON PricePlans.pricePlanIndex = DayRates.pricePlanId ORDER BY PricePlans.supplier ASC, PricePlans.planName ASC ")
    public abstract Map<PricePlan, List<DayRate>> getAllPricePlansForExport();
}
