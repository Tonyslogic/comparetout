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
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.RewriteQueriesToDropUnusedColumns;
import androidx.room.Update;

import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.model.scenario.LoadProfileData;

import java.util.ArrayList;
import java.util.List;

/**
 * Load-profile / load-profile-data queries, moved verbatim from ScenarioDAO
 * (mega-refactor C7). Pure abstract queries only — orchestration lives in
 * {@link com.tfcode.comparetout.model.ops.LoadProfileOps}. Deliberately kept on
 * ScenarioDAO: copyLoadProfileData (copyScenario lifecycle, C9),
 * getLoadProfileForScenarioID (used by lifecycle export/components and the
 * link/copy ops), linkLoadProfileFromScenario, and the addNew / orphan-delete
 * primitives.
 */
@Dao
public abstract class LoadProfileDAO {

    @Query("SELECT * FROM loadprofile, scenario2loadprofile " +
            "WHERE scenarioID = :scenarioID AND loadProfile.loadProfileIndex = loadProfileID")
    @RewriteQueriesToDropUnusedColumns
    public abstract LiveData<LoadProfile> getLoadProfile(Long scenarioID);

    @Query("SELECT * FROM loadprofile WHERE loadProfileIndex = :id")
    public abstract LoadProfile getLoadProfileWithLoadProfileID(long id);

    @Update (entity = LoadProfile.class)
    public abstract void updateLoadProfile(LoadProfile loadProfile);

    @Query("SELECT DISTINCT loadProfileID FROM loadprofiledata WHERE loadProfileID = :id")
    public abstract long loadProfileDataCheck(long id);

    @Query("DELETE FROM loadprofiledata WHERE loadProfileID = :id")
    public abstract void deleteLoadProfileData(long id);

    @Insert(entity = LoadProfileData.class)
    public abstract void createLoadProfileDataEntries(ArrayList<LoadProfileData> rows);

    @Query("SELECT loadProfileIndex FROM loadprofile WHERE loadProfileIndex NOT IN " +
            "(SELECT DISTINCT loadProfileID FROM loadprofiledata)")
    public abstract List<Long> checkForMissingLoadProfileData();

    @Query("SELECT scenarioName FROM scenarios WHERE scenarioIndex IN (" +
            "SELECT scenarioID FROM scenario2loadprofile WHERE  loadProfileID = (" +
            "SELECT loadProfileID FROM scenario2loadprofile WHERE scenarioID = :scenarioID) AND scenarioID != :scenarioID )")
    public abstract List<String> getLinkedLoadProfiles(Long scenarioID);

    @Query("SELECT DISTINCT gridExportMax FROM loadprofile, scenario2loadprofile WHERE loadProfileID = loadProfileIndex AND scenarioID = :scenarioID")
    public abstract double getGridExportMaxForScenario(long scenarioID);
}
