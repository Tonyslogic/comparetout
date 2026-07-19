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
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.PanelData;
import com.tfcode.comparetout.model.scenario.PanelPVSummary;
import com.tfcode.comparetout.model.scenario.Scenario2Panel;

import java.util.ArrayList;
import java.util.List;

/**
 * Panel / panel-data queries, moved verbatim from ScenarioDAO (mega-refactor
 * C6). Pure abstract queries only — orchestration lives in
 * {@link com.tfcode.comparetout.model.ops.PanelOps}.
 * <p>
 * Deliberately NOT moved (stay on ScenarioDAO until later): {@code copyPanelData}
 * (called by the copyScenario lifecycle transaction, C9); {@code getPanelForID},
 * {@code getAllPanels}, {@code deleteAllPanelData} (direct non-facade consumers —
 * SnapshotImporter and PanelDataRefreshWorker hold scenarioDAO directly, so those
 * migrate when their callers do); and the addNew / orphan-delete primitives and
 * linkPanelFromScenario.
 */
@Dao
public abstract class PanelDAO {

    @Query("SELECT * FROM scenario2panel")
    public abstract LiveData<List<Scenario2Panel>> loadPanelRelations();

    @Query("DELETE FROM scenario2panel WHERE scenarioID = :scenarioID AND panelID = :panelID")
    public abstract void removeScenario2Panel(Long panelID, Long scenarioID);

    @Update (entity = Panel.class)
    public abstract void updatePanel(Panel panel);

    @Query("SELECT COUNT(*) FROM scenario2panel WHERE scenarioID = :scenarioID AND panelID = :panelID")
    public abstract int countPanelLink(long scenarioID, long panelID);

    @Insert(entity = PanelData.class, onConflict = OnConflictStrategy.REPLACE)
    public abstract void savePanelData(ArrayList<PanelData> panelDataList);

    @Query("SELECT panelID, substr(Date, 6,2) AS Month, SUM(pv) AS tot FROM paneldata GROUP BY panelID, Month ORDER BY Month ASC")
    public abstract LiveData<List<PanelPVSummary>> getPanelPVSummary();

    @Query("SELECT COUNT(*) FROM paneldata WHERE panelID IN " +
            "(SELECT panelIndex FROM panels WHERE ROUND(latitude,3) = ROUND(:lat,3) " +
            "AND ROUND(longitude,3) = ROUND(:lon,3) AND azimuth = :azimuth AND slope = :slope)")
    public abstract int countPanelDataForParameters(double lat, double lon, int azimuth, int slope);

    /** Names of the scenarios whose panels sit at this PVGIS location/orientation — for the PVGIS cache view
     *  ("which scenarios this cached download feeds"). Matches the same lat/lon rounding as the cache key. */
    @Query("SELECT DISTINCT scenarios.scenarioName FROM scenarios, scenario2panel, panels " +
            "WHERE scenarios.scenarioIndex = scenario2panel.scenarioID " +
            "AND scenario2panel.panelID = panels.panelIndex " +
            "AND ROUND(panels.latitude,3) = ROUND(:lat,3) " +
            "AND ROUND(panels.longitude,3) = ROUND(:lon,3) " +
            "AND panels.azimuth = :azimuth AND panels.slope = :slope " +
            "ORDER BY scenarios.scenarioName")
    public abstract List<String> getScenarioNamesAtLocation(double lat, double lon, int azimuth, int slope);

    @Query("SELECT CASE WHEN " +
            "(SELECT COUNT (DISTINCT paneldata.panelID) AS Found FROM paneldata, scenario2panel WHERE scenario2panel.panelID = paneldata.panelID AND scenarioID = :scenarioID) = " +
            "(SELECT COUNT (DISTINCT panelID) AS Needed FROM scenario2panel WHERE scenarioID = :scenarioID) " +
            "THEN 1 " +
            "ELSE 0 " +
            "END AS OK")
    public abstract boolean checkForMissingPanelData(Long scenarioID);

    @Query("DELETE FROM paneldata WHERE panelID = :panelID")
    public abstract void removePanelData(Long panelID);

    @Query("SELECT scenarioName FROM scenarios WHERE scenarioIndex IN (" +
            "SELECT scenarioID FROM scenario2panel WHERE panelID = :panelIndex) AND scenarioIndex != :scenarioID")
    public abstract List<String> getLinkedPanels(long panelIndex, Long scenarioID);
}
